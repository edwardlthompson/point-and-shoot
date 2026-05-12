package dev.pointandshoot

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Highlight-weighted metering per BUILD_PLAN §4 (Phase 1): **expose-for-highlights** with
 * **scene scaling** — tiny hot speculars (filaments, sun disk) still pull hard; uniformly bright
 * or high-key scenes raise the effective highlight target so mids and ambient brightness are not
 * crushed. Predominantly dark scenes get a modest brighten boost on the positive-EV path.
 *
 * **OEM-style goals (non-cloning):** documented “highlight-weighted” behaviors from compact-camera
 * marketing (preserve highlight gradation, tame spotlights, accept deeper shadows) are mapped to these
 * knobs in **BUILD_PLAN.md** → Milestone 4 → **Highlight (H) metering — OEM-style behavior mapping**.
 *
 * Strategy:
 * 1. Base [DEFAULT_HIGHLIGHT_CEILING] for **specular-weighted** frames (low bright-pixel fraction,
 *    low bulk luminance).
 * 2. [RELAXED_HIGHLIGHT_CEILING] blended in when the bulk of the histogram is already bright
 *    (diffuse / normal indoor-outdoor) or many pixels sit in upper mid-tones.
 * 3. Tiered minimum darken EV when above the **effective** ceiling (stronger near 255).
 * 4. [DEFAULT_HIGHLIGHT_DARKEN_GAIN] multiplies **negative** EV (kept modest to avoid clamp pegging).
 *
 * Pure data + pure functions; Phase 1 plugs the histogram from downsampled YUV preview.
 */
object HighlightMeter {

    /**
     * Default lower-tail mass: **1.0** = the tail sits on the **brightest occupied bin**
     * (any highlight mass sets the meter).
     */
    const val DEFAULT_BRIGHT_TAIL_PERCENTILE: Double = 1.0

    /**
     * Target luma (0–255) for the **brightest occupied** bin when the scene reads as **specular /
     * tiny hotspot** — aggressive pull for filaments and clips.
     */
    const val DEFAULT_HIGHLIGHT_CEILING: Int = 40

    /**
     * Upper bound for the **effective** highlight target when the histogram shows broad bright bulk
     * (normal brightness rooms, overcast, snowfields). Prevents treating every sunny wall like a sun disk.
     */
    const val RELAXED_HIGHLIGHT_CEILING: Int = 126

    /**
     * Applied to **negative** EV after log + tier floors.
     *
     * Do **not** crank this linear multiplier into double digits: tier floors are already several
     * stops negative; multiplying them makes almost every “above ceiling” frame hit [maxAbsEv] and
     * [CONTROL_AE_COMPENSATION_RANGE] min together → **one exposure for everything** (universal
     * darkness). Stronger filament / sun pulls come from matching still capture to preview AE comp,
     * not from blowing past adaptive range here.
     */
    const val DEFAULT_HIGHLIGHT_DARKEN_GAIN: Double = 2.55

    /**
     * @param histogram256 a 256-bin luminance histogram (bin 0 = darkest).
     * @param percentile   lower-tail mass in (0,1]; see [DEFAULT_BRIGHT_TAIL_PERCENTILE].
     * @param ceilingValue desired luma at the metered highlight level. Default [DEFAULT_HIGHLIGHT_CEILING].
     * @param darkenGain   multiply negative EV by this (default [DEFAULT_HIGHLIGHT_DARKEN_GAIN]).
     * @param maxAbsEv     clamps the suggested correction so a single frame can never demand more than this many stops.
     *
     * @return positive EV = brighten next frame; negative EV = darken next frame; 0 = no change needed.
     */
    fun suggestEvCorrection(
        histogram256: IntArray,
        percentile: Double = DEFAULT_BRIGHT_TAIL_PERCENTILE,
        ceilingValue: Int = DEFAULT_HIGHLIGHT_CEILING,
        darkenGain: Double = DEFAULT_HIGHLIGHT_DARKEN_GAIN,
        maxAbsEv: Double = 24.0,
    ): Double {
        require(histogram256.size == 256) { "expected 256-bin histogram, got ${histogram256.size}" }
        require(percentile > 0.0 && percentile <= 1.0) { "percentile out of range: $percentile" }
        require(ceilingValue in 1..255) { "ceilingValue out of range: $ceilingValue" }
        require(darkenGain >= 1.0) { "darkenGain must be >= 1.0: $darkenGain" }

        val total = histogram256.fold(0L) { acc, n -> acc + n }
        if (total <= 0L) return 0.0

        val pTail = lowerTailBin(histogram256, total, percentile)
        val minSupport = minPeakSupportCount(total)
        val pPeak = highestBinWithMinSupport(histogram256, minSupport)
        val p =
            if (percentile >= PEAK_BLEND_MIN_PERCENTILE) {
                max(pTail, pPeak)
            } else {
                pTail
            }

        val p75 = lowerTailBin(histogram256, total, 0.75)
        val fracHot = fractionAtOrAbove(histogram256, total, BRIGHT_PIXEL_BIN_START)
        val wDiffuse = diffuseCeilingBlendWeight(fracHot, p75)
        val relaxedTop = max(ceilingValue, RELAXED_HIGHLIGHT_CEILING).coerceIn(1, 255)
        val effectiveCeiling =
            (ceilingValue + (relaxedTop - ceilingValue) * wDiffuse)
                .toInt()
                .coerceIn(ceilingValue, relaxedTop)

        if (p == effectiveCeiling) return 0.0

        val current = p.coerceAtLeast(1).toDouble()
        val target8 = effectiveCeiling.toDouble()
        var ev = log2(target8 / current)

        if (p > effectiveCeiling) {
            val floorEv =
                when {
                    p >= 254 -> -5.35
                    p >= 252 -> -4.95
                    p >= 248 -> -4.35
                    p >= 244 -> -3.75
                    p >= 238 -> -3.15
                    p >= 232 -> -2.75
                    p >= 224 -> -2.45
                    p >= 218 -> -2.25
                    p >= 200 -> -2.05
                    else -> -1.92
                }
            ev = min(ev, floorEv)
        }

        if (ev < 0.0) {
            ev *= darkenGain
            // Soften mid-range pulls in diffuse / high-key scenes only; keep full gain for specular + near-clip peaks.
            if (p < NEAR_CLIP_FOR_COMPRESS && wDiffuse > COMPRESS_W_DIFFUSE_MIN) {
                ev = compressNegativeEv(ev)
            }
        } else if (ev > 0.0) {
            ev *= darkenBrightenBoostForMedian(lowerTailBin(histogram256, total, 0.5))
        }

        return clamp(ev, -maxAbsEv, maxAbsEv)
    }

    /** Fraction of pixels at or above this bin (hot sky, lamps, speculars). */
    private const val BRIGHT_PIXEL_BIN_START: Int = 192

    /** Below this fraction, keep the base ceiling (tiny bright tail / filament). */
    private const val DIFFUSE_HOT_FRAC_LOW: Double = 0.0045

    /** Above this fraction, treat highlight mass as largely diffuse / ambient. */
    private const val DIFFUSE_HOT_FRAC_HIGH: Double = 0.13

    /** 75th-percentile bin below this → bulk is not “already bright”; don’t relax for ceiling glare alone. */
    private const val DIFFUSE_P75_SOFT: Double = 58.0

    /** 75th-percentile bin above this → bulk is comfortably bright; ease off extreme highlight targeting. */
    private const val DIFFUSE_P75_HARD: Double = 96.0

    /**
     * Sublinear curve on negative EV after gain: strong near clip, softer for moderate over-ceiling
     * nudges so normal rooms don’t feel like a single global stop-down.
     */
    private fun compressNegativeEv(ev: Double): Double {
        val a = -ev
        val compressed = a.pow(NEGATIVE_EV_COMPRESS_POWER)
        return -compressed
    }

    private const val NEGATIVE_EV_COMPRESS_POWER: Double = 0.82

    /** At or above this bin, skip [compressNegativeEv] so near-clip peaks stay decisive. */
    private const val NEAR_CLIP_FOR_COMPRESS: Int = 246

    /** Require this much “diffuse” blend before softening negative EV (specular frames unchanged). */
    private const val COMPRESS_W_DIFFUSE_MIN: Double = 0.24

    /** Up to this multiplier on positive EV when the median pixel is quite dark. */
    private const val DARK_BRIGHTEN_BOOST_MAX: Double = 0.16

    private fun darkenBrightenBoostForMedian(p50: Int): Double {
        // median in ~[62 .. 28] ramps boost from 0 toward max (darker median → stronger brighten push)
        val span = (MEDIAN_BRIGHT_REF - MEDIAN_DARK_REF).toDouble()
        val t = ((MEDIAN_BRIGHT_REF - p50) / span).coerceIn(0.0, 1.0)
        val s = smoothstep01(t)
        return 1.0 + DARK_BRIGHTEN_BOOST_MAX * s
    }

    private const val MEDIAN_BRIGHT_REF: Int = 63
    private const val MEDIAN_DARK_REF: Int = 26

    /**
     * Weight in [0,1] for blending toward [RELAXED_HIGHLIGHT_CEILING]: high when many pixels are hot
     * **or** the bulk (p75) is already mid–high key (normal brightness without a tiny filament).
     */
    fun diffuseCeilingBlendWeight(fracHotPixels: Double, p75Bin: Int): Double {
        val wHot =
            smoothstep01(
                ((fracHotPixels - DIFFUSE_HOT_FRAC_LOW) /
                    (DIFFUSE_HOT_FRAC_HIGH - DIFFUSE_HOT_FRAC_LOW).coerceAtLeast(1e-9)),
            )
        val wBulk =
            smoothstep01(
                ((p75Bin.toDouble() - DIFFUSE_P75_SOFT) / (DIFFUSE_P75_HARD - DIFFUSE_P75_SOFT))
                    .coerceIn(0.0, 1.0),
            )
        return max(wHot, wBulk * 0.94).coerceIn(0.0, 1.0)
    }

    private fun fractionAtOrAbove(histogram256: IntArray, total: Long, startBin: Int): Double {
        val from = startBin.coerceIn(0, 255)
        var sum = 0L
        for (b in from..255) sum += histogram256[b]
        return sum.toDouble() / total.toDouble()
    }

    private fun smoothstep01(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private const val PEAK_BLEND_MIN_PERCENTILE: Double = 0.99

    private fun lowerTailBin(histogram256: IntArray, total: Long, percentile: Double): Int {
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
        return p
    }

    private fun highestBinWithMinSupport(histogram256: IntArray, minCount: Long): Int {
        for (bin in 255 downTo 0) {
            if (histogram256[bin].toLong() >= minCount) {
                return bin
            }
        }
        return 0
    }

    private fun minPeakSupportCount(total: Long): Long {
        val fromArea = (total.toDouble() / 600_000.0).toLong().coerceAtLeast(1L)
        return max(1L, fromArea)
    }

    private fun log2(x: Double): Double = ln(x) / LN2
    private val LN2 = ln(2.0)

    private fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))
}
