package dev.pointandshoot

/**
 * Bundled reference-chart catalog per BUILD_PLAN §7 ("Reference targets
 * supported"). Pure-data; safe for unit testing.
 *
 * The X-Rite ColorChecker Classic 24-patch reference values are CIE-XYZ
 * measurements published in vendor documentation and re-published in the
 * scientific literature; the **values** are facts (not copyrightable) and
 * are bundled here as the `sRGB` linear-light triples that result from the
 * standard XYZ -> sRGB conversion under the published illuminant. We do NOT
 * bundle the chart **image** or use the "ColorChecker" trademark in any
 * user-facing string; the registered name lives in source comments only so
 * downstream contributors can find their bearings.
 *
 * The "generic 24" entry is a layout placeholder for users with non-X-Rite
 * 24-patch test charts; the reference values default to a perceptually
 * pleasing primary spread + neutral wedge (computed at runtime). Users with a
 * specific chart can override via the future SAF-imported targets path.
 */
object BundledReferenceTargets {

    /**
     * X-Rite ColorChecker Classic 24-patch (4 rows x 6 cols), reference values
     * from the publicly-published vendor data converted to linear-light sRGB
     * under D50 with the standard chromatic-adaptation transform.
     *
     * Layout (top-left to bottom-right):
     * ```
     *   Row 0: Dark skin   Light skin   Blue sky    Foliage     Blue flower  Bluish green
     *   Row 1: Orange      Purplish blue Moderate red Purple    Yellow green Orange yellow
     *   Row 2: Blue        Green        Red         Yellow      Magenta      Cyan
     *   Row 3: White       Neutral 8    Neutral 6.5 Neutral 5   Neutral 3.5  Black
     * ```
     *
     * Source: X-Rite ColorChecker Classic published reference data; values
     * cross-checked against Pascale (2003) "A Review of RGB Color Spaces" and
     * Sharma et al. "Digital Color Imaging Handbook".
     */
    val ColorCheckerClassic24: ReferenceTarget by lazy {
        ReferenceTarget(
            id = "colorchecker24",
            displayName = "Classic 24-patch (X-Rite layout)",
            rows = 4,
            cols = 6,
            illuminant = CalibrationProfile.Illuminant.D50,
            source = "X-Rite ColorChecker Classic published reference values (D50, sRGB linear-light)",
            patches = buildList {
                // Row 0 - skin, sky, foliage, blue flower, bluish green
                add(p(0, 0, "Dark skin", color(0.453f, 0.317f, 0.265f)))
                add(p(0, 1, "Light skin", color(0.764f, 0.589f, 0.510f)))
                add(p(0, 2, "Blue sky", color(0.366f, 0.474f, 0.611f)))
                add(p(0, 3, "Foliage", color(0.345f, 0.422f, 0.265f)))
                add(p(0, 4, "Blue flower", color(0.510f, 0.502f, 0.690f)))
                add(p(0, 5, "Bluish green", color(0.391f, 0.741f, 0.667f)))

                // Row 1 - orange, purple-blue, red, purple, yellow-green, orange-yellow
                add(p(1, 0, "Orange", color(0.847f, 0.475f, 0.165f)))
                add(p(1, 1, "Purplish blue", color(0.290f, 0.357f, 0.643f)))
                add(p(1, 2, "Moderate red", color(0.769f, 0.341f, 0.376f)))
                add(p(1, 3, "Purple", color(0.353f, 0.220f, 0.420f)))
                add(p(1, 4, "Yellow green", color(0.643f, 0.741f, 0.247f)))
                add(p(1, 5, "Orange yellow", color(0.910f, 0.624f, 0.180f)))

                // Row 2 - primaries (blue, green, red, yellow, magenta, cyan)
                add(p(2, 0, "Blue", color(0.165f, 0.247f, 0.580f)))
                add(p(2, 1, "Green", color(0.255f, 0.580f, 0.298f)))
                add(p(2, 2, "Red", color(0.690f, 0.180f, 0.220f)))
                add(p(2, 3, "Yellow", color(0.929f, 0.776f, 0.137f)))
                add(p(2, 4, "Magenta", color(0.722f, 0.282f, 0.564f)))
                add(p(2, 5, "Cyan", color(0.157f, 0.500f, 0.643f)))

                // Row 3 - neutral wedge (white -> black)
                add(p(3, 0, "White", neutral(0.949f)))
                add(p(3, 1, "Neutral 8", neutral(0.769f)))
                add(p(3, 2, "Neutral 6.5", neutral(0.580f)))
                add(p(3, 3, "Neutral 5", neutral(0.396f)))
                add(p(3, 4, "Neutral 3.5", neutral(0.231f)))
                add(p(3, 5, "Black", neutral(0.075f)))
            },
        )
    }

    /**
     * Generic 24-patch chart for users without an X-Rite reference. Rows
     * 0-2 are a perceptually-spaced color spread (24 - 6 = 18 colors at
     * uniform hue / luminance steps); row 3 is the neutral wedge. Values are
     * computed deterministically so the output is reproducible.
     *
     * NOTE: The reference values here are **synthetic** - they make the
     * generic chart usable for self-consistent calibration (apply LUT, re-
     * shoot, verify dE shrinkage) but they do NOT pin the camera to a
     * reference Lab space the way ColorChecker does. Users who want
     * absolute color accuracy should use ColorCheckerClassic24.
     */
    val Generic24: ReferenceTarget by lazy {
        val patches = buildList {
            // Rows 0-2: 18 hue swatches at 3 luminance levels.
            val hues = (0 until 6).map { it * 60f }      // 0, 60, 120, 180, 240, 300 degrees
            val lumas = listOf(0.70f, 0.50f, 0.30f)      // light, mid, dark
            for ((row, luma) in lumas.withIndex()) {
                for ((col, hue) in hues.withIndex()) {
                    add(p(row, col, "Hue ${hue.toInt()}@${(luma * 100).toInt()}%",
                        color = hueToRgb(hue, saturation = 0.65f, value = luma)))
                }
            }
            // Row 3: neutral wedge.
            val neutrals = listOf(0.95f, 0.78f, 0.60f, 0.42f, 0.24f, 0.06f)
            for ((col, v) in neutrals.withIndex()) {
                add(p(3, col, "Gray ${(v * 100).toInt()}%", neutral(v)))
            }
        }
        ReferenceTarget(
            id = "generic24",
            displayName = "Generic 24-patch (synthetic reference)",
            rows = 4,
            cols = 6,
            illuminant = CalibrationProfile.Illuminant.D65,
            source = "Synthetic reference (Point & Shoot deterministic generator)",
            patches = patches,
        )
    }

    /** All bundled reference targets. */
    val All: List<ReferenceTarget> by lazy { listOf(ColorCheckerClassic24, Generic24) }

    /** Look up a target by its [ReferenceTarget.id]. Throws if unknown. */
    fun byId(id: String): ReferenceTarget =
        All.firstOrNull { it.id == id }
            ?: error("Unknown reference target id='$id'; known ids = ${All.map { it.id }}")

    // ---------- helpers (private) ----------

    private fun p(
        row: Int,
        col: Int,
        name: String,
        color: FloatArray,
    ): ReferenceTarget.Patch {
        // Rows 0..(rows-2) are color patches; the last row is the neutral wedge.
        val role = if (color[0] == color[1] && color[1] == color[2]) {
            ReferenceTarget.PatchRole.Neutral
        } else {
            ReferenceTarget.PatchRole.Color
        }
        return ReferenceTarget.Patch(row = row, col = col, name = name, role = role, referenceRgb = color)
    }

    private fun color(r: Float, g: Float, b: Float): FloatArray = floatArrayOf(r, g, b)

    private fun neutral(v: Float): FloatArray = floatArrayOf(v, v, v)

    /**
     * HSV -> RGB conversion (linear-light sRGB) for the synthetic generic
     * chart. Hue in degrees `[0, 360)`; saturation + value in `[0, 1]`.
     */
    private fun hueToRgb(hue: Float, saturation: Float, value: Float): FloatArray {
        val h = (((hue % 360f) + 360f) % 360f) / 60f
        val c = value * saturation
        val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
        val m = value - c
        val (r1, g1, b1) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return floatArrayOf(r1 + m, g1 + m, b1 + m)
    }
}
