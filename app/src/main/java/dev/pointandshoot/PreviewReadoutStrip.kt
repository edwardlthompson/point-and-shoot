package dev.pointandshoot

import android.hardware.camera2.CaptureResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Shared formatting for on-screen readout + ChromeUx logs (Milestone 9). */
object PreviewReadoutFormat {
    fun formatShutter(exposureNs: Long?): String {
        if (exposureNs == null || exposureNs <= 0L) return "—"
        val sec = exposureNs / 1_000_000_000.0
        if (sec >= 1.0) return "%.1fs".format(sec)
        val inv = (1_000_000_000.0 / exposureNs.toDouble()).roundToInt().coerceAtLeast(1)
        return "1/$inv"
    }

    fun awbModeLabel(mode: Int?): String {
        if (mode == null) return "—"
        return when (mode) {
            CaptureResult.CONTROL_AWB_MODE_OFF -> "OFF"
            CaptureResult.CONTROL_AWB_MODE_AUTO -> "AWB"
            CaptureResult.CONTROL_AWB_MODE_INCANDESCENT -> "INC"
            CaptureResult.CONTROL_AWB_MODE_FLUORESCENT -> "FL"
            CaptureResult.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "WFL"
            CaptureResult.CONTROL_AWB_MODE_DAYLIGHT -> "DY"
            CaptureResult.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "CLD"
            CaptureResult.CONTROL_AWB_MODE_TWILIGHT -> "TWI"
            CaptureResult.CONTROL_AWB_MODE_SHADE -> "SHD"
            else -> "?$mode"
        }
    }
}

/**
 * Thin exposure readout above the chrome rails; counter-rotates with device UI rotation like other
 * Sony-style chrome (preview buffer stays fixed).
 */
@Composable
fun PreviewReadoutStrip(
    iso: Int?,
    exposureNs: Long?,
    awbMode: Int?,
    measuredFps: Double,
    uiRotationDeg: Float,
    /** Preferred still pipeline: DNG-only (`false`) vs RAW+JPEG companion (`true`). Persists with preview chrome prefs. */
    stillCaptureJpegCompanion: Boolean,
    onStillCaptureJpegCompanionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isoText = iso?.toString() ?: "—"
    val ss = PreviewReadoutFormat.formatShutter(exposureNs)
    val awb = PreviewReadoutFormat.awbModeLabel(awbMode)
    val fpsText =
        if (measuredFps > 0.05) {
            "%.1f".format(measuredFps)
        } else {
            "—"
        }
    val line = "ISO $isoText   $ss   $awb   ${fpsText}fps"
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PreviewReadoutStripHeight)
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .graphicsLayer {
                    rotationZ = uiRotationDeg
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = line,
            modifier = Modifier.weight(1f, fill = true),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.92f),
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        RawStillPipelineToggle(
            jpegCompanion = stillCaptureJpegCompanion,
            onChange = onStillCaptureJpegCompanionChange,
        )
    }
}

val PreviewReadoutStripHeight = 34.dp

@Composable
private fun RawStillPipelineToggle(
    jpegCompanion: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val divider = Color.White.copy(alpha = 0.28f)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .border(1.dp, Color.White.copy(alpha = 0.38f), shape)
                .background(Color.Black.copy(alpha = 0.62f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RawStillPipelineToggleSegment(
            label = "RAW",
            selected = !jpegCompanion,
            onClick = { onChange(false) },
            semanticsLabel = "Still capture RAW only",
        )
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(divider),
        )
        RawStillPipelineToggleSegment(
            label = "RAW+",
            selected = jpegCompanion,
            onClick = { onChange(true) },
            semanticsLabel = "Still capture RAW plus JPEG companion",
        )
    }
}

@Composable
private fun RawStillPipelineToggleSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    semanticsLabel: String,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .semantics { contentDescription = semanticsLabel }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ).background(
                    if (selected) Color.White.copy(alpha = 0.24f) else Color.Transparent,
                ).padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = Color.White.copy(alpha = if (selected) 0.98f else 0.72f),
            maxLines = 1,
        )
    }
}
