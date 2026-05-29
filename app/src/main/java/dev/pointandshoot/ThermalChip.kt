package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Sprint **15.23** — compact battery + thermal readout extracted from [PreviewPowerThermalOverlay].
 */
@Composable
fun ThermalChip(
    snapshot: PreviewPowerThermalMonitor.Snapshot,
    modifier: Modifier = Modifier,
) {
    val chipBg = Color.Black.copy(alpha = 0.62f)
    val drainText = PreviewBatteryDrainEstimator.formatDrainPctPerHour(snapshot.drainPctPerHour)
    val batteryLine =
        buildString {
            append(snapshot.batteryPct?.let { "$it%" } ?: "—")
            append(" · ")
            append(drainText)
        }

    Column(
        modifier =
            modifier
                .background(chipBg, MaterialTheme.shapes.small)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = batteryLine,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.92f),
        )
        if (snapshot.thermalWarning) {
            val thermalColor =
                if (PreviewThermalLabels.isSevereOrWorse(snapshot.thermalStatus)) {
                    PnsColors.RecordRed.copy(alpha = 0.95f)
                } else {
                    PnsColors.WarnAmber.copy(alpha = 0.95f)
                }
            Text(
                text = "THERMAL ${snapshot.thermalLabel}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = thermalColor,
            )
        }
    }
}
