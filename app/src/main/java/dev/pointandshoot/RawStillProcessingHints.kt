package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import dev.pointandshoot.fleet.LegacyFleetPolicy
import dev.pointandshoot.fleet.StillDngBackend
import dev.pointandshoot.fleet.StillDngBackendPolicy

/**
 * Capture-time hints for **linear-ish RAW** stills (LYT-style sensors): avoid heavy spatial NR /
 * edge enhancement that can interact badly with preserved highlights when the ISP later tone-maps.
 *
 * This does **not** disable OEM “Master Mode” pipelines globally — it only sets keys the Camera2
 * HAL honors on this [CaptureRequest].
 */
object RawStillProcessingHints {
    private const val TAG = "PNS.ReferenceAppStill"

    /** Preview exposure sampled **before** [CameraCaptureSession.stopRepeating] for ReferenceCam leaf stills. */
    data class ReferenceAppExposureLatch(
        val iso: Int,
        val expNs: Long,
        val frameNs: Long?,
        val aeExposureCompensation: Int?,
        val postRawSensitivityBoost: Int?,
    )

    fun snapshotReferenceAppExposure(preview: TotalCaptureResult?): ReferenceAppExposureLatch? {
        val iso = preview?.get(CaptureResult.SENSOR_SENSITIVITY) ?: return null
        val expNs = preview?.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return null
        return ReferenceAppExposureLatch(
            iso = iso,
            expNs = expNs,
            frameNs = preview.get(CaptureResult.SENSOR_FRAME_DURATION),
            aeExposureCompensation = preview.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION),
            postRawSensitivityBoost = preview.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST),
        )
    }

    fun applyLinearRawFriendlyProcessing(req: CaptureRequest.Builder, chars: CameraCharacteristics) {
        if (LegacyFleetPolicy.appliesToDevice() &&
            StillCaptureIqPolicy.isLeafBackCharacteristics(chars)
        ) {
            return
        }
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

    /**
     * ReferenceCam-style leaf still on LegacySku: keep **HAL still metering** on [TEMPLATE_STILL_CAPTURE],
     * latch preview AE compensation / post-raw boost, and AE-lock when available.
     *
     * USB May 2026: copying preview ISO + [CONTROL_AE_MODE_OFF] made DNG EXIF match ReferenceCam but RAW
     * Bayer means stayed ~3× darker (metadata vs sensor integration mismatch on this HAL).
     */
    fun applyReferenceAppPreviewExposureFromResult(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        sessionCameraId: String,
        previewResult: TotalCaptureResult?,
        latchManualExposureFromPreview: Boolean = false,
        exposureLatch: ReferenceAppExposureLatch? = null,
    ) {
        if (!LegacyFleetPolicy.useReferenceAppPureDngSave()) return
        if (StillDngBackendPolicy.active() != StillDngBackend.FRAMEWORK_REFERENCEAPP) return
        if (!StillCaptureIqPolicy.isLeafBackCharacteristics(chars)) return
        val latch = exposureLatch
        val previewIso =
            latch?.iso ?: previewResult?.get(CaptureResult.SENSOR_SENSITIVITY)
        val previewExpNs =
            latch?.expNs ?: previewResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val baseEv =
            latch?.aeExposureCompensation
                ?: previewResult?.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)
                ?: 0
        val evOffset = LegacyFleetPolicy.proShotStillAeExposureCompensationSteps(sessionCameraId)
        val ev = baseEv + evOffset
        val evRange =
            chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val clampedEv =
            if (evRange != null) {
                ev.coerceIn(evRange.lower, evRange.upper)
            } else {
                ev
            }
        req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, clampedEv)
        val requestKeys = chars.availableCaptureRequestKeys ?: emptyList()
        val boost = latch?.postRawSensitivityBoost
            ?: previewResult?.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST)
        if (requestKeys.contains(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST)) {
            boost?.let {
                runCatching {
                    req.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, it)
                }
            }
        }
        val aeModes =
            chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        if (
            latchManualExposureFromPreview &&
            previewIso != null &&
            previewExpNs != null &&
            aeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)
        ) {
            req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            req.set(CaptureRequest.CONTROL_AE_LOCK, false)
            req.set(CaptureRequest.SENSOR_SENSITIVITY, previewIso)
            req.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewExpNs)
            val frameNs =
                latch?.frameNs ?: previewResult?.get(CaptureResult.SENSOR_FRAME_DURATION)
            frameNs?.let { req.set(CaptureRequest.SENSOR_FRAME_DURATION, it) }
            Log.i(
                TAG,
                "referencecam leaf still: latched manual exposure iso=$previewIso expNs=$previewExpNs " +
                    "postPrecapture=${latch != null}",
            )
            return
        }
        if (previewResult == null && latch == null) return
        applyAeLockIfAvailable(req, chars, lock = true)
        Log.i(
            TAG,
            "referencecam leaf still: HAL metering + AE lock previewIso=$previewIso previewExpNs=$previewExpNs " +
                "postRawBoost=$boost aeComp=$clampedEv (base=$baseEv offset=$evOffset)",
        )
    }
}
