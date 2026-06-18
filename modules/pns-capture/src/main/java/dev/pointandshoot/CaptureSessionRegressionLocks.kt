package dev.pointandshoot

/**
 * Locked capture-session regression constants (REG-20260513-001/002, REG-20260512-001).
 *
 * See [docs/REVERTED_FEATURES_RESTORE_LIST.md] §8 and [docs/PNS_TECHNICAL_SETTINGS.md].
 */
object CaptureSessionRegressionLocks {
    /**
     * §4a — REGULAR preview session [android.hardware.camera2.params.OutputConfiguration.setStreamUseCase]
     * hints. Stays **false** on legacy-class fleet until USB proof (RAW still timeout / `ERROR_CAMERA_DEVICE`).
     */
    const val REGULAR_SESSION_STREAM_HINTS_ENABLED: Boolean = false

    /**
     * Face-pipeline suppression for automation: **only** when an ADB bracket pattern is seeded —
     * not for sequential RAW-only (`pns_preview_raw_count`) alone.
     */
    fun automationSuppressFacePipeline(adbBracketPattern: BracketPattern?): Boolean =
        adbBracketPattern != null
}
