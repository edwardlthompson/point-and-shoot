package dev.pointandshoot

import android.graphics.ImageFormat
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build

/**
 * Optional post-capture telemetry for the readout strip (Milestone **10.6**): RAW binning factor,
 * negotiated dynamic-range profile label, and RAW pixel format label.
 */
data class StillPostReadoutSnapshot(
    val rawBinningDisplay: String?,
    val dynamicRangeShort: String?,
    val rawFormatLabel: String?,
)

object StillPostReadoutExtract {
    fun rawFormatLabel(format: Int): String =
        when (format) {
            ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
            ImageFormat.RAW10 -> "RAW10"
            ImageFormat.RAW12 -> "RAW12"
            else -> "fmt_$format"
        }

    /**
     * @param previewDynamicRangeShort Label for the **preview session** profile applied to output 0
     *   when the still ran (public [CaptureResult] does not expose a negotiated still DR key on all
     *   API levels — Milestone **10.6** readout uses the session profile we configured).
     */
    fun from(
        result: TotalCaptureResult,
        rawFormatLabel: String?,
        previewDynamicRangeShort: String?,
    ): StillPostReadoutSnapshot {
        val binning: String? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                runCatching {
                    @Suppress("NewApi")
                    result.get(CaptureResult.SENSOR_RAW_BINNING_FACTOR_USED)?.toString()
                }.getOrNull()
            } else {
                null
            }
        return StillPostReadoutSnapshot(
            rawBinningDisplay = binning,
            dynamicRangeShort = previewDynamicRangeShort,
            rawFormatLabel = rawFormatLabel,
        )
    }
}
