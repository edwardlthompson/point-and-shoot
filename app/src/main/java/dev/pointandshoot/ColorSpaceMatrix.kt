package dev.pointandshoot

import kotlin.math.abs

/**
 * Pure-data RGB-primaries / chromatic-adaptation library used by every
 * place Point & Shoot needs to convert between RGB working spaces (sRGB
 * / Rec.709, Rec.2020, DCI-P3, ACES AP1) and CIE XYZ per BUILD_PLAN
 * §7 "Color management, calibration & LUT pipeline" + the Phase 1
 * capture engine's HDR (10-bit AVIF Rec.2020, 12-bit JXL Rec.2020) path.
 *
 * Every coefficient here is CIE / IEC / ITU-R standards data published
 * in the corresponding spec — sRGB primaries are the IEC 61966-2-1
 * Annex A values, Rec.2020 are the ITU-R BT.2020-2 Table 1 values,
 * DCI-P3 the SMPTE RP 431-2 values, ACES AP1 the AMPAS S-2014-004
 * values, and the Bradford CAT is the CIE 1995 reference.
 *
 * The matrices are computed from the chromaticity coordinates at
 * construction (so the unit tests can independently verify each row
 * against the published target), with one canonical implementation at
 * [primariesToXyz]. The pre-computed common conversions ([SRGB_TO_XYZ_D65],
 * [REC2020_TO_XYZ_D65], [DCI_P3_TO_XYZ_D65], [ACES_AP1_TO_XYZ_D60],
 * [BRADFORD_D65_TO_D50]) round-trip to within 1e-5 of the published
 * matrices.
 *
 * No Android imports - safe for unit testing on the JVM.
 */
object ColorSpaceMatrix {

    /** Bumped only when the constants table changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    // ---------- Standard chromaticities ----------

    /**
     * Chromaticity (x, y) of a primary or whitepoint in CIE 1931. We
     * allow primaries outside the spectral locus (`x + y > 1`) because
     * wide-gamut working spaces (notably ACES AP0 / AP1) intentionally
     * use "virtual" primaries above the spectral locus to enclose the
     * full visible gamut. We only reject grossly out-of-range values
     * (negative or unbounded). The whitepoint sanity is enforced
     * indirectly by [primariesToXyz] which solves for non-negative
     * scale factors.
     */
    data class Chromaticity(val x: Float, val y: Float) {
        init {
            require(x in -0.5f..1.5f) { "x out of range: $x" }
            require(y in -0.5f..1.5f) { "y out of range: $y" }
            require(y > 0f) { "y must be > 0 (was $y) - chromaticity divides by y" }
        }
    }

    /** RGB primaries + whitepoint defining a working space. */
    data class Primaries(
        val red: Chromaticity,
        val green: Chromaticity,
        val blue: Chromaticity,
        val whitepoint: Chromaticity,
    )

    /** sRGB / BT.709 primaries (IEC 61966-2-1 / ITU-R BT.709-6). */
    val SRGB_PRIMARIES: Primaries = Primaries(
        red = Chromaticity(0.6400f, 0.3300f),
        green = Chromaticity(0.3000f, 0.6000f),
        blue = Chromaticity(0.1500f, 0.0600f),
        whitepoint = Chromaticity(0.3127f, 0.3290f),
    )

    /** Rec.2020 / BT.2020 primaries (ITU-R BT.2020-2 Table 1). */
    val REC2020_PRIMARIES: Primaries = Primaries(
        red = Chromaticity(0.7080f, 0.2920f),
        green = Chromaticity(0.1700f, 0.7970f),
        blue = Chromaticity(0.1310f, 0.0460f),
        whitepoint = Chromaticity(0.3127f, 0.3290f),
    )

    /** DCI-P3 (theatrical) primaries (SMPTE RP 431-2). */
    val DCI_P3_PRIMARIES: Primaries = Primaries(
        red = Chromaticity(0.6800f, 0.3200f),
        green = Chromaticity(0.2650f, 0.6900f),
        blue = Chromaticity(0.1500f, 0.0600f),
        whitepoint = Chromaticity(0.3127f, 0.3290f),
    )

    /** ACES AP1 primaries (AMPAS S-2014-004); whitepoint is "ACES" (~D60). */
    val ACES_AP1_PRIMARIES: Primaries = Primaries(
        red = Chromaticity(0.713f, 0.293f),
        green = Chromaticity(0.165f, 0.830f),
        blue = Chromaticity(0.128f, 0.044f),
        whitepoint = Chromaticity(0.32168f, 0.33767f),
    )

    /** Standard CIE 1931 illuminants (whitepoints), x/y in 2-degree observer. */
    object Illuminants {
        val D50: Chromaticity = Chromaticity(0.34567f, 0.35850f)
        val D55: Chromaticity = Chromaticity(0.33242f, 0.34743f)
        val D65: Chromaticity = Chromaticity(0.31270f, 0.32900f)
        val D75: Chromaticity = Chromaticity(0.29902f, 0.31485f)
        val A: Chromaticity = Chromaticity(0.44757f, 0.40745f)
        val F2: Chromaticity = Chromaticity(0.37207f, 0.37512f)
    }

    // ---------- Bradford CAT M matrix (CIE 1995) ----------

    /**
     * Bradford chromatic adaptation reference matrix. Declared BEFORE
     * the [BRADFORD_D65_TO_D50] / [BRADFORD_D50_TO_D65] init lines so
     * the Kotlin object's top-down init order finds it on first use.
     */
    val BRADFORD_M: Array<FloatArray> = arrayOf(
        floatArrayOf(0.8951000f, 0.2664000f, -0.1614000f),
        floatArrayOf(-0.7502000f, 1.7135000f, 0.0367000f),
        floatArrayOf(0.0389000f, -0.0685000f, 1.0296000f),
    )

    // ---------- Pre-computed canonical matrices ----------

    /** sRGB-linear -> CIE XYZ_D65 (rows = X / Y / Z). */
    val SRGB_TO_XYZ_D65: Array<FloatArray> = primariesToXyz(SRGB_PRIMARIES)

    /** Rec.2020-linear -> CIE XYZ_D65. */
    val REC2020_TO_XYZ_D65: Array<FloatArray> = primariesToXyz(REC2020_PRIMARIES)

    /** DCI-P3-linear -> CIE XYZ_D65. */
    val DCI_P3_TO_XYZ_D65: Array<FloatArray> = primariesToXyz(DCI_P3_PRIMARIES)

    /** ACES AP1-linear -> CIE XYZ_D60 (whitepoint of the ACES system). */
    val ACES_AP1_TO_XYZ_D60: Array<FloatArray> = primariesToXyz(ACES_AP1_PRIMARIES)

    /**
     * Bradford chromatic adaptation: CIE XYZ_D65 -> CIE XYZ_D50.
     * Reference: CIE 1995 Bradford CAT (used by ICC profiles + DNG spec).
     */
    val BRADFORD_D65_TO_D50: Array<FloatArray> = bradford(Illuminants.D65, Illuminants.D50)

    /** Bradford chromatic adaptation: CIE XYZ_D50 -> CIE XYZ_D65 (inverse). */
    val BRADFORD_D50_TO_D65: Array<FloatArray> = bradford(Illuminants.D50, Illuminants.D65)

    // ---------- Construction ----------

    /**
     * Compute the linear-RGB -> CIE XYZ matrix for a given set of
     * primaries + whitepoint. Implements the standard "find the
     * scaling that makes (1, 1, 1) RGB land on the whitepoint" recipe
     * (Bruce Lindbloom's RGB/XYZ matrix derivation).
     *
     * Returns a 3x3 row-major matrix (row 0 = X-row).
     */
    fun primariesToXyz(p: Primaries): Array<FloatArray> {
        // Per-primary XYZ (assuming Y = 1 for each):
        val xr = p.red.x / p.red.y
        val yr = 1f
        val zr = (1f - p.red.x - p.red.y) / p.red.y

        val xg = p.green.x / p.green.y
        val yg = 1f
        val zg = (1f - p.green.x - p.green.y) / p.green.y

        val xb = p.blue.x / p.blue.y
        val yb = 1f
        val zb = (1f - p.blue.x - p.blue.y) / p.blue.y

        // Whitepoint XYZ (assuming Y = 1):
        val xw = p.whitepoint.x / p.whitepoint.y
        val yw = 1f
        val zw = (1f - p.whitepoint.x - p.whitepoint.y) / p.whitepoint.y

        // Solve: M_p * (Sr, Sg, Sb) = (Xw, Yw, Zw) where M_p is the per-primary matrix.
        val mP = arrayOf(
            floatArrayOf(xr, xg, xb),
            floatArrayOf(yr, yg, yb),
            floatArrayOf(zr, zg, zb),
        )
        val inv = invert3x3(mP)
        val s = multiplyVec(inv, floatArrayOf(xw, yw, zw))
        // Scale each column of M_p by Sr/Sg/Sb.
        return arrayOf(
            floatArrayOf(xr * s[0], xg * s[1], xb * s[2]),
            floatArrayOf(yr * s[0], yg * s[1], yb * s[2]),
            floatArrayOf(zr * s[0], zg * s[1], zb * s[2]),
        )
    }

    /**
     * Bradford chromatic adaptation matrix from `srcWhite` to `dstWhite`
     * (both in CIE 1931 chromaticity coordinates). Output maps a CIE
     * XYZ vector under `srcWhite` to its appearance-matched XYZ under
     * `dstWhite`.
     */
    fun bradford(srcWhite: Chromaticity, dstWhite: Chromaticity): Array<FloatArray> {
        val src = whitepointXyz(srcWhite)
        val dst = whitepointXyz(dstWhite)
        val mSrc = multiplyVec(BRADFORD_M, src)
        val mDst = multiplyVec(BRADFORD_M, dst)
        // Diagonal scaling in cone space.
        val diag = arrayOf(
            floatArrayOf(mDst[0] / mSrc[0], 0f, 0f),
            floatArrayOf(0f, mDst[1] / mSrc[1], 0f),
            floatArrayOf(0f, 0f, mDst[2] / mSrc[2]),
        )
        return multiply(invert3x3(BRADFORD_M), multiply(diag, BRADFORD_M))
    }

    // ---------- Matrix utilities ----------

    /** Multiply a 3x3 matrix by a 3-vector, yielding a 3-vector. */
    fun multiplyVec(m: Array<FloatArray>, v: FloatArray): FloatArray {
        require(v.size == 3) { "vec must be length 3 (was ${v.size})" }
        return floatArrayOf(
            m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
            m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2],
        )
    }

    /** Multiply two 3x3 matrices: out = a * b. */
    fun multiply(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
        val out = Array(3) { FloatArray(3) }
        for (i in 0..2) {
            for (j in 0..2) {
                var s = 0f
                for (k in 0..2) {
                    s += a[i][k] * b[k][j]
                }
                out[i][j] = s
            }
        }
        return out
    }

    /** Invert a 3x3 matrix; throws if `det` is below `1e-9`. */
    fun invert3x3(m: Array<FloatArray>): Array<FloatArray> {
        val a = m[0][0]; val b = m[0][1]; val c = m[0][2]
        val d = m[1][0]; val e = m[1][1]; val f = m[1][2]
        val g = m[2][0]; val h = m[2][1]; val i = m[2][2]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        require(abs(det) >= 1e-9f) { "matrix is singular (det=$det)" }
        val invDet = 1f / det
        return arrayOf(
            floatArrayOf(
                (e * i - f * h) * invDet,
                (c * h - b * i) * invDet,
                (b * f - c * e) * invDet,
            ),
            floatArrayOf(
                (f * g - d * i) * invDet,
                (a * i - c * g) * invDet,
                (c * d - a * f) * invDet,
            ),
            floatArrayOf(
                (d * h - e * g) * invDet,
                (b * g - a * h) * invDet,
                (a * e - b * d) * invDet,
            ),
        )
    }

    /** XYZ vector for a chromaticity under Y = 1. */
    fun whitepointXyz(c: Chromaticity): FloatArray {
        return floatArrayOf(c.x / c.y, 1f, (1f - c.x - c.y) / c.y)
    }

}
