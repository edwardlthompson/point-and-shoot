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

        val cropRect =
            if (crop != CropGuideAspect.OFF && crop.widthOverHeight > 0f) {
                aspectRect(w, h, crop.widthOverHeight)
            } else {
                null
            }

        if (cropRect != null) {
            val r = cropRect
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

        val gridBounds = cropRect ?: Rect(0f, 0f, w, h)
        when (grid) {
            GridOverlayMode.OFF -> {}
            GridOverlayMode.RULE_OF_THIRDS -> drawThirdsInRect(gridBounds, strokeW)
            GridOverlayMode.GOLDEN_RATIO_LINES -> drawGoldenLinesInRect(gridBounds, strokeW)
            GridOverlayMode.GOLDEN_SPIRAL -> drawGoldenSpiralApproxInRect(gridBounds, stroke)
            GridOverlayMode.DIAGONALS -> drawDiagonalsInRect(gridBounds, strokeW)
            GridOverlayMode.SQUARE_3X3 -> drawSquare3x3InRect(gridBounds, strokeW)
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

private fun DrawScope.drawThirdsInRect(r: Rect, strokeW: Float) {
    val w = r.width
    val h = r.height
    val x1 = r.left + w / 3f
    val x2 = r.left + 2f * w / 3f
    val y1 = r.top + h / 3f
    val y2 = r.top + 2f * h / 3f
    drawLine(GuideColor, Offset(x1, r.top), Offset(x1, r.bottom), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(x2, r.top), Offset(x2, r.bottom), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(r.left, y1), Offset(r.right, y1), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(r.left, y2), Offset(r.right, y2), strokeWidth = strokeW)
}

private fun DrawScope.drawGoldenLinesInRect(r: Rect, strokeW: Float) {
    val w = r.width
    val h = r.height
    val vx1 = r.left + w * INV_PHI
    val vx2 = r.left + w * (1f - INV_PHI)
    val hy1 = r.top + h * INV_PHI
    val hy2 = r.top + h * (1f - INV_PHI)
    drawLine(GuideColor, Offset(vx1, r.top), Offset(vx1, r.bottom), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(vx2, r.top), Offset(vx2, r.bottom), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(r.left, hy1), Offset(r.right, hy1), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(r.left, hy2), Offset(r.right, hy2), strokeWidth = strokeW)
}

private fun DrawScope.drawDiagonalsInRect(r: Rect, strokeW: Float) {
    drawLine(GuideColor, Offset(r.left, r.top), Offset(r.right, r.bottom), strokeWidth = strokeW)
    drawLine(GuideColor, Offset(r.right, r.top), Offset(r.left, r.bottom), strokeWidth = strokeW)
}

private fun DrawScope.drawSquare3x3InRect(r: Rect, strokeW: Float) {
    for (i in 1..2) {
        val x = r.left + r.width * i / 3f
        val y = r.top + r.height * i / 3f
        drawLine(GuideColor, Offset(x, r.top), Offset(x, r.bottom), strokeWidth = strokeW)
        drawLine(GuideColor, Offset(r.left, y), Offset(r.right, y), strokeWidth = strokeW)
    }
}

/** Curved guide approximating a φ spiral from corner toward the opposite quadrant. */
private fun DrawScope.drawGoldenSpiralApproxInRect(r: Rect, stroke: Stroke) {
    drawGoldenLinesInRect(r, stroke.width)
    val path =
        Path().apply {
            moveTo(r.right, r.bottom)
            cubicTo(
                r.left + r.width * INV_PHI,
                r.bottom,
                r.left + r.width * INV_PHI * INV_PHI,
                r.top + r.height * INV_PHI,
                r.left + r.width * 0.12f,
                r.top + r.height * 0.18f,
            )
        }
    drawPath(path, color = GuideColor, style = stroke)
}
