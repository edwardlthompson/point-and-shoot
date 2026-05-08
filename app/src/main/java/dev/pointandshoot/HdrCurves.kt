package dev.pointandshoot

import kotlin.math.pow

/**
 * Pure-data transfer-function library used by every place Point & Shoot
 * needs to convert between display-encoded values (`sRGB`, `Rec.709`,
 * `PQ`, `HLG`) and scene-linear floats per BUILD_PLAN §7
 * "Color management, calibration & LUT pipeline" + the Phase 1 capture
 * engine's HDR (10-bit AVIF, 12-bit JXL) path.
 *
 * Every function operates on a single normalized `Float` in `[0, 1]` so
 * the same primitives can be lifted across hot per-pixel loops, GLES
 * shader generation (the GLSL form of these curves is one-to-one with
 * the Kotlin form), and unit tests. Inputs outside `[0, 1]` are NOT
 * clamped (the call site is expected to clamp before / after if the
 * downstream pipeline requires it); each curve is monotonic and
 * well-defined for slightly-negative + slightly-greater-than-1 inputs
 * (we use `pow` directly rather than `kotlin.math.expm1` workarounds so
 * floating-point parity with the GLSL `pow` builtin is preserved).
 *
 * The math is all CIE / IEC / ITU-R standards data (sRGB IEC 61966-2-1;
 * Rec.709 = ITU-R BT.709-6; PQ = SMPTE ST 2084; HLG = ITU-R BT.2100). No
 * trademarked source — every coefficient is published in the
 * corresponding standard.
 *
 * No Android imports - safe for unit testing on the JVM.
 */
object HdrCurves {

    /** Bumped only when the implementations gain a new revision (e.g. errata). */
    const val SCHEMA_VERSION: Int = 1

    // ---------- sRGB (IEC 61966-2-1) ----------

    /**
     * sRGB EOTF: encoded `[0, 1]` -> linear `[0, 1]`. Piecewise: linear
     * segment for low values, gamma-2.4 for high values (with the
     * canonical 1.055 / 0.055 / 0.04045 / 12.92 break point).
     */
    fun srgbToLinear(encoded: Float): Float {
        return if (encoded <= 0.04045f) {
            encoded / 12.92f
        } else {
            ((encoded + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }

    /**
     * sRGB OETF: linear `[0, 1]` -> encoded `[0, 1]`. Inverse of
     * [srgbToLinear]; piecewise linear / gamma-1/2.4.
     */
    fun linearToSrgb(linear: Float): Float {
        return if (linear <= 0.0031308f) {
            linear * 12.92f
        } else {
            (1.055f * linear.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()
        }
    }

    // ---------- Rec.709 (ITU-R BT.709-6) ----------

    /**
     * Rec.709 EOTF: encoded `[0, 1]` -> linear `[0, 1]`. Piecewise
     * linear / gamma-`(1 / 0.45)` per BT.709-6. The 0.018 / 4.5 / 0.099
     * break point matches the spec's formula 1.2.
     */
    fun rec709ToLinear(encoded: Float): Float {
        return if (encoded < 4.5f * 0.018f) {
            encoded / 4.5f
        } else {
            ((encoded + 0.099f) / 1.099f).toDouble().pow(1.0 / 0.45).toFloat()
        }
    }

    /**
     * Rec.709 OETF: linear `[0, 1]` -> encoded `[0, 1]`. Inverse of
     * [rec709ToLinear].
     */
    fun linearToRec709(linear: Float): Float {
        return if (linear < 0.018f) {
            4.5f * linear
        } else {
            (1.099f * linear.toDouble().pow(0.45) - 0.099).toFloat()
        }
    }

    // ---------- PQ (SMPTE ST 2084) ----------

    /** Peak luminance the PQ EOTF is normalized to (cd/m^2). */
    const val PQ_PEAK_NITS: Float = 10000f

    /**
     * PQ EOTF: encoded `[0, 1]` -> normalized linear `[0, 1]` (1.0
     * corresponds to [PQ_PEAK_NITS]). Implements ST 2084 equation A.4.
     */
    fun pqToLinear(encoded: Float): Float {
        val e = encoded.toDouble()
        val numerator = (e.pow(1.0 / PQ_M2) - PQ_C1).coerceAtLeast(0.0)
        val denominator = PQ_C2 - PQ_C3 * e.pow(1.0 / PQ_M2)
        return (numerator / denominator).pow(1.0 / PQ_M1).toFloat()
    }

    /**
     * PQ OETF: normalized linear `[0, 1]` (1.0 == [PQ_PEAK_NITS])
     * -> encoded `[0, 1]`. Inverse of [pqToLinear].
     */
    fun linearToPq(linear: Float): Float {
        val l = linear.toDouble().coerceAtLeast(0.0).pow(PQ_M1)
        return (
            (PQ_C1 + PQ_C2 * l) /
                (1.0 + PQ_C3 * l)
            ).pow(PQ_M2).toFloat()
    }

    // ---------- HLG (ITU-R BT.2100) ----------

    /** HLG normalization: `1.0` linear corresponds to peak nominal luminance. */
    const val HLG_PEAK_NITS: Float = 1000f

    /**
     * HLG OETF (display-light to reference HDR signal): linear
     * `[0, 1]` -> encoded `[0, 1]`. Implements BT.2100 Table 5.
     */
    fun linearToHlg(linear: Float): Float {
        val l = linear.coerceAtLeast(0f)
        return if (l <= 1f / 12f) {
            kotlin.math.sqrt(3f * l)
        } else {
            (HLG_A * kotlin.math.ln(12f * l - HLG_B) + HLG_C).toFloat()
        }
    }

    /**
     * HLG EOTF (reference HDR signal back to scene linear): encoded
     * `[0, 1]` -> linear `[0, 1]`. Inverse of [linearToHlg].
     */
    fun hlgToLinear(encoded: Float): Float {
        val e = encoded.coerceAtLeast(0f)
        return if (e <= 0.5f) {
            (e * e) / 3f
        } else {
            ((kotlin.math.exp(((e - HLG_C) / HLG_A).toDouble()).toFloat() + HLG_B) / 12f)
        }
    }

    // ---------- Convenience aliases used by tests + GLSL generation ----------

    /**
     * Identity transfer function. Useful as the "no transform" branch
     * in unit tests so the same plumbing exercises every curve.
     */
    fun identity(value: Float): Float = value

    // ---------- PQ / HLG constants (SMPTE ST 2084 / BT.2100) ----------

    private const val PQ_M1: Double = 2610.0 / 16384.0
    private const val PQ_M2: Double = 2523.0 / 4096.0 * 128.0
    private const val PQ_C1: Double = 3424.0 / 4096.0
    private const val PQ_C2: Double = 2413.0 / 4096.0 * 32.0
    private const val PQ_C3: Double = 2392.0 / 4096.0 * 32.0

    // BT.2100 HLG reference constants (table 5).
    private const val HLG_A: Double = 0.17883277
    private const val HLG_B: Float = 0.28466892f
    private const val HLG_C: Double = 0.55991073
}
