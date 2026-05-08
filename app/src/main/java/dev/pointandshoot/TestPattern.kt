package dev.pointandshoot

import kotlin.math.max

/**
 * Pure-data procedural test pattern used by the GLES preview LUT screen
 * (BUILD_PLAN \u00a77 "Live preview / video"). The generator returns an
 * `IntArray` of ARGB packed pixels (Android's `Bitmap.Config.ARGB_8888`
 * native layout when `setPremultiplied(false)` and the wrapper handles the
 * channel order) so the host can wrap it in a [android.graphics.Bitmap]
 * without redoing the layout.
 *
 * The pattern is designed to make every bundled LUT visibly different:
 *
 *   * **Top band (66.7 % of height)**: 8 vertical bars - white, yellow,
 *     cyan, green, magenta, red, blue, black at 100 % saturation. A B&W
 *     LUT collapses these to a known luma sequence (white > yellow >
 *     cyan > green > magenta > red > blue > black for BT.709; yellow
 *     and cyan flip relative to BT.601 so the user can SEE which luma
 *     standard is active just by glancing at the bars). Cinematic LUTs
 *     are most legible against the colored bars.
 *   * **Middle band (16.6 %)**: 11-step grayscale wedge from black to
 *     white. Tone-curve LUTs visibly reshape this strip (e.g. PnsCinematic
 *     pulls deep shadows toward teal and highlights toward orange).
 *   * **Bottom band (16.6 %)**: smooth horizontal grayscale ramp 0->1.
 *     Quantization artifacts in the LUT path show up as visible bands
 *     here; a clean apply path looks perfectly continuous.
 *
 * The math is intentionally pure ([generateRgb] returns linear-light RGB
 * triples in `[0, 1]` so the same data backs unit tests as backs the
 * Bitmap-touching wrapper). Any regression in the bar luminances or wedge
 * step values will show up in [TestPatternTest] before it ever reaches a
 * GLES context.
 *
 * The pattern is defined as a fixed-size 1024 x 768 reference image; the
 * GLES renderer scales it via the `aTexCoord` quad so it always fills the
 * preview surface regardless of viewport aspect.
 */
object TestPattern {

    /** Reference width of the procedural pattern in pixels. */
    const val WIDTH: Int = 1024

    /** Reference height of the procedural pattern in pixels. */
    const val HEIGHT: Int = 768

    /**
     * The 8 vertical bars across the top band, in screen order (left to
     * right). All values are linear-light RGB in `[0, 1]`.
     */
    val COLOR_BARS: List<FloatArray> = listOf(
        floatArrayOf(1f, 1f, 1f), // white
        floatArrayOf(1f, 1f, 0f), // yellow
        floatArrayOf(0f, 1f, 1f), // cyan
        floatArrayOf(0f, 1f, 0f), // green
        floatArrayOf(1f, 0f, 1f), // magenta
        floatArrayOf(1f, 0f, 0f), // red
        floatArrayOf(0f, 0f, 1f), // blue
        floatArrayOf(0f, 0f, 0f), // black
    )

    /** Number of steps in the middle grayscale wedge (inclusive of 0 and 1). */
    const val WEDGE_STEPS: Int = 11

    private const val TOP_BAND_FRACTION: Float = 2f / 3f
    private const val WEDGE_BAND_FRACTION: Float = 1f / 6f
    // Bottom band fraction is implicit: 1 - top - wedge = 1/6.

    /**
     * Generate the test pattern as `[0, 1]` linear-light RGB triples.
     *
     * Returned shape is a `FloatArray` of length `width * height * 3`,
     * row-major, RGB-interleaved (matching the convention used by
     * [BitmapRgbPlane] elsewhere in the codebase).
     */
    fun generateRgb(
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): FloatArray {
        require(width > 0 && height > 0) { "width and height must be positive (got $width x $height)" }
        val out = FloatArray(width * height * 3)
        val topEnd = (height * TOP_BAND_FRACTION).toInt().coerceAtLeast(1)
        val wedgeEnd = (topEnd + height * WEDGE_BAND_FRACTION).toInt().coerceAtMost(height - 1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val triple = when {
                    y < topEnd -> colorBarFor(x, width)
                    y < wedgeEnd -> wedgeStepFor(x, width)
                    else -> smoothRampFor(x, width)
                }
                val idx = (y * width + x) * 3
                out[idx] = triple[0]
                out[idx + 1] = triple[1]
                out[idx + 2] = triple[2]
            }
        }
        return out
    }

    /**
     * Convenience overload that returns ARGB-packed pixels suitable for
     * `Bitmap.setPixels(...)` with `Bitmap.Config.ARGB_8888`. Linear-light
     * RGB is gamma-encoded with a basic sRGB approximation so what shows
     * up on a phone display matches what a desktop image viewer renders.
     */
    fun generateArgb(
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): IntArray {
        val rgb = generateRgb(width, height)
        val out = IntArray(width * height)
        var i = 0
        var p = 0
        while (p < out.size) {
            val r = linearToSrgb8(rgb[i])
            val g = linearToSrgb8(rgb[i + 1])
            val b = linearToSrgb8(rgb[i + 2])
            out[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            i += 3
            p += 1
        }
        return out
    }

    /** Width index -> color-bar triple. Public so tests can call it. */
    fun colorBarFor(x: Int, width: Int): FloatArray {
        require(width > 0) { "width must be positive" }
        val barIdx = (x * COLOR_BARS.size / width).coerceIn(0, COLOR_BARS.size - 1)
        return COLOR_BARS[barIdx]
    }

    /**
     * Width index -> grayscale wedge step. The wedge has [WEDGE_STEPS]
     * discrete levels uniformly spaced in `[0, 1]`. Public so the test can
     * verify the step boundaries.
     */
    fun wedgeStepFor(x: Int, width: Int): FloatArray {
        require(width > 0) { "width must be positive" }
        val stepIdx = (x * WEDGE_STEPS / width).coerceIn(0, WEDGE_STEPS - 1)
        val v = stepIdx.toFloat() / (WEDGE_STEPS - 1).toFloat()
        return floatArrayOf(v, v, v)
    }

    /**
     * Width index -> smooth grayscale ramp. Each pixel column gets a
     * unique value so the bottom band looks continuous on any reasonable
     * display. Public for symmetry with the other helpers + tests.
     */
    fun smoothRampFor(x: Int, width: Int): FloatArray {
        require(width > 0) { "width must be positive" }
        val v = x.toFloat() / max(1, width - 1).toFloat()
        return floatArrayOf(v, v, v)
    }

    /**
     * sRGB encoder shared with [generateArgb]. Pulled out so unit tests
     * can verify the endpoints (0.0 -> 0, 1.0 -> 255) without going
     * through Bitmap.
     */
    fun linearToSrgb8(v: Float): Int {
        val clamped = v.coerceIn(0f, 1f)
        val srgb = if (clamped <= 0.0031308f) {
            12.92f * clamped
        } else {
            1.055f * Math.pow(clamped.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
        }
        return (srgb * 255f + 0.5f).toInt().coerceIn(0, 255)
    }
}
