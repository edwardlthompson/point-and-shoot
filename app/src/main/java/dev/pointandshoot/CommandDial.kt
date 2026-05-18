package dev.pointandshoot

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Command-dial modes from BUILD_PLAN §5 (Phase 2): **A**uto / **M**anual /
 * **H**ighlight / **S**nap (Ricoh GR-style fixed focus) / **BKT** (bracket).
 *
 * Sprint 13.6: Added **Macro** mode for close-up photography (<10cm focus).
 * Sprint 13.18: Added **Night** and **Bokeh** OEM ISP modes (CameraX Extensions).
 *   These are hidden in the dial when [CameraXExtensionProbe] reports them unavailable
 *   for the selected camera (typical on LineageOS / AOSP where OEM HAL extensions are stripped).
 *
 * Each mode is a deterministic capture program; the engine reads the active
 * mode and reconfigures `CaptureRequest.Builder` accordingly. Defaults are
 * documented per spec and intentionally narrow.
 */
enum class CommandDialMode(val label: String, val description: String) {
    Auto("A", "Auto: continuous AE/AF — standard point-and-shoot behavior"),
    M("M", "Manual: full ISO / shutter / focus control"),
    H("H", "Highlight: underexpose for bright peaks (sky / sun disk) — save-the-highlights"),
    S("S", "Snap: street preset — AF at infinity (tap preview to refocus)"),
    BKT("BKT", "Bracket: 3 / 5 / 7 RAW12 sequence with GroupingID"),
    Macro("MACRO", "Macro: close-up focus for subjects <10cm"),
    Night("NIGHT", "Night: OEM multi-frame stacking for low light (requires OEM extension support)"),
    Bokeh("BOKEH", "Bokeh: OEM portrait with hardware background blur (requires OEM extension support)"),
}

/**
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
    val visibleModes = CommandDialMode.entries.filter { mode ->
        when (mode) {
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
