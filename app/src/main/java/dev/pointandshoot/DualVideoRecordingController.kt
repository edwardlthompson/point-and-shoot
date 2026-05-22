package dev.pointandshoot

import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.util.Size

/**
 * Sprint **14.12** — rear + front **stacked** (LG dual-recording heritage: one MP4, main rear + inset front)
 * into one encoder [android.view.Surface] via GL composite.
 *
 * Session graph and HAL limits: [docs/M14_12_DUAL_VIDEO.md].
 */
object DualVideoRecordingController {
    const val TAG = "PNS.DualVideo"

    /** v1 cap when HAL cannot sustain dual 4K (see design doc). */
    const val V1_MAX_LONG_EDGE_PX = 1920

    const val V1_TARGET_FPS = 30

    const val IS_WIRED = true

    /** Composite MP4: rear top half + front bottom half (1920×1080). */
    fun compositeRecordSize(): Size = Size(1920, 1080)

    /** Front inset height fraction (LG-style PiP uses ~25–30%; stacked v1 uses 50%). */
    const val STACKED_FRONT_HEIGHT_FRACTION = 0.5f

    fun canRunConcurrentRearFront(cm: CameraManager, rearId: String?, frontId: String?): Boolean {
        if (rearId.isNullOrBlank() || frontId.isNullOrBlank()) return false
        val sets =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { cm.concurrentCameraIds }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
        if (sets.isEmpty()) return true
        return sets.any { rearId in it && frontId in it }
    }

    fun logStatus(active: Boolean, rearId: String?, frontId: String?) {
        Log.i(
            TAG,
            "dualVideo=active=$active wired=$IS_WIRED rear=$rearId front=$frontId " +
                "record=${compositeRecordSize().width}x${compositeRecordSize().height}@${V1_TARGET_FPS}fps",
        )
    }
}
