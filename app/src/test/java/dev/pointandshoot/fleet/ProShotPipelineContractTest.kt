package dev.pointandshoot.fleet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProShotPipelineContractTest {

    @Test
    fun standardProShot_leafPostSaveReconcile_offOnOp13() {
        if (!OnePlus13FleetPolicy.appliesToDevice()) return
        assertFalse(ProShotPipelineContract.wideLeafCalibrationEnabled())
        assertFalse(ProShotPipelineContract.stillPrecaptureEnabled())
        assertFalse(ProShotPipelineContract.manualExposureLatchOnStill(OnePlus13FleetPolicy.CANONICAL_TELE))
        assertFalse(OnePlus13FleetPolicy.useProShotReferenceCalibration())
        assertFalse(OnePlus13FleetPolicy.useOp13LeafAuxColorReconcile())
        for (camId in
            listOf(
                OnePlus13FleetPolicy.CANONICAL_UW,
                OnePlus13FleetPolicy.CANONICAL_WIDE,
                OnePlus13FleetPolicy.CANONICAL_TELE,
            )
        ) {
            assertFalse(ProShotPipelineContract.leafPostSaveTiffReconcileEnabled(camId))
        }
    }


    @Test
    fun leafPostSaveReconcile_proShotPure_uwAndTeleAuxColorFlag() {
        assertTrue(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_PROSHOT,
                sessionCameraId = OnePlus13FleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                uwProShotAsnReconcile = true,
            ),
        )
        assertTrue(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_PROSHOT,
                sessionCameraId = OnePlus13FleetPolicy.CANONICAL_TELE,
                proShotPureDngSave = true,
                uwProShotAsnReconcile = true,
            ),
        )
    }

    @Test
    fun leafPostSaveReconcile_when_wideCalEnabled_onlyAux() {
        val uw =
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_PROSHOT,
                sessionCameraId = OnePlus13FleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                wideLeafCalibrationForAuxDng = true,
            )
        val wide =
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_PROSHOT,
                sessionCameraId = OnePlus13FleetPolicy.CANONICAL_WIDE,
                proShotPureDngSave = true,
                wideLeafCalibrationForAuxDng = true,
            )
        assertFalse(wide)
        assertEquals(true, uw)
    }

    @Test
    fun shipped_wideCalAndPrecapture_disabled() {
        assertFalse(OnePlus13FleetPolicy.useWideLeafCalibrationForAuxDng())
        assertFalse(OnePlus13FleetPolicy.useProShotStillPrecapture())
        assertFalse(OnePlus13FleetPolicy.proShotLeafStillSkipsStopRepeating(OnePlus13FleetPolicy.CANONICAL_TELE))
    }
}
