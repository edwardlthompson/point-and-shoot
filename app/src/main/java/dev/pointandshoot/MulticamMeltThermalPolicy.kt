package dev.pointandshoot

import android.os.PowerManager

/**
 * Thermal caps for Multicam Melt concurrent record (Milestone **20.2**).
 */
object MulticamMeltThermalPolicy {
    const val HAL_MAX_CAMERAS = 4
    const val TAG = "PNS.MulticamMelt"

    fun allowedCameraCount(thermalStatus: Int, halMaxConcurrent: Int): Int {
        val halCap = halMaxConcurrent.coerceIn(1, HAL_MAX_CAMERAS)
        val thermalCap =
            when {
                thermalStatus >= PowerManager.THERMAL_STATUS_SHUTDOWN -> 1
                thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> 1
                thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> 2
                thermalStatus >= PowerManager.THERMAL_STATUS_LIGHT -> 3
                else -> HAL_MAX_CAMERAS
            }
        return minOf(halCap, thermalCap).coerceAtLeast(1)
    }
}
