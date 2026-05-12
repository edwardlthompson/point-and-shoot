package dev.pointandshoot

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * HUD toggles + extras for the in-preview **Settings** popup (same data as [HudSettingsScreen], chrome styling).
 */
@Composable
fun HudRailSheetContent(hudState: HudSettingsState) {
    val settings = hudState.current
    val onUpdate: (HudSettings) -> Unit = { hudState.update(it) }
    val ctx = LocalContext.current
    val cameraGranted =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    var gateLines by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(cameraGranted, ctx) {
        gateLines = if (cameraGranted) CapabilityGateBridge.uiLines(ctx) else emptyList()
    }
    val rows: List<HudToggleRow> = remember(settings) { hudToggleRows(settings, onUpdate) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Granular toggles for HUD elements. Persists across launches.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
        )

        Text(
            text = "Capability gate (rear camera)",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        if (!cameraGranted) {
            Text(
                text = "Grant camera permission to see which HUD-related features are supported on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        } else if (gateLines.isEmpty()) {
            Text(
                text = "No capability summary available (no cameras or probe failed).",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                gateLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }

        Text(
            text = "HUD elements",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row -> HudToggle(row) }
        }
        WhiteBalanceReadoutInfoCard()
        CompositionGuideQuickControls()
        TextButton(
            onClick = { onUpdate(HudSettings()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset HUD to defaults", color = Color.White.copy(alpha = 0.88f))
        }
    }
}

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
fun HudSettingsScreen(
    onBack: () -> Unit,
    initialFocus: HudSettingsFocus = HudSettingsFocus.None,
) {
    val state = rememberHudSettings()
    val insets = rememberSystemInsetsDp()
    HudSettingsScreenContent(
        padding = insets.asPaddingValues(extra = 16.dp),
        settings = state.current,
        onUpdate = state.update,
        onBack = onBack,
        initialFocus = initialFocus,
    )
}

@Composable
private fun HudSettingsScreenContent(
    padding: PaddingValues,
    settings: HudSettings,
    onUpdate: (HudSettings) -> Unit,
    onBack: () -> Unit,
    initialFocus: HudSettingsFocus,
) {
    val ctx = LocalContext.current
    val cameraGranted =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    var gateLines by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(cameraGranted, ctx) {
        gateLines = if (cameraGranted) CapabilityGateBridge.uiLines(ctx) else emptyList()
    }

    val rows: List<HudToggleRow> = remember(settings) { hudToggleRows(settings, onUpdate) }

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

        Text(
            text = "Capability gate (rear camera)",
            style = MaterialTheme.typography.titleMedium,
            color = PnsColors.PhotoOrange,
        )
        if (!cameraGranted) {
            Text(
                text = "Grant camera permission to see which HUD-related features are supported on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        } else if (gateLines.isEmpty()) {
            Text(
                text = "No capability summary available (no cameras or probe failed).",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                gateLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(initialFocus, rows) {
            if (initialFocus == HudSettingsFocus.None) return@LaunchedEffect
            delay(80)
            val index =
                when (initialFocus) {
                    HudSettingsFocus.IsoShutterReadout ->
                        rows.indexOfFirst { it.title.startsWith("ISO + shutter") }
                    HudSettingsFocus.FpsReadout ->
                        rows.indexOfFirst { it.title.startsWith("FPS readout") }
                    HudSettingsFocus.WhiteBalanceInfo -> rows.size
                    HudSettingsFocus.None -> -1
                }
            if (index >= 0) {
                listState.scrollToItem(index)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rows, key = { it.title }) { row -> HudToggle(row) }
            item(key = "wb_readout_info") {
                WhiteBalanceReadoutInfoCard()
            }
            item(key = "composition_guides") {
                CompositionGuideQuickControls()
            }
        }
    }
}

@Composable
private fun WhiteBalanceReadoutInfoCard() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "White balance (readout)",
            style = MaterialTheme.typography.titleMedium,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text =
                "The preview strip shows the active AWB mode from the camera pipeline " +
                    "(e.g. AWB, DAYLIGHT). There is no separate manual WB dial yet; " +
                    "use your camera vendor app for vendor-specific WB locks.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun CompositionGuideQuickControls() {
    val state = rememberCompositionGuideSettings()
    val c = state.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Composition guides",
            style = MaterialTheme.typography.titleMedium,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text = "Crop outlines and grids draw in white over the preview; the full sensor view stays visible (letterboxed).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        OutlinedButton(
            onClick = {
                val latest = state.current
                state.update(latest.copy(cropGuide = latest.cropGuide.next()))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Crop guide: ${c.cropGuide.label}")
        }
        OutlinedButton(
            onClick = {
                val latest = state.current
                state.update(latest.copy(gridMode = latest.gridMode.next()))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Grid: ${c.gridMode.label}")
        }
    }
}

private fun hudToggleRows(
    settings: HudSettings,
    onUpdate: (HudSettings) -> Unit,
): List<HudToggleRow> =
    listOf(
        HudToggleRow(
            title = "Command dial (A / M / H / S / BKT)",
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
            title = "Horizon level",
            description = "Accelerometer line on the preview only (Sony Photography Pro style).",
            enabled = settings.showHorizonLevel,
            onChange = { onUpdate(settings.copy(showHorizonLevel = it)) },
        ),
        HudToggleRow(
            title = "Histogram (experimental)",
            description = "RGB histogram overlay; off by default until perf is profiled.",
            enabled = settings.showHistogram,
            onChange = { onUpdate(settings.copy(showHistogram = it)) },
        ),
        HudToggleRow(
            title = "Highlight clip zebra (YUV)",
            description =
                "Diagonal hatch where preview Y ≥ ~0.95 (near clip). Uses analysis stream; off by default.",
            enabled = settings.showHighlightClipZebra,
            onChange = { onUpdate(settings.copy(showHighlightClipZebra = it)) },
        ),
        HudToggleRow(
                title = "Focus peaking (preview)",
            description =
                "Preview false-color edges when the native shader lands. " +
                    "Color and sensitivity: chrome grid → Preview & keys → " +
                    "Preview framing & overlays → Focus peaking. " +
                    "This switch is a quick on/off (on picks red if you had Off).",
            enabled = settings.focusPeakingEnabled(),
            onChange = { on ->
                onUpdate(
                    settings.copy(
                        focusPeakingColor =
                            if (on) {
                                if (settings.focusPeakingColor == FocusPeakingColor.Off) {
                                    FocusPeakingColor.Red
                                } else {
                                    settings.focusPeakingColor
                                }
                            } else {
                                FocusPeakingColor.Off
                            },
                    ),
                )
            },
        ),
    )

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
            Text(
                row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
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
