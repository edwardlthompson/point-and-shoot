package dev.pointandshoot

import android.util.Log
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Minimum pillar width before side HUD columns activate (Sprint **15.23**). */
val VideoPillarHudMinWidthDp: Dp = 24.dp

/** Fraction of pillar height used for HUD spans. */
private const val PillarHudSpanFraction = 0.92f

/**
 * Letterbox pillar width from measured finder vs preview-content geometry.
 */
fun computePillarBarWidthDp(
    finderPx: IntSize,
    contentPx: IntSize,
    density: Density,
): Dp {
    if (finderPx.width <= 0 || contentPx.width <= 0 || finderPx.width <= contentPx.width) {
        return 0.dp
    }
    return with(density) { ((finderPx.width - contentPx.width) / 2).toDp() }
}

/**
 * Sprint **15.23** — recording timecode + battery/thermal + storage (left) and stereo PPM (right)
 * in 16:9 letterbox pillars. Left/right HUD only while recording.
 */
@Composable
fun VideoSidePanels(
    active: Boolean,
    pillarBarWidthDp: Dp,
    showTimecode: Boolean,
    showPowerThermal: Boolean,
    showStorageRemaining: Boolean,
    isRecording: Boolean,
    videoPrimary: Boolean,
    selectedFps: Int,
    recordStartElapsedMs: Long?,
    highDrainContext: PreviewHighDrainMode.Context,
    videoEncodeSize: Size,
    rawVideoLane: Boolean,
    enableResearchDcgHdr: Boolean,
    adbPreviewVideoDcg: Boolean,
    adbPreviewVideoTenBit: Boolean,
    adbStorageAvailableBytes: Long?,
    sampleAudioAmplitudeStereo: () -> Pair<Int, Int>,
    modifier: Modifier = Modifier,
    pollIntervalMs: Long = 2_000L,
) {
    LaunchedEffect(active, pillarBarWidthDp, isRecording, videoPrimary) {
        Log.i(
            "PNS.ChromeUx",
            "pillarHud=${if (active) "active" else "off"} pillarW=${pillarBarWidthDp.value.toInt()}dp " +
                "recording=$isRecording videoPrimary=$videoPrimary stereoMeters=true",
        )
    }

    if (!active || !isRecording || !videoPrimary) return

    val innerPad = 3.dp
    val showThermal = showPowerThermal
    val showTc = showTimecode
    val showStorage = showStorageRemaining
    val showLeftInfo = showTc || showThermal || showStorage

    Box(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        if (showLeftInfo) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(pillarBarWidthDp)
                        .fillMaxHeight()
                        .padding(innerPad),
                contentAlignment = Alignment.Center,
            ) {
                val pillarSpan = maxHeight * PillarHudSpanFraction
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(pillarSpan),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .pillarLandscapeRotate90Cw()
                                .width(pillarSpan),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showTc) {
                                TimecodeOverlay(
                                    isRecording = true,
                                    fps = selectedFps,
                                    startedElapsedMs = recordStartElapsedMs,
                                )
                            }
                            if (showTc && showThermal) {
                                PillarRecordingInfoSeparator()
                            }
                            if (showThermal) {
                                VideoPillarThermalChip(
                                    highDrainContext = highDrainContext,
                                    hudShowPowerThermal = showPowerThermal,
                                    pollIntervalMs = pollIntervalMs,
                                )
                            }
                            if ((showTc || showThermal) && showStorage) {
                                PillarRecordingInfoSeparator()
                            }
                            if (showStorage) {
                                VideoPillarStorageChip(
                                    encodeWidth = videoEncodeSize.width,
                                    encodeHeight = videoEncodeSize.height,
                                    targetFps = selectedFps,
                                    rawVideoLane = rawVideoLane,
                                    enableResearchDcgHdr = enableResearchDcgHdr,
                                    adbPreviewVideoDcg = adbPreviewVideoDcg,
                                    adbPreviewVideoTenBit = adbPreviewVideoTenBit,
                                    adbStorageAvailableBytes = adbStorageAvailableBytes,
                                )
                            }
                        }
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(pillarBarWidthDp)
                    .fillMaxHeight()
                    .padding(innerPad),
            contentAlignment = Alignment.Center,
        ) {
            LiveStereoVerticalPpmAudioMeter(
                active = true,
                sampleAudioAmplitudeStereo = sampleAudioAmplitudeStereo,
                modifier =
                    Modifier
                        .fillMaxWidth(0.94f)
                        .fillMaxHeight(),
                barGap = 5.dp,
            )
        }
    }
}

@Composable
private fun PillarRecordingInfoSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(horizontal = 6.dp)
                .width(1.dp)
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.38f)),
    )
}

@Composable
private fun VideoPillarThermalChip(
    highDrainContext: PreviewHighDrainMode.Context,
    hudShowPowerThermal: Boolean,
    pollIntervalMs: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val monitor = remember { PreviewPowerThermalMonitor(context) }
    var snapshot by remember { mutableStateOf<PreviewPowerThermalMonitor.Snapshot?>(null) }

    LaunchedEffect(highDrainContext, hudShowPowerThermal) {
        monitor.reset()
        while (isActive) {
            snapshot = monitor.sample()
            delay(pollIntervalMs)
        }
    }

    val snap = snapshot ?: return
    ThermalChip(snapshot = snap, modifier = modifier)
}

@Composable
private fun VideoPillarStorageChip(
    encodeWidth: Int,
    encodeHeight: Int,
    targetFps: Int,
    rawVideoLane: Boolean,
    enableResearchDcgHdr: Boolean,
    adbPreviewVideoDcg: Boolean,
    adbPreviewVideoTenBit: Boolean,
    adbStorageAvailableBytes: Long?,
    pollIntervalMs: Long = 3_000L,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val session =
        remember(
            encodeWidth,
            encodeHeight,
            targetFps,
            rawVideoLane,
            enableResearchDcgHdr,
            adbPreviewVideoDcg,
            adbPreviewVideoTenBit,
        ) {
            PreviewVideoStorageEstimate.Session(
                encodeWidth = encodeWidth,
                encodeHeight = encodeHeight,
                targetFps = targetFps,
                rawVideoLane = rawVideoLane,
                enableResearchDcgHdr = enableResearchDcgHdr,
                adbPreviewVideoDcg = adbPreviewVideoDcg,
                adbPreviewVideoTenBit = adbPreviewVideoTenBit,
            )
        }
    var display by remember { mutableStateOf<PreviewVideoStorageEstimate.Result?>(null) }

    LaunchedEffect(session, adbStorageAvailableBytes) {
        while (isActive) {
            val base = PreviewVideoStorageEstimate.estimate(session)
            val avail =
                PreviewVideoStorageProbe.availableBytesForDcim(
                    context,
                    adbOverrideBytes = adbStorageAvailableBytes,
                )
            display = PreviewVideoStorageEstimate.withAvailableBytes(base, avail)
            delay(pollIntervalMs)
        }
    }

    val snap = display ?: return
    StorageRemainingChip(result = snap, modifier = modifier)
}
