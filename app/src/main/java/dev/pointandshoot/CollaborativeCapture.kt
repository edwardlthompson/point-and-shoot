package dev.pointandshoot

import android.util.Log

/**
 * Sprint **IP.2** — lightweight collaborative session counter (LAN + tether clients).
 *
 * Full multi-operator control uses [TetheredCaptureServer] POST /capture plus LAN file pull.
 */
object CollaborativeCapture {
    const val TAG = "PNS.Collab"

    @Volatile
    var enabled: Boolean = true

    @Volatile
    private var activeClients: Int = 0

    fun onClientConnected(count: Int) {
        activeClients = count
        Log.i(TAG, "clientConnected count=$count collaborative=$enabled")
    }

    fun logProbe(context: android.content.Context) {
        PnsAdbLog.i(context, "connectivity collaborative clients=$activeClients enabled=$enabled")
    }

    fun onClientDisconnected(count: Int) {
        activeClients = count
        Log.i(TAG, "clientDisconnected count=$count")
    }

    fun clientCount(): Int = activeClients
}
