package dev.pointandshoot

import kotlin.math.abs

/**
 * Pure-data 3D RGB lookup table. Backs both the bundled LUT library and the
 * calibration mode's `.cube` export per BUILD_PLAN §7 ("Phase 4").
 *
 * Storage layout: [samples] is row-major over (b, g, r) with three floats per
 * cell, i.e., the cell at index (r, g, b) starts at
 * `((b * size + g) * size + r) * 3`. This matches the canonical Adobe Cube
 * spec ordering (R varies fastest, then G, then B).
 *
 * Sample values are stored exactly as supplied; clamping happens in
 * [LutPipeline.applyTrilinear] so a calibration LUT can briefly round-trip
 * out-of-gamut values without losing precision.
 */
class Lut3D(
    val size: Int,
    val samples: FloatArray,
) {
    init {
        require(size in SUPPORTED_SIZES) {
            "Lut3D size must be one of $SUPPORTED_SIZES (was $size)"
        }
        val expected = size * size * size * 3
        require(samples.size == expected) {
            "Lut3D samples must have $expected entries for size=$size (was ${samples.size})"
        }
    }

    /**
     * @return true when every cell in this LUT equals the identity sample at
     *   that grid position to within [tolerance]. Handy for short-circuiting
     *   the apply path on the GPU when the user has selected "None".
     */
    fun isIdentity(tolerance: Float = DEFAULT_IDENTITY_TOLERANCE): Boolean {
        val denom = (size - 1).toFloat()
        for (b in 0 until size) {
            val bf = b / denom
            for (g in 0 until size) {
                val gf = g / denom
                for (r in 0 until size) {
                    val rf = r / denom
                    val idx = ((b * size + g) * size + r) * 3
                    if (abs(samples[idx] - rf) > tolerance) return false
                    if (abs(samples[idx + 1] - gf) > tolerance) return false
                    if (abs(samples[idx + 2] - bf) > tolerance) return false
                }
            }
        }
        return true
    }

    companion object {
        /** Adobe Cube spec allows arbitrary sizes, but we pin three for cache predictability. */
        val SUPPORTED_SIZES: List<Int> = listOf(17, 33, 65)

        /** Tight enough to detect rounding artifacts but loose enough for `Float` parse drift. */
        const val DEFAULT_IDENTITY_TOLERANCE: Float = 1e-5f

        /**
         * Build an identity LUT of the given grid size: each cell maps to its
         * own normalized RGB position so apply() is a no-op (modulo precision).
         */
        fun identity(size: Int): Lut3D {
            require(size in SUPPORTED_SIZES) { "size must be in $SUPPORTED_SIZES (was $size)" }
            val s = FloatArray(size * size * size * 3)
            val denom = (size - 1).toFloat()
            for (b in 0 until size) {
                val bf = b / denom
                for (g in 0 until size) {
                    val gf = g / denom
                    for (r in 0 until size) {
                        val rf = r / denom
                        val idx = ((b * size + g) * size + r) * 3
                        s[idx] = rf
                        s[idx + 1] = gf
                        s[idx + 2] = bf
                    }
                }
            }
            return Lut3D(size, s)
        }
    }
}
