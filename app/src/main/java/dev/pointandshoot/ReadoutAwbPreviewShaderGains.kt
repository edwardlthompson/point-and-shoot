package dev.pointandshoot

import android.hardware.camera2.CaptureRequest

/**
 * RGB multipliers applied in the GLES preview path after sampling the camera external-OES texture.
 * Many devices do not honor [CaptureRequest.CONTROL_AWB_MODE] on the SurfaceTexture preview
 * consumer; these gains give a visible, preset-shaped response on the finder while Camera2
 * requests still drive capture metadata.
 *
 * Values are mild (anchor G at 1) to limit stacking when the HAL *does* apply some correction.
 */
object ReadoutAwbPreviewShaderGains {

    /** Returns a new 3-vector `{r, g, b}` with `g == 1f` (caller must not mutate shared state). */
    fun rgbForMode(mode: Int?): FloatArray =
        when (mode) {
            null,
            CaptureRequest.CONTROL_AWB_MODE_AUTO,
            -> floatArrayOf(1f, 1f, 1f)
            CaptureRequest.CONTROL_AWB_MODE_OFF -> floatArrayOf(1f, 1f, 1f)
            CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> floatArrayOf(0.86f, 1f, 1.22f)
            CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> floatArrayOf(1.08f, 0.94f, 1.05f)
            CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT -> floatArrayOf(0.90f, 1f, 1.14f)
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> floatArrayOf(0.98f, 1f, 1.04f)
            CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> floatArrayOf(0.92f, 1f, 1.14f)
            CaptureRequest.CONTROL_AWB_MODE_TWILIGHT -> floatArrayOf(1.10f, 0.97f, 1.05f)
            CaptureRequest.CONTROL_AWB_MODE_SHADE -> floatArrayOf(0.90f, 1f, 1.16f)
            else -> floatArrayOf(1f, 1f, 1f)
        }
}
