package dev.pointandshoot

/**
 * Tray / QS shutter behavior: single capture, self-timer, or burst sequence.
 * Timer and burst are mutually exclusive; [Single] clears both.
 */
enum class ShutterCaptureMode {
    Single,
    Timer,
    Burst,
    ;

    companion object {
        fun current(
            chrome: PreviewChromePreferences,
            hud: HudSettings,
        ): ShutterCaptureMode =
            when {
                hud.burstModeEnabled -> Burst
                chrome.selfTimerDelaySec > 0 -> Timer
                else -> Single
            }

        fun cycle(current: ShutterCaptureMode): ShutterCaptureMode =
            when (current) {
                Single -> Timer
                Timer -> Burst
                Burst -> Single
            }
    }
}

fun applyShutterCaptureMode(
    mode: ShutterCaptureMode,
    chromePrefs: PreviewChromePreferencesState,
    hudState: HudSettingsState,
    timerSec: Int? = null,
) {
    val hud = hudState.current
    when (mode) {
        ShutterCaptureMode.Single -> {
            chromePrefs.updateMutate { it.copy(selfTimerDelaySec = 0) }
            hudState.update(hud.copy(burstModeEnabled = false))
        }
        ShutterCaptureMode.Timer -> {
            val sec =
                PreviewChromePreferences.normalizeSelfTimerDelaySec(
                    timerSec ?: chromePrefs.current.selfTimerDelaySec.let { if (it > 0) it else 3 },
                )
            chromePrefs.updateMutate { it.copy(selfTimerDelaySec = sec) }
            hudState.update(hud.copy(burstModeEnabled = false))
        }
        ShutterCaptureMode.Burst -> {
            chromePrefs.updateMutate { it.copy(selfTimerDelaySec = 0) }
            hudState.update(hud.copy(burstModeEnabled = true))
        }
    }
}

fun shutterCaptureModeLabel(
    mode: ShutterCaptureMode,
    chrome: PreviewChromePreferences,
): String =
    when (mode) {
        ShutterCaptureMode.Single -> "Single"
        ShutterCaptureMode.Timer -> "Timer ${chrome.selfTimerDelaySec}s"
        ShutterCaptureMode.Burst -> "Burst"
    }
