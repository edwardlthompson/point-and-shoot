package dev.pointandshoot

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Host-side helpers for BUILD_PLAN §4 highlight-weighted metering: pick a small
 * YUV analysis stream and map floating EV suggestions to [CONTROL_AE_EXPOSURE_COMPENSATION] indices.
 */
object HighlightMeterSupport {

    /**
     * Prefer a modest YUV size (≥320×240 when available) to limit bandwidth while
     * keeping enough pixels for a stable luma histogram.
     */
    /**
     * @param preview When non-null, prefer a YUV size whose aspect ratio is closest to the preview
     *   buffer so ML face boxes map more cleanly onto [TexturePreviewFit] space.
     */
    fun pickYuv420AnalysisSize(map: StreamConfigurationMap?, preview: Size? = null): Size? {
        val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: return null
        if (sizes.isEmpty()) return null
        val p = preview
        if (p != null && p.width > 0 && p.height > 0) {
            val exact = sizes.find { it.width == p.width && it.height == p.height }
            if (exact != null) return exact
        }
        val bigEnough = sizes.filter { it.width >= 320 && it.height >= 240 }
        val pool = if (bigEnough.isNotEmpty()) bigEnough else sizes.toList()
        if (p == null || p.width <= 0 || p.height <= 0) {
            return pool.minByOrNull { it.width * it.height }
        }
        val targetAspect = p.width.toFloat() / p.height.toFloat()
        return pool.minWithOrNull(
            compareBy<Size> { abs(it.width.toFloat() / it.height - targetAspect) }
                .thenBy { it.width * it.height },
        ) ?: pool.minByOrNull { it.width * it.height }
    }

    /**
     * Converts a suggested EV offset into the device's AE compensation index using
     * [CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP] and [CONTROL_AE_COMPENSATION_RANGE].
     *
     * Uses **floor** for negative EV and **ceil** for positive EV (instead of rounding),
     * so small non-zero suggestions (e.g. −0.25 EV with a ⅓-stop step) still move at least
     * one index; `roundToInt` was collapsing most highlight pulls to **0** → preview ISO/SS
     * matched Auto even though the meter was running.
     */
    fun evToCompensationIndex(ev: Double, chars: CameraCharacteristics): Int? {
        val range = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: return null
        val stepRat = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: return null
        val step = stepRat.numerator.toDouble() / stepRat.denominator.toDouble()
        if (step <= 0.0) return null
        return compensationIndexFromEv(ev, step, range.lower, range.upper)
    }

    /**
     * Maps floating EV to an integer compensation index for a given [step] (EV per index).
     * Pure function for unit tests and [evToCompensationIndex].
     */
    fun compensationIndexFromEv(ev: Double, step: Double, lower: Int, upper: Int): Int {
        require(step > 0.0) { "step must be positive" }
        if (abs(ev) < 1e-12) return 0.coerceIn(lower, upper)
        val raw = ev / step
        val q =
            if (raw < 0.0) {
                floor(raw).toInt()
            } else {
                ceil(raw).toInt()
            }
        return q.coerceIn(lower, upper)
    }
}
