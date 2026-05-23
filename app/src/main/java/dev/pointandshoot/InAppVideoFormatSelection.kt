package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Log
import android.util.Size

/**
 * Sprint **14.1** — persisted in-app video format (codec / resolution / fps) via
 * [PreviewChromePreferences], surfaced by [VideoFormatChip] + [VideoFormatPickerSheet].
 */
object InAppVideoFormatSelection {
    private const val TAG = "PNS.ChromeUx"

    fun loadCatalog(
        supportsDcg: Boolean,
        supportsAv1: Boolean = false,
        highSpeedMap: StreamConfigurationMap? = null,
    ): List<VideoFormat> {
        val tiers = VideoFormatPresets.getHardwareTiers(supportsDcg = supportsDcg, supportsAv1 = supportsAv1)
        return filterCatalogToCaptureCapabilities(tiers, highSpeedMap)
    }

    /**
     * HFR rows must exist on the **camera** constrained HS table, not encoder performance-points alone.
     * Hides e.g. **4K @ 120** when only **1080p @ 120** is HS-capable.
     */
    fun filterCatalogToCaptureCapabilities(
        catalog: List<VideoFormat>,
        highSpeedMap: StreamConfigurationMap?,
    ): List<VideoFormat> {
        if (highSpeedMap == null) {
            return catalog.filter { format ->
                !(format.frameRate >= 120 && format.resolution.width >= 3840)
            }
        }
        val filtered =
            catalog.filter { format ->
                if (format.frameRate < 120) {
                    true
                } else {
                    InAppVideoRecordingSupport.pickHighSpeedVideoTarget(
                        highSpeedMap,
                        format.frameRate,
                        format.resolution,
                    ) != null
                }
            }
        val dropped = catalog.size - filtered.size
        if (dropped > 0) {
            Log.i(TAG, "videoFormatCatalog filtered hsDropped=$dropped remaining=${filtered.size}")
        }
        return filtered
    }

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
        supportsAv1: Boolean = MediaCodecCapabilityProbe.supportsAv1Encoder(),
        adbForceAv1: Boolean = false,
        highSpeedMap: StreamConfigurationMap? = null,
    ): VideoFormat {
        val catalog = loadCatalog(supportsDcg, supportsAv1, highSpeedMap)
        val forcedCodec =
            when {
                adbForceAv1 && supportsAv1 -> VideoCodec.AV1
                chrome.inAppVideoCodecOrdinal >= 0 ->
                    VideoCodec.entries.getOrNull(chrome.inAppVideoCodecOrdinal)
                else -> null
            }
        if (forcedCodec != null) {
            fun resolutionDistancePx(format: VideoFormat): Long {
                val dw = (format.resolution.width - recordSize.width).toLong()
                val dh = (format.resolution.height - recordSize.height).toLong()
                return dw * dw + dh * dh
            }
            catalog
                .filter { it.codec == forcedCodec && it.frameRate == targetFps }
                .minByOrNull { resolutionDistancePx(it) }
                ?.let { forced ->
                    Log.i(
                        TAG,
                        "inAppVideoFormat=forcedCodec=${forced.codec} label=${forced.getLabel()} " +
                            "${forced.resolution.width}x${forced.resolution.height}@${forced.frameRate} " +
                            "hsRecord=${recordSize.width}x${recordSize.height}",
                    )
                    return forced
                }
            catalog
                .filter { it.codec == forcedCodec }
                .minByOrNull { kotlin.math.abs(it.frameRate - targetFps) }
                ?.let { forced ->
                    Log.i(
                        TAG,
                        "inAppVideoFormat=forcedCodecNearest label=${forced.getLabel()} " +
                            "wantedFps=$targetFps actualFps=${forced.frameRate}",
                    )
                    return forced
                }
        }
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
                targetFps.coerceIn(15, 480)
            } else {
                targetFps.coerceIn(15, VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS)
            }
        val formats =
            VideoFormatPresets.getAvailableFormats(
                resolution = recordSize,
                fps = fps,
                supportsDcg = supportsDcg,
                supportsAv1 = supportsAv1,
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
