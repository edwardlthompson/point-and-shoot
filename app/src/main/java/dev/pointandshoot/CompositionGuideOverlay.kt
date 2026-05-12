package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

private val GuideColor = Color.White.copy(alpha = 0.72f)
private const val INV_PHI = 0.618033988749895f

@Composable
fun CompositionGuideOverlay(
    crop: CropGuideAspect,
    grid: GridOverlayMode,
    modifier: Modifier = Modifier,
) {
    if (crop == CropGuideAspect.OFF && grid == GridOverlayMode.OFF) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val strokeW = maxOf(1.5f, min(w, h) * 0.003f)
        val stroke = Stroke(width = strokeW)

        if (crop != CropGuideAspect.OFF && crop.widthOverHeight > 0f) {
            val r = aspectRect(w, h, crop.widthOverHeight)
            val dim = Color.Black.copy(alpha = 0.34f)
            if (r.top > 0f) {
                drawRect(
                    color = dim,
                    topLeft = Offset(0f, 0f),
                    size = Size(w, r.top),
                )
            }
            if (r.bottom < h) {
                drawRect(
                    color = dim,
                    topLeft = Offset(0f, r.bottom),
                    size = Size(w, h - r.bottom),
                )
            }
            if (r.left > 0f) {
                drawRect(
                    color = dim,
                    topLeft = Offset(0f, r.top),
                    size = Size(r.left, r.height),
                )
            }
            if (r.right < w) {
                drawRect(
                    color = dim,
                    topLeft = Offset(r.right, r.top),
                    size = Size(w - r.right, r.height),
                )
            }
            drawRect(
                color = GuideColor,
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
                style = stroke,
            )
        }

        when (grid) {
            GridOverlayMode.OFF -> {}
            GridOverlayMode.RULE_OF_THIRDS -> drawThirds(w, h, strokeW)
            GridOverlayMode.GOLDEN_RATIO_LINES -> drawGoldenLines(w, h, strokeW)
            GridOverlayMode.GOLDEN_SPIRAL -> drawGoldenSpiralApprox(w, h, stroke)
            GridOverlayMode.DIAGONALS -> drawDiagonals(w, h, strokeW)
            GridOverlayMode.SQUARE_3X3 -> drawSquare3x3(w, h, strokeW)
        }
    }
}

private fun aspectRect(w: Float, h: Float, widthOverHeight: Float): Rect {
    val boxW: Float
    val boxH: Float
    if (w / h > widthOverHeight) {
        boxH = h
        boxW = h * widthOverHeight
    } else {
        boxW = w
        boxH = w / widthOverHeight
    }
    val left = (w - boxW) / 2f
    val top = (h - boxH) / 2f
    return Rect(left, top, left + boxW, top + boxH)
}

private fun DrawScope.drawThirds(w: Float, h: Float, strokeW: Float) {
    val x1 = w / 3f
    val x2 = 2f * w / 3f
    val y1 = h / 3f
    val y2 = 2f * h / 3f
    drawLine(GuideColor, Offset(x1, 0f), Offset(x1, h), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(x2, 0f), Offset(x2, h), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(0f, y1), Offset(w, y1), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(0f, y2), Offset(w, y2), strokeWidth = strokeW)
}

private fun DrawScope.drawGoldenLines(w: Float, h: Float, strokeW: Float) {
    val vx1 = w * INV_PHI
    val vx2 = w * (1f - INV_PHI)
    val hy1 = h * INV_PHI
    val hy2 = h * (1f - INV_PHI)
    drawLine(GuideColor, Offset(vx1, 0f), Offset(vx1, h), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(vx2, 0f), Offset(vx2, h), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(0f, hy1), Offset(w, hy1), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(0f, hy2), Offset(w, hy2), strokeWidth = strokeW)
}

private fun DrawScope.drawDiagonals(w: Float, h: Float, strokeW: Float) {
    drawLine(GuideColor, Offset(0f, 0f), Offset(w, h), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(w, 0f), Offset(0f, h), strokeWidth = strokeW)
}

private fun DrawScope.drawSquare3x3(w: Float, h: Float, strokeW: Float) {
    for (i in 1..2) {
        val x = w * i / 3f
        val y = h * i / 3f
        drawLine(GuideColor, Offset(x, 0f), Offset(x, h), strokeWidth = strokeW)
        drawLine(GuideColor, Offset(0f, y), Offset(w, y), strokeWidth = strokeW)
    }
}

/** Curved guide approximating a φ spiral from corner toward the opposite quadrant. */
private fun DrawScope.drawGoldenSpiralApprox(w: Float, h: Float, stroke: Stroke) {
    drawGoldenLines(w, h, stroke.width)
    val path =
        Path().apply {
            moveTo(w, h)
            cubicTo(
                w * INV_PHI,
                h,
                w * INV_PHI * INV_PHI,
                h * INV_PHI,
                w * 0.12f,
                h * 0.18f,
            )
        }
    drawPath(path, color = GuideColor, style = stroke)
}
