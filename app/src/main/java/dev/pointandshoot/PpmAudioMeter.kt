package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.log10
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Segment layout for [PpmAudioMeter]. */
enum class PpmMeterLayout {
    /** Top status bar — segments stack bottom → top. */
    Vertical,
    /** Pillar HUD (landscape record) — quiet left, loud right. */
    Horizontal,
}

/**
 * Sprint **15.20** — segmented PPM-style audio meter with falling peak.
 */
@Composable
fun PpmAudioMeter(
    levelLinear: Float,
    modifier: Modifier = Modifier,
    segmentCount: Int = 24,
    layout: PpmMeterLayout = PpmMeterLayout.Vertical,
    minDb: Float = -60f,
    peakDecayPerSecond: Float = 1.9f,
) {
    var peakHold by remember { mutableStateOf(0f) }
    val level = levelLinear.coerceIn(0f, 1f)
    if (level > peakHold) peakHold = level
    LaunchedEffect(level, peakHold, peakDecayPerSecond) {
        if (peakHold > level) {
            val stepSec = 0.033f
            delay((stepSec * 1000f).toLong())
            peakHold = max(level, peakHold - peakDecayPerSecond.coerceAtLeast(0.1f) * stepSec)
        }
    }
    val lit = litSegmentsForDb(level, segmentCount, minDb)
    val peakLit = litSegmentsForDb(peakHold, segmentCount, minDb)
    Canvas(
        modifier =
            when (layout) {
                PpmMeterLayout.Vertical -> modifier.fillMaxHeight()
                PpmMeterLayout.Horizontal -> modifier.fillMaxWidth().fillMaxHeight()
            },
    ) {
        when (layout) {
            PpmMeterLayout.Vertical -> {
                val segH = size.height / segmentCount
                for (i in 0 until segmentCount) {
                    val idx = segmentCount - 1 - i
                    val on = idx < lit || idx == peakLit - 1
                    drawPpmSegment(
                        on = on,
                        idx = idx,
                        segmentCount = segmentCount,
                        topLeft = Offset(0f, i * segH),
                        size = Size(size.width, segH - 1f),
                    )
                }
            }
            PpmMeterLayout.Horizontal -> {
                val segW = size.width / segmentCount
                for (i in 0 until segmentCount) {
                    val on = i < lit || i == peakLit - 1
                    drawPpmSegment(
                        on = on,
                        idx = i,
                        segmentCount = segmentCount,
                        topLeft = Offset(i * segW, 0f),
                        size = Size(segW - 1f, size.height),
                    )
                }
            }
        }
    }
}

private fun litSegmentsForDb(
    linear: Float,
    segmentCount: Int,
    minDb: Float,
): Int {
    val db = linearToDbFs(linear).coerceIn(minDb, 0f)
    val normalized = ((db - minDb) / (0f - minDb)).coerceIn(0f, 1f)
    return (normalized * segmentCount).toInt().coerceIn(0, segmentCount)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPpmSegment(
    on: Boolean,
    idx: Int,
    segmentCount: Int,
    topLeft: Offset,
    size: Size,
) {
    val color =
        when {
            idx >= segmentCount - 2 -> Color(0xFFE53935)
            idx >= segmentCount - 4 -> Color(0xFFFFB300)
            else -> Color(0xFF43A047)
        }
    drawRect(
        color = if (on) color else Color.White.copy(alpha = 0.12f),
        topLeft = topLeft,
        size = size,
    )
}

/**
 * Polls [sampleAudioAmplitude] while [active] so meters animate during video record.
 */
@Composable
fun LivePpmAudioMeter(
    active: Boolean,
    sampleAudioAmplitude: () -> Int,
    modifier: Modifier = Modifier,
    layout: PpmMeterLayout = PpmMeterLayout.Vertical,
    pollIntervalMs: Long = 50L,
) {
    var levelLinear by remember { mutableStateOf(0f) }
    LaunchedEffect(active, pollIntervalMs) {
        if (!active) {
            levelLinear = 0f
            return@LaunchedEffect
        }
        while (isActive) {
            levelLinear = sampleAudioAmplitude().coerceIn(0, 32767) / 32767f
            delay(pollIntervalMs)
        }
    }
    PpmAudioMeter(
        levelLinear = levelLinear,
        modifier = modifier,
        segmentCount = 20,
        layout = layout,
    )
}

/**
 * Stereo L/R vertical meters — fill pillar height (portrait UI); reads as L/R pair in landscape.
 */
@Composable
fun LiveStereoVerticalPpmAudioMeter(
    active: Boolean,
    sampleAudioAmplitudeStereo: () -> Pair<Int, Int>,
    modifier: Modifier = Modifier,
    barGap: androidx.compose.ui.unit.Dp = 4.dp,
    showDbScale: Boolean = true,
    pollIntervalMs: Long = 50L,
) {
    var levelLeft by remember { mutableStateOf(0f) }
    var levelRight by remember { mutableStateOf(0f) }
    LaunchedEffect(active, pollIntervalMs) {
        if (!active) {
            levelLeft = 0f
            levelRight = 0f
            return@LaunchedEffect
        }
        while (isActive) {
            val (l, r) = sampleAudioAmplitudeStereo()
            levelLeft = l.coerceIn(0, 32767) / 32767f
            levelRight = r.coerceIn(0, 32767) / 32767f
            delay(pollIntervalMs)
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(barGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PpmAudioMeter(
            levelLinear = levelLeft,
            layout = PpmMeterLayout.Vertical,
            segmentCount = 30,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        if (showDbScale) {
            DbScaleMarks(
                modifier = Modifier.width(20.dp).fillMaxHeight(),
                marks = listOf(-60, -48, -36, -24, -12, -6, -3, 0),
            )
        }
        PpmAudioMeter(
            levelLinear = levelRight,
            layout = PpmMeterLayout.Vertical,
            segmentCount = 30,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/**
 * Stereo L/R pillar meters — two horizontal bars (quiet→loud left→right) before +90° CW rotation.
 */
@Composable
fun LiveStereoPpmAudioMeter(
    active: Boolean,
    sampleAudioAmplitudeStereo: () -> Pair<Int, Int>,
    modifier: Modifier = Modifier,
    barHeight: androidx.compose.ui.unit.Dp = 10.dp,
    barGap: androidx.compose.ui.unit.Dp = 4.dp,
    pollIntervalMs: Long = 50L,
) {
    var levelLeft by remember { mutableStateOf(0f) }
    var levelRight by remember { mutableStateOf(0f) }
    LaunchedEffect(active, pollIntervalMs) {
        if (!active) {
            levelLeft = 0f
            levelRight = 0f
            return@LaunchedEffect
        }
        while (isActive) {
            val (l, r) = sampleAudioAmplitudeStereo()
            levelLeft = l.coerceIn(0, 32767) / 32767f
            levelRight = r.coerceIn(0, 32767) / 32767f
            delay(pollIntervalMs)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(barGap),
    ) {
        PpmAudioMeter(
            levelLinear = levelLeft,
            layout = PpmMeterLayout.Horizontal,
            segmentCount = 24,
            modifier = Modifier.fillMaxWidth().height(barHeight),
        )
        PpmAudioMeter(
            levelLinear = levelRight,
            layout = PpmMeterLayout.Horizontal,
            segmentCount = 24,
            modifier = Modifier.fillMaxWidth().height(barHeight),
        )
    }
}

@Composable
private fun DbScaleMarks(
    modifier: Modifier = Modifier,
    marks: List<Int>,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        for (db in marks.reversed()) {
            Text(
                text = if (db == 0) "0 dB" else "$db",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

/** dBFS from linear amplitude (0..1). */
fun linearToDbFs(linear: Float): Float {
    val v = linear.coerceAtLeast(1e-6f)
    return (20f * log10(v)).coerceIn(-60f, 0f)
}
