package dev.pointandshoot

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Highlight-weighted metering per BUILD_PLAN §4 (Phase 1):
 * "Highlight-weighted metering (protect 95th percentile luma; Ricoh GR style)".
 *
 * The algorithm is engine-agnostic: feed it a luminance histogram (256 bins,
 * 0..255) and a target percentile (default `0.95`), and it returns the EV
 * offset to apply to the next `CONTROL_AE_EXPOSURE_COMPENSATION` so the
 * specified percentile lands at the configured ceiling (default `240/255`).
 *
 * Pure data + pure functions = trivially unit-testable; Phase 1's capture
 * pipeline plugs the histogram in from a downsampled YUV preview frame.
 */
object HighlightMeter {

    /**
     * Compute the EV correction needed so [percentile] of the luminance
     * histogram falls at or below [ceilingValue].
     *
     * @param histogram256 a 256-bin luminance histogram (bin 0 = darkest).
     * @param percentile   percentile to protect; 0.95 = 95th-percentile (Ricoh GR style).
     * @param ceilingValue desired luma value at the protected percentile (0..255). Defaults to 240,
     *                     leaving ~15 codes of headroom under pure white so highlights stay recoverable in RAW.
     * @param maxAbsEv     clamps the suggested correction so a single frame can never demand more than this many stops.
     *
     * @return positive EV = brighten next frame; negative EV = darken next frame; 0 = no change needed.
     */
    fun suggestEvCorrection(
        histogram256: IntArray,
        percentile: Double = 0.95,
        ceilingValue: Int = 240,
        maxAbsEv: Double = 3.0,
    ): Double {
        require(histogram256.size == 256) { "expected 256-bin histogram, got ${histogram256.size}" }
        require(percentile in 0.0..1.0) { "percentile out of range: $percentile" }
        require(ceilingValue in 1..255) { "ceilingValue out of range: $ceilingValue" }

        val total = histogram256.fold(0L) { acc, n -> acc + n }
        if (total <= 0L) return 0.0

        val target = (total.toDouble() * percentile).toLong().coerceIn(1L, total)
        var running = 0L
        var p = 255
        for (bin in 0 until 256) {
            running += histogram256[bin]
            if (running >= target) {
                p = bin
                break
            }
        }

        if (p == ceilingValue) return 0.0

        // Map current p -> ceilingValue in linear-light using log2(ratio).
        // Both values are guaranteed >= 1 by the require above.
        val current = p.coerceAtLeast(1).toDouble()
        val target8 = ceilingValue.toDouble()
        val ev = log2(target8 / current)

        return clamp(ev, -maxAbsEv, maxAbsEv)
    }

    private fun log2(x: Double): Double = ln(x) / LN2
    private val LN2 = ln(2.0)

    private fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))
}
