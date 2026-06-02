package dev.pointandshoot.fleet

import dev.pointandshoot.BackCameraRoleResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class FleetCameraProfileBuilderTest {

    @Test
    fun classifyId_mapsResolverRoles() {
        val roles =
            BackCameraRoleResolver.Roles(
                wide = "2",
                ultraWide = "3",
                tele = "4",
                longTele = null,
            )
        assertEquals(FleetCameraRole.WIDE, FleetCameraProfileBuilder.classifyId("2", null, roles))
        assertEquals(FleetCameraRole.ULTRA_WIDE, FleetCameraProfileBuilder.classifyId("3", null, roles))
        assertEquals(FleetCameraRole.TELE, FleetCameraProfileBuilder.classifyId("4", null, roles))
        assertEquals(FleetCameraRole.UNKNOWN, FleetCameraProfileBuilder.classifyId("9", null, roles))
    }

    @Test
    fun profileJson_roundTrip() {
        val profile =
            FleetCameraProfile(
                cameraId = "2",
                role = FleetCameraRole.WIDE,
                physicalCameraIds = emptyList(),
                focalLengthsMm = listOf(6.06f),
                rawFormatsAdvertised = listOf(32, 37, 38),
                prefersRawSensor = true,
                lensShadingMapOnStill = true,
                shadingModes = setOf(1, 2),
                supportsDcgSession = true,
                hfrMaxFps = 480,
                activeArrayWidth = 4096,
                activeArrayHeight = 3072,
                largestRawSensorWxH = "4096x3072",
                largestRaw12WxH = "4096x3072",
            )
        val back = FleetCameraProfile.fromJson(profile.toJson())
        assertEquals(profile, back)
    }

    @Test
    fun snapshotJson_roundTrip() {
        val snap =
            FleetProfilesSnapshot(
                deviceModel = "LegacySku",
                manufacturer = "OnePlus",
                logicalCameraId = "0",
                roleByCameraId = mapOf("2" to FleetCameraRole.WIDE, "3" to FleetCameraRole.ULTRA_WIDE),
                profiles =
                    listOf(
                        FleetCameraProfile(
                            cameraId = "2",
                            role = FleetCameraRole.WIDE,
                            physicalCameraIds = emptyList(),
                            focalLengthsMm = emptyList(),
                            rawFormatsAdvertised = listOf(32),
                            prefersRawSensor = true,
                            lensShadingMapOnStill = true,
                            shadingModes = emptySet(),
                            supportsDcgSession = false,
                            hfrMaxFps = null,
                            activeArrayWidth = 1,
                            activeArrayHeight = 1,
                            largestRawSensorWxH = null,
                            largestRaw12WxH = null,
                        ),
                    ),
                policyId = LegacyFleetPolicy.POLICY_ID,
                leafRawFormatOrder = LegacyFleetPolicy.LEAF_RAW_FORMAT_ORDER,
            )
        val back = FleetProfilesSnapshot.fromJson(snap.toJson())!!
        assertEquals(snap.deviceModel, back.deviceModel)
        assertEquals(snap.policyId, back.policyId)
        assertEquals(1, back.profiles.size)
        assertEquals("2", back.profiles[0].cameraId)
    }
}
