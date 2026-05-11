package dev.pointandshoot

import android.hardware.camera2.CaptureResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Shared formatting for on-screen readout + ChromeUx logs (Milestone 9). */
object PreviewReadoutFormat {
    fun formatShutter(exposureNs: Long?): String {
        if (exposureNs == null || exposureNs <= 0L) return "—"
        val sec = exposureNs / 1_000_000_000.0
        if (sec >= 1.0) return "%.1fs".format(sec)
        val inv = (1_000_000_000.0 / exposureNs.toDouble()).roundToInt().coerceAtLeast(1)
        return "1/$inv"
    }

    fun awbModeLabel(mode: Int?): String {
        if (mode == null) return "—"
        return when (mode) {
            CaptureResult.CONTROL_AWB_MODE_OFF -> "OFF"
            CaptureResult.CONTROL_AWB_MODE_AUTO -> "AWB"
            CaptureResult.CONTROL_AWB_MODE_INCANDESCENT -> "INC"
            CaptureResult.CONTROL_AWB_MODE_FLUORESCENT -> "FL"
            CaptureResult.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "WFL"
            CaptureResult.CONTROL_AWB_MODE_DAYLIGHT -> "DY"
            CaptureResult.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "CLD"
            CaptureResult.CONTROL_AWB_MODE_TWILIGHT -> "TWI"
            CaptureResult.CONTROL_AWB_MODE_SHADE -> "SHD"
            else -> "?$mode"
        }
    }
}

/**
 * Exposure readout above the chrome rails: tappable chips open popups (ISO / Ss / WB / FPS /
 * Still LUT / Video LUT / RAW pipeline).
 * The strip stays screen-aligned — it does **not** counter-rotate with device/chrome twist.
 */
@Composable
fun PreviewReadoutStrip(
    iso: Int?,
    exposureNs: Long?,
    awbMode: Int?,
    measuredFps: Double,
    stillCaptureJpegCompanion: Boolean,
    menu: ReadoutMenuSnapshot,
    fpsOptions: List<PreviewFpsSupport.QuickFpsOption>,
    onPickIso: (Int?) -> Unit,
    onPickShutter: (Long?) -> Unit,
    onPickAwb: (Int?) -> Unit,
    onPickFps: (Int) -> Unit,
    stillLut: LutCatalog,
    videoLut: LutCatalog,
    onPickStillLut: (LutCatalog) -> Unit,
    onPickVideoLut: (LutCatalog) -> Unit,
    onPickStillPipeline: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isoText = iso?.toString() ?: "—"
    val ss = PreviewReadoutFormat.formatShutter(exposureNs)
    val awb = PreviewReadoutFormat.awbModeLabel(awbMode)
    val fpsText =
        if (measuredFps > 0.05) {
            "%.1f".format(measuredFps)
        } else {
            "—"
        }
    var isoMenu by remember { mutableStateOf(false) }
    var ssMenu by remember { mutableStateOf(false) }
    var awbMenu by remember { mutableStateOf(false) }
    var fpsMenu by remember { mutableStateOf(false) }
    var stillLutMenu by remember { mutableStateOf(false) }
    var videoLutMenu by remember { mutableStateOf(false) }
    var rawMenu by remember { mutableStateOf(false) }
    val stillLutChoices = remember { LutCatalog.forScope(LutCatalog.Scope.Stills) }
    val videoLutChoices = remember { LutCatalog.forScope(LutCatalog.Scope.Video) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PreviewReadoutStripHeight)
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f, fill = true)
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                ReadoutMetricChip(
                    label = "ISO",
                    value = isoText,
                    onClick = { isoMenu = true },
                    accessibilityLabel = "ISO. Current $isoText. Opens ISO menu.",
                )
                DropdownMenu(expanded = isoMenu, onDismissRequest = { isoMenu = false }) {
                    for (choice in menu.isoChoices) {
                        val label = if (choice == null) "Auto" else choice.toString()
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onPickIso(choice)
                                isoMenu = false
                            },
                        )
                    }
                }
            }
            Box {
                ReadoutMetricChip(
                    label = "Ss",
                    value = ss,
                    onClick = { ssMenu = true },
                    accessibilityLabel = "Shutter speed. Current $ss. Opens shutter menu.",
                )
                DropdownMenu(expanded = ssMenu, onDismissRequest = { ssMenu = false }) {
                    for (choice in menu.exposureChoices) {
                        val label =
                            if (choice == null) {
                                "Auto"
                            } else {
                                PreviewReadoutFormat.formatShutter(choice)
                            }
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onPickShutter(choice)
                                ssMenu = false
                            },
                        )
                    }
                }
            }
            Box {
                ReadoutMetricChip(
                    label = "WB",
                    value = awb,
                    onClick = { awbMenu = true },
                    accessibilityLabel = "White balance. Current $awb. Opens WB menu.",
                )
                DropdownMenu(expanded = awbMenu, onDismissRequest = { awbMenu = false }) {
                    for (choice in menu.awbChoices) {
                        val label =
                            when (choice) {
                                null -> "Default (program)"
                                else -> PreviewReadoutFormat.awbModeLabel(choice)
                            }
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onPickAwb(choice)
                                awbMenu = false
                            },
                        )
                    }
                }
            }
            Box {
                ReadoutMetricChip(
                    label = "FPS",
                    value = "${fpsText}fps",
                    onClick = { fpsMenu = true },
                    accessibilityLabel = "Target FPS. Current ${fpsText}fps. Opens FPS menu.",
                )
                DropdownMenu(expanded = fpsMenu, onDismissRequest = { fpsMenu = false }) {
                    for (opt in fpsOptions) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    buildString {
                                        append(opt.targetFps)
                                        append(" fps")
                                        if (opt.requiresRoot) append(" (root)")
                                    },
                                )
                            },
                            onClick = {
                                onPickFps(opt.targetFps)
                                fpsMenu = false
                            },
                        )
                    }
                }
            }
            Box {
                ReadoutLutChip(
                    label = "Still",
                    scope = LutCatalog.Scope.Stills,
                    current = stillLut,
                    onClick = { stillLutMenu = true },
                    accessibilityLabel = "Still capture LUT. Index ${stillLut.indexInScope(LutCatalog.Scope.Stills)} (${stillLut.displayName}).",
                )
                DropdownMenu(expanded = stillLutMenu, onDismissRequest = { stillLutMenu = false }) {
                    for (entry in stillLutChoices) {
                        DropdownMenuItem(
                            text = { Text(entry.displayName) },
                            onClick = {
                                onPickStillLut(entry)
                                stillLutMenu = false
                            },
                        )
                    }
                }
            }
            Box {
                ReadoutLutChip(
                    label = "Video",
                    scope = LutCatalog.Scope.Video,
                    current = videoLut,
                    onClick = { videoLutMenu = true },
                    accessibilityLabel = "Video LUT. Index ${videoLut.indexInScope(LutCatalog.Scope.Video)} (${videoLut.displayName}).",
                )
                DropdownMenu(expanded = videoLutMenu, onDismissRequest = { videoLutMenu = false }) {
                    for (entry in videoLutChoices) {
                        DropdownMenuItem(
                            text = { Text(entry.displayName) },
                            onClick = {
                                onPickVideoLut(entry)
                                videoLutMenu = false
                            },
                        )
                    }
                }
            }
        }
        Box {
            RawStillPipelineChip(
                jpegCompanion = stillCaptureJpegCompanion,
                onOpenMenu = { rawMenu = true },
            )
            DropdownMenu(expanded = rawMenu, onDismissRequest = { rawMenu = false }) {
                DropdownMenuItem(
                    text = { Text("RAW (DNG only)") },
                    onClick = {
                        onPickStillPipeline(false)
                        rawMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("RAW+ (DNG + JPEG)") },
                    onClick = {
                        onPickStillPipeline(true)
                        rawMenu = false
                    },
                )
            }
        }
    }
}

val PreviewReadoutStripHeight = 40.dp

@Composable
private fun ReadoutLutChip(
    label: String,
    scope: LutCatalog.Scope,
    current: LutCatalog,
    onClick: () -> Unit,
    accessibilityLabel: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .semantics { contentDescription = accessibilityLabel }
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ).background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
        )
        Text(
            text = current.indexInScope(scope).toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = PnsColors.PhotoOrange.copy(alpha = 0.98f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReadoutMetricChip(
    label: String,
    value: String,
    onClick: () -> Unit,
    accessibilityLabel: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .semantics { contentDescription = accessibilityLabel }
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ).background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 1,
        )
    }
}

@Composable
private fun RawStillPipelineChip(
    jpegCompanion: Boolean,
    onOpenMenu: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(6.dp)
    val label = if (jpegCompanion) "RAW+" else "RAW"
    Row(
        modifier =
            Modifier
                .semantics {
                    contentDescription =
                        "Still capture pipeline. Current $label. Opens RAW or RAW plus JPEG menu."
                }
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.38f), shape)
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onOpenMenu,
                ).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 1,
        )
    }
}
