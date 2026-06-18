package dev.pointandshoot

import kotlin.math.min

/**
 * Adobe DNG **DefaultUserCrop** describes the intended visible rectangle as
 * **fractions of the full raw image** (see DNG 1.7 — tags `DefaultCropOrigin`
 * + `DefaultCropSize`, conceptually a normalized crop in `[0,1]`).
 *
 * When the capture pipeline applies [CropPlan] via [SensorCropGeometry] +
 * [android.hardware.camera2.CaptureRequest.SCALER_CROP_REGION], the delivered
 * RAW buffer already matches the crop; Android's [android.hardware.camera2.DngCreator]
 * folds [TotalCaptureResult] + [CameraCharacteristics] into standard DNG tags.
 *
 * This helper materializes the **relative** crop vs the **full active array**
 * so host tooling / documentation can round-trip the same numbers we use for
 * `SCALER_CROP_REGION` without relying on device-specific DNG internals.
 */
object DngDefaultUserCropRatios {

    /**
     * Top / left / bottom / right as fractions of [activeWidth] x [activeHeight],
     * aligned with a centered [CropPlan] on that rectangle (same math as
     * [SensorCropGeometry.scalerCropRect]).
     *
     * `bottom` and `right` use **exclusive** edge semantics (matches
     * [android.graphics.Rect]).
     */
    fun normalizedEdges(
        mode: FocalMode,
        activeWidth: Int,
        activeHeight: Int,
    ): FloatArray {
        require(activeWidth > 0 && activeHeight > 0)
        val plan = CropPlan.centeredCrop(mode, activeWidth, activeHeight)
        val top = plan.cropTop.toFloat() / activeHeight
        val left = plan.cropLeft.toFloat() / activeWidth
        val bottom = (plan.cropTop + plan.cropHeight).toFloat() / activeHeight
        val right = (plan.cropLeft + plan.cropWidth).toFloat() / activeWidth
        return floatArrayOf(
            clamp01(top),
            clamp01(left),
            clamp01(bottom),
            clamp01(right),
        )
    }

    private fun clamp01(x: Float): Float = min(1f, maxOf(0f, x))
}
