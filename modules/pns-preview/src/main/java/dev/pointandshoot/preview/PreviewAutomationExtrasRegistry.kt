package dev.pointandshoot.preview

/**
 * Canonical ADB intent extra keys consumed by [dev.pointandshoot.consumePreviewLaunchExtras]
 * and related cold-start preview automation.
 *
 * | Extra key constant | Intent key string | [dev.pointandshoot.PreviewLaunchExtras] field / effect |
 * |--------------------|-------------------|--------------------------------------------------------|
 * | [EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC] | `pns_preview_automation_in_app_video_sec` |
 * |   | `inAppVideoAutomationSec`; clears RAW count, forces Auto dial + video-primary |
 * | [EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC] | `pns_preview_video_raw_sec` |
 * |   | `rawVideoAutomationSec`; same video-primary sanitization as in-app video |
 * | [EXTRA_PNS_PREVIEW_RAW_COUNT] | `pns_preview_raw_count` | Cleared to 0 when video automation extras are present |
 * | [EXTRA_PNS_PREVIEW_RAW_STILL_FAST] | `pns_preview_raw_still_fast` | Removed when video automation extras are present |
 * | [EXTRA_PNS_PREVIEW_DIAL] | `pns_preview_dial` |
 * |   | Set to `"Auto"` when video automation extras are present |
 * | [EXTRA_PNS_PREVIEW_PRIMARY_PHOTO] | `pns_preview_primary_photo` |
 * |   | Forced `false` when video automation; otherwise optional seed → `primaryPhoto` |
 * | [EXTRA_PNS_PREVIEW_VIDEO_FPS] | `pns_preview_video_fps` | `videoFps` |
 * | [EXTRA_PNS_PREVIEW_VIDEO_ENCODE_W] | `pns_preview_video_encode_w` | `videoEncodeW` |
 * | [EXTRA_PNS_PREVIEW_VIDEO_ENCODE_H] | `pns_preview_video_encode_h` | `videoEncodeH` |
 * | [EXTRA_PNS_PREVIEW_VIDEO_TENBIT] | `pns_preview_video_10bit` | `videoTenBit` |
 * | [EXTRA_PNS_PREVIEW_VIDEO_CODEC_ORDINAL] | `pns_preview_video_codec_ordinal` | `videoCodecOrdinal` |
 *
 * Probe hub and script gates may continue importing via [dev.pointandshoot.EXTRA_PNS_*]
 * re-exports in [dev.pointandshoot.CameraCapabilitiesProbe].
 */
object PreviewAutomationExtrasRegistry {
    const val EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC =
        "pns_preview_automation_in_app_video_sec"
    const val EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC = "pns_preview_video_raw_sec"
    const val EXTRA_PNS_PREVIEW_RAW_COUNT = "pns_preview_raw_count"
    const val EXTRA_PNS_PREVIEW_RAW_STILL_FAST = "pns_preview_raw_still_fast"
    const val EXTRA_PNS_PREVIEW_DIAL = "pns_preview_dial"
    const val EXTRA_PNS_PREVIEW_PRIMARY_PHOTO = "pns_preview_primary_photo"
    const val EXTRA_PNS_PREVIEW_VIDEO_FPS = "pns_preview_video_fps"
    const val EXTRA_PNS_PREVIEW_VIDEO_ENCODE_W = "pns_preview_video_encode_w"
    const val EXTRA_PNS_PREVIEW_VIDEO_ENCODE_H = "pns_preview_video_encode_h"
    const val EXTRA_PNS_PREVIEW_VIDEO_TENBIT = "pns_preview_video_10bit"
    const val EXTRA_PNS_PREVIEW_VIDEO_CODEC_ORDINAL = "pns_preview_video_codec_ordinal"
}
