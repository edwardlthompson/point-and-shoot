package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class SessionMatrixProbeCoreTest {

    @RunWith(Parameterized::class)
    class SessionTestNameTable(
        private val testName: String,
        private val supported: Boolean,
    ) {
        @Test
        fun sessionTestSupported_readsFixtureTestsArray() {
            val sessionCam =
                JSONObject().apply {
                    put(
                        "tests",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("name", testName)
                                    put("supported", supported)
                                },
                            )
                        },
                    )
                }
            assertEquals(supported, SessionMatrixProbeCore.sessionTestSupported(sessionCam, testName))
        }

        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{0}={1}")
            fun cases(): Collection<Array<Any>> =
                listOf(
                    arrayOf("regular_1920x1080", true),
                    arrayOf("regular_1920x1080", false),
                    arrayOf("high_speed_first_advertised", true),
                )
        }
    }

    @Test
    fun sessionTestSupported_missingTestsArray_isFalse() {
        assertFalse(SessionMatrixProbeCore.sessionTestSupported(null, "regular_1920x1080"))
        assertFalse(SessionMatrixProbeCore.sessionTestSupported(JSONObject(), "regular_1920x1080"))
    }

    @Test
    fun highSpeedSessionOk_delegatesToHighSpeedTestName() {
        val sessionCam =
            JSONObject().apply {
                put(
                    "tests",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("name", "high_speed_first_advertised")
                                put("supported", true)
                            },
                        )
                    },
                )
            }
        assertTrue(SessionMatrixProbeCore.highSpeedSessionOk(sessionCam))
    }

}
