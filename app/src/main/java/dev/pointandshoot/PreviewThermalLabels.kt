package dev.pointandshoot

/**
 * Maps thermal status (API 29+ from [PowerManager.getCurrentThermalStatus]) to user-facing labels.
 * Uses [ApiLevelGuards] literals so API 28 devices never resolve [PowerManager.THERMAL_STATUS_*] fields.
 */
object PreviewThermalLabels {
    const val THERMAL_WARNING_MIN_STATUS = ApiLevelGuards.THERMAL_STATUS_MODERATE

    fun labelForStatus(status: Int): String =
        when (status) {
            ApiLevelGuards.THERMAL_STATUS_NONE -> "OK"
            ApiLevelGuards.THERMAL_STATUS_LIGHT -> "WARM"
            ApiLevelGuards.THERMAL_STATUS_MODERATE -> "HOT"
            ApiLevelGuards.THERMAL_STATUS_SEVERE -> "SEVERE"
            ApiLevelGuards.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            ApiLevelGuards.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            ApiLevelGuards.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
            else -> "?"
        }

    fun isThermalWarning(status: Int): Boolean = status >= THERMAL_WARNING_MIN_STATUS

    fun isSevereOrWorse(status: Int): Boolean = status >= ApiLevelGuards.THERMAL_STATUS_SEVERE
}
