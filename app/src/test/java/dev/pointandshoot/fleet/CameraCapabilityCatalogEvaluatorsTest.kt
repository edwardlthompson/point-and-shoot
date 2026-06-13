package dev.pointandshoot.fleet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class CameraCapabilityCatalogEvaluatorsTest {

    @RunWith(Parameterized::class)
    class CatalogRowTableTest(
        private val rowId: String,
        private val expectSupported: Boolean,
        private val expectSessionOk: Boolean?,
        private val detailContains: String,
    ) {
        @Test
        fun evaluate_matchesExpected() {
            val root = loadMinimalMatrix()
            val row = CameraCapabilityCatalog.registry.first { it.id == rowId }
            val (supported, sessionOk, detail) = CameraCapabilityCatalogEvaluators.evaluate(row, root)
            assertEquals("supported row=$rowId", expectSupported, supported)
            assertEquals("sessionOk row=$rowId", expectSessionOk, sessionOk)
            assertTrue("detail row=$rowId detail=$detail", detail.contains(detailContains))
        }

        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{0}")
            fun rows(): Collection<Array<Any?>> =
                listOf(
                    arrayOf("raw.dng", true, true, "advertised=true"),
                    arrayOf("video.hfr", true, true, "sessionOk=true"),
                    arrayOf("face.detect", false, null, "no gate"),
                    arrayOf("still.referenceapp_leaf", false, null, "legacy_regression_lane"),
                    arrayOf("fleet.matrix", true, null, "matrix present"),
                    arrayOf("fleet.parity_sweep", true, null, "parity runner shipped"),
                    arrayOf("lens.multi", false, null, "focalSlots=0"),
                    arrayOf("video.anamorphic", true, null, "anamorphicSar=metadata"),
                )

            private fun loadMinimalMatrix(): JSONObject =
                FleetMatrixFixtureSupport.loadClasspath("fleet_matrix_gate_minimal.json")
        }
    }

    @Test
    fun cph2583Golden_hfrAndRawGatesEvaluateTrue() {
        val root = FleetMatrixFixtureSupport.loadRepoFixture("cph2583_v1.json")
        val hfrRow = CameraCapabilityCatalog.registry.first { it.id == "video.hfr" }
        val rawRow = CameraCapabilityCatalog.registry.first { it.id == "raw.dng" }
        val (hfrOk, hfrSess, _) = CameraCapabilityCatalogEvaluators.evaluate(hfrRow, root)
        val (rawOk, rawSess, _) = CameraCapabilityCatalogEvaluators.evaluate(rawRow, root)
        assertTrue(hfrOk)
        assertTrue(rawOk)
        assertEquals(true, hfrSess)
        assertEquals(true, rawSess)
    }

    @Test
    fun rootMaxResUnlock_cph2583Model_isFalse() {
        val root = FleetMatrixFixtureSupport.loadRepoFixture("cph2583_v1.json")
        val row = CameraCapabilityCatalog.registry.first { it.id == "root.max_res_unlock_cph2583" }
        val (supported, sessionOk, detail) = CameraCapabilityCatalogEvaluators.evaluate(row, root)
        assertFalse(supported)
        assertNull(sessionOk)
        assertTrue(detail.contains("unlockState=missing"))
    }
}
