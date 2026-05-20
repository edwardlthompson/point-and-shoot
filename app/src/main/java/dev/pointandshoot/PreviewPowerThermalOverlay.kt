package dev.pointandshoot

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Sprint **13V.12** — battery % + drain estimate + thermal severity on high-drain video preview.
 */
@Composable
fun PreviewPowerThermalOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean,
    highDrainContext: PreviewHighDrainMode.Context,
    hudShowPowerThermal: Boolean,
    pollIntervalMs: Long = 2_000L,
) {
    if (!PreviewHighDrainMode.shouldShowPowerThermalOverlay(highDrainContext, hudShowPowerThermal)) {
        return
    }

    val context = LocalContext.current
    val monitor = remember { PreviewPowerThermalMonitor(context) }
    var snapshot by remember { mutableStateOf<PreviewPowerThermalMonitor.Snapshot?>(null) }

    LaunchedEffect(visible, highDrainContext, hudShowPowerThermal) {
        if (!visible) {
            monitor.reset()
            snapshot = null
            return@LaunchedEffect
        }
        monitor.reset()
        while (isActive) {
            val next = monitor.sample()
            snapshot = next
            Log.i(
                "PNS.PowerThermal",
                "battery=${next.batteryPct} drainPctPerHr=${next.drainPctPerHour} " +
                    "thermal=${next.thermalStatus} label=${next.thermalLabel} " +
                    "highDrain=${PreviewHighDrainMode.isHighDrain(highDrainContext)} " +
                    "fps=${highDrainContext.selectedFps} dcg=${highDrainContext.enableResearchDcgHdr} " +
                    "recording=${highDrainContext.isRecording}",
            )
            delay(pollIntervalMs)
        }
    }

    val snap = snapshot ?: return
    val chipBg = Color.Black.copy(alpha = 0.62f)
    val drainText = PreviewBatteryDrainEstimator.formatDrainPctPerHour(snap.drainPctPerHour)
    val batteryLine =
        buildString {
            append(snap.batteryPct?.let { "$it%" } ?: "—")
            append(" · ")
            append(drainText)
        }

    Column(
        modifier =
            modifier
                .padding(8.dp)
                .background(chipBg, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = batteryLine,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.92f),
        )
        if (snap.thermalWarning) {
            val thermalColor =
                if (PreviewThermalLabels.isSevereOrWorse(snap.thermalStatus)) {
                    PnsColors.RecordRed.copy(alpha = 0.95f)
                } else {
                    PnsColors.WarnAmber.copy(alpha = 0.95f)
                }
            Text(
                text = "THERMAL ${snap.thermalLabel}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = thermalColor,
            )
        }
    }
}
