@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.location.Location

/** Off / coarse (~1 km) / precise geotag, plus share-time GPS strip. */
object PnsGeotagPrivacy {
    private const val COARSE_DEG: Double = 0.01

    fun apply(location: Location, mode: PnsGeotagMode): Location? {
        return when (mode) {
            PnsGeotagMode.Off -> null
            PnsGeotagMode.Precise -> location
            PnsGeotagMode.Coarse -> {
                val copy = Location(location)
                copy.latitude = roundCoord(location.latitude)
                copy.longitude = roundCoord(location.longitude)
                if (copy.hasAltitude()) copy.removeAltitude()
                copy
            }
        }
    }

    fun roundCoord(value: Double): Double = Math.round(value / COARSE_DEG) * COARSE_DEG

    fun shouldEmbed(mode: PnsGeotagMode): Boolean = mode != PnsGeotagMode.Off
}
