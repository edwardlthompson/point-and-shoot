package dev.pointandshoot

import android.os.PowerManager

/**
 * Maps [PowerManager.getCurrentThermalStatus] (API 29+) to user-facing labels for Sprint **13V.12**.
 */
object PreviewThermalLabels {
    const val THERMAL_WARNING_MIN_STATUS = PowerManager.THERMAL_STATUS_MODERATE

    fun labelForStatus(status: Int): String =
        when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "OK"
            PowerManager.THERMAL_STATUS_LIGHT -> "WARM"
            PowerManager.THERMAL_STATUS_MODERATE -> "HOT"
            PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
            PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
            else -> "?"
        }

    fun isThermalWarning(status: Int): Boolean = status >= THERMAL_WARNING_MIN_STATUS

    fun isSevereOrWorse(status: Int): Boolean = status >= PowerManager.THERMAL_STATUS_SEVERE
}
