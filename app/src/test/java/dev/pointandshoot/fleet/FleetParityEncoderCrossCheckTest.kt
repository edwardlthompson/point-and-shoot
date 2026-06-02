package dev.pointandshoot.fleet

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetParityEncoderCrossCheckTest {
    @Test
    fun `build returns rows for encoder catalog ids`() {
        val matrix =
            JSONObject().apply {
                put(
                    FleetDeviceMatrix.KEY_ENCODER,
                    JSONObject().apply {
                        put("source", "probe")
                        put("bestByCameraFps", org.json.JSONArray())
                    },
                )
            }
        val cross = FleetParityEncoderCrossCheck.build(matrix)
        assertTrue(cross.getJSONArray("rows").length() > 0)
    }
}
