package dev.pointandshoot

import android.content.Context
import android.util.Log
import android.view.KeyEvent

/**
 * Sprint **15.19** — Bluetooth / headset media button → shutter when app foregrounded.
 *
 * Wired from [MainActivity.dispatchKeyEvent] (no extra media-session dependency).
 */
object PnsMediaSessionManager {
    const val TAG = "PNS.MediaSession"

    @Volatile
    var btRemoteShutterEnabled: Boolean = false

    @Volatile
    var onRemoteShutter: (() -> Unit)? = null

    fun handleKeyEvent(context: Context, event: KeyEvent, foreground: Boolean): Boolean {
        if (!foreground || !btRemoteShutterEnabled || event.action != KeyEvent.ACTION_DOWN) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> {
                Log.i(TAG, "shutterFired source=bt_media")
                PnsAdbLog.i(context, "shutterFired source=bt_media")
                onRemoteShutter?.invoke()
                return true
            }
        }
        return false
    }
}
