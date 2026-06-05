package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size

/**
 * M24 / M26 — format picker and readout must honestly label 4K@120 tiers
 * (strict 4K HS vs sub-4K HS capture with 4K encode container).
 */
object VideoDeliveryHonesty {

    private const val UHD_ENCODE_WIDTH_MIN = 3840

    fun annotateHfrDeliveryTiers(
        catalog: List<VideoFormat>,
        highSpeedMap: android.hardware.camera2.params.StreamConfigurationMap?,
    ): List<VideoFormat> =
        catalog.map { format ->
            if (format.frameRate < VideoRecordingController.HFR_THRESHOLD_FPS) return@map format
            if (format.resolution.width < UHD_ENCODE_WIDTH_MIN) return@map format
            val strict =
                InAppVideoRecordingSupport.hasExactHighSpeedFps(
                    highSpeedMap,
                    format.resolution.width,
                    format.resolution.height,
                    format.frameRate,
                )
            when {
                strict -> format.copy(hfrDeliveryTier = HfrDeliveryTier.STRICT_4K120)
                InAppVideoRecordingSupport.supportsHighSpeedCaptureFor4KEncode(
                    highSpeedMap,
                    format.frameRate,
                    format.resolution,
                ) -> format.copy(
                    hfrDeliveryTier = HfrDeliveryTier.HS_SUB4K_CAPTURE,
                    hfrCaptureSize = captureSizeForLabel(format, highSpeedMap),
                )
                else -> format
            }
        }

    fun captureSizeForLabel(
        format: VideoFormat,
        highSpeedMap: android.hardware.camera2.params.StreamConfigurationMap?,
    ): Size? {
        if (format.hfrDeliveryTier != HfrDeliveryTier.HS_SUB4K_CAPTURE) return null
        return InAppVideoRecordingSupport.pickHighSpeedVideoTarget(
            highSpeedMap,
            format.frameRate,
            format.resolution,
        )?.first
    }

    fun readoutSuffix(format: VideoFormat, highSpeedMap: android.hardware.camera2.params.StreamConfigurationMap?): String? {
        val capture = captureSizeForLabel(format, highSpeedMap) ?: return null
        return "${capture.width}x${capture.height} HS"
    }

    fun isCatalogHonest(
        catalog: List<VideoFormat>,
        highSpeedMap: android.hardware.camera2.params.StreamConfigurationMap?,
    ): Boolean {
        val hfr4k =
            catalog.filter {
                it.frameRate >= VideoRecordingController.HFR_THRESHOLD_FPS &&
                    it.resolution.width >= UHD_ENCODE_WIDTH_MIN
            }
        if (hfr4k.isEmpty()) return true
        return hfr4k.all { format ->
            when (format.hfrDeliveryTier) {
                HfrDeliveryTier.STRICT_4K120 -> true
                HfrDeliveryTier.HS_SUB4K_CAPTURE ->
                    format.hfrCaptureSize != null || captureSizeForLabel(format, highSpeedMap) != null
                null -> false
            }
        }
    }

    fun wideHighSpeedMap(context: Context): android.hardware.camera2.params.StreamConfigurationMap? {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val roles = BackCameraRoleResolver.resolve(cm, cm.cameraIdList.toList())
        val wideId = roles.wide ?: return null
        val chars = cm.getCameraCharacteristics(wideId)
        return chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    }
}
