package dev.pointandshoot.fleet

import dev.pointandshoot.BackCameraRoleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FleetDevicePolicyTest {

    @Test
    fun genericPolicy_passesThroughEnumeratedRoles() {
        val enumerated =
            BackCameraRoleResolver.Roles(
                wide = "2",
                ultraWide = "3",
                tele = "4",
                longTele = null,
            )
        val merged = GenericFleetPolicy.mergeRoles(enumerated, listOf("0", "2", "3", "4"))
        assertEquals(enumerated, merged)
        assertNull(GenericFleetPolicy.policyId)
    }

    @Test
    fun op13Plugin_delegatesToOnePlus13Policy() {
        assertEquals(OnePlus13FleetPolicy.POLICY_ID, OnePlus13FleetPolicyPlugin.policyId)
        val enumerated =
            BackCameraRoleResolver.Roles(
                wide = "9",
                ultraWide = "8",
                tele = "7",
                longTele = null,
            )
        val ids = listOf("0", "2", "3", "4")
        assertEquals(
            OnePlus13FleetPolicy.mergeRoles(enumerated, ids),
            OnePlus13FleetPolicyPlugin.mergeRoles(enumerated, ids),
        )
        assertEquals(
            OnePlus13FleetPolicy.leafRawFormatOrder(),
            OnePlus13FleetPolicyPlugin.leafRawFormatOrder(),
        )
    }

    @Test
    fun genericLeafOrder_prefersRaw12BeforeRawSensor() {
        val order = GenericFleetPolicy.leafRawFormatOrder()
        assertEquals(
            listOf(
                android.graphics.ImageFormat.RAW12,
                android.graphics.ImageFormat.RAW_SENSOR,
                android.graphics.ImageFormat.RAW10,
                android.graphics.ImageFormat.RAW_PRIVATE,
            ),
            order,
        )
    }
}
