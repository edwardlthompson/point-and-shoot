package dev.pointandshoot

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraDevice
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Video recording controller extracted from PreviewEngineScreen.kt (Sprint 12.4).
 *
 * Supports two encoder paths:
 * - **[MediaRecorder] path**: standard 8-bit H.264/HEVC up to 60 fps (bounded by
 *   `ro.media.recorder-max-base-layer-fps=60` on CPH2655-class devices).
 * - **[MediaCodecVideoRecorder] path**: Surface-input MediaCodec encoder that bypasses the
 *   60 fps MediaRecorder cap. Used automatically when [VideoFormat.isTenBit] is true
 *   or when [desiredFps] > [IN_APP_VIDEO_PREVIEW_CAP_FPS]. Drives `c2.qti.hevc.encoder`
 *   directly (supports 480 fps, Main10/HDR10/HDR10Plus profiles, YUVP010 color format).
 *
 * Thread safety: All mutable state accessed only from [handler] thread.
 * UI thread callbacks posted via [mainHandler].
 */
internal class VideoRecordingController(
    private val appContext: Context,
    private val handler: Handler?,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {

    companion object {
        private const val TAG = "PNS.VideoController"

        /** Cap in-app preview + recording to 60 fps for thermal stability. */
        const val IN_APP_VIDEO_PREVIEW_CAP_FPS = 60

        /** Bitrate for in-app MP4 recording (H.264 Baseline). */
        const val IN_APP_VIDEO_RECORD_BITRATE = 20_000_000

        /** Minimum/Maximum supported video FPS. */
        private const val MIN_FPS = 15
        private const val MAX_FPS = 480 // HFR support up to 480fps (OnePlus 13)
        
        /** HFR threshold - FPS above this requires CameraConstrainedHighSpeedCaptureSession. */
        private const val HFR_THRESHOLD_FPS = 120
    }

    // State assigned from [handler] thread only
    @Volatile private var mediaRecorder: MediaRecorder? = null
    private var mcRecorder: MediaCodecVideoRecorder? = null
    private var recordingSurface: Surface? = null
    private var recordingUri: Uri? = null
    private var recordingPfd: ParcelFileDescriptor? = null
    @Volatile private var recorderStarted: Boolean = false
    private var audioEnabled: Boolean = false

    /** Dedupes [applyShell] across idle recompositions. */
    private var lastShellWant: Boolean? = null

    /**
     * True when the pending or active recording will use [MediaCodecVideoRecorder].
     * Set as soon as `wantRecord=true` is processed so [createSession] can skip
     * `useHighSpeed` even before the recorder is fully prepared.
     */
    @Volatile var wantsMediaCodecPath: Boolean = false
        private set

    /**
     * After a start failure, blocks further start attempts until [applyShellLocked]
     * sees [wantRecord]==false (Compose clears recording).
     */
    @Volatile private var startFailureHold: Boolean = false

    sealed class Event {
        data object StartFailed : Event()
        data class Stopped(val uri: Uri?, val audioEnabled: Boolean) : Event()
    }

    /**
     * Result of preparing video recording.
     */
    sealed class PrepareResult {
        /** Recording prepared successfully, session rebuild needed. */
        data class Ready(val surface: Surface) : PrepareResult()
        /** Recording preparation rejected. */
        data class Rejected(val reason: String) : PrepareResult()
        /** No action needed (already prepared, or stopping). */
        data object NoAction : PrepareResult()
    }

    /**
     * Check if RECORD_AUDIO permission is granted.
     */
    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Query current recorder presence (for UI/automation polls).
     */
    fun isRecorderPresent(): Boolean = mediaRecorder != null || mcRecorder?.isActive == true

    /** True after [maybeStartRecorder] / MediaRecorder.start (not merely prepared). */
    fun isRecorderStarted(): Boolean = recorderStarted

    /** MediaCodec path: true once [MediaMuxer] has started (first encoder output format). */
    fun isMuxerReadyForRecord(): Boolean =
        mcRecorder?.isMuxerReady() == true || mediaRecorder != null

    /**
     * Pre-signal from the Compose layer that the upcoming recording will use [MediaCodecVideoRecorder].
     * Called synchronously before `isRecording=true` triggers a session rebuild, so [createSession]
     * can skip `useHighSpeed` before [applyShellLocked] has run on the handler thread.
     */
    fun hintMediaCodecPath(wants: Boolean) {
        wantsMediaCodecPath = wants
    }

    /**
     * Query start failure hold state (for debouncing).
     */
    fun isStartFailureHold(): Boolean = startFailureHold

    /**
     * Sprint 13.8: Returns the peak audio amplitude of the current [MediaRecorder] recording
     * (0..32767), or 0 when no MediaRecorder is active (MediaCodec path or not recording).
     * Resets the internal peak counter on each call, matching [MediaRecorder.getMaxAmplitude] semantics.
     */
    fun peekAudioAmplitude(): Int = mcRecorder?.peekAmplitude() ?: mediaRecorder?.maxAmplitude ?: 0

    /**
     * Get the recording surface if currently preparing/recording.
     */
    fun getRecordingSurface(): Surface? = recordingSurface

    /**
     * Apply video recording shell - prepares or tears down recording.
     * Thread-safe: posts to handler internally.
     *
     * @param wantRecord true to start recording, false to stop
     * @param profile imaging profile for output path
     * @param desiredFps target frame rate (may be capped)
     * @param size video output size
     * @param orientationHintDegrees rotation hint for recorder
     * @param wantHighSpeed if true and device supports HFR, use high-speed recording path
     * @param supportsHighSpeed device capability - true if high-speed video available
     * @param videoFormat encoding format (H.264, H.265, H.265 10-bit, DCG) - default H.264
     * @param onEvent callback for recording events
     * @return PrepareResult indicating what action was taken:
     * - Ready(surface): Recording prepared, caller must rebuild session with surface
     * - Rejected(reason): Preparation failed
     * - NoAction: No state change (already prepared, stopping, or debounced)
     */
    fun applyShell(
        wantRecord: Boolean,
        profile: ImagingProfile,
        desiredFps: Int,
        size: android.util.Size,
        orientationHintDegrees: Int,
        wantHighSpeed: Boolean = false,
        supportsHighSpeed: Boolean = false,
        videoFormat: VideoFormat = VideoFormatPresets.getAvailableFormats(size, desiredFps.coerceAtMost(60)).first(),
        onEvent: (Event) -> Unit,
    ): PrepareResult {
        val h = handler
        if (h == null) {
            if (wantRecord) {
                Log.w(TAG, "applyShell: handler null, rejecting start")
                startFailureHold = true
                mainHandler.post { onEvent(Event.StartFailed) }
            }
            return PrepareResult.Rejected("handler unavailable")
        }

        return if (Thread.currentThread() === h.looper.thread) {
            applyShellLocked(wantRecord, profile, desiredFps, size, orientationHintDegrees, wantHighSpeed, supportsHighSpeed, videoFormat, onEvent)
        } else {
            var result: PrepareResult = PrepareResult.NoAction
            h.post {
                result = applyShellLocked(wantRecord, profile, desiredFps, size, orientationHintDegrees, wantHighSpeed, supportsHighSpeed, videoFormat, onEvent)
            }
            // Note: This is a synchronous return - the actual result will be set after post executes
            // For correct behavior, this should be called from handler thread
            result
        }
    }

    private fun applyShellLocked(
        wantRecord: Boolean,
        profile: ImagingProfile,
        desiredFps: Int,
        size: android.util.Size,
        orientationHintDegrees: Int,
        wantHighSpeed: Boolean,
        supportsHighSpeed: Boolean,
        videoFormat: VideoFormat,
        onEvent: (Event) -> Unit,
    ): PrepareResult {
        // Debounce duplicate states
        if (lastShellWant == wantRecord && !startFailureHold) {
            return PrepareResult.NoAction
        }
        lastShellWant = wantRecord

        if (!wantRecord) {
            // Stop recording
            startFailureHold = false
            wantsMediaCodecPath = false
            val hadMc = mcRecorder != null
            val hadMr = mediaRecorder != null
            if (hadMc || hadMr) {
                val wasAudioEnabled = audioEnabled
                val uri = stopRecordingLocked(onEvent)
                if (hadMr) {
                    mainHandler.post { onEvent(Event.Stopped(uri, wasAudioEnabled)) }
                }
            }
            return PrepareResult.NoAction
        }

        // Want to start recording
        if (mediaRecorder != null || mcRecorder?.isActive == true || startFailureHold) {
            return PrepareResult.NoAction // Already recording or in failure hold
        }

        // HFR check - MediaCodec path handles fps > 60 natively; no rejection needed for that path
        val isHfr = desiredFps >= HFR_THRESHOLD_FPS
        val useMediaCodecPath = isHfr || videoFormat.isTenBit || videoFormat.isDcg
        // Signal immediately so createSession can skip useHighSpeed before recorder is prepared
        wantsMediaCodecPath = useMediaCodecPath
        if (isHfr && !useMediaCodecPath && !(wantHighSpeed && supportsHighSpeed)) {
            Log.w(TAG, "HFR video rejected: fps=$desiredFps, wantHighSpeed=$wantHighSpeed, supportsHighSpeed=$supportsHighSpeed")
            startFailureHold = true
            mainHandler.post { onEvent(Event.StartFailed) }
            return PrepareResult.Rejected("HFR not supported or not enabled")
        }

        // MediaCodec path: don't clamp to 60fps cap — codec supports up to 480fps
        val targetFps = if (useMediaCodecPath) {
            desiredFps.coerceIn(MIN_FPS, MAX_FPS)
        } else if (isHfr && wantHighSpeed && supportsHighSpeed) {
            desiredFps.coerceIn(HFR_THRESHOLD_FPS, MAX_FPS)
        } else {
            min(desiredFps, IN_APP_VIDEO_PREVIEW_CAP_FPS).coerceIn(MIN_FPS, MAX_FPS)
        }
        
        if (isHfr || videoFormat.isTenBit) {
            Log.i(TAG, "MediaCodec path: fps=$targetFps tenBit=${videoFormat.isTenBit} hfr=$isHfr")
        }
        
        val actualBitrate = bitrateForSize(size.width, size.height, targetFps, videoFormat.codec)
        val scalePct =
            HudSettings.load(appContext).videoBitrateScalePercent.coerceIn(
                HudSettings.VIDEO_BITRATE_SCALE_MIN,
                HudSettings.VIDEO_BITRATE_SCALE_MAX,
            )
        Log.i(
            TAG,
            "videoBitrateScale=${scalePct}% actualBitrate=$actualBitrate " +
                "size=${size.width}x${size.height} fps=$targetFps codec=${videoFormat.codec}",
        )

        // Open video output
        val pair = runCatching {
            CaptureStorage.openVideoOutputReadWritePfd(appContext, profile)
        }.getOrElse {
            Log.w(TAG, "video output unavailable: ${it.message}")
            startFailureHold = true
            mainHandler.post { onEvent(Event.StartFailed) }
            return PrepareResult.Rejected("output unavailable: ${it.message}")
        }

        val uri = pair.first
        val pfd = pair.second

        // Check audio permission
        val hasAudio = hasAudioPermission()
        audioEnabled = hasAudio

        // MediaCodec path for HFR (>60fps) and 10-bit — bypasses ro.media.recorder-max-base-layer-fps=60
        if (useMediaCodecPath) {
            return prepareMediaCodecPath(
                uri, pfd, videoFormat, targetFps, size, hasAudio, onEvent,
            )
        }

        // Standard MediaRecorder path (≤60fps, 8-bit)
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        // Configure MediaRecorder
        val surface = runCatching {
            if (hasAudio) {
                mr.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            }
            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setOutputFile(pfd.fileDescriptor)
            // Sprint 13.2: Use videoFormat for encoder and bitrate
            mr.setVideoEncoder(videoFormat.getMediaRecorderVideoEncoder())
            mr.setVideoSize(videoFormat.resolution.width, videoFormat.resolution.height)
            mr.setVideoFrameRate(targetFps)
            mr.setVideoEncodingBitRate(videoFormat.bitrate)
            mr.setOrientationHint(orientationHintDegrees)

            if (hasAudio) {
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mr.setAudioEncodingBitRate(128_000)
                mr.setAudioSamplingRate(48_000)
            }

            mr.prepare()
            mr.surface
        }.getOrElse { e ->
            Log.w(TAG, "MediaRecorder prepare failed", e)
            runCatching { mr.reset() }
            runCatching { mr.release() }
            runCatching { pfd.close() }
            CaptureStorage.discardPendingVideo(appContext, uri)
            startFailureHold = true
            mainHandler.post { onEvent(Event.StartFailed) }
            return PrepareResult.Rejected("prepare failed: ${e.message}")
        }

        // Store state
        mediaRecorder = mr
        recordingSurface = surface
        recordingUri = uri
        recordingPfd = pfd
        recorderStarted = false

        Log.i(
            TAG,
            "inAppVideoPrepared audioEnabled=$hasAudio size=${videoFormat.resolution.width}x${videoFormat.resolution.height} fps=$targetFps bitrate=${videoFormat.bitrate} codec=${videoFormat.getLabel()} tenBit=${videoFormat.isTenBit}"
        )

        // Caller must rebuild session with this surface, then call maybeStartRecorder()
        return PrepareResult.Ready(surface)
    }

    /**
     * Prepare the [MediaCodecVideoRecorder] path for HFR / 10-bit recording.
     * Bypasses [MediaRecorder]'s `ro.media.recorder-max-base-layer-fps=60` system cap.
     */
    private fun prepareMediaCodecPath(
        uri: android.net.Uri,
        pfd: ParcelFileDescriptor,
        videoFormat: VideoFormat,
        targetFps: Int,
        size: android.util.Size,
        hasAudio: Boolean,
        onEvent: (Event) -> Unit,
    ): PrepareResult {
        val hdrProfile = when {
            videoFormat.isDcg -> android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
            videoFormat.isTenBit -> android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            else -> 0
        }
        val isHdr10 = videoFormat.isDcg ||
            hdrProfile == android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
        val config = MediaCodecVideoRecorder.Config(
            width = videoFormat.resolution.width,
            height = videoFormat.resolution.height,
            fps = targetFps,
            bitrate = videoFormat.bitrate,
            isTenBit = videoFormat.isTenBit,
            hdrProfile = hdrProfile,
            isHdr10 = isHdr10,
            maxCll = 1000,
            maxFall = 400,
        )
        val rec = MediaCodecVideoRecorder(appContext, mainHandler)
        val surface = rec.prepare(config, uri, pfd)
        if (surface == null) {
            Log.e(TAG, "MediaCodecVideoRecorder.prepare returned null surface")
            startFailureHold = true
            mainHandler.post { onEvent(Event.StartFailed) }
            return PrepareResult.Rejected("MediaCodec prepare failed")
        }
        mcRecorder = rec
        recordingSurface = surface
        recordingUri = uri
        recorderStarted = false
        Log.i(
            TAG,
            "mcVideoPrepared audioEnabled=$hasAudio size=${size.width}x${size.height} fps=$targetFps " +
                "bitrate=${videoFormat.bitrate} codec=${videoFormat.getLabel()} tenBit=${videoFormat.isTenBit}",
        )
        return PrepareResult.Ready(surface)
    }

    /**
     * Called after preview settles and session is rebuilt with recording surface.
     */
    fun maybeStartRecorder() {
        if (recorderStarted) return
        val mc = mcRecorder
        if (mc != null) {
            recorderStarted = true
            mc.start()
            Log.i(TAG, "MediaCodecVideoRecorder started")
            return
        }
        if (mediaRecorder == null) return
        recorderStarted = true
        runCatching { mediaRecorder?.start() }.onFailure { e ->
            Log.e(TAG, "MediaRecorder start failed", e)
        }
        Log.i(TAG, "MediaRecorder started")
    }

    /**
     * Stop recording and finalize video.
     * For the MediaCodec path, finalization is async; returns null immediately
     * and fires [Event.Stopped] via [mcRecorder] callback.
     */
    private fun stopRecordingLocked(onEvent: (Event) -> Unit): Uri? {
        // MediaCodec path
        val mc = mcRecorder
        if (mc != null) {
            val wasAudioEnabled = audioEnabled
            mc.stop { savedUri ->
                mcRecorder = null
                recordingSurface = null
                recordingUri = null
                recorderStarted = false
                audioEnabled = false
                lastShellWant = false
                onEvent(Event.Stopped(savedUri, wasAudioEnabled))
            }
            return null
        }

        // MediaRecorder path
        val mr = mediaRecorder ?: return null
        val uri = recordingUri
        val pfd = recordingPfd

        runCatching {
            mr.stop()
            Log.i(TAG, "MediaRecorder stopped")
        }.onFailure { e ->
            Log.w(TAG, "MediaRecorder stop failed (may be expected if not started)", e)
        }

        runCatching { mr.reset() }
        runCatching { mr.release() }
        runCatching { pfd?.close() }

        mediaRecorder = null
        recordingSurface = null
        recordingUri = null
        recordingPfd = null
        recorderStarted = false

        var out: Uri? = null
        if (uri != null) {
            runCatching {
                CaptureStorage.finalizePendingVideoInsert(appContext, uri)
                out = uri
                Log.i(TAG, "inAppVideoSaved uri=$uri")
            }.onFailure { e ->
                Log.w(TAG, "finalize video failed", e)
                runCatching { CaptureStorage.discardPendingVideo(appContext, uri) }
            }
        }

        return out
    }

    /**
     * Tear down recording when camera is closing.
     * Discards partial recording.
     */
    fun tearDownForCloseCamera() {
        val mc = mcRecorder
        if (mc != null) {
            Log.i(TAG, "tearDownForCloseCamera - aborting MediaCodec recorder")
            mc.abort()
            mcRecorder = null
            val uri = recordingUri
            if (uri != null) runCatching { CaptureStorage.discardPendingVideo(appContext, uri) }
            recordingSurface = null
            recordingUri = null
            recorderStarted = false
            audioEnabled = false
            return
        }

        val mr = mediaRecorder ?: return
        val uri = recordingUri
        val pfd = recordingPfd

        Log.i(TAG, "tearDownForCloseCamera - discarding partial MediaRecorder recording")

        runCatching { mr.stop() }
        runCatching { mr.reset() }
        runCatching { mr.release() }
        runCatching { pfd?.close() }

        if (uri != null) {
            runCatching { CaptureStorage.discardPendingVideo(appContext, uri) }
        }

        mediaRecorder = null
        recordingSurface = null
        recordingUri = null
        recordingPfd = null
        recorderStarted = false
        audioEnabled = false
    }

    /**
     * Calculate video bitrate based on resolution and codec.
     * Delegates to [VideoFormatPresets.calculateBitrate] for the probe-validated table
     * (4K@120: 120 Mbps, 4K@60: 80 Mbps, 1080p: 50 Mbps max, etc.).
     * [baseBitrate] is ignored when [codec] is provided; kept for MediaRecorder fallback path.
     */
    @Suppress("MagicNumber")
    fun bitrateForSize(
        width: Int,
        height: Int,
        fps: Int = 30,
        codec: VideoCodec = VideoCodec.H265,
        baseBitrate: Int = IN_APP_VIDEO_RECORD_BITRATE,
    ): Int {
        val base = VideoFormatPresets.calculateBitrate(width, height, fps, codec)
        val scale =
            HudSettings.load(appContext).videoBitrateScalePercent.coerceIn(
                HudSettings.VIDEO_BITRATE_SCALE_MIN,
                HudSettings.VIDEO_BITRATE_SCALE_MAX,
            )
        return (base.toLong() * scale / 100L).toInt().coerceAtLeast(2_000_000)
    }
}
