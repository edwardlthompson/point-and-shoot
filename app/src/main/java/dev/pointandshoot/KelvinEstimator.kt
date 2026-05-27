package dev.pointandshoot

import kotlin.math.roundToInt

/**
 * Sprint **15.33** — estimate color temperature (K) from AWB R/B gain tilt.
 */
object KelvinEstimator {
    private val ROBERTSON_TILT_TO_K: List<Pair<Float, Int>> =
        listOf(
            0.55f to 2500,
            0.65f to 2850,
            0.75f to 3200,
            0.85f to 3800,
            0.95f to 4500,
            1.05f to 5200,
            1.15f to 6000,
            1.30f to 7000,
            1.50f to 8000,
        )

    /**
     * @param rgGainTilt R gain / B gain from [android.hardware.camera2.CaptureResult.COLOR_CORRECTION_GAINS].
     */
    fun estimateFromRgGainTilt(tilt: Float): Int {
        if (!tilt.isFinite() || tilt <= 0f) return 5600
        val pairs = ROBERTSON_TILT_TO_K
        if (tilt <= pairs.first().first) return pairs.first().second
        if (tilt >= pairs.last().first) return pairs.last().second
        for (i in 0 until pairs.lastIndex) {
            val (t0, k0) = pairs[i]
            val (t1, k1) = pairs[i + 1]
            if (tilt in t0..t1) {
                val w = (tilt - t0) / (t1 - t0)
                return (k0 + w * (k1 - k0)).roundToInt()
            }
        }
        return 5600
    }
}
