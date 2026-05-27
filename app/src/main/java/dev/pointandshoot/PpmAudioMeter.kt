package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.log10

/**
 * Sprint **15.20** — segmented PPM-style audio meter (12 segments, peak hold).
 */
@Composable
fun PpmAudioMeter(
    levelLinear: Float,
    modifier: Modifier = Modifier,
    segmentCount: Int = 12,
) {
    var peakHold by remember { mutableStateOf(0f) }
    val level = levelLinear.coerceIn(0f, 1f)
    if (level > peakHold) peakHold = level
    val lit = ((level * segmentCount).toInt()).coerceIn(0, segmentCount)
    val peakLit = ((peakHold * segmentCount).toInt()).coerceIn(0, segmentCount)
    Canvas(modifier = modifier.width(10.dp).fillMaxHeight()) {
        val segH = size.height / segmentCount
        for (i in 0 until segmentCount) {
            val idx = segmentCount - 1 - i
            val on = idx < lit || idx == peakLit - 1
            val color =
                when {
                    idx >= segmentCount - 2 -> Color(0xFFE53935)
                    idx >= segmentCount - 4 -> Color(0xFFFFB300)
                    else -> Color(0xFF43A047)
                }
            drawRect(
                color = if (on) color else Color.White.copy(alpha = 0.12f),
                topLeft = Offset(0f, i * segH),
                size = Size(size.width, segH - 1f),
            )
        }
    }
}

/** dBFS from linear amplitude (0..1). */
fun linearToDbFs(linear: Float): Float {
    val v = linear.coerceAtLeast(1e-6f)
    return (20f * log10(v)).coerceIn(-60f, 0f)
}
