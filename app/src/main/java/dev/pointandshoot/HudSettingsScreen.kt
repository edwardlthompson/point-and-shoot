package dev.pointandshoot

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.widget.Toast
import dev.pointandshoot.fleet.FleetUiVisibilityGate
import kotlin.math.roundToInt

/**
 * HUD toggles + extras for the in-preview **Settings** popup (same data as [HudSettingsScreen], chrome styling).
 */
@Composable
fun HudRailSheetContent(
    hudState: HudSettingsState,
    themeMode: PnsThemeMode = PnsThemeMode.System,
    onThemeModeChange: (PnsThemeMode) -> Unit = {},
    onPictureProfileImaging: ((ImagingProfile) -> Unit)? = null,
    onApplyWorkflowPreset: ((WorkflowPreset) -> Unit)? = null,
    activeCameraId: String? = null,
    cameraIds: List<String> = emptyList(),
    highlightSettingKey: String? = null,
    onHighlightConsumed: () -> Unit = {},
) {
    val settings = hudState.current
    val onUpdate: (HudSettings) -> Unit = { hudState.update(it) }
    val patchHud: ((HudSettings) -> HudSettings) -> Unit = { transform ->
        hudState.update(transform(hudState.current))
    }
    val ctx = LocalContext.current
    val cameraGranted =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    var gateLines by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(cameraGranted, ctx) {
        gateLines = if (cameraGranted) CapabilityGateBridge.uiLines(ctx) else emptyList()
    }
    var developerUnlocked by remember { mutableStateOf(false) }
    val appCtx = ctx.applicationContext
    val visibilityCtx =
        remember(activeCameraId, cameraIds) {
            FleetUiVisibilityGate.buildContext(appCtx, activeCameraId, cameraIds)
        }
    val showEyeAfOverlay =
        FleetUiVisibilityGate.visible("face.eye_af", visibilityCtx)
    val rows: List<HudToggleRow> =
        remember(settings, developerUnlocked, showEyeAfOverlay) {
            hudToggleRows(
                settings,
                patchHud,
                includeResearch = developerUnlocked,
                showEyeAfOverlay = showEyeAfOverlay,
            )
        }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChromeSettingsIntroText(
            text =
                if (developerUnlocked) {
                    "Granular toggles for HUD elements (research items visible). Persists across launches."
                } else {
                    "Granular toggles for HUD elements. Long-press this line to show research toggles."
                },
            modifier =
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { developerUnlocked = true },
                ),
        )

        UxAppearanceSection(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )
        WorkflowPresetsHudSection(
            context = ctx,
            onApplyPreset = onApplyWorkflowPreset,
        )
        CloudBackupHudSection(context = ctx)

        if (developerUnlocked) {
        PreviewRailSectionTitle("Capability gate (rear camera)")
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

        FpsQuickChip(
            label = "Rescan cameras (shallow cache)",
            selected = false,
            requiresRoot = false,
            onClick = {
                ShallowCapabilityCacheStore.requestShallowScanRescan(ctx.applicationContext)
                Toast.makeText(
                    ctx,
                    "Shallow rescan queued — reopen Diagnostics hub to refresh.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = ShallowCapabilityCacheStore.lastScanSummaryLine(ctx),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.52f),
        )
        }

    val highlightFlash = rememberSettingHighlightFlash()
    LaunchedEffect(highlightSettingKey) {
        val key = highlightSettingKey ?: return@LaunchedEffect
        delay(120)
        highlightFlash.request(key)
        onHighlightConsumed()
    }

        PreviewRailSectionTitle("HUD elements")
        HudStillCaptureModeRow(
            mode = settings.stillCaptureMode,
            onModeChange = { mode -> onUpdate(settings.copy(stillCaptureMode = mode)) },
        )
        AdvancedCaptureHudSection(
            settings = settings,
            onUpdate = onUpdate,
        )
        ProFeaturesHudSection(
            settings = settings,
            hudState = hudState,
            onImagingProfile = onPictureProfileImaging,
        )
        ExperimentalUnlockHudSection(
            settings = settings,
            onUpdate = onUpdate,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row -> HudToggle(row, highlightFlash = highlightFlash) }
        }
        ZebraIreThresholdSection(
            ire = settings.zebraIreThreshold,
            onIreChange = { v -> patchHud { it.copy(zebraIreThreshold = v) } },
        )
        WhiteBalanceReadoutInfoCard()
        CompositionGuideQuickControls()
        TextButton(
            onClick = {
                onUpdate(HudSettings())
            },
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
    themeMode: PnsThemeMode = PnsThemeMode.System,
    onThemeModeChange: (PnsThemeMode) -> Unit = {},
    onBack: () -> Unit,
    initialFocus: HudSettingsFocus = HudSettingsFocus.None,
    highlightSettingKey: String? = null,
    onHighlightConsumed: () -> Unit = {},
    onReplayWelcomeTips: (() -> Unit)? = null,
) {
    val state = rememberHudSettings()
    val insets = rememberSystemInsetsDp()
    HudSettingsScreenContent(
        padding = insets.asPaddingValues(extra = 16.dp),
        hudState = state,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onBack = onBack,
        initialFocus = initialFocus,
        highlightSettingKey = highlightSettingKey,
        onHighlightConsumed = onHighlightConsumed,
        onReplayWelcomeTips = onReplayWelcomeTips,
    )
}

@Composable
private fun HudSettingsScreenContent(
    padding: PaddingValues,
    hudState: HudSettingsState,
    themeMode: PnsThemeMode,
    onThemeModeChange: (PnsThemeMode) -> Unit,
    onBack: () -> Unit,
    initialFocus: HudSettingsFocus,
    highlightSettingKey: String? = null,
    onHighlightConsumed: () -> Unit = {},
    onReplayWelcomeTips: (() -> Unit)?,
) {
    val settings = hudState.current
    val onUpdate: (HudSettings) -> Unit = { hudState.update(it) }
    val patchHud: ((HudSettings) -> HudSettings) -> Unit = { transform ->
        hudState.update(transform(hudState.current))
    }
    val ctx = LocalContext.current
    val cameraGranted =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    var gateLines by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(cameraGranted, ctx) {
        gateLines = if (cameraGranted) CapabilityGateBridge.uiLines(ctx) else emptyList()
    }
    var dualIsoMultiResSupported by remember { mutableStateOf(false) }
    LaunchedEffect(cameraGranted, ctx) {
        dualIsoMultiResSupported =
            if (cameraGranted) {
                DualIsoVideoMerger.probeMultiResFromContext(ctx)
            } else {
                false
            }
    }

    val appCtx = ctx.applicationContext
    val cameraIds =
        remember(cameraGranted) {
            if (!cameraGranted) {
                emptyList()
            } else {
                runCatching {
                    val cm = appCtx.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    cm.cameraIdList.toList()
                }.getOrDefault(emptyList())
            }
        }
    val visibilityCtx =
        remember(cameraIds) {
            FleetUiVisibilityGate.buildContext(appCtx, activeCameraId = null, cameraIds)
        }
    val showEyeAfOverlay =
        FleetUiVisibilityGate.visible("face.eye_af", visibilityCtx)

    val highlightFlash = rememberSettingHighlightFlash()
    LaunchedEffect(highlightSettingKey) {
        val key = highlightSettingKey ?: return@LaunchedEffect
        delay(120)
        highlightFlash.request(key)
        onHighlightConsumed()
    }

    val rows: List<HudToggleRow> =
        remember(settings, showEyeAfOverlay) {
            hudToggleRows(settings, patchHud, showEyeAfOverlay = showEyeAfOverlay)
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
            OutlinedButton(
                onClick = {
                    onUpdate(HudSettings())
                },
            ) {
                Text("Reset to defaults")
            }
            if (onReplayWelcomeTips != null) {
                OutlinedButton(onClick = onReplayWelcomeTips) { Text("Replay welcome tips") }
            }
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

        OutlinedButton(
            onClick = {
                ShallowCapabilityCacheStore.requestShallowScanRescan(ctx.applicationContext)
                Toast.makeText(
                    ctx,
                    "Shallow rescan queued — reopen Diagnostics hub to refresh.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Rescan cameras (shallow cache)")
        }
        Text(
            text = ShallowCapabilityCacheStore.lastScanSummaryLine(ctx),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.52f),
        )

        val listState = rememberLazyListState()
        val highlightFlash = rememberSettingHighlightFlash()
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

        LaunchedEffect(highlightSettingKey, rows) {
            val key = highlightSettingKey ?: return@LaunchedEffect
            delay(80)
            val rowIndex = rows.indexOfFirst { it.settingKey == key }
            if (rowIndex >= 0) {
                listState.scrollToItem(13 + rowIndex)
            }
            highlightFlash.request(key)
            onHighlightConsumed()
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "ux_appearance") {
                UxAppearanceSection(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                )
            }
            item(key = "cloud_backup") {
                CloudBackupHudSection(context = LocalContext.current)
            }
            item(key = "still_capture_mode") {
                HudStillCaptureModeRow(
                    mode = settings.stillCaptureMode,
                    onModeChange = { mode -> onUpdate(settings.copy(stillCaptureMode = mode)) },
                )
            }
            item(key = "advanced_capture") {
                AdvancedCaptureHudSection(settings = settings, onUpdate = onUpdate)
            }
            item(key = "pro_features_cc3") {
                ProFeaturesHudSection(
                    settings = settings,
                    hudState = hudState,
                    onImagingProfile = null,
                )
            }
            item(key = "experimental_max_res_unlock") {
                ExperimentalUnlockHudSection(
                    settings = settings,
                    onUpdate = onUpdate,
                )
            }
            item(key = "video_bitrate_scale") {
                VideoBitrateScaleSection(
                    scalePercent = settings.videoBitrateScalePercent,
                    onScaleChange = { pct -> onUpdate(settings.copy(videoBitrateScalePercent = pct)) },
                )
            }
            item(key = "video_audio_source") {
                VideoAudioSourceSection(
                    selected = settings.videoAudioSourceEnum(),
                    onSelect = { src -> onUpdate(settings.copy(videoAudioSource = src.storageId)) },
                )
            }
            item(key = "video_audio_gain") {
                VideoAudioGainSection(
                    gainDb = settings.audioGainDb,
                    onGainChange = { db -> onUpdate(settings.copy(audioGainDb = db)) },
                )
            }
            item(key = "video_wind_noise_filter") {
                VideoWindNoiseFilterSection(
                    enabled = settings.windNoiseFilterEnabled,
                    camcorderSource = settings.videoAudioSourceEnum() == VideoAudioSource.Camcorder,
                    onChange = { on -> onUpdate(settings.copy(windNoiseFilterEnabled = on)) },
                )
            }
            item(key = "video_dual_iso") {
                VideoDualIsoSection(
                    enabled = settings.dualIsoVideoEnabled,
                    multiResSupported = dualIsoMultiResSupported,
                    onChange = { on -> onUpdate(settings.copy(dualIsoVideoEnabled = on)) },
                )
            }
            item(key = "video_focus_breathing") {
                VideoFocusBreathingSection(
                    enabled = settings.enableFocusBreathingComp,
                    k = settings.focusBreathingCompK,
                    onEnabledChange = { on -> onUpdate(settings.copy(enableFocusBreathingComp = on)) },
                    onKChange = { k -> onUpdate(settings.copy(focusBreathingCompK = k)) },
                )
            }
            item(key = "rack_focus_waypoints_info") {
                RackFocusWaypointsInfoSection()
            }
            item(key = "video_histogram_during_recording") {
                VideoHistogramDuringRecordingSection(
                    enabled = settings.showHistogramDuringVideo,
                    onChange = { on -> onUpdate(settings.copy(showHistogramDuringVideo = on)) },
                )
            }
            item(key = "zebra_ire_threshold") {
                ZebraIreThresholdSection(
                    ire = settings.zebraIreThreshold,
                    onIreChange = { v -> onUpdate(settings.copy(zebraIreThreshold = v)) },
                )
            }
            items(rows, key = { it.settingKey ?: it.title }) { row -> HudToggle(row, highlightFlash = highlightFlash) }
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
private fun ZebraIreThresholdSection(
    ire: Int,
    onIreChange: (Int) -> Unit,
) {
    val clamped = ire.coerceIn(PreviewLumaHistogram.IRE_MIN, PreviewLumaHistogram.IRE_MAX)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Zebra IRE threshold ($clamped)",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text = "75–100 IRE maps to near-clip luma on the YUV analysis grid (Sprint 15.21).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        androidx.compose.material3.Slider(
            value = clamped.toFloat(),
            onValueChange = { v ->
                onIreChange(v.toInt().coerceIn(PreviewLumaHistogram.IRE_MIN, PreviewLumaHistogram.IRE_MAX))
            },
            valueRange = PreviewLumaHistogram.IRE_MIN.toFloat()..PreviewLumaHistogram.IRE_MAX.toFloat(),
            steps = PreviewLumaHistogram.IRE_MAX - PreviewLumaHistogram.IRE_MIN - 1,
        )
    }
}

@Composable
private fun VideoBitrateScaleSection(
    scalePercent: Int,
    onScaleChange: (Int) -> Unit,
) {
    val scale = scalePercent.coerceIn(HudSettings.VIDEO_BITRATE_SCALE_MIN, HudSettings.VIDEO_BITRATE_SCALE_MAX)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Video bitrate scale (manual)",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text = "Scales in-app encode bitrate ($scale% of MediaCodec probe table, 50–150%).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        androidx.compose.material3.Slider(
            value = scale.toFloat(),
            onValueChange = { v -> onScaleChange(v.roundToInt()) },
            valueRange = HudSettings.VIDEO_BITRATE_SCALE_MIN.toFloat()..
                HudSettings.VIDEO_BITRATE_SCALE_MAX.toFloat(),
            steps = 9,
        )
    }
}

@Composable
private fun VideoAudioGainSection(
    gainDb: Float,
    onGainChange: (Float) -> Unit,
) {
    val gain = HudSettings.coerceAudioGainDb(gainDb)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Recording audio gain",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text =
                "Boost or cut mic level for in-app MediaCodec video (−12 to +12 dB). " +
                    "Applied to PCM before AAC encode.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(
            text = "${"%.1f".format(gain)} dB (${"%.2f".format(HudSettings.audioGainDbToLinear(gain))}×)",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        androidx.compose.material3.Slider(
            value = gain,
            onValueChange = { v -> onGainChange(HudSettings.coerceAudioGainDb(v)) },
            valueRange = HudSettings.AUDIO_GAIN_DB_MIN..HudSettings.AUDIO_GAIN_DB_MAX,
            steps = 47,
        )
    }
}

@Composable
private fun VideoWindNoiseFilterSection(
    enabled: Boolean,
    camcorderSource: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Wind noise filter",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text =
                "When enabled with Camcorder audio source, attaches NoiseSuppressor and " +
                    "AcousticEchoCanceler after recording starts.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = onChange,
            enabled = camcorderSource,
        )
        if (!camcorderSource) {
            Text(
                text = "Requires Camcorder audio source.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun VideoDualIsoSection(
    enabled: Boolean,
    multiResSupported: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Dual ISO video (experimental)",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text =
                "Dual ISO video — probes SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP and " +
                    "applies log-domain short/long merge on RAW video frames when enabled.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = onChange,
            enabled = multiResSupported,
        )
        if (!multiResSupported) {
            Text(
                text = "Not supported on this device (multi-res stream map unavailable).",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun VideoAudioSourceSection(
    selected: VideoAudioSource,
    onSelect: (VideoAudioSource) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Video audio source",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text =
                "Mic path for in-app MediaCodec / MediaRecorder capture. Unprocessed requires API 24+ " +
                    "(falls back to camcorder on older devices).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        VideoAudioSource.entries.forEach { source ->
            OutlinedButton(
                onClick = { onSelect(source) },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor =
                            if (source == selected) {
                                PnsColors.PhotoOrange
                            } else {
                                Color.White.copy(alpha = 0.85f)
                            },
                    ),
            ) {
                Text(
                    text = if (source == selected) "${source.label} (active)" else source.label,
                )
            }
        }
    }
}

@Composable
private fun RackFocusWaypointsInfoSection() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Rack focus waypoints",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text =
                "Long-press the AF readout chip on the preview strip to set near/far waypoints " +
                    "and run a smooth rack on the M dial (or manual distance mode).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun VideoHistogramDuringRecordingSection(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Histogram during video recording",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text =
                "Keeps the YUV analysis stream armed in video mode and shows the luma histogram " +
                    "overlay while recording. Does not restart the capture session mid-record.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        HudToggle(
            HudToggleRow(
                title = "Show histogram while recording",
                description = "Bottom-right luma histogram during in-app video record.",
                enabled = enabled,
                onChange = onChange,
            ),
        )
    }
}

@Composable
private fun VideoFocusBreathingSection(
    enabled: Boolean,
    k: Float,
    onEnabledChange: (Boolean) -> Unit,
    onKChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Focus breathing compensation (15.28)",
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange,
        )
        Text(
            text =
                "M dial + tele focal slot + manual focus: subtly widens preview crop when focus racks " +
                    "closer to counter lens breathing. Log: PNS.FocusBreathing.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        HudToggle(
            HudToggleRow(
                title = "Focus breathing compensation",
                description = "Tele manual-focus crop nudge (M dial).",
                enabled = enabled,
                onChange = onEnabledChange,
            ),
        )
        Text(
            "Compensation strength (k)",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.003f to "Light", 0.005f to "Normal", 0.008f to "Strong").forEach { (value, label) ->
                val sel = kotlin.math.abs(k - value) < 0.0005f
                OutlinedButton(
                    onClick = { onKChange(value) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        label,
                        color = if (sel) PnsColors.PhotoOrange else Color.White,
                    )
                }
            }
        }
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
    patch: ((HudSettings) -> HudSettings) -> Unit,
    includeResearch: Boolean = false,
    showEyeAfOverlay: Boolean = true,
): List<HudToggleRow> {
    val rows =
        listOf(
        HudToggleRow(
            title = "Command dial (A / M / H / S / BKT)",
            description = "Rotary mode selector overlay (Hasselblad-orange selected segment).",
            enabled = settings.showCommandDial,
            onChange = { checked -> patch { it.copy(showCommandDial = checked) } },
        ),
        HudToggleRow(
            title = "Video tally border",
            description = "Solid record-red border while video is recording.",
            enabled = settings.showVideoTally,
            onChange = { checked -> patch { it.copy(showVideoTally = checked) } },
            settingKey = "hud.video_tally",
        ),
        HudToggleRow(
            title = "Pillar-bar video HUD",
            description =
                "During 16:9 video recording, show timecode + battery/thermal in the left " +
                    "letterbox pillar and PPM audio meters in the right pillar (when wide enough).",
            enabled = settings.showVideoPillarHud,
            onChange = { checked -> patch { it.copy(showVideoPillarHud = checked) } },
        ),
        HudToggleRow(
            title = "Timecode (HH:MM:SS:FF)",
            description = "Sony-style monospaced counter with rec / standby dot.",
            enabled = settings.showTimecode,
            onChange = { checked -> patch { it.copy(showTimecode = checked) } },
        ),
        HudToggleRow(
            title = "Power + thermal (HFR / DCG)",
            description = "Battery % and drain rate on high-drain video preview; thermal warning when hot.",
            enabled = settings.showPowerThermalOverlay,
            onChange = { checked -> patch { it.copy(showPowerThermalOverlay = checked) } },
        ),
        HudToggleRow(
            title = "Storage remaining (video)",
            description = "Estimated minutes left at the current encode bitrate; warns below 5 minutes.",
            enabled = settings.showStorageRemainingOverlay,
            onChange = { checked -> patch { it.copy(showStorageRemainingOverlay = checked) } },
        ),
        HudToggleRow(
            title = "FPS readout",
            description = "Live capture / preview frame rate, useful for HFR debugging.",
            enabled = settings.showFpsReadout,
            onChange = { checked -> patch { it.copy(showFpsReadout = checked) } },
            settingKey = "hud.fps_readout",
        ),
        HudToggleRow(
            title = "ISO + shutter readout",
            description = "Exposure values for the in-flight CaptureRequest.",
            enabled = settings.showIsoShutterReadout,
            onChange = { checked -> patch { it.copy(showIsoShutterReadout = checked) } },
            settingKey = "hud.iso_shutter",
        ),
        HudToggleRow(
            title = "Highlight-weighted meter",
            description = "Ricoh GR-style 95th-percentile-luma protection indicator.",
            enabled = settings.showHighlightWeightedMeter,
            onChange = { checked -> patch { it.copy(showHighlightWeightedMeter = checked) } },
        ),
    )
    val eyeAfRows =
        if (showEyeAfOverlay) {
            listOf(
                HudToggleRow(
                    title = "Eye-AF overlay",
                    description = "Sony-style green pupil rectangles when face detect is FULL.",
                    enabled = settings.showEyeAfOverlay,
                    onChange = { checked -> patch { it.copy(showEyeAfOverlay = checked) } },
                    settingKey = "hud.eye_af",
                ),
            )
        } else {
            emptyList()
        }
    val tail =
        listOf(
        HudToggleRow(
            title = "Face alignment crosshair (14.5 debug)",
            description =
                "Center crosshair on the preview tile — compare with face/eye boxes after cover-crop mapping.",
            enabled = settings.showFaceAlignmentDebugCrosshair,
            onChange = { checked -> patch { it.copy(showFaceAlignmentDebugCrosshair = checked) } },
        ),
        HudToggleRow(
            title = "Smile-triggered still (13V.17)",
            description =
                "Photo mode: ML Kit smile probability fires the tray still path (~4.5 s cooldown). " +
                    "Uses YUV analysis; enable Eye-AF or this toggle alone.",
            enabled = settings.enableSmileTriggeredStill,
            onChange = { checked -> patch { it.copy(enableSmileTriggeredStill = checked) } },
        ),
        HudToggleRow(
            title = "Scene vendor hints (read-only)",
            description =
                "Logs OEM scene/quality Camera2 keys at startup (PNS.SceneHint). " +
                    "Often empty on LineageOS; no capture behavior yet.",
            enabled = settings.showSceneVendorHints,
            onChange = { checked -> patch { it.copy(showSceneVendorHints = checked) } },
        ),
        HudToggleRow(
            title = "Horizon level",
            description = "Accelerometer line on the preview only (Sony Photography Pro style).",
            enabled = settings.showHorizonLevel,
            onChange = { checked -> patch { it.copy(showHorizonLevel = checked) } },
            settingKey = "hud.horizon",
        ),
        HudToggleRow(
            title = "Histogram (experimental)",
            description = "RGB histogram overlay; off by default until perf is profiled.",
            enabled = settings.showHistogram,
            onChange = { checked -> patch { it.copy(showHistogram = checked) } },
            settingKey = "hud.histogram",
        ),
        HudToggleRow(
            title = "Highlight clip zebra (YUV)",
            description =
                "Diagonal hatch where preview Y exceeds the IRE threshold below. Uses analysis stream; off by default.",
            enabled = settings.showHighlightClipZebra,
            onChange = { checked ->
                patch {
                    it.copy(
                        showHighlightClipZebra = checked,
                        falseColorMode =
                            if (checked && it.falseColorModeEnum() == FalseColorMode.Off) {
                                FalseColorMode.ZebraOnly.storageId
                            } else if (!checked && it.falseColorModeEnum() == FalseColorMode.ZebraOnly) {
                                FalseColorMode.Off.storageId
                            } else {
                                it.falseColorMode
                            },
                    )
                }
            },
        ),
        HudToggleRow(
            title = "False color: Off",
            description = "No zebra or false-color YUV overlay.",
            enabled = settings.falseColorModeEnum() == FalseColorMode.Off,
            onChange = { on ->
                if (!on) return@HudToggleRow
                patch { it.copy(falseColorMode = FalseColorMode.Off.storageId, showHighlightClipZebra = false) }
            },
        ),
        HudToggleRow(
            title = "False color: Zebra only",
            description = "Near-clip zebra at any preview FPS when YUV analysis is active.",
            enabled = settings.falseColorModeEnum() == FalseColorMode.ZebraOnly,
            onChange = { on ->
                if (!on) return@HudToggleRow
                patch {
                    it.copy(
                        falseColorMode = FalseColorMode.ZebraOnly.storageId,
                        showHighlightClipZebra = true,
                    )
                }
            },
        ),
        HudToggleRow(
            title = "False color: Exposure bands",
            description = "Blue shadows, orange highs, red clip on YUV analysis grid.",
            enabled = settings.falseColorModeEnum() == FalseColorMode.FalseColor,
            onChange = { on ->
                if (!on) return@HudToggleRow
                patch {
                    it.copy(
                        falseColorMode = FalseColorMode.FalseColor.storageId,
                        showHighlightClipZebra = false,
                    )
                }
            },
        ),
        HudToggleRow(
            title = "Focus peaking (preview / video)",
            description =
                "Edge-based false color on the GL preview (high-contrast luma gradients), not a Camera2 " +
                    "AF confirmation — tele scenes can peak while the RAW plane is slightly soft. " +
                    "Color and sensitivity: chrome grid → Preview & keys → Preview framing & overlays → Focus peaking. " +
                    "This switch is a quick on/off (on picks red if you had Off).",
            enabled = settings.focusPeakingEnabled(),
            onChange = { on ->
                patch {
                    it.copy(
                        focusPeakingColor =
                            if (on) {
                                if (it.focusPeakingColor == FocusPeakingColor.Off) {
                                    FocusPeakingColor.Red
                                } else {
                                    it.focusPeakingColor
                                }
                            } else {
                                FocusPeakingColor.Off
                            },
                    )
                }
            },
        ),
        HudToggleRow(
            title = "Optical stabilization (OIS)",
            description =
                "When the HAL exposes OIS, request ON for preview + stills (Camera2). " +
                    "Quick tile: grid row 2 column 3.",
            enabled = settings.enableLensOpticalStabilization,
            onChange = { checked -> patch { it.copy(enableLensOpticalStabilization = checked) } },
        ),
        HudToggleRow(
            title = "Tripod / static: OIS off for stills only",
            description =
                "When OIS is otherwise enabled, still captures can force optical stabilization OFF " +
                    "if the HAL lists OFF (preview unchanged). Try on a tripod if tele stills look smeared.",
            enabled = settings.disableOisForStillCapture,
            onChange = { checked -> patch { it.copy(disableOisForStillCapture = checked) } },
        ),
        HudToggleRow(
            title = "Electronic stabilization (EIS)",
            description =
                "Electronic stabilization on the preview stream only; off by default. " +
                    "Skipped for HFR (≥120 fps target) and for still captures. " +
                    "Quick tile: grid row 2 column 4.",
            enabled = settings.enableVideoStabilizationPreview,
            onChange = { checked -> patch { it.copy(enableVideoStabilizationPreview = checked) } },
        ),
        HudToggleRow(
            title = "Camera2 auto-framing (preview)",
            description =
                "HAL auto-framing when supported (Android 15+ / API 35). Off by default; distinct from in-app face overlays.",
            enabled = settings.enableAutoFraming,
            onChange = { checked -> patch { it.copy(enableAutoFraming = checked) } },
        ),
        HudToggleRow(
            title = "HDR / 10-bit preview session",
            description =
                "When supported (API 33+), applies a validated dynamic-range profile on the preview " +
                    "OutputConfiguration only if the full preview+RAW+analysis surface list passes " +
                    "isSessionConfigurationSupported. Off by default.",
            enabled = settings.enableHdr10LivePreview,
            onChange = { checked -> patch { it.copy(enableHdr10LivePreview = checked) } },
        ),
        HudToggleRow(
            title = "Research: AF bracketing session vendor key",
            description =
                "When advertised (Qualcomm `EnableAFBracketing` session key), attaches it to REGULAR " +
                    "preview session parameters. Off by default — HAL-specific; disable if preview fails to open.",
            enabled = settings.enableResearchAfBracketing,
            onChange = { checked -> patch { it.copy(enableResearchAfBracketing = checked) } },
        ),
        HudToggleRow(
            title = "Research: HFR AI Camera HSR (120fps)",
            description =
                "When advertised (Qualcomm `EnableAICameraHSR` session key), attaches it to REGULAR " +
                    "preview session parameters to enable AI Camera High Speed Recording. Off by default — " +
                    "HAL-specific; Milestone 13.3 research for 120fps video.",
            enabled = settings.enableResearchHfrAICameraHSR,
            onChange = { checked -> patch { it.copy(enableResearchHfrAICameraHSR = checked) } },
        ),
        HudToggleRow(
            title = "Research: HFR VIULL (Ultra Low Latency)",
            description =
                "When advertised (Qualcomm `EnableVIULL` session key), attaches it to REGULAR " +
                    "preview session parameters to enable Video ISP Ultra Low Latency mode (critical for HFR). " +
                    "Off by default — HAL-specific; Milestone 13.3 research for 120fps video.",
            enabled = settings.enableResearchHfrVIULL,
            onChange = { checked -> patch { it.copy(enableResearchHfrVIULL = checked) } },
        ),
        HudToggleRow(
            title = "Research: HFR VSR (Video Stabilization Rotation)",
            description =
                "When advertised (Qualcomm `EnableVSR` session key), attaches it to REGULAR " +
                    "preview session parameters to enable Video Stabilization Rotation (may be required for HFR). " +
                    "Off by default — HAL-specific; Milestone 13.3 research for 120fps video.",
            enabled = settings.enableResearchHfrVSR,
            onChange = { checked -> patch { it.copy(enableResearchHfrVSR = checked) } },
        ),
        HudToggleRow(
            title = "Video: RAW lane (.mcraw, legacy)",
            description =
                    "Records preview-session RAW frames to a P&S MCRAW-class `.mcraw` file (no MediaRecorder). " +
                    "Legacy leaf-camera path only. Disables DCG HDR session while active.",
            enabled = settings.videoEncodeLane == VideoEncodeLane.Raw,
            onChange = { rawOn ->
                patch {
                    it.copy(
                        videoEncodeLane = if (rawOn) VideoEncodeLane.Raw else VideoEncodeLane.Encoded,
                        enableResearchDcgHDR = if (rawOn) false else it.enableResearchDcgHDR,
                    )
                }
            },
        ),
        HudToggleRow(
            title = "Research: DCG HDR mode (10-bit)",
            description =
                "When advertised (Qualcomm `EnableHDRDCGMode` session key), attaches it to REGULAR " +
                    "preview session parameters to enable Dual Conversion Gain mode for HDR video. " +
                    "Off by default — HAL-specific; Milestone 13.2 research for 10-bit video. " +
                    "Disabled while RAW video lane is on.",
            enabled = settings.enableResearchDcgHDR,
            onChange = { dcgOn ->
                patch {
                    it.copy(
                        enableResearchDcgHDR = dcgOn,
                        videoEncodeLane = if (dcgOn) VideoEncodeLane.Encoded else it.videoEncodeLane,
                    )
                }
            },
        ),
        HudToggleRow(
            title = "Research: Qualcomm HDR mode (10-bit)",
            description =
                "When advertised (Qualcomm `EnableQHDR` session key), attaches it to REGULAR " +
                    "preview session parameters to enable Qualcomm HDR mode for 10-bit video. " +
                    "Off by default — HAL-specific; Milestone 13.2 research for 10-bit video.",
            enabled = settings.enableResearchQHDR,
            onChange = { checked -> patch { it.copy(enableResearchQHDR = checked) } },
        ),
        HudToggleRow(
            title = "External Camera App–style AF settle before RAW still",
            description =
                "In-app only (not scripted ADB), flash off: after stopRepeating, run preview-only AF " +
                    "polling captures before the high-res still. Off by default — try for tele tripod softness.",
            enabled = settings.enableExternalCameraAppStyleAfSettleBeforeStill,
            onChange = { checked -> patch { it.copy(enableExternalCameraAppStyleAfSettleBeforeStill = checked) } },
        ),
        HudToggleRow(
            title = "Wait for AF before still (shutter gate)",
            description =
                "In-app only: after shutter, run AF precapture triggers and block capture until the HAL " +
                    "reports passive focused or focused locked (or timeout). Skipped for manual ISO/shutter, " +
                    "S dial, flash Auto/On/Torch on back hardware, and HFR constrained preview. Peaking is still edge-based, not this signal.",
            enabled = settings.waitForAfFocusBeforeStill,
            onChange = { checked -> patch { it.copy(waitForAfFocusBeforeStill = checked) } },
        ),
    )
    val allRows = rows + eyeAfRows + tail
    return if (includeResearch) {
        allRows
    } else {
        allRows.filter { !it.title.startsWith("Research:") }
    }
}

private data class HudToggleRow(
    val title: String,
    val description: String,
    val enabled: Boolean,
    val onChange: (Boolean) -> Unit,
    val settingKey: String? = null,
)

@Composable
private fun AdvancedCaptureHudSection(
    settings: HudSettings,
    onUpdate: (HudSettings) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewRailSectionTitle("Advanced capture (CC.1)")
        HudToggle(
            HudToggleRow(
                title = "Burst mode",
                description =
                    "Tap shutter captures ${settings.burstShotCount} stills, ${settings.burstIntervalMs}ms apart. " +
                        "Long-press shutter captures continuously until release.",
                enabled = settings.burstModeEnabled,
                onChange = { onUpdate(settings.copy(burstModeEnabled = it)) },
            ),
        )
        Text("Burst count", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdvancedCaptureSettings.burstCountOptions.forEach { n ->
                val sel = settings.burstShotCount == n
                OutlinedButton(
                    onClick = { onUpdate(settings.copy(burstShotCount = n)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "$n",
                        color = if (sel) PnsColors.PhotoOrange else Color.White,
                    )
                }
            }
        }
        Text("Burst pace", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        val fleetPreset = AdvancedCaptureSettings.burstCadencePresets.first()
        val fleetFps = AdvancedCaptureSettings.burstCadenceFps(fleetPreset.intervalMs)
        Text(
            "Fleet max burst is fixed at ${fleetPreset.intervalMs}ms (~${String.format("%.1f", fleetFps)} fps target).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
        )
        Text("Burst quality", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BurstPhotoQualityProfile.entries.forEach { mode ->
                val sel = settings.burstPhotoQualityProfileEnum() == mode
                OutlinedButton(
                    onClick = { onUpdate(settings.copy(burstPhotoQualityProfile = mode.storageId)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        mode.label,
                        fontSize = 11.sp,
                        color = if (sel) PnsColors.PhotoOrange else Color.White,
                    )
                }
            }
        }
        Text(
            "NightScape frames (Night dial)",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdvancedCaptureSettings.nightScapeFrameCountOptions.forEach { n ->
                val sel = settings.nightScapeFrameCount == n
                OutlinedButton(
                    onClick = { onUpdate(settings.copy(nightScapeFrameCount = n)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "$n",
                        color = if (sel) PnsColors.PhotoOrange else Color.White,
                    )
                }
            }
        }
        Text("Intervalometer", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdvancedCaptureSettings.intervalometerSecOptions.forEach { sec ->
                val label = if (sec == 0) "Off" else "${sec}s"
                val sel = settings.intervalometerIntervalSec == sec
                OutlinedButton(
                    onClick = {
                        onUpdate(
                            settings.copy(
                                intervalometerIntervalSec = sec,
                                intervalometerRunning = if (sec == 0) false else settings.intervalometerRunning,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = if (sel) PnsColors.PhotoOrange else Color.White,
                    )
                }
            }
        }
        HudToggle(
            HudToggleRow(
                title = "Intervalometer running",
                description = "Timed stills while preview is open (photo mode, not recording).",
                enabled = settings.intervalometerRunning && settings.intervalometerIntervalSec > 0,
                onChange = { run ->
                    onUpdate(
                        settings.copy(
                            intervalometerRunning = run,
                        ),
                    )
                },
            ),
        )
        Text(
            "Time-lapse output",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeLapseMode.entries.forEach { mode ->
                val sel = settings.timeLapseModeEnum() == mode
                OutlinedButton(
                    onClick = { onUpdate(settings.copy(timeLapseMode = mode.storageId)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        mode.label,
                        fontSize = 11.sp,
                        color = if (sel) PnsColors.PhotoOrange else Color.White,
                    )
                }
            }
        }
        HudToggle(
            HudToggleRow(
                title = "Pre-capture buffer",
                description = "Keeps a ZSL RAW ring so the still can use the frame before shutter (pairs with ZSL mode).",
                enabled = settings.preCaptureBufferEnabled,
                onChange = { onUpdate(settings.copy(preCaptureBufferEnabled = it)) },
            ),
        )
    }
}

@Composable
private fun UxAppearanceSection(
    themeMode: PnsThemeMode,
    onThemeModeChange: (PnsThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewRailSectionTitle("Appearance (UX.1)")
        Text(
            "Theme follows system by default. Photo chrome stays dark charcoal in all modes.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PnsThemeMode.entries.forEach { mode ->
                FpsQuickChip(
                    label = mode.name,
                    selected = themeMode == mode,
                    requiresRoot = false,
                    onClick = { onThemeModeChange(mode) },
                    modifier = Modifier.weight(1f),
                    fillMaxTile = true,
                )
            }
        }
    }
}

@Composable
private fun CloudBackupHudSection(context: android.content.Context) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(CloudCaptureBackup.isEnabled(context)) }
    var wifiOnly by remember { mutableStateOf(CloudCaptureBackup.isWifiOnly(context)) }
    var folderUri by remember { mutableStateOf(CloudCaptureBackup.loadTreeUri(context)) }
    val folderLabel = folderUri?.lastPathSegment ?: "Not set"
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            CloudCaptureBackup.persistTreePermission(context, uri)
            folderUri = uri
            Toast.makeText(context, "Backup folder set", Toast.LENGTH_SHORT).show()
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewRailSectionTitle("Cloud backup (UX.3)")
        Text(
            "Copies new P&S captures to a folder you choose (Syncthing, Nextcloud, Drive, etc.). " +
                "Does not upload by itself — your sync app handles the cloud.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        HudToggle(
            HudToggleRow(
                title = "Backup captures",
                description = "After each still/video save, copy into $folderLabel/Point-and-Shoot/",
                enabled = enabled,
                onChange = { on ->
                    enabled = on
                    CloudCaptureBackup.setEnabled(context, on)
                },
            ),
        )
        HudToggle(
            HudToggleRow(
                title = "Wi‑Fi only",
                description = "Skip backup copies on mobile data (saves data; retry on next Wi‑Fi)",
                enabled = wifiOnly,
                onChange = { on ->
                    wifiOnly = on
                    CloudCaptureBackup.setWifiOnly(context, on)
                },
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FpsQuickChip(
                label = "Choose folder",
                selected = false,
                requiresRoot = false,
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.weight(1f),
                fillMaxTile = true,
            )
            FpsQuickChip(
                label = "Sync now",
                selected = false,
                requiresRoot = false,
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            CloudCaptureBackup.syncRecentCaptures(context, maxItems = 40)
                        }
                        Toast.makeText(context, "Backup sync finished", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                fillMaxTile = true,
                enabled = enabled && folderUri != null,
            )
        }
    }
}

@Composable
private fun WorkflowPresetsHudSection(
    context: android.content.Context,
    onApplyPreset: ((WorkflowPreset) -> Unit)?,
) {
    val presets = remember(context) { WorkflowPresets.all(context) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewRailSectionTitle("Workflow presets (UX.3)")
        Text(
            "One-tap dial + imaging folder + photo/video tray. ADB: pns_preview_workflow_preset=street",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { preset ->
                OutlinedButton(
                    onClick = { onApplyPreset?.invoke(preset) },
                    modifier = Modifier.weight(1f),
                    enabled = onApplyPreset != null,
                ) {
                    Text(preset.label, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ProFeaturesHudSection(
    settings: HudSettings,
    hudState: HudSettingsState,
    onImagingProfile: ((ImagingProfile) -> Unit)?,
) {
    val ctx = LocalContext.current
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val profile = ColorCalibrationTools.importProfileFromUri(ctx, uri)
            if (profile != null) {
                Toast.makeText(
                    ctx,
                    "Imported ${profile.targetId}",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(ctx, "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewRailSectionTitle("Pro capture (CC.3)")
        Text(
            "Named looks (LUT + ISP). Chart calibration uses Calibrate screen; export/import profiles here.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text("Picture profile", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProPictureProfiles.presets.forEach { profile ->
                val sel = settings.selectedPictureProfileId == profile.id
                OutlinedButton(
                    onClick = {
                        ProCapture.applyPictureProfile(
                            ctx,
                            profile,
                            hudState,
                            onImagingProfile = onImagingProfile,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        profile.label,
                        fontSize = 10.sp,
                        color = if (sel) PnsColors.PhotoOrange else Color.White,
                    )
                }
            }
        }
        HudToggle(
            HudToggleRow(
                title = "Tethered capture (HTTP)",
                description =
                    "127.0.0.1:${TetheredCaptureServer.DEFAULT_PORT} — " +
                    "adb reverse tcp:${TetheredCaptureServer.DEFAULT_PORT} tcp:${TetheredCaptureServer.DEFAULT_PORT}",
                enabled = settings.tetheredCaptureEnabled,
                onChange = { on ->
                    val next = settings.copy(tetheredCaptureEnabled = on)
                    hudState.update(next)
                    HudSettings.save(ctx.applicationContext, next)
                },
            ),
        )
        val wifiDirectPermLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                val ok = WifiDirectTetherSupport.requiredPermissions().all { grants[it] == true }
                if (!ok) {
                    Toast.makeText(ctx, "Wi‑Fi tether needs location + nearby Wi‑Fi permissions", Toast.LENGTH_LONG).show()
                }
            }
        HudToggle(
            HudToggleRow(
                title = "Wi‑Fi Direct tether (LAN)",
                description =
                    "HTTP on 0.0.0.0:${TetheredCaptureServer.DEFAULT_PORT} + mDNS ${WifiDirectTetherSupport.NSD_SERVICE_TYPE} " +
                        "(same API as loopback tether). Requires location + nearby Wi‑Fi.",
                enabled = settings.wifiDirectTetherEnabled,
                onChange = { on ->
                    if (on) {
                        val missing = WifiDirectTetherSupport.missingPermissions(ctx)
                        if (missing.isNotEmpty()) {
                            wifiDirectPermLauncher.launch(missing.toTypedArray())
                        }
                        val next =
                            settings.copy(
                                wifiDirectTetherEnabled = true,
                                tetheredCaptureEnabled = true,
                            )
                        hudState.update(next)
                        HudSettings.save(ctx.applicationContext, next)
                    } else {
                        val next = settings.copy(wifiDirectTetherEnabled = false)
                        hudState.update(next)
                        HudSettings.save(ctx.applicationContext, next)
                    }
                },
            ),
        )
        var lanTransfer by remember { mutableStateOf(PnsConnectivity.isLanTransferEnabled(ctx)) }
        HudToggle(
            HudToggleRow(
                title = "LAN media transfer",
                description =
                    "Wi‑Fi HTTP :${LanMediaTransferServer.DEFAULT_PORT} — GET /files, /file?id= (desktop pull)",
                enabled = lanTransfer,
                onChange = { on ->
                    lanTransfer = on
                    PnsConnectivity.setLanTransferEnabled(ctx, on)
                },
            ),
        )
        Text("Flash strength", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 50, 75, 100).forEach { pct ->
                val sel = settings.previewFlashStrengthPercent == pct
                OutlinedButton(
                    onClick = {
                        val next = settings.copy(previewFlashStrengthPercent = pct)
                        hudState.update(next)
                        HudSettings.save(ctx.applicationContext, next)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "$pct%",
                        color = if (sel) PnsColors.PhotoOrange else Color.White,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val r = ColorCalibrationTools.exportLatestProfile(ctx)
                    if (r != null) {
                        Toast.makeText(
                            ctx,
                            "Exported ${r.file.name}",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        Toast.makeText(ctx, "No saved profile to export", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Export cal JSON")
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Import cal JSON")
            }
        }
    }
}

@Composable
private fun ExperimentalUnlockHudSection(
    settings: HudSettings,
    onUpdate: (HudSettings) -> Unit,
) {
    val ctx = LocalContext.current.applicationContext
    val rootGranted = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        rootGranted.value = RootCapabilityStore.loadOrUnknown(ctx).grantsPrivileged
    }
    val safeModeActive = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        safeModeActive.value = ExperimentalSafeModeStore.isSafeModeActive(ctx)
    }
    var confirmMasterEnable by remember { mutableStateOf(false) }
    var confirmUnlockEnable by remember { mutableStateOf(false) }

    val masterEnabled = settings.enableExperimentalAppBreakingFeatures && !safeModeActive.value
    val unlockEnabled = masterEnabled && settings.enableExperimentalMaxResolutionUnlock && rootGranted.value
    val vendorSessionEnabled = masterEnabled && settings.enableExperimentalVendorSessionKeys && rootGranted.value

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewRailSectionTitle("Experimental unlock lane")
        Text(
            text =
                "App-breaking experiments for max-resolution unlock research. These toggles are root-only, may crash " +
                    "preview/session create, and are automatically disabled by Safe Mode on crash loops.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        if (safeModeActive.value) {
            Text(
                text = "Safe mode is active: experimental features are forced off until manually cleared.",
                style = MaterialTheme.typography.bodySmall,
                color = PnsColors.RecordRed,
            )
        }
        HudToggle(
            HudToggleRow(
                title = "Experimental app-breaking features",
                description =
                    "Master gate for high-risk capture experiments. Turning this off hard-disables all unlock lanes.",
                enabled = masterEnabled,
                onChange = { on ->
                    if (safeModeActive.value) return@HudToggleRow
                    if (on) {
                        confirmMasterEnable = true
                    } else {
                        onUpdate(
                            settings.copy(
                                enableExperimentalAppBreakingFeatures = false,
                                enableExperimentalMaxResolutionUnlock = false,
                                enableExperimentalVendorSessionKeys = false,
                            ),
                        )
                    }
                },
            ),
        )
        HudToggle(
            HudToggleRow(
                title = "Experimental max resolution unlock (CPH2583 lane)",
                description =
                    if (!rootGranted.value) {
                        "Requires root grant in Root Only settings. Writes persist.vendor.camera.preview.size and verifies fail-closed fallback."
                    } else {
                        "Root lane for CPH2583: applies preview-size override before session create, verifies observed value, and falls back on mismatch."
                    },
                enabled = unlockEnabled,
                onChange = { on ->
                    if (!masterEnabled || !rootGranted.value || safeModeActive.value) return@HudToggleRow
                    if (on) {
                        confirmUnlockEnable = true
                    } else {
                        onUpdate(settings.copy(enableExperimentalMaxResolutionUnlock = false))
                    }
                },
            ),
        )
        HudToggle(
            HudToggleRow(
                title = "Experimental vendor session key lane",
                description =
                    "Independent session-parameter experiment (XCFA/vendor toggles). Kept separate from property override and fails closed.",
                enabled = vendorSessionEnabled,
                onChange = { on ->
                    if (!masterEnabled || !rootGranted.value || safeModeActive.value) return@HudToggleRow
                    onUpdate(settings.copy(enableExperimentalVendorSessionKeys = on))
                },
            ),
        )
    }

    if (confirmMasterEnable) {
        AlertDialog(
            onDismissRequest = { confirmMasterEnable = false },
            title = { Text("Enable experimental app-breaking features?") },
            text = {
                Text(
                    "This can destabilize camera startup or capture sessions. If repeated crashes occur, Safe Mode will auto-disable these toggles.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate(settings.copy(enableExperimentalAppBreakingFeatures = true))
                        confirmMasterEnable = false
                    },
                ) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { confirmMasterEnable = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmUnlockEnable) {
        AlertDialog(
            onDismissRequest = { confirmUnlockEnable = false },
            title = { Text("Enable max-res unlock lane?") },
            text = {
                Text(
                    "This lane is root-only and intentionally hacky. It may not unlock higher resolutions on your HAL and can force Safe Mode fallback.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate(settings.copy(enableExperimentalMaxResolutionUnlock = true))
                        confirmUnlockEnable = false
                    },
                ) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnlockEnable = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HudStillCaptureModeRow(
    mode: StillCaptureMode,
    onModeChange: (StillCaptureMode) -> Unit,
) {
    val cycle: () -> Unit = {
        val next =
            when (mode) {
                StillCaptureMode.Standard -> StillCaptureMode.ZslStill
                StillCaptureMode.ZslStill -> StillCaptureMode.HdrStill
                StillCaptureMode.HdrStill -> StillCaptureMode.Standard
            }
        onModeChange(next)
    }
    val effective = StillCaptureModePolicy.effectiveForCapture(mode)
    val scaffoldNote =
        if (mode != effective) {
            " (ships as ${effective.name} until 13.8b/c)"
        } else {
            ""
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Still capture mode (M13.8)",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        Text(
            "Standard = ReferenceCam DNG. ZSL = ring buffer (13.8b). HDR = 3-shot EV bracket burst (13.8c).$scaffoldNote",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
        )
        OutlinedButton(onClick = cycle, modifier = Modifier.fillMaxWidth()) {
            Text("Mode: ${mode.name}$scaffoldNote", color = Color.White)
        }
    }
}

@Composable
private fun HudToggle(row: HudToggleRow, highlightFlash: SettingHighlightFlashState? = null) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(highlightFlash?.applyHighlight(Modifier, row.settingKey) ?: Modifier),
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
