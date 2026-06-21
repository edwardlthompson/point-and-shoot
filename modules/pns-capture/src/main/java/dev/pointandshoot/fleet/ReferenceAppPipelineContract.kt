package dev.pointandshoot.fleet

import dev.pointandshoot.LeafDngFleetPolicies
import dev.pointandshoot.PureHalDngSavePolicy

/**
 * Sprint **13.3g** — documents what “Standard / ReferenceCam” still save means on LegacyDevice.
 * Host tests assert these flags; runtime code reads [LeafDngFleetPolicies.active].
 */
object ReferenceAppPipelineContract {

    /** Leaf DNG: framework [android.hardware.camera2.DngCreator] only (no post-save TIFF reconcile). */
    fun leafPostSaveTiffReconcileEnabled(sessionCameraId: String): Boolean {
        if (PureHalDngSavePolicy.ENABLED) return false
        if (!LeafDngFleetPolicies.active.appliesToDevice()) return false
        if (!LeafDngFleetPolicies.active.useReferenceAppPureDngSave()) return true
        if (LeafDngFleetPolicies.active.useWideLeafCalibrationForAuxDng() &&
            sessionCameraId in
                setOf(
                    LeafDngFleetPolicies.active.canonicalUw,
                    LeafDngFleetPolicies.active.canonicalTele,
                )
        ) {
            return true
        }
        return LeafDngHalReconcile.shouldReconcileLeafDngMetadata(sessionCameraId)
    }

    fun wideLeafCalibrationEnabled(): Boolean = LeafDngFleetPolicies.active.useWideLeafCalibrationForAuxDng()

    fun stillPrecaptureEnabled(): Boolean = LeafDngFleetPolicies.active.useReferenceAppStillPrecapture()

    fun manualExposureLatchOnStill(sessionCameraId: String): Boolean =
        (LeafDngFleetPolicies.active.useWideLeafCalibrationForAuxDng() &&
            sessionCameraId in setOf(
                LeafDngFleetPolicies.active.canonicalUw,
                LeafDngFleetPolicies.active.canonicalTele,
            ))
}
