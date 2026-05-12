package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log

/**
 * Sets [CaptureRequest.CONTROL_AE_ANTIBANDING_MODE] when the HAL advertises the key
 * (`BUILD_PLAN.md` Sprint 4.4).
 */
object PreviewAeAntibanding {
    private const val TAG = "PNS.Antibanding"

    /** Exposed for JVM tests (no [CameraCharacteristics] required). */
    internal fun pickAntibandingMode(avail: IntArray): Int? {
        if (avail.isEmpty()) return null
        return when {
            avail.contains(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO) ->
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
            avail.contains(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ) ->
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ
            avail.contains(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ) ->
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ
            else -> avail[0]
        }
    }

    fun applyToRequest(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        if (chars.availableCaptureRequestKeys?.contains(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE) != true) {
            return
        }
        val avail = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES) ?: return
        val mode = pickAntibandingMode(avail) ?: return
        runCatching { req.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, mode) }
            .onFailure { Log.w(TAG, "set antibanding: ${it.message}") }
    }
}
