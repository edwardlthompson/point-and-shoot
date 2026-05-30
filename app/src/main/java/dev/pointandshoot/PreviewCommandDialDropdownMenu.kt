package dev.pointandshoot

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sprint **14.3** — shooting-mode menu with **Photo programs** / **Video programs** section header
 * and [PnsColors.PhotoOrange] selected row (tray FAB unchanged per chrome lock).
 */
@Composable
fun PreviewCommandDialDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    primaryPhoto: Boolean,
    selectedMode: CommandDialMode,
    onModeSelected: (CommandDialMode) -> Unit,
    visibilityCtx: dev.pointandshoot.fleet.FleetUiVisibilityGate.VisibilityContext? = null,
    modifier: Modifier = Modifier,
) {
    val family = CaptureMediaFamily.fromPrimaryPhoto(primaryPhoto)
    val sectionTitle = CaptureMediaFamily.commandDialMenuSectionTitle(family)
    val allModes = CaptureMediaFamily.commandDialModesFor(family)
    val modes =
        visibilityCtx?.let { ctx ->
            dev.pointandshoot.fleet.FleetChromeVisibility.filterCommandDialModes(allModes, ctx)
        } ?: allModes

    LaunchedEffect(expanded, sectionTitle) {
        if (expanded) {
            Log.i(
                "PNS.ChromeUx",
                "modeDialPopout=menuSections header=$sectionTitle family=${family.name} modes=${modes.size}",
            )
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 288.dp),
    ) {
        Text(
            text = sectionTitle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            color = PnsColors.PhotoOrange.copy(alpha = 0.95f),
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider()
        modes.forEach { mode ->
            val selected = mode == selectedMode
            DropdownMenuItem(
                text = {
                    Text(
                        text = "${mode.label} — ${mode.description}",
                        color =
                            if (selected) {
                                PnsColors.PhotoOrange
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 2,
                    )
                },
                leadingIcon = {
                    Box(
                        modifier = Modifier.width(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = PnsColors.PhotoOrange,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                },
                onClick = { onModeSelected(mode) },
            )
        }
    }
}
