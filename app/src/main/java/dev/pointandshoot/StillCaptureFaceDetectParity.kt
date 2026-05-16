package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest

/**
 * Mirrors preview [CaptureRequest.STATISTICS_FACE_DETECT_MODE] onto still requests when the face HUD
 * is enabled, so preview vs still metadata does not diverge ([StillCaptureBoundaryDiag] `face_detect_mode_delta`).
 */
object StillCaptureFaceDetectParity {
    fun pickMode(chars: CameraCharacteristics): Int {
        val modes =
            chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
                ?: intArrayOf(CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)
        return when {
            modes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL) ->
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL
            modes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) ->
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE
            else -> CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
        }
    }

    fun applyWhenFaceHudEnabled(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        faceHudEnabled: Boolean,
        automationSuppressFacePipeline: Boolean,
    ) {
        val mode =
            if (automationSuppressFacePipeline || !faceHudEnabled) {
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
            } else {
                pickMode(chars)
            }
        req.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mode)
    }
}
