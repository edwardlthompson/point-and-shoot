package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest

/**
 * Characteristics-only view of **YUV / private reprocessing** and related stall metadata.
 * End-to-end **`createReprocessCaptureRequest`** remains the Phase 5 probe scope ([CaptureLatencyProbeScreen]);
 * preview engine still ships **ZSL** via [PreviewStillCaptureHints.applyZslIfCompatible] when JPEG is attached.
 */
object PreviewReprocessStillHints {

    fun supportsYuvReprocessing(cc: CameraCharacteristics): Boolean =
        capabilitySet(cc).contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING)

    fun supportsPrivateReprocessing(cc: CameraCharacteristics): Boolean =
        capabilitySet(cc).contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING)

    fun reprocessMaxCaptureStall(cc: CameraCharacteristics): Int? =
        cc.get(CameraCharacteristics.REPROCESS_MAX_CAPTURE_STALL)

    fun reprocessEffectiveExposureKeyAdvertised(cc: CameraCharacteristics): Boolean =
        cc.availableCaptureRequestKeys?.contains(CaptureRequest.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR) == true

    fun markdownLines(cc: CameraCharacteristics): String =
        buildString {
            append("- YUV_REPROCESSING: ${supportsYuvReprocessing(cc)}")
            appendLine()
            append("- PRIVATE_REPROCESSING: ${supportsPrivateReprocessing(cc)}")
            appendLine()
            append("- REPROCESS_MAX_CAPTURE_STALL: ${reprocessMaxCaptureStall(cc)?.toString() ?: "null"}")
            appendLine()
            append("- REPROCESS_EFFECTIVE_EXPOSURE_FACTOR in request keys: ${reprocessEffectiveExposureKeyAdvertised(cc)}")
            appendLine()
        }

    private fun capabilitySet(cc: CameraCharacteristics): Set<Int> =
        cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet() ?: emptySet()
}
