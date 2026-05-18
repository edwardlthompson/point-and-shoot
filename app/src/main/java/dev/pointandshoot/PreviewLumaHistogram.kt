package dev.pointandshoot

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-data luma histogram reducer for the highlight-weighted metering
 * pipeline (BUILD_PLAN §4 "Highlight-weighted metering" + the existing
 * [HighlightMeter.suggestEvCorrection] consumer). The capture engine will
 * feed [reduceY8] / [reduceYuv420Y] with the Y-plane of a downscaled
 * preview frame; this module returns a 256-bin histogram that
 * [HighlightMeter] turns into an EV-correction signal.
 *
 * The reducer is intentionally an Android-free pure function so it can be
 * unit-tested on the JVM without a Camera2 stub. Engine wiring (where the
 * Y plane comes from, what stride/rowstride the preview format actually
 * has, and how often the reduction runs) lives in the Phase 1 capture
 * engine; this file is the math boundary.
 */
object PreviewLumaHistogram {

    /** Number of bins in every histogram this module produces. */
    const val BIN_COUNT: Int = 256

    /**
     * Reduce a tightly-packed `Y8` plane (1 byte per pixel, no padding)
     * into a 256-bin histogram. The byte values are interpreted as
     * **unsigned** in the `[0, 255]` range so a Kotlin `Byte` (signed) of
     * `-1` correctly bumps bin 255.
     *
     * @throws IllegalArgumentException if [width] / [height] are non-
     *   positive or the buffer is too short.
     */
    fun reduceY8(plane: ByteArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0) {
            "preview dimensions must be positive (was ${width}x$height)"
        }
        val expectedBytes = width.toLong() * height.toLong()
        require(plane.size.toLong() >= expectedBytes) {
            "Y plane too short: have ${plane.size}, need >= $expectedBytes for ${width}x$height"
        }
        val hist = IntArray(BIN_COUNT)
        val total = (width * height).coerceAtLeast(0)
        for (i in 0 until total) {
            hist[plane[i].toInt() and 0xFF] += 1
        }
        return hist
    }

    /**
     * Reduce a `YUV_420_888`-style Y plane that may have stride padding.
     * The Camera2 preview surface delivers Y planes whose [rowStride] can
     * exceed [width] (e.g. aligned to 16 bytes for hardware DMA), so this
     * variant skips the trailing padding bytes per row.
     *
     * @param plane the raw Y plane bytes (length `>= rowStride * (height - 1) + width`)
     * @param width the visible image width in pixels (`<= rowStride`)
     * @param height the visible image height in pixels
     * @param rowStride the actual row stride in bytes (`>= width`)
     */
    fun reduceYuv420Y(
        plane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
    ): IntArray {
        require(width > 0 && height > 0) {
            "preview dimensions must be positive (was ${width}x$height)"
        }
        require(rowStride >= width) {
            "rowStride ($rowStride) must be >= width ($width)"
        }
        val minBytes = rowStride.toLong() * (height - 1).toLong() + width.toLong()
        require(plane.size.toLong() >= minBytes) {
            "Y plane too short: have ${plane.size}, need >= $minBytes for ${width}x$height (stride=$rowStride)"
        }
        val hist = IntArray(BIN_COUNT)
        for (row in 0 until height) {
            val base = row * rowStride
            for (col in 0 until width) {
                hist[plane[base + col].toInt() and 0xFF] += 1
            }
        }
        return hist
    }

    /**
     * Optional center-weighted variant: pixels in the center [centerFrac]
     * of the frame are counted [centerWeight] times each so the highlight
     * metering tracks the subject rather than the corners. [centerFrac]
     * is the side length of the centered square as a fraction of
     * `min(width, height)`; [centerWeight] is the multiplier (>= 1).
     *
     * Pure integer arithmetic — every pixel still contributes at least
     * once so the histogram can never go zero on a non-empty frame.
     */
    fun reduceYuv420YCenterWeighted(
        plane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        centerFrac: Float = DEFAULT_CENTER_FRAC,
        centerWeight: Int = DEFAULT_CENTER_WEIGHT,
    ): IntArray {
        require(centerFrac in 0f..1f) { "centerFrac must be in [0, 1] (was $centerFrac)" }
        require(centerWeight >= 1) { "centerWeight must be >= 1 (was $centerWeight)" }

        val baseHist = reduceYuv420Y(plane, width, height, rowStride)
        if (centerWeight == 1 || centerFrac <= 0f) return baseHist

        val side = (min(width, height) * centerFrac).toInt().coerceAtLeast(1)
        val left = max(0, (width - side) / 2)
        val top = max(0, (height - side) / 2)
        val right = min(width, left + side)
        val bottom = min(height, top + side)
        val extraWeight = centerWeight - 1

        for (row in top until bottom) {
            val base = row * rowStride
            for (col in left until right) {
                baseHist[plane[base + col].toInt() and 0xFF] += extraWeight
            }
        }
        return baseHist
    }

    /**
     * Coarse near-clip mask on Y (same plane contract as [reduceYuv420Y]): each cell is true if any
     * pixel in that cell has luma ≥ [thresholdUnsigned] (~0.95×255 for highlight zebra).
     */
    fun buildClipZebraGridYuv420Y(
        plane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        cellSizePx: Int = 12,
        thresholdUnsigned: Int = 242,
    ): HighlightClipZebraFrame {
        require(width > 0 && height > 0) {
            "preview dimensions must be positive (was ${width}x$height)"
        }
        require(rowStride >= width) {
            "rowStride ($rowStride) must be >= width ($width)"
        }
        require(cellSizePx >= 4) { "cellSizePx must be >= 4 (was $cellSizePx)" }
        require(thresholdUnsigned in 0..255) { "thresholdUnsigned out of range: $thresholdUnsigned" }
        val minBytes = rowStride.toLong() * (height - 1).toLong() + width.toLong()
        require(plane.size.toLong() >= minBytes) {
            "Y plane too short: have ${plane.size}, need >= $minBytes for ${width}x$height (stride=$rowStride)"
        }

        val cols = ceil(width / cellSizePx.toDouble()).toInt().coerceAtLeast(1)
        val rows = ceil(height / cellSizePx.toDouble()).toInt().coerceAtLeast(1)
        val cells = BooleanArray(cols * rows)
        for (row in 0 until rows) {
            val y0 = row * cellSizePx
            val y1 = min(y0 + cellSizePx, height)
            for (col in 0 until cols) {
                val x0 = col * cellSizePx
                val x1 = min(x0 + cellSizePx, width)
                var hit = false
                cy@ for (cy in y0 until y1) {
                    val base = cy * rowStride
                    for (cx in x0 until x1) {
                        val v = plane[base + cx].toInt() and 0xFF
                        if (v >= thresholdUnsigned) {
                            hit = true
                            break@cy
                        }
                    }
                }
                cells[row * cols + col] = hit
            }
        }
        return HighlightClipZebraFrame(
            sourceWidth = width,
            sourceHeight = height,
            cellSizePx = cellSizePx,
            cols = cols,
            rows = rows,
            nearClip = cells,
        )
    }

    /**
     * Sum every bin (sanity / unit-test helper). Equivalent to
     * `width * height` for [reduceY8] / [reduceYuv420Y]; for the
     * center-weighted variant the sum exceeds pixel count by the extra
     * contributions from the center region.
     */
    fun pixelCount(hist: IntArray): Long {
        var total = 0L
        for (b in hist) total += b
        return total
    }

    /** Default center-weight square side relative to `min(w, h)`. */
    const val DEFAULT_CENTER_FRAC: Float = 0.5f

    /** Default center-region weight multiplier. */
    const val DEFAULT_CENTER_WEIGHT: Int = 3

    /**
     * Sprint 13.9: RGB histogram result — three 256-bin arrays for red, green, blue channels.
     */
    data class RgbHistogramBins(
        val r: IntArray,
        val g: IntArray,
        val b: IntArray,
    )

    /**
     * Sprint 13.9: Reduce a `YUV_420_888` frame to per-channel 256-bin R/G/B histograms.
     *
     * Conversion uses BT.601 full-range integer arithmetic (same spec as Android's YUV_420_888
     * preview surfaces). The chroma planes may be interleaved (NV12/NV21) or planar; this
     * function uses the supplied strides and pixel-strides to handle both.
     *
     * Downsamples spatially: every [step]th pixel in both axes is sampled — keeps the
     * computation cheap on the metering executor without visibly affecting histogram shape.
     *
     * @param yPlane   raw Y plane bytes
     * @param uPlane   raw U (Cb) plane bytes
     * @param vPlane   raw V (Cr) plane bytes
     * @param width    visible frame width
     * @param height   visible frame height
     * @param yRowStride   Y plane row stride
     * @param uvRowStride  U/V plane row stride (same for both in YUV_420_888)
     * @param uvPixelStride U/V pixel stride (1 = planar, 2 = interleaved NV12/NV21)
     * @param step     spatial downsampling step (default 2 = every other pixel)
     */
    fun reduceRgb(
        yPlane: ByteArray,
        uPlane: ByteArray,
        vPlane: ByteArray,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        step: Int = 2,
    ): RgbHistogramBins {
        require(width > 0 && height > 0) {
            "preview dimensions must be positive (was ${width}x$height)"
        }
        val safeStep = step.coerceAtLeast(1)
        val rHist = IntArray(BIN_COUNT)
        val gHist = IntArray(BIN_COUNT)
        val bHist = IntArray(BIN_COUNT)

        var row = 0
        while (row < height) {
            val yBase = row * yRowStride
            val uvRow = (row / 2) * uvRowStride
            var col = 0
            while (col < width) {
                val y = yPlane[yBase + col].toInt() and 0xFF
                val uvCol = (col / 2) * uvPixelStride
                val u = (uPlane[uvCol + uvRow].toInt() and 0xFF) - 128
                val v = (vPlane[uvCol + uvRow].toInt() and 0xFF) - 128

                // BT.601 full-range integer approximation (×256 fixed-point)
                val r = (y * 256 + v * 359) shr 8
                val g = (y * 256 - u * 88 - v * 183) shr 8
                val b = (y * 256 + u * 454) shr 8

                rHist[r.coerceIn(0, 255)]++
                gHist[g.coerceIn(0, 255)]++
                bHist[b.coerceIn(0, 255)]++

                col += safeStep
            }
            row += safeStep
        }
        return RgbHistogramBins(rHist, gHist, bHist)
    }
}
