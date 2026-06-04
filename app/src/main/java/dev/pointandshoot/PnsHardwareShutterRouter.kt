package dev.pointandshoot

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import dev.pointandshoot.fleet.FleetCapabilityGate
import dev.pointandshoot.fleet.ProductHardwareLaunchScan

/**
 * Dedicated hardware camera key + fleet-discovered programmable shutter keys when preview is foreground.
 */
object PnsHardwareShutterRouter {
    const val TAG = "PNS.HardwareKey"

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var onShutter: (() -> Unit)? = null

    @Volatile
    var onFocusHalfPress: (() -> Unit)? = null

    @Volatile
    var onFocusHalfPressRelease: (() -> Unit)? = null

    @Volatile
    private var fleetExtraKeyCodes: Set<Int> = emptySet()

    fun refreshFleetExtraKeyCodes(context: Context) {
        val matrix = FleetCapabilityGate.loadMatrix(context.applicationContext)
        fleetExtraKeyCodes = ProductHardwareLaunchScan.extraShutterKeyCodes(matrix)
    }

    fun handleKeyEvent(context: Context, event: KeyEvent, foreground: Boolean): Boolean {
        if (HardwareKeyProbeRecorder.handleKeyEvent(event)) return true
        if (!foreground || !enabled) return false
        val keyCode = event.keyCode
        val action = event.action
        return when {
            keyCode == KeyEvent.KEYCODE_FOCUS && action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 -> {
                Log.i(
                    TAG,
                    "halfPressAf keyCode=$keyCode scanCode=${event.scanCode} source=${event.source} model=${Build.MODEL}",
                )
                PnsAdbLog.i(context, "hardwareKey halfPressAf keyCode=$keyCode")
                onFocusHalfPress?.invoke()
                true
            }
            keyCode == KeyEvent.KEYCODE_FOCUS && action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 -> {
                true
            }
            keyCode == KeyEvent.KEYCODE_FOCUS && action == KeyEvent.ACTION_UP -> {
                Log.i(TAG, "halfPressAfRelease keyCode=$keyCode scanCode=${event.scanCode}")
                PnsAdbLog.i(context, "hardwareKey halfPressAfRelease keyCode=$keyCode")
                onFocusHalfPressRelease?.invoke()
                true
            }
            keyCode == KeyEvent.KEYCODE_CAMERA && action == KeyEvent.ACTION_UP -> {
                fireShutter(context, event, source = "camera_key")
                true
            }
            keyCode == KeyEvent.KEYCODE_CAMERA && action == KeyEvent.ACTION_DOWN -> {
                Log.d(TAG, "cameraKeyDown scanCode=${event.scanCode} repeat=${event.repeatCount}")
                true
            }
            keyCode in fleetExtraKeyCodes && action == KeyEvent.ACTION_UP -> {
                fireShutter(context, event, source = "fleet_extra_key")
                true
            }
            else -> false
        }
    }

    private fun fireShutter(context: Context, event: KeyEvent, source: String) {
        Log.i(
            TAG,
            "shutterFired source=$source keyCode=${event.keyCode} scanCode=${event.scanCode} " +
                "deviceModel=${Build.MODEL}",
        )
        PnsAdbLog.i(context, "shutterFired source=$source keyCode=${event.keyCode}")
        onShutter?.invoke()
    }
}
