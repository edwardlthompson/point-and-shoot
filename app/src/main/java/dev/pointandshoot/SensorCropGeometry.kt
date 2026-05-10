package dev.pointandshoot

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics

/**
 * Maps [BUILD_PLAN.md] §3 digital-crop modes to Camera2
 * [android.hardware.camera2.CaptureRequest.SCALER_CROP_REGION].
 *
 * Crop rectangles are expressed in the same coordinate system as
 * [CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE] (pixel coordinates on
 * the full sensor, not necessarily origin `(0,0)`).
 */
object SensorCropGeometry {

    /** LYT-808 wide — Street / Standard digital crops. */
    private val WIDE_DIGITAL_MODES = setOf(FocalMode.Street35, FocalMode.Standard50)

    /** LYT-600 tele — Portrait / long-tele digital crops. */
    private val TELE_DIGITAL_MODES = setOf(FocalMode.Portrait85, FocalMode.LongTele150)

    fun allowsDigitalCrop(cameraId: String, mode: FocalMode): Boolean =
        when (cameraId) {
            "2" -> mode in WIDE_DIGITAL_MODES
            "4", "5", "6" -> mode in TELE_DIGITAL_MODES
            else -> false
        }

    /**
     * @param mode `null` = use full [CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE]
     * (native lens FOV for that cameraId).
     */
    fun scalerCropRect(
        characteristics: CameraCharacteristics,
        cameraId: String,
        mode: FocalMode?,
    ): Rect {
        val active =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: return Rect(0, 0, 0, 0)
        return scalerCropRect(active, cameraId, mode)
    }

    /**
     * Testable entry: [activeArray] is typically from [SENSOR_INFO_ACTIVE_ARRAY_SIZE].
     */
    fun scalerCropRect(activeArray: Rect, cameraId: String, mode: FocalMode?): Rect {
        if (mode == null) {
            return Rect(
                activeArray.left,
                activeArray.top,
                activeArray.right,
                activeArray.bottom,
            )
        }
        if (!allowsDigitalCrop(cameraId, mode)) {
            return Rect(
                activeArray.left,
                activeArray.top,
                activeArray.right,
                activeArray.bottom,
            )
        }

        val aw = activeArray.width()
        val ah = activeArray.height()
        val plan = CropPlan.centeredCrop(mode, aw, ah)
        return Rect(
            activeArray.left + plan.cropLeft,
            activeArray.top + plan.cropTop,
            activeArray.left + plan.cropLeft + plan.cropWidth,
            activeArray.top + plan.cropTop + plan.cropHeight,
        )
    }
}
