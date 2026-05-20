package dev.pointandshoot

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Sprint **13V.13** — minutes of record time left at current video bitrate; warns below 5 min.
 */
@Composable
fun PreviewStorageRemainingOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean,
    videoPrimary: Boolean,
    isRecording: Boolean,
    encodeWidth: Int,
    encodeHeight: Int,
    targetFps: Int,
    rawVideoLane: Boolean,
    enableResearchDcgHdr: Boolean,
    adbPreviewVideoDcg: Boolean,
    adbPreviewVideoTenBit: Boolean,
    hudShowStorageRemaining: Boolean,
    adbStorageAvailableBytes: Long? = null,
    pollIntervalMs: Long = 3_000L,
) {
    if (!hudShowStorageRemaining) return
    if (!videoPrimary && !isRecording) return

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

    LaunchedEffect(visible, session, adbStorageAvailableBytes) {
        if (!visible) {
            display = null
            return@LaunchedEffect
        }
        while (isActive) {
            val base = PreviewVideoStorageEstimate.estimate(session)
            val avail =
                PreviewVideoStorageProbe.availableBytesForDcim(
                    context,
                    adbOverrideBytes = adbStorageAvailableBytes,
                )
            val result = PreviewVideoStorageEstimate.withAvailableBytes(base, avail)
            display = result
            Log.i(
                "PNS.StorageRemain",
                "minutes=${result.minutesRemaining} warn=${result.lowStorageWarning} " +
                    "bytesPerSec=${result.bytesPerSecond} avail=$avail " +
                    "bitrate=${result.bitrateBps} raw=${result.rawLane} " +
                    "size=${encodeWidth}x$encodeHeight fps=$targetFps",
            )
            delay(pollIntervalMs)
        }
    }

    val snap = display ?: return
    val minutesText = PreviewVideoStorageEstimate.formatMinutesRemaining(snap.minutesRemaining)
    val line =
        if (snap.rawLane) {
            "RAW · $minutesText left"
        } else {
            "REC · $minutesText left"
        }
    val textColor =
        when {
            snap.lowStorageWarning -> PnsColors.RecordRed.copy(alpha = 0.95f)
            else -> Color.White.copy(alpha = 0.92f)
        }
    val chipBg = Color.Black.copy(alpha = 0.62f)

    Column(
        modifier =
            modifier
                .padding(8.dp)
                .background(chipBg, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = textColor,
        )
        if (snap.lowStorageWarning) {
            Text(
                text = "LOW STORAGE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = PnsColors.WarnAmber.copy(alpha = 0.95f),
            )
        }
    }
}
