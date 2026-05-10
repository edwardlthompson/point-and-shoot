package dev.pointandshoot

import android.graphics.Bitmap
import kotlin.math.pow

/**
 * Convert an Android [Bitmap] to an [RgbPlane] in linear-light, normalized
 * `[0, 1]` per BUILD_PLAN \u00a77 ("CalibrationSampler operates on linear-light
 * RGB"). The conversion uses the standard sRGB EOTF (IEC 61966-2-1) so a
 * patch sampled from a sRGB-encoded chart photo lands in the same space the
 * reference values in [BundledReferenceTargets] live in.
 *
 * The Bitmap is downsampled to [maxEdge] on its longer edge before
 * conversion - the calibration math is happy with a few hundred pixels per
 * chart and full-resolution decoding wastes cycles.
 *
 * Pure file-of-bytes-in, file-of-floats-out helper; the int-to-linear math
 * is exposed via [srgbByteToLinear] for unit testing without an Android
 * Bitmap dependency.
 */
object BitmapRgbPlane {

    /**
     * Default longer-edge cap for the converted plane. The calibration
     * sampler uses ~32x32-pixel patches on a 24-patch chart, so 1024 px
     * gives ~150 px per patch even with worst-case framing - plenty for
     * mean + variance.
     */
    const val DEFAULT_MAX_EDGE: Int = 1024

    /**
     * Convert [bitmap] to an [RgbPlane] in linear-light sRGB. The output
     * width and height match the bitmap's downsampled dimensions; the
     * caller maps tapped corner coordinates against those dimensions.
     */
    fun fromBitmap(bitmap: Bitmap, maxEdge: Int = DEFAULT_MAX_EDGE): RgbPlane {
        val (scaledWidth, scaledHeight) = scaledDimensionsFor(bitmap.width, bitmap.height, maxEdge)
        val source = if (scaledWidth == bitmap.width && scaledHeight == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        }
        val pixels = IntArray(scaledWidth * scaledHeight)
        source.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
        val rgb = FloatArray(pixels.size * 3)
        for (i in pixels.indices) {
            val argb = pixels[i]
            val rByte = (argb shr 16) and 0xFF
            val gByte = (argb shr 8) and 0xFF
            val bByte = argb and 0xFF
            val idx = i * 3
            rgb[idx] = srgbByteToLinear(rByte)
            rgb[idx + 1] = srgbByteToLinear(gByte)
            rgb[idx + 2] = srgbByteToLinear(bByte)
        }
        if (source !== bitmap) source.recycle()
        return RgbPlane(rgb = rgb, width = scaledWidth, height = scaledHeight)
    }

    /**
     * Standard sRGB EOTF (IEC 61966-2-1) on a single 8-bit channel value.
     * Returns a linear-light value in `[0, 1]`. Public for unit testing.
     */
    fun srgbByteToLinear(value: Int): Float {
        val v = (value.coerceIn(0, 255)) / 255.0
        return if (v <= 0.04045) {
            (v / 12.92).toFloat()
        } else {
            ((v + 0.055) / 1.055).pow(2.4).toFloat()
        }
    }

    /**
     * Inverse of [srgbByteToLinear]: maps linear-light `[0, 1]` to an sRGB 8-bit code.
     */
    fun linearToSrgbByte(linear: Float): Int {
        val x = linear.coerceIn(0f, 1f)
        val v =
            if (x <= 0.0031308f) {
                12.92f * x
            } else {
                1.055f * x.pow(1f / 2.4f) - 0.055f
            }
        return (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    /**
     * Compute the downsampled dimensions for [width] x [height], constraining
     * the longer edge to [maxEdge] while preserving aspect. Public for
     * testing.
     */
    fun scaledDimensionsFor(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "width and height must be positive (was ${width}x$height)" }
        require(maxEdge > 0) { "maxEdge must be positive (was $maxEdge)" }
        val longEdge = maxOf(width, height)
        if (longEdge <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longEdge.toDouble()
        val w = maxOf(1, (width * scale).toInt())
        val h = maxOf(1, (height * scale).toInt())
        return w to h
    }
}
