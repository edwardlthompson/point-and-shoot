package dev.pointandshoot

import android.media.MediaRecorder
import android.os.Build

/**
 * Sprint **15.24** — in-app video audio source selection.
 */
enum class VideoAudioSource(val storageId: String, val label: String) {
    Mic("mic", "Microphone"),
    Camcorder("camcorder", "Camcorder"),
    Unprocessed("unprocessed", "Unprocessed"),
    ;

    fun logTag(): String =
        when (this) {
            Mic -> "MIC"
            Camcorder -> "CAMCORDER"
            Unprocessed -> "UNPROCESSED"
        }

    fun toMediaRecorderSource(): Int =
        when (this) {
            Mic -> MediaRecorder.AudioSource.MIC
            Camcorder -> MediaRecorder.AudioSource.CAMCORDER
            Unprocessed -> MediaRecorder.AudioSource.UNPROCESSED
        }

    /** [AudioRecord] source with API 24 guard for [UNPROCESSED]. */
    fun toAudioRecordSource(): Int =
        if (this == Unprocessed && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            MediaRecorder.AudioSource.CAMCORDER
        } else {
            toMediaRecorderSource()
        }

    companion object {
        fun fromStorage(id: String?): VideoAudioSource =
            entries.firstOrNull { it.storageId == id } ?: Camcorder
    }
}
