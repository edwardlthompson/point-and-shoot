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
    fun store_isStillValid_invalidatesOnFingerprintOrVersion() {
        val stored =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_SCHEMA_VERSION, FleetDeviceMatrix.SCHEMA_VERSION)
                put(
                    FleetDeviceMatrix.KEY_SCAN_META,
                    FleetDeviceMatrix.scanMeta(
                        scanTier = FleetDeviceMatrix.ScanTier.QUICK,
                        appVersionCode = 1L,
                        sdkInt = 34,
                        securityPatch = "x",
                        fingerprintSha256Prefix = "fp_a",
                        scanDurationMs = 1L,
                    ),
                )
                put(FleetDeviceMatrix.KEY_DEVICE, FleetDeviceMatrix.deviceBlock("M", "M", "d"))
                put(FleetDeviceMatrix.KEY_CAMERAS, JSONArray().put(JSONObject().put("cameraId", "0")))
            }
        assertTrue(
            FleetDeviceMatrixStore.isStillValid(stored, "fp_a", 1L),
        )
        assertFalse(
            FleetDeviceMatrixStore.isStillValid(stored, "fp_b", 1L),
        )
        assertFalse(
            FleetDeviceMatrixStore.isStillValid(stored, "fp_a", 2L),
        )
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
    fun capabilitiesNormalized_mapsRawAndHfrCaps() {
        val caps =
            intArrayOf(
                android.hardware.camera2.CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW,
                android.hardware.camera2.CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO,
            )
        val arr = FleetDeviceMatrixStructured.capabilitiesNormalized(caps, cc = null)
        val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
        assertTrue(set.contains("RAW"))
        assertTrue(set.contains("CONSTRAINED_HIGH_SPEED_VIDEO"))
    }

    @Test
    fun featureGates_rawRequiresSession1080() {
        val shallow =
            JSONObject().apply {
                put("cameraId", "2")
                put("hfrMaxFpsAt1080", 120)
            }
        val deep =
            JSONObject().apply {
                put(
                    "availableCapabilities",
                    JSONArray().put(android.hardware.camera2.CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW),
                )
            }
        val sessionNo =
            JSONObject().apply {
                put(
                    "tests",
                    JSONArray().put(
                        JSONObject().apply {
                            put("name", "regular_1920x1080")
                            put("supported", false)
                        },
                    ),
                )
            }
        val gatesNo = FleetDeviceMatrixStructured.featureGates(shallow, deep, sessionNo, fleetProfile = null)
        assertTrue(gatesNo.getJSONObject("raw").getBoolean("advertised"))
        assertFalse(gatesNo.getJSONObject("raw").getBoolean("sessionOk"))

        val sessionYes =
            JSONObject().apply {
                put(
                    "tests",
                    JSONArray().put(
                        JSONObject().apply {
                            put("name", "regular_1920x1080")
                            put("supported", true)
                        },
                    ),
                )
            }
        val gatesYes = FleetDeviceMatrixStructured.featureGates(shallow, deep, sessionYes, fleetProfile = null)
        assertTrue(gatesYes.getJSONObject("raw").getBoolean("sessionOk"))
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
    fun needsFullRescan_fullWithDeepCapsAndCatalog_false() {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        val root = CameraCapabilityCatalogBuilder.attachTo(JSONObject(json))
        root.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)?.put("scanTier", "full")
        root.optJSONObject(FleetDeviceMatrix.KEY_APPENDIX)?.put(
            "deepCaps",
            JSONObject().put("cameras", JSONArray().put(JSONObject().put("cameraId", "2"))),
        )
        assertFalse(FleetDeviceMatrix.needsFullRescan(root))
    }

    @Test
    fun withCatalogIfMissing_attachesWhenAbsent() {
        val json = javaClass.getResource("/fleet_matrix_gate_minimal.json")!!.readText()
        val root = JSONObject(json)
        assertFalse(root.has(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG))
        val attached = FleetDeviceMatrix.withCatalogIfMissing(root)
        assertTrue(attached.has(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG))
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
