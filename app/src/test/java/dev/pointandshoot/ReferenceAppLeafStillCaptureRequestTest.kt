package dev.pointandshoot

import dev.pointandshoot.fleet.LegacyFleetPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceAppLeafStillCaptureRequestTest {

    @Test
    fun exactLeafStillPath_enabledOnOp13Fleet() {
        assertTrue(LegacyFleetPolicy.useExactReferenceAppLeafStillCaptureRequestWhen(deviceApplies = true))
        assertFalse(LegacyFleetPolicy.useExactReferenceAppLeafStillCaptureRequestWhen(deviceApplies = false))
    }

    @Test
    fun postSaveColorPaths_disabledForShippedParity() {
        assertFalse(LegacyFleetPolicy.useLegacyLeafAuxColorReconcile())
        assertFalse(LegacyFleetPolicy.useReferenceAppReferenceCalibration())
    }

    @Test
    fun captureTimeGains_offWhenExactLeafStill() {
        assertFalse(
            dev.pointandshoot.fleet.LegacyLeafStillColorCorrection.appliesCaptureTimeGainsWhen(
                deviceApplies = true,
                sessionCameraId = LegacyFleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                uwReferenceAppAsnReconcile = false,
                proShotReferenceCalibration = false,
            ),
        )
    }
}
