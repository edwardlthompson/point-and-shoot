package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.location.Location
import android.util.Log

/**
 * Optional still-capture keys for the preview engine RAW+JPEG path (`BUILD_PLAN.md` Sprint 4.4).
 *
 * All setters are guarded by [CameraCharacteristics.getAvailableCaptureRequestKeys] and wrapped in
 * [runCatching] so OEM HAL quirks cannot take down capture.
 */
object PreviewStillCaptureHints {
    private const val TAG = "PNS.StillHints"

    /** Normalizes degrees for [CaptureRequest.JPEG_ORIENTATION] (0..359). */
    internal fun normalizeOrientationDegrees(deg: Int): Int = ((deg % 360) + 360) % 360

    /**
     * Enables zero-shutter-lag when the HAL advertises [CaptureRequest.CONTROL_ENABLE_ZSL] and the
     * shot is a normal (non–manual-sensor) JPEG-capable still — same policy as
     * [CaptureLatencyProbeScreen] latency A/B.
     *
     * **RAW + JPEG dual-surface stills:** pass [wantZsl] `false`. Several OEM HALs return no RAW
     * image, `onCaptureFailed`, or other errors when ZSL is enabled on the same request as a RAW
     * target ([PreviewEngineScreen.PreviewController.captureRawStill] / bracket RAW path).
     */
    fun applyZslIfCompatible(
        builder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics,
        wantZsl: Boolean,
        manualSensorStill: Boolean,
    ) {
        if (!wantZsl || manualSensorStill) return
        val keys = characteristics.availableCaptureRequestKeys ?: return
        if (!keys.contains(CaptureRequest.CONTROL_ENABLE_ZSL)) return
        runCatching { builder.set(CaptureRequest.CONTROL_ENABLE_ZSL, true) }
            .onFailure { Log.w(TAG, "CONTROL_ENABLE_ZSL: ${it.message}") }
    }

    fun applyJpegOrientationIfSupported(
        builder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics,
        surfaceRotation: Int,
    ) {
        val keys = characteristics.availableCaptureRequestKeys ?: return
        if (!keys.contains(CaptureRequest.JPEG_ORIENTATION)) return
        val deg =
            normalizeOrientationDegrees(
                RawCaptureSupport.orientationClockwiseDegForDng(characteristics, surfaceRotation),
            )
        runCatching { builder.set(CaptureRequest.JPEG_ORIENTATION, deg) }
            .onFailure { Log.w(TAG, "JPEG_ORIENTATION: ${it.message}") }
    }

    fun applyJpegGpsIfSupported(
        builder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics,
        location: Location?,
    ) {
        if (location == null) return
        val keys = characteristics.availableCaptureRequestKeys ?: return
        if (!keys.contains(CaptureRequest.JPEG_GPS_LOCATION)) return
        runCatching { builder.set(CaptureRequest.JPEG_GPS_LOCATION, location) }
            .onFailure { Log.w(TAG, "JPEG_GPS_LOCATION: ${it.message}") }
    }
}
