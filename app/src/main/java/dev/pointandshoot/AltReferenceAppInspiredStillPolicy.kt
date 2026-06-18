package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import dev.pointandshoot.fleet.FleetCameraProfile
import dev.pointandshoot.fleet.FleetCameraRole
import dev.pointandshoot.StillDngBackend
import dev.pointandshoot.fleet.StillDngBackendPolicy

/**
 * AltReferenceApp-inspired still path while [StillDngBackend.ALTREFERENCEAPP_NATIVE] is unimplemented.
 *
 * AltReferenceApp encodes DNG in native code ([RawEncoder::encode_DNG10/12]); P&S still uses
 * [android.hardware.camera2.DngCreator] but adjusts RAW negotiation + still IQ to match AltReferenceApp's
 * per-device profile model ([NativeDeviceSpecificProfile], vignette / shading prefs).
 */
object AltReferenceAppInspiredStillPolicy {

    private const val TAG = "PNS.AltReferenceAppStill"

    fun isActive(): Boolean = StillDngBackendPolicy.active() == StillDngBackend.ALTREFERENCEAPP_INSPIRED

    fun logSessionContext(cameraId: String) {
        if (!isActive()) return
        Log.i(
            TAG,
            "still backend=ALTREFERENCEAPP_INSPIRED cameraId=$cameraId writer=DngCreator " +
                "rawPick=RAW_SENSOR@activeArray leafReconcile=off proShotOptical=off",
        )
    }

    /** AltReferenceApp still path does not use ReferenceCam aberration/distortion on the still request. */
    fun applyReferenceAppOpticalCorrectionOnLeaf(): Boolean =
        applyReferenceAppOpticalCorrectionOnLeafWhen(StillDngBackendPolicy.active())

    internal fun applyReferenceAppOpticalCorrectionOnLeafWhen(backend: StillDngBackend): Boolean =
        backend != StillDngBackend.ALTREFERENCEAPP_INSPIRED

    /**
     * Tele stills: request lens shading **map** only (no [CaptureRequest.SHADING_MODE]) — full shading
     * mode broke HAL still capture on legacy tele (capture reason=0); matches AltReferenceApp map-first IQ.
     */
    fun teleLensShadingMapOnly(profile: FleetCameraProfile?): Boolean =
        teleLensShadingMapOnlyWhen(StillDngBackendPolicy.active(), profile)

    internal fun teleLensShadingMapOnlyWhen(
        backend: StillDngBackend,
        profile: FleetCameraProfile?,
    ): Boolean {
        if (backend != StillDngBackend.ALTREFERENCEAPP_INSPIRED) return false
        return profile?.role == FleetCameraRole.TELE || profile?.role == FleetCameraRole.LONG_TELE
    }
}
