package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared chrome for in-preview **Settings** popups, quick-setting sheets, and related menus.
 *
 * Canonical spec: [docs/preview-chrome-settings-style-guide.md]
 */
object PreviewChromeMenuColors {
    val dialogSurface = Color(0xFF1A1A1A)
    val introText = Color.White.copy(alpha = 0.65f)
    val bodySecondary = Color.White.copy(alpha = 0.62f)
    val menuCardFill = Color.White.copy(alpha = 0.08f)
    val insetPanelFill = Color.Black.copy(alpha = 0.45f)
    val divider = Color.White.copy(alpha = 0.15f)
}

@Composable
fun ChromeSettingsIntroText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = PreviewChromeMenuColors.introText,
    )
}

@Composable
fun PreviewRailSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = PnsColors.PhotoOrange,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
fun PreviewRailSettingToggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    settingKey: String? = null,
    highlightFlash: SettingHighlightFlashState? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .then(
                    highlightFlash?.applyHighlight(Modifier, settingKey) ?: Modifier,
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PreviewChromeMenuColors.bodySecondary,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun RailSettingsMenuEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = PreviewChromeMenuColors.menuCardFill),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PreviewChromeMenuColors.introText,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

/** Inset panel for monospace / probe blocks inside settings sheets. */
@Composable
fun ChromeInsetPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PreviewChromeMenuColors.insetPanelFill)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
fun ChromeMonospaceBlock(text: String) {
    ChromeInsetPanel {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/**
 * Quick-setting chip — grid focal tiles, FPS targets, crop/grid presets, and sheet actions.
 * Matches [IconCubeVectorButton] chrome family ([PnsIcons]).
 */
@Composable
fun FpsQuickChip(
    label: String,
    selected: Boolean,
    requiresRoot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxTile: Boolean = false,
    subLabel: String? = null,
    contentDescription: String? = null,
) {
    val borderColor =
        when {
            !enabled -> Color.White.copy(alpha = 0.12f)
            selected -> PnsColors.PhotoOrange
            requiresRoot -> PnsColors.RootAccentBlue
            else -> Color.White.copy(alpha = 0.35f)
        }
    val bg =
        when {
            !enabled -> Color.Black.copy(alpha = 0.25f)
            selected -> PnsColors.PhotoOrange
            else -> Color.Black.copy(alpha = 0.45f)
        }
    val fg =
        when {
            !enabled -> Color.White.copy(alpha = 0.35f)
            selected -> Color.Black
            requiresRoot && !selected -> PnsColors.RootAccentBlue
            else -> Color.White.copy(alpha = 0.92f)
        }
    Box(
        modifier =
            modifier
                .then(
                    if (fillMaxTile) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.height(44.dp).widthIn(min = PnsDimens.quickSettingsChipMinWidth)
                    },
                )
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                )
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .background(bg)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (subLabel != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label,
                    color = fg,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    subLabel,
                    color = fg.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Text(
                label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
