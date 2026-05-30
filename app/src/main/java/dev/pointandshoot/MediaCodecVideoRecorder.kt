package dev.pointandshoot

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import kotlin.math.roundToInt
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * MediaCodec-based video recorder that accepts a Camera2 [Surface] input.
 *
 * **Why this exists:** Android's [android.media.MediaRecorder] is bounded by the read-only system
 * property `ro.media.recorder-max-base-layer-fps` (set to 60 on CPH2655-class devices), which
 * silently clamps [MediaRecorder.setVideoFrameRate] regardless of hardware capability. The
 * underlying Qualcomm codec `c2.qti.hevc.encoder` supports:
 *   - frame-rate-range  : 1–480 fps
 *   - profiles          : Main / Main10 / Main10HDR10 / Main10HDR10Plus
 *   - color formats     : YUVP010 (10-bit), Surface (opaque), YUV420SemiPlanar
 *   - performance-point : 1080p @ 240 fps, 4K @ 120 fps
 *
 * By driving [MediaCodec] directly in [MediaCodec.CONFIGURE_FLAG_ENCODE] mode with a Surface
 * input, the 60 fps cap is bypassed entirely — the codec sees raw frames from Camera2 at whatever
 * rate the HAL produces them.
 *
 * ## Usage
 * ```kotlin
 * val recorder = MediaCodecVideoRecorder(appContext, handler)
 * val surface = recorder.prepare(config, pfd) ?: return // surface goes into Camera2 session
 * recorder.start()
 * // … recording …
 * recorder.stop { uri -> /* saved */ }
 * ```
 *
 * Thread-safety: [prepare], [start], [stop], [abort] may be called from any thread; they post
 * internally to an internal encoder thread.
 */
class MediaCodecVideoRecorder(
    private val appContext: android.content.Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {

    enum class VideoEncoderKind {
        H264,
        HEVC,
        AV1,
        VP9,
    }

    companion object {
        private const val TAG = "PNS.MCVideoRec"

        /** Qualcomm HW HEVC encoder name — preferred for 10-bit and HFR. */
        private const val QTI_HEVC_ENCODER = "c2.qti.hevc.encoder"

        /** Qualcomm HW HEVC HDR encoder — supports Main10HDR10Plus, slightly lower FPS ceiling. */
        private const val QTI_HEVC_HDR_ENCODER = "c2.qti.hevc.encoder.hdr"

        /** Fallback SW HEVC encoder — 8-bit Main only, low performance. */
        private const val ANDROID_HEVC_ENCODER = "c2.android.hevc.encoder"

        /** Qualcomm HW AVC encoder — HFR performance-points on OP13-class devices. */
        private const val QTI_AVC_ENCODER = "c2.qti.avc.encoder"

        /** Fallback SW AVC encoder. */
        private const val ANDROID_AVC_ENCODER = "c2.android.avc.encoder"

        /** Qualcomm HW AV1 encoder (Sprint **VF.1**). */
        private const val QTI_AV1_ENCODER = "c2.qti.av1.encoder"

        /** AOSP SW AV1 encoder fallback. */
        private const val ANDROID_AV1_ENCODER = "c2.android.av1.encoder"

        /** AOSP SW VP9 encoder (WebM). */
        private const val ANDROID_VP9_ENCODER = "c2.android.vp9.encoder"

        /** AAC-LC samples per encoded access unit. */
        private const val AAC_SAMPLES_PER_FRAME = 1024L

        /**
         * Pick the best available encoder for [config].
         * HEVC priority: QTI main (480 fps, 10-bit) > QTI HDR (120 fps, 10-bit) > AOSP SW.
         * AV1: QTI HW > AOSP SW (first MIME_AV1 match).
         */
        fun pickEncoder(config: Config): String {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            val all = list.codecInfos.filter { it.isEncoder && !it.isAlias }
                .map { it.name }
            if (config.encoderKind == VideoEncoderKind.AV1) {
                val av1Names =
                    list.codecInfos
                        .filter { it.isEncoder && !it.isAlias && MediaFormat.MIMETYPE_VIDEO_AV1 in it.supportedTypes }
                        .map { it.name }
                val qti = av1Names.firstOrNull { it.contains("qti", ignoreCase = true) }
                if (config.fps >= 120) {
                    // Do not fall back to a hardcoded QTI name — on many devices only SW AV1 exists.
                    return qti ?: av1Names.firstOrNull() ?: ANDROID_AV1_ENCODER
                }
                return qti
                    ?: av1Names.firstOrNull { it == QTI_AV1_ENCODER }
                    ?: av1Names.firstOrNull { it == ANDROID_AV1_ENCODER }
                    ?: av1Names.firstOrNull()
                    ?: QTI_AV1_ENCODER
            }
            if (config.encoderKind == VideoEncoderKind.VP9) {
                val vp9Mime = "video/x-vnd.on2.vp9"
                val vp9Names =
                    list.codecInfos
                        .filter { it.isEncoder && !it.isAlias && vp9Mime in it.supportedTypes }
                        .map { it.name }
                return vp9Names.firstOrNull { it.contains("qti", ignoreCase = true) }
                    ?: vp9Names.firstOrNull { it == ANDROID_VP9_ENCODER }
                    ?: vp9Names.firstOrNull()
                    ?: ANDROID_VP9_ENCODER
            }
            if (config.encoderKind == VideoEncoderKind.H264) {
                val avcNames =
                    list.codecInfos
                        .filter { it.isEncoder && !it.isAlias && MediaFormat.MIMETYPE_VIDEO_AVC in it.supportedTypes }
                        .map { it.name }
                return avcNames.firstOrNull { it.contains("qti", ignoreCase = true) }
                    ?: avcNames.firstOrNull { it == QTI_AVC_ENCODER }
                    ?: avcNames.firstOrNull { it == ANDROID_AVC_ENCODER }
                    ?: avcNames.firstOrNull()
                    ?: QTI_AVC_ENCODER
            }
            return when {
                config.isTenBit || config.fps > 60 -> when {
                    QTI_HEVC_ENCODER in all -> QTI_HEVC_ENCODER
                    QTI_HEVC_HDR_ENCODER in all -> QTI_HEVC_HDR_ENCODER
                    else -> QTI_HEVC_ENCODER
                }
                else -> if (QTI_HEVC_ENCODER in all) QTI_HEVC_ENCODER else ANDROID_HEVC_ENCODER
            }
        }

        fun videoMimeForConfig(config: Config): String =
            when (config.encoderKind) {
                VideoEncoderKind.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
                VideoEncoderKind.AV1 -> MediaFormat.MIMETYPE_VIDEO_AV1
                VideoEncoderKind.VP9 -> "video/x-vnd.on2.vp9"
                VideoEncoderKind.HEVC -> MediaFormat.MIMETYPE_VIDEO_HEVC
            }

        /**
         * Build the 25-byte [MediaFormat.KEY_HDR_STATIC_INFO] ByteBuffer.
         *
         * Layout (Android / CTA-861.3 / SMPTE ST 2086):
         *  byte 0      : type = 0x00 (static metadata type 1)
         *  bytes 1-2   : display primary Rx (little-endian uint16, 50000nit scale)
         *  bytes 3-4   : display primary Ry
         *  bytes 5-6   : display primary Gx
         *  bytes 7-8   : display primary Gy
         *  bytes 9-10  : display primary Bx
         *  bytes 11-12 : display primary By
         *  bytes 13-14 : white point x
         *  bytes 15-16 : white point y
         *  bytes 17-18 : max display mastering luminance (1 nit units, uint16)
         *  bytes 19-20 : min display mastering luminance (0.0001 nit units, uint16)
         *  bytes 21-22 : MaxCLL (nits, uint16)
         *  bytes 23-24 : MaxFALL (nits, uint16)
         *
         * Uses P3-D65 display primaries and D65 white point as a safe default when actual
         * mastering display metadata is unavailable.
         */
        @Suppress("MagicNumber")
        fun buildHdrStaticInfo(maxCll: Int, maxFall: Int): ByteBuffer {
            val buf = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
            buf.put(0x00.toByte())               // type
            // P3-D65 primaries (×50000): R(0.680,0.320) G(0.265,0.690) B(0.150,0.060)
            buf.putShort(34000.toShort())         // Rx
            buf.putShort(16000.toShort())         // Ry
            buf.putShort(13250.toShort())         // Gx
            buf.putShort(34500.toShort())         // Gy
            buf.putShort(7500.toShort())          // Bx
            buf.putShort(3000.toShort())          // By
            // D65 white point (×50000): (0.3127, 0.3290)
            buf.putShort(15635.toShort())         // Wx
            buf.putShort(16450.toShort())         // Wy
            // Display mastering luminance: 1000 nit max, 0.005 nit min (×10000 for min)
            buf.putShort(1000.toShort())          // max display luminance (nits)
            buf.putShort(50.toShort())            // min display luminance (0.0001 nit units → 0.005 nit)
            // MaxCLL / MaxFALL
            buf.putShort(maxCll.coerceIn(0, 65535).toShort())
            buf.putShort(maxFall.coerceIn(0, 65535).toShort())
            buf.rewind()
            return buf
        }

        /**
         * Log tag for [TAG] `colorVui=` (JVM-testable; no [MediaFormat] side effects).
         */
        internal fun colorVuiTagForConfig(config: Config): String {
            val isHdr10Profile =
                config.isHdr10 ||
                    config.hdrProfile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                    config.hdrProfile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
            return when {
                isHdr10Profile -> "bt2020-pq"
                config.videoColorProfile == VideoColorProfile.Hlg && config.isTenBit -> "bt2020-hlg"
                // Surface-input HFR is Camera2 SDR 8-bit; HLG tags on Main10 look wrong in players.
                config.isTenBit -> "bt709"
                else -> "bt709"
            }
        }

        /**
         * Sprint **15.30** — stereo channel mask + PCM encoding on AAC [MediaFormat] (API 28+).
         */
        internal fun applySpatialAudioMetadata(
            format: MediaFormat,
            channelMask: Int = AudioFormat.CHANNEL_IN_STEREO,
            pcmEncoding: Int = AudioFormat.ENCODING_PCM_16BIT,
        ): MediaFormat {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { format.setInteger(MediaFormat.KEY_CHANNEL_MASK, channelMask) }
                runCatching { format.setInteger(MediaFormat.KEY_PCM_ENCODING, pcmEncoding) }
            }
            return format
        }

        /** Log label for [applySpatialAudioMetadata] (JVM-testable). */
        internal fun spatialAudioMetaLogLabel(channelMask: Int): String =
            when (channelMask) {
                AudioFormat.CHANNEL_IN_STEREO -> "stereo"
                AudioFormat.CHANNEL_IN_MONO -> "mono"
                else -> "chMask=$channelMask"
            }

        /**
         * Copy encoder output format and attach spatial metadata before [MediaMuxer.addTrack].
         */
        internal fun enrichAudioMuxerFormat(
            encoderOutput: MediaFormat,
            channelMask: Int,
            pcmEncoding: Int,
        ): MediaFormat =
            applySpatialAudioMetadata(MediaFormat(encoderOutput), channelMask, pcmEncoding)

        /** AV1 / VP9 require WebM mux on API 34+; HEVC/H.264 stay MP4. */
        fun muxerOutputFormatFor(config: Config): Int {
            if (config.encoderKind == VideoEncoderKind.AV1 || config.encoderKind == VideoEncoderKind.VP9) {
                require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    "AV1/VP9 in-app recording requires API 34+ WebM muxer"
                }
                return MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            }
            return MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }

        /**
         * HEVC encoder color VUI for [MediaFormat] (API 29+).
         *
         * **Sprint 14.6:** 8-bit Main uses **BT.709 limited** so HFR HEVC matches the H.264
         * [MediaRecorder] SDR path; 10-bit / HDR keep BT.2020 family tags.
         */
        internal fun applyHevcColorMetadata(format: MediaFormat, config: Config): String {
            val tag = colorVuiTagForConfig(config)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return tag

            val profile =
                when {
                    config.hdrProfile != 0 -> config.hdrProfile
                    config.isTenBit -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                    else -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
                }
            format.setInteger(MediaFormat.KEY_PROFILE, profile)

            when (tag) {
                "bt2020-pq" -> {
                    runCatching {
                        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_ST2084)
                        format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                    }
                    if (config.isHdr10 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        runCatching {
                            format.setByteBuffer(
                                MediaFormat.KEY_HDR_STATIC_INFO,
                                buildHdrStaticInfo(config.maxCll, config.maxFall),
                            )
                        }
                    }
                }
                "bt2020-hlg" -> {
                    runCatching {
                        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_HLG)
                        format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                    }
                }
                "bt709" -> {
                    runCatching {
                        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                        format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                    }
                }
            }
            return tag
        }
    }

    /**
     * Recording configuration.
     *
     * @param width  frame width (must be even)
     * @param height frame height (must be even)
     * @param fps    target frame rate; bypasses MediaRecorder 60-fps cap
     * @param bitrate bits per second
     * @param isTenBit when true: HEVC Main10 profile + YUVP010 surface color hint
     * @param hdrProfile one of [MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10],
     *   [HEVCProfileMain10HDR10], [HEVCProfileMain10HDR10Plus], or 0 for standard 8/10-bit
     * @param isHdr10 when true: use ST2084 (PQ) color transfer and write HDR10 SEI metadata
     * @param maxCll maximum content light level in nits (HDR10 SEI; 0 = unspecified)
     * @param maxFall maximum frame-average light level in nits (HDR10 SEI; 0 = unspecified)
     * @param encoderName explicit codec name; null → [pickEncoder] is called
     */
    data class Config(
        val width: Int,
        val height: Int,
        val fps: Int,
        val bitrate: Int,
        val isTenBit: Boolean = false,
        val hdrProfile: Int = 0,
        val isHdr10: Boolean = false,
        val maxCll: Int = 1000,
        val maxFall: Int = 400,
        val encoderName: String? = null,
        val encoderKind: VideoEncoderKind = VideoEncoderKind.HEVC,
        /** Clockwise rotation for gallery players ([MediaMuxer.setOrientationHint]). */
        val orientationHintDegrees: Int = 0,
        /** Sprint **15.16** — HLG / flat-cine intent (VUI + preview; not DCG/HDR10). */
        val videoColorProfile: VideoColorProfile = VideoColorProfile.Sdr,
        /** Sprint **15.24** — [AudioRecord] / MediaRecorder mic source. */
        val audioSource: VideoAudioSource = VideoAudioSource.Camcorder,
        /** Sprint **15.35** — mic gain in dB applied to PCM before AAC encode. */
        val audioGainDb: Float = 0f,
    ) {
        val effectiveEncoder: String get() = encoderName ?: pickEncoder(this)
        val audioGainLinear: Float get() = HudSettings.audioGainDbToLinear(audioGainDb)
    }

    /** Current recorder state. */
    sealed class State {
        data object Idle : State()
        data class Prepared(val surface: Surface) : State()
        data object Recording : State()
        data object Stopped : State()
    }

    private var state: State = State.Idle
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var muxerStarted = false
    @Volatile private var muxerReadyLatch = CountDownLatch(1)
    private var encoderSurface: Surface? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pendingUri: Uri? = null

    private val encoderThread = HandlerThread("PNS.MCEnc").also { it.start() }
    private val encoderHandler = Handler(encoderThread.looper)
    private val stopping = AtomicBoolean(false)
    private val muxerFinalized = AtomicBoolean(false)
    private val muxerLock = Any()
    private val videoSamplesWritten = java.util.concurrent.atomic.AtomicLong(0)
    private var targetFpsForPts: Int = 30
    private var videoFrameIndex: Long = 0L
    private var videoPtsOriginUs: Long = -1L
    private var lastVideoMuxPtsUs: Long = 0L
    private var recordingStartNano: Long = 0L
    private var audioMuxFrameIndex: Long = 0L
    private var audioPtsOriginUs: Long = -1L
    private var finalizeCaptureInfo: VideoCaptureMetadata.CaptureInfo? = null

    // Audio track fields
    private var audioRecord: AudioRecord? = null
    private var audioCodec: MediaCodec? = null
    private var audioTrack = -1
    private var audioThread: Thread? = null
    private val peakAmplitude = AtomicInteger(0)
    private val peakAmplitudeLeft = AtomicInteger(0)
    private val peakAmplitudeRight = AtomicInteger(0)
    @Volatile private var pendingAudioFormat: MediaFormat? = null
    @Volatile private var audioCaptureStarted = false
    private var audioWindNoiseSuppression = false
    /** PCM / AudioRecord rate (hi-fi may be 96 kHz). */
    private var audioSampleRateHz: Int = 44_100
    private var audioChannelCount: Int = 2
    private var audioChannelMask: Int = AudioFormat.CHANNEL_IN_STEREO
    private var audioPcmEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
    /** PCM multiply from [Config.audioGainDb] (1f = unity). */
    private var audioGainLinear: Float = 1f
    /** AAC encoder output rate from [MediaCodec] format (often 44.1 kHz when capture is 96 kHz). */
    @Volatile private var audioEncoderSampleRateHz: Int = 0

    /** Peak amplitude since last call (0..32767), resets on read. */
    fun peekAmplitude(): Int = peakAmplitude.getAndSet(0)

    /** L/R peak amplitudes since last call (0..32767 each), reset on read. */
    fun peekAmplitudeStereo(): Pair<Int, Int> {
        val left = peakAmplitudeLeft.getAndSet(0)
        val right = peakAmplitudeRight.getAndSet(0)
        return left to right
    }

    /** True after [MediaMuxer.start] and the video track is registered (first encoded frame path). */
    fun isMuxerReady(): Boolean = muxerStarted

    fun peekVideoSamplesWritten(): Long = videoSamplesWritten.get()

    /**
     * Block until the muxer has started or [timeoutMs] elapses. Call after [start] once the
     * Camera2 session is feeding the encoder [Surface] — avoids empty MP4s when
     * [onOutputFormatChanged] would otherwise arrive only at [stop].
     */
    fun awaitMuxerReady(timeoutMs: Long): Boolean {
        if (muxerStarted) return true
        val deadline = SystemClock.uptimeMillis() + timeoutMs.coerceAtLeast(0L)
        while (!muxerStarted && SystemClock.uptimeMillis() < deadline) {
            muxerReadyLatch.await(50L, TimeUnit.MILLISECONDS)
        }
        return muxerStarted
    }

    /**
     * Prepare the encoder. Returns the [Surface] that must be added to the Camera2 session.
     * Returns null if the codec failed to configure.
     */
    fun prepare(
        config: Config,
        uri: Uri,
        pfd: ParcelFileDescriptor,
        captureInfo: VideoCaptureMetadata.CaptureInfo? = null,
    ): Surface? {
        check(state == State.Idle) { "prepare called in state $state" }
        this.pfd = pfd
        this.pendingUri = uri
        this.finalizeCaptureInfo = captureInfo
        muxerReadyLatch = CountDownLatch(1)
        muxerStarted = false
        muxerFinalized.set(false)
        stopping.set(false)
        videoSamplesWritten.set(0)
        targetFpsForPts = config.fps.coerceAtLeast(1)
        videoFrameIndex = 0L
        videoPtsOriginUs = -1L
        lastVideoMuxPtsUs = 0L
        recordingStartNano = 0L
        audioMuxFrameIndex = 0L
        audioPtsOriginUs = -1L
        audioCaptureStarted = false
        audioEncoderSampleRateHz = 0
        audioGainLinear = config.audioGainLinear

        val codecName = config.effectiveEncoder

        val hasAudioPerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPerm) {
            val chrome = PreviewChromePreferences.load(appContext)
            AudioEffects.lightCompressionEnabled = chrome.audioLightCompression
            AudioEffects.voiceoverDuckingEnabled = chrome.audioVoiceoverDucking
            val profile =
                PnsAudioCaptureSupport.resolve(appContext, chrome, config.audioSource)
            PnsAudioCaptureSupport.logInputDevices(appContext)
            Log.i(TAG, "audioSource=${config.audioSource.logTag()}")
            Log.i(
                TAG,
                "audioGainDb=${config.audioGainDb} gainLinear=$audioGainLinear",
            )
            PnsAdbLog.i(
                appContext,
                "audioGainDb=${config.audioGainDb} gainLinear=$audioGainLinear",
            )
            Log.i(TAG, "audioCapturePrepare ${PnsAudioCaptureSupport.diagSummary(profile)}")
            PnsAdbLog.i(appContext, "audioCapturePrepare ${PnsAudioCaptureSupport.diagSummary(profile)}")
            startAudioCapture(profile)
        }

        return runCatching {
            if (config.encoderKind == VideoEncoderKind.AV1 &&
                config.fps >= 120 &&
                !codecName.contains("qti", ignoreCase = true)
            ) {
                error("AV1 HFR requires hardware encoder (qti); got $codecName")
            }
            val encoder = MediaCodec.createByCodecName(codecName)
            val mime = videoMimeForConfig(config)
            val format = MediaFormat.createVideoFormat(mime, config.width, config.height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }
            val colorVui =
                when (config.encoderKind) {
                    VideoEncoderKind.AV1, VideoEncoderKind.VP9, VideoEncoderKind.H264 -> {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                                format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                                format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                            }
                            if (config.encoderKind == VideoEncoderKind.H264 &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            ) {
                                format.setInteger(
                                    MediaFormat.KEY_PROFILE,
                                    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                                )
                            }
                        }
                        "bt709"
                    }
                    VideoEncoderKind.HEVC -> applyHevcColorMetadata(format, config)
                }
            MediaCodecCapabilityProbe.probeSync()?.let { probe ->
                Log.i(
                    TAG,
                    "maxFps8k=${probe.maxFps8k} supports8k=${probe.supports8k} " +
                        "4k_max=${probe.maxFps4k} 1080p_max=${probe.maxFps1080p}",
                )
            }
            Log.i(
                TAG,
                "prepare codec=$codecName mime=$mime size=${config.width}x${config.height} fps=${config.fps} " +
                    "10bit=${config.isTenBit} hdrProfile=${config.hdrProfile} colorVui=$colorVui " +
                    "encoderKind=${config.encoderKind}",
            )
            format.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching { setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, config.fps.toFloat()) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { setInteger(MediaFormat.KEY_PRIORITY, 0) }
                }
            }

            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()

            encoder.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {}

                override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        mc.releaseOutputBuffer(index, false)
                        return
                    }
                    // Drop late frames only after muxer is running and EOS was signaled.
                    if (stopping.get() && muxerStarted &&
                        (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0
                    ) {
                        mc.releaseOutputBuffer(index, false)
                        return
                    }
                    val buf = mc.getOutputBuffer(index)
                    // Start audio on first encoded frame even if muxer is not ready yet (HS prep delay).
                    maybeStartAudioCapture()
                    if (buf != null && muxerStarted && videoTrack >= 0 && info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val muxPtsUs = muxVideoPresentationUs(info.presentationTimeUs)
                        lastVideoMuxPtsUs = muxPtsUs
                        videoFrameIndex++
                        val muxInfo = MediaCodec.BufferInfo()
                        muxInfo.set(info.offset, info.size, muxPtsUs, info.flags)
                        runCatching { muxer?.writeSampleData(videoTrack, buf, muxInfo) }
                        val n = videoSamplesWritten.incrementAndGet()
                        if (n == 1L || n % 120L == 0L) {
                            Log.i(
                                TAG,
                                "mcVideoSample n=$n muxPtsUs=$muxPtsUs rawPtsUs=${info.presentationTimeUs}",
                            )
                        }
                    }
                    mc.releaseOutputBuffer(index, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        finalizeMuxer()
                    }
                }

                override fun onError(mc: MediaCodec, e: MediaCodec.CodecException) {
                    Log.e(TAG, "codec error: ${e.message}")
                }

                override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
                    synchronized(muxerLock) {
                        if (muxerFinalized.get() || muxer == null || muxerStarted) return
                        if (videoTrack >= 0) return
                        val muxFormat = MediaFormat(format).apply {
                            setInteger(MediaFormat.KEY_FRAME_RATE, targetFpsForPts)
                            runCatching { setInteger("capture-fps", targetFpsForPts) }
                        }
                        videoTrack =
                            runCatching { muxer?.addTrack(muxFormat) ?: -1 }.getOrElse { -1 }
                        if (videoTrack >= 0) {
                            Log.i(TAG, "video track added idx=$videoTrack format=$muxFormat")
                        }
                    }
                    nudgeAudioMuxerReady()
                    tryStartMuxerIfReady()
                }
            }, encoderHandler)

            this.codec = encoder
            val muxFormat = muxerOutputFormatFor(config)
            this.muxer = MediaMuxer(pfd.fileDescriptor, muxFormat).also { mux ->
                if (config.orientationHintDegrees != 0) {
                    mux.setOrientationHint(config.orientationHintDegrees)
                }
            }
            Log.i(
                TAG,
                "muxer format=$muxFormat mime=${videoMimeForConfig(config)} " +
                    "orientationHint=${config.orientationHintDegrees}",
            )
            this.encoderSurface = surface
            state = State.Prepared(surface)
            surface
        }.onFailure { e ->
            Log.e(
                TAG,
                "prepare failed codec=${config.effectiveEncoder} mime=${videoMimeForConfig(config)} " +
                    "kind=${config.encoderKind}: ${e.message}",
                e,
            )
        }.getOrNull()
    }

    /** Start encoding. Call after the Camera2 session with the prepared surface is active. */
    fun start() {
        val s = state
        check(s is State.Prepared) { "start called in state $state" }
        stopping.set(false)
        recordingStartNano = System.nanoTime()
        codec?.start()
        // Defer audio encoder + AudioRecord until the first video frame (HS prep can take many seconds).
        state = State.Recording
        Log.i(TAG, "recording started (audio deferred until first video frame)")
    }

    /**
     * Stop recording and finalize the MP4. [onSaved] is called on the main thread with the Uri.
     */
    fun stop(onSaved: (Uri?) -> Unit) {
        if (state != State.Recording) {
            onSaved(null)
            return
        }
        state = State.Stopped
        stopping.set(true)
        Log.i(TAG, "stopping recorder — signaling EOS to encoder surface")
        runCatching { encoderSurface?.let { codec?.signalEndOfInputStream() } }
        this.pendingOnSaved = onSaved
        // If the codec never delivers EOS (configure race / empty HS feed), finalize anyway.
        mainHandler.postDelayed({
            if (pendingOnSaved != null && !muxerFinalized.get()) {
                Log.w(TAG, "finalizeMuxer timeout — forcing teardown")
                finalizeMuxer()
            }
        }, 8_000L)
    }

    /** Hard abort — no finalization. Safe to call from any state. */
    fun abort() {
        stopping.set(true)
        state = State.Stopped
        if (!muxerFinalized.get()) {
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            muxer = null
            muxerStarted = false
            muxerFinalized.set(true)
        }
        pendingUri?.let { runCatching { CaptureStorage.discardPendingVideo(appContext, it) } }
        pendingUri = null
        releaseResources(finalize = false)
    }

    private fun maybeStartAudioCapture() {
        if (audioCaptureStarted) return
        if (audioRecord == null || audioCodec == null) return
        audioCaptureStarted = true
        val ar = audioRecord ?: return
        runCatching { ar.startRecording() }
        PnsAudioCaptureSupport.attachCaptureEffects(ar, audioWindNoiseSuppression)
        startAudioFeedThread()
        nudgeAudioMuxerReady()
        Log.i(TAG, "audio PCM capture started with first video frame")
    }

    private fun nudgeAudioMuxerReady() {
        val ac = audioCodec ?: return
        encoderHandler.post {
            drainAudioEncoder(ac)
            tryStartMuxerIfReady()
        }
    }

    /**
     * [MediaMuxer.addTrack] for all tracks, then [MediaMuxer.start]. Never add tracks after start.
     * Starts video-only when audio format is not ready yet (audio is optional for a valid clip).
     */
    private fun tryStartMuxerIfReady() {
        synchronized(muxerLock) {
            if (muxerFinalized.get() || muxerStarted) return
            val m = muxer ?: return
            if (videoTrack < 0) return
            val wantsAudio = audioCodec != null
            val af = pendingAudioFormat
            if (wantsAudio && audioTrack < 0 && af != null) {
                audioTrack = runCatching { m.addTrack(af) }.getOrElse { -1 }
                if (audioTrack >= 0) {
                    Log.i(TAG, "audio track added idx=$audioTrack")
                }
            }
            // Never start muxer video-only when audio was prepared — HFR lost audio when video
            // format arrived before AAC OUTPUT_FORMAT_CHANGED (tracks cannot be added after start).
            if (wantsAudio && audioTrack < 0) return
            runCatching { m.start() }
                .onFailure { e ->
                    Log.e(TAG, "muxer.start failed: ${e.message}", e)
                    return
                }
            muxerStarted = true
            muxerReadyLatch.countDown()
            Log.i(TAG, "muxer started videoTrack=$videoTrack audioTrack=$audioTrack")
        }
    }

    private fun startAudioCapture(profile: PnsAudioCaptureProfile) {
        val minBufProbe =
            AudioRecord.getMinBufferSize(
                profile.sampleRateHz,
                profile.channelConfig,
                profile.pcmEncoding,
            )
        if (minBufProbe == AudioRecord.ERROR_BAD_VALUE || minBufProbe == AudioRecord.ERROR) return
        val bufSize = maxOf(minBufProbe, 4096)

        val pick =
            PnsAacEncoderSupport.openBestAacEncoder(profile, bufSize) ?: run {
                Log.w(TAG, "audio encoder open failed — recording video-only")
                return
            }
        audioCodec = pick.codec
        audioEncoderSampleRateHz = pick.muxSampleRateHz
        audioSampleRateHz = pick.captureSampleRateHz

        val recordProfile =
            if (pick.captureSampleRateHz == profile.sampleRateHz) {
                profile
            } else {
                Log.w(
                    TAG,
                    "AAC mux=${pick.muxSampleRateHz}Hz — PCM capture aligned from " +
                        "${profile.sampleRateHz}Hz (hiFi=${profile.hiFiMode})",
                )
                profile.copy(sampleRateHz = pick.captureSampleRateHz)
            }
        audioChannelCount = recordProfile.channelCount.coerceAtLeast(1)
        audioChannelMask = recordProfile.channelConfig
        audioPcmEncoding = recordProfile.pcmEncoding
        val minBuf =
            AudioRecord.getMinBufferSize(
                recordProfile.sampleRateHz,
                recordProfile.channelConfig,
                recordProfile.pcmEncoding,
            )
        if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) {
            runCatching { pick.codec.release() }
            audioCodec = null
            return
        }
        val ar =
            PnsAudioCaptureSupport.createAudioRecord(appContext, recordProfile, maxOf(minBuf, 4096))
                ?: run {
                    runCatching { pick.codec.release() }
                    audioCodec = null
                    return
                }
        audioRecord = ar
        audioWindNoiseSuppression = recordProfile.windNoiseSuppression
        Log.i(
            TAG,
            "audioSource=${recordProfile.audioSource.logTag()} " +
                "audio capture prepared ${PnsAudioCaptureSupport.diagSummary(recordProfile)} " +
                "encoder=${pick.codecName} muxRate=${pick.muxSampleRateHz}",
        )
        PnsAdbLog.i(
            appContext,
            "audioEncoderPick name=${pick.codecName} capture=${pick.captureSampleRateHz} " +
                "mux=${pick.muxSampleRateHz} hiFi=${profile.hiFiMode}",
        )
        Log.i(
            TAG,
            "spatialAudioMeta=${spatialAudioMetaLogLabel(recordProfile.channelConfig)} " +
                "pcm=${recordProfile.pcmEncoding}",
        )
        PnsAdbLog.i(
            appContext,
            "spatialAudioMeta=${spatialAudioMetaLogLabel(recordProfile.channelConfig)}",
        )
        primeAudioEncoderForMuxer()
    }

    /** Start AAC encoder early so [tryStartMuxerIfReady] can add the audio track before muxer start (HFR). */
    private fun primeAudioEncoderForMuxer() {
        encoderHandler.post {
            val ac = audioCodec ?: return@post
            runCatching {
                ac.start()
                // One AAC-LC frame @ stereo 16-bit ≈ 4096 bytes; prime with a few frames.
                repeat(6) {
                    val idx = runCatching { ac.dequeueInputBuffer(10_000L) }.getOrDefault(-1)
                    if (idx < 0) return@repeat
                    val ib = ac.getInputBuffer(idx) ?: return@repeat
                    ib.clear()
                    val silentBytes = ByteArray(4096)
                    ib.put(silentBytes)
                    ac.queueInputBuffer(idx, 0, silentBytes.size, 0L, 0)
                    drainAudioEncoder(ac)
                }
                tryStartMuxerIfReady()
                Log.i(TAG, "audio encoder primed for muxer (PCM deferred)")
            }.onFailure { e ->
                Log.w(TAG, "audio encoder prime failed: ${e.message}")
            }
        }
    }

    private fun startAudioFeedThread() {
        val ar = audioRecord ?: return
        audioThread = Thread({
            val buf = ShortArray(2048)
            var presentationUs = 0L
            val sampleRate = audioSampleRateHz
            val channels = audioChannelCount.coerceAtLeast(1)
            while (!stopping.get()) {
                val read = ar.read(buf, 0, buf.size)
                if (read <= 0) continue
                AudioEffects.applyPcmProcessing(buf, read)
                if (audioGainLinear != 1f) {
                    applyPcmGainInPlace(buf, read, audioGainLinear)
                }
                var peak = 0
                var peakL = 0
                var peakR = 0
                if (channels >= 2) {
                    var i = 0
                    while (i + 1 < read) {
                        val l = kotlin.math.abs(buf[i].toInt())
                        val r = kotlin.math.abs(buf[i + 1].toInt())
                        if (l > peakL) peakL = l
                        if (r > peakR) peakR = r
                        if (l > peak) peak = l
                        if (r > peak) peak = r
                        i += 2
                    }
                } else {
                    for (i in 0 until read) {
                        val v = kotlin.math.abs(buf[i].toInt())
                        if (v > peak) peak = v
                        if (v > peakL) peakL = v
                        if (v > peakR) peakR = v
                    }
                }
                peakAmplitude.getAndUpdate { cur -> if (peak > cur) peak else cur }
                peakAmplitudeLeft.getAndUpdate { cur -> if (peakL > cur) peakL else cur }
                peakAmplitudeRight.getAndUpdate { cur -> if (peakR > cur) peakR else cur }
                val pcm = buf.copyOf(read)
                val ptsUs = presentationUs
                // [AudioRecord.read] returns shorts; stereo = 2 shorts per frame — do not treat each short as a sample.
                val framesRead = read / channels
                presentationUs += framesRead * 1_000_000L / sampleRate
                encoderHandler.post { queueAudioPcmToEncoder(pcm, ptsUs) }
            }
            val eosPts = presentationUs
            encoderHandler.post { signalAudioEncoderEndOfStream(eosPts) }
        }, "PNS.MCAudio")
        audioThread?.isDaemon = true
        audioThread?.start()
    }

    private fun queueAudioPcmToEncoder(pcm: ShortArray, presentationUs: Long) {
        if (stopping.get()) return
        val ac = audioCodec ?: return
        val idx = runCatching { ac.dequeueInputBuffer(10_000L) }.getOrDefault(-1)
        if (idx < 0) return
        val ib = ac.getInputBuffer(idx) ?: return
        ib.clear()
        val byteCount = pcm.size * 2
        ib.asShortBuffer().put(pcm)
        ac.queueInputBuffer(idx, 0, byteCount, presentationUs, 0)
        drainAudioEncoder(ac)
    }

    private fun signalAudioEncoderEndOfStream(presentationUs: Long) {
        val ac = audioCodec ?: return
        val idx = runCatching { ac.dequeueInputBuffer(10_000L) }.getOrDefault(-1)
        if (idx >= 0) {
            ac.queueInputBuffer(idx, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainAudioEncoder(ac)
    }

    /** Poll AAC encoder output; call only on [encoderHandler] (never with Callback on same codec). */
    private fun drainAudioEncoder(ac: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = runCatching { ac.dequeueOutputBuffer(info, 0L) }.getOrDefault(-1)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(muxerLock) {
                        if (!muxerFinalized.get() && !muxerStarted && audioTrack < 0) {
                            pendingAudioFormat =
                                enrichAudioMuxerFormat(
                                    ac.outputFormat,
                                    audioChannelMask,
                                    audioPcmEncoding,
                                )
                            val encRate =
                                runCatching {
                                    ac.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                }.getOrDefault(audioSampleRateHz)
                            if (encRate > 0) {
                                audioEncoderSampleRateHz = encRate
                            }
                            if (encRate > 0 && encRate != audioSampleRateHz) {
                                Log.i(
                                    TAG,
                                    "AAC capture=${audioSampleRateHz}Hz encoderOutput=$encRate Hz " +
                                        "(mux PTS uses encoder timestamps, not sample-rate scale)",
                                )
                            }
                            Log.i(
                                TAG,
                                "audio output format ready $pendingAudioFormat " +
                                    "spatialAudioMeta=${spatialAudioMetaLogLabel(audioChannelMask)}",
                            )
                        }
                    }
                    tryStartMuxerIfReady()
                }
                outIdx >= 0 -> {
                    val buf = ac.getOutputBuffer(outIdx)
                    if (buf != null && audioTrack >= 0 && muxerStarted && info.size > 0 &&
                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val muxPtsUs = audioMuxPresentationUs(info.presentationTimeUs)
                        audioMuxFrameIndex++
                        val muxInfo = MediaCodec.BufferInfo()
                        muxInfo.set(info.offset, info.size, muxPtsUs, info.flags)
                        runCatching { muxer?.writeSampleData(audioTrack, buf, muxInfo) }
                    }
                    ac.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
                else -> return
            }
        }
    }

    private var pendingOnSaved: ((Uri?) -> Unit)? = null

    /**
     * Video mux PTS for HFR: uniform capture-rate timeline so MP4 avg_fps / gallery match
     * [targetFpsForPts] when the HAL delivers frames at that rate. Encoder surface timestamps
     * often land on a 60 Hz grid on CPH2655-class devices even at a 120 fps HS target.
     *
     * ≤119 fps: keep encoder PTS for A/V alignment with [MediaRecorder]-class pacing.
     */
    private fun muxVideoPresentationUs(encoderPtsUs: Long): Long {
        if (targetFpsForPts >= 120) {
            val idx = videoSamplesWritten.get()
            return idx * 1_000_000L / targetFpsForPts.coerceAtLeast(1)
        }
        if (encoderPtsUs > 0) {
            if (videoPtsOriginUs < 0) videoPtsOriginUs = encoderPtsUs
            return encoderPtsUs - videoPtsOriginUs
        }
        if (recordingStartNano <= 0L) recordingStartNano = System.nanoTime()
        return (System.nanoTime() - recordingStartNano) / 1_000L
    }

    /**
     * AAC output [MediaCodec.BufferInfo.presentationTimeUs] is already on the mux microsecond
     * timeline (same as queued PCM timestamps). Do not rescale by sample rate — that broke Hi-Fi
     * A/V sync after the 96→48 kHz encoder path landed.
     */
    private fun audioMuxPresentationUs(encoderPtsUs: Long): Long {
        if (encoderPtsUs > 0) {
            if (audioPtsOriginUs < 0) audioPtsOriginUs = encoderPtsUs
            return encoderPtsUs - audioPtsOriginUs
        }
        val muxRate =
            (audioEncoderSampleRateHz.takeIf { it > 0 } ?: audioSampleRateHz).coerceAtLeast(1)
        return audioMuxFrameIndex * AAC_SAMPLES_PER_FRAME * 1_000_000L / muxRate
    }

    private fun finalizeMuxer() {
        if (!muxerFinalized.compareAndSet(false, true)) {
            Log.w(TAG, "finalizeMuxer ignored (already finalized)")
            return
        }
        val frames = videoSamplesWritten.get()
        val durationUs =
            when {
                lastVideoMuxPtsUs > 0L -> lastVideoMuxPtsUs
                frames > 0L && targetFpsForPts > 0 ->
                    (frames - 1) * 1_000_000L / targetFpsForPts.coerceAtLeast(1)
                else -> 0L
            }
        val effectiveFps =
            if (frames > 1L && durationUs > 0L) {
                ((frames - 1) * 1_000_000L / durationUs).toInt().coerceIn(1, 480)
            } else {
                targetFpsForPts
            }
        var savedUri: Uri? = null
        val mux = muxer
        val uri = pendingUri
        val muxAudioRate = audioEncoderSampleRateHz.takeIf { it > 0 }
        val captureInfo =
            finalizeCaptureInfo?.let { info ->
                var out = info
                if (muxAudioRate != null && info.hasAudio) {
                    out = out.copy(audioSampleRateHz = muxAudioRate)
                }
                val galleryFps =
                    when {
                        frames > 1L && effectiveFps > 0 -> effectiveFps
                        else -> info.captureFps
                    }
                if (galleryFps != info.captureFps) {
                    out = out.copy(captureFps = galleryFps)
                }
                if (effectiveFps > 0 && effectiveFps != info.captureFps) {
                    Log.i(
                        TAG,
                        "captureFps metadata gallery=$galleryFps target=${info.captureFps} muxEffective=$effectiveFps " +
                            "frames=$frames durationUs=$durationUs",
                    )
                }
                out
            }
        finalizeCaptureInfo = null
        pendingUri = null

        Log.i(
            TAG,
            "mcVideoFramesWritten=$frames muxDurationSec=${durationUs / 1_000_000.0} " +
                "effectiveFps=$effectiveFps targetFps=$targetFpsForPts",
        )
        PnsAdbLog.i(
            appContext,
            "mcVideoFramesWritten=$frames muxDurationSec=${"%.2f".format(durationUs / 1_000_000.0)} " +
                "effectiveFps=$effectiveFps targetFps=$targetFpsForPts",
        )

        if (frames > 0L && muxerStarted && mux != null) {
            runCatching { mux.stop() }
                .onFailure { e -> Log.w(TAG, "muxer.stop failed: ${e.message}") }
        } else if (uri != null) {
            Log.w(TAG, "discarding empty video (frames=$frames muxerStarted=$muxerStarted)")
            runCatching { CaptureStorage.discardPendingVideo(appContext, uri) }
        }

        runCatching { mux?.release() }
            .onFailure { e -> Log.w(TAG, "muxer.release failed: ${e.message}") }
        muxer = null
        muxerStarted = false

        if (uri != null && frames > 0L) {
            runCatching {
                CaptureStorage.finalizePendingVideoInsert(appContext, uri, captureInfo)
                Log.i(TAG, "inAppVideoSaved uri=$uri")
                savedUri = uri
            }.onFailure { e ->
                Log.w(TAG, "finalizePendingVideoInsert failed: ${e.message}")
                runCatching { CaptureStorage.discardPendingVideo(appContext, uri) }
            }
        }

        releaseResources(finalize = false)

        val cb = pendingOnSaved
        pendingOnSaved = null
        mainHandler.post { cb?.invoke(savedUri) }
    }

    private fun releaseResources(finalize: Boolean) {
        runCatching { audioThread?.join(500) }
        audioThread = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { audioCodec?.stop() }
        runCatching { audioCodec?.release() }
        audioCodec = null
        audioTrack = -1
        pendingAudioFormat = null
        audioEncoderSampleRateHz = 0
        runCatching { codec?.setCallback(null, null) }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { encoderSurface?.release() }
        encoderSurface = null
        if (finalize) {
            runCatching { if (muxerStarted) muxer?.stop(); muxer?.release() }
        }
        muxer = null
        runCatching { pfd?.close() }
        pfd = null
        videoTrack = -1
        muxerStarted = false
    }

    /** Current input [Surface] (valid between [prepare] and [stop]/[abort]). */
    val surface: Surface? get() = (state as? State.Prepared)?.surface ?: encoderSurface

    /** True while [State.Recording]. */
    val isRecording: Boolean get() = state == State.Recording

    /** True while [State.Prepared] or [State.Recording]. */
    val isActive: Boolean get() = state is State.Prepared || state == State.Recording

    private fun applyPcmGainInPlace(buffer: ShortArray, count: Int, gainLinear: Float) {
        if (gainLinear == 1f || count <= 0) return
        for (i in 0 until count) {
            val scaled =
                (buffer[i].toInt() * gainLinear)
                    .toDouble()
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = scaled.toShort()
        }
    }
}
