package dev.pointandshoot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Preview chrome / readout dropdown shell: dark surface, rounded corners, optional title row.
 * Use with [PnsChromeMenuItem] or [PnsChromePlainMenuItem] for consistent quick-setting menus.
 */
@Composable
fun PnsChromeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 176.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = Color.Black.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.92f),
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
        }
        content()
    }
}

@Composable
fun PnsChromeMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val itemColors =
        MenuDefaults.itemColors(
            textColor = Color.White.copy(alpha = 0.92f),
            leadingIconColor = PnsColors.PhotoOrange,
            disabledTextColor = Color.White.copy(alpha = 0.38f),
            disabledLeadingIconColor = Color.White.copy(alpha = 0.28f),
        )
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (enabled) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.38f),
            )
        },
        onClick = onClick,
        enabled = enabled,
        colors = itemColors,
        leadingIcon = {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = PnsColors.PhotoOrange,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}

@Composable
fun PnsChromePlainMenuItem(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val itemColors =
        MenuDefaults.itemColors(
            textColor = Color.White.copy(alpha = 0.92f),
            leadingIconColor = PnsColors.PhotoOrange,
            disabledTextColor = Color.White.copy(alpha = 0.38f),
            disabledLeadingIconColor = Color.White.copy(alpha = 0.28f),
        )
    val labelColor =
        when {
            !enabled -> Color.White.copy(alpha = 0.38f)
            selected -> PnsColors.PhotoOrange.copy(alpha = 0.98f)
            else -> Color.White.copy(alpha = 0.92f)
        }
    DropdownMenuItem(
        text = {
            Text(label, color = labelColor)
        },
        onClick = onClick,
        enabled = enabled,
        colors = itemColors,
        leadingIcon = {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = PnsColors.PhotoOrange,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}

@Composable
fun PnsChromeDetailMenuItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val itemColors =
        MenuDefaults.itemColors(
            textColor = Color.White.copy(alpha = 0.92f),
            leadingIconColor = PnsColors.PhotoOrange,
            disabledTextColor = Color.White.copy(alpha = 0.38f),
            disabledLeadingIconColor = Color.White.copy(alpha = 0.28f),
        )
    val titleColor =
        when {
            !enabled -> Color.White.copy(alpha = 0.38f)
            selected -> PnsColors.PhotoOrange.copy(alpha = 0.98f)
            else -> Color.White.copy(alpha = 0.92f)
        }
    val subtitleColor =
        when {
            !enabled -> Color.White.copy(alpha = 0.28f)
            selected -> PnsColors.PhotoOrange.copy(alpha = 0.72f)
            else -> Color.White.copy(alpha = 0.62f)
        }
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    title,
                    color = titleColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    color = subtitleColor,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        onClick = onClick,
        enabled = enabled,
        colors = itemColors,
        leadingIcon = {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = PnsColors.PhotoOrange,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}
