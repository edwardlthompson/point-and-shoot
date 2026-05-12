package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/** Default: ~0.95 × 255 — near clipping on 8-bit preview Y (spec alignment). */
const val HIGHLIGHT_CLIP_ZEBRA_THRESHOLD_UNSIGNED: Int = 242

/**
 * Diagonal red hatch over cells where [HighlightClipZebraFrame.nearClip] is true.
 * Coordinates are mapped from YUV analysis space → preview buffer → view using [TexturePreviewFit].
 */
@Composable
fun HighlightClipZebraOverlay(
    frame: HighlightClipZebraFrame?,
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
        val stripeColor = Color.Red.copy(alpha = 0.42f)
        val stroke = max(1.5f, min(vw, vh) / 480f)

        for (row in 0 until frame.rows) {
            for (col in 0 until frame.cols) {
                if (!frame.nearClip[row * frame.cols + col]) continue
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
                val l = min(min(vl0, vl1), min(vl2, vl3))
                val t = min(min(vt0, vt1), min(vt2, vt3))
                val r = max(max(vl0, vl1), max(vl2, vl3))
                val b = max(max(vt0, vt1), max(vt2, vt3))
                if (r - l < 2f || b - t < 2f) continue

                val wCell = r - l
                val hCell = b - t
                val step = max(6f, min(wCell, hCell) / 7f)
                var x = l - hCell
                while (x < r) {
                    drawLine(
                        color = stripeColor,
                        start = Offset(x, b),
                        end = Offset(x + hCell, t),
                        strokeWidth = stroke,
                    )
                    x += step
                }
            }
        }
    }
}

private fun yuvCornerToView(
    yuvX: Float,
    yuvY: Float,
    yuvWidth: Int,
    yuvHeight: Int,
    bufferWidthPx: Int,
    bufferHeightPx: Int,
    viewWidthPx: Int,
    viewHeightPx: Int,
    coverCrop: Boolean,
    mirrorHorizontally: Boolean,
): Pair<Float, Float> {
    val (bx, by) =
        TexturePreviewFit.mapYuvPixelToBufferPixel(
            yuvX,
            yuvY,
            yuvWidth,
            yuvHeight,
            bufferWidthPx,
            bufferHeightPx,
            coverCrop,
        )
    var (vx, vy) =
        TexturePreviewFit.mapBufferToView(
            bx,
            by,
            viewWidthPx,
            viewHeightPx,
            bufferWidthPx,
            bufferHeightPx,
            coverCrop,
        )
    if (mirrorHorizontally) {
        vx = viewWidthPx.toFloat() - vx
    }
    return vx to vy
}
