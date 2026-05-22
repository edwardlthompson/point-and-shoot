package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.util.Log

/**
 * Manual [android.hardware.camera2.CaptureRequest.LENS_FOCUS_DISTANCE] helpers.
 *
 * HAL [CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE] is already in **diopters**
 * (1/m); `0f` means fixed-focus at infinity. [LENS_FOCUS_DISTANCE] uses the same unit
 * (`0` = infinity, higher = closer).
 */
object ManualFocusDistance {
    private const val TAG = "PNS.FocusPeaking"
    private const val DEFAULT_FALLBACK_MAX_DIOPTERS = 8f
    private const val DRAG_PIXELS_TO_DIOPTERS = 0.0018f

    data class FocusRange(
        /** Always `0` (infinity) for the rack slider. */
        val minDiopters: Float = 0f,
        /** HAL closest-focus diopters, or fallback when unknown. */
        val maxDiopters: Float,
        /** HAL reported `0` — lens may not honor [LENS_FOCUS_DISTANCE]. */
        val fixedAtInfinity: Boolean,
        /** Raw [CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE] (null = missing). */
        val halMinimumFocusDiopters: Float?,
    ) {
        val sliderEnabled: Boolean get() = !fixedAtInfinity && maxDiopters > 0.001f
    }

    fun focusRange(chars: CameraCharacteristics): FocusRange {
        val hal = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        val max = maxDioptersFromHalMinimumFocus(hal)
        val fixed = hal != null && hal <= 0f
        return FocusRange(
            maxDiopters = max,
            fixedAtInfinity = fixed,
            halMinimumFocusDiopters = hal,
        )
    }

    fun maxDiopters(chars: CameraCharacteristics): Float = focusRange(chars).maxDiopters

    /**
     * @param halMinimumFocusDiopters [CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE]
     *   (diopters, not meters).
     */
    fun maxDioptersFromHalMinimumFocus(halMinimumFocusDiopters: Float?): Float =
        when {
            halMinimumFocusDiopters == null -> DEFAULT_FALLBACK_MAX_DIOPTERS
            halMinimumFocusDiopters <= 0f -> 0f
            else -> halMinimumFocusDiopters
        }

    /** @deprecated Misnamed; value is diopters. Use [maxDioptersFromHalMinimumFocus]. */
    fun maxDioptersFromMinFocusMeters(minFocusMeters: Float?): Float =
        maxDioptersFromHalMinimumFocus(minFocusMeters)

    fun clamp(diopters: Float, maxDiopters: Float): Float = diopters.coerceIn(0f, maxDiopters)

    fun clamp(diopters: Float, chars: CameraCharacteristics): Float =
        clamp(diopters, maxDiopters(chars))

    /** Reasonable starting point when entering Manual (M) dial or manual-distance AF. */
    fun defaultForLens(chars: CameraCharacteristics): Float {
        val maxD = maxDiopters(chars)
        if (maxD <= 0f) return 0f
        return (maxD * 0.35f).coerceIn(0f, maxD)
    }

    /** Positive [dragPixels] (finger moved right on the finder) increases diopters → closer focus. */
    fun nudgeFromDrag(current: Float, dragPixels: Float, chars: CameraCharacteristics): Float {
        return clamp(current + dragPixels * DRAG_PIXELS_TO_DIOPTERS, maxDiopters(chars))
    }

    fun formatDioptersShort(diopters: Float?): String {
        if (diopters == null) return "MF"
        if (diopters <= 0.001f) return "∞"
        val meters = 1f / diopters
        return if (meters >= 100f) "∞" else "%.1fm".format(meters)
    }

    fun formatDioptersLong(diopters: Float, maxDiopters: Float): String {
        val short = formatDioptersShort(diopters)
        return "$short · ${"%.2f".format(diopters)} / ${"%.2f".format(maxDiopters)} diopters"
    }

    fun logFocusRange(cameraId: String, chars: CameraCharacteristics) {
        val range = focusRange(chars)
        val closest =
            range.halMinimumFocusDiopters?.takeIf { it > 0f }?.let { formatDioptersShort(it) } ?: "fixed/unknown"
        Log.i(
            TAG,
            "lensFocusRange cameraId=$cameraId halMinDiopters=${range.halMinimumFocusDiopters} " +
                "rack=0..${"%.3f".format(range.maxDiopters)} closest=$closest fixedAtInfinity=${range.fixedAtInfinity}",
        )
    }
}
