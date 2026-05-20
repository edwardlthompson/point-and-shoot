package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import dev.pointandshoot.fleet.FleetCameraProfile
import dev.pointandshoot.fleet.FleetCameraRole
import dev.pointandshoot.fleet.StillDngBackend
import dev.pointandshoot.fleet.StillDngBackendPolicy

/**
 * MotionCam-inspired still path while [StillDngBackend.MOTIONCAM_NATIVE] is unimplemented.
 *
 * MotionCam Pro encodes DNG in native code ([RawEncoder::encode_DNG10/12]); P&S still uses
 * [android.hardware.camera2.DngCreator] but adjusts RAW negotiation + still IQ to match MotionCam's
 * per-device profile model ([NativeDeviceSpecificProfile], vignette / shading prefs).
 */
object MotionCamInspiredStillPolicy {

    private const val TAG = "PNS.MotionCamStill"

    fun isActive(): Boolean = StillDngBackendPolicy.active() == StillDngBackend.MOTIONCAM_INSPIRED

    fun logSessionContext(cameraId: String) {
        if (!isActive()) return
        Log.i(
            TAG,
            "still backend=MOTIONCAM_INSPIRED cameraId=$cameraId writer=DngCreator " +
                "rawPick=RAW_SENSOR@activeArray leafReconcile=off proShotOptical=off",
        )
    }

    /** MotionCam still path does not use ProShot aberration/distortion on the still request. */
    fun applyProShotOpticalCorrectionOnLeaf(): Boolean =
        applyProShotOpticalCorrectionOnLeafWhen(StillDngBackendPolicy.active())

    internal fun applyProShotOpticalCorrectionOnLeafWhen(backend: StillDngBackend): Boolean =
        backend != StillDngBackend.MOTIONCAM_INSPIRED

    /**
     * Tele stills: request lens shading **map** only (no [CaptureRequest.SHADING_MODE]) — full shading
     * mode broke HAL still capture on CPH2655 tele (capture reason=0); matches MotionCam map-first IQ.
     */
    fun teleLensShadingMapOnly(profile: FleetCameraProfile?): Boolean =
        teleLensShadingMapOnlyWhen(StillDngBackendPolicy.active(), profile)

    internal fun teleLensShadingMapOnlyWhen(
        backend: StillDngBackend,
        profile: FleetCameraProfile?,
    ): Boolean {
        if (backend != StillDngBackend.MOTIONCAM_INSPIRED) return false
        return profile?.role == FleetCameraRole.TELE || profile?.role == FleetCameraRole.LONG_TELE
    }
}
