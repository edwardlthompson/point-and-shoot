@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Single in-process sink for Wear / BLE / LAN / volume remotes.
 * [PreviewEngineScreen] registers shutter, video, and chapter handlers.
 */
object PnsRemoteCommandBus {
    const val TAG: String = "PNS.Remote"

    @Volatile
    var onShutter: (() -> Unit)? = null

    @Volatile
    var onStartVideo: (() -> Unit)? = null

    @Volatile
    var onStopVideo: (() -> Unit)? = null

    @Volatile
    var onToggleVideo: (() -> Unit)? = null

    @Volatile
    var onChapter: (() -> Unit)? = null

    @Volatile
    var photoPrimary: Boolean = true

    @Volatile
    var sessionReady: Boolean = false

    private val main = Handler(Looper.getMainLooper())
    private val timerLock = Any()
    private var pendingTimer: Runnable? = null

    @Volatile
    var timerArmedSec: Int = 0
        private set

    fun cancelTimer() {
        synchronized(timerLock) {
            pendingTimer?.let { main.removeCallbacks(it) }
            pendingTimer = null
            timerArmedSec = 0
        }
        Log.i(TAG, "timer cancelled")
    }

    fun post(command: PnsRemoteProtocol.Command) {
        Log.i(TAG, "cmd=${command.action.wire} sec=${command.normalizedTimerSec} src=${command.source}")
        main.post {
            when (command.action) {
                PnsRemoteProtocol.Action.Shutter -> onShutter?.invoke()
                PnsRemoteProtocol.Action.VideoStart -> onStartVideo?.invoke()
                PnsRemoteProtocol.Action.VideoStop -> onStopVideo?.invoke()
                PnsRemoteProtocol.Action.VideoToggle -> onToggleVideo?.invoke()
                PnsRemoteProtocol.Action.Chapter -> onChapter?.invoke()
                PnsRemoteProtocol.Action.CancelTimer -> cancelTimer()
                PnsRemoteProtocol.Action.Timer -> {
                    val sec = command.normalizedTimerSec
                    val fire =
                        Runnable {
                            synchronized(timerLock) {
                                pendingTimer = null
                                timerArmedSec = 0
                            }
                            onShutter?.invoke()
                        }
                    synchronized(timerLock) {
                        pendingTimer?.let { main.removeCallbacks(it) }
                        pendingTimer = fire
                        timerArmedSec = sec
                    }
                    main.postDelayed(fire, sec * 1000L)
                }
            }
        }
    }

    fun statusJson(host: String, port: Int): String =
        PnsRemoteProtocol.statusJson(
            recording = PnsForegroundCapture.isRecording,
            photoPrimary = photoPrimary,
            ready = sessionReady,
            host = host,
            port = port,
            timerSec = timerArmedSec,
            hdmi = PnsExternalOutput.statusLine(),
        )
}
