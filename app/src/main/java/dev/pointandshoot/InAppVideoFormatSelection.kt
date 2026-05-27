package dev.pointandshoot



import android.hardware.camera2.params.StreamConfigurationMap

import android.media.MediaRecorder

import android.util.Log

import android.util.Size

import kotlin.math.max

/**

 * Sprint **14.1** — persisted in-app video format (codec / resolution / fps) via

 * [PreviewChromePreferences], surfaced by [VideoFormatChip] + [VideoFormatPickerSheet].

 */

object InAppVideoFormatSelection {

    private const val TAG = "PNS.ChromeUx"



    /** Short device/camera notes shown at the top of [VideoFormatPickerSheet]. */

    data class VideoTruth(

        val lines: List<String>,

    )



    fun loadCatalog(

        supportsDcg: Boolean,

        supportsAv1: Boolean = false,

        highSpeedMap: StreamConfigurationMap? = null,

    ): List<VideoFormat> {

        val mrSizes =

            runCatching {

                highSpeedMap?.getOutputSizes(MediaRecorder::class.java)?.toList()

            }.getOrNull()

        val tiers =

            VideoFormatPresets.getHardwareTiers(

                supportsDcg = supportsDcg,

                supportsAv1 = supportsAv1,

                highSpeedMap = highSpeedMap,

                mediaRecorderSizes = mrSizes,

            )

        return filterCatalogToCaptureCapabilities(tiers, highSpeedMap, supportsAv1)

    }



    /**

     * Human-readable matrix for the active camera (HAL HS + encoder policy).

     */

    fun buildVideoTruth(highSpeedMap: StreamConfigurationMap?): VideoTruth {

        val lines = mutableListOf<String>()

        val hs1080 =

            InAppVideoRecordingSupport.highSpeedFpsForEncodeSize(highSpeedMap, 1920, 1080)

        val hs720 =

            InAppVideoRecordingSupport.highSpeedFpsForEncodeSize(highSpeedMap, 1280, 720)

        val maxHs = max(hs1080.maxOrNull() ?: 0, hs720.maxOrNull() ?: 0)

        when {

            maxHs >= 480 ->

                lines +=

                    "This camera: up to 480 fps @ 1080p / 720p (H.264 HFR when listed below)."

            maxHs >= 240 ->

                lines +=

                    "This camera: up to 240 fps @ 1080p / 720p — no 480 on this lens (HAL)."

            maxHs >= 120 ->

                lines += "This camera: up to 120 fps high-speed @ 1080p / 720p."

            hs1080.isEmpty() && hs720.isEmpty() ->

                lines += "No constrained high-speed video on this camera — HFR rows hidden."

        }

        lines += "Honest HFR: H.264 only. H.265 / 10-bit / AV1 at ≥120 fps hidden on this device."

        if (hs1080.isNotEmpty()) {

            lines += "HAL 1080p: ${hs1080.joinToString("/")} fps"

        }

        if (hs720.isNotEmpty()) {

            lines += "HAL 720p: ${hs720.joinToString("/")} fps"

        }

        runCatching { MediaCodecCapabilityProbe.probeSync() }.getOrNull()?.let { probe ->
            val h264Max =
                probe.h264PerformancePoints
                    .filter { it.width == 1920 && it.height == 1080 }
                    .maxOfOrNull { it.fps }
                    ?: 0
            if (h264Max >= 480 && maxHs < 480) {
                lines +=
                    "Encoder supports 1080p@480 H.264, but this lens does not — switch to ultra-wide for 480."
            }
            val hal8k =
                InAppVideoRecordingSupport.supportsEightKCameraOutputs(highSpeedMap)
            lines +=
                "8K: encoder probe max ${probe.maxFps8k} fps (supports8k=${probe.supports8k}); " +
                    "HAL capture outputs=${if (hal8k) "yes" else "no"}."
        }

        return VideoTruth(lines)

    }



    /**

     * Picker rows must match **labeled** resolution, fps, and codec on the active camera:

     * HFR — exact HS size+fps **and** exact H.264 encoder performance-point (no 4K→1080p fallback).

     * ≤119 fps H.264 — exact H.264 perf **and** [MediaRecorder] output size on this camera.

     * ≤119 fps HEVC family — exact HEVC encoder performance-point at that size+fps.

     */

    fun filterCatalogToCaptureCapabilities(

        catalog: List<VideoFormat>,

        highSpeedMap: StreamConfigurationMap?,

        supportsAv1: Boolean = MediaCodecCapabilityProbe.supportsAv1Encoder(),

    ): List<VideoFormat> {

        val filtered =

            catalog.filter { format ->

                isFormatAvailableOnDevice(format, highSpeedMap, supportsAv1)

            }

        val dropped = catalog.size - filtered.size

        if (dropped > 0) {

            Log.i(TAG, "videoFormatCatalog filtered dropped=$dropped remaining=${filtered.size}")

        }

        return filtered

    }



    internal fun isFormatAvailableOnDevice(

        format: VideoFormat,

        highSpeedMap: StreamConfigurationMap?,

        supportsAv1: Boolean,

    ): Boolean {

        if (VideoRecordingController.lacksTrueHfrUniqueFrames(format.frameRate, format.codec)) {

            return false

        }

        val w = format.resolution.width

        val h = format.resolution.height

        val fps = format.frameRate

        val probe = MediaCodecCapabilityProbe



        if (fps >= 120) {

            if (format.codec != VideoCodec.H264) return false

            return InAppVideoRecordingSupport.hasExactHighSpeedFps(highSpeedMap, w, h, fps) &&

                probe.hasExactH264PerformancePoint(w, h, fps)

        }



        if (InAppVideoRecordingSupport.isEightKSize(w, h)) {
            val halOk = InAppVideoRecordingSupport.supportsEightKCameraOutputs(highSpeedMap)
            val enc8k = MediaCodecCapabilityProbe.probeSync()?.supports8k == true
            return when (format.codec) {
                VideoCodec.H264 ->
                    probe.hasExactH264PerformancePoint(w, h, fps) &&
                        halOk &&
                        enc8k
                VideoCodec.H265 ->
                    probe.hasExactHevcPerformancePoint(w, h, fps) &&
                        halOk &&
                        enc8k
                VideoCodec.H265_10BIT,
                VideoCodec.DCG,
                -> probe.hasExactHevcPerformancePoint(w, h, fps) && halOk
                VideoCodec.AV1 ->
                    supportsAv1 && probe.hasExactHevcPerformancePoint(w, h, fps) && halOk
            }
        }

        return when (format.codec) {

            VideoCodec.H264 ->

                probe.hasExactH264PerformancePoint(w, h, fps) &&

                    InAppVideoRecordingSupport.supportsMediaRecorderOutputSize(highSpeedMap, w, h)

            VideoCodec.H265 ->

                probe.hasExactHevcPerformancePoint(w, h, fps) &&

                    InAppVideoRecordingSupport.supportsMediaRecorderOutputSize(highSpeedMap, w, h)

            VideoCodec.H265_10BIT,

            VideoCodec.DCG,

            -> probe.hasExactHevcPerformancePoint(w, h, fps)

            VideoCodec.AV1 ->

                supportsAv1 && probe.hasExactHevcPerformancePoint(w, h, fps)

        }

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

            catalog.firstOrNull {

                it.codec == forcedCodec &&

                    it.frameRate == targetFps &&

                    it.resolution.width == recordSize.width &&

                    it.resolution.height == recordSize.height

            }?.let { forced ->

                Log.i(

                    TAG,

                    "inAppVideoFormat=forcedCodec=${forced.codec} label=${forced.getLabel()} " +

                        "${forced.resolution.width}x${forced.resolution.height}@${forced.frameRate}",

                )

                return forced

            }

            val synthFps =

                if (targetFps >= 120) {

                    targetFps.coerceIn(15, 480)

                } else {

                    targetFps.coerceIn(15, VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS)

                }

            val canHonorForced =

                when (forcedCodec) {

                    VideoCodec.H264 -> true

                    VideoCodec.H265 ->

                        synthFps < VideoRecordingController.HFR_THRESHOLD_FPS

                    VideoCodec.AV1 -> supportsAv1

                    else -> false

                }

            if (canHonorForced) {

                val fallback =

                    VideoFormat(

                        codec = forcedCodec,

                        resolution = recordSize,

                        frameRate = synthFps,

                        bitrate =

                            VideoFormatPresets.calculateBitrate(

                                recordSize.width,

                                recordSize.height,

                                synthFps,

                                forcedCodec,

                            ),

                    )

                Log.i(

                    TAG,

                    "inAppVideoFormat=forcedCodecFallback label=${fallback.getLabel()} " +

                        "${fallback.resolution.width}x${fallback.resolution.height}@${fallback.frameRate}",

                )

                return fallback

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

