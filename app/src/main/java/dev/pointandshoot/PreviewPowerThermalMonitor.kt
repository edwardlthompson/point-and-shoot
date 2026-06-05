package dev.pointandshoot

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock

/**
 * Samples battery level + [PowerManager.getCurrentThermalStatus] for the preview power HUD.
 */
class PreviewPowerThermalMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val samples = ArrayDeque<PreviewBatteryDrainEstimator.Sample>(MAX_SAMPLES)

    data class Snapshot(
        val batteryPct: Int?,
        val drainPctPerHour: Float?,
        val thermalStatus: Int,
        val thermalLabel: String,
        val thermalWarning: Boolean,
    )

    fun sample(): Snapshot {
        val now = SystemClock.elapsedRealtime()
        val pct = readBatteryCapacityPct()
        if (pct != null) {
            appendSample(pct, now)
        }
        val thermal = powerManager.currentThermalStatusCompat()
        return Snapshot(
            batteryPct = pct,
            drainPctPerHour = PreviewBatteryDrainEstimator.estimateDrainPctPerHour(samples.toList()),
            thermalStatus = thermal,
            thermalLabel = PreviewThermalLabels.labelForStatus(thermal),
            thermalWarning = PreviewThermalLabels.isThermalWarning(thermal),
        )
    }

    fun reset() {
        samples.clear()
    }

    private fun readBatteryCapacityPct(): Int? {
        val raw =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (raw in 0..100) raw else null
    }

    private fun appendSample(levelPct: Int, elapsedMs: Long) {
        if (samples.isNotEmpty() && samples.last().levelPct == levelPct) {
            return
        }
        samples.addLast(PreviewBatteryDrainEstimator.Sample(levelPct, elapsedMs))
        while (samples.size > MAX_SAMPLES) {
            samples.removeFirst()
        }
    }

    companion object {
        private const val MAX_SAMPLES = 32
    }
}
