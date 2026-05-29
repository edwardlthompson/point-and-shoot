package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest

/**
 * Sprint **15.26** — preview AE lock via [CaptureRequest.CONTROL_AE_LOCK] (AF unchanged).
 */
object PreviewAeLock {
    fun isAvailable(chars: CameraCharacteristics): Boolean =
        chars.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true

    /** Pure resolver for unit tests and [applyToRequest]. */
    internal fun requestAeLockValue(locked: Boolean, lockAvailable: Boolean): Boolean =
        locked && lockAvailable

    /**
     * @return true when [CaptureRequest.CONTROL_AE_LOCK] was set true on [req].
     */
    fun applyToRequest(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        locked: Boolean,
    ): Boolean {
        val value = requestAeLockValue(locked, isAvailable(chars))
        req.set(CaptureRequest.CONTROL_AE_LOCK, value)
        return value
    }
}
