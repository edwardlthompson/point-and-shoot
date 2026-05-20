package dev.pointandshoot

import dev.pointandshoot.fleet.FleetCameraProfile
import dev.pointandshoot.fleet.FleetCameraRole
import dev.pointandshoot.fleet.StillDngBackend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionCamInspiredStillPolicyTest {

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
            MotionCamInspiredStillPolicy.teleLensShadingMapOnlyWhen(
                StillDngBackend.MOTIONCAM_INSPIRED,
                teleProfile(),
            ),
        )
        assertFalse(
            MotionCamInspiredStillPolicy.teleLensShadingMapOnlyWhen(
                StillDngBackend.MOTIONCAM_INSPIRED,
                teleProfile().copy(role = FleetCameraRole.WIDE),
            ),
        )
        assertFalse(
            MotionCamInspiredStillPolicy.teleLensShadingMapOnlyWhen(
                StillDngBackend.FRAMEWORK_PROSHOT,
                teleProfile(),
            ),
        )
    }

    @Test
    fun applyProShotOpticalCorrectionOnLeaf_offForMotionCam() {
        assertFalse(
            MotionCamInspiredStillPolicy.applyProShotOpticalCorrectionOnLeafWhen(
                StillDngBackend.MOTIONCAM_INSPIRED,
            ),
        )
        assertTrue(
            MotionCamInspiredStillPolicy.applyProShotOpticalCorrectionOnLeafWhen(
                StillDngBackend.FRAMEWORK_PROSHOT,
            ),
        )
    }
}
