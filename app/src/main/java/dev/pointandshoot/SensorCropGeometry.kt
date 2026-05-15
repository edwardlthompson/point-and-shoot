package dev.pointandshoot

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics

/**
 * Maps [BUILD_PLAN.md] digital-crop modes to Camera2 SCALER_CROP_REGION.
 * Crop policy uses [BackCameraRoleResolver] ids (and logical physical children), not hardcoded camera numbers.
 *
 * **73 / 85 / 150 mm** digital equivalents always apply on the resolved **mid-tele** sensor ([Roles.tele])
 * per [DODGE_PROFILE.md] — there is no alternate “fleet” long-native lens path for the 150 mm slot.
 */
object SensorCropGeometry {

    private val WIDE_DIGITAL_MODES = setOf(FocalMode.Street35, FocalMode.Standard50)

    fun allowsDigitalCrop(
        sessionCameraId: String,
        mode: FocalMode,
        sessionPhysicalIds: Set<String>?,
        wideId: String?,
        teleId: String?,
    ): Boolean {
        val phys = sessionPhysicalIds.orEmpty()
        fun sessionCoversPhysical(pid: String?): Boolean {
            if (pid == null) return false
            if (sessionCameraId == pid) return true
            return phys.isNotEmpty() && pid in phys
        }
        return when (mode) {
            in WIDE_DIGITAL_MODES -> sessionCoversPhysical(wideId)
            FocalMode.Portrait85 -> sessionCoversPhysical(teleId)
            FocalMode.LongTele150 -> sessionCoversPhysical(teleId)
            else -> false
        }
    }

    fun scalerCropRect(
        characteristics: CameraCharacteristics,
        sessionCameraId: String,
        mode: FocalMode?,
        sessionPhysicalIds: Set<String>? = null,
        wideId: String? = null,
        teleId: String? = null,
    ): Rect {
        val active =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: return Rect(0, 0, 0, 0)
        return scalerCropRect(
            active,
            sessionCameraId,
            mode,
            sessionPhysicalIds,
            wideId,
            teleId,
        )
    }

    fun scalerCropRect(
        activeArray: Rect,
        sessionCameraId: String,
        mode: FocalMode?,
        sessionPhysicalIds: Set<String>? = null,
        wideId: String? = null,
        teleId: String? = null,
    ): Rect {
        if (mode == null) {
            return Rect(
                activeArray.left,
                activeArray.top,
                activeArray.right,
                activeArray.bottom,
            )
        }
        if (!allowsDigitalCrop(
                sessionCameraId,
                mode,
                sessionPhysicalIds,
                wideId,
                teleId,
            )
        ) {
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
