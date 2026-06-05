package dev.pointandshoot

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log

/**
 * Picks an AAC encoder that can mux at the requested sample rate (96 kHz hi-fi when available).
 *
 * Default [MediaCodec.createEncoderByType] often configures 96 kHz but outputs 44.1 kHz; we probe
 * [MediaCodec.getOutputFormat] after a short prime and prefer encoders whose output matches input.
 */
object PnsAacEncoderSupport {
    private const val TAG = "PNS.Audio"
    private val cacheLock = Any()
    @Volatile private var cachedMaxHiFiMuxHz: Int? = null

    data class AacEncoderPick(
        val codec: MediaCodec,
        val codecName: String,
        /** PCM / [AudioRecord] rate aligned with the encoder. */
        val captureSampleRateHz: Int,
        /** Confirmed [MediaFormat.KEY_SAMPLE_RATE] from encoder output (mux timeline). */
        val muxSampleRateHz: Int,
    )

    /** Rate try order for encoder open (hi-fi prefers 96 kHz first). */
    internal fun targetMuxSampleRates(profile: PnsAudioCaptureProfile): IntArray =
        if (profile.hiFiMode) {
            intArrayOf(96_000, 48_000, 44_100)
        } else {
            intArrayOf(48_000, 44_100)
        }

    /** Highest AAC mux sample rate this device can encode (cached per process). */
    fun maxHiFiMuxSampleRateHz(context: Context): Int {
        cachedMaxHiFiMuxHz?.let { return it }
        synchronized(cacheLock) {
            cachedMaxHiFiMuxHz?.let { return it }
            val profile = hiFiProbeProfile()
            val bufSize = pcmMinBufferSize(profile.sampleRateHz, profile.channelConfig, profile.pcmEncoding)
            val rate =
                targetMuxSampleRates(profile).firstOrNull { targetRate ->
                    listAacEncoderNames().any { name ->
                        probeOutputSampleRate(name, profile, targetRate, bufSize) == targetRate
                    }
                } ?: 44_100
            cachedMaxHiFiMuxHz = rate
            Log.i(TAG, "maxHiFiMuxSampleRateHz=$rate (device cache)")
            return rate
        }
    }

    fun hiFiMenuSubtitle(context: Context): String {
        val khz = maxHiFiMuxSampleRateHz(context) / 1000
        return "$khz kHz / 256 kbps AAC"
    }

    fun hiFiMenuTitle(context: Context): String {
        val khz = maxHiFiMuxSampleRateHz(context) / 1000
        return "Hi-Fi capture ($khz kHz)"
    }

    private fun hiFiProbeProfile(): PnsAudioCaptureProfile =
        PnsAudioCaptureProfile(
            sampleRateHz = 96_000,
            aacBitrateBps = 256_000,
            channelCount = 2,
            channelConfig = AudioFormat.CHANNEL_IN_STEREO,
            pcmEncoding = AudioFormat.ENCODING_PCM_16BIT,
            windNoiseSuppression = false,
            preferExternalInput = false,
            hiFiMode = true,
        )

    private fun pcmMinBufferSize(sampleRateHz: Int, channelConfig: Int, encoding: Int): Int {
        val min = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, encoding)
        return if (min > 0) maxOf(min, 4096) else 4096
    }

    fun openBestAacEncoder(
        profile: PnsAudioCaptureProfile,
        maxInputSize: Int,
    ): AacEncoderPick? {
        val names = listAacEncoderNames()
        for (targetRate in targetMuxSampleRates(profile)) {
            for (codecName in names) {
                val outputRate = probeOutputSampleRate(codecName, profile, targetRate, maxInputSize)
                if (outputRate == null) continue
                if (outputRate != targetRate) {
                    if (profile.hiFiMode && targetRate == 96_000) {
                        Log.d(TAG, "aacProbe $codecName requested=${targetRate}Hz output=${outputRate}Hz")
                    }
                    continue
                }
                val codec =
                    runCatching { MediaCodec.createByCodecName(codecName) }.getOrNull() ?: continue
                if (!configureEncoder(codec, profile, targetRate, maxInputSize)) {
                    runCatching { codec.release() }
                    continue
                }
                Log.i(
                    TAG,
                    "aacEncoderPick name=$codecName capture=${targetRate}Hz mux=${outputRate}Hz " +
                        "hiFi=${profile.hiFiMode}",
                )
                return AacEncoderPick(
                    codec = codec,
                    codecName = codecName,
                    captureSampleRateHz = targetRate,
                    muxSampleRateHz = outputRate,
                )
            }
        }
        if (profile.hiFiMode) {
            Log.w(TAG, "aacEncoderPick: no 96 kHz AAC on device — hi-fi will use 48/44.1 kHz if available")
        } else {
            Log.w(TAG, "aacEncoderPick failed — no AAC encoder matched target rates")
        }
        return null
    }

    private fun listAacEncoderNames(): List<String> {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        return list.codecInfos
            .filter { info ->
                info.isNonAliasEncoder() &&
                    MediaFormat.MIMETYPE_AUDIO_AAC in info.supportedTypes
            }
            .map { it.name }
            .distinct()
            .sortedWith(
                compareBy(
                    { name ->
                        when {
                            name.contains("qti", ignoreCase = true) -> 0
                            name.contains("android", ignoreCase = true) -> 1
                            else -> 2
                        }
                    },
                    { it },
                ),
            )
    }

  /**
     * Configure → start → silent prime → read output format → stop/release.
     * Returns output [MediaFormat.KEY_SAMPLE_RATE] or null on failure.
     */
    private fun probeOutputSampleRate(
        codecName: String,
        profile: PnsAudioCaptureProfile,
        sampleRateHz: Int,
        maxInputSize: Int,
    ): Int? {
        val codec = runCatching { MediaCodec.createByCodecName(codecName) }.getOrNull() ?: return null
        try {
            if (!configureEncoder(codec, profile, sampleRateHz, maxInputSize)) return null
            codec.start()
            var outputRate: Int? = null
            val info = MediaCodec.BufferInfo()
            repeat(8) {
                val inIdx = runCatching { codec.dequeueInputBuffer(5_000L) }.getOrDefault(-1)
                if (inIdx >= 0) {
                    val ib = codec.getInputBuffer(inIdx)
                    if (ib != null) {
                        ib.clear()
                        val silent = ByteArray(4096)
                        ib.put(silent)
                        codec.queueInputBuffer(inIdx, 0, silent.size, 0L, 0)
                    }
                }
                while (true) {
                    val outIdx = runCatching { codec.dequeueOutputBuffer(info, 0L) }.getOrDefault(-1)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            outputRate =
                                runCatching {
                                    codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                }.getOrNull()
                            return outputRate
                        }
                        outIdx >= 0 -> {
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return outputRate
                        }
                        else -> break
                    }
                }
            }
            return outputRate
        } catch (e: Exception) {
            Log.d(TAG, "aacProbe $codecName @${sampleRateHz}Hz: ${e.message}")
            return null
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private fun configureEncoder(
        codec: MediaCodec,
        profile: PnsAudioCaptureProfile,
        sampleRateHz: Int,
        maxInputSize: Int,
    ): Boolean {
        val fmt =
            MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRateHz,
                profile.channelCount,
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, profile.aacBitrateBps)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize)
            }.also { configured ->
                MediaCodecVideoRecorder.applySpatialAudioMetadata(
                    configured,
                    profile.channelConfig,
                    profile.pcmEncoding,
                )
            }
        return runCatching {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            true
        }.getOrDefault(false)
    }
}
