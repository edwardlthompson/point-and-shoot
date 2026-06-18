package dev.pointandshoot.fleet

import dev.pointandshoot.LeafDngFleetPolicies

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import android.util.Log
import dev.pointandshoot.DngForwardMatrixFix
import dev.pointandshoot.getColorCorrectionAvailableModesOrEmpty
import dev.pointandshoot.DngSaveBisectState

/**
 * LegacySku leaf UW/tele: HAL still [CaptureResult.COLOR_CORRECTION_GAINS] often match the **wide**
 * camera. ReferenceCam aligns AWB on the still **request**; we scale gains before capture only (no post-save
 * TIFF edits when [LeafDngFleetPolicies.active.useReferenceAppPureDngSave] — shipped LegacyDevice).
 *
 * See `scripts/compute_wb_scale.py` and [DngForwardMatrixFix.getWbCorrection].
 */
object LegacyLeafStillColorCorrection {
    private const val TAG = "PNS.Op13StillColor"

    @Volatile
    private var pendingCorrectedGains: Pair<String, RggbChannelVector>? = null

    /** Bisect-only: [LeafDngHalReconcile] ASN TIFF patch when [LeafDngFleetPolicies.active.useReferenceAppPureDngSave] is false. */
    fun takePendingCorrectedGains(sessionCameraId: String): RggbChannelVector? {
        val pending = pendingCorrectedGains ?: return null
        if (pending.first != sessionCameraId) return null
        pendingCorrectedGains = null
        return pending.second
    }

    fun appliesCaptureTimeGains(sessionCameraId: String): Boolean =
        appliesCaptureTimeGainsWhen(LeafDngFleetPolicies.active.appliesToDevice(), sessionCameraId)

    fun appliesCaptureTimeGainsWhen(
        deviceApplies: Boolean,
        sessionCameraId: String,
        proShotPureDngSave: Boolean = LeafDngFleetPolicies.active.useReferenceAppPureDngSave(),
        uwReferenceAppAsnReconcile: Boolean = LeafDngFleetPolicies.active.useLegacyLeafAuxColorReconcile(),
        proShotReferenceCalibration: Boolean = LeafDngFleetPolicies.active.useReferenceAppReferenceCalibration(),
    ): Boolean {
        if (!deviceApplies) return false
        if (proShotPureDngSave) {
            // Only UW gets capture-time WB gain correction. Tele's HAL gains appear closer to truth
            // and the static scale table over-corrects on LegacySku (see structural_verify tele ASN).
            return (uwReferenceAppAsnReconcile || proShotReferenceCalibration) &&
                sessionCameraId == LeafDngFleetPolicies.active.canonicalUw
        }
        if (DngSaveBisectState.skipOp13CaptureTimeColorGains) return false
        return sessionCameraId == LeafDngFleetPolicies.active.canonicalUw ||
            sessionCameraId == LeafDngFleetPolicies.active.canonicalTele
    }

    /**
     * When true, [LeafDngHalReconcile] should not rewrite AsShotNeutral (avoid double correction).
     */
    fun applyToStillCaptureRequest(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        sessionCameraId: String,
        previewResult: TotalCaptureResult?,
    ) {
        if (!appliesCaptureTimeGains(sessionCameraId)) return
        val halGains =
            previewResult?.get(CaptureResult.COLOR_CORRECTION_GAINS)
                ?: return
        val corrected =
            LeafDngHalReconcile.applyHalWbGainCorrection(halGains, sessionCameraId)
        val modes = chars.getColorCorrectionAvailableModesOrEmpty()
        when {
            modes.contains(CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX) -> {
                req.set(
                    CaptureRequest.COLOR_CORRECTION_MODE,
                    CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX,
                )
                chars.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)?.let { cst ->
                    req.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, cst)
                }
            }
            modes.contains(CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY) ->
                req.set(
                    CaptureRequest.COLOR_CORRECTION_MODE,
                    CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY,
                )
            modes.contains(CameraMetadata.COLOR_CORRECTION_MODE_FAST) ->
                req.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_FAST)
        }
        req.set(CaptureRequest.COLOR_CORRECTION_GAINS, corrected)
        val stashPending =
            !LeafDngFleetPolicies.active.useReferenceAppPureDngSave() ||
                ((LeafDngFleetPolicies.active.useLegacyLeafAuxColorReconcile() ||
                    LeafDngFleetPolicies.active.useReferenceAppReferenceCalibration()) &&
                    sessionCameraId == LeafDngFleetPolicies.active.canonicalUw)
        if (stashPending) {
            pendingCorrectedGains = sessionCameraId to corrected
        }
        val wb = DngForwardMatrixFix.getWbCorrection(Build.MODEL?.lowercase().orEmpty(), sessionCameraId)
        Log.i(
            TAG,
            "still COLOR_CORRECTION_GAINS corrected cam=$sessionCameraId " +
                "halR=${halGains.red} halB=${halGains.blue} " +
                "outR=${corrected.red} outB=${corrected.blue} " +
                "scaleR=${wb?.scaleR ?: 1f} scaleB=${wb?.scaleB ?: 1f}",
        )
    }
}
