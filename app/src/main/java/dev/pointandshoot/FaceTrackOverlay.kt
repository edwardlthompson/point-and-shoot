package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Face bounds in **camera buffer** pixel space (same convention as [EyeMark.position] before
 * [TexturePreviewFit.mapBufferToView] in [PreviewEngineScreen]).
 */
data class FaceTrackBoxBuffer(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val trackingLocked: Boolean = false,
)

/** One face rectangle in **view** pixel space (inside the rotated preview tile). */
data class FaceTrackBoxView(
    val rect: Rect,
    val trackingLocked: Boolean,
)

/** Atomic update for eye marks + face tracking boxes (buffer space). */
data class FaceHudOverlayState(
    val eyeMarks: List<EyeMark>,
    val faceBoxesBuffer: List<FaceTrackBoxBuffer>,
)

/**
 * Draws a visible tracking rectangle around each detected face (Camera2 [android.hardware.camera2.params.Face] bounds).
 * Complements [EyeAfOverlay] pupil marks.
 */
@Composable
fun FaceTrackOverlay(
    faceBoxes: List<FaceTrackBoxView>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00FF66),
    strokeWidth: Dp = 2.5.dp,
) {
    if (faceBoxes.isEmpty()) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val sw = max(2f, strokeWidth.toPx())
        for (box in faceBoxes) {
            val c =
                when {
                    box.trackingLocked -> Color(0xFFFFFFFF)
                    else -> color
                }
            val stroke = if (box.trackingLocked) sw * 1.25f else sw
            drawRect(
                color = c,
                topLeft = box.rect.topLeft,
                size = box.rect.size,
                style = Stroke(width = stroke),
            )
        }
    }
}
