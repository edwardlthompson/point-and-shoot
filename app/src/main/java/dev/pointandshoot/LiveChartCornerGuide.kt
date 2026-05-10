package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Four-corner chart alignment overlay on the letterboxed preview (BUILD_PLAN §7).
 * Taps accumulate TL→TR→BR→BL in **local** coordinates; a fifth tap clears and restarts.
 * When four corners are set, draws the quad plus a bilinear grid (`rows` × `cols` cells).
 */
@Composable
fun LiveChartCornerGuide(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    corners: List<Offset>,
    onCornersChange: (List<Offset>) -> Unit,
    rows: Int,
    cols: Int,
) {
    if (!enabled) return
    Canvas(
        modifier =
            modifier.pointerInput(enabled, corners) {
                detectTapGestures { offset ->
                    val next =
                        when {
                            corners.size >= 4 -> listOf(offset)
                            else -> corners + offset
                        }
                    onCornersChange(next)
                }
            },
    ) {
        val strokeGuide = Stroke(width = maxOf(1.5f, size.minDimension * 0.002f))
        for (p in corners) {
            drawCircle(PnsColors.PhotoOrange, radius = maxOf(6f, size.minDimension * 0.012f), center = p)
        }
        if (corners.size != 4) return@Canvas
        val tl = Point2(corners[0].x, corners[0].y)
        val tr = Point2(corners[1].x, corners[1].y)
        val br = Point2(corners[2].x, corners[2].y)
        val bl = Point2(corners[3].x, corners[3].y)
        val cc = ChartCorners(tl, tr, br, bl)
        val outline = Path().apply {
            moveTo(corners[0].x, corners[0].y)
            lineTo(corners[1].x, corners[1].y)
            lineTo(corners[2].x, corners[2].y)
            lineTo(corners[3].x, corners[3].y)
            close()
        }
        drawPath(outline, Color.White.copy(alpha = 0.88f), style = strokeGuide)

        val gridAlpha = 0.42f
        for (i in 0..cols) {
            val u = i / cols.toFloat()
            val p0 = cc.bilinearMap(u, 0f)
            val p1 = cc.bilinearMap(u, 1f)
            drawLine(
                Color.White.copy(alpha = gridAlpha),
                Offset(p0.x, p0.y),
                Offset(p1.x, p1.y),
                strokeWidth = strokeGuide.width,
            )
        }
        for (j in 0..rows) {
            val v = j / rows.toFloat()
            val p0 = cc.bilinearMap(0f, v)
            val p1 = cc.bilinearMap(1f, v)
            drawLine(
                Color.White.copy(alpha = gridAlpha),
                Offset(p0.x, p0.y),
                Offset(p1.x, p1.y),
                strokeWidth = strokeGuide.width,
            )
        }
    }
}
