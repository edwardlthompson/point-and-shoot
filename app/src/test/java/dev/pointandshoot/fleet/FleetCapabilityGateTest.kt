package dev.pointandshoot.fleet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetCapabilityGateTest {

    private fun loadFixture(): JSONObject {
        val stream =
            checkNotNull(javaClass.getResourceAsStream("/fleet_matrix_gate_minimal.json")) {
                "Missing classpath fixture fleet_matrix_gate_minimal.json"
            }
        return JSONObject(stream.bufferedReader().readText())
    }

    @Test
    fun maxHfrAt1080_readsPerCamera() {
        val matrix = loadFixture()
        assertEquals(120, FleetCapabilityGate.maxHfrAt1080(matrix, "2"))
        assertEquals(480, FleetCapabilityGate.maxHfrAt1080(matrix, "3"))
        assertNull(FleetCapabilityGate.maxHfrAt1080(matrix, "99"))
    }

    @Test
    fun featureGates_hfrAndRaw_sessionOk() {
        val matrix = loadFixture()
        val uw = FleetCapabilityGate.featureGate(matrix, "2", "hfr")!!
        assertTrue(uw.advertised)
        assertFalse(uw.sessionOk)
        assertFalse(uw.appEnabled)

        val wide = FleetCapabilityGate.featureGate(matrix, "3", "hfr")!!
        assertTrue(wide.sessionOk)
        assertTrue(wide.appEnabled)

        assertTrue(FleetCapabilityGate.featureGate(matrix, "2", "raw")!!.sessionOk)
    }

    @Test
    fun policyId_nullWhenGenericFixtureHasNoPlugin() {
        val matrix = loadFixture()
        assertNull(FleetCapabilityGate.policyId(matrix))
    }

    @Test
    fun cameraIds_listsFixtureCameras() {
        val matrix = loadFixture()
        assertEquals(listOf("2", "3"), FleetCapabilityGate.cameraIds(matrix))
    }
}
