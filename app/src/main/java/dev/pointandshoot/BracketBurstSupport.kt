package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics

/**
 * Helpers for choosing `CameraCaptureSession.captureBurst` vs sequential `capture`
 * for exposure brackets (see repo `CAPTURE_ARCHITECTURE.md`, bracket lane).
 */
object BracketBurstSupport {

    /**
     * Returns true when a single `captureBurst` can be used for all bracket stops.
     *
     * Constraints:
     * * HAL must advertise [CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE].
     * * [shotCount] must not exceed the RAW still [ImageReader] `maxImages` budget — otherwise the
     *   HAL can fill the queue before the app drains frames (drops / wrong pairing).
     * * Manual sensor-time overrides are kept on the sequential path (less fleet risk).
     */
    fun mayUseSingleCaptureBurst(
        availableCapabilities: IntArray?,
        shotCount: Int,
        readerMaxImages: Int,
        manualSensorBracket: Boolean,
    ): Boolean {
        if (manualSensorBracket) return false
        if (shotCount <= 0 || shotCount > readerMaxImages) return false
        val caps = availableCapabilities ?: return false
        return caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE }
    }
}
