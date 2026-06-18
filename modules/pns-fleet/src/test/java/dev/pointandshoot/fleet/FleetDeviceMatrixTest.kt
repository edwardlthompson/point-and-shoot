package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetDeviceMatrixTest {

    @Test
    fun scanMeta_roundTrip_fields() {
        val meta =
            FleetDeviceMatrix.scanMeta(
                scanTier = FleetDeviceMatrix.ScanTier.QUICK,
                appVersionCode = 14003L,
                sdkInt = 36,
                securityPatch = "2026-05-01",
                fingerprintSha256Prefix = "abc123def456",
                scanDurationMs = 42L,
            )
        assertEquals("quick", meta.getString("scanTier"))
        assertEquals(14003L, meta.getLong("appVersionCode"))
        assertEquals(36, meta.getInt("sdkInt"))
        assertEquals("abc123def456", meta.getString("fingerprintSha256Prefix"))
        assertEquals(42L, meta.getLong("scanDurationMs"))
        assertEquals(FleetDeviceMatrix.SCAN_ORDERING_VERSION, meta.getInt("scanOrderingVersion"))
    }

    @Test
    fun validRoot_acceptsSchemaV1() {
        val v1 =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_SCHEMA_VERSION, 1)
                put(FleetDeviceMatrix.KEY_SCAN_META, JSONObject().put("scanTier", "quick"))
                put(FleetDeviceMatrix.KEY_DEVICE, FleetDeviceMatrix.deviceBlock("OEM", "MODEL", "device"))
                put(FleetDeviceMatrix.KEY_CAMERAS, JSONArray())
            }
        assertTrue(FleetDeviceMatrix.isValidRoot(v1))
    }

    @Test
    fun validRoot_requiresSchemaAndCameras() {
        val invalid = JSONObject().put(FleetDeviceMatrix.KEY_SCHEMA_VERSION, 99)
        assertFalse(FleetDeviceMatrix.isValidRoot(invalid))

        val valid =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_SCHEMA_VERSION, FleetDeviceMatrix.SCHEMA_VERSION)
                put(FleetDeviceMatrix.KEY_SCAN_META, JSONObject().put("scanTier", "quick"))
                put(FleetDeviceMatrix.KEY_DEVICE, FleetDeviceMatrix.deviceBlock("OEM", "MODEL", "device"))
                put(FleetDeviceMatrix.KEY_CAMERAS, JSONArray())
            }
        assertTrue(FleetDeviceMatrix.isValidRoot(valid))
        assertEquals(FleetDeviceMatrix.ScanTier.QUICK, FleetDeviceMatrix.parseScanTier(valid))
    }

    @Test
    fun syntheticMatrix_productAndAppendix() {
        val cameras =
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("cameraId", "2")
                        put("lensFacing", "BACK")
                        put("hfrMaxFpsAt1080", 120)
                    },
                )
            }
        val root =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_SCHEMA_VERSION, FleetDeviceMatrix.SCHEMA_VERSION)
                put(
                    FleetDeviceMatrix.KEY_SCAN_META,
                    FleetDeviceMatrix.scanMeta(
                        scanTier = FleetDeviceMatrix.ScanTier.QUICK,
                        appVersionCode = 1L,
                        sdkInt = 36,
                        securityPatch = "p",
                        fingerprintSha256Prefix = "fp",
                        scanDurationMs = 10L,
                    ),
                )
                put(FleetDeviceMatrix.KEY_DEVICE, FleetDeviceMatrix.deviceBlock("OnePlus", "CPH2583", "OP5929L1"))
                put(FleetDeviceMatrix.KEY_CAMERAS, cameras)
                put(
                    FleetDeviceMatrix.KEY_PRODUCT,
                    JSONObject().put(
                        "focalSlots",
                        JSONArray().put(
                            JSONObject().apply {
                                put("cameraId", "2")
                                put("focalMm35", 23)
                                put("grayscaled", false)
                            },
                        ),
                    ),
                )
                put(FleetDeviceMatrix.KEY_APPENDIX, FleetDeviceMatrix.emptyAppendix())
            }
        assertTrue(FleetDeviceMatrix.isValidRoot(root))
        assertEquals(1, FleetDeviceMatrix.cameraCount(root))
        assertNotNull(root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONArray("focalSlots"))
    }

    @Test
    fun diff_detectsHfrAndFeatureGateChanges() {
        fun cam(hfr: Int, rawSessionOk: Boolean) =
            JSONObject().apply {
                put("cameraId", "2")
                put("hfrMaxFpsAt1080", hfr)
                put(
                    "featureGates",
                    JSONObject().apply {
                        put(
                            "raw",
                            JSONObject().apply {
                                put("advertised", true)
                                put("sessionOk", rawSessionOk)
                                put("appEnabled", rawSessionOk)
                            },
                        )
                    },
                )
                put("capabilitiesNormalized", JSONArray().put("RAW"))
            }
        val prev =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_CAMERAS, JSONArray().put(cam(60, true)))
            }
        val cur =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_CAMERAS, JSONArray().put(cam(120, false)))
            }
        val diff = FleetDeviceMatrixDiff.diff(prev, cur)
        assertTrue(diff.hasChanges)
        assertTrue(diff.summaryLines.any { it.contains("hfrMaxFpsAt1080") })
        assertTrue(diff.summaryLines.any { it.contains("sessionOk") })
    }

    @Test
    fun diff_noPrevious_isBaseline() {
        val cur =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_CAMERAS, JSONArray())
            }
        val diff = FleetDeviceMatrixDiff.diff(null, cur)
        assertFalse(diff.hasChanges)
        assertTrue(diff.summaryLines.first().contains("baseline"))
    }

    @Test
    fun needsFullRescan_nullOrQuick_true() {
        assertTrue(FleetDeviceMatrix.needsFullRescan(null))
        val quick =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_SCAN_META, JSONObject().put("scanTier", "quick"))
                put(FleetDeviceMatrix.KEY_APPENDIX, JSONObject())
            }
        assertTrue(FleetDeviceMatrix.needsFullRescan(quick))
    }

    @Test
    fun withCatalogIfMissing_noOpWhenAbsent_modulePure() {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        val root = JSONObject(json)
        assertFalse(root.has(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG))
        val attached = FleetDeviceMatrix.withCatalogIfMissing(root)
        assertFalse(attached.has(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG))
    }

    @Test
    fun featureGatesShallow_populatesRawHfrFaceKeys() {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        val root = JSONObject(json)
        val cam = root.getJSONArray(FleetDeviceMatrix.KEY_CAMERAS).getJSONObject(0)
        val gates = cam.getJSONObject("featureGates")
        assertTrue(gates.has("raw"))
        assertTrue(gates.has("hfr"))
        assertTrue(gates.has("face"))
        assertTrue(gates.getJSONObject("raw").getBoolean("advertised"))
    }
}
