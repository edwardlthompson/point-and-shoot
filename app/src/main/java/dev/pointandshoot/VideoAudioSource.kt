package dev.pointandshoot

import android.media.MediaRecorder

/**
 * Sprint **15.24** — in-app video audio source selection.
 */
enum class VideoAudioSource(val storageId: String, val label: String) {
    Mic("mic", "Microphone"),
    Camcorder("camcorder", "Camcorder"),
    Unprocessed("unprocessed", "Unprocessed"),
    ;

    fun toMediaRecorderSource(): Int =
        when (this) {
            Mic -> MediaRecorder.AudioSource.MIC
            Camcorder -> MediaRecorder.AudioSource.CAMCORDER
            Unprocessed -> MediaRecorder.AudioSource.UNPROCESSED
        }

    companion object {
        fun fromStorage(id: String?): VideoAudioSource =
            entries.firstOrNull { it.storageId == id } ?: Camcorder
    }
}
