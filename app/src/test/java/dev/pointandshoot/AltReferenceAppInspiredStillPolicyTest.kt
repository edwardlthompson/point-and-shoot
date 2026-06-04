package dev.pointandshoot

import dev.pointandshoot.fleet.FleetCameraProfile
import dev.pointandshoot.fleet.FleetCameraRole
import dev.pointandshoot.fleet.StillDngBackend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AltReferenceAppInspiredStillPolicyTest {

    private fun teleProfile() =
        FleetCameraProfile(
            cameraId = "4",
            role = FleetCameraRole.TELE,
            physicalCameraIds = emptyList(),
            focalLengthsMm = emptyList(),
            rawFormatsAdvertised = emptyList(),
            prefersRawSensor = true,
            lensShadingMapOnStill = true,
            shadingModes = emptySet(),
            supportsDcgSession = false,
            hfrMaxFps = null,
            activeArrayWidth = 4096,
            activeArrayHeight = 3072,
            largestRawSensorWxH = "4096x3072",
            largestRaw12WxH = null,
        )

    @Test
    fun teleLensShadingMapOnly_motionCamBackend_teleOnly() {
        assertTrue(
            AltReferenceAppInspiredStillPolicy.teleLensShadingMapOnlyWhen(
                StillDngBackend.ALTREFERENCEAPP_INSPIRED,
                teleProfile(),
            ),
        )
        assertFalse(
            AltReferenceAppInspiredStillPolicy.teleLensShadingMapOnlyWhen(
                StillDngBackend.ALTREFERENCEAPP_INSPIRED,
                teleProfile().copy(role = FleetCameraRole.WIDE),
            ),
        )
        assertFalse(
            AltReferenceAppInspiredStillPolicy.teleLensShadingMapOnlyWhen(
                StillDngBackend.FRAMEWORK_REFERENCEAPP,
                teleProfile(),
            ),
        )
    }

    @Test
    fun applyReferenceAppOpticalCorrectionOnLeaf_offForAltReferenceApp() {
        assertFalse(
            AltReferenceAppInspiredStillPolicy.applyReferenceAppOpticalCorrectionOnLeafWhen(
                StillDngBackend.ALTREFERENCEAPP_INSPIRED,
            ),
        )
        assertTrue(
            AltReferenceAppInspiredStillPolicy.applyReferenceAppOpticalCorrectionOnLeafWhen(
                StillDngBackend.FRAMEWORK_REFERENCEAPP,
            ),
        )
    }
}
