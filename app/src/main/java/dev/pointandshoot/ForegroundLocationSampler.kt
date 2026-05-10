package dev.pointandshoot

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log

/**
 * Foreground-only location updates via [LocationManager] (no Play Services).
 * Call [start] only when fine-location permission is granted.
 */
class ForegroundLocationSampler(
    private val appContext: Context,
    private val onLocation: (Location) -> Unit,
) {
    private val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val listener =
        LocationListener { loc ->
            onLocation(loc)
        }

    @SuppressLint("MissingPermission")
    fun start() {
        val providers =
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
            )
        for (p in providers) {
            if (lm.isProviderEnabled(p)) {
                runCatching {
                    lm.getLastKnownLocation(p)?.let(onLocation)
                }.onFailure { Log.w(TAG, "getLastKnownLocation $p", it) }
                runCatching {
                    lm.requestLocationUpdates(p, MIN_TIME_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper())
                }.onFailure { Log.w(TAG, "requestLocationUpdates $p", it) }
            }
        }
    }

    fun stop() {
        runCatching { lm.removeUpdates(listener) }
            .onFailure { Log.w(TAG, "removeUpdates", it) }
    }

    companion object {
        private const val TAG = "PNS.LocSampler"
        private const val MIN_TIME_MS = 2_000L
        private const val MIN_DISTANCE_M = 5f
    }
}
