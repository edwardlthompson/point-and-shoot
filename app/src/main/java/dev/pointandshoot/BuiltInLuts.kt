@file:Suppress("MagicNumber")

package dev.pointandshoot

/**
 * Code-generated LUTs that ship with the runtime - no asset files, no
 * build-time download, no third-party blobs. Each function returns a fresh
 * [Lut3D] instance (callers may cache).
 *
 * Per BUILD_PLAN §7 ("Bundled LUTs"), every entry here MUST be derived from
 * either:
 *   * pure public-domain math (encoding identities, BT.601 / BT.709 luma
 *     weights), or
 *   * an original creation under Apache-2.0 by this project.
 *
 * No proprietary "free" LUT (Lightroom presets, DaVinci, FilmConvert, etc.)
 * may be reverse-engineered into a generator function here. The catalog test
 * (`LutCatalogTest`) enforces an SPDX whitelist so accidental drift is caught
 * at gate time.
 */
object BuiltInLuts {

    /**
     * Rec.709 identity: each cell maps to its own normalized RGB. This is the
     * canonical "no-op" LUT - the user-facing `LUT > None` selection routes
     * here so the apply path stays uniform (and the GLES shader can short-
     * circuit via [Lut3D.isIdentity]).
     *
     * License: public-domain (encoding identity, no creative content).
     */
    fun rec709Identity(size: Int = DEFAULT_SIZE): Lut3D = Lut3D.identity(size)

    /**
     * B&W via BT.601 luma weights: `Y = 0.299 R + 0.587 G + 0.114 B`. Output
     * R = G = B = Y at every grid cell, which matches the historical NTSC /
     * SDTV monochrome conversion. Slightly warmer-feeling than BT.709 because
     * of the higher red weight.
     *
     * License: public-domain (BT.601 luma weights are an ITU recommendation,
     * not copyrightable; the math itself is a fact).
     */
    fun bwBt601(size: Int = DEFAULT_SIZE): Lut3D = bwLut(size, R_BT601, G_BT601, B_BT601)

    /**
     * B&W via BT.709 luma weights: `Y = 0.2126 R + 0.7152 G + 0.0722 B`. The
     * HD-era / sRGB-display monochrome conversion; greens map slightly
     * brighter than BT.601 so foliage looks more luminous.
     *
     * License: public-domain (BT.709 luma weights are an ITU recommendation).
     */
    fun bwBt709(size: Int = DEFAULT_SIZE): Lut3D = bwLut(size, R_BT709, G_BT709, B_BT709)

    /**
     * "Point & Shoot Cinematic" - an original Apache-2.0 teal-orange grade.
     * Pulls shadows toward teal `(0.30, 0.55, 0.70)` and highlights toward
     * warm orange `(1.00, 0.65, 0.35)` based on the BT.709 luma of each cell;
     * mid-tones blend smoothly between the two via two Hermite weights so the
     * result is continuous and free of banding.
     *
     * Strength is capped at 30 % so the look reads as "graded", not a filter
     * costume. The grade is computed against the cell's normalized RGB only -
     * no upstream LUT, no proprietary preset, no reverse-engineered look.
     *
     * License: Apache-2.0 (original creation by the Point & Shoot project).
     */
    fun pnsCinematic(size: Int = DEFAULT_SIZE): Lut3D {
        require(size in Lut3D.SUPPORTED_SIZES) { "size must be in ${Lut3D.SUPPORTED_SIZES} (was $size)" }
        val out = FloatArray(size * size * size * 3)
        val denom = (size - 1).toFloat()
        for (b in 0 until size) {
            val bf = b / denom
            for (g in 0 until size) {
                val gf = g / denom
                for (r in 0 until size) {
                    val rf = r / denom
                    val luma = R_BT709 * rf + G_BT709 * gf + B_BT709 * bf
                    val shadowW = smoothstep(0.5f, 0.0f, luma) * CINEMATIC_STRENGTH
                    val highlightW = smoothstep(0.5f, 1.0f, luma) * CINEMATIC_STRENGTH
                    val outR = mix3(rf, SHADOW_R, HIGHLIGHT_R, shadowW, highlightW)
                    val outG = mix3(gf, SHADOW_G, HIGHLIGHT_G, shadowW, highlightW)
                    val outB = mix3(bf, SHADOW_B, HIGHLIGHT_B, shadowW, highlightW)
                    val idx = ((b * size + g) * size + r) * 3
                    out[idx] = outR.coerceIn(0f, 1f)
                    out[idx + 1] = outG.coerceIn(0f, 1f)
                    out[idx + 2] = outB.coerceIn(0f, 1f)
                }
            }
        }
        return Lut3D(size, out)
    }

    /**
     * Orange-mask negative invert: `1 - rgb` then a mild contrast stretch.
     * Original Apache-2.0 math for film-scan work.
     */
    fun negativeInvert(size: Int = DEFAULT_SIZE): Lut3D {
        require(size in Lut3D.SUPPORTED_SIZES) { "size must be in ${Lut3D.SUPPORTED_SIZES} (was $size)" }
        val out = FloatArray(size * size * size * 3)
        val denom = (size - 1).toFloat()
        for (b in 0 until size) {
            val bf = 1f - (b / denom)
            for (g in 0 until size) {
                val gf = 1f - (g / denom)
                for (r in 0 until size) {
                    val rf = 1f - (r / denom)
                    val idx = ((b * size + g) * size + r) * 3
                    out[idx] = (rf * 1.08f - 0.04f).coerceIn(0f, 1f)
                    out[idx + 1] = (gf * 1.08f - 0.04f).coerceIn(0f, 1f)
                    out[idx + 2] = (bf * 1.12f - 0.06f).coerceIn(0f, 1f)
                }
            }
        }
        return Lut3D(size, out)
    }

    private fun bwLut(size: Int, kr: Float, kg: Float, kb: Float): Lut3D {
        require(size in Lut3D.SUPPORTED_SIZES) { "size must be in ${Lut3D.SUPPORTED_SIZES} (was $size)" }
        val out = FloatArray(size * size * size * 3)
        val denom = (size - 1).toFloat()
        for (b in 0 until size) {
            val bf = b / denom
            for (g in 0 until size) {
                val gf = g / denom
                for (r in 0 until size) {
                    val rf = r / denom
                    val y = (kr * rf + kg * gf + kb * bf).coerceIn(0f, 1f)
                    val idx = ((b * size + g) * size + r) * 3
                    out[idx] = y
                    out[idx + 1] = y
                    out[idx + 2] = y
                }
            }
        }
        return Lut3D(size, out)
    }

    /** Hermite smoothstep `(t)` between [edge0] and [edge1]. */
    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Three-way blend: `base` -> `shadowTint` (by `ws`) and `base` -> `highlightTint` (by `wh`). */
    private fun mix3(base: Float, shadowTint: Float, highlightTint: Float, ws: Float, wh: Float): Float {
        val afterShadow = base * (1f - ws) + shadowTint * ws
        return afterShadow * (1f - wh) + highlightTint * wh
    }

    /** Default cube grid - matches the size used by `CalibrationToLut.toLut3D`. */
    const val DEFAULT_SIZE: Int = 33

    // BT.601 (Rec.601 / NTSC SDTV) luma coefficients.
    private const val R_BT601: Float = 0.299f
    private const val G_BT601: Float = 0.587f
    private const val B_BT601: Float = 0.114f

    // BT.709 (Rec.709 / sRGB display) luma coefficients.
    private const val R_BT709: Float = 0.2126f
    private const val G_BT709: Float = 0.7152f
    private const val B_BT709: Float = 0.0722f

    // Cinematic grade tints + strength (Apache-2.0; original to this project).
    private const val SHADOW_R: Float = 0.30f
    private const val SHADOW_G: Float = 0.55f
    private const val SHADOW_B: Float = 0.70f
    private const val HIGHLIGHT_R: Float = 1.00f
    private const val HIGHLIGHT_G: Float = 0.65f
    private const val HIGHLIGHT_B: Float = 0.35f
    private const val CINEMATIC_STRENGTH: Float = 0.30f
}
