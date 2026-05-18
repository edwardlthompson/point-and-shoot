package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Sprint 13.8: Dual-channel audio level meter overlay for the live preview.
 *
 * Polls [sampleAmplitude] every [pollMs] milliseconds and renders two thin
 * vertical bars (L / R mirrored for stereo symmetry, or identical for mono).
 * Bar fill is green below -12 dBFS, amber from -12 to -3 dBFS, red above.
 *
 * Only visible while [isRecording] is true. Positioned by the caller.
 */
@Composable
fun AudioLevelMeter(
    isRecording: Boolean,
    sampleAmplitude: () -> Int,
    modifier: Modifier = Modifier,
    barWidth: Dp = 6.dp,
    barHeight: Dp = 56.dp,
    pollMs: Long = 100L,
) {
    if (!isRecording) return

    var level by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            val raw = sampleAmplitude().coerceIn(0, 32767)
            level = raw / 32767f
            delay(pollMs)
        }
        level = 0f
    }

    val barColor = when {
        level > 0.708f -> PnsColors.RecordRed          // > -3 dBFS
        level > 0.251f -> PnsColors.WarnAmber           // > -12 dBFS
        else           -> Color(0xFF44CC66)              // safe green
    }

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(2) {
            AudioBar(
                level = level,
                color = barColor,
                width = barWidth,
                maxHeight = barHeight,
            )
        }
    }
}

@Composable
private fun AudioBar(
    level: Float,
    color: Color,
    width: Dp,
    maxHeight: Dp,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .height(maxHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.15f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val fillFraction = level.coerceIn(0f, 1f)
        if (fillFraction > 0f) {
            Spacer(
                modifier =
                    Modifier
                        .width(width)
                        .fillMaxHeight(fillFraction)
                        .background(color),
            )
        }
    }
}
