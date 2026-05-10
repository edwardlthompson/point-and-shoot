package dev.pointandshoot

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import kotlin.math.roundToInt

/**
 * Host-side helpers for BUILD_PLAN §4 highlight-weighted metering: pick a small
 * YUV analysis stream and map floating EV suggestions to [CONTROL_AE_EXPOSURE_COMPENSATION] indices.
 */
object HighlightMeterSupport {

    /**
     * Prefer a modest YUV size (≥320×240 when available) to limit bandwidth while
     * keeping enough pixels for a stable luma histogram.
     */
    fun pickYuv420AnalysisSize(map: StreamConfigurationMap?): Size? {
        val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: return null
        if (sizes.isEmpty()) return null
        val bigEnough = sizes.filter { it.width >= 320 && it.height >= 240 }
        val pool = if (bigEnough.isNotEmpty()) bigEnough else sizes.toList()
        return pool.minByOrNull { it.width * it.height }
    }

    /**
     * Converts a suggested EV offset into the device's AE compensation index using
     * [CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP] and [CONTROL_AE_COMPENSATION_RANGE].
     */
    fun evToCompensationIndex(ev: Double, chars: CameraCharacteristics): Int? {
        val range = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: return null
        val stepRat = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: return null
        val step = stepRat.numerator.toDouble() / stepRat.denominator.toDouble()
        if (step <= 0.0) return null
        val idx = (ev / step).roundToInt()
        return idx.coerceIn(range.lower, range.upper)
    }
}
