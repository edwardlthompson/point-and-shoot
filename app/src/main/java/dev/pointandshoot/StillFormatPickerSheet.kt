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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * M19.6 — still export format + color-space picker (CQI-ranked color step + export scaffolds).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StillFormatPickerSheet(
    chrome: PreviewChromePreferences,
    composedStillIntent: ComposedStillIntent,
    selectedExportKind: StillExportKind?,
    onApply: (PreviewChromePreferences, ComposedStillIntent, StillExportKind?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorSpaces = remember { ColorQualityIndex.stillSpacesForPicker() }
    var pickedColorOrdinal by remember(chrome.stillColorSpaceOrdinal) {
        mutableIntStateOf(chrome.stillColorSpaceOrdinal.coerceAtLeast(0))
    }
    val pickedColor = colorSpaces.getOrNull(pickedColorOrdinal)?.first
    val rawChoices = remember(pickedColor) { StillPhotoPickerMatrix.allowedRawFormats(pickedColor) }
    var pickedRawFormat by remember(composedStillIntent.raw, pickedColorOrdinal) {
        mutableStateOf(
            rawChoices.firstOrNull { it.tier == composedStillIntent.raw } ?: rawChoices.first(),
        )
    }
    if (pickedRawFormat !in rawChoices) {
        pickedRawFormat = rawChoices.first()
    }
    val compressedChoices = remember(pickedColor) { StillPhotoPickerMatrix.allowedCompressedFormats(pickedColor) }
    val resolutionModes =
        remember {
            listOf(PhotoResolutionMode.MaxResolution, PhotoResolutionMode.Binned) +
                PhotoResolutionMode.entries.filterNot {
                    it == PhotoResolutionMode.MaxResolution || it == PhotoResolutionMode.Binned
                }
        }
    var pickedResolutionMode by remember(composedStillIntent.photoResolutionMode) {
        mutableStateOf(composedStillIntent.photoResolutionMode)
    }
    var pickedCompressedFormat by remember(selectedExportKind, composedStillIntent.jpeg, pickedColorOrdinal) {
        mutableStateOf(
            compressedChoices.firstOrNull { it.exportKind == selectedExportKind } ?:
                compressedChoices.firstOrNull { it.tier == composedStillIntent.jpeg } ?:
                compressedChoices.first(),
        )
    }
    if (pickedCompressedFormat !in compressedChoices) {
        pickedCompressedFormat = compressedChoices.first()
    }
    val maxColorOrdinal =
        remember(colorSpaces) {
            colorSpaces
                .withIndex()
                .maxByOrNull { it.value.second }
                ?.index
                ?: 0
        }
    fun applySelection(
        colorOrdinal: Int = pickedColorOrdinal,
        rawFormat: RawFormatOption = pickedRawFormat,
        compressedFormat: CompressedFormatOption = pickedCompressedFormat,
        resolutionMode: PhotoResolutionMode = pickedResolutionMode,
    ) {
        val nextChrome = chrome.copy(stillColorSpaceOrdinal = colorOrdinal)
        val selectedColor = colorSpaces.getOrNull(colorOrdinal)?.first
        val rawTier = rawFormat.tier
        val compressedTier = compressedFormat.tier
        val nextIntent =
            ComposedStillIntent(
                raw = rawTier,
                jpeg = compressedTier,
                hdrWhenJpegOff =
                    if (rawTier == ImgMenuTier.Ultra || compressedTier == ImgMenuTier.Ultra) {
                        ImgMenuTier.Ultra
                    } else {
                        ImgMenuTier.Standard
                    },
                photoResolutionMode = resolutionMode,
            ).coerceForStillColorSpace(selectedColor)
        val resolvedExportKind =
            when {
                compressedFormat.exportKind != null &&
                    nextIntent.jpeg != ImgMenuTier.Off &&
                    StillExportScaffolds.supportsColorSpace(compressedFormat.exportKind, selectedColor) ->
                    compressedFormat.exportKind
                nextIntent.raw != ImgMenuTier.Off && nextIntent.jpeg == ImgMenuTier.Off -> StillExportKind.Dng
                else -> null
            }
        onApply(
            nextChrome.copy(stillExportKindOrdinal = StillExportScaffolds.toOrdinal(resolvedExportKind)),
            nextIntent,
            resolvedExportKind,
        )
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
        contentColor = Color.White,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Photo,
                        contentDescription = null,
                        tint = PnsColors.PhotoOrange,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Still export settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PnsColors.PhotoOrange.copy(alpha = 0.15f))
                            .border(1.dp, PnsColors.PhotoOrange.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
                            .clickable { applySelection() }
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
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                item {
                    StillPickerSectionHeader(
                        step = "★",
                        label = "Max quality preset",
                        selected = null,
                    )
                }
                item {
                    StillPickerOptionRow(
                        label = "MAX Photo",
                        sublabel = "Auto-pick highest CQI color space + best RAW/compressed formats",
                        isSelected =
                            pickedColorOrdinal == maxColorOrdinal &&
                                pickedRawFormat.tier == ImgMenuTier.Ultra &&
                                pickedCompressedFormat == StillPhotoPickerMatrix.maxCompressedForColor(colorSpaces.getOrNull(maxColorOrdinal)?.first) &&
                                pickedResolutionMode == PhotoResolutionMode.MaxResolution,
                        enabled = true,
                    ) {
                        applySelection(
                            colorOrdinal = maxColorOrdinal,
                            rawFormat = StillPhotoPickerMatrix.allowedRawFormats(colorSpaces.getOrNull(maxColorOrdinal)?.first).first(),
                            compressedFormat = StillPhotoPickerMatrix.maxCompressedForColor(colorSpaces.getOrNull(maxColorOrdinal)?.first),
                            resolutionMode = PhotoResolutionMode.MaxResolution,
                        )
                    }
                }

                item {
                    StillPickerSectionHeader(
                        step = "C",
                        label = "Color space",
                        selected = pickedColor?.displayName,
                    )
                }
                items(items = colorSpaces, key = { "cqi_${it.first.name}" }) { (target, cqi) ->
                    val idx = colorSpaces.indexOfFirst { it.first == target }
                    val isSel = idx == pickedColorOrdinal
                    StillPickerOptionRow(
                        label = ColorQualityIndex.label(target.displayName, cqi),
                        sublabel = "Determines RAW/compressed choices below",
                        isSelected = isSel,
                        enabled = true,
                    ) {
                        pickedColorOrdinal = idx
                    }
                }

                item {
                    StillPickerSectionHeader(
                        step = "S",
                        label = "Sensor resolution",
                        selected = pickedResolutionMode.label,
                    )
                }
                items(items = resolutionModes, key = { "res_${it.name}" }) { mode ->
                    val selected = mode == pickedResolutionMode
                    val sublabel =
                        when (mode) {
                            PhotoResolutionMode.Binned ->
                                "Default binned stream dimensions (faster captures)"
                            PhotoResolutionMode.MaxResolution ->
                                "Maximum-resolution stream when supported (falls back safely)"
                        }
                    StillPickerOptionRow(
                        label = mode.label,
                        sublabel = sublabel,
                        isSelected = selected,
                        enabled = true,
                    ) {
                        pickedResolutionMode = mode
                    }
                }

                item {
                    StillPickerSectionHeader(
                        step = "R",
                        label = "RAW format",
                        selected = pickedRawFormat.label,
                    )
                }
                items(items = rawChoices, key = { "raw_${it.name}" }) { option ->
                    val selected = option == pickedRawFormat
                    StillPickerOptionRow(
                        label = option.label,
                        sublabel = option.bitDepthLabel,
                        isSelected = selected,
                        enabled = true,
                    ) {
                        pickedRawFormat = option
                    }
                }

                item {
                    StillPickerSectionHeader(
                        step = "J",
                        label = "Compressed format",
                        selected = pickedCompressedFormat.label,
                    )
                }
                items(items = compressedChoices, key = { "jpeg_${it.name}" }) { option ->
                    val selected = option == pickedCompressedFormat
                    StillPickerOptionRow(
                        label = option.label,
                        sublabel = option.bitDepthLabel,
                        isSelected = selected,
                        enabled = true,
                    ) {
                        pickedCompressedFormat = option
                    }
                }
            }
        }
    }
}

@Composable
private fun StillPickerSectionHeader(step: String, label: String, selected: String?) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
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
private fun StillPickerOptionRow(
    label: String,
    sublabel: String?,
    isSelected: Boolean,
    enabled: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val accent = if (enabled) PnsColors.PhotoOrange else Color.White.copy(alpha = 0.35f)
    val bg = if (isSelected) accent.copy(alpha = 0.10f) else Color.Transparent
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bg)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelected && enabled) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        } else {
            Spacer(Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) Color.White.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.45f),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (badge != null) {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(badge, style = MaterialTheme.typography.labelSmall, color = accent, fontSize = 9.sp)
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
}

/** Photo tray FAB at the same gallery-adjacent slot as video format FAB. */
@Composable
fun PreviewTrayStillFormatFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = Color.White.copy(alpha = 0.88f)
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier =
            modifier
                .size(52.dp)
                .border(2.dp, ring, RoundedCornerShape(percent = 50))
                .semantics {
                    contentDescription = "Still format settings. Tap to choose color space, RAW format, and compressed format."
                },
        containerColor = PnsColors.PhotoOrange.copy(alpha = 0.88f),
        contentColor = Color.Black.copy(alpha = 0.92f),
        shape = RoundedCornerShape(percent = 50),
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
        )
    }
}
