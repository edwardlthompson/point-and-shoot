package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest

/**
 * Capture-time hints for **linear-ish RAW** stills (LYT-style sensors): avoid heavy spatial NR /
 * edge enhancement that can interact badly with preserved highlights when the ISP later tone-maps.
 *
 * This does **not** disable OEM “Master Mode” pipelines globally — it only sets keys the Camera2
 * HAL honors on this [CaptureRequest].
 */
object RawStillProcessingHints {

    fun applyLinearRawFriendlyProcessing(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        val edgeModes = chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES) ?: intArrayOf()
        when {
            edgeModes.contains(CaptureRequest.EDGE_MODE_FAST) ->
                req.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
            edgeModes.contains(CaptureRequest.EDGE_MODE_OFF) ->
                req.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
        }
        val nrModes =
            chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) ?: intArrayOf()
        when {
            nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_FAST) ->
                req.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
            nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF) ->
                req.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
        }
    }

    /**
     * Locks AE for this **single** capture request when the device reports AE lock is available.
     * Useful with highlight-priority workflows so the ISP does not pump shadows during the shot.
     */
    fun applyAeLockIfAvailable(req: CaptureRequest.Builder, chars: CameraCharacteristics, lock: Boolean) {
        if (!lock) return
        val ok = chars.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) ?: false
        if (ok) {
            req.set(CaptureRequest.CONTROL_AE_LOCK, true)
        }
    }
}
