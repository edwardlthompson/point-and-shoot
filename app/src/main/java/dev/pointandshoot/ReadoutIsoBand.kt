package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.util.Range

/**
 * ISO ceiling presets for the readout strip (Sprint **14.7**).
 *
 * Stops are still chosen from [ReadoutExposureCatalog]; the band only filters the menu and
 * hard-clamps picks in [PreviewController].
 */
enum class ReadoutIsoBand(
    val menuLabel: String,
    /** Inclusive minimum ISO for this band (typical base ISO). */
    val minIso: Int,
    /** Inclusive maximum ISO, or [Int.MAX_VALUE] for sensor-limited full range. */
    val maxIso: Int,
) {
    FULL("Full range (sensor)", 50, Int.MAX_VALUE),
    BAND_100_400("Band 100 – 400", 100, 400),
    BAND_100_800("Band 100 – 800", 100, 800),
    BAND_100_3200("Band 100 – 3200", 100, 3200),
    ;

    fun contains(iso: Int): Boolean = iso in minIso..maxIso

    /** Clamp [value] to this band and the HAL [range] (when present). */
    fun clampPick(
        range: Range<Int>?,
        value: Int,
    ): Int {
        var v = value.coerceIn(minIso, maxIso)
        if (range != null) {
            v = v.coerceIn(range.lower, range.upper)
        }
        return v
    }

    /** Filter standard ISO table entries to those inside this band and the sensor range. */
    fun filterStops(
        range: Range<Int>?,
        table: IntArray = ReadoutExposureCatalog.ISO_STOP_TABLE,
    ): List<Int> {
        val lo = if (range != null) maxOf(minIso, range.lower) else minIso
        val hi =
            if (range != null) {
                minOf(maxIso, range.upper)
            } else {
                maxIso
            }
        if (lo > hi) return emptyList()
        return table.filter { it in lo..hi }
    }
}

/**
 * Readout AE axis coupling (Sprint **14.7**).
 *
 * Differs from command-dial **M**, which is **manual focus distance** on the preview (drag),
 * not ISO/shutter. Full sensor manual uses **both** readout chips locked (AE off).
 */
enum class ReadoutAeCoupling {
    /** Both axes automatic (HAL AE). */
    AUTO,

    /** Fixed ISO; shutter from YUV chase ([CaptureRequest.CONTROL_AE_MODE_OFF] + [SENSOR_EXPOSURE_TIME]). */
    LOCKED_ISO_AUTO_SS,

    /** Fixed shutter; ISO from YUV chase ([CONTROL_AE_MODE_OFF] + [SENSOR_SENSITIVITY]). */
    LOCKED_SS_AUTO_ISO,

    /** Both locked — [CaptureRequest.CONTROL_AE_MODE_OFF]. */
    MANUAL_BOTH,
    ;

    companion object {
        fun fromOverrides(
            iso: Int?,
            exposureNs: Long?,
        ): ReadoutAeCoupling =
            when {
                iso != null && exposureNs != null -> MANUAL_BOTH
                iso != null -> LOCKED_ISO_AUTO_SS
                exposureNs != null -> LOCKED_SS_AUTO_ISO
                else -> AUTO
            }
    }
}
