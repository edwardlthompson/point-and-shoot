package dev.pointandshoot

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-data crop plan for the spec's "focal-equivalent" modes (BUILD_PLAN §3
 * "Hardware-to-software mapping"):
 *
 * | Focal eq. | Source camera | Crop strategy                                       |
 * |-----------|---------------|-----------------------------------------------------|
 * | 35mm      | LYT-808 wide  | 1.50x center crop + DefaultUserCrop EXIF metadata   |
 * | 50mm      | LYT-808 wide  | 2.20x center crop + center-weighted metering        |
 * | 85mm      | LYT-600 tele  | 1.16x center crop + Eye-AF priority                 |
 *
 * The plan is **engine-agnostic**: it produces a centered crop rectangle on
 * the active sensor array, plus engine-facing flags (metering / AF priority).
 * [PreviewEngineScreen] consumes the rectangle via `SCALER_CROP_REGION` on the
 * live preview path; still capture / `DngCreator` land with Phase 1. Host-side
 * [DngDefaultUserCropRatios] mirrors normalized crop vs the active array for
 * tooling parity with Adobe-style DefaultUserCrop semantics.
 *
 * Centered-crop math is pure-primitive so it is unit-testable on the JVM
 * without any Android stubs.
 */
data class CropPlan(
    val mode: FocalMode,
    /** Crop rectangle on the active sensor array (top-left origin, inclusive). */
    val cropLeft: Int,
    val cropTop: Int,
    val cropWidth: Int,
    val cropHeight: Int,
    /** Effective digital-zoom factor relative to the source sensor. */
    val zoomFactor: Double,
    /** Engine-facing metering hint (mapped to `CONTROL_AE_REGIONS`). */
    val meteringHint: MeteringHint,
    /** Engine-facing AF hint (mapped to `CONTROL_AF_REGIONS` / face-priority). */
    val afHint: AfHint,
) {
    companion object {
        /**
         * Build a centered crop on an active-array of [sensorWidth] x [sensorHeight].
         * The crop rectangle is clamped to the sensor bounds, and at least 1x1.
         *
         * @throws IllegalArgumentException if sensor dimensions or zoom are non-positive.
         */
        fun centeredCrop(
            mode: FocalMode,
            sensorWidth: Int,
            sensorHeight: Int,
        ): CropPlan {
            require(sensorWidth > 0 && sensorHeight > 0) {
                "sensor dimensions must be positive (was ${sensorWidth}x$sensorHeight)"
            }
            val zoom = mode.zoomFactor
            require(zoom >= 1.0) { "zoom factor must be >= 1.0 (was $zoom)" }

            val cropW = max(1, (sensorWidth / zoom).roundToInt()).coerceAtMost(sensorWidth)
            val cropH = max(1, (sensorHeight / zoom).roundToInt()).coerceAtMost(sensorHeight)
            val left = ((sensorWidth - cropW) / 2).coerceAtLeast(0)
            val top = ((sensorHeight - cropH) / 2).coerceAtLeast(0)

            return CropPlan(
                mode = mode,
                cropLeft = left,
                cropTop = top,
                cropWidth = cropW,
                cropHeight = cropH,
                zoomFactor = zoom,
                meteringHint = mode.meteringHint,
                afHint = mode.afHint,
            )
        }
    }

    /** Effective zoom after clamping (may be < requested if the sensor is small). */
    val effectiveZoomX: Double
        get() = max(1.0, sensorWidthOverCropWidth())

    private fun sensorWidthOverCropWidth(): Double {
        if (cropWidth <= 0) return 1.0
        // Caller knows the source sensor width via cropLeft + cropWidth + symmetric crop.
        return min(
            (cropLeft.toDouble() * 2 + cropWidth) / cropWidth,
            (cropTop.toDouble() * 2 + cropHeight) / cropHeight,
        )
    }
}

/**
 * The focal-equivalent crop modes the app exposes. Names match the spec's
 * "focal length" labels in [BUILD_PLAN](BUILD_PLAN.md) §3.
 */
enum class FocalMode(
    val displayName: String,
    val zoomFactor: Double,
    val meteringHint: MeteringHint,
    val afHint: AfHint,
) {
    /** 35mm equivalent on the LYT-808 main wide. Street crop. */
    Street35(displayName = "35mm", zoomFactor = 1.50, meteringHint = MeteringHint.Average, afHint = AfHint.SinglePoint),

    /** 50mm equivalent on the LYT-808 main wide. Standard crop with center-weighted metering. */
    Standard50(displayName = "50mm", zoomFactor = 2.20, meteringHint = MeteringHint.CenterWeighted, afHint = AfHint.SinglePoint),

    /** 85mm equivalent on the LYT-600 tele. Portrait crop with Eye-AF priority. */
    Portrait85(displayName = "85mm", zoomFactor = 1.16, meteringHint = MeteringHint.CenterWeighted, afHint = AfHint.EyeAf),

    /**
     * 150mm equivalent on the LYT-600 tele. Center crop of the 50 MP sensor down
     * to a 12 MP output (`sqrt(50/12) \u2248 2.04x` linear). Useful for portraits at
     * working distance, distant subjects, and tight architecture details.
     * Eye-AF priority is retained from [Portrait85] - at 150 mm equivalent on a
     * phone, subject isolation matters even more.
     */
    LongTele150(displayName = "150mm", zoomFactor = 2.04, meteringHint = MeteringHint.CenterWeighted, afHint = AfHint.EyeAf),
}

enum class MeteringHint { Average, CenterWeighted, HighlightWeighted }

enum class AfHint { SinglePoint, FaceAware, EyeAf }
