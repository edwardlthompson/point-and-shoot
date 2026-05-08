package dev.pointandshoot

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlinx.coroutines.delay

/**
 * Sony-style timecode HUD per BUILD_PLAN §5 (Phase 2): `HH:MM:SS:FF`.
 *
 *   * Frames field (`FF`) wraps at the active recording fps.
 *   * Always monospaced via [MonoFamily] so digits don't jitter as values change.
 *   * Recording state shows a leading red dot in [PnsColors.RecordRed]; standby
 *     state shows a hollow dot.
 */
@Composable
fun TimecodeOverlay(
    isRecording: Boolean,
    fps: Int,
    modifier: Modifier = Modifier,
    startedElapsedMs: Long? = null,
) {
    val safeFps = max(1, fps)

    // Anchor the elapsed counter to the moment the caller transitioned into
    // recording. If the caller doesn't pass one, anchor at this composition
    // and reset whenever recording flips.
    val anchorMs = remember(isRecording, startedElapsedMs) {
        if (!isRecording) 0L
        else startedElapsedMs ?: SystemClock.elapsedRealtime()
    }

    var nowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(isRecording, anchorMs, safeFps) {
        if (!isRecording) {
            nowMs = anchorMs
            return@LaunchedEffect
        }
        // Update at frame cadence so FF advances cleanly. Cap at 60Hz to keep
        // recomposition cost bounded; FF still derives from real time, so the
        // displayed frame count remains accurate.
        val periodMs = (1000L / safeFps).coerceAtLeast(16L)
        while (true) {
            nowMs = SystemClock.elapsedRealtime()
            delay(periodMs)
        }
    }

    val elapsedMs = (nowMs - anchorMs).coerceAtLeast(0L)
    val tc = formatTimecode(elapsedMs, safeFps)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecDot(active = isRecording)
        Text(
            text = "  $tc",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isRecording) Color.White else Color.White.copy(alpha = 0.65f),
        )
    }
}

@Composable
private fun RecDot(active: Boolean) {
    val color = if (active) PnsColors.RecordRed else Color.White.copy(alpha = 0.45f)
    Text(
        text = if (active) "\u25CF" else "\u25CB", // filled vs hollow circle
        color = color,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * Format [elapsedMs] as `HH:MM:SS:FF` where `FF` wraps at [fps].
 * Public so tests / probes can format raw timestamps the same way the HUD does.
 *
 * Defensive against bad inputs:
 *   * Negative `elapsedMs` clamps to zero (never returns a `-12` field).
 *   * Non-positive `fps` clamps to 1 so the `FF` field is always a valid 0..N range.
 */
fun formatTimecode(elapsedMs: Long, fps: Int): String {
    val safeFps = max(1, fps)
    val safeElapsed = elapsedMs.coerceAtLeast(0L)
    val totalSeconds = safeElapsed / 1000L
    val hh = totalSeconds / 3600L
    val mm = (totalSeconds % 3600L) / 60L
    val ss = totalSeconds % 60L
    val frameInSecond = ((safeElapsed % 1000L) * safeFps) / 1000L
    return "%02d:%02d:%02d:%02d".format(hh, mm, ss, frameInSecond)
}
