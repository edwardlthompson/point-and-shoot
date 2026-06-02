package dev.pointandshoot.fleet

/**
 * Sprint **13.3g** — documents what “Standard / ReferenceCam” still save means on LegacyDevice.
 * Host tests assert these flags; runtime code reads [LegacyFleetPolicy].
 */
object ProShotPipelineContract {

    /** Leaf DNG: framework [android.hardware.camera2.DngCreator] only (no post-save TIFF reconcile). */
    fun leafPostSaveTiffReconcileEnabled(sessionCameraId: String): Boolean {
        if (!LegacyFleetPolicy.appliesToDevice()) return false
        if (!LegacyFleetPolicy.useProShotPureDngSave()) return true
        if (LegacyFleetPolicy.useWideLeafCalibrationForAuxDng() &&
            sessionCameraId in
                setOf(
                    LegacyFleetPolicy.CANONICAL_UW,
                    LegacyFleetPolicy.CANONICAL_TELE,
                )
        ) {
            return true
        }
        return LeafDngHalReconcile.shouldReconcileLeafDngMetadata(sessionCameraId)
    }

    fun wideLeafCalibrationEnabled(): Boolean = LegacyFleetPolicy.useWideLeafCalibrationForAuxDng()

    fun stillPrecaptureEnabled(): Boolean = LegacyFleetPolicy.useProShotStillPrecapture()

    fun manualExposureLatchOnStill(sessionCameraId: String): Boolean =
        LegacyFleetPolicy.proShotLatchManualExposureOnStill(sessionCameraId)
}
