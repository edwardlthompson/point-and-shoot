package dev.pointandshoot

/**
 * Sprint **15.12** — when RAW+JPEG companion, defer shutter haptic until tonal still completes.
 */
object PreviewCaptureHapticsPolicy {
    fun shouldFireStillTick(rawComplete: Boolean, deferUntilTonal: Boolean): Boolean {
        if (!rawComplete) return false
        return !deferUntilTonal
    }
}
