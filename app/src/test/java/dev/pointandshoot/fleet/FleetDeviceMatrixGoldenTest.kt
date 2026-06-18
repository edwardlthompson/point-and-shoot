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
 *
 * Repo SoT: `tests/fixtures/fleet_matrix/` (classpath copy under `app/src/test/resources/`).
 */
class FleetDeviceMatrixGoldenTest {

    @Test
    fun cph2583Golden_repoFixtureValidAndGatesReadable() {
        val root = FleetMatrixFixtureSupport.loadRepoFixture("cph2583_v1.json")
        assertGoldenInvariants(root)
    }

    @Test
    fun cph2583Golden_classpathCopyMatchesRepoFixture() {
        val repo = FleetMatrixFixtureSupport.loadRepoFixture("cph2583_v1.json")
        val classpath = FleetMatrixFixtureSupport.loadClasspath("fleet_golden_cph2583_v1.json")
        assertEquals(repo.toString(), classpath.toString())
    }

    @Test
    fun cph2583Golden_catalogBuilderAttachPreservesGates() {
        val root = FleetMatrixFixtureSupport.loadRepoFixture("cph2583_v1.json")
        val attached = CameraCapabilityCatalogBuilder.attachTo(root)
        assertTrue(attached.has(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG))
        val raw = CameraCapabilityCatalogBuilder.evaluatedRows(attached).first { it.row.id == "raw.dng" }
        assertTrue(raw.deviceSupported)
        assertEquals(true, raw.sessionOk)
    }

    @Test
    fun cph2583Golden_breakthroughSliceEmptyWhenNoStillResolutionAdvertised() {
        val root = FleetMatrixFixtureSupport.loadRepoFixture("cph2583_v1.json")
        val evidences = Camera2FullMpBreakthrough.evaluateFromMatrix(root)
        assertTrue(evidences.isEmpty())
        val summary = Camera2FullMpBreakthrough.toSummaryJson(evidences)
        assertEquals(false, summary.getBoolean("proven"))
    }

    private fun assertGoldenInvariants(root: JSONObject) {
        assertTrue(FleetDeviceMatrixSchemaValidator.validate(root) is FleetDeviceMatrixSchemaValidator.Result.Ok)
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
}
