package dev.pointandshoot

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission

/**
 * Sprint **AS.1** — in-app video audio profile (sample rate, AAC bitrate, wind NS, external mic).
 */
data class PnsAudioCaptureProfile(
    val sampleRateHz: Int,
    val aacBitrateBps: Int,
    val channelCount: Int,
    val channelConfig: Int,
    val pcmEncoding: Int,
    val windNoiseSuppression: Boolean,
    val preferExternalInput: Boolean,
    val hiFiMode: Boolean,
) {
}

object PnsAudioCaptureSupport {
    private const val TAG = "PNS.Audio"

    private val SAMPLE_RATE_CANDIDATES_HIFI = intArrayOf(96_000, 48_000, 44_100)
    private val SAMPLE_RATE_CANDIDATES_STANDARD = intArrayOf(48_000, 44_100)

    /** Highest PCM rate [AudioRecord] can open (mic path), independent of AAC mux. */
    fun maxPcmCaptureSampleRateHz(hiFiMode: Boolean): Int {
        val rates = if (hiFiMode) SAMPLE_RATE_CANDIDATES_HIFI else SAMPLE_RATE_CANDIDATES_STANDARD
        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        for (rate in rates) {
            if (AudioRecord.getMinBufferSize(rate, channelConfig, encoding) > 0) return rate
        }
        return 44_100
    }

    fun resolve(context: Context, chrome: PreviewChromePreferences): PnsAudioCaptureProfile {
        val hiFi = chrome.audioHiFiCapture
        val rates = if (hiFi) SAMPLE_RATE_CANDIDATES_HIFI else SAMPLE_RATE_CANDIDATES_STANDARD
        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val sampleRate = pickSampleRate(rates, channelConfig, encoding)
        val aacBitrate = if (hiFi) 256_000 else 128_000
        return PnsAudioCaptureProfile(
            sampleRateHz = sampleRate,
            aacBitrateBps = aacBitrate,
            channelCount = 2,
            channelConfig = channelConfig,
            pcmEncoding = encoding,
            windNoiseSuppression = chrome.audioWindNoiseReduction,
            preferExternalInput = chrome.audioPreferExternalInput,
            hiFiMode = hiFi,
        )
    }

    fun diagSummary(profile: PnsAudioCaptureProfile): String =
        buildString {
            append("sampleRate=${profile.sampleRateHz} aacBitrate=${profile.aacBitrateBps} ")
            append("pcm=int16 ")
            append("windNs=${profile.windNoiseSuppression} extMic=${profile.preferExternalInput} hiFi=${profile.hiFiMode}")
        }

    private fun pickSampleRate(
        candidates: IntArray,
        channelConfig: Int,
        encoding: Int,
    ): Int {
        for (rate in candidates) {
            val min = AudioRecord.getMinBufferSize(rate, channelConfig, encoding)
            if (min > 0) return rate
        }
        return 44_100
    }

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun createAudioRecord(
        context: Context,
        profile: PnsAudioCaptureProfile,
        bufferSize: Int,
    ): AudioRecord? {
        val ar =
            runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.CAMCORDER,
                    profile.sampleRateHz,
                    profile.channelConfig,
                    profile.pcmEncoding,
                    bufferSize,
                )
            }.getOrNull() ?: return null
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            return null
        }
        if (profile.preferExternalInput) {
            preferExternalInputDevice(context, ar)
        }
        attachCaptureEffects(ar, profile.windNoiseSuppression)
        return ar
    }

    fun attachCaptureEffects(audioRecord: AudioRecord, windNoiseSuppression: Boolean): String {
        val sessionId = audioRecord.audioSessionId
        val parts = mutableListOf<String>()
        if (windNoiseSuppression && NoiseSuppressor.isAvailable()) {
            runCatching {
                NoiseSuppressor.create(sessionId)?.enabled = true
                parts += "noiseSuppressor=on"
            }.onFailure { parts += "noiseSuppressor=fail" }
        } else {
            parts += "noiseSuppressor=${if (windNoiseSuppression) "unavailable" else "off"}"
        }
        if (AutomaticGainControl.isAvailable()) {
            runCatching {
                AutomaticGainControl.create(sessionId)?.enabled = false
                parts += "agc=off"
            }
        }
        val summary = parts.joinToString(" ")
        Log.i(TAG, "audioEffects session=$sessionId $summary")
        return summary
    }

    fun preferExternalInputDevice(context: Context, audioRecord: AudioRecord): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices =
            am.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { dev ->
                dev.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
        val pick = devices.firstOrNull() ?: return false
        val ok = audioRecord.setPreferredDevice(pick)
        if (ok) {
            Log.i(TAG, "preferredInput type=${pick.type} id=${pick.id} product=${pick.productName}")
        }
        return ok
    }

    fun logInputDevices(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val summary =
            inputs.joinToString { d -> "type=${d.type}" }
        Log.i(TAG, "audioInputs count=${inputs.size} $summary")
    }
}
