package dev.pointandshoot

import android.util.Log
import dev.pointandshoot.fleet.FleetUiVisibilityGate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ShutterModeRailSection(
    chromePrefs: PreviewChromePreferencesState,
    hudState: HudSettingsState,
) {
    val chrome = chromePrefs.current
    val hud = hudState.current
    val mode = ShutterCaptureMode.current(chrome, hud)
    PreviewRailSectionTitle("Shutter behavior")
    Text(
        "Single fires immediately. Timer counts down before capture. Burst saves multiple stills per press.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.62f),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FocusPeakingOptionRow(
            label = "Single",
            selected = mode == ShutterCaptureMode.Single,
            swatchColor = null,
            onClick = {
                applyShutterCaptureMode(ShutterCaptureMode.Single, chromePrefs, hudState)
                Log.i("PNS.ChromeUx", "shutterMode=Single")
            },
        )
        FocusPeakingOptionRow(
            label = "Burst",
            selected = mode == ShutterCaptureMode.Burst,
            swatchColor = null,
            onClick = {
                applyShutterCaptureMode(ShutterCaptureMode.Burst, chromePrefs, hudState)
                Log.i("PNS.ChromeUx", "shutterMode=Burst")
            },
        )
    }
    Text(
        "Burst cadence (photo long-press / burst mode)",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.72f),
    )
    val burstFileType = normalizeBurstFileTypeProfile(hud.burstPhotoQualityProfileEnum())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FocusPeakingOptionRow(
            label = "RAW burst",
            selected = burstFileType == BurstPhotoQualityProfile.RawOnly,
            swatchColor = null,
            onClick = {
                applyBurstFileTypeProfile(BurstPhotoQualityProfile.RawOnly, chromePrefs, hudState)
                Log.i("PNS.ChromeUx", "burstFileType=raw_only")
            },
        )
        FocusPeakingOptionRow(
            label = "JPEG burst",
            selected = burstFileType == BurstPhotoQualityProfile.ProcessedOnly,
            swatchColor = null,
            onClick = {
                applyBurstFileTypeProfile(BurstPhotoQualityProfile.ProcessedOnly, chromePrefs, hudState)
                Log.i("PNS.ChromeUx", "burstFileType=jpeg_only")
            },
        )
    }
    val fleetPreset = AdvancedCaptureSettings.burstCadencePresets.first()
    val fleetFps = AdvancedCaptureSettings.burstCadenceFps(fleetPreset.intervalMs)
    Text(
        "Burst speed: Fleet Max (${String.format("%.1f", fleetFps)} fps target)",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.72f),
    )
    Text(
        "Self-timer (photo mode)",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.72f),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (sec in PreviewChromePreferences.SELF_TIMER_DELAY_SEC_OPTIONS) {
            val label = if (sec == 0) "Off" else "${sec}s"
            val selected = mode == ShutterCaptureMode.Timer && chrome.selfTimerDelaySec == sec ||
                (sec == 0 && mode == ShutterCaptureMode.Single)
            FocusPeakingOptionRow(
                label = label,
                selected = selected,
                swatchColor = null,
                onClick = {
                    if (sec == 0) {
                        applyShutterCaptureMode(ShutterCaptureMode.Single, chromePrefs, hudState)
                        Log.i("PNS.ChromeUx", "shutterMode=Single selfTimerSec=0")
                    } else {
                        applyShutterCaptureMode(
                            ShutterCaptureMode.Timer,
                            chromePrefs,
                            hudState,
                            timerSec = sec,
                        )
                        Log.i("PNS.ChromeUx", "shutterMode=Timer selfTimerSec=$sec")
                    }
                },
            )
        }
    }
}

@Composable
fun QuickSettingsRailSheetContent(
    chromePrefs: PreviewChromePreferencesState,
    hudState: HudSettingsState,
    fineLocationGranted: Boolean,
    onPendingEnableGeotagChange: (Boolean) -> Unit,
    onRequestLocationForGeotag: () -> Unit,
    onKickPreviewPipeline: () -> Unit,
    visibilityCtx: FleetUiVisibilityGate.VisibilityContext,
    highlightFlash: SettingHighlightFlashState? = null,
) {
    val chrome = chromePrefs.current
    val hud = hudState.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChromeSettingsIntroText(
            "Same toggles as the 7×3 quick grid. Use Settings search to jump here.",
        )
        ShutterModeRailSection(chromePrefs = chromePrefs, hudState = hudState)
        PreviewRailSectionTitle("Overlays")
        if (FleetUiVisibilityGate.visible("hud.histogram", visibilityCtx)) {
        PreviewRailSettingToggle(
            title = "Histogram",
            subtitle = null,
            checked = hud.showHistogram,
            onCheckedChange = { on ->
                hudState.update(hud.copy(showHistogram = on))
            },
            settingKey = "hud.histogram",
            highlightFlash = highlightFlash,
        )
        }
        PreviewRailSettingToggle(
            title = "Horizon level",
            subtitle = null,
            checked = hud.showHorizonLevel,
            onCheckedChange = { on ->
                hudState.update(hud.copy(showHorizonLevel = on))
            },
            settingKey = "hud.horizon",
            highlightFlash = highlightFlash,
        )
        if (FleetUiVisibilityGate.visible("face.eye_af", visibilityCtx)) {
            PreviewRailSettingToggle(
                title = "Eye-AF overlay",
                subtitle = null,
                checked = hud.showEyeAfOverlay,
                onCheckedChange = { on ->
                    hudState.update(hud.copy(showEyeAfOverlay = on))
                },
                settingKey = "hud.eye_af",
                highlightFlash = highlightFlash,
            )
        }
        PreviewRailSettingToggle(
            title = "Video tally",
            subtitle = null,
            checked = hud.showVideoTally,
            onCheckedChange = { on ->
                hudState.update(hud.copy(showVideoTally = on))
            },
            settingKey = "hud.video_tally",
            highlightFlash = highlightFlash,
        )
        PreviewRailSectionTitle("Preview & capture")
        PreviewRailSettingToggle(
            title = "Max brightness in preview",
            subtitle = null,
            checked = chrome.maxBrightnessInPreview,
            onCheckedChange = { on ->
                chromePrefs.updateMutate { it.copy(maxBrightnessInPreview = on) }
            },
        )
        PreviewRailSettingToggle(
            title = "Silence notifications (preview open)",
            subtitle = null,
            checked = chrome.dndWhileInPreview,
            onCheckedChange = { on ->
                chromePrefs.updateMutate { it.copy(dndWhileInPreview = on) }
            },
        )
        PreviewRailSettingToggle(
            title = "Tap preview to capture",
            subtitle = null,
            checked = chrome.tapPreviewToCapture,
            onCheckedChange = { on ->
                chromePrefs.updateMutate { it.copy(tapPreviewToCapture = on) }
            },
        )
        PreviewRailSettingToggle(
            title = "Volume keys shutter",
            subtitle = null,
            checked = chrome.volumeKeysCapture,
            onCheckedChange = { on ->
                chromePrefs.updateMutate { it.copy(volumeKeysCapture = on) }
            },
        )
        PreviewRailSettingToggle(
            title = "Save location with media",
            subtitle = null,
            checked = chrome.saveLocationWithMedia && fineLocationGranted,
            onCheckedChange = { on ->
                val c = chromePrefs.current
                if (on && !fineLocationGranted) {
                    onPendingEnableGeotagChange(true)
                    onRequestLocationForGeotag()
                } else if (on) {
                    chromePrefs.update(c.copy(saveLocationWithMedia = true))
                } else {
                    chromePrefs.update(c.copy(saveLocationWithMedia = false))
                    onPendingEnableGeotagChange(false)
                }
            },
        )
        PreviewRailSectionTitle("Stabilization")
        if (FleetUiVisibilityGate.visible("lens.ois", visibilityCtx)) {
        PreviewRailSettingToggle(
            title = "Optical stabilization (OIS)",
            subtitle = null,
            checked = hud.enableLensOpticalStabilization,
            onCheckedChange = { on ->
                hudState.update(hud.copy(enableLensOpticalStabilization = on))
                onKickPreviewPipeline()
            },
            settingKey = "video.ois",
            highlightFlash = highlightFlash,
        )
        }
        if (FleetUiVisibilityGate.visible("lens.eis", visibilityCtx)) {
        PreviewRailSettingToggle(
            title = "Electronic stabilization (EIS)",
            subtitle = null,
            checked = hud.enableVideoStabilizationPreview,
            onCheckedChange = { on ->
                hudState.update(hud.copy(enableVideoStabilizationPreview = on))
                onKickPreviewPipeline()
            },
            settingKey = "video.eis",
            highlightFlash = highlightFlash,
        )
        }
        PreviewRailSectionTitle("Flash (rear)")
        Text(
            "Mode: ${chrome.previewFlashMode.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.88f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (m in PreviewFlashMode.entries) {
                val sel = chrome.previewFlashMode == m
                FocusPeakingOptionRow(
                    label =
                        when (m) {
                            PreviewFlashMode.Off -> "Off"
                            PreviewFlashMode.Auto -> "Auto"
                            PreviewFlashMode.On -> "On"
                            PreviewFlashMode.Torch -> "Torch"
                        },
                    selected = sel,
                    swatchColor = null,
                    onClick = {
                        chromePrefs.updateMutate { it.copy(previewFlashMode = m) }
                        Log.i("PNS.ChromeUx", "flashMode=${m.name}")
                    },
                )
            }
        }
    }
}
