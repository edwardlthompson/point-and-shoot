package dev.pointandshoot

import androidx.compose.runtime.mutableStateOf

/** Tray / banner read webcam without growing PreviewEngineContent's Detekt-baselined signature. */
object PnsWebcamModeBridge {
    val active = mutableStateOf(false)
    var request: ((Boolean) -> Unit)? = null
}
