package dev.pointandshoot

import android.util.Range
import kotlin.math.abs
import kotlin.math.pow

/**
 * YUV-driven adjustment for the **auto** exposure axis when the other axis is locked (Sprint **14.7**).
 *
 * Uses smoothed histogram median / EV and **partial blending** toward equilibrium so 20 Hz YUV polling
 * does not overshoot and hunt. HAL [refreshRepeatingPreviewOnly] should only run when
 * [ChaseAdjustResult.applied] is true.
 *
 * **Documented defaults:** [docs/PNS_TECHNICAL_SETTINGS.md] §4 — update that file when changing constants here.
 */
object ReadoutExposureChase {
    /**
     * Target scene median (0–255 histogram bin) for locked-axis YUV chase.
     * Single knob for preview, DNG, and independent tonal still (same [applyReadoutManualExposureAndWb]).
     * Tuned May 2026 on CPH2655: **40** (~½ stop below **56**; prior **112** was too bright for JPEG).
     */
    const val TARGET_MEDIAN_BIN = 34

    /** Inside this band of smoothed median, hold exposure/ISO steady. */
    const val MEDIAN_DEADBAND_BINS = 10

    private const val MEDIAN_EMA_ALPHA = 0.22

    /** Fraction of the gap closed per YUV sample toward luminance equilibrium. */
    private const val LUMINANCE_BLEND_ALPHA = 0.14

    private const val MIN_EV_STEP = 0.04

    private const val MAX_EV_STEP = 0.10

    /** ~1/15 stop — ignore smaller HAL updates. */
    private const val MIN_SIGNIFICANT_EXPOSURE_RATIO = 1.023

    private const val MIN_SIGNIFICANT_ISO_RATIO = 1.023

    data class ChaseAdjustResult<T>(
        val value: T,
        val medianEma: Double = Double.NaN,
        val evEma: Double = Double.NaN,
        /** True when the caller should push a new repeating request. */
        val applied: Boolean,
    )

    /** Shorten exposure (or lower ISO) by [stops] (1.0 = one stop darker). */
    fun darkenExposureNs(
        exposureNs: Long,
        stops: Double,
        expRange: Range<Long>?,
    ): Long {
        if (stops <= 0.0) return exposureNs
        var next = (exposureNs / 2.0.pow(stops)).toLong().coerceAtLeast(1L)
        if (expRange != null) {
            next = next.coerceIn(expRange.lower, expRange.upper)
        }
        return next
    }

    /** Lengthen exposure by [stops] (1.0 = one stop brighter). */
    fun brightenExposureNs(
        exposureNs: Long,
        stops: Double,
        expRange: Range<Long>?,
    ): Long {
        if (stops <= 0.0) return exposureNs
        var next = (exposureNs * 2.0.pow(stops)).toLong().coerceAtLeast(1L)
        if (expRange != null) {
            next = next.coerceIn(expRange.lower, expRange.upper)
        }
        return next
    }

    fun darkenIso(
        iso: Int,
        stops: Double,
        isoRange: Range<Int>?,
        band: ReadoutIsoBand,
    ): Int {
        if (stops <= 0.0) return iso
        val scaled = (iso / 2.0.pow(stops)).toInt().coerceAtLeast(1)
        return band.clampPick(isoRange, scaled)
    }

    fun medianBin(histogram256: IntArray): Int {
        require(histogram256.size == 256)
        val total = histogram256.fold(0L) { acc, n -> acc + n }
        if (total <= 0L) return TARGET_MEDIAN_BIN
        val half = total / 2
        var running = 0L
        for (bin in 0..255) {
            running += histogram256[bin]
            if (running >= half) return bin
        }
        return 255
    }

    fun smoothMedian(ema: Double, sampleBin: Int): Double =
        if (ema.isNaN()) {
            sampleBin.toDouble()
        } else {
            ema * (1.0 - MEDIAN_EMA_ALPHA) + sampleBin * MEDIAN_EMA_ALPHA
        }

    /** Same time constant as [PreviewController.smoothHighlightMeterEv] for H-dial chase. */
    fun smoothEv(ema: Double, rawEv: Double): Double =
        if (ema.isNaN()) {
            rawEv
        } else if (rawEv <= 0.0) {
            ema * 0.78 + rawEv * 0.22
        } else {
            ema * 0.92 + rawEv * 0.08
        }

    fun adjustExposureNs(
        currentNs: Long,
        medianEma: Double,
        expRange: Range<Long>?,
    ): ChaseAdjustResult<Long> {
        if (abs(medianEma - TARGET_MEDIAN_BIN) < MEDIAN_DEADBAND_BINS) {
            return ChaseAdjustResult(currentNs, medianEma, applied = false)
        }
        val targetNs =
            equilibriumExposureNs(currentNs, medianEma, expRange)
        val next = blendExposure(currentNs, targetNs, LUMINANCE_BLEND_ALPHA, expRange)
        val applied = exposureChangeSignificant(currentNs, next)
        return ChaseAdjustResult(next, medianEma, applied = applied)
    }

    fun adjustIso(
        current: Int,
        medianEma: Double,
        isoRange: Range<Int>?,
        band: ReadoutIsoBand,
    ): ChaseAdjustResult<Int> {
        if (abs(medianEma - TARGET_MEDIAN_BIN) < MEDIAN_DEADBAND_BINS) {
            return ChaseAdjustResult(current, medianEma, applied = false)
        }
        val ratio = TARGET_MEDIAN_BIN / medianEma
        val target = band.clampPick(isoRange, (current * ratio).toInt().coerceAtLeast(1))
        val next = blendIso(current, target, LUMINANCE_BLEND_ALPHA, isoRange, band)
        val applied = isoChangeSignificant(current, next)
        return ChaseAdjustResult(next, medianEma, applied = applied)
    }

    fun adjustExposureNsFromEv(
        currentNs: Long,
        evEma: Double,
        expRange: Range<Long>?,
    ): ChaseAdjustResult<Long> {
        if (abs(evEma) < MIN_EV_STEP) {
            return ChaseAdjustResult(currentNs, evEma = evEma, applied = false)
        }
        val clampedEv = evEma.coerceIn(-MAX_EV_STEP, MAX_EV_STEP)
        val factor = 2.0.pow(clampedEv)
        var next = (currentNs * factor).toLong().coerceAtLeast(1L)
        if (expRange != null) {
            next = next.coerceIn(expRange.lower, expRange.upper)
        }
        val applied = exposureChangeSignificant(currentNs, next)
        return ChaseAdjustResult(next, evEma = evEma, applied = applied)
    }

    fun adjustIsoFromEv(
        current: Int,
        evEma: Double,
        isoRange: Range<Int>?,
        band: ReadoutIsoBand,
    ): ChaseAdjustResult<Int> {
        if (abs(evEma) < MIN_EV_STEP) {
            return ChaseAdjustResult(current, evEma = evEma, applied = false)
        }
        val clampedEv = evEma.coerceIn(-MAX_EV_STEP, MAX_EV_STEP)
        val factor = 2.0.pow(clampedEv)
        val scaled = (current * factor).toInt().coerceAtLeast(1)
        val next = band.clampPick(isoRange, scaled)
        val applied = isoChangeSignificant(current, next)
        return ChaseAdjustResult(next, evEma = evEma, applied = applied)
    }

    private fun equilibriumExposureNs(
        currentNs: Long,
        medianEma: Double,
        expRange: Range<Long>?,
    ): Long {
        if (medianEma <= 0.0) return currentNs
        val ratio = TARGET_MEDIAN_BIN / medianEma
        var target = (currentNs * ratio).toLong().coerceAtLeast(1L)
        if (expRange != null) {
            target = target.coerceIn(expRange.lower, expRange.upper)
        }
        return target
    }

    private fun blendExposure(
        current: Long,
        target: Long,
        alpha: Double,
        expRange: Range<Long>?,
    ): Long {
        val next = (current * (1.0 - alpha) + target * alpha).toLong().coerceAtLeast(1L)
        return if (expRange != null) {
            next.coerceIn(expRange.lower, expRange.upper)
        } else {
            next
        }
    }

    private fun blendIso(
        current: Int,
        target: Int,
        alpha: Double,
        isoRange: Range<Int>?,
        band: ReadoutIsoBand,
    ): Int {
        val blended = (current * (1.0 - alpha) + target * alpha).toInt().coerceAtLeast(1)
        return band.clampPick(isoRange, blended)
    }

    private fun exposureChangeSignificant(
        current: Long,
        next: Long,
    ): Boolean {
        if (current == next) return false
        val ratio = next.toDouble() / current.toDouble()
        return ratio <= 1.0 / MIN_SIGNIFICANT_EXPOSURE_RATIO ||
            ratio >= MIN_SIGNIFICANT_EXPOSURE_RATIO
    }

    private fun isoChangeSignificant(
        current: Int,
        next: Int,
    ): Boolean {
        if (current == next) return false
        val ratio = next.toDouble() / current.toDouble()
        return ratio <= 1.0 / MIN_SIGNIFICANT_ISO_RATIO ||
            ratio >= MIN_SIGNIFICANT_ISO_RATIO
    }
}
