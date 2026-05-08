package dev.pointandshoot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Solid record-red border tally per BUILD_PLAN §5 (Phase 2).
 *
 *   * Drawn as a 1:1 stroke around the entire preview surface.
 *   * Color is locked to [PnsColors.RecordRed] (`#E00000`).
 *   * **No haptics** are fired by this composable - the haptics-on-video
 *     prohibition is enforced by `CaptureHaptics` only being invoked from
 *     still-capture paths.
 *   * Optional [pulse] mode adds a slow alpha pulse so a paused / standby
 *     recording state can be visually distinguished from active recording
 *     without changing the fundamental color.
 *
 * Usage:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     CameraPreview(...)
 *     if (isRecording) VideoTallyOverlay()
 * }
 * ```
 */
@Composable
fun VideoTallyOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    strokeWidth: Dp = 4.dp,
    pulse: Boolean = false,
) {
    if (!visible) return

    val alpha = if (pulse) {
        var a by remember { mutableStateOf(1f) }
        LaunchedEffect(Unit) {
            while (true) {
                a = 1f
                delay(700)
                a = 0.55f
                delay(700)
            }
        }
        a
    } else {
        1f
    }

    val tally = PnsColors.RecordRed.copy(alpha = alpha)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                val sw = max(1f, strokeWidth.toPx())
                val inset = sw / 2f
                drawRect(
                    color = tally,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - sw, size.height - sw),
                    style = Stroke(width = sw),
                )
            },
    )
}
