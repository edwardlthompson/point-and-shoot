package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest

/**
 * Face-detect mode on still requests.
 *
 * ProShot `C0353b0` never sets [CaptureRequest.STATISTICS_FACE_DETECT_MODE] on stills (no FACE keys
 * in the still path). Mirroring FULL face detect onto RAW stills left `faceMode=1` in
 * `PNS.CaptureStill` while Bayer R/G stayed low vs ProShot — USB-bisect OFF on RAW stills.
 */
object StillCaptureFaceDetectParity {
    /** When true, RAW / pure-HAL stills force face-detect OFF (ProShot still footprint). */
    const val FORCE_OFF_ON_RAW_STILL: Boolean = true

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
        forceOffForRawStill: Boolean = FORCE_OFF_ON_RAW_STILL,
    ) {
        val mode =
            if (forceOffForRawStill || automationSuppressFacePipeline || !faceHudEnabled) {
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
            } else {
                pickMode(chars)
            }
        req.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mode)
    }
}
