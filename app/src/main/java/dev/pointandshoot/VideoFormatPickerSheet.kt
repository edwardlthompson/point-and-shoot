package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pointandshoot.fleet.DeviceAdaptedCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sprint 13.17 — Unified stepped video picker.
 *
 * Four sequential steps, each filtered by the previous:
 *   1. Aspect Ratio  (16:9, 4:3, 21:9 …)
 *   2. Resolution    (filtered to chosen aspect)
 *   3. Frame Rate    (filtered to chosen resolution)
 *   4. Codec/Format  (filtered to chosen resolution+fps; unavailable options hidden)
 *
 * The chip label shows the full selection: "16:9 · 4K · 120fps · H.265".
 */

/** Aspect ratio derived from resolution dimensions. */
private data class AspectRatio(val label: String, val wRatio: Int, val hRatio: Int)

private fun videoAspectRatio(w: Int, h: Int): AspectRatio {
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val g = gcd(w, h)
    val wr = w / g; val hr = h / g
    return when {
        w == h -> AspectRatio("1:1", 1, 1)
        wr == 16 && hr == 9 -> AspectRatio("16:9", 16, 9)
        wr == 4 && hr == 3 -> AspectRatio("4:3", 4, 3)
        wr == 21 && hr == 9 -> AspectRatio("21:9", 21, 9)
        wr == 64 && hr == 27 -> AspectRatio("21:9", 21, 9)   // 2560×1080
        wr == 320 && hr == 137 -> AspectRatio("21:9", 21, 9) // 3840×1644
        wr == 256 && hr == 135 -> AspectRatio("4K DCI", 256, 135) // 4096×2160
        wr == 17 && hr == 9 -> AspectRatio("4K DCI", 17, 9)
        else -> AspectRatio("${wr}:${hr}", wr, hr)
    }
}

private fun resolutionLabel(w: Int, h: Int): String = when {
    w >= 7680 -> "8K  ${w}×${h}"
    w >= 4096 -> "4K DCI  ${w}×${h}"
    w >= 3840 -> "4K UHD  ${w}×${h}"
    w >= 1920 -> "1080p  ${w}×${h}"
    w >= 1280 -> "720p  ${w}×${h}"
    else -> "${w}×${h}"
}

private fun resolutionShortLabel(w: Int, h: Int): String = when {
    w >= 7680 -> "8K"
    w >= 4096 -> "4K DCI"
    w >= 3840 -> "4K"
    w >= 1920 -> "1080p"
    w >= 1280 -> "720p"
    else -> "${h}p"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFormatPickerSheet(
    formats: List<VideoFormat>,
    selectedFormat: VideoFormat?,
    halSupports8kCapture: Boolean = false,
    rawVideoAvailable: Boolean = false,
    rawVideoSelected: Boolean = false,
    chrome: PreviewChromePreferences,
    patchChrome: ((PreviewChromePreferences) -> PreviewChromePreferences) -> Unit,
    hud: HudSettings,
    patchHud: (HudSettings) -> Unit,
    onSelect: (VideoFormat) -> Unit,
    onSelectRawVideo: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appContext = LocalContext.current.applicationContext
    var hiFiMuxRateHz by remember { mutableIntStateOf(0) }
    var mcCaps by remember { mutableStateOf<MediaCodecCapabilityProbe.CapabilityMatrix?>(null) }
    LaunchedEffect(appContext) {
        hiFiMuxRateHz =
            withContext(Dispatchers.Default) {
                PnsAacEncoderSupport.maxHiFiMuxSampleRateHz(appContext)
            }
        mcCaps =
            withContext(Dispatchers.Default) {
                MediaCodecCapabilityProbe.probeSync()
            }
    }
    val colorSpaces =
        remember(formats) {
            DeviceAdaptedCatalog.videoColorSpacesForDevice(formats)
        }
    var pickedColorOrdinal by remember(chrome.inAppVideoColorSpaceOrdinal, colorSpaces) {
        mutableIntStateOf(
            chrome.inAppVideoColorSpaceOrdinal.takeIf { ord ->
                ord >= 0 && colorSpaces.getOrNull(ord) != null
            } ?: colorSpaces.indexOfFirst { it.id == "rec709" }.coerceAtLeast(0),
        )
    }
    LaunchedEffect(colorSpaces, pickedColorOrdinal) {
        if (colorSpaces.isEmpty()) return@LaunchedEffect
        if (colorSpaces.getOrNull(pickedColorOrdinal) == null) {
            val rec709 = colorSpaces.indexOfFirst { it.id == "rec709" }.coerceAtLeast(0)
            pickedColorOrdinal = rec709
            patchChrome { it.copy(inAppVideoColorSpaceOrdinal = rec709) }
        }
    }
    val pickedColorSpace = colorSpaces.getOrNull(pickedColorOrdinal)
    val maxColorOrdinal =
        remember(colorSpaces) {
            colorSpaces
                .withIndex()
                .maxByOrNull { it.value.cqi }
                ?.index
                ?: 0
        }
    val maxCqiColorSpace = colorSpaces.getOrNull(maxColorOrdinal)
    val filteredFormats =
        remember(formats, pickedColorSpace) {
            ColorQualityIndex.filterVideoFormats(formats, pickedColorSpace)
        }
    val maxCqiFilteredFormats =
        remember(formats, maxCqiColorSpace) {
            ColorQualityIndex.filterVideoFormats(formats, maxCqiColorSpace)
        }
    val pickerLists8k =
        remember(filteredFormats) {
            filteredFormats.any { it.resolution.width >= 7680 || it.resolution.height >= 4320 }
        }
    val hiFiKhzLabel =
        remember(hiFiMuxRateHz) {
            if (hiFiMuxRateHz > 0) "${hiFiMuxRateHz / 1000} kHz" else "…"
        }

    // Derive all distinct aspects, resolutions, fps, codecs from the filtered format list
    val allAspects = remember(filteredFormats) {
        filteredFormats.map { videoAspectRatio(it.resolution.width, it.resolution.height) }
            .distinctBy { it.label }.sortedBy { it.label }
    }

    // Picker state — initialise from selectedFormat if available
    var pickedAspect by remember(selectedFormat, filteredFormats) {
        mutableStateOf(
            selectedFormat?.let { videoAspectRatio(it.resolution.width, it.resolution.height) }
                ?: allAspects.firstOrNull { it.label == "16:9" } ?: allAspects.firstOrNull()
        )
    }
    var pickedResolution by remember(selectedFormat, filteredFormats) {
        mutableStateOf(selectedFormat?.resolution)
    }
    var pickedFps by remember(selectedFormat) { mutableStateOf(selectedFormat?.frameRate) }
    var pickedCodec by remember(selectedFormat) { mutableStateOf(selectedFormat?.codec) }

    // Derived filtered lists for each step
    val aspectFormats = remember(filteredFormats, pickedAspect) {
        val a = pickedAspect ?: return@remember filteredFormats
        filteredFormats.filter { videoAspectRatio(it.resolution.width, it.resolution.height).label == a.label }
    }
    val availableResolutions = remember(aspectFormats) {
        aspectFormats.map { it.resolution }.distinctBy { "${it.width}x${it.height}" }
            .sortedByDescending { it.width.toLong() * it.height }
    }
    val resolutionFormats = remember(aspectFormats, pickedResolution) {
        val r = pickedResolution ?: return@remember aspectFormats
        aspectFormats.filter { it.resolution.width == r.width && it.resolution.height == r.height }
    }
    val availableFps = remember(resolutionFormats) {
        resolutionFormats.map { it.frameRate }.distinct().sortedDescending()
    }
    val fpsFormats = remember(resolutionFormats, pickedFps) {
        val f = pickedFps ?: return@remember resolutionFormats
        resolutionFormats.filter { it.frameRate == f }
    }
    val availableCodecs = remember(fpsFormats) {
        FormatQualityRegistry.sortVideoCodecs(fpsFormats.map { it.codec }.distinct())
    }

    val maxPresets = remember(maxCqiFilteredFormats) {
        VideoFormatQualityRank.ResolutionBucket.entries.mapNotNull { bucket ->
            VideoFormatQualityRank.pickBestForBucket(maxCqiFilteredFormats, bucket)?.let { bucket to it }
        }
    }

    // Auto-correct picker state when available options shrink
    val correctedResolution = if (pickedResolution != null && availableResolutions.none {
        it.width == pickedResolution!!.width && it.height == pickedResolution!!.height
    }) availableResolutions.firstOrNull() else pickedResolution
    if (correctedResolution != pickedResolution) pickedResolution = correctedResolution

    val correctedFps = if (pickedFps != null && pickedFps !in availableFps) availableFps.firstOrNull() else pickedFps
    if (correctedFps != pickedFps) pickedFps = correctedFps

    val correctedCodec = if (pickedCodec != null && pickedCodec !in availableCodecs) availableCodecs.firstOrNull() else pickedCodec
    if (correctedCodec != pickedCodec) pickedCodec = correctedCodec

    // Final matching format
    val resolvedFormat = fpsFormats.firstOrNull { it.codec == pickedCodec } ?: fpsFormats.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = null,
                        tint = PnsColors.PhotoOrange,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Video Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
                // Confirm button — only active when a complete selection is possible
                if (resolvedFormat != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PnsColors.PhotoOrange.copy(alpha = 0.15f))
                            .border(1.dp, PnsColors.PhotoOrange.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
                            .clickable {
                                patchChrome { it.copy(inAppVideoColorSpaceOrdinal = pickedColorOrdinal) }
                                onSelect(resolvedFormat)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Apply",
                            style = MaterialTheme.typography.labelMedium,
                            color = PnsColors.PhotoOrange,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            if (pickerLists8k && (mcCaps?.supports8k != true || !halSupports8kCapture)) {
                Text(
                    text =
                        "8K is not available on this device (encoder max ${mcCaps?.maxFps8k ?: 0} fps @ 8K; " +
                            "HAL capture=${if (halSupports8kCapture) "yes" else "no"}). Choose 4K or lower.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PnsColors.PhotoOrange.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            if (filteredFormats.isEmpty() && !rawVideoAvailable) {
                Text(
                    text =
                        "No video formats on this device for the selected color space. " +
                            "Choose Rec.709 SDR below or dismiss.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {

                // ── Max presets (highest quality per resolution bucket) ───────
                if (maxPresets.isNotEmpty()) {
                    item {
                        PickerSectionHeader(
                            step = "★",
                            label = "Max quality presets",
                            selected = maxCqiColorSpace?.displayName,
                        )
                    }
                    items(items = maxPresets, key = { "max_${it.first.name}" }) { (bucket, format) ->
                        val desc = FormatQualityRegistry.forVideoCodec(format.codec)
                        PickerOptionRow(
                            label = bucket.label,
                            sublabel =
                                buildString {
                                    append(format.getLabel())
                                    append(" · ")
                                    append(format.frameRate)
                                    append(" fps")
                                    desc?.let {
                                        append(" · ")
                                        append(it.bitDepthLabel)
                                    }
                                },
                            rightLabel = format.getBitrateLabel(),
                            isSelected = resolvedFormat == format,
                            accentColor = PnsColors.PhotoOrange,
                        ) {
                            pickedColorOrdinal = maxColorOrdinal
                            patchChrome { it.copy(inAppVideoColorSpaceOrdinal = maxColorOrdinal) }
                            pickedAspect = videoAspectRatio(format.resolution.width, format.resolution.height)
                            pickedResolution = format.resolution
                            pickedFps = format.frameRate
                            pickedCodec = format.codec
                        }
                    }
                }

                // ── Step 0: Color space (CQI) ─────────────────────────────────
                item {
                    PickerSectionHeader(
                        step = "0",
                        label = "Color space",
                        selected = pickedColorSpace?.displayName,
                    )
                }
                items(items = colorSpaces, key = { "cspace_${it.id}" }) { space ->
                    val idx = colorSpaces.indexOf(space)
                    val isSel = idx == pickedColorOrdinal
                    PickerOptionRow(
                        label = space.displayName,
                        sublabel = "Filters codecs below",
                        isSelected = isSel,
                        accentColor = PnsColors.PhotoOrange,
                    ) {
                        pickedColorOrdinal = idx
                        patchChrome { it.copy(inAppVideoColorSpaceOrdinal = idx) }
                        pickedResolution = null
                        pickedFps = null
                        pickedCodec = null
                    }
                }

                if (rawVideoAvailable) {
                    item {
                        PickerSectionHeader(
                            step = "R",
                            label = "RAW video",
                            selected = if (rawVideoSelected) ".mcraw" else null,
                        )
                    }
                    item {
                        PickerOptionRow(
                            label = "RAW · .mcraw",
                            sublabel = "Sensor dump lane (matrix-gated)",
                            isSelected = rawVideoSelected,
                            accentColor = Color(0xFF80DEEA),
                            badge = "RAW",
                        ) {
                            onSelectRawVideo()
                            onDismiss()
                        }
                    }
                }

                // ── Step 1: Aspect Ratio ──────────────────────────────────────
                item {
                    PickerSectionHeader(
                        step = "1",
                        label = "Aspect Ratio",
                        selected = pickedAspect?.label,
                    )
                }
                items(items = allAspects, key = { "asp_${it.label}" }) { aspect ->
                    val isSel = aspect.label == pickedAspect?.label
                    PickerOptionRow(
                        label = aspect.label,
                        isSelected = isSel,
                        accentColor = Color.White,
                    ) {
                        pickedAspect = aspect
                        pickedResolution = null; pickedFps = null; pickedCodec = null
                    }
                }

                // ── Step 2: Resolution ────────────────────────────────────────
                item { PickerSectionHeader(step = "2", label = "Resolution", selected = pickedResolution?.let { resolutionShortLabel(it.width, it.height) }) }
                items(items = availableResolutions, key = { "res_${it.width}x${it.height}" }) { res ->
                    val isSel = pickedResolution?.width == res.width && pickedResolution?.height == res.height
                    PickerOptionRow(
                        label = resolutionLabel(res.width, res.height),
                        isSelected = isSel,
                        accentColor = Color.White,
                    ) {
                        pickedResolution = res; pickedFps = null; pickedCodec = null
                    }
                }

                // ── Step 3: Frame Rate ────────────────────────────────────────
                item { PickerSectionHeader(step = "3", label = "Frame Rate", selected = pickedFps?.let { "${it} fps" }) }
                items(items = availableFps, key = { "fps_$it" }) { fps ->
                    val isSel = fps == pickedFps
                    val isHfr = fps >= 120
                    PickerOptionRow(
                        label = "${fps} fps${if (isHfr) " · HFR" else ""}",
                        isSelected = isSel,
                        accentColor = if (isHfr) VideoFormatColors.HfrAmber else Color.White,
                        badge = if (isHfr) "MediaCodec" else null,
                    ) {
                        pickedFps = fps; pickedCodec = null
                    }
                }

                // ── Step 4: Codec / File Format ───────────────────────────────
                item { PickerSectionHeader(step = "4", label = "File Format", selected = pickedCodec?.let { codecLabel(it) }) }
                items(items = availableCodecs, key = { "codec_${it.name}" }) { codec ->
                    val isSel = codec == pickedCodec
                    val fmt = fpsFormats.firstOrNull { it.codec == codec }
                    val desc = FormatQualityRegistry.forVideoCodec(codec)
                    val accent = when {
                        fmt?.isDcg == true -> VideoFormatColors.DcgPurple
                        fmt?.isTenBit == true -> VideoFormatColors.TenBitTeal
                        codec == VideoCodec.AV1 -> Color(0xFF82B1FF)
                        codec == VideoCodec.VP9 -> Color(0xFF80CBC4)
                        else -> Color.White
                    }
                    PickerOptionRow(
                        label = codecLabel(codec),
                        sublabel =
                            desc?.let { "${it.containerLabel} · ${it.compressionLabel}" }
                                ?: fmt?.getQualityHint(),
                        rightLabel = fmt?.getBitrateLabel() ?: desc?.bitrateMbpsHint?.let { "$it Mbps" },
                        isSelected = isSel,
                        accentColor = accent,
                        badge = when {
                            fmt?.isDcg == true -> "DCG"
                            fmt?.isTenBit == true -> "10-bit"
                            else -> null
                        },
                    ) {
                        pickedCodec = codec
                    }
                }

                // ── Audio (Sprint AS) ─────────────────────────────────────────
                item {
                    PickerSectionHeader(
                        step = "A",
                        label = "Audio",
                        selected =
                            buildString {
                                append(hud.videoAudioSourceEnum().label)
                                append(" · ")
                                if (chrome.audioHiFiCapture) append("Hi-Fi $hiFiKhzLabel ")
                                append("${if (chrome.audioWindNoiseReduction) "wind NS" else "wind off"}")
                            }.trim(),
                    )
                }
                items(items = VideoAudioSource.entries, key = { "audsrc_${it.name}" }) { source ->
                    val isSel = hud.videoAudioSourceEnum() == source
                    PickerOptionRow(
                        label = source.label,
                        isSelected = isSel,
                        accentColor = Color.White,
                    ) {
                        patchHud(hud.copy(videoAudioSource = source.storageId))
                    }
                }
                item {
                    VideoAudioSettingRow(
                        title =
                            if (hiFiMuxRateHz > 0) {
                                "Hi-Fi capture (${hiFiMuxRateHz / 1000} kHz)"
                            } else {
                                "Hi-Fi capture"
                            },
                        subtitle = "$hiFiKhzLabel / 256 kbps AAC",
                        checked = chrome.audioHiFiCapture,
                        onCheckedChange = { checked -> patchChrome { it.copy(audioHiFiCapture = checked) } },
                    )
                }
                item {
                    VideoAudioSettingRow(
                        title = "Wind noise reduction",
                        subtitle = "Noise suppressor on record path",
                        checked = chrome.audioWindNoiseReduction,
                        onCheckedChange = { checked -> patchChrome { it.copy(audioWindNoiseReduction = checked) } },
                    )
                }
                item {
                    VideoAudioSettingRow(
                        title = "Prefer external mic",
                        subtitle = "USB / wired / Bluetooth when connected",
                        checked = chrome.audioPreferExternalInput,
                        onCheckedChange = { checked -> patchChrome { it.copy(audioPreferExternalInput = checked) } },
                    )
                }
                item {
                    VideoAudioSettingRow(
                        title = "Light compression",
                        subtitle = "Soft-knee PCM before AAC encode",
                        checked = chrome.audioLightCompression,
                        onCheckedChange = { checked -> patchChrome { it.copy(audioLightCompression = checked) } },
                    )
                }
                item {
                    VideoAudioSettingRow(
                        title = "Duck other audio",
                        subtitle = "Lower music while recording (voiceover)",
                        checked = chrome.audioVoiceoverDucking,
                        onCheckedChange = { checked -> patchChrome { it.copy(audioVoiceoverDucking = checked) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoAudioSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showSwitch: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (showSwitch) {
                        Modifier.clickable { onCheckedChange(!checked) }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
            )
        }
        if (showSwitch) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun codecLabel(codec: VideoCodec): String = when (codec) {
    VideoCodec.H264 -> "H.264 AVC"
    VideoCodec.H265 -> "H.265 HEVC"
    VideoCodec.H265_10BIT -> "H.265 10-bit"
    VideoCodec.DCG -> "H.265 DCG HDR"
    VideoCodec.AV1 -> "AV1"
    VideoCodec.VP9 -> "VP9 WebM"
}

@Composable
private fun PickerSectionHeader(step: String, label: String, selected: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PnsColors.PhotoOrange.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(step, style = MaterialTheme.typography.labelSmall, color = PnsColors.PhotoOrange, fontSize = 10.sp)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.SemiBold,
        )
        if (selected != null) {
            Text(
                text = "→ $selected",
                style = MaterialTheme.typography.labelSmall,
                color = PnsColors.PhotoOrange.copy(alpha = 0.80f),
                fontSize = 11.sp,
            )
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun PickerOptionRow(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    sublabel: String? = null,
    rightLabel: String? = null,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) accentColor.copy(alpha = 0.10f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Spacer(Modifier.size(16.dp))
            }
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) accentColor else Color.White.copy(alpha = 0.88f),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.16f))
                                .border(0.5.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(badge, style = MaterialTheme.typography.labelSmall, color = accentColor, fontSize = 9.sp)
                        }
                    }
                }
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
        if (rightLabel != null) {
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * Bottom-tray FAB (left of shutter) to open [VideoFormatPickerSheet]. Same 52dp chrome as
 * [dev.pointandshoot.PreviewTrayPhotoVideoModeToggleFab] in `PreviewEngineScreen.kt`.
 */
@Composable
fun PreviewTrayVideoFormatFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = Color.White.copy(alpha = 0.88f)
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier =
            modifier
                .size(52.dp)
                .border(2.dp, ring, CircleShape)
                .semantics {
                    contentDescription = "Video format settings. Tap to change resolution, fps, and codec."
                },
        containerColor = PnsColors.RecordRed.copy(alpha = 0.88f),
        contentColor = Color.White.copy(alpha = 0.92f),
        shape = CircleShape,
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Legacy compact chip (readout row) — prefer [PreviewTrayVideoFormatFab] on the shutter tray.
 */
@Composable
fun VideoFormatChip(
    selectedFormat: VideoFormat?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (selectedFormat != null) {
        val aspect = videoAspectRatio(selectedFormat.resolution.width, selectedFormat.resolution.height).label
        val res = resolutionShortLabel(selectedFormat.resolution.width, selectedFormat.resolution.height)
        val hsHint =
            selectedFormat.hfrCaptureSize?.let { cap ->
                " · ${cap.width}x${cap.height} HS"
            }.orEmpty()
        "$aspect · $res · ${selectedFormat.frameRate}fps · ${selectedFormat.getLabel()}$hsHint"
    } else {
        "Video Settings"
    }
    val isHfr = (selectedFormat?.frameRate ?: 0) >= 120
    val borderColor = when {
        selectedFormat?.isDcg == true -> VideoFormatColors.DcgPurple
        selectedFormat?.isTenBit == true -> VideoFormatColors.TenBitTeal
        isHfr -> VideoFormatColors.HfrAmber
        else -> Color.White.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = "Video format: $label. Tap to change." },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Videocam,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

private object VideoFormatColors {
    val HfrAmber = Color(0xFFFFC107)
    val TenBitTeal = Color(0xFF26C6DA)
    val DcgPurple = Color(0xFFCE93D8)
}
