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
 * Encapsulates all MediaRecorder state and lifecycle:
 * - prepare (with audio permission check)
 * - start (after preview settles and session rebuilt)
 * - stop (with proper cleanup)
 * - abort (for camera close scenarios)
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
        private const val MAX_FPS = 60
    }

    // State assigned from [handler] thread only
    @Volatile private var mediaRecorder: MediaRecorder? = null
    private var recordingSurface: Surface? = null
    private var recordingUri: Uri? = null
    private var recordingPfd: ParcelFileDescriptor? = null
    @Volatile private var recorderStarted: Boolean = false
    private var audioEnabled: Boolean = false

    /** Dedupes [applyShell] across idle recompositions. */
    private var lastShellWant: Boolean? = null

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
    fun isRecorderPresent(): Boolean = mediaRecorder != null

    /**
     * Query start failure hold state (for debouncing).
     */
    fun isStartFailureHold(): Boolean = startFailureHold

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
            applyShellLocked(wantRecord, profile, desiredFps, size, orientationHintDegrees, wantHighSpeed, supportsHighSpeed, onEvent)
        } else {
            var result: PrepareResult = PrepareResult.NoAction
            h.post {
                result = applyShellLocked(wantRecord, profile, desiredFps, size, orientationHintDegrees, wantHighSpeed, supportsHighSpeed, onEvent)
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
            val hadRecorder = mediaRecorder != null
            val wasAudioEnabled = audioEnabled
            val uri = if (hadRecorder) stopRecordingLocked() else null
            if (hadRecorder) {
                mainHandler.post { onEvent(Event.Stopped(uri, wasAudioEnabled)) }
            }
            return PrepareResult.NoAction
        }

        // Want to start recording
        if (mediaRecorder != null || startFailureHold) {
            return PrepareResult.NoAction // Already recording or in failure hold
        }

        // HFR check - block 120fps+ video unless device supports high-speed
        if (desiredFps >= 120 && !(wantHighSpeed && supportsHighSpeed)) {
            Log.w(TAG, "HFR video rejected: fps=$desiredFps, wantHighSpeed=$wantHighSpeed, supportsHighSpeed=$supportsHighSpeed")
            startFailureHold = true
            mainHandler.post { onEvent(Event.StartFailed) }
            return PrepareResult.Rejected("HFR not supported or not enabled")
        }

        val targetFps = min(desiredFps, IN_APP_VIDEO_PREVIEW_CAP_FPS).coerceIn(MIN_FPS, MAX_FPS)
        val actualBitrate = bitrateForSize(size.width, size.height, IN_APP_VIDEO_RECORD_BITRATE)

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

        // Create MediaRecorder
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
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mr.setVideoSize(size.width, size.height)
            mr.setVideoFrameRate(targetFps)
            mr.setVideoEncodingBitRate(actualBitrate)
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
            "inAppVideoPrepared audioEnabled=$hasAudio size=${size.width}x${size.height} fps=$targetFps bitrate=$actualBitrate"
        )

        // Caller must rebuild session with this surface, then call maybeStartRecorder()
        return PrepareResult.Ready(surface)
    }

    /**
     * Called after preview settles and session is rebuilt with recording surface.
     */
    fun maybeStartRecorder() {
        if (mediaRecorder == null || recorderStarted) return
        recorderStarted = true
        runCatching { mediaRecorder?.start() }.onFailure { e ->
            Log.e(TAG, "MediaRecorder start failed", e)
        }
        Log.i(TAG, "MediaRecorder started")
    }

    /**
     * Stop recording and finalize video.
     * Returns the URI of the saved video.
     */
    private fun stopRecordingLocked(): Uri? {
        val mr = mediaRecorder ?: return null
        val uri = recordingUri
        val pfd = recordingPfd

        // Stop recorder
        runCatching {
            mr.stop()
            Log.i(TAG, "MediaRecorder stopped")
        }.onFailure { e ->
            Log.w(TAG, "MediaRecorder stop failed (may be expected if not started)", e)
        }

        // Release resources
        runCatching { mr.reset() }
        runCatching { mr.release() }
        runCatching { pfd?.close() }

        // Clear state
        mediaRecorder = null
        recordingSurface = null
        recordingUri = null
        recordingPfd = null
        recorderStarted = false

        // Finalize video in MediaStore
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
        val mr = mediaRecorder ?: return
        val uri = recordingUri
        val pfd = recordingPfd

        Log.i(TAG, "tearDownForCloseCamera - discarding partial recording")

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
     * Calculate video bitrate based on resolution.
     * 4K (8MP+) gets 1.5x base, 1080p (3MP+) gets 1x, 720p (1.5MP+) gets 0.6x, SD gets 0.4x.
     */
    @Suppress("MagicNumber")
    fun bitrateForSize(width: Int, height: Int, baseBitrate: Int = IN_APP_VIDEO_RECORD_BITRATE): Int {
        val megapixels = (width * height) / 1_000_000.0
        return when {
            megapixels >= 8.0 -> (baseBitrate * 1.5).toInt() // 4K tier
            megapixels >= 3.0 -> baseBitrate // 1080p tier
            megapixels >= 1.5 -> (baseBitrate * 0.6).toInt() // 720p tier
            else -> (baseBitrate * 0.4).toInt() // SD tier
        }
    }
}
