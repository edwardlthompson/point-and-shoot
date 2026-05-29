package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun RackFocusWaypointSheet(
    onDismiss: () -> Unit,
    currentDiopters: Float,
    maxDiopters: Float,
    waypointNear: Float?,
    waypointFar: Float?,
    durationMs: Int,
    rackEnabled: Boolean,
    rackRunning: Boolean,
    onSetNear: () -> Unit,
    onSetFar: () -> Unit,
    onClearWaypoints: () -> Unit,
    onPickDurationMs: (Int) -> Unit,
    onRack: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A1A),
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier
                    .padding(12.dp)
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Rack focus waypoints",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Text(
                    "Set near and far focus distances, then run a smooth pull on the M dial " +
                        "(or manual distance mode). Long-press the AF readout chip to reopen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
                Text(
                    text =
                        "Current ${ManualFocusDistance.formatDioptersLong(currentDiopters, maxDiopters)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PnsColors.PhotoOrange,
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                WaypointRow(
                    label = "Near",
                    value = waypointNear,
                    maxDiopters = maxDiopters,
                    onSet = onSetNear,
                )
                WaypointRow(
                    label = "Far",
                    value = waypointFar,
                    maxDiopters = maxDiopters,
                    onSet = onSetFar,
                )
                Text(
                    "Duration",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (choice in RackFocusPull.DURATION_MS_CHOICES) {
                        val selected = RackFocusPull.coerceDurationMs(durationMs) == choice
                        val label =
                            when (choice) {
                                500 -> "0.5s"
                                1000 -> "1s"
                                2000 -> "2s"
                                3000 -> "3s"
                                else -> "${choice}ms"
                            }
                        DurationChip(
                            label = label,
                            selected = selected,
                            onClick = { onPickDurationMs(choice) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onClearWaypoints) {
                        Text("Clear", color = Color.White.copy(alpha = 0.7f))
                    }
                    RackActionButton(
                        enabled = rackEnabled && RackFocusPull.rackReady(waypointNear, waypointFar),
                        running = rackRunning,
                        onClick = onRack,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!rackEnabled) {
                    Text(
                        "Switch to M dial or manual distance to run a rack.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WaypointRow(
    label: String,
    value: Float?,
    maxDiopters: Float,
    onSet: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
            Text(
                text =
                    value?.let { ManualFocusDistance.formatDioptersLong(it, maxDiopters) }
                        ?: "Not set",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = onSet) {
            Text("Set $label", color = PnsColors.PhotoOrange)
        }
    }
}

@Composable
private fun DurationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (selected) PnsColors.PhotoOrange else Color.White.copy(alpha = 0.35f)
    val bg = if (selected) PnsColors.PhotoOrange.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.45f)
    Text(
        text = label,
        modifier =
            modifier
                .clip(shape)
                .border(1.dp, borderColor, shape)
                .background(bg)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        color = if (selected) PnsColors.PhotoOrange else Color.White.copy(alpha = 0.85f),
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun RackActionButton(
    enabled: Boolean,
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val active = enabled || running
    val borderColor =
        when {
            running -> Color.White.copy(alpha = 0.85f)
            enabled -> PnsColors.PhotoOrange
            else -> Color.White.copy(alpha = 0.25f)
        }
    val bg =
        when {
            running -> Color.White.copy(alpha = 0.12f)
            enabled -> PnsColors.PhotoOrange.copy(alpha = 0.22f)
            else -> Color.Black.copy(alpha = 0.35f)
        }
    val label = if (running) "■ Stop rack" else "▶ Rack"
    Text(
        text = label,
        modifier =
            modifier
                .clip(shape)
                .border(1.dp, borderColor, shape)
                .background(bg)
                .clickable(enabled = active, onClick = onClick)
                .padding(vertical = 10.dp),
        color = if (active) Color.White else Color.White.copy(alpha = 0.4f),
        style = MaterialTheme.typography.titleSmall,
    )
}
