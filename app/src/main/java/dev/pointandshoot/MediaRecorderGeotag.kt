package dev.pointandshoot

import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.util.Log

/**
 * Embeds location into MP4/3GP metadata when using [MediaRecorder].
 * Call **after** [MediaRecorder.setVideoSource] / [MediaRecorder.setAudioSource] and
 * **before** [MediaRecorder.prepare] (API 26+).
 */
fun MediaRecorder.applyCaptureGeotag(location: Location?) {
    if (location == null) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    runCatching {
        setLocation(location.latitude.toFloat(), location.longitude.toFloat())
    }.onFailure { Log.w("PNS.Geotag", "MediaRecorder.setLocation failed", it) }
}
