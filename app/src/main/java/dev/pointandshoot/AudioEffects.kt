package dev.pointandshoot

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Sprint **AS.3** — lightweight PCM post-process + ducking + multi-track policy for in-app video.
 */
object AudioEffects {
    private const val TAG = "PNS.AudioFx"

    @Volatile
    var voiceoverDuckingEnabled: Boolean = false

    @Volatile
    var lightCompressionEnabled: Boolean = false

  private var focusRequest: AudioFocusRequest? = null

    /** Simple soft-knee compression on interleaved PCM shorts (in-place). */
    fun applyPcmProcessing(buffer: ShortArray, count: Int) {
        if (!lightCompressionEnabled || count <= 0) return
        val threshold = (0.65f * Short.MAX_VALUE).toInt()
        for (i in 0 until count) {
            val v = buffer[i].toInt()
            val abs = kotlin.math.abs(v)
            if (abs <= threshold) continue
            val sign = if (v < 0) -1 else 1
            val excess = abs - threshold
            val compressed = threshold + (excess * 0.35f).toInt()
            buffer[i] = (sign * compressed.coerceAtMost(Short.MAX_VALUE.toInt())).toShort()
        }
    }

    fun requestVoiceoverDuck(context: Context): Boolean {
        if (!voiceoverDuckingEnabled) return false
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setOnAudioFocusChangeListener { }
                    .build()
            focusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.also { ok ->
            Log.i(TAG, "voiceoverDuck requested ok=$ok")
        }
    }

    fun abandonVoiceoverDuck(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    /** Multi-track in-app video is not implemented; log policy for gates. */
    fun multiTrackPolicy(): String {
        Log.i(TAG, "multiTrackRecording=unsupported v1=stereoAacMux")
        return "multiTrack=unsupported"
    }

    fun logPostProcessState() {
        Log.i(
            TAG,
            "audioPostProcess compression=$lightCompressionEnabled ducking=$voiceoverDuckingEnabled " +
                multiTrackPolicy(),
        )
    }
}
