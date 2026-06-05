package dev.pointandshoot.fleet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetParityGoldenSweepTest {
    private fun loadGolden(name: String): JSONObject {
        val stream =
            checkNotNull(javaClass.getResourceAsStream("/$name")) {
                "Missing classpath golden $name"
            }
        return JSONObject(stream.bufferedReader().readText())
    }

    @Test
    fun `golden minimal matrix evaluates raw dng advertised`() {
        val matrix = loadGolden("fleet_matrix_gate_minimal.json")
        val raw = CameraCapabilityCatalogBuilder.evaluatedRows(matrix).first { it.row.id == "raw.dng" }
        assertTrue(raw.deviceSupported)
        assertEquals(true, raw.sessionOk)
    }

    @Test
    fun `golden cph2583 matrix validates`() {
        val matrix = loadGolden("fleet_golden_cph2583_v1.json")
        assertTrue(FleetDeviceMatrix.isValidRoot(matrix))
        assertEquals("CPH2583", matrix.optJSONObject(FleetDeviceMatrix.KEY_DEVICE)?.optString("model"))
    }

    @Test
    fun `conflict matrix emits pairs when both advertised`() {
        val pairs =
            FleetParityConflictMatrix.activeConflicts(
                setOf("video.dual", "video.hfr", "raw.dng"),
            )
        assertTrue(pairs.isNotEmpty())
    }

    @Test
    fun `session template coverage builds from minimal golden`() {
        val matrix = loadGolden("fleet_matrix_gate_minimal.json")
        val templates = FleetSessionTemplateCoverage.build(matrix, listOf("raw.dng", "video.h264"))
        assertNotNull(templates)
        assertTrue(templates.length() > 0)
    }

    @Test
    fun `encoder cross check builds from minimal golden`() {
        val matrix = loadGolden("fleet_matrix_gate_minimal.json")
        val cross = FleetParityEncoderCrossCheck.build(matrix)
        assertTrue(cross.getJSONArray("rows").length() > 0)
    }

    @Test
    fun `quick tier matrix nulls sessionOk for fourKRegular gate`() {
        val matrix =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_SCHEMA_VERSION, FleetDeviceMatrix.SCHEMA_VERSION)
                put(
                    FleetDeviceMatrix.KEY_SCAN_META,
                    JSONObject().put("scanTier", "quick"),
                )
                put(
                    FleetDeviceMatrix.KEY_CAMERAS,
                    org.json.JSONArray().put(
                        JSONObject().apply {
                            put("cameraId", "2")
                            put(
                                "featureGates",
                                JSONObject().apply {
                                    put(
                                        "fourKRegular",
                                        JSONObject().apply {
                                            put("advertised", true)
                                            put("sessionOk", true)
                                            put("appEnabled", true)
                                        },
                                    )
                                },
                            )
                        },
                    ),
                )
            }
        val row = CameraCapabilityCatalogBuilder.evaluatedRows(matrix).first { it.row.id == "video.4k_regular" }
        assertTrue(row.deviceSupported)
        assertEquals(null, row.sessionOk)
    }

    @Test
    fun `session gated catalog ids include fourKRegular`() {
        assertTrue(FleetParitySweepRunner.SESSION_GATED_CATALOG_IDS.contains("video.4k_regular"))
        assertTrue(FleetParitySweepRunner.SESSION_GATED_CATALOG_IDS.contains("video.uhd60"))
    }
}
