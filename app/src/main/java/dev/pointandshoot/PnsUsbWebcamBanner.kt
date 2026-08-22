@file:Suppress("FunctionNaming", "MagicNumber")

package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun PnsUsbWebcamBanner(modifier: Modifier = Modifier) {
    var line by remember { mutableStateOf(PnsUsbWebcam.statusLine()) }
    var on by remember { mutableStateOf(PnsUsbWebcam.active) }
    LaunchedEffect(Unit) {
        while (true) {
            on = PnsUsbWebcam.active || PnsWebcamModeBridge.active.value
            line = PnsUsbWebcam.statusLine()
            delay(1_000)
        }
    }
    if (!on) return
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier =
            modifier
                .padding(top = 2.dp)
                .background(PnsColors.Charcoal.copy(alpha = 0.92f), shape)
                .border(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.8f), shape)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF4FC3F7),
        )
    }
}
