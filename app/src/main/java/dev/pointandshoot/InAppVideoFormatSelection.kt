package dev.pointandshoot

import android.content.Context
import android.util.Log
import android.util.Size

/**
 * Sprint **14.1** — persisted in-app video format (codec / resolution / fps) via
 * [PreviewChromePreferences], surfaced by [VideoFormatChip] + [VideoFormatPickerSheet].
 */
object InAppVideoFormatSelection {
    private const val TAG = "PNS.ChromeUx"

    fun loadCatalog(supportsDcg: Boolean): List<VideoFormat> =
        VideoFormatPresets.getHardwareTiers(supportsDcg = supportsDcg)

    /**
     * Best-effort match for the readout chip label from chrome prefs + live encode size / fps.
     */
    fun resolveSelected(
        catalog: List<VideoFormat>,
        chrome: PreviewChromePreferences,
        fallbackWidth: Int,
        fallbackHeight: Int,
        fallbackFps: Int,
    ): VideoFormat? {
        if (catalog.isEmpty()) return null
        val w = chrome.inAppVideoEncodeWidth.takeIf { it > 0 } ?: fallbackWidth
        val h = chrome.inAppVideoEncodeHeight.takeIf { it > 0 } ?: fallbackHeight
        val fps = chrome.inAppVideoFps.takeIf { it > 0 } ?: fallbackFps
        val codec =
            chrome.inAppVideoCodecOrdinal.takeIf { it >= 0 }?.let { ord ->
                VideoCodec.entries.getOrNull(ord)
            }

        if (codec != null) {
            catalog.firstOrNull {
                it.resolution.width == w &&
                    it.resolution.height == h &&
                    it.frameRate == fps &&
                    it.codec == codec
            }?.let { return it }
        }
        catalog.firstOrNull {
            it.resolution.width == w && it.resolution.height == h && it.frameRate == fps
        }?.let { return it }
        catalog.firstOrNull {
            it.resolution.width == w && it.resolution.height == h
        }?.let { return it }
        return catalog.firstOrNull()
    }

    fun chromeAfterSelect(
        chrome: PreviewChromePreferences,
        format: VideoFormat,
    ): PreviewChromePreferences =
        chrome.copy(
            inAppVideoEncodeWidth = format.resolution.width,
            inAppVideoEncodeHeight = format.resolution.height,
            inAppVideoFps = format.frameRate,
            inAppVideoCodecOrdinal = format.codec.ordinal,
        )

    /**
     * Format passed into [VideoRecordingController] when recording starts.
     * Honors user picker prefs when they match the active record size / target fps; otherwise
     * falls back to DCG / 10-bit ADB overrides, then first available tier (legacy).
     */
    fun pickForRecording(
        recordSize: Size,
        targetFps: Int,
        supportsDcg: Boolean,
        wantDcg: Boolean,
        adbAutomationVideoTenBit: Boolean,
        chrome: PreviewChromePreferences,
    ): VideoFormat {
        val catalog = loadCatalog(supportsDcg)
        val pinned =
            resolveSelected(
                catalog,
                chrome,
                recordSize.width,
                recordSize.height,
                targetFps,
            )
        if (
            pinned != null &&
            pinned.resolution.width == recordSize.width &&
            pinned.resolution.height == recordSize.height &&
            pinned.frameRate == targetFps
        ) {
            if (!wantDcg || pinned.isDcg) {
                if (!adbAutomationVideoTenBit || pinned.isTenBit || !pinned.isDcg) {
                    Log.i(
                        TAG,
                        "inAppVideoFormat=userPick label=${pinned.getLabel()} " +
                            "${pinned.resolution.width}x${pinned.resolution.height}@${pinned.frameRate}",
                    )
                    return pinned
                }
            }
        }

        val fps =
            if (targetFps >= 120) {
                targetFps.coerceIn(15, 240)
            } else {
                targetFps.coerceIn(15, VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS)
            }
        val formats =
            VideoFormatPresets.getAvailableFormats(
                resolution = recordSize,
                fps = fps,
                supportsDcg = supportsDcg,
            )
        val picked =
            when {
                wantDcg ->
                    formats.firstOrNull { it.isDcg }
                        ?: formats.firstOrNull { it.isTenBit }
                        ?: formats.first()
                adbAutomationVideoTenBit ->
                    formats.firstOrNull { it.isTenBit && !it.isDcg }
                        ?: formats.first()
                else -> formats.first()
            }
        Log.i(
            TAG,
            "inAppVideoFormat=auto label=${picked.getLabel()} dcg=${picked.isDcg} " +
                "tenBit=${picked.isTenBit} fps=${picked.frameRate}",
        )
        return picked
    }
}
