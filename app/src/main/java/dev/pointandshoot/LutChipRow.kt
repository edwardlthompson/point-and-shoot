@file:Suppress("FunctionNaming")

package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * HUD chip row for selecting the active still + video LUT per BUILD_PLAN
 * \u00a77 ("HUD chip 'LUT' alongside the imaging-profile selector; per-mode
 * memory; 'None' (identity) is always the default and survives app restart
 * unless the user explicitly chose otherwise").
 *
 * Two chips:
 *   * **STILL LUT** - choices restricted to [LutCatalog.Scope.Stills] +
 *     [LutCatalog.Scope.Both].
 *   * **VIDEO LUT** - choices restricted to [LutCatalog.Scope.Video] +
 *     [LutCatalog.Scope.Both].
 *
 * Selection persists immediately to [HudSettings] via the supplied
 * [HudSettingsState], so process death does not lose the user's intent.
 *
 * The picker is an [AlertDialog] (built into Material 3, no ModalBottomSheet
 * dependency) listing every available `LutCatalog` entry with its display
 * name + 1-line description + SPDX badge.
 */
@Composable
fun LutChipRow(
    state: HudSettingsState,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf<LutCatalog.Scope?>(null) }
    val settings = state.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LutChip(
            scopeLabel = "STILL LUT",
            lutScope = LutCatalog.Scope.Stills,
            current = settings.stillsLut(),
            onClick = { picking = LutCatalog.Scope.Stills },
        )
        LutChip(
            scopeLabel = "VIDEO LUT",
            lutScope = LutCatalog.Scope.Video,
            current = settings.videoLut(),
            onClick = { picking = LutCatalog.Scope.Video },
        )
    }

    val pickingScope = picking
    val context = LocalContext.current
    if (pickingScope != null) {
        LutPickerDialog(
            scope = pickingScope,
            currentSelectionName = when (pickingScope) {
                LutCatalog.Scope.Stills -> settings.selectedLutForStills
                LutCatalog.Scope.Video -> settings.selectedLutForVideo
                LutCatalog.Scope.Both -> settings.selectedLutForStills
            },
            importedNames = ImportedLutStore.list(context).map { it.name },
            onSelect = { entry ->
                PnsProductPrefs.setSelectedImportedLut(context, null)
                state.update(
                    when (pickingScope) {
                        LutCatalog.Scope.Stills -> settings.copy(selectedLutForStills = entry.name)
                        LutCatalog.Scope.Video -> settings.copy(selectedLutForVideo = entry.name)
                        LutCatalog.Scope.Both -> settings.copy(
                            selectedLutForStills = entry.name,
                            selectedLutForVideo = entry.name,
                        )
                    },
                )
                picking = null
            },
            onSelectImported = { fileName ->
                PnsProductPrefs.setSelectedImportedLut(context, fileName)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun LutChip(
    scopeLabel: String,
    lutScope: LutCatalog.Scope,
    current: LutCatalog,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = scopeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = PnsColors.PhotoOrange,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = current.indexInScope(lutScope).toString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun LutPickerDialog(
    scope: LutCatalog.Scope,
    currentSelectionName: String,
    importedNames: List<String> = emptyList(),
    onSelect: (LutCatalog) -> Unit,
    onSelectImported: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val choices = remember(scope) { LutCatalog.forScope(scope) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (scope) {
                    LutCatalog.Scope.Stills -> "Choose still LUT"
                    LutCatalog.Scope.Video -> "Choose video LUT"
                    LutCatalog.Scope.Both -> "Choose LUT"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                choices.forEach { entry ->
                    val isSelected = entry.name == currentSelectionName
                    val rowBg = if (isSelected) {
                        PnsColors.PhotoOrange.copy(alpha = 0.18f)
                    } else {
                        Color.Transparent
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(rowBg)
                            .clickable { onSelect(entry) }
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = entry.spdx,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                importedNames.forEach { name ->
                    Text(
                        text = "Imported · $name",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectImported(name) }
                                .padding(8.dp),
                        color = PnsColors.PhotoOrange,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(
                onClick = { onSelect(LutCatalog.None) },
            ) { Text("Reset to None") }
        },
    )
}
