package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Thin horizontal rack at the bottom of the preview for manual [LENS_FOCUS_DISTANCE].
 */
@Composable
fun ManualFocusRackBar(
    diopters: Float,
    maxDiopters: Float,
    onDioptersChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeMax = maxDiopters.coerceAtLeast(0.001f)
    var localDiopters by remember(diopters, safeMax) { mutableFloatStateOf(diopters.coerceIn(0f, safeMax)) }
    var dragging by remember { mutableStateOf(false) }
    if (!dragging && kotlin.math.abs(localDiopters - diopters) > 0.0005f) {
        localDiopters = diopters.coerceIn(0f, safeMax)
    }
    val rangeLabel =
        remember(localDiopters, safeMax, dragging) {
            ManualFocusDistance.formatDioptersLong(localDiopters, safeMax)
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (dragging) {
            Text(
                text = rangeLabel,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Color.Black.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = localDiopters,
                onValueChange = {
                    dragging = true
                    localDiopters = it
                    onDioptersChange(it)
                },
                onValueChangeFinished = { dragging = false },
                valueRange = 0f..safeMax,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    SliderDefaults.colors(
                        thumbColor = Color.White.copy(alpha = 0.95f),
                        activeTrackColor = Color.White.copy(alpha = 0.55f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
            )
        }
    }
}
