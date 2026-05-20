package dev.pointandshoot.fleet

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import android.util.Log
import dev.pointandshoot.DngForwardMatrixFix
import dev.pointandshoot.DngSaveBisectState

/**
 * CPH2655 leaf UW/tele: HAL still [CaptureResult.COLOR_CORRECTION_GAINS] often match the **wide**
 * camera. ProShot aligns AWB on the still **request**; we scale gains before capture only (no post-save
 * TIFF edits when [OnePlus13FleetPolicy.useProShotPureDngSave] — shipped OP13).
 *
 * See `scripts/compute_wb_scale.py` and [DngForwardMatrixFix.getWbCorrection].
 */
object Op13LeafStillColorCorrection {
    private const val TAG = "PNS.Op13StillColor"

    @Volatile
    private var pendingCorrectedGains: Pair<String, RggbChannelVector>? = null

    /** Bisect-only: [LeafDngHalReconcile] ASN TIFF patch when [OnePlus13FleetPolicy.useProShotPureDngSave] is false. */
    fun takePendingCorrectedGains(sessionCameraId: String): RggbChannelVector? {
        val pending = pendingCorrectedGains ?: return null
        if (pending.first != sessionCameraId) return null
        pendingCorrectedGains = null
        return pending.second
    }

    fun appliesCaptureTimeGains(sessionCameraId: String): Boolean =
        appliesCaptureTimeGainsWhen(OnePlus13FleetPolicy.appliesToDevice(), sessionCameraId)

    internal fun appliesCaptureTimeGainsWhen(
        deviceApplies: Boolean,
        sessionCameraId: String,
    ): Boolean {
        if (!deviceApplies) return false
        if (OnePlus13FleetPolicy.useProShotPureDngSave()) return false
        if (DngSaveBisectState.skipOp13CaptureTimeColorGains) return false
        return sessionCameraId == OnePlus13FleetPolicy.CANONICAL_UW ||
            sessionCameraId == OnePlus13FleetPolicy.CANONICAL_TELE
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
        val modes = chars.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES) ?: intArrayOf()
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
        if (!OnePlus13FleetPolicy.useProShotPureDngSave()) {
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
