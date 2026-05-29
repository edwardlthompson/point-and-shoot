package dev.pointandshoot.fleet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
/**
 * Golden fleet matrix fixture (Milestone **16.12**) — host script
 * `scripts/fleet_matrix_schema_validate.py` mirrors these invariants.
 */
class FleetDeviceMatrixGoldenTest {

    @Test
    fun cph2583Golden_fixtureValidAndGatesReadable() {
        val root = loadGolden()
        assertTrue(FleetDeviceMatrix.isValidRoot(root))
        assertEquals(FleetDeviceMatrix.ScanTier.FULL, FleetDeviceMatrix.parseScanTier(root))
        assertEquals("CPH2583", root.optJSONObject(FleetDeviceMatrix.KEY_DEVICE)?.optString("model"))
        val ids = FleetCapabilityGate.cameraIds(root)
        assertEquals(listOf("2", "3"), ids)
        assertEquals(480, FleetCapabilityGate.maxHfrAt1080(root, "2"))
        assertEquals(240, FleetCapabilityGate.maxHfrAt1080(root, "3"))
        assertNotNull(FleetCapabilityGate.featureGate(root, "2", "hfr")?.appEnabled)
        assertNull(FleetCapabilityGate.policyId(root))
    }

    private fun loadGolden(): JSONObject {
        val stream =
            checkNotNull(javaClass.getResourceAsStream("/fleet_golden_cph2583_v1.json")) {
                "Missing classpath golden fleet_golden_cph2583_v1.json"
            }
        return JSONObject(stream.bufferedReader().readText())
    }
}
