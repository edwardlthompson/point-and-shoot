package dev.pointandshoot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-wide preview record flag for launch dialogs (not persisted). */
object PnsForegroundCapture {
    var isRecording: Boolean by mutableStateOf(false)

    var settingsOpen: Boolean by mutableStateOf(false)

    var aboutOpen: Boolean by mutableStateOf(false)

    var intervalometerRunning: Boolean by mutableStateOf(false)

    var selfTimerRunning: Boolean by mutableStateOf(false)

    var holdBurstActive: Boolean by mutableStateOf(false)

    var stillQueueBusy: Boolean by mutableStateOf(false)

    var bracketBusy: Boolean by mutableStateOf(false)

    var nightScapeBusy: Boolean by mutableStateOf(false)

    var installDialogOpen: Boolean by mutableStateOf(false)

    val overlayBlocksLaunchPrompts: Boolean
        get() = settingsOpen || aboutOpen
}
