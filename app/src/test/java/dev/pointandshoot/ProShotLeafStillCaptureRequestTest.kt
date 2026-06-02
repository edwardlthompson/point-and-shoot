package dev.pointandshoot

import dev.pointandshoot.fleet.LegacyFleetPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProShotLeafStillCaptureRequestTest {

    @Test
    fun exactLeafStillPath_enabledOnOp13Fleet() {
        assertTrue(LegacyFleetPolicy.useExactProShotLeafStillCaptureRequestWhen(deviceApplies = true))
        assertFalse(LegacyFleetPolicy.useExactProShotLeafStillCaptureRequestWhen(deviceApplies = false))
    }

    @Test
    fun postSaveColorPaths_disabledForShippedParity() {
        assertFalse(LegacyFleetPolicy.useLegacyLeafAuxColorReconcile())
        assertFalse(LegacyFleetPolicy.useProShotReferenceCalibration())
    }

    @Test
    fun captureTimeGains_offWhenExactLeafStill() {
        assertFalse(
            dev.pointandshoot.fleet.LegacyLeafStillColorCorrection.appliesCaptureTimeGainsWhen(
                deviceApplies = true,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                uwProShotAsnReconcile = false,
                proShotReferenceCalibration = false,
            ),
        )
    }
}
