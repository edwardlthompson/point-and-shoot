package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetDeviceMatrixSchemaValidatorTest {

    @Test
    fun goldenFixture_passesValidation() {
        val root = FleetMatrixFixtureSupport.loadRepoFixture("cph2583_v1.json")
        assertTrue(FleetDeviceMatrixSchemaValidator.validate(root) is FleetDeviceMatrixSchemaValidator.Result.Ok)
    }

    @Test
    fun rejectsEmptyCameras() {
        val root = minimalRoot(cameras = JSONArray())
        val result = FleetDeviceMatrixSchemaValidator.validate(root)
        assertTrue(result is FleetDeviceMatrixSchemaValidator.Result.Fail)
        assertEquals("cameras must be non-empty", (result as FleetDeviceMatrixSchemaValidator.Result.Fail).message)
    }

    @Test
    fun fullTier_requiresSortedCameraIds() {
        val cameras =
            JSONArray()
                .put(cameraStub("3"))
                .put(cameraStub("2"))
        val root = minimalRoot(cameras = cameras, scanTier = "full")
        val result = FleetDeviceMatrixSchemaValidator.validate(root)
        assertTrue(result is FleetDeviceMatrixSchemaValidator.Result.Fail)
        assertTrue((result as FleetDeviceMatrixSchemaValidator.Result.Fail).message.contains("sorted"))
    }

    private fun cameraStub(id: String): JSONObject =
        JSONObject().apply {
            put("cameraId", id)
            put("hfrMaxFpsAt1080", 120)
        }

    private fun minimalRoot(cameras: JSONArray, scanTier: String = "quick"): JSONObject =
        JSONObject().apply {
            put(FleetDeviceMatrix.KEY_SCHEMA_VERSION, FleetDeviceMatrix.SCHEMA_VERSION)
            put(
                FleetDeviceMatrix.KEY_SCAN_META,
                JSONObject().apply {
                    put("scanTier", scanTier)
                    put("appVersionCode", 1L)
                    put("sdkInt", 34)
                    put("fingerprintSha256Prefix", "abc")
                },
            )
            put(FleetDeviceMatrix.KEY_DEVICE, FleetDeviceMatrix.deviceBlock("OEM", "MODEL", "device"))
            put(FleetDeviceMatrix.KEY_CAMERAS, cameras)
            put(FleetDeviceMatrix.KEY_PRODUCT, JSONObject())
            put(FleetDeviceMatrix.KEY_APPENDIX, JSONObject())
        }
}
