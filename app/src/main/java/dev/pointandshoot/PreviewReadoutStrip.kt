package dev.pointandshoot

import android.hardware.camera2.CaptureResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private fun maxReadoutValueWidthDp(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    strings: Collection<String>,
    density: androidx.compose.ui.unit.Density,
): Dp {
    var maxPx = 0
    for (s in strings) {
        val w =
            textMeasurer.measure(
                text = AnnotatedString(s),
                style = style,
                overflow = TextOverflow.Visible,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = Int.MAX_VALUE),
            ).size.width
        maxPx = maxOf(maxPx, w)
    }
    return with(density) { maxPx.toDp() }
}

private val ReadoutChipGap = 6.dp

private const val ReadoutMinFontScale = 0.56f

private fun TextStyle.scaledFont(scale: Float): TextStyle {
    val sz = fontSize
    if (sz == TextUnit.Unspecified) return this
    return copy(fontSize = (sz.value * scale).sp)
}

private fun chipOuterWidthPx(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    label: String,
    labelStyle: TextStyle,
    valueMinWidthDp: Dp,
): Int {
    val labelW = textMeasurer.measure(AnnotatedString(label), labelStyle).size.width
    val vminPx = with(density) { valueMinWidthDp.roundToPx() }
    return max(labelW, vminPx) + with(density) { 16.dp.roundToPx() }
}

private fun isoCandidateStrings(menu: ReadoutMenuSnapshot): Set<String> =
    buildSet {
        add("—")
        add("Auto")
        add("102400")
        menu.isoChoices.forEach { add(if (it == null) "Auto" else it.toString()) }
    }

private fun ssCandidateStrings(menu: ReadoutMenuSnapshot): Set<String> =
    buildSet {
        add("—")
        add("Auto")
        add("30.0s")
        add("1/8000")
        menu.exposureChoices.forEach {
            add(if (it == null) "Auto" else PreviewReadoutFormat.formatShutter(it))
        }
    }

private fun wbCandidateStrings(menu: ReadoutMenuSnapshot): Set<String> =
    buildSet {
        add("—")
        add("AWB")
        add("?99999")
        menu.awbChoices.forEach {
            add(if (it == null) "AWB" else PreviewReadoutFormat.awbModeLabel(it))
        }
    }

private fun computeReadoutFontScale(
    maxWidthPx: Int,
    density: androidx.compose.ui.unit.Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    baseLabelTypography: TextStyle,
    menu: ReadoutMenuSnapshot,
    stillLutIndex: String,
    videoLutIndex: String,
    jpegCompanion: Boolean,
): Float {
    if (maxWidthPx <= 0) return 1f

    fun rowWidthPx(scale: Float): Int {
        val ls = baseLabelTypography.scaledFont(scale)
        val vs = ls.copy(fontFamily = FontFamily.Monospace)
        val isoMin = maxReadoutValueWidthDp(textMeasurer, vs, isoCandidateStrings(menu), density)
        val ssMin = maxReadoutValueWidthDp(textMeasurer, vs, ssCandidateStrings(menu), density)
        val wbMin = maxReadoutValueWidthDp(textMeasurer, vs, wbCandidateStrings(menu), density)
        val fpsMin = maxReadoutValueWidthDp(textMeasurer, vs, listOf("9999fps", "—fps"), density)
        val lutStillMin =
            maxReadoutValueWidthDp(textMeasurer, vs, listOf(stillLutIndex, "999"), density)
        val lutVideoMin =
            maxReadoutValueWidthDp(textMeasurer, vs, listOf(videoLutIndex, "999"), density)
        val rawStrings = if (jpegCompanion) listOf("DNG", "DNG+") else listOf("DNG")
        val rawMin = maxReadoutValueWidthDp(textMeasurer, vs, rawStrings, density)

        val gapPx = with(density) { ReadoutChipGap.roundToPx() }
        return chipOuterWidthPx(textMeasurer, density, "ISO", ls, isoMin) +
            chipOuterWidthPx(textMeasurer, density, "Ss", ls, ssMin) +
            chipOuterWidthPx(textMeasurer, density, "WB", ls, wbMin) +
            chipOuterWidthPx(textMeasurer, density, "FPS", ls, fpsMin) +
            chipOuterWidthPx(textMeasurer, density, "Still", ls, lutStillMin) +
            chipOuterWidthPx(textMeasurer, density, "Video", ls, lutVideoMin) +
            chipOuterWidthPx(textMeasurer, density, "RAW", ls, rawMin) +
            gapPx * 6
    }

    if (rowWidthPx(1f) <= maxWidthPx) return 1f

    var lo = ReadoutMinFontScale
    var hi = 1f
    repeat(14) {
        val mid = (lo + hi) / 2f
        if (rowWidthPx(mid) <= maxWidthPx) {
            lo = mid
        } else {
            hi = mid
        }
    }
    return lo
}

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
 * Exposure readout between the finder and the 7×7 chrome grid: tappable chips open popups (ISO /
 * Ss / WB / FPS / Still LUT / Video LUT / RAW pipeline). Menus may overlap the preview; the strip
 * itself stays in its own band when closed (see `docs/preview-chrome-layout-style-guide.md`).
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
    val fpsDisplay =
        if (measuredFps > 0.05) {
            "${ceil(measuredFps).toInt()}fps"
        } else {
            "—fps"
        }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseLabelTypography = MaterialTheme.typography.labelSmall
    val stillLutIndex = stillLut.indexInScope(LutCatalog.Scope.Stills).toString()
    val videoLutIndex = videoLut.indexInScope(LutCatalog.Scope.Video).toString()
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
        horizontalArrangement = Arrangement.Start,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maxPx = constraints.maxWidth
            val scale =
                remember(
                    maxPx,
                    menu,
                    stillLutIndex,
                    videoLutIndex,
                    stillCaptureJpegCompanion,
                    baseLabelTypography,
                ) {
                    computeReadoutFontScale(
                        maxPx,
                        density,
                        textMeasurer,
                        baseLabelTypography,
                        menu,
                        stillLutIndex,
                        videoLutIndex,
                        stillCaptureJpegCompanion,
                    )
                }
            val labelStyle = remember(scale, baseLabelTypography) { baseLabelTypography.scaledFont(scale) }
            val valueStyle = remember(scale, labelStyle) { labelStyle.copy(fontFamily = FontFamily.Monospace) }
            val lutValueStyle =
                remember(valueStyle) {
                    valueStyle.copy(color = PnsColors.PhotoOrange.copy(alpha = 0.98f))
                }
            val isoValueMinWidth =
                remember(scale, menu, textMeasurer, valueStyle, density) {
                    maxReadoutValueWidthDp(textMeasurer, valueStyle, isoCandidateStrings(menu), density)
                }
            val ssValueMinWidth =
                remember(scale, menu, textMeasurer, valueStyle, density) {
                    maxReadoutValueWidthDp(textMeasurer, valueStyle, ssCandidateStrings(menu), density)
                }
            val wbValueMinWidth =
                remember(scale, menu, textMeasurer, valueStyle, density) {
                    maxReadoutValueWidthDp(textMeasurer, valueStyle, wbCandidateStrings(menu), density)
                }
            val fpsValueMinWidth =
                remember(scale, textMeasurer, valueStyle, density) {
                    maxReadoutValueWidthDp(textMeasurer, valueStyle, listOf("9999fps", "—fps"), density)
                }
            val lutStillValueMinWidth =
                remember(scale, stillLutIndex, textMeasurer, lutValueStyle, density) {
                    maxReadoutValueWidthDp(
                        textMeasurer,
                        lutValueStyle,
                        listOf(stillLutIndex, "999"),
                        density,
                    )
                }
            val lutVideoValueMinWidth =
                remember(scale, videoLutIndex, textMeasurer, lutValueStyle, density) {
                    maxReadoutValueWidthDp(
                        textMeasurer,
                        lutValueStyle,
                        listOf(videoLutIndex, "999"),
                        density,
                    )
                }
            val rawValueStrings =
                if (stillCaptureJpegCompanion) listOf("DNG", "DNG+") else listOf("DNG")
            val rawValueMinWidth =
                remember(scale, stillCaptureJpegCompanion, textMeasurer, valueStyle, density) {
                    maxReadoutValueWidthDp(textMeasurer, valueStyle, rawValueStrings, density)
                }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ReadoutChipGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Box {
                ReadoutMetricChip(
                    label = "ISO",
                    value = isoText,
                    valueMinWidth = isoValueMinWidth,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    onClick = { isoMenu = true },
                    accessibilityLabel = "ISO. Current $isoText. Opens ISO menu.",
                )
                PnsChromeDropdownMenu(expanded = isoMenu, onDismissRequest = { isoMenu = false }) {
                    for (choice in menu.isoChoices) {
                        val label = if (choice == null) "Auto" else choice.toString()
                        PnsChromePlainMenuItem(
                            label = label,
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
                    valueMinWidth = ssValueMinWidth,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    onClick = { ssMenu = true },
                    accessibilityLabel = "Shutter speed. Current $ss. Opens shutter menu.",
                )
                PnsChromeDropdownMenu(expanded = ssMenu, onDismissRequest = { ssMenu = false }) {
                    for (choice in menu.exposureChoices) {
                        val label =
                            if (choice == null) {
                                "Auto"
                            } else {
                                PreviewReadoutFormat.formatShutter(choice)
                            }
                        PnsChromePlainMenuItem(
                            label = label,
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
                    valueMinWidth = wbValueMinWidth,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    onClick = { awbMenu = true },
                    accessibilityLabel = "White balance. Current $awb. Opens WB menu.",
                )
                PnsChromeDropdownMenu(expanded = awbMenu, onDismissRequest = { awbMenu = false }) {
                    for (choice in menu.awbChoices) {
                        val label =
                            when (choice) {
                                null -> "Default (program)"
                                else -> PreviewReadoutFormat.awbModeLabel(choice)
                            }
                        PnsChromePlainMenuItem(
                            label = label,
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
                    value = fpsDisplay,
                    valueMinWidth = fpsValueMinWidth,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    onClick = { fpsMenu = true },
                    accessibilityLabel = "Measured FPS. Current $fpsDisplay. Opens FPS menu.",
                )
                PnsChromeDropdownMenu(expanded = fpsMenu, onDismissRequest = { fpsMenu = false }) {
                    for (opt in fpsOptions) {
                        val label =
                            buildString {
                                append(opt.targetFps)
                                append(" fps")
                                if (opt.requiresRoot) append(" (root)")
                            }
                        PnsChromePlainMenuItem(
                            label = label,
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
                    labelStyle = labelStyle,
                    valueStyle = lutValueStyle,
                    valueMinWidth = lutStillValueMinWidth,
                    onClick = { stillLutMenu = true },
                    accessibilityLabel = "Still capture LUT. Index ${stillLut.indexInScope(LutCatalog.Scope.Stills)} (${stillLut.displayName}).",
                )
                PnsChromeDropdownMenu(expanded = stillLutMenu, onDismissRequest = { stillLutMenu = false }) {
                    for (entry in stillLutChoices) {
                        PnsChromePlainMenuItem(
                            label = entry.displayName,
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
                    labelStyle = labelStyle,
                    valueStyle = lutValueStyle,
                    valueMinWidth = lutVideoValueMinWidth,
                    onClick = { videoLutMenu = true },
                    accessibilityLabel = "Video LUT. Index ${videoLut.indexInScope(LutCatalog.Scope.Video)} (${videoLut.displayName}).",
                )
                PnsChromeDropdownMenu(expanded = videoLutMenu, onDismissRequest = { videoLutMenu = false }) {
                    for (entry in videoLutChoices) {
                        PnsChromePlainMenuItem(
                            label = entry.displayName,
                            onClick = {
                                onPickVideoLut(entry)
                                videoLutMenu = false
                            },
                        )
                    }
                }
            }
                Box {
                    ReadoutMetricChip(
                        label = "RAW",
                        value = if (stillCaptureJpegCompanion) "DNG+" else "DNG",
                        valueMinWidth = rawValueMinWidth,
                        labelStyle = labelStyle,
                        valueStyle = valueStyle,
                        onClick = { rawMenu = true },
                        accessibilityLabel =
                            "Still capture pipeline. Current ${if (stillCaptureJpegCompanion) "DNG+" else "DNG only"}. Opens RAW menu.",
                    )
                    PnsChromeDropdownMenu(expanded = rawMenu, onDismissRequest = { rawMenu = false }) {
                        PnsChromePlainMenuItem(
                            label = "RAW (DNG only)",
                            onClick = {
                                onPickStillPipeline(false)
                                rawMenu = false
                            },
                        )
                        PnsChromePlainMenuItem(
                            label = "RAW+ (DNG + JPEG)",
                            onClick = {
                                onPickStillPipeline(true)
                                rawMenu = false
                            },
                        )
                    }
                }
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
    labelStyle: TextStyle,
    valueStyle: TextStyle,
    valueMinWidth: Dp,
    onClick: () -> Unit,
    accessibilityLabel: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .widthIn(min = valueMinWidth + 16.dp)
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
            style = labelStyle,
            color = Color.White.copy(alpha = 0.55f),
        )
        Text(
            text = current.indexInScope(scope).toString(),
            modifier = Modifier.fillMaxWidth(),
            style = valueStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReadoutMetricChip(
    label: String,
    value: String,
    valueMinWidth: Dp,
    labelStyle: TextStyle,
    valueStyle: TextStyle,
    onClick: () -> Unit,
    accessibilityLabel: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .widthIn(min = valueMinWidth + 16.dp)
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
            style = labelStyle,
            color = Color.White.copy(alpha = 0.55f),
        )
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = valueStyle,
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
