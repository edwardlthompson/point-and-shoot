package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log

/**
 * Optional [CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST] for RAW stills when the HAL exposes
 * the key and range (`BUILD_PLAN.md` Milestone 4 Sprint 4.4).
 *
 * Disabled when the operator forces manual ISO or manual exposure (readout row) so the pipeline
 * stays predictable. When [HudSettings.enablePostRawSensitivityBoost] is off, the key is not set
 * (HAL default).
 */
object PreviewPostRawSensitivity {
    private const val TAG = "PNS.PostRawBoost"

    internal fun pickBoostMidpoint(lower: Int, upper: Int): Int {
        val mid = (lower.toLong() + upper.toLong()) / 2L
        return mid.toInt().coerceIn(lower, upper)
    }

    fun applyIfCompatible(
        builder: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        settings: HudSettings,
        manualIsoOverride: Int?,
        manualExposureNsOverride: Long?,
    ) {
        if (!settings.enablePostRawSensitivityBoost) return
        if (manualIsoOverride != null || manualExposureNsOverride != null) return
        val keys = chars.availableCaptureRequestKeys ?: return
        if (!keys.contains(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST)) return
        val range = chars.get(CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE) ?: return
        val v = pickBoostMidpoint(range.lower, range.upper)
        runCatching { builder.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, v) }
            .onFailure { Log.w(TAG, "CONTROL_POST_RAW_SENSITIVITY_BOOST: ${it.message}") }
    }
}
