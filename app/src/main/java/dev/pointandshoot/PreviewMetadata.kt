package dev.pointandshoot

import android.hardware.camera2.CaptureResult

/**
 * Latest preview repeating-request metering triple (ISO / shutter / AWB).
 * Stored in [java.util.concurrent.atomic.AtomicReference] on [PreviewController] for tear-free reads.
 */
data class PreviewMetadata(
    val iso: Int?,
    val exposureNs: Long?,
    val awbMode: Int?,
) {
    companion object {
        fun mergeFromResult(current: PreviewMetadata, result: CaptureResult): PreviewMetadata {
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: current.iso
            val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: current.exposureNs
            val awbMode = result.get(CaptureResult.CONTROL_AWB_MODE) ?: current.awbMode
            return PreviewMetadata(iso, exposureNs, awbMode)
        }

        /** Pure merge for unit tests (mirrors [mergeFromResult] null-coalesce rules). */
        fun mergeForTest(
            current: PreviewMetadata,
            iso: Int?,
            exposureNs: Long?,
            awbMode: Int?,
        ): PreviewMetadata =
            PreviewMetadata(
                iso = iso ?: current.iso,
                exposureNs = exposureNs ?: current.exposureNs,
                awbMode = awbMode ?: current.awbMode,
            )
    }
}
