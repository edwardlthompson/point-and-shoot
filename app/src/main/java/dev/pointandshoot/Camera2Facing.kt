package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata

/** Camera2 lens-facing helpers for preview routing (Milestone **10.4**). */
object Camera2Facing {
    fun frontCameraId(cm: CameraManager, ids: List<String>): String? =
        ids.firstOrNull { id ->
            runCatching {
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraMetadata.LENS_FACING_FRONT
            }.getOrDefault(false)
        }

    fun isFrontCamera(cm: CameraManager, cameraId: String?): Boolean {
        if (cameraId.isNullOrBlank()) return false
        return runCatching {
            cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) ==
                CameraMetadata.LENS_FACING_FRONT
        }.getOrDefault(false)
    }

    fun isBack(cm: CameraManager, cameraId: String?): Boolean {
        if (cameraId.isNullOrBlank()) return false
        return runCatching {
            cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) ==
                CameraMetadata.LENS_FACING_BACK
        }.getOrDefault(false)
    }
}
