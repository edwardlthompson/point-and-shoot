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

    /**
     * Composite MP4: front top + rear bottom in a **portrait** frame so stacked GLES matches
     * preview chrome and is not turned into a left/right pair by muxer rotation (90°).
     */
    const val COMPOSITE_RECORD_WIDTH = 1080
    const val COMPOSITE_RECORD_HEIGHT = 1920

    fun compositeRecordSize(): Size = Size(COMPOSITE_RECORD_WIDTH, COMPOSITE_RECORD_HEIGHT)

    /** Dual composite is authored upright; do not apply rear-camera rotation on the muxer. */
    const val COMPOSITE_ORIENTATION_HINT_DEGREES = 0

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
        if (sets.isEmpty()) return false
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
