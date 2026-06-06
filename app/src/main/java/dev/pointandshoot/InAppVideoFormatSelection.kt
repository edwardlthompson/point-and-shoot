package dev.pointandshoot



import android.hardware.camera2.params.StreamConfigurationMap

import android.media.MediaRecorder

import android.util.Log

import android.util.Size
import dev.pointandshoot.fleet.DeviceAdaptedCatalog
import dev.pointandshoot.fleet.FleetUiVisibilityGate

/**

 * Sprint **14.1** — persisted in-app video format (codec / resolution / fps) via

 * [PreviewChromePreferences], surfaced by [VideoFormatChip] + [VideoFormatPickerSheet].

 */

object InAppVideoFormatSelection {

    private const val TAG = "PNS.ChromeUx"



    fun loadCatalog(

        supportsDcg: Boolean,

        supportsAv1: Boolean = false,

        supportsVp9: Boolean = false,

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

                supportsVp9 = supportsVp9,

                highSpeedMap = highSpeedMap,

                mediaRecorderSizes = mrSizes,

            )

        return filterCatalogToCaptureCapabilities(tiers, highSpeedMap, supportsAv1, supportsVp9)
            .let { VideoDeliveryHonesty.annotateHfrDeliveryTiers(it, highSpeedMap) }

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

        supportsVp9: Boolean = MediaCodecCapabilityProbe.supportsVp9Encoder(),

    ): List<VideoFormat> {

        val filtered =

            catalog.filter { format ->

                isFormatAvailableOnDevice(format, highSpeedMap, supportsAv1, supportsVp9)

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

        supportsVp9: Boolean = MediaCodecCapabilityProbe.supportsVp9Encoder(),

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

            if (!probe.hasExactH264PerformancePoint(w, h, fps)) return false

            return InAppVideoRecordingSupport.hasExactHighSpeedFps(highSpeedMap, w, h, fps) ||

                (w >= 3840 &&

                    InAppVideoRecordingSupport.supportsHighSpeedCaptureFor4KEncode(

                        highSpeedMap,

                        fps,

                        Size(w, h),

                    ))

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
                VideoCodec.VP9 ->
                    supportsVp9 && probe.hasExactHevcPerformancePoint(w, h, fps) && halOk && fps < 120
            }
        }

        if (
            fps == UltraHd60RecordSupport.TARGET_FPS &&
                UltraHd60RecordSupport.isUltraHdSize(w, h) &&
                !UltraHd60RecordSupport.isCatalogTierSupported(highSpeedMap, w, h, fps)
        ) {
            return false
        }

        if (
            UltraHd60RecordSupport.isUltraHdSize(w, h) &&
                fps < UltraHd60RecordSupport.TARGET_FPS &&
                android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P &&
                format.codec != VideoCodec.H264
        ) {
            return false
        }

        return when (format.codec) {

            VideoCodec.H264 ->
                InAppVideoRecordingSupport.supportsMediaRecorderOutputSize(highSpeedMap, w, h) &&
                    (
                        fps <= 60 ||
                            probe.hasExactH264PerformancePoint(w, h, fps)
                        )

            VideoCodec.H265 ->

                probe.hasExactHevcPerformancePoint(w, h, fps) &&

                    InAppVideoRecordingSupport.supportsMediaRecorderOutputSize(highSpeedMap, w, h)

            VideoCodec.H265_10BIT,

            VideoCodec.DCG,

            -> probe.hasExactHevcPerformancePoint(w, h, fps)

            VideoCodec.AV1 ->

                supportsAv1 && probe.hasExactHevcPerformancePoint(w, h, fps)

            VideoCodec.VP9 ->

                supportsVp9 && fps < 120 && probe.hasExactHevcPerformancePoint(w, h, fps)

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
        fleetCtx: FleetUiVisibilityGate.VisibilityContext? = null,
        hfrSessionOk: Boolean = true,

    ): VideoFormat {

        val supportsVp9 = MediaCodecCapabilityProbe.supportsVp9Encoder()

        val catalog =
            if (fleetCtx != null) {
                DeviceAdaptedCatalog.adaptedVideoCatalog(
                    fleetCtx,
                    supportsDcg,
                    supportsAv1,
                    supportsVp9,
                    highSpeedMap,
                )
            } else {
                loadCatalog(supportsDcg, supportsAv1, supportsVp9, highSpeedMap)
            }

        if (catalog.isEmpty()) {
            val fps =
                targetFps.coerceIn(15, VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS)
            return VideoFormat(
                codec = VideoCodec.H264,
                resolution = recordSize,
                frameRate = fps,
                bitrate = VideoFormatPresets.calculateBitrate(recordSize.width, recordSize.height, fps, VideoCodec.H264),
            )
        }

        val forcedCodec =
            when {
                adbForceAv1 && supportsAv1 -> VideoCodec.AV1
                chrome.inAppVideoCodecOrdinal >= 0 ->
                    VideoCodec.entries.getOrNull(chrome.inAppVideoCodecOrdinal)
                else -> null
            }?.takeUnless { !hfrSessionOk && it != VideoCodec.H264 }

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
        }

        val pinned =
            DeviceAdaptedCatalog.resolveInAdaptedCatalog(
                catalog = catalog,
                chrome = chrome,
                fallbackWidth = recordSize.width,
                fallbackHeight = recordSize.height,
                fallbackFps = targetFps,
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

        if (wantDcg || adbAutomationVideoTenBit) {
            val requestedFormat =
                catalog.firstOrNull { fmt ->
                    fmt.resolution.width == recordSize.width &&
                        fmt.resolution.height == recordSize.height &&
                        fmt.frameRate == targetFps &&
                        (!wantDcg || fmt.isDcg) &&
                        (!adbAutomationVideoTenBit || fmt.isTenBit || fmt.isDcg)
                }
                    ?: catalog.firstOrNull { fmt ->
                        fmt.frameRate == targetFps &&
                            (!wantDcg || fmt.isDcg) &&
                            (!adbAutomationVideoTenBit || fmt.isTenBit || fmt.isDcg)
                    }
            if (requestedFormat != null) {
                Log.i(
                    TAG,
                    "inAppVideoFormat=requestedPreference label=${requestedFormat.getLabel()} dcg=${requestedFormat.isDcg} " +
                        "tenBit=${requestedFormat.isTenBit} fps=${requestedFormat.frameRate} wantDcg=$wantDcg " +
                        "adbTenBit=$adbAutomationVideoTenBit",
                )
                return requestedFormat
            }
        }

        val default =
            DeviceAdaptedCatalog.defaultVideoFormat(catalog, targetFps, hfrSessionOk)
                ?: if (!hfrSessionOk) {
                    val fps =
                        targetFps.coerceIn(15, VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS)
                    VideoFormat(
                        codec = VideoCodec.H264,
                        resolution = recordSize,
                        frameRate = fps,
                        bitrate =
                            VideoFormatPresets.calculateBitrate(
                                recordSize.width,
                                recordSize.height,
                                fps,
                                VideoCodec.H264,
                            ),
                    )
                } else {
                    catalog.first()
                }
        Log.i(
            TAG,
            "inAppVideoFormat=adaptedDefault label=${default.getLabel()} dcg=${default.isDcg} " +
                "tenBit=${default.isTenBit} fps=${default.frameRate}",
        )
        return default
    }

}

