package dev.pointandshoot

import android.hardware.camera2.CaptureResult
import android.util.Size
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
import androidx.compose.material3.HorizontalDivider
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
import android.media.MediaRecorder
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
        add("SHD")
        add("?99999")
        menu.awbChoices.forEach { add(PreviewReadoutFormat.awbModeLabel(it)) }
    }

private fun computeReadoutFontScale(
    maxWidthPx: Int,
    density: androidx.compose.ui.unit.Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    baseLabelTypography: TextStyle,
    menu: ReadoutMenuSnapshot,
    stillLutIndex: String,
    videoLutIndex: String,
    primaryPhoto: Boolean,
    includeVideoRes: Boolean,
): Float {
    if (maxWidthPx <= 0) return 1f

    fun rowWidthPx(scale: Float): Int {
        val ls = baseLabelTypography.scaledFont(scale)
        val vs = ls.copy(fontFamily = FontFamily.Monospace)
        val isoMin = maxReadoutValueWidthDp(textMeasurer, vs, isoCandidateStrings(menu), density)
        val ssMin = maxReadoutValueWidthDp(textMeasurer, vs, ssCandidateStrings(menu), density)
        val wbMin = maxReadoutValueWidthDp(textMeasurer, vs, wbCandidateStrings(menu), density)
        val fpsMin = maxReadoutValueWidthDp(textMeasurer, vs, listOf("9999fps", "—fps"), density)
        val resMin =
            if (includeVideoRes) {
                maxReadoutValueWidthDp(textMeasurer, vs, listOf("3840×2160", "1080p", "8888p"), density)
            } else {
                0.dp
            }
        val lutStillMin =
            if (PreviewReadoutChipMode.showStillLutChip(primaryPhoto)) {
                maxReadoutValueWidthDp(textMeasurer, vs, listOf(stillLutIndex, "999"), density)
            } else {
                0.dp
            }
        val lutVideoMin =
            if (PreviewReadoutChipMode.showVideoLutChip(primaryPhoto)) {
                maxReadoutValueWidthDp(textMeasurer, vs, listOf(videoLutIndex, "999"), density)
            } else {
                0.dp
            }
        val imgStrings = listOf("DNG", "DNG+", "JPG")
        val imgMin =
            if (PreviewReadoutChipMode.showImgChip(primaryPhoto)) {
                maxReadoutValueWidthDp(textMeasurer, vs, imgStrings, density)
            } else {
                0.dp
            }
        val gapPx = with(density) { ReadoutChipGap.roundToPx() }
        var rowPx =
            chipOuterWidthPx(textMeasurer, density, "ISO", ls, isoMin) +
                chipOuterWidthPx(textMeasurer, density, "Ss", ls, ssMin) +
                chipOuterWidthPx(textMeasurer, density, "WB", ls, wbMin) +
                chipOuterWidthPx(textMeasurer, density, "FPS", ls, fpsMin) +
                if (includeVideoRes) {
                    chipOuterWidthPx(textMeasurer, density, "RES", ls, resMin)
                } else {
                    0
                }
        if (PreviewReadoutChipMode.showStillLutChip(primaryPhoto)) {
            rowPx += chipOuterWidthPx(textMeasurer, density, "Still", ls, lutStillMin)
        }
        if (PreviewReadoutChipMode.showVideoLutChip(primaryPhoto)) {
            rowPx += chipOuterWidthPx(textMeasurer, density, "Video", ls, lutVideoMin)
        }
        if (PreviewReadoutChipMode.showImgChip(primaryPhoto)) {
            rowPx += chipOuterWidthPx(textMeasurer, density, "IMG", ls, imgMin)
        }
        var chipCount = 5 // ISO, Ss, WB, FPS, AF
        if (includeVideoRes) chipCount++
        if (PreviewReadoutChipMode.showStillLutChip(primaryPhoto)) chipCount++
        if (PreviewReadoutChipMode.showVideoLutChip(primaryPhoto)) chipCount++
        if (PreviewReadoutChipMode.showImgChip(primaryPhoto)) chipCount++
        rowPx += gapPx * (chipCount - 1)
        return rowPx
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

    /** Second line under each WB preset in the readout menu (Kelvin anchor + tint direction). */
    fun awbModeMenuSubtitle(mode: Int): String? =
        AwbPresetReadout.kelvinTintForMode(mode)?.let { kt ->
            if (kt.kelvin <= 0) {
                kt.tintNote
            } else {
                "~${kt.kelvin} K · ${kt.tintNote}"
            }
        }
}

/**
 * Exposure readout between the finder and the **7×3** quick chrome grid (plus focal row): tappable chips open popups (ISO /
 * Ss / WB / FPS / Still LUT / Video LUT / IMG still pipeline). Menus may overlap the preview; the strip
 * itself stays in its own band when closed (see `docs/preview-chrome-layout-style-guide.md`).
 * The strip stays screen-aligned — it does **not** counter-rotate with device/chrome twist.
 */
@Suppress("LongParameterList", "LongMethod", "FunctionNaming")
@Composable
fun PreviewReadoutStrip(
    iso: Int?,
    exposureNs: Long?,
    awbMode: Int?,
    measuredFps: Double,
    stillCaptureJpegCompanion: Boolean,
    /** True when the preview session actually has a hardware JPEG surface (matches [PreviewController.previewUsesJpegCompanion]). */
    sessionJpegCompanionReady: Boolean,
    composedStillIntent: ComposedStillIntent,
    menu: ReadoutMenuSnapshot,
    fpsOptions: List<PreviewFpsSupport.QuickFpsOption>,
    /** When false, FPS readout stays visible but the target FPS menu is disabled (photo-primary tray). */
    fpsTargetEditable: Boolean = true,
    readoutAeCoupling: ReadoutAeCoupling = ReadoutAeCoupling.AUTO,
    onPickIsoBand: (ReadoutIsoBand) -> Unit = {},
    onPickIso: (Int?) -> Unit,
    onPickShutter: (Long?) -> Unit,
    onPickAwb: (Int) -> Unit,
    onPickFps: (Int) -> Unit,
    stillLut: LutCatalog,
    videoLut: LutCatalog,
    onPickStillLut: (LutCatalog) -> Unit,
    onPickVideoLut: (LutCatalog) -> Unit,
    /** Persists IMG menu tiers (RAW vs HDR / companion); must apply [ComposedStillIntent.coerceNoOffOff] if needed. */
    onComposedStillIntentChange: (ComposedStillIntent) -> Unit,
    /** Samples center YUV chroma (gray card) and locks preview WB; see [GrayCardWhiteBalance]. */
    onGrayCardWb: () -> Unit = {},
    /** Shallow hub rescan in flight without a valid JSON snapshot yet — non-blocking readout hint. */
    focalMapCalibratingHint: Boolean = false,
    /** Still / bracket encode progress; Milestone **10.15** (shown ahead of focal-map hint). */
    capturePipelineHint: String? = null,
    /** Last completed still metadata (Milestone **10.6**); shown inside the IMG menu. */
    lastStillPostReadout: StillPostReadoutSnapshot? = null,
    /** Video mode: [MediaRecorder] output sizes from the active camera map (readout only). */
    videoResSelectorVisible: Boolean = false,
    videoEncodeSizes: List<Size> = emptyList(),
    videoEncodeShortLabel: String = "",
    onPickVideoEncodeSize: (Size) -> Unit = {},
    /** Hide wrong-mode LUT chip and IMG chip: true = photo, false = video. */
    primaryPhoto: Boolean = true,
    /** Sprint **14.8** — tap opens HAL focus-mode picker (CAF / manual distance / …). */
    focusChipValue: String = "CAF",
    onFocusChipClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isVideoMode = PreviewReadoutChipMode.isVideoMode(primaryPhoto)
    val isoText =
        when {
            iso == null -> "—"
            readoutAeCoupling == ReadoutAeCoupling.LOCKED_ISO_AUTO_SS ||
                readoutAeCoupling == ReadoutAeCoupling.MANUAL_BOTH -> "$iso·L"
            else -> iso.toString()
        }
    val ss =
        when {
            exposureNs == null -> "—"
            readoutAeCoupling == ReadoutAeCoupling.LOCKED_SS_AUTO_ISO ||
                readoutAeCoupling == ReadoutAeCoupling.MANUAL_BOTH ->
                "${PreviewReadoutFormat.formatShutter(exposureNs)}·L"
            else -> PreviewReadoutFormat.formatShutter(exposureNs)
        }
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
    val rawChipValue =
        PreviewReadoutStillPipeline.chipLabel(composedStillIntent, stillCaptureJpegCompanion, sessionJpegCompanionReady)
    val rawChipA11y =
        PreviewReadoutStillPipeline.chipContentDescription(
            composedStillIntent,
            stillCaptureJpegCompanion,
            sessionJpegCompanionReady,
        )
    var isoMenu by remember { mutableStateOf(false) }
    var ssMenu by remember { mutableStateOf(false) }
    var awbMenu by remember { mutableStateOf(false) }
    var fpsMenu by remember { mutableStateOf(false) }
    var stillLutMenu by remember { mutableStateOf(false) }
    var videoLutMenu by remember { mutableStateOf(false) }
    var imgMenu by remember { mutableStateOf(false) }
    var videoResMenu by remember { mutableStateOf(false) }
    val stillLutChoices = remember { LutCatalog.forScope(LutCatalog.Scope.Stills) }
    val videoLutChoices = remember { LutCatalog.forScope(LutCatalog.Scope.Video) }
    val includeVideoRes = videoResSelectorVisible && videoEncodeSizes.isNotEmpty()

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
                    baseLabelTypography,
                    primaryPhoto,
                    includeVideoRes,
                ) {
                    computeReadoutFontScale(
                        maxPx,
                        density,
                        textMeasurer,
                        baseLabelTypography,
                        menu,
                        stillLutIndex,
                        videoLutIndex,
                        primaryPhoto,
                        includeVideoRes,
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
            val resValueMinWidth =
                remember(scale, includeVideoRes, videoEncodeShortLabel, textMeasurer, valueStyle, density) {
                    if (!includeVideoRes) {
                        0.dp
                    } else {
                        val opts =
                            buildSet {
                                add(videoEncodeShortLabel)
                                add("3840×2160")
                                add("1080p")
                            }
                        maxReadoutValueWidthDp(textMeasurer, valueStyle, opts, density)
                    }
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
            val imgValueStrings = listOf("DNG", "DNG+", "DNG12", "DNG12+", "JPG", "JPG+")
            val imgValueMinWidth =
                remember(scale, rawChipValue, textMeasurer, valueStyle, density) {
                    maxReadoutValueWidthDp(textMeasurer, valueStyle, imgValueStrings, density)
                }
            val focusValueMinWidth =
                remember(scale, focusChipValue, textMeasurer, valueStyle, density) {
                    maxReadoutValueWidthDp(
                        textMeasurer,
                        valueStyle,
                        listOf(focusChipValue, "CAF-P", "CAF-V", "MAC", "EDOF", "∞"),
                        density,
                    )
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
                    accessibilityLabel =
                        when (readoutAeCoupling) {
                            ReadoutAeCoupling.LOCKED_ISO_AUTO_SS ->
                                "ISO locked at $isoText, shutter automatic. Opens ISO menu."
                            ReadoutAeCoupling.MANUAL_BOTH ->
                                "ISO locked at $isoText. Opens ISO menu."
                            else -> "ISO. Current $isoText. Opens ISO menu."
                        },
                )
                PnsChromeDropdownMenu(expanded = isoMenu, onDismissRequest = { isoMenu = false }) {
                    Text(
                        text = "ISO band (${menu.isoBand.menuLabel})",
                        style = labelStyle,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    for (band in ReadoutIsoBand.entries) {
                        PnsChromePlainMenuItem(
                            label = band.menuLabel,
                            onClick = {
                                onPickIsoBand(band)
                                isoMenu = false
                            },
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                    for (choice in menu.isoChoices) {
                        if (choice == null) {
                            PnsChromePlainMenuItem(
                                label = "Auto",
                                onClick = {
                                    onPickIso(null)
                                    isoMenu = false
                                },
                            )
                        } else {
                            PnsChromeDetailMenuItem(
                                title = choice.toString(),
                                subtitle = "Lock ISO · auto shutter",
                                onClick = {
                                    onPickIso(choice)
                                    isoMenu = false
                                },
                            )
                        }
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
                    accessibilityLabel =
                        when (readoutAeCoupling) {
                            ReadoutAeCoupling.LOCKED_SS_AUTO_ISO ->
                                "Shutter locked at $ss, ISO automatic. Opens shutter menu."
                            ReadoutAeCoupling.MANUAL_BOTH ->
                                "Shutter locked at $ss. Opens shutter menu."
                            else -> "Shutter speed. Current $ss. Opens shutter menu."
                        },
                )
                PnsChromeDropdownMenu(expanded = ssMenu, onDismissRequest = { ssMenu = false }) {
                    for (choice in menu.exposureChoices) {
                        if (choice == null) {
                            PnsChromePlainMenuItem(
                                label = "Auto",
                                onClick = {
                                    onPickShutter(null)
                                    ssMenu = false
                                },
                            )
                        } else {
                            val title = PreviewReadoutFormat.formatShutter(choice)
                            PnsChromeDetailMenuItem(
                                title = title,
                                subtitle = "Lock shutter · auto ISO",
                                onClick = {
                                    onPickShutter(choice)
                                    ssMenu = false
                                },
                            )
                        }
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
                        val title = PreviewReadoutFormat.awbModeLabel(choice)
                        val subtitle = PreviewReadoutFormat.awbModeMenuSubtitle(choice)
                        if (subtitle == null) {
                            PnsChromePlainMenuItem(
                                label = title,
                                onClick = {
                                    onPickAwb(choice)
                                    awbMenu = false
                                },
                            )
                        } else {
                            PnsChromeDetailMenuItem(
                                title = title,
                                subtitle = subtitle,
                                onClick = {
                                    onPickAwb(choice)
                                    awbMenu = false
                                },
                            )
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                    PnsChromeDetailMenuItem(
                        title = "Gray card custom WB",
                        subtitle = "Fill the finder center with a neutral gray card, then tap",
                        onClick = {
                            onGrayCardWb()
                            awbMenu = false
                        },
                    )
                }
            }
            ReadoutMetricChip(
                label = "AF",
                value = focusChipValue,
                valueMinWidth = focusValueMinWidth,
                labelStyle = labelStyle,
                valueStyle = valueStyle,
                onClick = onFocusChipClick,
                accessibilityLabel = "Focus mode. Current $focusChipValue. Opens focus mode picker.",
            )
            if (PreviewReadoutChipMode.showVideoLutChip(primaryPhoto)) {
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
            }
            if (false /* RES chip superseded by tray VideoFormat FAB */ && includeVideoRes) {
                Box {
                    ReadoutMetricChip(
                        label = "RES",
                        value = videoEncodeShortLabel,
                        valueMinWidth = resValueMinWidth,
                        labelStyle = labelStyle,
                        valueStyle = valueStyle,
                        onClick = { videoResMenu = true },
                        accessibilityLabel =
                            "Video encode resolution. Current $videoEncodeShortLabel. Opens resolution menu.",
                    )
                    PnsChromeDropdownMenu(expanded = videoResMenu, onDismissRequest = { videoResMenu = false }) {
                        for (choice in videoEncodeSizes) {
                            val title = InAppVideoRecordingSupport.shortLabel(choice)
                            val subtitle = "${choice.width}×${choice.height}"
                            PnsChromeDetailMenuItem(
                                title = title,
                                subtitle = subtitle,
                                onClick = {
                                    onPickVideoEncodeSize(choice)
                                    videoResMenu = false
                                },
                            )
                        }
                    }
                }
            }
            if (PreviewReadoutChipMode.showStillLutChip(primaryPhoto)) {
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
            }
            if (PreviewReadoutChipMode.showImgChip(primaryPhoto)) {
                Box {
                    ReadoutMetricChip(
                        label = "IMG",
                        value = rawChipValue,
                        valueMinWidth = imgValueMinWidth,
                        labelStyle = labelStyle,
                        valueStyle = valueStyle,
                        onClick = { imgMenu = true },
                        accessibilityLabel = rawChipA11y,
                    )
                    PnsChromeDropdownMenu(expanded = imgMenu, onDismissRequest = { imgMenu = false }) {
                        Column {
                            fun applyIntent(next: ComposedStillIntent) {
                                onComposedStillIntentChange(next.coerceNoOffOff())
                            }
                            fun onPickRawTier(t: ImgMenuTier) {
                                val base = composedStillIntent.copy(raw = t)
                                val withHdr =
                                    if (t != ImgMenuTier.Off && base.jpeg == ImgMenuTier.Off) {
                                        base.copy(hdrWhenJpegOff = t)
                                    } else {
                                        base
                                    }
                                applyIntent(withHdr)
                                imgMenu = false
                            }
                            fun onPickJpegTier(t: ImgMenuTier) {
                                val base = composedStillIntent.copy(jpeg = t)
                                val withHdr =
                                    if (base.raw != ImgMenuTier.Off && t == ImgMenuTier.Off) {
                                        base.copy(hdrWhenJpegOff = base.raw)
                                    } else {
                                        base
                                    }
                                applyIntent(withHdr)
                                imgMenu = false
                            }
                            Text(
                                text = "-RAW-",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                            PnsChromeDetailMenuItem(
                                title = "Ultra",
                                subtitle = ImgMenuHints.rawRowSubtitle(ImgMenuTier.Ultra)!!,
                                selected = composedStillIntent.raw == ImgMenuTier.Ultra,
                                onClick = { onPickRawTier(ImgMenuTier.Ultra) },
                            )
                            PnsChromeDetailMenuItem(
                                title = "Standard",
                                subtitle = ImgMenuHints.rawRowSubtitle(ImgMenuTier.Standard)!!,
                                selected = composedStillIntent.raw == ImgMenuTier.Standard,
                                onClick = { onPickRawTier(ImgMenuTier.Standard) },
                            )
                            PnsChromePlainMenuItem(
                                label = "Off",
                                selected = composedStillIntent.raw == ImgMenuTier.Off,
                                onClick = { onPickRawTier(ImgMenuTier.Off) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = Color.White.copy(alpha = 0.18f),
                            )
                            Text(
                                text = "-JPEG-",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                            if (composedStillIntent.raw == ImgMenuTier.Off) {
                                PnsChromeDetailMenuItem(
                                    title = "Ultra",
                                    subtitle = ImgMenuHints.jpegOnlyPrimaryRowSubtitle(ImgMenuTier.Ultra)!!,
                                    selected = composedStillIntent.jpeg == ImgMenuTier.Ultra,
                                    onClick = { onPickJpegTier(ImgMenuTier.Ultra) },
                                )
                                PnsChromeDetailMenuItem(
                                    title = "Standard",
                                    subtitle = ImgMenuHints.jpegOnlyPrimaryRowSubtitle(ImgMenuTier.Standard)!!,
                                    selected = composedStillIntent.jpeg == ImgMenuTier.Standard,
                                    onClick = { onPickJpegTier(ImgMenuTier.Standard) },
                                )
                            } else {
                                PnsChromeDetailMenuItem(
                                    title = "Ultra",
                                    subtitle = ImgMenuHints.jpegHdrRowSubtitle(ImgMenuTier.Ultra)!!,
                                    selected = composedStillIntent.jpeg == ImgMenuTier.Ultra,
                                    onClick = { onPickJpegTier(ImgMenuTier.Ultra) },
                                )
                                PnsChromeDetailMenuItem(
                                    title = "Standard",
                                    subtitle = ImgMenuHints.jpegHdrRowSubtitle(ImgMenuTier.Standard)!!,
                                    selected = composedStillIntent.jpeg == ImgMenuTier.Standard,
                                    onClick = { onPickJpegTier(ImgMenuTier.Standard) },
                                )
                                PnsChromePlainMenuItem(
                                    label = "Off",
                                    selected = composedStillIntent.jpeg == ImgMenuTier.Off,
                                    onClick = { onPickJpegTier(ImgMenuTier.Off) },
                                )
                            }
                            val post = lastStillPostReadout
                            if (post != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = Color.White.copy(alpha = 0.18f),
                                )
                                Text(
                                    text = "Last still (HAL)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                )
                                post.rawFormatLabel?.let { fmt ->
                                    Text(
                                        text = "Format: $fmt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.82f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    )
                                }
                                post.dynamicRangeShort?.let { dr ->
                                    Text(
                                        text = "Dynamic range: $dr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.82f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    )
                                }
                                post.rawBinningDisplay?.let { b ->
                                    Text(
                                        text = "RAW binning: $b",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.82f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
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
    enabled: Boolean = true,
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
                    enabled = enabled,
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
