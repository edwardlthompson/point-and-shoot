package dev.pointandshoot.fleet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProShotPipelineContractTest {

    @Test
    fun standardProShot_leafPostSaveReconcile_offOnOp13() {
        if (!LegacyFleetPolicy.appliesToDevice()) return
        assertFalse(ProShotPipelineContract.wideLeafCalibrationEnabled())
        assertFalse(ProShotPipelineContract.stillPrecaptureEnabled())
        assertFalse(ProShotPipelineContract.manualExposureLatchOnStill(LegacyFleetPolicy.CANONICAL_TELE))
        assertFalse(LegacyFleetPolicy.useProShotReferenceCalibration())
        assertFalse(LegacyFleetPolicy.useLegacyLeafAuxColorReconcile())
        for (camId in
            listOf(
                LegacyFleetPolicy.CANONICAL_UW,
                LegacyFleetPolicy.CANONICAL_WIDE,
                LegacyFleetPolicy.CANONICAL_TELE,
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
                sessionCameraId = LegacyFleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                uwProShotAsnReconcile = true,
            ),
        )
        assertTrue(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_PROSHOT,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_TELE,
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
                sessionCameraId = LegacyFleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                wideLeafCalibrationForAuxDng = true,
            )
        val wide =
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_PROSHOT,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_WIDE,
                proShotPureDngSave = true,
                wideLeafCalibrationForAuxDng = true,
            )
        assertFalse(wide)
        assertEquals(true, uw)
    }

    @Test
    fun shipped_wideCalAndPrecapture_disabled() {
        assertFalse(LegacyFleetPolicy.useWideLeafCalibrationForAuxDng())
        assertFalse(LegacyFleetPolicy.useProShotStillPrecapture())
        assertFalse(LegacyFleetPolicy.proShotLeafStillSkipsStopRepeating(LegacyFleetPolicy.CANONICAL_TELE))
    }
}
