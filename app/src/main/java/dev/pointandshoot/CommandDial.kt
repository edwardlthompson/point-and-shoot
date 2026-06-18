package dev.pointandshoot

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Command-dial modes: see [CommandDialMode] in `:pns-core`.
 *
 * Segmented rotary command dial. Compose-only and theme-driven so it can sit
 * directly on top of the live preview without further wiring.
 *
 * Visual feedback per Part 4: selected segment uses [PnsColors.PhotoOrange]
 * (Hasselblad orange) for *photo* dial states (A/M/H/S/BKT). The video tally
 * uses [PnsColors.RecordRed] separately (`VideoTallyOverlay`).
 *
 * Sprint 13.18: [Night] and [Bokeh] segments are hidden when [CameraXExtensionProbe]
 * reports them unavailable for [selectedCameraId], keeping the dial uncluttered on
 * LineageOS/AOSP devices where OEM extensions are not present.
 */
@Composable
fun CommandDial(
    selected: CommandDialMode,
    onSelect: (CommandDialMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedCameraId: String? = null,
) {
    val appCtx = LocalContext.current.applicationContext
    val hasDedicatedMonochrome =
        remember(appCtx) { hasDedicatedMonochromeCamera(appCtx) }
    val visibleModes = CommandDialMode.entries.filter { mode ->
        when (mode) {
            CommandDialMode.Qr, CommandDialMode.Dual -> false
            CommandDialMode.Monochrome -> hasDedicatedMonochrome
            CommandDialMode.Night ->
                CameraXExtensionProbe.isAvailable(
                    selectedCameraId ?: "0",
                    androidx.camera.extensions.ExtensionMode.NIGHT,
                )
            CommandDialMode.Bokeh ->
                CameraXExtensionProbe.isAvailable(
                    selectedCameraId ?: "0",
                    androidx.camera.extensions.ExtensionMode.BOKEH,
                )
            else -> true
        }
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(4.dp)
            .semantics {
                contentDescription = "Command dial; current mode ${selected.label} (${selected.description})"
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (mode in visibleModes) {
            DialSegment(
                mode = mode,
                isSelected = mode == selected,
                onSelect = onSelect,
                enabled = enabled,
            )
        }
    }
}

private fun hasDedicatedMonochromeCamera(context: Context): Boolean {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    for (cameraId in cm.cameraIdList) {
        val chars = runCatching { cm.getCameraCharacteristics(cameraId) }.getOrNull() ?: continue
        if (isDedicatedMonochromeCamera(chars)) return true
    }
    return false
}

@Composable
private fun DialSegment(
    mode: CommandDialMode,
    isSelected: Boolean,
    onSelect: (CommandDialMode) -> Unit,
    enabled: Boolean,
) {
    val bg = when {
        !enabled -> Color.White.copy(alpha = 0.05f)
        isSelected -> PnsColors.PhotoOrange
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        isSelected -> Color.Black
        else -> Color.White.copy(alpha = 0.85f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled) { onSelect(mode) }
            .semantics {
                role = Role.Tab
                contentDescription = "${mode.label} mode (${mode.description})"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = mode.label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 14.sp,
        )
    }
}
