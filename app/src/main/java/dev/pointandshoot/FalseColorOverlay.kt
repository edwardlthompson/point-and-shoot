package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.min

/**
 * Semi-transparent false-color tint per coarse YUV cell (Sprint **15.21**).
 */
@Composable
fun FalseColorOverlay(
    frame: FalseColorFrame?,
    bufferWidthPx: Int,
    bufferHeightPx: Int,
    coverCrop: Boolean,
    mirrorHorizontally: Boolean,
    modifier: Modifier = Modifier,
) {
    if (frame == null || frame.cols <= 0 || frame.rows <= 0) return
    val yuvW = frame.sourceWidth
    val yuvH = frame.sourceHeight
    if (yuvW <= 0 || yuvH <= 0 || bufferWidthPx <= 0 || bufferHeightPx <= 0) return

    Canvas(modifier = modifier) {
        val vw = size.width
        val vh = size.height
        if (vw <= 0f || vh <= 0f) return@Canvas

        for (row in 0 until frame.rows) {
            for (col in 0 until frame.cols) {
                val argb = frame.cellArgb[row * frame.cols + col]
                if (argb == 0) continue
                val x0 = col * frame.cellSizePx
                val y0 = row * frame.cellSizePx
                val x1 = min(x0 + frame.cellSizePx, yuvW)
                val y1 = min(y0 + frame.cellSizePx, yuvH)
                val (vl0, vt0) =
                    yuvCornerToView(
                        x0.toFloat(),
                        y0.toFloat(),
                        yuvW,
                        yuvH,
                        bufferWidthPx,
                        bufferHeightPx,
                        vw.toInt(),
                        vh.toInt(),
                        coverCrop,
                        mirrorHorizontally,
                    )
                val (vl1, vt1) =
                    yuvCornerToView(
                        x1.toFloat(),
                        y0.toFloat(),
                        yuvW,
                        yuvH,
                        bufferWidthPx,
                        bufferHeightPx,
                        vw.toInt(),
                        vh.toInt(),
                        coverCrop,
                        mirrorHorizontally,
                    )
                val (vl2, vt2) =
                    yuvCornerToView(
                        x1.toFloat(),
                        y1.toFloat(),
                        yuvW,
                        yuvH,
                        bufferWidthPx,
                        bufferHeightPx,
                        vw.toInt(),
                        vh.toInt(),
                        coverCrop,
                        mirrorHorizontally,
                    )
                val (vl3, vt3) =
                    yuvCornerToView(
                        x0.toFloat(),
                        y1.toFloat(),
                        yuvW,
                        yuvH,
                        bufferWidthPx,
                        bufferHeightPx,
                        vw.toInt(),
                        vh.toInt(),
                        coverCrop,
                        mirrorHorizontally,
                    )
                val left = minOf(vl0, vl1, vl2, vl3)
                val top = minOf(vt0, vt1, vt2, vt3)
                val right = maxOf(vl0, vl1, vl2, vl3)
                val bottom = maxOf(vt0, vt1, vt2, vt3)
                val w = (right - left).coerceAtLeast(1f)
                val h = (bottom - top).coerceAtLeast(1f)
                drawRect(
                    color = Color(argb),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                )
            }
        }
    }
}
