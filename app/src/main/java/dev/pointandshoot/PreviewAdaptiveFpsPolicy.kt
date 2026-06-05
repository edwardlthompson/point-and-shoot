package dev.pointandshoot

import android.os.PowerManager

/**
 * Sprint **PO.2** — lowers preview FPS when battery is low or the device is thermally stressed.
 *
 * Pure JVM policy; [PreviewEngineScreen] applies [decide] on a timer and logs `PNS.PowerThermal`.
 */
object PreviewAdaptiveFpsPolicy {
    const val BATTERY_CRITICAL_PCT = 10
    const val BATTERY_LOW_PCT = 20
    const val BATTERY_MODERATE_PCT = 30

    data class Decision(
        val effectiveFps: Int,
        /** Non-null when [effectiveFps] is below the user's chosen FPS. */
        val capFps: Int?,
        val reason: String?,
    )

    /**
     * @param userFps User-selected preview FPS (sheet / readout / dial).
     * @param batteryPct 0–100 or null if unknown.
     * @param thermalStatus [PowerManager.getCurrentThermalStatus] (API 29+).
     */
    fun decide(
        userFps: Int,
        batteryPct: Int?,
        thermalStatus: Int,
    ): Decision {
        val cap = suggestCapFps(batteryPct, thermalStatus) ?: return Decision(userFps, null, null)
        if (userFps <= cap) {
            return Decision(userFps, null, null)
        }
        return Decision(
            effectiveFps = cap,
            capFps = cap,
            reason = capReason(batteryPct, thermalStatus),
        )
    }

    /** Highest FPS allowed under current power/thermal conditions, or null if no cap. */
    fun suggestCapFps(batteryPct: Int?, thermalStatus: Int): Int? {
        val thermalCap = thermalCapFps(thermalStatus)
        val batteryCap = batteryCapFps(batteryPct)
        return when {
            thermalCap == null && batteryCap == null -> null
            thermalCap == null -> batteryCap
            batteryCap == null -> thermalCap
            else -> minOf(thermalCap, batteryCap)
        }
    }

    private fun thermalCapFps(thermalStatus: Int): Int? =
        when {
            thermalStatus >= ApiLevelGuards.THERMAL_STATUS_CRITICAL -> 30
            thermalStatus >= ApiLevelGuards.THERMAL_STATUS_SEVERE -> 60
            thermalStatus >= ApiLevelGuards.THERMAL_STATUS_MODERATE -> 90
            else -> null
        }

    private fun batteryCapFps(batteryPct: Int?): Int? =
        when {
            batteryPct == null -> null
            batteryPct <= BATTERY_CRITICAL_PCT -> 30
            batteryPct <= BATTERY_LOW_PCT -> 60
            batteryPct <= BATTERY_MODERATE_PCT -> 90
            else -> null
        }

    private fun capReason(batteryPct: Int?, thermalStatus: Int): String {
        val parts = mutableListOf<String>()
        batteryCapFps(batteryPct)?.let { parts.add("battery<=${batteryPct}% cap=$it") }
        thermalCapFps(thermalStatus)?.let { parts.add("thermal=$thermalStatus cap=$it") }
        return parts.joinToString("; ")
    }
}
