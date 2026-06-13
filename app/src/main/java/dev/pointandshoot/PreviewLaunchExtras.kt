package dev.pointandshoot

import android.content.Context
import android.content.Intent
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_DIAL
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_PRIMARY_PHOTO
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_RAW_COUNT
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_RAW_STILL_FAST
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_CODEC_ORDINAL
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_ENCODE_H
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_ENCODE_W
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_FPS
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_TENBIT

/**
 * One-shot preview automation values read in [MainActivity.onCreate] so cold `am start` extras
 * are not lost to Compose `remember(activity)` ordering or sticky RAW/H-dial intent pollution.
 */
data class PreviewLaunchExtras(
    val inAppVideoAutomationSec: Int = 0,
    val rawVideoAutomationSec: Int = 0,
    val videoFps: Int? = null,
    val videoEncodeW: Int? = null,
    val videoEncodeH: Int? = null,
    val videoTenBit: Boolean = false,
    val videoCodecOrdinal: Int? = null,
    val primaryPhoto: Boolean? = null,
) {
    val wantsVideoAutomation: Boolean
        get() = inAppVideoAutomationSec > 0 || rawVideoAutomationSec > 0

    companion object {
        val EMPTY = PreviewLaunchExtras()
    }
}

/**
 * Parse and sanitize preview automation extras once per activity instance.
 * When in-app video automation is requested, clear sequential RAW and force Auto dial.
 */
fun consumePreviewLaunchExtras(context: Context, intent: Intent?): PreviewLaunchExtras {
    if (intent == null) return PreviewLaunchExtras.EMPTY
    val videoSec =
        intent.getIntExtra(EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC, 0).coerceIn(0, 120)
    val rawVideoSec =
        intent.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC, 0).coerceIn(0, 120)
    if (videoSec > 0 || rawVideoSec > 0) {
        intent.putExtra(EXTRA_PNS_PREVIEW_RAW_COUNT, 0)
        intent.removeExtra(EXTRA_PNS_PREVIEW_RAW_STILL_FAST)
        intent.putExtra(EXTRA_PNS_PREVIEW_DIAL, "Auto")
        intent.putExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO, false)
    }
    val fps = intent.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_FPS, 0).takeIf { it > 0 }
    val encodeW = intent.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_ENCODE_W, 0).takeIf { it > 0 }
    val encodeH = intent.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_ENCODE_H, 0).takeIf { it > 0 }
    val tenBit = intent.getBooleanExtra(EXTRA_PNS_PREVIEW_VIDEO_TENBIT, false)
    val codecOrdinal =
        if (intent.hasExtra(EXTRA_PNS_PREVIEW_VIDEO_CODEC_ORDINAL)) {
            intent.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_CODEC_ORDINAL, 0)
        } else {
            null
        }
    val primaryPhoto =
        if (intent.hasExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO)) {
            intent.getBooleanExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO, true)
        } else {
            null
        }
    if (videoSec > 0) {
        intent.removeExtra(EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC)
    }
    if (rawVideoSec > 0) {
        intent.removeExtra(EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC)
    }
    if (videoSec > 0 || rawVideoSec > 0) {
        PnsAdbLog.i(
            context,
            "previewLaunchExtras videoSec=$videoSec rawVideoSec=$rawVideoSec fps=$fps " +
                "encode=${encodeW ?: 0}x${encodeH ?: 0} codecOrdinal=$codecOrdinal primaryPhoto=false",
        )
    }
    return PreviewLaunchExtras(
        inAppVideoAutomationSec = videoSec,
        rawVideoAutomationSec = rawVideoSec,
        videoFps = fps,
        videoEncodeW = encodeW,
        videoEncodeH = encodeH,
        videoTenBit = tenBit,
        videoCodecOrdinal = codecOrdinal,
        primaryPhoto = primaryPhoto,
    )
}
