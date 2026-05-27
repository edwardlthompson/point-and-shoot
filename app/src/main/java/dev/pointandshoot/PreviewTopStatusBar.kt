package dev.pointandshoot

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Sprint **14.2** — timecode, audio meters, and transient status in the top inset band
 * (cutout-safe horizontal padding; keep center clear for punch-hole / selfie camera).
 */
@Composable
fun PreviewTopStatusBar(
    statusLine: String?,
    showTimecode: Boolean,
    videoPrimary: Boolean,
    isRecording: Boolean,
    selectedFps: Int,
    recordStartElapsedMs: Long?,
    sampleAudioAmplitude: () -> Int,
    modifier: Modifier = Modifier,
) {
    val showTc = showTimecode && (videoPrimary || isRecording)
    val showMeters = isRecording && videoPrimary

    LaunchedEffect(showTc, showMeters, statusLine, isRecording, videoPrimary) {
        Log.i(
            "PNS.ChromeUx",
            "statusBar=visible recording=$isRecording videoPrimary=$videoPrimary " +
                "timecode=$showTc audioMeters=$showMeters status=${statusLine?.take(48) ?: ""}",
        )
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (!statusLine.isNullOrBlank()) {
            Text(
                text = statusLine,
                style = MaterialTheme.typography.labelSmall,
                color = PnsColors.PhotoOrange.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f, fill = true)
                        .padding(end = 8.dp),
            )
        } else {
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.weight(1f, fill = true),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTc) {
                TimecodeOverlay(
                    isRecording = isRecording,
                    fps = selectedFps,
                    startedElapsedMs = recordStartElapsedMs,
                )
            }
            if (showMeters) {
                PpmAudioMeter(
                    levelLinear = sampleAudioAmplitude().coerceIn(0, 32767) / 32767f,
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
}

/** Builds the single-line status message for [PreviewTopStatusBar]. */
fun previewStatusBarLine(
    capturePipelineHint: String?,
    focalMapCalibratingHint: Boolean,
    sessionStatus: String,
): String? =
    when {
        !capturePipelineHint.isNullOrBlank() -> capturePipelineHint
        focalMapCalibratingHint -> "Calibrating focal map…"
        sessionStatus.isNotBlank() && !sessionStatus.equals("Idle", ignoreCase = true) ->
            sessionStatus
        else -> null
    }
