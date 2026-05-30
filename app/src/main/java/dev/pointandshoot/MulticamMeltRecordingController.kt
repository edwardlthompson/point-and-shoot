package dev.pointandshoot

import android.util.Log

/**
 * Multicam Melt — concurrent multi-rear record scaffold (Milestone **20.2**).
 *
 * Full encoder graph deferred; matrix + thermal policy + parity cells ship first.
 */
object MulticamMeltRecordingController {
    const val TAG = MulticamMeltThermalPolicy.TAG

    data class ArmState(
        val active: Boolean,
        val requestedCameras: Int,
        val allowedCameras: Int,
        val thermalStatus: Int,
        val cameraIds: List<String>,
    )

    @Volatile
    var lastArmState: ArmState? = null
        private set

    fun arm(
        requestedCameras: Int,
        thermalStatus: Int,
        halMaxConcurrent: Int,
        cameraIds: List<String>,
    ): ArmState {
        val allowed =
            MulticamMeltThermalPolicy.allowedCameraCount(
                thermalStatus = thermalStatus,
                halMaxConcurrent = halMaxConcurrent,
            )
        val picked = cameraIds.take(allowed.coerceAtMost(requestedCameras.coerceIn(1, MulticamMeltThermalPolicy.HAL_MAX_CAMERAS)))
        val state =
            ArmState(
                active = picked.size >= 2,
                requestedCameras = requestedCameras,
                allowedCameras = allowed,
                thermalStatus = thermalStatus,
                cameraIds = picked,
            )
        lastArmState = state
        runCatching {
            Log.i(
                TAG,
                "multicamMelt=arm active=${state.active} requested=$requestedCameras allowed=$allowed " +
                    "thermal=$thermalStatus ids=${picked.joinToString(",")}",
            )
        }
        return state
    }

    fun disarm() {
        lastArmState = null
        runCatching { Log.i(TAG, "multicamMelt=disarm") }
    }
}
