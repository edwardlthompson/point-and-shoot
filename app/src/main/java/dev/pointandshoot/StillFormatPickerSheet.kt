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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onApply: (PreviewChromePreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorSpaces = remember { ColorQualityIndex.stillSpacesForPicker() }
    var pickedColorOrdinal by remember(chrome.stillColorSpaceOrdinal) {
        mutableIntStateOf(chrome.stillColorSpaceOrdinal.coerceAtLeast(0))
    }
    val pickedColor = colorSpaces.getOrNull(pickedColorOrdinal)?.first

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
                            .clickable {
                                onApply(chrome.copy(stillColorSpaceOrdinal = pickedColorOrdinal))
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
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
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
                        sublabel = "Export ICC / container tagging",
                        isSelected = isSel,
                        enabled = true,
                    ) {
                        pickedColorOrdinal = idx
                    }
                }

                item {
                    StillPickerSectionHeader(
                        step = "F",
                        label = "Export formats",
                        selected = null,
                    )
                }
                items(items = StillExportScaffolds.availableKinds(), key = { it.name }) { kind ->
                    val shipped = StillExportScaffolds.isEnabled(kind)
                    StillPickerOptionRow(
                        label = kind.label,
                        sublabel = StillExportScaffolds.statusLabel(kind),
                        isSelected = shipped && kind == StillExportKind.Jpeg,
                        enabled = shipped,
                        badge = if (shipped) "Shipped" else "Planned",
                    ) { }
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
