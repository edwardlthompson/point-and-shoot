package dev.pointandshoot

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Maps an EV correction onto ISO + exposure time with **low-noise priority**:
 *
 * - **Darken (ev &lt; 0):** lower ISO while **holding shutter** until [isoLower] is hit or the
 *   target brightness is reached; only then **shorten** exposure (faster shutter), capped by
 *   [maxExposureNs] when provided.
 * - **Brighten (ev &gt; 0):** lengthen exposure at **fixed ISO** until the cap; only then raise ISO.
 *
 * Pure JVM-friendly bounds (no [android.util.Range]) so unit tests run on the host.
 */
object HighlightLowNoiseExposure {

    fun applyEvLowNoiseFirst(
        iso: Int,
        exposureNs: Long,
        ev: Double,
        isoLower: Int,
        isoUpper: Int,
        expLower: Long,
        expUpper: Long,
        maxExposureNs: Long? = null,
    ): Pair<Int, Long> {
        val iMin = isoLower
        val iMax = isoUpper
        val tMin = expLower.toDouble().coerceAtLeast(1.0)
        val tMaxSensor = expUpper.toDouble()
        val tCap = maxExposureNs?.toDouble()?.coerceIn(tMin, tMaxSensor) ?: tMaxSensor
        val tMax = min(tMaxSensor, tCap)

        val i0 = iso.coerceIn(iMin, iMax)
        val t0 = exposureNs.toDouble().coerceIn(tMin, tMax)
        if (abs(ev) < 1e-10) {
            return Pair(
                i0.coerceIn(isoLower, isoUpper),
                t0.toLong().coerceIn(expLower, expUpper),
            )
        }

        val Bp = i0 * t0 * 2.0.pow(ev)

        val (isoOut, tOut) =
            if (ev < 0) {
                darkenIsoFirst(i0, t0, Bp, iMin, tMin, tMax)
            } else {
                brightenExposureFirst(i0, t0, Bp, iMax, tMin, tMax)
            }
        return Pair(
            isoOut.coerceIn(isoLower, isoUpper),
            tOut.coerceIn(expLower, expUpper),
        )
    }

    /**
     * Darken: lower ISO toward [iMin] while keeping shutter fixed when that alone hits [Bp]
     * within tolerance; otherwise adjust shutter for the remainder.
     */
    private fun darkenIsoFirst(
        i0: Int,
        t0: Double,
        Bp: Double,
        iMin: Int,
        tMin: Double,
        tMax: Double,
    ): Pair<Int, Long> {
        val iTarget = min(i0, max(iMin, ceil(Bp / t0 - 1e-9).toInt()))
        val tNew = (Bp / iTarget.toDouble()).coerceIn(tMin, tMax)
        val productIsoOnly = iTarget * t0
        val tol = max(1.0, 0.005 * Bp)
        return if (abs(productIsoOnly - Bp) <= tol) {
            Pair(iTarget, t0.roundToLong())
        } else {
            Pair(iTarget, tNew.roundToLong())
        }
    }

    /**
     * Brighten: lengthen exposure at fixed ISO until [tMax]; then raise ISO if still short on light.
     */
    private fun brightenExposureFirst(
        i0: Int,
        t0: Double,
        Bp: Double,
        iMax: Int,
        tMin: Double,
        tMax: Double,
    ): Pair<Int, Long> {
        val tNeed = Bp / i0.toDouble()
        if (tNeed <= tMax) {
            val t1 = max(t0, tNeed).coerceIn(tMin, tMax)
            return Pair(i0, t1.roundToLong())
        }
        val i1 = ceil(Bp / tMax - 1e-9).toInt().coerceAtMost(iMax).coerceAtLeast(i0)
        val t1 = (Bp / i1.toDouble()).coerceIn(tMin, tMax)
        return Pair(i1, t1.roundToLong())
    }

    private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
}
