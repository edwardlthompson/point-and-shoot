package dev.pointandshoot

import dev.pointandshoot.fleet.FleetCameraProfile
import dev.pointandshoot.fleet.FleetCameraRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StillCaptureIqPolicyTest {

    private fun sampleProfile(lensShading: Boolean) =
        FleetCameraProfile(
            cameraId = "2",
            role = FleetCameraRole.WIDE,
            physicalCameraIds = emptyList(),
            focalLengthsMm = emptyList(),
            rawFormatsAdvertised = emptyList(),
            prefersRawSensor = true,
            lensShadingMapOnStill = lensShading,
            shadingModes = emptySet(),
            supportsDcgSession = false,
            hfrMaxFps = null,
            activeArrayWidth = 4096,
            activeArrayHeight = 3072,
            largestRawSensorWxH = "4096x3072",
            largestRaw12WxH = null,
        )

    @Test
    fun fleetProfile_lensShadingMapOnStill_flagStored() {
        assertTrue(sampleProfile(lensShading = true).lensShadingMapOnStill)
        assertFalse(sampleProfile(lensShading = false).lensShadingMapOnStill)
    }
}
