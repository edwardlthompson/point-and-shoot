package dev.pointandshoot

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Sprint **14.5** — developer overlay: center crosshair on the preview tile for buffer↔view
 * alignment checks (compare with [FaceTrackOverlay] / [EyeAfOverlay] after [TexturePreviewFit]).
 */
@Composable
@Suppress("FunctionNaming")
fun FaceAlignmentDebugCrosshairOverlay(modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        Log.i(TAG, "faceAlignCrosshair=visible")
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val arm = minOf(size.width, size.height) * CROSSHAIR_ARM_FRACTION
        val stroke = Stroke(width = CROSSHAIR_STROKE_DP.toPx())
        val color = Color(CROSSHAIR_COLOR_ARGB)
        drawLine(color, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = stroke.width)
        drawLine(color, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = stroke.width)
        drawCircle(color, radius = CROSSHAIR_CENTER_RADIUS_DP.toPx(), center = Offset(cx, cy), style = stroke)
    }
}

private const val TAG = "PNS.FaceAlign"
private const val CROSSHAIR_ARM_FRACTION = 0.12f
private const val CROSSHAIR_COLOR_ARGB = 0xFFFF4081.toInt()
private val CROSSHAIR_STROKE_DP = 2.dp
private val CROSSHAIR_CENTER_RADIUS_DP = 6.dp
