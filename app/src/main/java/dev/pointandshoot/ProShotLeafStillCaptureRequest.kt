package dev.pointandshoot

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import dev.pointandshoot.fleet.FleetCameraProfile
import dev.pointandshoot.fleet.OnePlus13FleetPolicy

/**
 * Leaf RAW still [CaptureRequest] keys aligned with ProShot decompile (`l0.C0353b0` still path,
 * `docs/PROSHOT_APK_FLEET_ANALYSIS.md` §5).
 *
 * ProShot does **not** latch readout manual ISO, AE-lock from preview, post-save TIFF color surgery,
 * or P&S face-priority metering on the still frame — it uses **HAL AE** on [CameraDevice.TEMPLATE_STILL_CAPTURE]
 * plus lens-shading map + still IQ (edge / NR / tonemap / aberration / distortion / shading).
 */
object ProShotLeafStillCaptureRequest {
    private const val TAG = "PNS.ProShotStill"

    fun applies(chars: CameraCharacteristics): Boolean =
        OnePlus13FleetPolicy.useExactProShotLeafStillCaptureRequest() &&
            StillCaptureIqPolicy.isLeafBackCharacteristics(chars)

    internal fun appliesWhen(
        deviceApplies: Boolean,
        chars: CameraCharacteristics,
    ): Boolean =
        OnePlus13FleetPolicy.useExactProShotLeafStillCaptureRequestWhen(deviceApplies) &&
            StillCaptureIqPolicy.isLeafBackCharacteristics(chars)

    /**
     * @param scalerCropRegion Active-array crop for the focal slot (may be full array).
     */
    fun applyToStillCapture(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        profile: FleetCameraProfile?,
        scalerCropRegion: Rect?,
    ) {
        scalerCropRegion
            ?.takeIf { it.width() > 0 && it.height() > 0 }
            ?.let { crop ->
                req.set(CaptureRequest.SCALER_CROP_REGION, crop)
                Log.d(
                    TAG,
                    "SCALER_CROP_REGION=${crop.left},${crop.top}-${crop.right},${crop.bottom}",
                )
            }
        StillCaptureIqPolicy.applyProShotLeafStillIq(req, chars, profile)
        applyContinuousPictureAf(req, chars)
    }

    private fun applyContinuousPictureAf(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
    ) {
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        when {
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) ->
                req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ->
                req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        }
    }
}
