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
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
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

    companion object {
        private const val TAG = "PNS.MCVideoRec"

        /** Qualcomm HW HEVC encoder name — preferred for 10-bit and HFR. */
        private const val QTI_HEVC_ENCODER = "c2.qti.hevc.encoder"

        /** Qualcomm HW HEVC HDR encoder — supports Main10HDR10Plus, slightly lower FPS ceiling. */
        private const val QTI_HEVC_HDR_ENCODER = "c2.qti.hevc.encoder.hdr"

        /** Fallback SW HEVC encoder — 8-bit Main only, low performance. */
        private const val ANDROID_HEVC_ENCODER = "c2.android.hevc.encoder"

        /**
         * Pick the best available HEVC encoder for [config].
         * Priority: QTI main (480 fps, 10-bit) > QTI HDR (120 fps, 10-bit) > AOSP SW.
         */
        fun pickEncoder(config: Config): String {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            val all = list.codecInfos.filter { it.isEncoder && !it.isAlias }
                .map { it.name }
            return when {
                config.isTenBit || config.fps > 60 -> when {
                    QTI_HEVC_ENCODER in all -> QTI_HEVC_ENCODER
                    QTI_HEVC_HDR_ENCODER in all -> QTI_HEVC_HDR_ENCODER
                    else -> QTI_HEVC_ENCODER
                }
                else -> if (QTI_HEVC_ENCODER in all) QTI_HEVC_ENCODER else ANDROID_HEVC_ENCODER
            }
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
    ) {
        val effectiveEncoder: String get() = encoderName ?: pickEncoder(this)
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
    private var encoderSurface: Surface? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pendingUri: Uri? = null

    private val encoderThread = HandlerThread("PNS.MCEnc").also { it.start() }
    private val encoderHandler = Handler(encoderThread.looper)
    private val stopping = AtomicBoolean(false)

    // Audio track fields
    private var audioRecord: AudioRecord? = null
    private var audioCodec: MediaCodec? = null
    private var audioTrack = -1
    private var audioThread: Thread? = null
    private val peakAmplitude = AtomicInteger(0)
    @Volatile private var pendingAudioFormat: MediaFormat? = null

    /** Peak amplitude since last call (0..32767), resets on read. */
    fun peekAmplitude(): Int = peakAmplitude.getAndSet(0)

    /**
     * Prepare the encoder. Returns the [Surface] that must be added to the Camera2 session.
     * Returns null if the codec failed to configure.
     */
    fun prepare(config: Config, uri: Uri, pfd: ParcelFileDescriptor): Surface? {
        check(state == State.Idle) { "prepare called in state $state" }
        this.pfd = pfd
        this.pendingUri = uri

        val codecName = config.effectiveEncoder
        Log.i(TAG, "prepare codec=$codecName size=${config.width}x${config.height} fps=${config.fps} 10bit=${config.isTenBit} hdrProfile=${config.hdrProfile}")

        val hasAudioPerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPerm) startAudioCapture(uri)

        return runCatching {
            val encoder = MediaCodec.createByCodecName(codecName)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, config.width, config.height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

                if (config.isTenBit || config.hdrProfile != 0) {
                    val profile = when {
                        config.hdrProfile != 0 -> config.hdrProfile
                        config.isTenBit -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                        else -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
                    }
                    setInteger(MediaFormat.KEY_PROFILE, profile)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && config.isTenBit) {
                        runCatching {
                            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                            // HDR10 uses ST2084 (PQ); HLG10/standard 10-bit uses HLG
                            val transfer = if (config.isHdr10) {
                                MediaFormat.COLOR_TRANSFER_ST2084
                            } else {
                                MediaFormat.COLOR_TRANSFER_HLG
                            }
                            setInteger(MediaFormat.KEY_COLOR_TRANSFER, transfer)
                            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                        }
                        // HDR10 SEI: MaxCLL / MaxFALL static metadata (fixes QC2GrallocUtils warning)
                        if (config.isHdr10 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            runCatching { setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, buildHdrStaticInfo(config.maxCll, config.maxFall)) }
                        }
                    }
                }

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
                    if (stopping.get() && (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                        mc.releaseOutputBuffer(index, false)
                        return
                    }
                    val buf = mc.getOutputBuffer(index)
                    if (buf != null && muxerStarted && videoTrack >= 0 && info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        runCatching { muxer?.writeSampleData(videoTrack, buf, info) }
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
                    if (!muxerStarted) {
                        videoTrack = muxer?.addTrack(format) ?: -1
                        // Add audio track now if audio format is already known
                        val af = pendingAudioFormat
                        if (af != null && audioTrack < 0) {
                            audioTrack = muxer?.addTrack(af) ?: -1
                            Log.i(TAG, "audio track added at muxer-start idx=$audioTrack")
                        }
                        muxer?.start()
                        muxerStarted = true
                        Log.i(TAG, "muxer started videoTrack=$videoTrack format=$format")
                    }
                }
            }, encoderHandler)

            this.codec = encoder
            this.muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            this.encoderSurface = surface
            state = State.Prepared(surface)
            surface
        }.onFailure { e ->
            Log.e(TAG, "prepare failed: ${e.message}", e)
        }.getOrNull()
    }

    /** Start encoding. Call after the Camera2 session with the prepared surface is active. */
    fun start() {
        val s = state
        check(s is State.Prepared) { "start called in state $state" }
        stopping.set(false)
        codec?.start()
        audioCodec?.start()
        audioRecord?.startRecording()
        startAudioFeedThread()
        state = State.Recording
        Log.i(TAG, "recording started")
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
    }

    /** Hard abort — no finalization. Safe to call from any state. */
    fun abort() {
        stopping.set(true)
        state = State.Stopped
        releaseResources(finalize = false)
    }

    private fun startAudioCapture(uri: Uri) {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) return
        val bufSize = maxOf(minBuf, 4096)
        val ar = runCatching {
            AudioRecord(MediaRecorder.AudioSource.CAMCORDER, sampleRate, channelConfig, encoding, bufSize)
        }.getOrNull() ?: return
        if (ar.state != AudioRecord.STATE_INITIALIZED) { ar.release(); return }
        audioRecord = ar

        val aCodec = runCatching { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC) }.getOrNull() ?: return
        val aFmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufSize)
        }
        runCatching { aCodec.configure(aFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }.onFailure { aCodec.release(); return }
        audioCodec = aCodec
        Log.i(TAG, "audio capture prepared sampleRate=$sampleRate")
    }

    private fun startAudioFeedThread() {
        val ar = audioRecord ?: return
        val ac = audioCodec ?: return
        audioThread = Thread({
            val buf = ShortArray(2048)
            var presentationUs = 0L
            val sampleRate = 44100
            while (!stopping.get()) {
                val read = ar.read(buf, 0, buf.size)
                if (read <= 0) continue
                // Track peak amplitude for meter
                var peak = 0
                for (i in 0 until read) { val v = kotlin.math.abs(buf[i].toInt()); if (v > peak) peak = v }
                peakAmplitude.getAndUpdate { cur -> if (peak > cur) peak else cur }
                // Feed into AAC encoder
                val idx = runCatching { ac.dequeueInputBuffer(10_000L) }.getOrDefault(-1)
                if (idx >= 0) {
                    val ib = ac.getInputBuffer(idx) ?: continue
                    ib.clear()
                    val byteCount = read * 2
                    ib.asShortBuffer().put(buf, 0, read)
                    ac.queueInputBuffer(idx, 0, byteCount, presentationUs, 0)
                    // read = number of shorts; each short = 1 sample per channel; sampleRate = samples/sec
                    presentationUs += (read.toLong() * 1_000_000L) / sampleRate
                }
                drainAudioEncoder(ac)
            }
            // Drain remaining
            val idx = runCatching { ac.dequeueInputBuffer(10_000L) }.getOrDefault(-1)
            if (idx >= 0) ac.queueInputBuffer(idx, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drainAudioEncoder(ac)
        }, "PNS.MCAudio")
        audioThread?.isDaemon = true
        audioThread?.start()
    }

    private fun drainAudioEncoder(ac: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = runCatching { ac.dequeueOutputBuffer(info, 0L) }.getOrDefault(-1)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Store the format; the muxer may not be started yet (video track not ready)
                    pendingAudioFormat = ac.outputFormat
                    if (audioTrack < 0 && muxerStarted) {
                        // Muxer already running — add audio track now
                        audioTrack = muxer?.addTrack(ac.outputFormat) ?: -1
                        Log.i(TAG, "audio track added late idx=$audioTrack")
                    }
                }
                outIdx >= 0 -> {
                    val buf = ac.getOutputBuffer(outIdx)
                    if (buf != null && audioTrack >= 0 && muxerStarted && info.size > 0 &&
                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        runCatching { muxer?.writeSampleData(audioTrack, buf, info) }
                    }
                    ac.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
                else -> return
            }
        }
    }

    private var pendingOnSaved: ((Uri?) -> Unit)? = null

    private fun finalizeMuxer() {
        val savedUri = runCatching {
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
            muxerStarted = false
            muxer = null
            val uri = pendingUri
            if (uri != null) {
                CaptureStorage.finalizePendingVideoInsert(appContext, uri)
                Log.i(TAG, "inAppVideoSaved uri=$uri")
            }
            uri
        }.onFailure { e ->
            Log.w(TAG, "finalizeMuxer failed: ${e.message}")
        }.getOrNull()

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
}
