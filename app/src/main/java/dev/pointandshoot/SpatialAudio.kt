package dev.pointandshoot

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Sprint **AS.3** — spatial / surround capability probe (in-app record stays stereo CAMCORDER).
 */
object SpatialAudio {
    private const val TAG = "PNS.SpatialAudio"

    fun isSpatialRecordingSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode =
            runCatching { am.encodedSurroundMode }
                .onFailure { e ->
                    Log.w(TAG, "encodedSurroundMode unavailable: ${e.message}")
                }
                .getOrDefault(AudioManager.ENCODED_SURROUND_OUTPUT_UNKNOWN)
        val supported =
            mode != AudioManager.ENCODED_SURROUND_OUTPUT_NEVER &&
                mode != AudioManager.ENCODED_SURROUND_OUTPUT_UNKNOWN
        Log.i(TAG, "spatialAudio supported=$supported encodedSurroundMode=$mode")
        return supported
    }

    fun diag(context: Context): String {
        val supported = isSpatialRecordingSupported(context)
        return "spatialRecording=$supported"
    }
}
