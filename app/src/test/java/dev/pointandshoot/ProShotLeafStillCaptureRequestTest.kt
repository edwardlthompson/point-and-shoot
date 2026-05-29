package dev.pointandshoot

import dev.pointandshoot.fleet.OnePlus13FleetPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProShotLeafStillCaptureRequestTest {

    @Test
    fun exactLeafStillPath_enabledOnOp13Fleet() {
        assertTrue(OnePlus13FleetPolicy.useExactProShotLeafStillCaptureRequestWhen(deviceApplies = true))
        assertFalse(OnePlus13FleetPolicy.useExactProShotLeafStillCaptureRequestWhen(deviceApplies = false))
    }

    @Test
    fun postSaveColorPaths_disabledForShippedParity() {
        assertFalse(OnePlus13FleetPolicy.useOp13LeafAuxColorReconcile())
        assertFalse(OnePlus13FleetPolicy.useProShotReferenceCalibration())
    }

    @Test
    fun captureTimeGains_offWhenExactLeafStill() {
        assertFalse(
            dev.pointandshoot.fleet.Op13LeafStillColorCorrection.appliesCaptureTimeGainsWhen(
                deviceApplies = true,
                sessionCameraId = OnePlus13FleetPolicy.CANONICAL_UW,
                proShotPureDngSave = true,
                uwProShotAsnReconcile = false,
                proShotReferenceCalibration = false,
            ),
        )
    }
}
