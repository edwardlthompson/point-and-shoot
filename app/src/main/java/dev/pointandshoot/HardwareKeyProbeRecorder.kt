package dev.pointandshoot

import android.util.Log
import android.view.KeyEvent
import dev.pointandshoot.fleet.ProductHardwareLaunchScan
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Engineering probe: records hardware [KeyEvent]s while [HardwareKeyProbeScreen] is active.
 */
object HardwareKeyProbeRecorder {
    const val TAG = "PNS.HardwareKeyProbe"

    @Volatile
    var active: Boolean = false

    private val events = CopyOnWriteArrayList<ProductHardwareLaunchScan.HardwareKeyProbeEvent>()

    fun clear() {
        events.clear()
    }

    fun snapshot(): List<ProductHardwareLaunchScan.HardwareKeyProbeEvent> = events.toList()

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!active) return false
        val label =
            when (event.action) {
                KeyEvent.ACTION_DOWN -> "DOWN"
                KeyEvent.ACTION_UP -> "UP"
                else -> "OTHER"
            }
        val row =
            ProductHardwareLaunchScan.HardwareKeyProbeEvent(
                keyCode = event.keyCode,
                scanCode = event.scanCode,
                actionLabel = label,
                source = event.source,
                repeatCount = event.repeatCount,
                deviceId = event.deviceId,
            )
        events.add(row)
        Log.i(
            TAG,
            "keyEvent keyCode=${event.keyCode} scanCode=${event.scanCode} action=$label " +
                "repeat=${event.repeatCount} source=${event.source}",
        )
        return true
    }
}
