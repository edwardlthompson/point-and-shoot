package dev.pointandshoot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import android.util.Size
import kotlin.math.roundToInt

/**
 * Experimental stitched "Max Photo" helper:
 * capture progressive tiles (via crop regions) and stitch into one larger RGB output.
 */
object MaxPhotoTileStitch {
    private const val TAG = "PNS.MaxPhotoStitch"
    private const val DEFAULT_OVERLAP_FRACTION = 0.12f

    data class TileLayout(
        val cols: Int,
        val rows: Int,
        val cropRects: List<Rect>,
        val overlapFraction: Float = DEFAULT_OVERLAP_FRACTION,
    )

    data class StitchedRgb(
        val rgb888: ByteArray,
        val width: Int,
        val height: Int,
    )

    fun chooseAdaptivePlan(
        activeArray: Rect?,
        baselineOutputSize: Size?,
        overlapFraction: Float = DEFAULT_OVERLAP_FRACTION,
    ): TileLayout? {
        if (activeArray == null || activeArray.width() <= 0 || activeArray.height() <= 0) return null
        val colsRows = chooseAdaptiveGrid(activeArray, baselineOutputSize)
        return planGrid(
            activeArray = activeArray,
            cols = colsRows.first,
            rows = colsRows.second,
            overlapFraction = overlapFraction,
        )
    }

    private fun chooseAdaptiveGrid(
        activeArray: Rect,
        baselineOutputSize: Size?,
    ): Pair<Int, Int> {
        val out = baselineOutputSize
        if (out == null || out.width <= 0 || out.height <= 0) {
            return 2 to 2
        }
        val activePx = activeArray.width().toDouble() * activeArray.height().toDouble()
        val outPx = out.width.toDouble() * out.height.toDouble()
        if (activePx <= 0.0 || outPx <= 0.0) return 2 to 2
        val ratio = activePx / outPx
        return when {
            ratio < 1.35 -> 1 to 1
            ratio < 2.4 -> 2 to 1
            else -> 2 to 2
        }
    }

    private fun planGrid(
        activeArray: Rect,
        cols: Int,
        rows: Int,
        overlapFraction: Float,
    ): TileLayout {
        val clampedOverlap = overlapFraction.coerceIn(0f, 0.40f)
        val strideFactor = (1f - clampedOverlap).coerceIn(0.55f, 1f)
        val cropW =
            (activeArray.width() / (1f + (cols - 1) * strideFactor))
                .roundToInt()
                .coerceAtLeast(1)
        val cropH =
            (activeArray.height() / (1f + (rows - 1) * strideFactor))
                .roundToInt()
                .coerceAtLeast(1)
        val stepW = (cropW * strideFactor).roundToInt().coerceAtLeast(1)
        val stepH = (cropH * strideFactor).roundToInt().coerceAtLeast(1)
        val rects = ArrayList<Rect>(cols * rows)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                var left = activeArray.left + col * stepW
                var top = activeArray.top + row * stepH
                if (left + cropW > activeArray.right) {
                    left = activeArray.right - cropW
                }
                if (top + cropH > activeArray.bottom) {
                    top = activeArray.bottom - cropH
                }
                left = left.coerceIn(activeArray.left, activeArray.right - 1)
                top = top.coerceIn(activeArray.top, activeArray.bottom - 1)
                val right = (left + cropW).coerceAtMost(activeArray.right)
                val bottom = (top + cropH).coerceAtMost(activeArray.bottom)
                rects += Rect(left, top, right, bottom)
            }
        }
        return TileLayout(cols = cols, rows = rows, cropRects = rects, overlapFraction = clampedOverlap)
    }

    fun stitchJpegTiles(
        jpegTiles: List<ByteArray>,
        layout: TileLayout,
    ): StitchedRgb? {
        val cols = layout.cols
        val rows = layout.rows
        if (jpegTiles.size != cols * rows || cols <= 0 || rows <= 0) return null
        val firstBmp = BitmapFactory.decodeByteArray(jpegTiles[0], 0, jpegTiles[0].size) ?: return null
        val tileW = firstBmp.width.coerceAtLeast(1)
        val tileH = firstBmp.height.coerceAtLeast(1)
        firstBmp.recycle()

        val overlapPxX = (tileW * layout.overlapFraction).roundToInt().coerceAtLeast(0)
        val overlapPxY = (tileH * layout.overlapFraction).roundToInt().coerceAtLeast(0)
        val stepX = (tileW - overlapPxX).coerceAtLeast(1)
        val stepY = (tileH - overlapPxY).coerceAtLeast(1)
        val outW = tileW + (cols - 1) * stepX
        val outH = tileH + (rows - 1) * stepY
        val out = ByteArray(outW * outH * 3)
        val coverage = ByteArray(outW * outH)

        jpegTiles.forEachIndexed { index, bytes ->
            val row = index / cols
            val col = index % cols
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp == null) {
                Log.w(TAG, "tile decode failed index=$index")
                return null
            }
            val useBmp =
                if (bmp.width == tileW && bmp.height == tileH) {
                    bmp
                } else {
                    Bitmap.createScaledBitmap(bmp, tileW, tileH, true).also {
                        bmp.recycle()
                    }
                }
            val tileRgb = bitmapToRgb888(useBmp)
            useBmp.recycle()
            blitTile(
                src = tileRgb,
                srcW = tileW,
                srcH = tileH,
                dst = out,
                coverage = coverage,
                dstW = outW,
                dstH = outH,
                dstX = col * stepX,
                dstY = row * stepY,
                blendOverlapPxX = overlapPxX,
                blendOverlapPxY = overlapPxY,
            )
        }
        return StitchedRgb(rgb888 = out, width = outW, height = outH)
    }

    private fun bitmapToRgb888(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val rgb = ByteArray(w * h * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            val o = i * 3
            rgb[o] = ((p shr 16) and 0xFF).toByte()
            rgb[o + 1] = ((p shr 8) and 0xFF).toByte()
            rgb[o + 2] = (p and 0xFF).toByte()
        }
        return rgb
    }

    private fun blitTile(
        src: ByteArray,
        srcW: Int,
        srcH: Int,
        dst: ByteArray,
        coverage: ByteArray,
        dstW: Int,
        dstH: Int,
        dstX: Int,
        dstY: Int,
        blendOverlapPxX: Int,
        blendOverlapPxY: Int,
    ) {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return
        for (y in 0 until srcH) {
            val ty = dstY + y
            if (ty !in 0 until dstH) continue
            for (x in 0 until srcW) {
                val tx = dstX + x
                if (tx !in 0 until dstW) continue
                val srcI = (y * srcW + x) * 3
                val pixIndex = ty * dstW + tx
                val dstI = pixIndex * 3
                val seen = coverage[pixIndex].toInt() and 0xFF
                if (seen == 0) {
                    dst[dstI] = src[srcI]
                    dst[dstI + 1] = src[srcI + 1]
                    dst[dstI + 2] = src[srcI + 2]
                    coverage[pixIndex] = 1
                    continue
                }
                val alpha = seamBlendAlpha(x, y, srcW, srcH, blendOverlapPxX, blendOverlapPxY)
                val inv = 1f - alpha
                val sr = src[srcI].toInt() and 0xFF
                val sg = src[srcI + 1].toInt() and 0xFF
                val sb = src[srcI + 2].toInt() and 0xFF
                val dr = dst[dstI].toInt() and 0xFF
                val dg = dst[dstI + 1].toInt() and 0xFF
                val db = dst[dstI + 2].toInt() and 0xFF
                dst[dstI] = (dr * inv + sr * alpha).roundToInt().coerceIn(0, 255).toByte()
                dst[dstI + 1] = (dg * inv + sg * alpha).roundToInt().coerceIn(0, 255).toByte()
                dst[dstI + 2] = (db * inv + sb * alpha).roundToInt().coerceIn(0, 255).toByte()
                coverage[pixIndex] = (seen + 1).coerceAtMost(255).toByte()
            }
        }
    }

    private fun seamBlendAlpha(
        x: Int,
        y: Int,
        tileW: Int,
        tileH: Int,
        overlapX: Int,
        overlapY: Int,
    ): Float {
        if (overlapX <= 0 && overlapY <= 0) return 0.5f
        var alpha = 1f
        if (overlapX > 0) {
            val left = if (x < overlapX) x.toFloat() / overlapX.toFloat() else 1f
            val right =
                if (x > tileW - overlapX - 1) {
                    (tileW - 1 - x).toFloat() / overlapX.toFloat()
                } else {
                    1f
                }
            alpha *= minOf(left, right).coerceIn(0.05f, 1f)
        }
        if (overlapY > 0) {
            val top = if (y < overlapY) y.toFloat() / overlapY.toFloat() else 1f
            val bottom =
                if (y > tileH - overlapY - 1) {
                    (tileH - 1 - y).toFloat() / overlapY.toFloat()
                } else {
                    1f
                }
            alpha *= minOf(top, bottom).coerceIn(0.05f, 1f)
        }
        return alpha.coerceIn(0.10f, 0.90f)
    }
}
