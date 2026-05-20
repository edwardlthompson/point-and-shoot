package dev.pointandshoot.fleet

/**
 * Sprint **13.3g** — documents what “Standard / ProShot” still save means on OP13.
 * Host tests assert these flags; runtime code reads [OnePlus13FleetPolicy].
 */
object ProShotPipelineContract {

    /** Leaf DNG: framework [android.hardware.camera2.DngCreator] only (no post-save TIFF reconcile). */
    fun leafPostSaveTiffReconcileEnabled(sessionCameraId: String): Boolean {
        if (!OnePlus13FleetPolicy.appliesToDevice()) return false
        if (!OnePlus13FleetPolicy.useProShotPureDngSave()) return true
        if (OnePlus13FleetPolicy.useWideLeafCalibrationForAuxDng() &&
            sessionCameraId in
                setOf(
                    OnePlus13FleetPolicy.CANONICAL_UW,
                    OnePlus13FleetPolicy.CANONICAL_TELE,
                )
        ) {
            return true
        }
        return LeafDngHalReconcile.shouldReconcileLeafDngMetadata(sessionCameraId)
    }

    fun wideLeafCalibrationEnabled(): Boolean = OnePlus13FleetPolicy.useWideLeafCalibrationForAuxDng()

    fun stillPrecaptureEnabled(): Boolean = OnePlus13FleetPolicy.useProShotStillPrecapture()

    fun manualExposureLatchOnStill(sessionCameraId: String): Boolean =
        OnePlus13FleetPolicy.proShotLatchManualExposureOnStill(sessionCameraId)
}
