package dev.pointandshoot.fleet

import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Log
import android.util.Size
import dev.pointandshoot.ColorQualityIndex
import dev.pointandshoot.InAppVideoFormatSelection
import dev.pointandshoot.PreviewChromePreferences
import dev.pointandshoot.VideoCodec
import dev.pointandshoot.VideoFormat
import dev.pointandshoot.VideoRecordingController

/**
 * Device-adapted consumer catalogs (Milestone **18.8**) — one pipeline for pickers and record start.
 */
object DeviceAdaptedCatalog {
    private const val TAG = "PNS.ChromeUx"

    fun adaptedVideoCatalog(
        ctx: FleetUiVisibilityGate.VisibilityContext,
        supportsDcg: Boolean,
        supportsAv1: Boolean,
        supportsVp9: Boolean,
        highSpeedMap: StreamConfigurationMap?,
    ): List<VideoFormat> {
        val raw =
            InAppVideoFormatSelection.loadCatalog(
                supportsDcg = supportsDcg,
                supportsAv1 = supportsAv1,
                supportsVp9 = supportsVp9,
                highSpeedMap = highSpeedMap,
            )
        return FleetChromeVisibility.filterVideoFormats(raw, ctx)
    }

    /** Color spaces that have at least one adapted format path (transitive filter). */
    fun videoColorSpacesForDevice(adaptedFormats: List<VideoFormat>): List<ColorQualityIndex.VideoColorSpace> =
        ColorQualityIndex.videoSpacesForPicker().filter { space ->
            ColorQualityIndex.filterVideoFormats(adaptedFormats, space).isNotEmpty()
        }

    fun rec709ColorSpaceOrdinal(): Int =
        ColorQualityIndex.videoSpacesForPicker()
            .indexOfFirst { it.id == "rec709" }
            .coerceAtLeast(0)

    fun containsFormat(catalog: List<VideoFormat>, format: VideoFormat): Boolean =
        catalog.any {
            it.codec == format.codec &&
                it.resolution.width == format.resolution.width &&
                it.resolution.height == format.resolution.height &&
                it.frameRate == format.frameRate
        }

    /**
     * Safe default for this device: prefer Rec.709 lane, H.264, 1080p or 720p, fps ≤ 60.
     */
    fun defaultVideoFormat(
        catalog: List<VideoFormat>,
        targetFps: Int,
        hfrSessionOk: Boolean = true,
    ): VideoFormat? {
        if (catalog.isEmpty()) return null
        val fpsCap = targetFps.coerceIn(15, VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS)
        val rec709 = ColorQualityIndex.videoSpacesForPicker().firstOrNull { it.id == "rec709" }
        val rec709Formats = ColorQualityIndex.filterVideoFormats(catalog, rec709)
        val pool = if (rec709Formats.isNotEmpty()) rec709Formats else catalog
        fun resolutionRank(w: Int, h: Int): Int =
            when {
                w == 1920 && h == 1080 -> 0
                w == 1280 && h == 720 -> 1
                w >= 3840 -> 3
                else -> 2
            }
        fun fpsRank(frameRate: Int): Int =
            if (frameRate == targetFps) {
                0
            } else {
                1 + kotlin.math.abs(frameRate - targetFps)
            }
        val legacySafe = !hfrSessionOk
        return pool
            .filter { it.codec == VideoCodec.H264 && it.frameRate <= fpsCap }
            .sortedWith(
                compareBy<VideoFormat> { resolutionRank(it.resolution.width, it.resolution.height) }
                    .thenBy { fpsRank(it.frameRate) },
            )
            .firstOrNull()
            ?: if (legacySafe) {
                null
            } else {
                pool
                    .filter { it.frameRate <= fpsCap }
                    .sortedWith(
                        compareBy<VideoFormat> { resolutionRank(it.resolution.width, it.resolution.height) }
                            .thenBy { fpsRank(it.frameRate) }
                            .thenBy { if (it.codec == VideoCodec.H264) 0 else 1 },
                    )
                    .firstOrNull()
            }
    }

    fun resolveInAdaptedCatalog(
        catalog: List<VideoFormat>,
        chrome: PreviewChromePreferences,
        fallbackWidth: Int,
        fallbackHeight: Int,
        fallbackFps: Int,
    ): VideoFormat? {
        val resolved =
            InAppVideoFormatSelection.resolveSelected(
                catalog = catalog,
                chrome = chrome,
                fallbackWidth = fallbackWidth,
                fallbackHeight = fallbackHeight,
                fallbackFps = fallbackFps,
            )
        return resolved?.takeIf { containsFormat(catalog, it) }
    }
}
