package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetDeviceMatrixStructuredTest {

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
}
