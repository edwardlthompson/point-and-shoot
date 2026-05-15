package dev.pointandshoot

import android.hardware.camera2.CaptureResult

/**
 * Approximate correlated color temperature and residual tint labels for readout menus.
 * (HAL presets vary; these are user-facing anchors for pro-style WB selection.)
 */
object AwbPresetReadout {

    /**
     * Standard AWB presets ordered **coldest (highest K) → warmest** for readout menus.
     * Excludes [CaptureResult.CONTROL_AWB_MODE_AUTO] and [CaptureResult.CONTROL_AWB_MODE_OFF].
     */
    val KELVIN_DESCENDING_PRESET_ORDER: IntArray =
        intArrayOf(
            CaptureResult.CONTROL_AWB_MODE_SHADE,
            CaptureResult.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
            CaptureResult.CONTROL_AWB_MODE_DAYLIGHT,
            CaptureResult.CONTROL_AWB_MODE_TWILIGHT,
            CaptureResult.CONTROL_AWB_MODE_FLUORESCENT,
            CaptureResult.CONTROL_AWB_MODE_WARM_FLUORESCENT,
            CaptureResult.CONTROL_AWB_MODE_INCANDESCENT,
        )

    data class KelvinTint(val kelvin: Int, val tintNote: String)

    fun kelvinTintForMode(mode: Int?): KelvinTint? =
        when (mode) {
            null -> null
            CaptureResult.CONTROL_AWB_MODE_AUTO ->
                KelvinTint(5500, "multi-zone scene estimate")
            CaptureResult.CONTROL_AWB_MODE_OFF -> KelvinTint(0, "Manual / locked")
            CaptureResult.CONTROL_AWB_MODE_INCANDESCENT -> KelvinTint(2850, "warm · +magenta")
            CaptureResult.CONTROL_AWB_MODE_FLUORESCENT -> KelvinTint(4200, "cool white · +green")
            CaptureResult.CONTROL_AWB_MODE_WARM_FLUORESCENT -> KelvinTint(3500, "warm CFL · slight magenta")
            CaptureResult.CONTROL_AWB_MODE_DAYLIGHT -> KelvinTint(5500, "neutral daylight")
            CaptureResult.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> KelvinTint(6500, "cool · slight blue")
            CaptureResult.CONTROL_AWB_MODE_TWILIGHT -> KelvinTint(4500, "blue hour · +blue")
            CaptureResult.CONTROL_AWB_MODE_SHADE -> KelvinTint(7500, "open shade · +blue")
            else -> null
        }
}
