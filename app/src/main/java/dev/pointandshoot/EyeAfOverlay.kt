package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Sony-style Eye-AF overlay per BUILD_PLAN §4 (Phase 1):
 * "green micro-rectangles over pupils; uses STATISTICS_FACE_DETECT_MODE_FULL when available".
 *
 * Engine integration (Phase 1+):
 *   * Read `CaptureResult.STATISTICS_FACES` each frame (only meaningful when
 *     `CaptureRequest.STATISTICS_FACE_DETECT_MODE` is `_FULL`).
 *   * For each `Face` with non-null `leftEyePosition` / `rightEyePosition`,
 *     map sensor coordinates -> preview coordinates (active array crop +
 *     orientation), then build [EyeMark] entries and pass them here.
 *   * If face-detect FULL is unavailable, fall back to face bounding rects
 *     only and render slightly larger rectangles around the face.
 *
 * The overlay itself is engine-agnostic - it draws green rectangles at the
 * positions you give it, with a one-pixel-wide outer stroke and an inner
 * cross so it stays visible against high-key skin tones.
 */
@Composable
fun EyeAfOverlay(
    eyes: List<EyeMark>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00FF66),
    strokeWidth: Dp = 1.5.dp,
    sizeDp: Dp = 18.dp,
) {
    if (eyes.isEmpty()) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val sw = max(1f, strokeWidth.toPx())
        val half = sizeDp.toPx() / 2f
        for (eye in eyes) {
            val cx = eye.position.x
            val cy = eye.position.y
            val rect = Rect(
                offset = Offset(cx - half, cy - half),
                size = Size(half * 2f, half * 2f),
            )
            drawRect(
                color = color,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = sw),
            )
            // Tiny crosshair so the lock is visible even on a green eye / skin tone.
            val tick = half / 2f
            drawLine(
                color = color,
                start = Offset(cx - tick, cy),
                end = Offset(cx + tick, cy),
                strokeWidth = sw,
            )
            drawLine(
                color = color,
                start = Offset(cx, cy - tick),
                end = Offset(cx, cy + tick),
                strokeWidth = sw,
            )
        }
    }
}

/**
 * One eye-AF mark in **preview-relative pixel coordinates** (origin top-left).
 *
 * @param position Center of the eye in preview pixels (already mapped from
 *   sensor coords).
 * @param confidence 0..1 score from the face detector. Currently unused by
 *   the renderer but plumbed through so future tuning (e.g., dim the
 *   rectangle for low-confidence detections) is one-line.
 */
data class EyeMark(
    val position: Offset,
    val confidence: Float = 1f,
)
