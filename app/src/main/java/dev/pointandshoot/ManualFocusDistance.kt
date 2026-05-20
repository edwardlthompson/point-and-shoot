package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics

/**
 * Manual [android.hardware.camera2.CaptureRequest.LENS_FOCUS_DISTANCE] helpers (diopters;
 * 0 = infinity). Used with [CommandDialMode.M] for video/stills peaking workflow (Sprint 13V.10).
 */
object ManualFocusDistance {
    private const val DEFAULT_FALLBACK_MAX_DIOPTERS = 8f
    private const val DRAG_PIXELS_TO_DIOPTERS = 0.0018f

    fun maxDiopters(chars: CameraCharacteristics): Float =
        maxDioptersFromMinFocusMeters(chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE))

    fun maxDioptersFromMinFocusMeters(minFocusMeters: Float?): Float =
        when {
            minFocusMeters != null && minFocusMeters > 0f -> 1f / minFocusMeters
            else -> DEFAULT_FALLBACK_MAX_DIOPTERS
        }

    fun clamp(diopters: Float, maxDiopters: Float): Float = diopters.coerceIn(0f, maxDiopters)

    fun clamp(diopters: Float, chars: CameraCharacteristics): Float =
        clamp(diopters, maxDiopters(chars))

    /** Reasonable starting point when entering Manual (M) dial. */
    fun defaultForLens(chars: CameraCharacteristics): Float {
        val maxD = maxDiopters(chars)
        return (maxD * 0.35f).coerceIn(0f, maxD)
    }

    /** Positive [dragPixels] (finger moved down) increases diopters → closer focus. */
    fun nudgeFromDrag(current: Float, dragPixels: Float, chars: CameraCharacteristics): Float {
        return clamp(current + dragPixels * DRAG_PIXELS_TO_DIOPTERS, maxDiopters(chars))
    }
}
