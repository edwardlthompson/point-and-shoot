package dev.pointandshoot

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Document-scanner-style quad finder for a rectangular calibration chart in a
 * downsampled preview frame. Pure Kotlin (no OpenCV): blur → Sobel edges →
 * extreme-corner clustering, then aspect-ratio / area gates.
 *
 * Corners are returned in **analyzed-image pixel space** (TL → TR → BR → BL),
 * same order as manual [ChartCorners] taps.
 */
object ChartQuadDetector {

    const val TAG = "PNS.ChartDetect"

    /** ColorChecker Classic is 6×4 patches → ~1.5:1 width:height. */
    private const val MIN_ASPECT = 1.12f
    private const val MAX_ASPECT = 1.92f
    /** Full chart in frame. */
    private const val MIN_AREA_FRAC = 0.04f
    /** Partial chart / skewed framing (Sprint **15.0**). */
    private const val MIN_AREA_FRAC_PARTIAL = 0.022f
    private const val MAX_AREA_FRAC = 0.94f
    private const val MIN_EDGE_PIXELS = 40
    private const val MIN_CONFIDENCE = 0.32f
    private const val DEFAULT_MAX_EDGE = 480

    data class Result(
        val corners: ChartCorners,
        /** 0..1 heuristic from area + aspect + edge density. */
        val confidence: Float,
        val analyzedWidth: Int,
        val analyzedHeight: Int,
    )

    fun detectFromBitmap(bitmap: Bitmap, maxEdge: Int = DEFAULT_MAX_EDGE): Result? {
        val (w, h) = BitmapRgbPlane.scaledDimensionsFor(bitmap.width, bitmap.height, maxEdge)
        val scaled =
            if (w == bitmap.width && h == bitmap.height) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, w, h, true)
            }
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        return detectFromArgb(pixels, w, h)
    }

    /**
     * @param pixels ARGB_8888 row-major, same as [Bitmap.getPixels].
     */
    fun detectFromArgb(pixels: IntArray, width: Int, height: Int): Result? {
        require(pixels.size == width * height)
        if (width < 32 || height < 32) return null
        val gray = argbToGray(pixels, width, height)
        boxBlur3x3InPlace(gray, width, height)
        val edges = sobelEdgeMask(gray, width, height) ?: return null
        val quad = quadFromEdgeMask(edges, width, height) ?: return null
        val conf = scoreQuad(quad, edges, width, height)
        if (conf < MIN_CONFIDENCE) {
            chartDetectLog("detect reject conf=${"%.2f".format(conf)} ${width}x$height")
            return null
        }
        chartDetectLog(
            "detect ok conf=${"%.2f".format(conf)} areaFrac=${"%.3f".format(
                abs(quadArea(quad.tl, quad.tr, quad.br, quad.bl)) / (width * height),
            )} ${width}x$height",
        )
        return Result(
            corners = quad,
            confidence = conf,
            analyzedWidth = width,
            analyzedHeight = height,
        )
    }

    /** Map detector corners to full-resolution [Bitmap] / view pixel coordinates. */
    fun scaleResultToSize(result: Result, targetWidth: Int, targetHeight: Int): ChartCorners {
        val sx = targetWidth.toFloat() / result.analyzedWidth.toFloat()
        val sy = targetHeight.toFloat() / result.analyzedHeight.toFloat()
        fun scale(p: Point2): Point2 = Point2(p.x * sx, p.y * sy)
        val c = result.corners
        return ChartCorners(
            tl = scale(c.tl),
            tr = scale(c.tr),
            br = scale(c.br),
            bl = scale(c.bl),
        )
    }

    private fun argbToGray(pixels: IntArray, w: Int, h: Int): IntArray {
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = ((r * 77 + g * 150 + b * 29) shr 8)
        }
        return gray
    }

    private fun boxBlur3x3InPlace(gray: IntArray, w: Int, h: Int) {
        val tmp = gray.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var sum = 0
                for (dy in -1..1) {
                    val row = (y + dy) * w
                    for (dx in -1..1) {
                        sum += tmp[row + x + dx]
                    }
                }
                gray[y * w + x] = sum / 9
            }
        }
    }

    private fun sobelEdgeMask(gray: IntArray, w: Int, h: Int): BooleanArray? {
        val mag = IntArray(w * h)
        var maxMag = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val gx =
                    -gray[idx - w - 1] - 2 * gray[idx - 1] - gray[idx + w - 1] +
                        gray[idx - w + 1] + 2 * gray[idx + 1] + gray[idx + w + 1]
                val gy =
                    -gray[idx - w - 1] - 2 * gray[idx - w] - gray[idx - w + 1] +
                        gray[idx + w - 1] + 2 * gray[idx + w] + gray[idx + w + 1]
                val m = abs(gx) + abs(gy)
                mag[idx] = m
                if (m > maxMag) maxMag = m
            }
        }
        if (maxMag < 20) return null
        val threshold = adaptiveEdgeThreshold(mag, maxMag)
        val mask = BooleanArray(w * h)
        var count = 0
        for (i in mag.indices) {
            if (mag[i] >= threshold) {
                mask[i] = true
                count++
            }
        }
        if (count < MIN_EDGE_PIXELS) return null
        return mask
    }

    /**
     * Pick four corners from edge pixels using extreme-point heuristics (common
     * in mobile document scanners when the chart fills much of the frame).
     */
    private fun quadFromEdgeMask(mask: BooleanArray, w: Int, h: Int): ChartCorners? {
        var tlX = Int.MAX_VALUE
        var tlY = Int.MAX_VALUE
        var tlScore = Int.MAX_VALUE
        var trScore = Int.MAX_VALUE
        var trX = 0
        var trY = Int.MAX_VALUE
        var brScore = Int.MIN_VALUE
        var brX = 0
        var brY = 0
        var blScore = Int.MIN_VALUE
        var blX = Int.MAX_VALUE
        var blY = 0
        var n = 0
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (!mask[row + x]) continue
                n++
                val sum = x + y
                if (sum < tlScore) {
                    tlScore = sum
                    tlX = x
                    tlY = y
                }
                val trMetric = y - x
                if (trMetric < trScore) {
                    trScore = trMetric
                    trX = x
                    trY = y
                }
                val brSum = x + y
                if (brSum > brScore) {
                    brScore = brSum
                    brX = x
                    brY = y
                }
                val blMetric = y - x
                if (blMetric > blScore) {
                    blScore = blMetric
                    blX = x
                    blY = y
                }
            }
        }
        if (n < MIN_EDGE_PIXELS) return null
        val tl = Point2(tlX.toFloat(), tlY.toFloat())
        val tr = Point2(trX.toFloat(), trY.toFloat())
        val br = Point2(brX.toFloat(), brY.toFloat())
        val bl = Point2(blX.toFloat(), blY.toFloat())
        if (!isConvexQuad(tl, tr, br, bl)) return null
        if (!isReasonableQuad(tl, tr, br, bl, w, h)) return null
        return ChartCorners(tl, tr, br, bl)
    }

    /** Glare-heavy frames: use high-percentile edge magnitude instead of a fixed fraction of max only. */
    private fun adaptiveEdgeThreshold(mag: IntArray, maxMag: Int): Int {
        val sample = IntArray(min(mag.size, 8192))
        var n = 0
        var i = 0
        while (i < mag.size && n < sample.size) {
            val m = mag[i]
            if (m > 0) {
                sample[n++] = m
            }
            i += max(1, mag.size / sample.size)
        }
        if (n < 32) return (maxMag * 0.40f).toInt().coerceAtLeast(14)
        val sorted = sample.copyOf(n)
        sorted.sort()
        val p70 = sorted[(n * 0.70f).toInt().coerceIn(0, n - 1)]
        val fromMax = (maxMag * 0.36f).toInt()
        return maxOf(fromMax, (p70 * 0.92f).toInt()).coerceIn(14, maxMag)
    }

    private fun isConvexQuad(tl: Point2, tr: Point2, br: Point2, bl: Point2): Boolean {
        fun cross(a: Point2, b: Point2, c: Point2): Float =
            (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        val c1 = cross(tl, tr, br)
        val c2 = cross(tr, br, bl)
        val c3 = cross(br, bl, tl)
        val c4 = cross(bl, tl, tr)
        return (c1 > 0f && c2 > 0f && c3 > 0f && c4 > 0f) ||
            (c1 < 0f && c2 < 0f && c3 < 0f && c4 < 0f)
    }

    private fun isReasonableQuad(
        tl: Point2,
        tr: Point2,
        br: Point2,
        bl: Point2,
        w: Int,
        h: Int,
    ): Boolean {
        val area = abs(quadArea(tl, tr, br, bl))
        val frame = w * h.toFloat()
        val widthTop = dist(tl, tr)
        val widthBot = dist(bl, br)
        val heightLeft = dist(tl, bl)
        val heightRight = dist(tr, br)
        val frac = area / frame
        if (frac < MIN_AREA_FRAC_PARTIAL || frac > MAX_AREA_FRAC) return false
        val minSideEarly = min(min(widthTop, widthBot), min(heightLeft, heightRight))
        if (frac < MIN_AREA_FRAC && minSideEarly < min(w, h) * 0.10f) return false
        val avgW = (widthTop + widthBot) * 0.5f
        val avgH = (heightLeft + heightRight) * 0.5f
        if (avgW < 8f || avgH < 8f) return false
        val aspect = avgW / avgH
        if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) return false
        // Corners must be sufficiently separated (not collapsed).
        val minSide = min(min(widthTop, widthBot), min(heightLeft, heightRight))
        if (minSide < min(w, h) * 0.12f) return false
        return true
    }

    private fun quadArea(tl: Point2, tr: Point2, br: Point2, bl: Point2): Float {
        // Shoelace for TL→TR→BR→BL.
        val xs = floatArrayOf(tl.x, tr.x, br.x, bl.x)
        val ys = floatArrayOf(tl.y, tr.y, br.y, bl.y)
        var sum = 0f
        for (i in 0..3) {
            val j = (i + 1) and 3
            sum += xs[i] * ys[j] - xs[j] * ys[i]
        }
        return sum * 0.5f
    }

    private fun dist(a: Point2, b: Point2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun scoreQuad(quad: ChartCorners, mask: BooleanArray, w: Int, h: Int): Float {
        val areaFrac = abs(quadArea(quad.tl, quad.tr, quad.br, quad.bl)) / (w * h)
        val widthTop = dist(quad.tl, quad.tr)
        val widthBot = dist(quad.bl, quad.br)
        val heightLeft = dist(quad.tl, quad.bl)
        val heightRight = dist(quad.tr, quad.br)
        val aspect = ((widthTop + widthBot) * 0.5f) / ((heightLeft + heightRight) * 0.5f)
        val aspectTarget = 1.5f
        val aspectScore = 1f - (abs(aspect - aspectTarget) / aspectTarget).coerceIn(0f, 1f)
        val areaScore =
            when {
                areaFrac < MIN_AREA_FRAC_PARTIAL -> 0f
                areaFrac < MIN_AREA_FRAC ->
                    ((areaFrac - MIN_AREA_FRAC_PARTIAL) /
                        (MIN_AREA_FRAC - MIN_AREA_FRAC_PARTIAL))
                        .coerceIn(0f, 0.65f)
                areaFrac > MAX_AREA_FRAC -> 0.2f
                else -> ((areaFrac - MIN_AREA_FRAC) / (0.45f - MIN_AREA_FRAC)).coerceIn(0f, 1f)
            }
        var edgeHits = 0
        var edgeTotal = 0
        sampleQuadPerimeter(quad, w, h) { x, y ->
            edgeTotal++
            if (mask[y * w + x]) edgeHits++
        }
        val edgeScore = if (edgeTotal > 0) edgeHits.toFloat() / edgeTotal else 0f
        return (0.35f * aspectScore + 0.25f * areaScore + 0.40f * edgeScore).coerceIn(0f, 1f)
    }

    private fun sampleQuadPerimeter(
        quad: ChartCorners,
        w: Int,
        h: Int,
        block: (x: Int, y: Int) -> Unit,
    ) {
        val steps = max(24, min(w, h) / 6)
        fun walk(a: Point2, b: Point2) {
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = (a.x + (b.x - a.x) * t).toInt().coerceIn(0, w - 1)
                val y = (a.y + (b.y - a.y) * t).toInt().coerceIn(0, h - 1)
                block(x, y)
            }
        }
        walk(quad.tl, quad.tr)
        walk(quad.tr, quad.br)
        walk(quad.br, quad.bl)
        walk(quad.bl, quad.tl)
    }

    private fun chartDetectLog(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: RuntimeException) {
            // JVM unit tests without android.util.Log shadowing
        }
    }
}
