package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range

/**
 * Optional **OIS** + **preview EIS** keys for the preview engine (`BUILD_PLAN.md` Milestone 4 Sprint 4.4).
 *
 * - [CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE] when [CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION]
 *   lists a non-OFF mode and [HudSettings.enableLensOpticalStabilization] is true.
 * - [CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE] when the HAL advertises ON in
 *   [CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES], the user enables
 *   [HudSettings.enableVideoStabilizationPreview], and we are not in an HFR / high-speed preview path.
 *
 * EIS is **not** applied on still-capture builders (HAL variance / crop); OIS still is when available.
 */
object PreviewStabilization {
    private const val TAG = "PNS.Stabilization"

    /** AE target FPS upper bound at or above this value disables preview EIS (HFR path). */
    private const val HFR_PREVIEW_EIS_DISABLE_FPS = 120

    internal fun pickOpticalStabilizationMode(avail: IntArray): Int? {
        if (avail.isEmpty()) return null
        return when {
            avail.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) ->
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
            else -> null
        }
    }

    internal fun pickVideoStabilizationMode(avail: IntArray): Int? {
        if (avail.isEmpty()) return null
        return when {
            avail.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) ->
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
            else -> null
        }
    }

    /**
     * @param previewFpsRange AE target FPS range for this repeating request (null = leave HFR detection to template only).
     * @param isStillCapture when true, skips EIS (still pipeline).
     */
    fun applyToRequest(
        builder: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        settings: HudSettings,
        previewFpsRange: Range<Int>?,
        manualSensor: Boolean,
        isStillCapture: Boolean,
    ) {
        val keys = chars.availableCaptureRequestKeys ?: return
        val hfrPreview =
            previewFpsRange != null && previewFpsRange.upper >= HFR_PREVIEW_EIS_DISABLE_FPS

        if (settings.enableLensOpticalStabilization &&
            keys.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE)
        ) {
            val avail = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
            val mode = pickOpticalStabilizationMode(avail)
            if (mode != null) {
                runCatching { builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, mode) }
                    .onFailure { Log.w(TAG, "LENS_OPTICAL_STABILIZATION_MODE: ${it.message}") }
            }
        }

        val allowEis =
            settings.enableVideoStabilizationPreview &&
                !isStillCapture &&
                !manualSensor &&
                !hfrPreview &&
                keys.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE)
        if (allowEis) {
            val avail = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
            val mode = pickVideoStabilizationMode(avail)
            if (mode != null) {
                runCatching { builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, mode) }
                    .onFailure { Log.w(TAG, "CONTROL_VIDEO_STABILIZATION_MODE: ${it.message}") }
            }
        }
    }
}
