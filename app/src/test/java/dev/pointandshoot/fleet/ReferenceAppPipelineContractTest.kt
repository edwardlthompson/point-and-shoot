package dev.pointandshoot.fleet

import dev.pointandshoot.StillDngBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceAppPipelineContractTest : LegacyFleetPolicyTestHarness() {

    @Test
    fun standardReferenceApp_leafPostSaveReconcile_offOnOp13() {
        if (!LegacyFleetPolicy.appliesToDevice()) return
        assertFalse(ReferenceAppPipelineContract.wideLeafCalibrationEnabled())
        assertFalse(ReferenceAppPipelineContract.stillPrecaptureEnabled())
        assertFalse(ReferenceAppPipelineContract.manualExposureLatchOnStill(LegacyFleetPolicy.CANONICAL_TELE))
        assertFalse(LegacyFleetPolicy.useReferenceAppReferenceCalibration())
        assertFalse(LegacyFleetPolicy.useLegacyLeafAuxColorReconcile())
        for (camId in
            listOf(
                LegacyFleetPolicy.CANONICAL_UW,
                LegacyFleetPolicy.CANONICAL_WIDE,
                LegacyFleetPolicy.CANONICAL_TELE,
            )
        ) {
            assertFalse(ReferenceAppPipelineContract.leafPostSaveTiffReconcileEnabled(camId))
        }
    }


    @Test
    fun leafPostSaveReconcile_proShotPure_uwAndTeleAuxColorFlag() {
        assertTrue(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                uwReferenceAppAsnReconcile = true,
            ),
        )
        assertTrue(
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_TELE,
                proShotPureDngSave = true,
                uwReferenceAppAsnReconcile = true,
            ),
        )
    }

    @Test
    fun leafPostSaveReconcile_when_wideCalEnabled_onlyAux() {
        val uw =
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                wideLeafCalibrationForAuxDng = true,
            )
        val wide =
            LeafDngHalReconcile.shouldReconcileLeafDngMetadataWhen(
                deviceApplies = true,
                backend = StillDngBackend.FRAMEWORK_REFERENCEAPP,
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
        assertFalse(LegacyFleetPolicy.useReferenceAppStillPrecapture())
        assertFalse(LegacyFleetPolicy.proShotLeafStillSkipsStopRepeating(LegacyFleetPolicy.CANONICAL_TELE))
    }
}
