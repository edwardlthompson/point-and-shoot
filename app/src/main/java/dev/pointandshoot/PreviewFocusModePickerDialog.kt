package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun PreviewFocusModePickerDialog(
    onDismiss: () -> Unit,
    menuSelections: List<PreviewFocusSelection>,
    current: PreviewFocusSelection,
    onPick: (PreviewFocusSelection) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A1A),
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier
                    .padding(12.dp)
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Focus mode",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Text(
                    "AF modes from this camera HAL. Auto restores continuous picture AF when available. " +
                        "Manual distance: drag horizontally on the finder (avoids vertical swipes for front/rear camera). " +
                        "Macro AF locks the ultra-wide lens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                for (opt in menuSelections) {
                    val label =
                        when (opt) {
                            PreviewFocusSelection.Auto -> "Auto (continuous AF)"
                            PreviewFocusSelection.ManualDistance -> "Manual distance"
                            is PreviewFocusSelection.HalAf -> PreviewFocusMode.afModeMenuLabel(opt.mode)
                        }
                    FocusModeOptionRow(
                        label = label,
                        selected = opt == current,
                        onClick = { onPick(opt) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusModeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor =
        if (selected) PnsColors.PhotoOrange else Color.White.copy(alpha = 0.35f)
    val bg =
        if (selected) PnsColors.PhotoOrange else Color.Black.copy(alpha = 0.45f)
    val fg =
        if (selected) Color.Black else Color.White.copy(alpha = 0.92f)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, borderColor, shape)
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}
