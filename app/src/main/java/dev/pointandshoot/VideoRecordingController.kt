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
 * - **[MediaRecorder] path**: 8-bit **H.264** up to 60 fps (bounded by
 *   `ro.media.recorder-max-base-layer-fps=60` on legacy-class devices).
 * - **[MediaCodecVideoRecorder] path**: Surface-input MediaCodec encoder that bypasses the
 *   60 fps MediaRecorder cap. Used for 8-bit **HEVC** (Sprint **15.2** BT.709 VUI), HFR,
 *   [VideoFormat.isTenBit], DCG, and AV1. Drives `c2.qti.hevc.encoder`
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
        private const val MAX_FPS = 480 // HFR support up to 480fps (legacy target)
        
        /** HFR threshold - FPS above this requires CameraConstrainedHighSpeedCaptureSession. */
        const val HFR_THRESHOLD_FPS = 120

        /**
         * True when the UI must not offer this codec at [fps] as honest HFR.
         *
         * **Legacy-class (May 2026 USB):** Constrained HS + Qualcomm HEVC (`c2.qti.hevc.encoder`,
         * including Main10 surface input for 8-bit HFR) delivers about **half** the target unique
         * frame rate (e.g. ~60 unique/s at a 120 fps HS target). Container fps / mux PTS can still
         * look correct — that is not the same as true HFR.
         *
         * **Honest HFR in-app today:** [VideoCodec.H264] via `c2.qti.avc.encoder` when the camera HS
         * table supports the tier.
         *
         * **[VideoCodec.AV1]:** hide at HFR until a fleet device shows **hardware** AV1 (e.g.
         * `c2.qti.av1.encoder`) that records at the target fps with verified unique frames. On
         * Legacy target (May 2026) only `c2.android.av1.encoder` is listed; forcing 120 fps
         * fails `MediaCodec.createByCodecName(c2.qti.av1.encoder)` with NAME_NOT_FOUND.
         */
        fun lacksTrueHfrUniqueFrames(fps: Int, codec: VideoCodec): Boolean {
            if (fps < HFR_THRESHOLD_FPS) return false
            return when (codec) {
                VideoCodec.H264 -> false
                VideoCodec.AV1, VideoCodec.VP9 -> true
                VideoCodec.H265,
                VideoCodec.H265_10BIT,
                VideoCodec.DCG,
                -> true
            }
        }
    }

    // State assigned from [handler] thread only
    @Volatile private var mediaRecorder: MediaRecorder? = null
    private var mcRecorder: MediaCodecVideoRecorder? = null
    private var recordingSurface: Surface? = null
    private var recordingUri: Uri? = null
    private var recordingPfd: ParcelFileDescriptor? = null
    private var recordingCaptureInfo: VideoCaptureMetadata.CaptureInfo? = null
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
     * Encoder-only record (HFR HS or UHD60 REGULAR): defer [awaitMuxerReady] off the camera
     * handler so [startRepeating] can run in the same [onConfigured] callback.
     */
    @Volatile private var encoderOnlyMcRecordHint: Boolean = false

    fun hintEncoderOnlyMcRecord(active: Boolean) {
        encoderOnlyMcRecordHint = active
    }

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
    /** True while a recorder exists (including MediaCodec stop/finalize in flight). */
    fun isRecorderPresent(): Boolean = mediaRecorder != null || mcRecorder != null

    /** True after [maybeStartRecorder] / MediaRecorder.start (not merely prepared). */
    fun isRecorderStarted(): Boolean = recorderStarted

    /** MediaCodec encoder actively accepting GL/composite frames (may lag [recorderStarted] on dual rebuild). */
    fun isMcEncoderRecording(): Boolean = mcRecorder?.isRecording == true

    /** MediaCodec path: true once [MediaMuxer] has started (first encoder output format). */
    fun isMuxerReadyForRecord(): Boolean =
        mcRecorder?.isMuxerReady() == true || mediaRecorder != null

    fun hasMcStartupStall(minUptimeMs: Long = 8_000L): Boolean =
        mcRecorder?.isStartupStalled(minUptimeMs) == true

    fun peekMcStartupDiag(): String = mcRecorder?.startupDiagSummary() ?: "mc=absent"

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

    /** L/R peaks for pillar stereo meters; mono paths duplicate the same level on both channels. */
    fun peekAudioAmplitudeStereo(): Pair<Int, Int> {
        val mc = mcRecorder?.peekAmplitudeStereo()
        if (mc != null) return mc
        val mono = mediaRecorder?.maxAmplitude ?: 0
        return mono to mono
    }

    fun peekMcVideoSamplesWritten(): Long = mcRecorder?.peekVideoSamplesWritten() ?: 0L

    private fun targetFpsForMcWait(): Int = recordingCaptureInfo?.captureFps ?: 0

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
        /** Dual video feeds encoder via [DualVideoGlEncoderSink] — use MediaCodec Surface input. */
        forceMediaCodecGlComposite: Boolean = false,
        videoColorProfile: VideoColorProfile = VideoColorProfile.Sdr,
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
            applyShellLocked(
                wantRecord,
                profile,
                desiredFps,
                size,
                orientationHintDegrees,
                wantHighSpeed,
                supportsHighSpeed,
                videoFormat,
                onEvent,
                forceMediaCodecGlComposite,
                videoColorProfile,
            )
        } else {
            var result: PrepareResult = PrepareResult.NoAction
            h.post {
                result = applyShellLocked(
                    wantRecord,
                    profile,
                    desiredFps,
                    size,
                    orientationHintDegrees,
                    wantHighSpeed,
                    supportsHighSpeed,
                    videoFormat,
                    onEvent,
                    forceMediaCodecGlComposite,
                    videoColorProfile,
                )
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
        forceMediaCodecGlComposite: Boolean,
        videoColorProfile: VideoColorProfile,
    ): PrepareResult {
        // Debounce duplicate states
        if (lastShellWant == wantRecord && !startFailureHold) {
            return PrepareResult.NoAction
        }
        lastShellWant = wantRecord

        if (!wantRecord) {
            // Stop recording
            startFailureHold = false
            encoderOnlyMcRecordHint = false
            // Keep [wantsMediaCodecPath] — Compose [hintInAppVideoMediaCodecPath] owns the HFR hint.
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
        // Sprint **15.2**: 8-bit HEVC ≤60 fps via MediaCodec so BT.709 limited VUI is set on
        // MediaFormat (MediaRecorder has no reliable color-metadata API on this fleet).
        val useHevcMediaCodecForSdrColor =
            videoFormat.codec == VideoCodec.H265 && !videoFormat.isTenBit && !videoFormat.isDcg
        // Sprint **15.4**: 8K is not reliable on MediaRecorder (corrupt / moov-less MP4 observed on legacy target);
        // route 8K through MediaCodec so muxer setup and track finalization are explicit.
        val useMediaCodecFor8k = videoFormat.resolution.width >= 7680 || videoFormat.resolution.height >= 4320
        val ultraHd60H264Mr =
            !isHfr &&
                desiredFps == UltraHd60RecordSupport.TARGET_FPS &&
                size.width >= UltraHd60RecordSupport.UHD_WIDTH &&
                videoFormat.codec == VideoCodec.H264 &&
                !videoFormat.isTenBit &&
                !videoFormat.isDcg
        val useMediaCodecForHlg =
            videoColorProfile == VideoColorProfile.Hlg &&
                !videoFormat.isDcg &&
                !ultraHd60H264Mr
        val useMediaCodecPath =
            forceMediaCodecGlComposite ||
                isHfr ||
                videoFormat.isTenBit ||
                videoFormat.isDcg ||
                videoFormat.codec == VideoCodec.AV1 ||
                useHevcMediaCodecForSdrColor ||
                useMediaCodecFor8k ||
                useMediaCodecForHlg
        if (ultraHd60H264Mr && !useMediaCodecPath) {
            Log.i(TAG, "UHD60: MediaRecorder path (HAL 4K@60 via MR output class)")
        }
        // Signal immediately so createSession can skip useHighSpeed before recorder is prepared
        wantsMediaCodecPath = useMediaCodecPath
        if (forceMediaCodecGlComposite) {
            Log.i(TAG, "dualVideo: MediaCodec GL composite encoder path")
        }
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

        val videoKind =
            if (videoFormat.codec == VideoCodec.AV1) {
                // CPH2583: MediaMuxer WebM rejects `video/av01` track add despite encoder output.
                // Route AV1 through MP4 muxing for in-app parity proof stability.
                CaptureStorage.CaptureKind.Mp4
            } else {
                CaptureStorage.CaptureKind.Mp4
            }

        // Open video output
        val pair = runCatching {
            CaptureStorage.openVideoOutputReadWritePfd(appContext, profile, kind = videoKind)
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
        val chrome = PreviewChromePreferences.load(appContext)
        val audioSource = HudSettings.load(appContext).videoAudioSourceEnum()
        val audioProfile = PnsAudioCaptureSupport.resolve(appContext, chrome, audioSource)
        AudioEffects.lightCompressionEnabled = chrome.audioLightCompression
        AudioEffects.voiceoverDuckingEnabled = chrome.audioVoiceoverDucking
        val audioMetaSampleRateHz =
            if (!hasAudio) {
                null
            } else if (audioProfile.hiFiMode) {
                PnsAacEncoderSupport.maxHiFiMuxSampleRateHz(appContext)
            } else {
                audioProfile.sampleRateHz
            }
        if (hasAudio) {
            PnsAudioCaptureSupport.logInputDevices(appContext)
            Log.i(TAG, "videoAudioProfile audioSource=${audioSource.logTag()} ${PnsAudioCaptureSupport.diagSummary(audioProfile)}")
            PnsAdbLog.i(appContext, "videoAudioProfile audioSource=${audioSource.logTag()} ${PnsAudioCaptureSupport.diagSummary(audioProfile)}")
        }
        recordingCaptureInfo =
            VideoCaptureMetadata.CaptureInfo(
                captureFps = targetFps,
                codecLabel = videoFormat.getLabel(),
                mimeType = videoKind.mimeType,
                audioSampleRateHz = audioMetaSampleRateHz,
                audioAacBitrateBps = if (hasAudio) audioProfile.aacBitrateBps else null,
                audioChannelCount = if (hasAudio) audioProfile.channelCount else null,
                audioHiFi = hasAudio && audioProfile.hiFiMode,
                audioWindNoiseReduction = hasAudio && audioProfile.windNoiseSuppression,
            )

        // MediaCodec path for HFR (>60fps) and 10-bit — bypasses ro.media.recorder-max-base-layer-fps=60
        if (useMediaCodecPath) {
            return prepareMediaCodecPath(
                uri,
                pfd,
                videoFormat,
                targetFps,
                size,
                hasAudio,
                orientationHintDegrees,
                videoColorProfile,
                onEvent,
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
                mr.setAudioSource(audioSource.toMediaRecorderSource())
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
                mr.setAudioEncodingBitRate(audioProfile.aacBitrateBps)
                mr.setAudioSamplingRate(audioProfile.sampleRateHz)
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

        val colorVui =
            when (videoFormat.codec) {
                VideoCodec.H265,
                VideoCodec.H265_10BIT,
                VideoCodec.H264,
                -> "bt709-limited-mediarecorder"
                else -> "default-mediarecorder"
            }
        Log.i(
            TAG,
            "inAppVideoPrepared audioEnabled=$hasAudio size=${videoFormat.resolution.width}x${videoFormat.resolution.height} fps=$targetFps bitrate=${videoFormat.bitrate} codec=${videoFormat.getLabel()} tenBit=${videoFormat.isTenBit} colorVui=$colorVui",
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
        orientationHintDegrees: Int,
        videoColorProfile: VideoColorProfile,
        onEvent: (Event) -> Unit,
    ): PrepareResult {
        val hdrProfile = when {
            videoFormat.isDcg -> android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
            videoFormat.isTenBit -> android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            videoColorProfile == VideoColorProfile.Hlg -> android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            else -> 0
        }
        val isHdr10 = videoFormat.isDcg ||
            hdrProfile == android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
        val encoderKind =
            when (videoFormat.codec) {
                VideoCodec.AV1 -> MediaCodecVideoRecorder.VideoEncoderKind.AV1
                VideoCodec.VP9 -> MediaCodecVideoRecorder.VideoEncoderKind.VP9
                VideoCodec.H264 -> MediaCodecVideoRecorder.VideoEncoderKind.H264
                else -> MediaCodecVideoRecorder.VideoEncoderKind.HEVC
            }
        // Encoder input must match constrained HS capture size ([size]), not chrome/catalog alone.
        val encodeW = size.width
        val encodeH = size.height
        val encodeBitrate = bitrateForSize(encodeW, encodeH, targetFps, videoFormat.codec)
        if (videoFormat.resolution.width != encodeW || videoFormat.resolution.height != encodeH) {
            Log.i(
                TAG,
                "mcEncoderSize aligned catalog=${videoFormat.resolution.width}x${videoFormat.resolution.height} " +
                    "hsSession=${encodeW}x$encodeH fps=$targetFps",
            )
        }
        // Legacy-class HS interleaved feeds TP10_UBWC to the encoder surface even when the UI
        // format is 8-bit HEVC; Main10 encoder input avoids QC2 "unsupported TP10 vs NV12" stalls.
        val mcTenBitSurfaceInput =
            targetFps >= 120 &&
                !videoFormat.isTenBit &&
                !videoFormat.isDcg &&
                videoFormat.codec != VideoCodec.H264 &&
                videoFormat.codec != VideoCodec.AV1
        if (mcTenBitSurfaceInput) {
            Log.i(TAG, "HFR: Main10 encoder surface input (HAL TP10_UBWC on interleaved HS)")
        }
        val encodeTenBit =
            videoFormat.isTenBit ||
                mcTenBitSurfaceInput ||
                (videoColorProfile == VideoColorProfile.Hlg && !videoFormat.isDcg)
        val hud = HudSettings.load(appContext)
        if (hud.anamorphicDesqueezeEnabled) {
            AnamorphicVideoMetadata.logApply(
                AnamorphicVideoMetadata.fromSqueezeFactor(hud.anamorphicSqueezeFactor),
            )
        }
        val audioSource = hud.videoAudioSourceEnum()
        val audioGainDb = hud.audioGainDb
        val config = MediaCodecVideoRecorder.Config(
            width = encodeW,
            height = encodeH,
            fps = targetFps,
            bitrate = encodeBitrate,
            isTenBit = encodeTenBit,
            hdrProfile = hdrProfile,
            isHdr10 = isHdr10,
            maxCll = 1000,
            maxFall = 400,
            encoderKind = encoderKind,
            orientationHintDegrees = orientationHintDegrees,
            videoColorProfile = videoColorProfile,
            audioSource = audioSource,
            audioGainDb = audioGainDb,
        )
        val rec = MediaCodecVideoRecorder(appContext, mainHandler)
        val surface = rec.prepare(config, uri, pfd, recordingCaptureInfo)
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
        val colorVui = MediaCodecVideoRecorder.colorVuiTagForConfig(config)
        Log.i(
            TAG,
            "mcVideoPrepared audioEnabled=$hasAudio audioSource=${audioSource.logTag()} size=${encodeW}x$encodeH fps=$targetFps " +
                "bitrate=$encodeBitrate codec=${videoFormat.getLabel()} tenBit=$encodeTenBit " +
                "colorProfile=${videoColorProfile.storageId} colorVui=$colorVui",
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
            if (audioEnabled) {
                AudioEffects.requestVoiceoverDuck(appContext)
                AudioEffects.logPostProcessState()
                SpatialAudio.diag(appContext)
            }
            mc.start()
            val deferMuxWait =
                wantsMediaCodecPath &&
                    (targetFpsForMcWait() >= HFR_THRESHOLD_FPS || encoderOnlyMcRecordHint)
            val muxReady =
                if (deferMuxWait) {
                    // HS / encoder-only burst starts after [maybeStartRecorder]; do not block session callback.
                    val waitMs = if (targetFpsForMcWait() >= HFR_THRESHOLD_FPS) 20_000L else 8_000L
                    mainHandler.post {
                        val ready = mc.awaitMuxerReady(waitMs)
                        val diag = mc.startupDiagSummary()
                        Log.i(
                            TAG,
                            "MediaCodec muxReady(deferred)=$ready encoderOnly=$encoderOnlyMcRecordHint " +
                                "fps=${targetFpsForMcWait()} $diag",
                        )
                        PnsAdbLog.i(
                            appContext,
                            "mcMuxWait deferredReady=$ready encoderOnly=$encoderOnlyMcRecordHint " +
                                "fps=${targetFpsForMcWait()} $diag",
                        )
                    }
                    false
                } else {
                    mc.awaitMuxerReady(5_000L).also { ready ->
                        if (!ready) {
                            Log.w(TAG, "MediaCodec muxer not ready within 5s after start")
                        }
                    }
                }
            Log.i(TAG, "MediaCodecVideoRecorder started muxReady=$muxReady deferMuxWait=$deferMuxWait")
            Log.i(
                "PNS.ChromeUx",
                "videoRecordMcHfr interleavedPreviewRecord=true livePreview=hfrInterleaved muxReady=$muxReady",
            )
            return
        }
        if (mediaRecorder == null) return
        recorderStarted = true
        if (audioEnabled) {
            AudioEffects.requestVoiceoverDuck(appContext)
            AudioEffects.logPostProcessState()
            SpatialAudio.diag(appContext)
        }
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
                AudioEffects.abandonVoiceoverDuck(appContext)
                mcRecorder = null
                recordingSurface = null
                recordingUri = null
                recorderStarted = false
                audioEnabled = false
                recordingCaptureInfo = null
                lastShellWant = false
                logAdbInAppVideoSaved(savedUri)
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
        AudioEffects.abandonVoiceoverDuck(appContext)

        mediaRecorder = null
        recordingSurface = null
        recordingUri = null
        recordingPfd = null
        recorderStarted = false

        var out: Uri? = null
        if (uri != null) {
            runCatching {
                val meta =
                    VideoCaptureMetadata.reconcileCaptureInfoWithMeasured(
                        appContext,
                        uri,
                        recordingCaptureInfo,
                    )
                CaptureStorage.finalizePendingVideoInsert(appContext, uri, meta)
                recordingCaptureInfo = null
                out = uri
                Log.i(TAG, "inAppVideoSaved uri=$uri")
                CloudCaptureBackup.queueUri(appContext, uri)
                logAdbInAppVideoSaved(uri)
            }.onFailure { e ->
                Log.w(TAG, "finalize video failed", e)
                runCatching { CaptureStorage.discardPendingVideo(appContext, uri) }
                logAdbInAppVideoSaved(null)
            }
        } else {
            logAdbInAppVideoSaved(null)
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

    private fun logAdbInAppVideoSaved(uri: Uri?) {
        if (uri == null) {
            PnsAdbLog.i(appContext, "inAppVideoSaved ok=false bytes=-1")
            return
        }
        val bytes = resolveSavedVideoBytes(uri)
        PnsAdbLog.i(appContext, "inAppVideoSaved ok=true bytes=$bytes saved=$uri")
    }

    private fun resolveSavedVideoBytes(uri: Uri): Long {
        runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.Video.Media.SIZE),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
            }
        }
        return runCatching {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                if (pfd.statSize > 0L) pfd.statSize else null
            }
        }.getOrNull() ?: -1L
    }
}
