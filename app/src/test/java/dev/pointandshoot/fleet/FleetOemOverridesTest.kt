package dev.pointandshoot.fleet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetOemOverridesTest {
    @Test
    fun onePlus13_hasRoleAliases() {
        val o = FleetOemOverrides.onePlus13Cph2655()
        assertEquals("wide", o.aliasCameraIds["2"])
        assertEquals("tele", o.aliasCameraIds["4"])
    }

    @Test
    fun jsonRoundTrip() {
        val o = FleetOemOverrides.onePlus13Cph2655()
        val back = FleetOemOverrides.fromJson(o.toJson())!!
        assertEquals(o.modelPattern, back.modelPattern)
        assertEquals(o.aliasCameraIds, back.aliasCameraIds)
        assertTrue(back.hideCameraIds.isEmpty())
    }
}
