package dev.pointandshoot

import android.hardware.camera2.CaptureResult
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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

private val ReadoutChipGap = 4.dp
private val ReadoutChipHorizontalPadding = 4.dp
private val ReadoutChipWidthSlack = 8.dp

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
    return max(labelW, vminPx) + with(density) { ReadoutChipWidthSlack.roundToPx() }
}

private fun displayValueWidthDp(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    display: String,
    density: androidx.compose.ui.unit.Density,
): Dp = maxReadoutValueWidthDp(textMeasurer, style, listOf(display), density)

private val ReadoutLutChipLabel = "LUT"

/** RES chip is disabled in UI (`if (false && includeVideoRes)`); do not reserve row width for it. */
private const val ReadoutResChipRendered = false

private fun readoutChipMinPxList(
    scale: Float,
    density: androidx.compose.ui.unit.Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    baseLabelTypography: TextStyle,
    primaryPhoto: Boolean,
    includeVideoRes: Boolean,
    focusChipValue: String,
    stabChipLabel: String?,
    aeLocked: Boolean,
    isoDisplay: String,
    ssDisplay: String,
    awbDisplay: String,
    apertureDisplay: String?,
    stillLutIndex: String,
    videoLutIndex: String,
    imgDisplay: String,
    videoResDisplay: String,
): List<Int> {
    val ls = baseLabelTypography.scaledFont(scale)
    val vs = ls.copy(fontFamily = FontFamily.Monospace)
    val lockExtraPx =
        if (aeLocked) {
            with(density) { (12.dp + 2.dp).roundToPx() }
        } else {
            0
        }
    val chips =
        buildList {
            add(Triple("ISO", isoDisplay, lockExtraPx))
            add(Triple("SS", ssDisplay, 0))
            if (!apertureDisplay.isNullOrBlank()) {
                add(Triple("F", apertureDisplay, 0))
            }
            add(Triple("WB", awbDisplay, 0))
            add(Triple("AF", focusChipValue, 0))
            if (!stabChipLabel.isNullOrBlank()) {
                add(Triple("STAB", stabChipLabel, 0))
            }
            if (ReadoutResChipRendered && includeVideoRes) {
                add(Triple("RES", videoResDisplay, 0))
            }
            if (PreviewReadoutChipMode.showStillLutChip(primaryPhoto)) {
                add(Triple(ReadoutLutChipLabel, stillLutIndex, 0))
            }
            if (PreviewReadoutChipMode.showVideoLutChip(primaryPhoto)) {
                add(Triple(ReadoutLutChipLabel, videoLutIndex, 0))
            }
            if (PreviewReadoutChipMode.showImgChip(primaryPhoto)) {
                add(Triple("IMG", imgDisplay, 0))
            }
        }
    return chips.map { (label, value, extraPx) ->
        val valueMin = displayValueWidthDp(textMeasurer, vs, value, density)
        chipOuterWidthPx(textMeasurer, density, label, ls, valueMin) + extraPx
    }
}

private fun distributeReadoutChipWidthsPx(
    rowWidthPx: Int,
    chipMinPxList: List<Int>,
    gapPx: Int,
    reservedLeadingPx: Int = 0,
): List<Int> {
    if (chipMinPxList.isEmpty()) return emptyList()
    val gapTotal = gapPx * (chipMinPxList.size - 1).coerceAtLeast(0)
    val budget = (rowWidthPx - reservedLeadingPx - gapTotal).coerceAtLeast(0)
    val sumMin = chipMinPxList.sum()
    val extraEach =
        if (chipMinPxList.isEmpty()) {
            0
        } else {
            ((budget - sumMin) / chipMinPxList.size).coerceAtLeast(0)
        }
    val remainder = (budget - sumMin - extraEach * chipMinPxList.size).coerceAtLeast(0)
    return chipMinPxList.mapIndexed { index, min ->
        min + extraEach + if (index == chipMinPxList.lastIndex) remainder else 0
    }
}

private data class ReadoutChipLayout(
    val iso: Dp,
    val ss: Dp,
    val wb: Dp,
    val aperture: Dp?,
    val af: Dp,
    val stab: Dp?,
    val videoLut: Dp?,
    val stillLut: Dp?,
    val img: Dp?,
    val res: Dp?,
)

private fun computeReadoutFontScale(
    maxWidthPx: Int,
    density: androidx.compose.ui.unit.Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    baseLabelTypography: TextStyle,
    primaryPhoto: Boolean,
    includeVideoRes: Boolean,
    focusChipValue: String,
    stabChipLabel: String?,
    showMacroVideoBadge: Boolean,
    aeLocked: Boolean,
    isoDisplay: String,
    ssDisplay: String,
    awbDisplay: String,
    apertureDisplay: String?,
    stillLutIndex: String,
    videoLutIndex: String,
    imgDisplay: String,
    videoResDisplay: String,
): Float {
    if (maxWidthPx <= 0) return ReadoutMinFontScale

    val widthBudgetPx = maxWidthPx.coerceAtLeast(1)

    fun rowWidthPx(scale: Float): Int {
        val gapPx = with(density) { ReadoutChipGap.roundToPx() }
        val chipMinPxList =
            readoutChipMinPxList(
                scale,
                density,
                textMeasurer,
                baseLabelTypography,
                primaryPhoto,
                includeVideoRes,
                focusChipValue,
                stabChipLabel,
                aeLocked,
                isoDisplay,
                ssDisplay,
                awbDisplay,
                apertureDisplay,
                stillLutIndex,
                videoLutIndex,
                imgDisplay,
                videoResDisplay,
            )
        val macroPx =
            if (showMacroVideoBadge) {
                with(density) { (textMeasurer.measure(AnnotatedString("MACRO VIDEO"), baseLabelTypography.scaledFont(scale)).size.width + 4.dp.roundToPx()) }
            } else {
                0
            }
        return macroPx +
            chipMinPxList.sum() +
            gapPx * (chipMinPxList.size - 1).coerceAtLeast(0)
    }

    if (rowWidthPx(1f) <= widthBudgetPx) return 1f

    var lo = ReadoutMinFontScale
    var hi = 1f
    repeat(14) {
        val mid = (lo + hi) / 2f
        if (rowWidthPx(mid) <= widthBudgetPx) {
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
    /** Sprint **15.26** — [CaptureRequest.CONTROL_AE_LOCK] active; long-press ISO/Ss toggles. */
    aeLocked: Boolean = false,
    onToggleAeLock: () -> Unit = {},
    /** When set in video mode, prefixed on the SS chip (e.g. `180°`). */
    videoShutterAngleLabel: String? = null,
    videoShutterAngle: VideoShutterAngle = VideoShutterAngle.Free,
    onPickIsoBand: (ReadoutIsoBand) -> Unit = {},
    onPickIso: (Int?) -> Unit,
    onPickShutter: (Long?) -> Unit,
    onPickVideoShutterAngle: (VideoShutterAngle) -> Unit = {},
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
    /** Sprint **15.31** — amber badge when video tray + **MACRO** dial. */
    showMacroVideoBadge: Boolean = false,
    /** Sprint **15.32** — OIS/EIS readout chip; hidden when null. */
    stabChipLabel: String? = null,
    /** Milestone **17.5** — fleet gate for STAB chip (defaults to showing when label set). */
    showStabChip: Boolean = true,
    /** Milestone **17.5** — fleet gate for IMG chip in photo mode. */
    showImgChipOverride: Boolean = true,
    /** M19.6 — optional long-press shortcut for [StillFormatPickerSheet] (primary entry is tray FAB). */
    onImgChipLongClick: () -> Unit = {},
    /** Sprint **14.8** — tap opens HAL focus-mode picker (CAF / manual distance / …). */
    focusChipValue: String = "CAF",
    onFocusChipClick: () -> Unit = {},
    onFocusChipLongClick: () -> Unit = {},
    apertureChipValue: String? = null,
    apertureChipEnabled: Boolean = false,
    onCycleAperture: () -> Unit = {},
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
                readoutAeCoupling == ReadoutAeCoupling.MANUAL_BOTH -> {
                val base = PreviewReadoutFormat.formatShutter(exposureNs)
                val anglePrefix =
                    if (isVideoMode && !videoShutterAngleLabel.isNullOrBlank()) {
                        "${videoShutterAngleLabel} "
                    } else {
                        ""
                    }
                "$anglePrefix$base·L"
            }
            isVideoMode && !videoShutterAngleLabel.isNullOrBlank() ->
                "${videoShutterAngleLabel} ${PreviewReadoutFormat.formatShutter(exposureNs)}"
            else -> PreviewReadoutFormat.formatShutter(exposureNs)
        }
    val awb = PreviewReadoutFormat.awbModeLabel(awbMode)
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
    var isoRangeStart by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(isoMenu) {
        if (!isoMenu) {
            isoRangeStart = null
        }
    }
    val stillLutChoices = remember { LutCatalog.forScope(LutCatalog.Scope.Stills) }
    val videoLutChoices = remember { LutCatalog.forScope(LutCatalog.Scope.Video) }
    val includeVideoRes = videoResSelectorVisible && videoEncodeSizes.isNotEmpty()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PreviewReadoutStripHeight)
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maxPx = constraints.maxWidth
            if (maxPx <= 0) return@BoxWithConstraints
            val scale =
                remember(
                    maxPx,
                    stillLutIndex,
                    videoLutIndex,
                    baseLabelTypography,
                    primaryPhoto,
                    includeVideoRes,
                    focusChipValue,
                    stabChipLabel,
                    showMacroVideoBadge,
                    aeLocked,
                    isoText,
                    ss,
                    awb,
                    rawChipValue,
                    videoEncodeShortLabel,
                    apertureChipValue,
                ) {
                    computeReadoutFontScale(
                        maxPx,
                        density,
                        textMeasurer,
                        baseLabelTypography,
                        primaryPhoto,
                        includeVideoRes,
                        focusChipValue,
                        stabChipLabel,
                        showMacroVideoBadge,
                        aeLocked,
                        isoText,
                        ss,
                        awb,
                        apertureChipValue,
                        stillLutIndex,
                        videoLutIndex,
                        rawChipValue,
                        videoEncodeShortLabel,
                    )
                }
            val labelStyle = remember(scale, baseLabelTypography) { baseLabelTypography.scaledFont(scale) }
            val valueStyle = remember(scale, labelStyle) { labelStyle.copy(fontFamily = FontFamily.Monospace) }
            val lutValueStyle =
                remember(valueStyle) {
                    valueStyle.copy(color = PnsColors.PhotoOrange.copy(alpha = 0.98f))
                }
            val isoValueMinWidth =
                remember(scale, isoText, textMeasurer, valueStyle, density) {
                    displayValueWidthDp(textMeasurer, valueStyle, isoText, density)
                }
            val ssValueMinWidth =
                remember(scale, ss, textMeasurer, valueStyle, density) {
                    displayValueWidthDp(textMeasurer, valueStyle, ss, density)
                }
            val wbValueMinWidth =
                remember(scale, awb, textMeasurer, valueStyle, density) {
                    displayValueWidthDp(textMeasurer, valueStyle, awb, density)
                }
            val apertureValueMinWidth =
                remember(scale, apertureChipValue, textMeasurer, valueStyle, density) {
                    if (apertureChipValue.isNullOrBlank()) {
                        0.dp
                    } else {
                        displayValueWidthDp(textMeasurer, valueStyle, apertureChipValue, density)
                    }
                }
            val focusValueMinWidth =
                remember(scale, focusChipValue, textMeasurer, valueStyle, density) {
                    displayValueWidthDp(textMeasurer, valueStyle, focusChipValue, density)
                }

            val stabValueMinWidth =
                remember(scale, stabChipLabel, textMeasurer, valueStyle, density) {
                    if (stabChipLabel.isNullOrBlank()) {
                        0.dp
                    } else {
                        displayValueWidthDp(textMeasurer, valueStyle, stabChipLabel, density)
                    }
                }
            val lutStillValueMinWidth =
                remember(scale, stillLutIndex, textMeasurer, lutValueStyle, density) {
                    displayValueWidthDp(textMeasurer, lutValueStyle, stillLutIndex, density)
                }
            val lutVideoValueMinWidth =
                remember(scale, videoLutIndex, textMeasurer, lutValueStyle, density) {
                    displayValueWidthDp(textMeasurer, lutValueStyle, videoLutIndex, density)
                }
            val imgValueMinWidth =
                remember(scale, rawChipValue, textMeasurer, valueStyle, density) {
                    displayValueWidthDp(textMeasurer, valueStyle, rawChipValue, density)
                }
            val chipMinPxList =
                remember(
                    scale,
                    maxPx,
                    primaryPhoto,
                    includeVideoRes,
                    focusChipValue,
                    stabChipLabel,
                    aeLocked,
                    isoText,
                    ss,
                    awb,
                    stillLutIndex,
                    videoLutIndex,
                    rawChipValue,
                    videoEncodeShortLabel,
                    showMacroVideoBadge,
                    apertureChipValue,
                ) {
                    readoutChipMinPxList(
                        scale,
                        density,
                        textMeasurer,
                        baseLabelTypography,
                        primaryPhoto,
                        includeVideoRes,
                        focusChipValue,
                        stabChipLabel,
                        aeLocked,
                        isoText,
                        ss,
                        awb,
                        apertureChipValue,
                        stillLutIndex,
                        videoLutIndex,
                        rawChipValue,
                        videoEncodeShortLabel,
                    )
                }
            val macroBadgePx =
                remember(showMacroVideoBadge, scale, labelStyle) {
                    if (!showMacroVideoBadge) {
                        0
                    } else {
                        with(density) {
                            (textMeasurer.measure(AnnotatedString("MACRO VIDEO"), labelStyle).size.width +
                                4.dp.roundToPx())
                        }
                    }
                }
            val gapPx = with(density) { ReadoutChipGap.roundToPx() }
            val chipWidthsPx =
                remember(maxPx, chipMinPxList, macroBadgePx, gapPx) {
                    distributeReadoutChipWidthsPx(maxPx, chipMinPxList, gapPx, macroBadgePx)
                }
            val chipLayout =
                remember(
                    chipWidthsPx,
                    density,
                    stabChipLabel,
                    primaryPhoto,
                    includeVideoRes,
                ) {
                    var idx = 0
                    fun takeWidth(): Dp = with(density) { chipWidthsPx[idx++].toDp() }
                    ReadoutChipLayout(
                        iso = takeWidth(),
                        ss = takeWidth(),
                        aperture = if (!apertureChipValue.isNullOrBlank()) takeWidth() else null,
                        wb = takeWidth(),
                        af = takeWidth(),
                        stab = if (!stabChipLabel.isNullOrBlank()) takeWidth() else null,
                        videoLut =
                            if (PreviewReadoutChipMode.showVideoLutChip(primaryPhoto)) {
                                takeWidth()
                            } else {
                                null
                            },
                        stillLut =
                            if (PreviewReadoutChipMode.showStillLutChip(primaryPhoto)) {
                                takeWidth()
                            } else {
                                null
                            },
                        img =
                            if (PreviewReadoutChipMode.showImgChip(primaryPhoto)) {
                                takeWidth()
                            } else {
                                null
                            },
                        res =
                            if (ReadoutResChipRendered && includeVideoRes) {
                                takeWidth()
                            } else {
                                null
                            },
                    )
                }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReadoutChipGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            if (showMacroVideoBadge) {
                Text(
                    text = "MACRO VIDEO",
                    style = labelStyle,
                    color = PnsColors.WarnAmber.copy(alpha = 0.98f),
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
            Box(modifier = Modifier.width(chipLayout.iso)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (aeLocked) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "AE locked",
                            tint = PnsColors.WarnAmber.copy(alpha = 0.95f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    ReadoutMetricChip(
                        label = "ISO",
                        value = isoText,
                        valueMinWidth = isoValueMinWidth,
                        labelStyle = labelStyle,
                        valueStyle = valueStyle,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onClick = { isoMenu = true },
                        onLongClick = onToggleAeLock,
                        accessibilityLabel =
                            when {
                                aeLocked ->
                                    "ISO $isoText, AE locked. Long press to unlock. Tap for ISO menu."
                                readoutAeCoupling == ReadoutAeCoupling.LOCKED_ISO_AUTO_SS ->
                                    "ISO locked at $isoText, shutter automatic. Opens ISO menu."
                                readoutAeCoupling == ReadoutAeCoupling.MANUAL_BOTH ->
                                    "ISO locked at $isoText. Opens ISO menu."
                                else -> "ISO. Current $isoText. Long press to lock AE. Opens ISO menu."
                            },
                    )
                }
                PnsChromeDropdownMenu(expanded = isoMenu, onDismissRequest = { isoMenu = false }) {
                    Text(
                        text = "ISO auto range (${menu.isoBand.menuLabel})",
                        style = labelStyle,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    PnsChromePlainMenuItem(
                        label = "Auto (sensor range)",
                        selected = menu.isoBand.isAutoRange,
                        onClick = {
                            onPickIsoBand(ReadoutIsoBand.AUTO)
                            isoRangeStart = null
                        },
                    )
                    val rangeStops = menu.isoChoices.filterNotNull().distinct().sorted()
                    for (stop in rangeStops) {
                        val band = menu.isoBand
                        val selected =
                            !band.isAutoRange &&
                                band.minIso == stop &&
                                band.maxIso == stop
                        PnsChromePlainMenuItem(
                            label = stop.toString(),
                            selected = selected,
                            onClick = {
                                val start = isoRangeStart
                                if (start == null || menu.isoBand.isAutoRange) {
                                    // Single-stop range = lock that ISO (AE leaves Auto).
                                    // A second tap fills a multi-stop auto-range clamp.
                                    onPickIsoBand(ReadoutIsoBand.fromBounds(stop, stop))
                                    onPickIso(stop)
                                    isoRangeStart = stop
                                } else {
                                    val lo = minOf(start, stop)
                                    val hi = maxOf(start, stop)
                                    onPickIsoBand(ReadoutIsoBand.fromBounds(lo, hi))
                                    // Span clamp keeps AE Auto; band still floors/ceilings chase + locks.
                                    onPickIso(null)
                                    isoRangeStart = null
                                }
                                isoMenu = false
                            },
                        )
                    }
                    if (isoRangeStart != null && !menu.isoBand.isAutoRange) {
                        Text(
                            text = "Tap another ISO to fill Auto range from $isoRangeStart (clears lock)",
                            style = MaterialTheme.typography.labelSmall,
                            color = PnsColors.PhotoOrange.copy(alpha = 0.82f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                    for (choice in menu.isoChoices) {
                        if (choice == null) {
                            PnsChromePlainMenuItem(
                                label = "Auto",
                                selected = menu.selectedIso == null,
                                onClick = {
                                    onPickIso(null)
                                    isoMenu = false
                                },
                            )
                        } else {
                            PnsChromeDetailMenuItem(
                                title = choice.toString(),
                                subtitle = "Lock ISO · auto shutter",
                                selected = choice == menu.selectedIso,
                                onClick = {
                                    onPickIsoBand(ReadoutIsoBand.fromBounds(choice, choice))
                                    onPickIso(choice)
                                    isoRangeStart = null
                                    isoMenu = false
                                },
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.width(chipLayout.ss)) {
                ReadoutMetricChip(
                    label = "SS",
                    value = ss,
                    valueMinWidth = ssValueMinWidth,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { ssMenu = true },
                    onLongClick = onToggleAeLock,
                    accessibilityLabel =
                        when {
                            aeLocked ->
                                "Shutter $ss, AE locked. Long press to unlock. Opens shutter menu."
                            readoutAeCoupling == ReadoutAeCoupling.LOCKED_SS_AUTO_ISO ->
                                "Shutter locked at $ss, ISO automatic. Opens shutter menu."
                            readoutAeCoupling == ReadoutAeCoupling.MANUAL_BOTH ->
                                "Shutter locked at $ss. Opens shutter menu."
                            else -> "Shutter speed. Current $ss. Long press to lock AE. Opens shutter menu."
                        },
                )
                PnsChromeDropdownMenu(expanded = ssMenu, onDismissRequest = { ssMenu = false }) {
                    if (isVideoMode) {
                        for (angle in VideoShutterAngle.entries) {
                            val subtitle =
                                if (angle == VideoShutterAngle.Free) {
                                    "No shutter-angle lock (manual shutter speed)"
                                } else {
                                    "Lock shutter to ${angle.label} of frame interval (auto ISO)"
                                }
                            val title =
                                if (angle == videoShutterAngle) {
                                    "${angle.label} (selected)"
                                } else {
                                    angle.label
                                }
                            PnsChromeDetailMenuItem(
                                title = title,
                                subtitle = subtitle,
                                onClick = {
                                    onPickVideoShutterAngle(angle)
                                    ssMenu = false
                                },
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                    }
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
            if (!apertureChipValue.isNullOrBlank() && chipLayout.aperture != null) {
                ReadoutMetricChip(
                    label = "F",
                    value = apertureChipValue,
                    valueMinWidth = apertureValueMinWidth,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    modifier = Modifier.width(chipLayout.aperture),
                    enabled = apertureChipEnabled,
                    onClick = onCycleAperture,
                    accessibilityLabel =
                        if (apertureChipEnabled) {
                            "Aperture $apertureChipValue. Tap to cycle f-stops."
                        } else {
                            "Aperture $apertureChipValue. Fixed on this lens."
                        },
                )
            }
            Box(modifier = Modifier.width(chipLayout.wb)) {
                ReadoutMetricChip(
                    label = "WB",
                    value = awb,
                    valueMinWidth = wbValueMinWidth,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.width(chipLayout.af),
                onClick = onFocusChipClick,
                onLongClick = onFocusChipLongClick,
                accessibilityLabel = "Focus mode. Current $focusChipValue. Opens focus mode picker.",
            )
            if (showStabChip && !stabChipLabel.isNullOrBlank()) {
                ReadoutMetricChip(
                    label = "STAB",
                    value = stabChipLabel,
                    valueMinWidth = stabValueMinWidth,
                    labelStyle = labelStyle,
                    valueStyle = valueStyle,
                    modifier = Modifier.width(chipLayout.stab!!),
                    enabled = false,
                    onClick = {},
                    accessibilityLabel = "Stabilization. Current $stabChipLabel.",
                )
            }
            if (PreviewReadoutChipMode.showVideoLutChip(primaryPhoto)) {
            Box(modifier = Modifier.width(chipLayout.videoLut!!)) {
                ReadoutLutChip(
                    label = ReadoutLutChipLabel,
                    scope = LutCatalog.Scope.Video,
                    current = videoLut,
                    labelStyle = labelStyle,
                    valueStyle = lutValueStyle,
                    valueMinWidth = lutVideoValueMinWidth,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { videoLutMenu = true },
                    accessibilityLabel =
                        "Video LUT. Index ${videoLut.indexInScope(LutCatalog.Scope.Video)} (${videoLut.displayName}).",
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
            if (false /* RES chip superseded by tray VideoFormat FAB */ && ReadoutResChipRendered && includeVideoRes) {
                Box(modifier = Modifier.width(chipLayout.res!!)) {
                    ReadoutMetricChip(
                        label = "RES",
                        value = videoEncodeShortLabel,
                        valueMinWidth = displayValueWidthDp(textMeasurer, valueStyle, videoEncodeShortLabel, density),
                        labelStyle = labelStyle,
                        valueStyle = valueStyle,
                        modifier = Modifier.fillMaxWidth(),
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
            Box(modifier = Modifier.width(chipLayout.stillLut!!)) {
                ReadoutLutChip(
                    label = ReadoutLutChipLabel,
                    scope = LutCatalog.Scope.Stills,
                    current = stillLut,
                    labelStyle = labelStyle,
                    valueStyle = lutValueStyle,
                    valueMinWidth = lutStillValueMinWidth,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { stillLutMenu = true },
                    accessibilityLabel =
                        "Still capture LUT. Index ${stillLut.indexInScope(LutCatalog.Scope.Stills)} (${stillLut.displayName}).",
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
            if (PreviewReadoutChipMode.showImgChip(primaryPhoto) && showImgChipOverride) {
                Box(modifier = Modifier.width(chipLayout.img!!)) {
                    ReadoutMetricChip(
                        label = "IMG",
                        value = rawChipValue,
                        valueMinWidth = imgValueMinWidth,
                        labelStyle = labelStyle,
                        valueStyle = valueStyle,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { imgMenu = true },
                        onLongClick = onImgChipLongClick,
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

val PreviewReadoutStripHeight = 44.dp

@Composable
private fun ReadoutLutChip(
    label: String,
    scope: LutCatalog.Scope,
    current: LutCatalog,
    labelStyle: TextStyle,
    valueStyle: TextStyle,
    valueMinWidth: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    accessibilityLabel: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            modifier
                .widthIn(min = valueMinWidth + ReadoutChipWidthSlack)
                .semantics { contentDescription = accessibilityLabel }
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ).background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = ReadoutChipHorizontalPadding, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    accessibilityLabel: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)
    val clickModifier =
        if (onLongClick != null) {
            Modifier.combinedClickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        } else {
            Modifier.clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
        }
    Column(
        modifier =
            modifier
                .widthIn(min = valueMinWidth + ReadoutChipWidthSlack)
                .semantics { contentDescription = accessibilityLabel }
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
                .then(clickModifier)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = ReadoutChipHorizontalPadding, vertical = 3.dp),
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
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
