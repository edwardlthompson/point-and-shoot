package dev.pointandshoot

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Composite preview of the Pro HUD per BUILD_PLAN §5 (Phase 2).
 *
 * Wires together [CommandDial], [TimecodeOverlay], [VideoTallyOverlay], the
 * mocked exposure / FPS readouts, and the [HudSettings] toggles so we can
 * verify HUD layout end-to-end without the live capture engine. The "preview"
 * area is a simple gradient placeholder until Phase 1 lands.
 *
 * This screen is a host-side smoke harness for the HUD - it intentionally
 * carries no Camera2 state.
 */
@Composable
fun ProHudScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state = rememberHudSettings()
    val settings = state.current

    var dialMode by remember {
        mutableStateOf(HudSettings.loadCommandDialMode(context))
    }
    var isRecording by remember { mutableStateOf(false) }
    var recordStartMs by remember { mutableStateOf<Long?>(null) }
    var imagingProfile by remember { mutableStateOf<ImagingProfile>(ImagingProfile.StandardPro) }

    val insets = rememberSystemInsetsDp()
    val padding: PaddingValues = insets.asPaddingValues(extra = 12.dp)

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        // Mock preview surface (gradient stand-in for the live preview).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF101112), PnsColors.Charcoal, Color(0xFF000000)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "PRO HUD HOST PREVIEW\n(camera surface placeholder)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.30f),
                textAlign = TextAlign.Center,
            )
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
            // Top row: back + timecode + readouts.
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
                        ReadoutChip(
                            text = buildString {
                                if (settings.showFpsReadout) append("FPS 30  ")
                                if (settings.showIsoShutterReadout) append("ISO 400  1/250")
                            }.trim(),
                        )
                    }
                    Text(
                        text = "profile  ${imagingProfile.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Bottom row: command dial + record / profile toggles.
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
                    RecordButton(
                        isRecording = isRecording,
                        onToggle = {
                            isRecording = !isRecording
                            recordStartMs = if (isRecording) SystemClock.elapsedRealtime() else null
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            imagingProfile = if (imagingProfile == ImagingProfile.StandardPro) {
                                ImagingProfile.UltraMax
                            } else {
                                ImagingProfile.StandardPro
                            }
                        },
                    ) {
                        Text(
                            text = if (imagingProfile == ImagingProfile.StandardPro) "-> Ultra-Max" else "-> Standard Pro",
                        )
                    }
                    Spacer16()
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

@Composable
private fun ReadoutChip(text: String) {
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

@Composable
private fun RecordButton(isRecording: Boolean, onToggle: () -> Unit) {
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
    androidx.compose.foundation.layout.Spacer(Modifier.padding(2.dp))
    OutlinedButton(onClick = onToggle) {
        Text(if (isRecording) "Recording (mock)" else "Start mock record")
    }
}

@Composable
private fun Spacer16() {
    Box(modifier = Modifier.height(0.dp))
}
