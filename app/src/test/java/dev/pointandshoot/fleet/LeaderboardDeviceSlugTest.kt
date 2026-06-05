package dev.pointandshoot.fleet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardDeviceSlugTest {

    @Test
    fun slug_matchesHostSha256Prefix() {
        val matrix =
            JSONObject().apply {
                put(FleetDeviceMatrix.KEY_DEVICE, FleetDeviceMatrix.deviceBlock("OnePlus", "CPH2583", "device"))
                put(
                    FleetDeviceMatrix.KEY_SCAN_META,
                    JSONObject().put("fingerprintSha256Prefix", "961221c0f0bc2eee"),
                )
            }
        assertEquals("718a8115ff142454", LeaderboardDeviceSlug.fromMatrix(matrix))
    }

    @Test
    fun publicUrl_buildsHashRoute() {
        val url = LeaderboardDeviceSlug.publicDeviceUrl("718a8115ff142454", "https://example.com/leaderboard")
        assertEquals("https://example.com/leaderboard/#/device/718a8115ff142454", url)
    }
}
