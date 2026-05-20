package dev.pointandshoot

/**
 * Sprint **13V.12** — when the preview should show battery drain + thermal HUD.
 *
 * High-drain video paths: HFR preview target (≥ [HFR_FPS_THRESHOLD]) or research DCG HDR session.
 */
object PreviewHighDrainMode {
    /** Preview / record FPS at or above this is treated as HFR for the power HUD. */
    const val HFR_FPS_THRESHOLD = 120

    data class Context(
        val videoPrimary: Boolean,
        val isRecording: Boolean,
        val selectedFps: Int,
        val enableResearchDcgHdr: Boolean,
        /** ADB `pns_preview_force_power_thermal` — gate / debug without changing FPS. */
        val adbForceOverlay: Boolean = false,
    )

    fun isHighDrain(c: Context): Boolean {
        if (c.adbForceOverlay) return true
        if (!c.videoPrimary && !c.isRecording) return false
        return c.selectedFps >= HFR_FPS_THRESHOLD || c.enableResearchDcgHdr
    }

    fun shouldShowPowerThermalOverlay(c: Context, hudEnabled: Boolean): Boolean {
        if (!hudEnabled) return false
        if (c.adbForceOverlay) return true
        if (!c.videoPrimary && !c.isRecording) return false
        return c.selectedFps >= HFR_FPS_THRESHOLD || c.enableResearchDcgHdr
    }
}
