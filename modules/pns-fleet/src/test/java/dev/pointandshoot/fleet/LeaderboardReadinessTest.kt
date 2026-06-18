package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardReadinessTest {

    @Test
    fun contribute_requiresFullTierAndFullSweepAndRearLensInfo() {
        val matrix = matrixWithRearLens(fullTier = true, lensWidth = 8.0)
        val parity =
            JSONObject().apply {
                put("mode", "full")
                put("resolutionBetrayalIndex", 0)
            }
        val report = LeaderboardReadiness.evaluate(matrix, parity, ingestConfigured = true)
        assertTrue(report.contributeEnabled)
        assertEquals(LeaderboardReadiness.Level.GREEN, report.overall)
    }

    @Test
    fun contribute_blockedOnQuickTier() {
        val matrix = matrixWithRearLens(fullTier = false, lensWidth = 8.0)
        val parity = JSONObject().apply { put("mode", "full") }
        val report = LeaderboardReadiness.evaluate(matrix, parity, ingestConfigured = true)
        assertFalse(report.contributeEnabled)
    }

    @Test
    fun rearLensInfo_requiresAllRearCameras() {
        val cams =
            JSONArray().apply {
                put(rearCam("0", "WIDE", 10.0))
                put(rearCam("2", "UW", 0.0))
            }
        val matrix =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_SCAN_META, JSONObject().put("scanTier", "full"))
                put(FleetDeviceMatrix.KEY_CAMERAS, cams)
            }
        val status = LeaderboardReadiness.rearLensInfoStatus(matrix)
        assertFalse(status.allPresent)
        assertTrue(status.anyPresent)
    }

    private fun matrixWithRearLens(fullTier: Boolean, lensWidth: Double): JSONObject {
        val cams = JSONArray().apply { put(rearCam("0", "WIDE", lensWidth)) }
        return JSONObject().apply {
            put(
                FleetDeviceMatrix.KEY_SCAN_META,
                JSONObject().put("scanTier", if (fullTier) "full" else "quick"),
            )
            put(FleetDeviceMatrix.KEY_CAMERAS, cams)
        }
    }

    private fun rearCam(id: String, role: String, width: Double): JSONObject =
        JSONObject().apply {
            put("cameraId", id)
            put("fleetPolicy", JSONObject().put("role", role))
            put(
                "lensInfo",
                JSONObject().put(
                    "sensorPhysicalSizeMm",
                    JSONObject().put("widthMm", width).put("heightMm", 6.0),
                ),
            )
        }
}
