package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Camera2FullMpBreakthroughTest {
    @Test
    fun evaluateFromMatrix_defaultPathProven() {
        val matrix =
            JSONObject().apply {
                put(
                    FleetDeviceMatrix.KEY_PRODUCT,
                    JSONObject().apply {
                        put(
                            "stillResolutionAdvertised",
                            JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put("cameraId", "0")
                                        put(
                                            "defaultJpeg",
                                            JSONObject().apply {
                                                put("width", 8192)
                                                put("height", 6144)
                                            },
                                        )
                                        put("hasLargerThanDefault", false)
                                    },
                                )
                            },
                        )
                    },
                )
            }
        val evidences = Camera2FullMpBreakthrough.evaluateFromMatrix(matrix)
        assertEquals(1, evidences.size)
        assertEquals(Camera2FullMpBreakthrough.EvidenceTier.DEFAULT, evidences[0].tier)
        assertTrue(evidences[0].provenMp >= 13.0)
    }

    @Test
    fun evaluateFromMatrix_binnedDefaultNotBreakthrough() {
        val matrix =
            JSONObject().apply {
                put(
                    FleetDeviceMatrix.KEY_PRODUCT,
                    JSONObject().apply {
                        put(
                            "stillResolutionAdvertised",
                            JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put("cameraId", "0")
                                        put(
                                            "defaultJpeg",
                                            JSONObject().apply {
                                                put("width", 4096)
                                                put("height", 3072)
                                            },
                                        )
                                        put("hasLargerThanDefault", false)
                                    },
                                )
                            },
                        )
                    },
                )
            }
        assertTrue(Camera2FullMpBreakthrough.evaluateFromMatrix(matrix).isEmpty())
    }

    @Test
    fun evaluateFromMatrix_maxResMapProven() {
        val matrix =
            JSONObject().apply {
                put(
                    FleetDeviceMatrix.KEY_PRODUCT,
                    JSONObject().apply {
                        put(
                            "stillResolutionAdvertised",
                            JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put("cameraId", "2")
                                        put(
                                            "defaultJpeg",
                                            JSONObject().apply {
                                                put("width", 4096)
                                                put("height", 3072)
                                            },
                                        )
                                        put("hasLargerThanDefault", true)
                                        put(
                                            "maxResMapJpeg",
                                            JSONObject().apply {
                                                put("width", 8192)
                                                put("height", 6144)
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        val evidences = Camera2FullMpBreakthrough.evaluateFromMatrix(matrix)
        assertEquals(1, evidences.size)
        assertEquals(Camera2FullMpBreakthrough.EvidenceTier.MAXRES_MAP, evidences[0].tier)
    }

    @Test
    fun toSummaryJson_mergesCaptureTier() {
        val hal =
            listOf(
                Camera2FullMpBreakthrough.CameraEvidence(
                    "0",
                    12.5,
                    Camera2FullMpBreakthrough.EvidenceTier.DEFAULT,
                ),
            )
        val capture =
            listOf(
                Camera2FullMpBreakthrough.CameraEvidence(
                    "0",
                    50.0,
                    Camera2FullMpBreakthrough.EvidenceTier.CAPTURE,
                ),
            )
        val summary = Camera2FullMpBreakthrough.toSummaryJson(hal, capture)
        assertTrue(summary.getBoolean("proven"))
        assertEquals("capture", summary.getString("evidenceTier"))
        assertEquals(50.0, summary.getDouble("maxMpPerSensor"), 0.1)
    }

    @Test
    fun toSummaryJson_notProvenWhenEmpty() {
        val summary = Camera2FullMpBreakthrough.toSummaryJson(emptyList())
        assertFalse(summary.getBoolean("proven"))
    }
}
