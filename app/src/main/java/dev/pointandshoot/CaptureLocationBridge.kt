package dev.pointandshoot

import android.location.Location

/**
 * Holds the latest location fix while the preview samples in the foreground.
 * [CaptureStorage] and [Dng12Saver] read from here when callers do not pass an explicit [Location].
 */
object CaptureLocationBridge {
    @Volatile
    private var latest: Location? = null

    fun update(location: Location?) {
        latest = location
    }

    fun snapshot(): Location? = latest
}
