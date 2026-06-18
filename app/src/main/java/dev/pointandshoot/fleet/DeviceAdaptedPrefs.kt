package dev.pointandshoot.fleet

import android.os.Build
import android.util.Size
import dev.pointandshoot.ColorQualityIndex
import dev.pointandshoot.InAppVideoFormatSelection
import dev.pointandshoot.PnsLog
import dev.pointandshoot.PreviewChromePreferences
import dev.pointandshoot.VideoCodec
import dev.pointandshoot.VideoFormat
import dev.pointandshoot.VideoFormatPresets
import dev.pointandshoot.VideoRecordingController

/**
 * Resets persisted chrome when prefs point at formats/color spaces not in the device-adapted catalog.
 */
object DeviceAdaptedPrefs {
    private const val TAG = "PNS.ChromeUx"

    data class VideoSanitizeResult(
        val chrome: PreviewChromePreferences,
        val migrated: Boolean,
        val selectedFps: Int?,
    )

    fun sanitizeVideoChrome(
        chrome: PreviewChromePreferences,
        catalog: List<VideoFormat>,
        fallbackWidth: Int,
        fallbackHeight: Int,
        fallbackFps: Int,
        hfrSessionOk: Boolean = true,
        fourKRegularSessionOk: Boolean? = null,
    ): VideoSanitizeResult {
        if (catalog.isEmpty()) {
            return VideoSanitizeResult(chrome, migrated = false, selectedFps = null)
        }
        val fourKOk = fourKRegularSessionOk ?: hfrSessionOk
        val spaces = DeviceAdaptedCatalog.videoColorSpacesForDevice(catalog)
        var next = chrome
        var migrated = false
        val colorOrdinal = chrome.inAppVideoColorSpaceOrdinal
        if (colorOrdinal >= 0) {
            val pickedSpace = ColorQualityIndex.videoSpacesForPicker().getOrNull(colorOrdinal)
            if (pickedSpace != null && spaces.none { it.id == pickedSpace.id }) {
                next = next.copy(inAppVideoColorSpaceOrdinal = DeviceAdaptedCatalog.rec709ColorSpaceOrdinal())
                migrated = true
                PnsLog.i(TAG, "videoColorSpaceMigrate id=${pickedSpace.id} -> rec709")
            }
        }
        val encodeW = next.inAppVideoEncodeWidth.takeIf { it > 0 } ?: fallbackWidth
        val encodeIs4k = encodeW >= 3840
        if (next.inAppVideoCodecOrdinal >= 0) {
            val pickedCodec =
                VideoCodec.entries.getOrNull(next.inAppVideoCodecOrdinal)
            val needsLegacyH264 =
                pickedCodec != null &&
                    pickedCodec != VideoCodec.H264 &&
                    (
                        !fourKOk ||
                            (encodeIs4k && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
                    )
            if (needsLegacyH264 && catalog.any { it.codec == VideoCodec.H264 }) {
                next = next.copy(inAppVideoCodecOrdinal = VideoCodec.H264.ordinal)
                migrated = true
                PnsLog.i(TAG, "videoCodecMigrate legacySafe ${pickedCodec.name} -> H264")
            }
        }
        val codecOk =
            next.inAppVideoCodecOrdinal < 0 ||
                catalog.any {
                    it.codec == VideoCodec.entries.getOrNull(next.inAppVideoCodecOrdinal)
                }
        val resolved =
            DeviceAdaptedCatalog.resolveInAdaptedCatalog(
                catalog = catalog,
                chrome = next,
                fallbackWidth = fallbackWidth,
                fallbackHeight = fallbackHeight,
                fallbackFps = fallbackFps,
            )
        val needs4kDowngrade =
            !fourKOk &&
                (
                    encodeW >= 3840 ||
                        (resolved?.resolution?.width ?: 0) >= 3840
                )
        if (resolved != null && codecOk && !needs4kDowngrade) {
            return VideoSanitizeResult(next, migrated, selectedFps = null)
        }
        val fps = fallbackFps.coerceAtMost(VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS)
        val fallback =
            DeviceAdaptedCatalog.defaultVideoFormat(
                catalog,
                fps,
                hfrSessionOk = hfrSessionOk,
            ) ?: if (!fourKOk) {
                val w = fallbackWidth.coerceIn(1280, 1920)
                val h = fallbackHeight.coerceIn(720, 1080)
                VideoFormat(
                    codec = VideoCodec.H264,
                    resolution = Size(w, h),
                    frameRate = fps,
                    bitrate = VideoFormatPresets.calculateBitrate(w, h, fps, VideoCodec.H264),
                )
            } else {
                catalog.first()
            }
        val patched = InAppVideoFormatSelection.chromeAfterSelect(next, fallback)
        PnsLog.i(
            TAG,
            "videoFormatSanitize stalePref -> ${fallback.getLabel()} " +
                "${fallback.resolution.width}x${fallback.resolution.height}@${fallback.frameRate}",
        )
        return VideoSanitizeResult(
            chrome = patched,
            migrated = true,
            selectedFps = if (fallback.frameRate != fallbackFps) fallback.frameRate else null,
        )
    }
}
