package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Store + catalog attach tests that depend on `:app` fleet glue. */
class FleetDeviceMatrixStoreTest {

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
    fun store_isStillValid_invalidatesOnRosterOrPolicyWhenPresent() {
        val meta =
            FleetDeviceMatrix.scanMeta(
                scanTier = FleetDeviceMatrix.ScanTier.QUICK,
                appVersionCode = 1L,
                sdkInt = 34,
                securityPatch = "x",
                fingerprintSha256Prefix = "fp_a",
                scanDurationMs = 1L,
            ).apply {
                put("cameraIdRosterSha256Prefix", "roster_a")
                put("fleetPolicyId", "generic")
            }
        val stored =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_SCHEMA_VERSION, FleetDeviceMatrix.SCHEMA_VERSION)
                put(FleetDeviceMatrix.KEY_SCAN_META, meta)
                put(FleetDeviceMatrix.KEY_DEVICE, FleetDeviceMatrix.deviceBlock("M", "M", "d"))
                put(FleetDeviceMatrix.KEY_CAMERAS, JSONArray().put(JSONObject().put("cameraId", "0")))
            }

        assertTrue(
            FleetDeviceMatrixStore.isStillValid(
                stored = stored,
                liveFingerprintPrefix = "fp_a",
                liveVersionCode = 1L,
                liveCameraIdRosterSha256Prefix = "roster_a",
                livePolicyId = "generic",
            ),
        )
        assertFalse(
            FleetDeviceMatrixStore.isStillValid(
                stored = stored,
                liveFingerprintPrefix = "fp_a",
                liveVersionCode = 1L,
                liveCameraIdRosterSha256Prefix = "roster_b",
                livePolicyId = "generic",
            ),
        )
        assertFalse(
            FleetDeviceMatrixStore.isStillValid(
                stored = stored,
                liveFingerprintPrefix = "fp_a",
                liveVersionCode = 1L,
                liveCameraIdRosterSha256Prefix = "roster_a",
                livePolicyId = "legacy_op13",
            ),
        )
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
}
