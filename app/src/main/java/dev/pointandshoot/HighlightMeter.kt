package dev.pointandshoot

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Highlight-weighted metering per BUILD_PLAN §4 (Phase 1): **expose-for-highlights** with
 * **scene scaling** — tiny hot speculars (filaments, sun disk) still pull hard; uniformly bright
 * or high-key scenes raise the effective highlight target so mids and ambient brightness are not
 * crushed. **Evenly lit scenes without a bright upper tail** leave correction near **0** so preview
 * matches normal AE; brighten-toward-ceiling is suppressed when there is no highlight headroom to
 * trade against.
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
 * 5. **Clip-save engagement:** negative EV is scaled by a weight that stays **~0** until the
 *    histogram shows **near-clip** stress (upper tail / 99.5th percentile / mass at bin 245+), then
 *    ramps toward **1** so flat and mid-key scenes match **Auto** while highlights get protection.
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

    /** Hot-pixel tail bin for clip engagement heuristics (see [fractionAtOrAbove]). */
    private const val NEAR_CLIP_PIXEL_BIN_245: Int = 245

    /** 99.5th percentile lower-tail mass for engagement. */
    private const val LOWER_TAIL_PERCENTILE_995: Double = 0.995

    /** Denominator guard when (CLIP_FRAC245_HIGH - CLIP_FRAC245_LOW) is tiny. */
    private const val CLIP_FRAC_DENOM_EPSILON: Double = 1e-12

    /**
     * Histogram-derived highlight suggestion split so the preview engine can **temporally smooth**
     * [darkenEngagement] (reduces breathing) while keeping [evCore] a pure per-frame function of the
     * histogram. For negative [evCore], effective EV is `evCore * smoothedEngagement`.
     */
    data class HighlightEvBreakdown(
        /** EV after gain, optional compress, and clamp — **before** multiplying [darkenEngagement]. */
        val evCore: Double,
        /** In `[0, 1]`; multiply into negative [evCore] only. */
        val darkenEngagement: Double,
    )

    /** Lightweight histogram stats for adb / debug when triaging H-mode metering. */
    data class HistogramDiagnostics(
        val p50: Int,
        val pMetered: Int,
        val fracAtOrAbove245: Double,
        val fracAt255: Double,
    )

    /**
     * YUV analysis frames can be **0xFF-filled** for a short window after session start (or when
     * the HAL has not delivered valid luma yet). Treating that as “scene at clip” pegs AE comp at
     * min and makes H mode look broken vs Auto.
     */
    fun isUntrustedAnalysisHistogram(histogram256: IntArray): Boolean {
        require(histogram256.size == 256) { "expected 256-bin histogram, got ${histogram256.size}" }
        val total = histogram256.fold(0L) { acc, n -> acc + n }
        if (total <= 0L) return true
        val frac255 = fractionAtOrAbove(histogram256, total, 255)
        val p50 = lowerTailBin(histogram256, total, 0.5)
        return frac255 > UNTRUSTED_GARBAGE_FRAC255 && p50 >= UNTRUSTED_GARBAGE_P50_MIN
    }

    fun histogramDiagnostics(histogram256: IntArray): HistogramDiagnostics? {
        require(histogram256.size == 256) { "expected 256-bin histogram, got ${histogram256.size}" }
        val total = histogram256.fold(0L) { acc, n -> acc + n }
        if (total <= 0L) return null
        val pTail = lowerTailBin(histogram256, total, DEFAULT_BRIGHT_TAIL_PERCENTILE)
        val minSupport = minPeakSupportCount(total)
        val pPeak = highestBinWithMinSupport(histogram256, minSupport)
        val pMetered =
            if (DEFAULT_BRIGHT_TAIL_PERCENTILE >= PEAK_BLEND_MIN_PERCENTILE) {
                max(pTail, pPeak)
            } else {
                pTail
            }
        return HistogramDiagnostics(
            p50 = lowerTailBin(histogram256, total, 0.5),
            pMetered = pMetered,
            fracAtOrAbove245 = fractionAtOrAbove(histogram256, total, NEAR_CLIP_PIXEL_BIN_245),
            fracAt255 = fractionAtOrAbove(histogram256, total, 255),
        )
    }

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
        val b = suggestEvCorrectionBreakdown(histogram256, percentile, ceilingValue, darkenGain, maxAbsEv)
        return if (b.evCore < 0.0) b.evCore * b.darkenEngagement else b.evCore
    }

    /**
     * Same inputs as [suggestEvCorrection]; exposes [HighlightEvBreakdown.evCore] before engagement
     * so the engine can smooth [HighlightEvBreakdown.darkenEngagement].
     */
    fun suggestEvCorrectionBreakdown(
        histogram256: IntArray,
        percentile: Double = DEFAULT_BRIGHT_TAIL_PERCENTILE,
        ceilingValue: Int = DEFAULT_HIGHLIGHT_CEILING,
        darkenGain: Double = DEFAULT_HIGHLIGHT_DARKEN_GAIN,
        maxAbsEv: Double = 24.0,
    ): HighlightEvBreakdown {
        require(histogram256.size == 256) { "expected 256-bin histogram, got ${histogram256.size}" }
        require(percentile > 0.0 && percentile <= 1.0) { "percentile out of range: $percentile" }
        require(ceilingValue in 1..255) { "ceilingValue out of range: $ceilingValue" }
        require(darkenGain >= 1.0) { "darkenGain must be >= 1.0: $darkenGain" }

        val total = histogram256.fold(0L) { acc, n -> acc + n }
        if (total <= 0L) return HighlightEvBreakdown(0.0, 0.0)

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

        if (p == effectiveCeiling) return HighlightEvBreakdown(0.0, 0.0)

        val frac245 = fractionAtOrAbove(histogram256, total, NEAR_CLIP_PIXEL_BIN_245)
        val p50 = lowerTailBin(histogram256, total, 0.5)
        val frac255 = fractionAtOrAbove(histogram256, total, 255)
        val p995 = lowerTailBin(histogram256, total, LOWER_TAIL_PERCENTILE_995)
        val darkenEngage =
            darkenEngagementWeight(frac245, p995, p) *
                bulkHighlightTailEngagementScale(p50, frac255)

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

        ev = clamp(ev, -maxAbsEv, maxAbsEv)

        return when {
            ev < 0.0 -> HighlightEvBreakdown(ev, darkenEngage)
            ev > 0.0 && shouldSuppressPositiveBrighten(frac245, p995) -> HighlightEvBreakdown(0.0, darkenEngage)
            else -> HighlightEvBreakdown(ev, darkenEngage)
        }
    }

    /**
     * Weight in **[0, 1]** for how much **negative EV** (exposure pull to save highlights) applies.
     * Stays **~0** until **near-clip** signals appear so **H ≈ Auto** on flat / mid-key frames; ramps
     * as bin **245+** mass, **99.5th percentile** luma, or the metered peak approach clipping.
     */
    fun darkenEngagementWeight(fracAtOrAbove245: Double, p995Bin: Int, pMetered: Int): Double {
        val w245 =
            smoothstep01(
                ((fracAtOrAbove245 - CLIP_FRAC245_LOW) /
                    (CLIP_FRAC245_HIGH - CLIP_FRAC245_LOW).coerceAtLeast(CLIP_FRAC_DENOM_EPSILON)),
            )
        val w995 =
            smoothstep01(
                ((p995Bin.toDouble() - CLIP_P995_LOW) / (CLIP_P995_HIGH - CLIP_P995_LOW))
                    .coerceIn(0.0, 1.0),
            )
        val wPeak =
            smoothstep01(
                ((pMetered.toDouble() - CLIP_P_LOW) / (CLIP_P_HIGH - CLIP_P_LOW))
                    .coerceIn(0.0, 1.0),
            )
        return max(max(w245, w995), wPeak).coerceIn(0.0, 1.0)
    }

    /**
     * When bulk luminance is low but only a **moderate** fraction sits at bin 255, do not apply the
     * full clip-save pull (avoids pegging min AE comp on mostly-dark scenes with a small window /
     * lamp tail). Tiny speculars (frac255 ≪ 1%) still keep strong engagement via [modestTail] near 0.
     */
    fun bulkHighlightTailEngagementScale(p50: Int, fracAt255: Double): Double {
        if (fracAt255 >= BULK_TAIL_FRAC255_HIGH || p50 >= BULK_P50_BRIGHT) return 1.0
        // Tiny speculars / filaments: keep full engagement (do not soften sun-disk pulls).
        if (fracAt255 < BULK_TAIL_FRAC255_LOW) return 1.0
        val darkBulk =
            smoothstep01((BULK_P50_DARK_REF - p50.toDouble()) / BULK_P50_DARK_SPAN)
        val modestTail =
            smoothstep01((BULK_TAIL_FRAC255_HIGH - fracAt255) / (BULK_TAIL_FRAC255_HIGH - BULK_TAIL_FRAC255_LOW))
        return (1.0 - darkBulk * modestTail * BULK_TAIL_ENGAGE_SUPPRESS).coerceIn(BULK_TAIL_ENGAGE_MIN, 1.0)
    }

    private const val BULK_P50_DARK_REF: Double = 62.0
    private const val BULK_P50_DARK_SPAN: Double = 35.0
    private const val BULK_P50_BRIGHT: Int = 66
    /** Tail mass below this is treated as specular / filament (full engagement). */
    private const val BULK_TAIL_FRAC255_LOW: Double = 0.02
    private const val BULK_TAIL_FRAC255_HIGH: Double = 0.14
    private const val BULK_TAIL_ENGAGE_SUPPRESS: Double = 0.88
    private const val BULK_TAIL_ENGAGE_MIN: Double = 0.12

    /** No near-clip mass and modest 99.5% bin → do not brighten toward the highlight ceiling. */
    private fun shouldSuppressPositiveBrighten(fracAtOrAbove245: Double, p995Bin: Int): Boolean =
        fracAtOrAbove245 < POSITIVE_SUPPRESS_FRAC245 && p995Bin < POSITIVE_SUPPRESS_P995

    /** Fraction of pixels at or above bin 245; ramps engagement for clip-save behavior. */
    private const val CLIP_FRAC245_LOW: Double = 7e-6

    private const val CLIP_FRAC245_HIGH: Double = 0.00085

    /** 99.5th percentile below this → treat as no highlight headroom to manage (Auto-like). */
    private const val CLIP_P995_LOW: Double = 214.0

    private const val CLIP_P995_HIGH: Double = 248.0

    /** Metered highlight bin must approach white before peak-alone engagement ramps. */
    private const val CLIP_P_LOW: Double = 234.0

    /** YUV garbage frame: nearly all pixels at bin 255 with bright median. */
    private const val UNTRUSTED_GARBAGE_FRAC255: Double = 0.92

    private const val UNTRUSTED_GARBAGE_P50_MIN: Int = 247

    private const val CLIP_P_HIGH: Double = 252.0

    private const val POSITIVE_SUPPRESS_FRAC245: Double = 9e-6
    private const val POSITIVE_SUPPRESS_P995: Int = 154

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
