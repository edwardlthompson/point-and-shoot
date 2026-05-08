package dev.pointandshoot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * `Settings > HUD` screen per BUILD_PLAN §5 (Phase 2).
 *
 *   * One row per HUD element with a description + toggle.
 *   * Defaults match the Pro HUD spec; experimental elements (focus peaking,
 *     histogram) are off by default and call this out in the description.
 *   * State persists to [HudSettings] via [SharedPreferences] -- no
 *     additional dependencies required.
 */
@Composable
fun HudSettingsScreen(onBack: () -> Unit) {
    val state = rememberHudSettings()
    val insets = rememberSystemInsetsDp()
    HudSettingsScreenContent(
        padding = insets.asPaddingValues(extra = 16.dp),
        settings = state.current,
        onUpdate = state.update,
        onBack = onBack,
    )
}

@Composable
private fun HudSettingsScreenContent(
    padding: PaddingValues,
    settings: HudSettings,
    onUpdate: (HudSettings) -> Unit,
    onBack: () -> Unit,
) {
    val rows: List<HudToggleRow> = remember(settings) {
        listOf(
            HudToggleRow(
                title = "Command dial (M / H / S / BKT)",
                description = "Rotary mode selector overlay (Hasselblad-orange selected segment).",
                enabled = settings.showCommandDial,
                onChange = { onUpdate(settings.copy(showCommandDial = it)) },
            ),
            HudToggleRow(
                title = "Video tally border",
                description = "Solid record-red border while video is recording.",
                enabled = settings.showVideoTally,
                onChange = { onUpdate(settings.copy(showVideoTally = it)) },
            ),
            HudToggleRow(
                title = "Timecode (HH:MM:SS:FF)",
                description = "Sony-style monospaced counter with rec / standby dot.",
                enabled = settings.showTimecode,
                onChange = { onUpdate(settings.copy(showTimecode = it)) },
            ),
            HudToggleRow(
                title = "FPS readout",
                description = "Live capture / preview frame rate, useful for HFR debugging.",
                enabled = settings.showFpsReadout,
                onChange = { onUpdate(settings.copy(showFpsReadout = it)) },
            ),
            HudToggleRow(
                title = "ISO + shutter readout",
                description = "Exposure values for the in-flight CaptureRequest.",
                enabled = settings.showIsoShutterReadout,
                onChange = { onUpdate(settings.copy(showIsoShutterReadout = it)) },
            ),
            HudToggleRow(
                title = "Highlight-weighted meter",
                description = "Ricoh GR-style 95th-percentile-luma protection indicator.",
                enabled = settings.showHighlightWeightedMeter,
                onChange = { onUpdate(settings.copy(showHighlightWeightedMeter = it)) },
            ),
            HudToggleRow(
                title = "Eye-AF overlay",
                description = "Sony-style green pupil rectangles when face detect is FULL.",
                enabled = settings.showEyeAfOverlay,
                onChange = { onUpdate(settings.copy(showEyeAfOverlay = it)) },
            ),
            HudToggleRow(
                title = "Histogram (experimental)",
                description = "RGB histogram overlay; off by default until perf is profiled.",
                enabled = settings.showHistogram,
                onChange = { onUpdate(settings.copy(showHistogram = it)) },
            ),
            HudToggleRow(
                title = "Focus peaking (Phase 1+)",
                description = "NDK shader; disabled until the native pipeline lands.",
                enabled = settings.showFocusPeaking,
                onChange = { onUpdate(settings.copy(showFocusPeaking = it)) },
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = { onUpdate(HudSettings()) }) { Text("Reset to defaults") }
        }

        Text("Settings > HUD", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Granular toggles for HUD elements. Persists across launches.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rows) { row -> HudToggle(row) }
        }
    }
}

private data class HudToggleRow(
    val title: String,
    val description: String,
    val enabled: Boolean,
    val onChange: (Boolean) -> Unit,
)

@Composable
private fun HudToggle(row: HudToggleRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(end = 12.dp).fillMaxWidth(0.78f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                row.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        }
        Switch(
            checked = row.enabled,
            onCheckedChange = row.onChange,
        )
    }
}
