package dev.pointandshoot.preview.mock

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pointandshoot.CommandDial
import dev.pointandshoot.HudSettings
import dev.pointandshoot.ImagingProfile
import dev.pointandshoot.LutChipRow
import dev.pointandshoot.PnsAdbLog
import dev.pointandshoot.PnsColors
import dev.pointandshoot.TimecodeOverlay
import dev.pointandshoot.VideoTallyOverlay
import dev.pointandshoot.asPaddingValues
import dev.pointandshoot.previewStillModeShortLabel
import dev.pointandshoot.rememberHudSettings
import dev.pointandshoot.rememberSystemInsetsDp

/**
 * T.14 unified mock/demo preview: GLES [TestPattern] + Pro HUD chrome without Camera2.
 *
 * Does not fork gallery-return resume policy (ADR-0008) — no live session, no tray viewer path.
 */
@Suppress("FunctionNaming")
@Composable
fun UnifiedMockPreviewScreen(
    onBack: () -> Unit,
    launchRoute: String = MockPreviewScreens.ROUTE_MOCK,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = rememberHudSettings()
    val settings = state.current
    val activeLut = settings.stillsLut()

    var dialMode by remember {
        mutableStateOf(HudSettings.loadCommandDialMode(context))
    }
    var isRecording by remember { mutableStateOf(false) }
    var recordStartMs by remember { mutableStateOf<Long?>(null) }
    var imagingProfile by remember {
        mutableStateOf(HudSettings.loadImagingProfile(context))
    }

    LaunchedEffect(launchRoute, activeLut) {
        val route = MockPreviewScreens.normalizeRoute(launchRoute)
        PnsAdbLog.i(context, "mock preview screen compose route=$route lut=${activeLut.name}")
        if (route == MockPreviewScreens.ROUTE_GLPREVIEW || route == MockPreviewScreens.ROUTE_MOCK) {
            PnsAdbLog.i(context, "glpreview screen compose active lut=${activeLut.name}")
        }
    }

    val insets = rememberSystemInsetsDp()
    val padding: PaddingValues = insets.asPaddingValues(extra = 12.dp)

    Box(modifier = modifier.fillMaxSize().padding(padding)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            MockPreviewGlViewport(activeLut = activeLut.load())
        }

        if (settings.showVideoTally && isRecording) {
            VideoTallyOverlay(visible = true)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Column(horizontalAlignment = Alignment.End) {
                    if (settings.showTimecode) {
                        TimecodeOverlay(
                            isRecording = isRecording,
                            fps = 30,
                            startedElapsedMs = recordStartMs,
                        )
                    }
                    if (settings.showFpsReadout || settings.showIsoShutterReadout) {
                        MockReadoutChip(
                            text = buildString {
                                if (settings.showFpsReadout) append("FPS 30  ")
                                if (settings.showIsoShutterReadout) append("ISO 400  1/250")
                            }.trim(),
                        )
                    }
                    Text(
                        text = "mock  ${imagingProfile.previewStillModeShortLabel}  LUT ${activeLut.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (settings.showCommandDial) {
                    CommandDial(
                        selected = dialMode,
                        onSelect = {
                            dialMode = it
                            HudSettings.saveCommandDialMode(context, it)
                        },
                        modifier = Modifier.wrapContentSize(),
                    )
                }
                LutChipRow(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MockRecordButton(
                        isRecording = isRecording,
                        onToggle = {
                            isRecording = !isRecording
                            recordStartMs = if (isRecording) SystemClock.elapsedRealtime() else null
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            val next =
                                when (imagingProfile) {
                                    ImagingProfile.StandardPro -> ImagingProfile.UltraMax
                                    ImagingProfile.UltraMax -> ImagingProfile.JpegOnly
                                    ImagingProfile.JpegOnly -> ImagingProfile.StandardPro
                                }
                            imagingProfile = next
                            HudSettings.saveImagingProfile(context, next)
                        },
                    ) {
                        Text(
                            text =
                                when (imagingProfile) {
                                    ImagingProfile.StandardPro -> "-> Ultra-Max"
                                    ImagingProfile.UltraMax -> "-> JPEG only"
                                    ImagingProfile.JpegOnly -> "-> Standard Pro"
                                },
                        )
                    }
                    Text(
                        text = "mode  ${dialMode.label}  ${dialMode.description}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun MockReadoutChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun MockRecordButton(isRecording: Boolean, onToggle: () -> Unit) {
    val bg = if (isRecording) PnsColors.RecordRed else Color.White.copy(alpha = 0.10f)
    val fg = if (isRecording) Color.White else PnsColors.RecordRed
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isRecording) "STOP" else "REC",
            style = MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
    OutlinedButton(onClick = onToggle) {
        Text(if (isRecording) "Recording (mock)" else "Start mock record")
    }
}
