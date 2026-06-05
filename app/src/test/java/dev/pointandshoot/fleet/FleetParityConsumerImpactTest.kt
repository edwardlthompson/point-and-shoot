package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FleetParityConsumerImpactTest {
    @Test
    fun `av1 tiers downgrade to engineering when matrix sessionOk false`() {
        val row =
            CameraCapabilityCatalog.registry.first { it.id == "video.av1.1080p" }
        val matrix =
            JSONObject().apply {
                put(
                    FleetDeviceMatrix.KEY_CAMERAS,
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put(
                                    "featureGates",
                                    JSONObject().apply {
                                        put(
                                            "av1",
                                            JSONObject().apply {
                                                put("advertised", true)
                                                put("sessionOk", false)
                                                put("appEnabled", false)
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        assertEquals(
            FleetParitySweep.ConsumerImpact.ENGINEERING_ONLY,
            FleetParityConsumerImpact.resolve(row, matrix),
        )
    }

    @Test
    fun `av1 tiers stay ship blocker when matrix sessionOk true`() {
        val row =
            CameraCapabilityCatalog.registry.first { it.id == "video.av1.1080p" }
        val matrix =
            JSONObject().apply {
                put(
                    FleetDeviceMatrix.KEY_CAMERAS,
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put(
                                    "featureGates",
                                    JSONObject().apply {
                                        put(
                                            "av1",
                                            JSONObject().apply {
                                                put("advertised", true)
                                                put("sessionOk", true)
                                                put("appEnabled", true)
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        assertEquals(
            FleetParitySweep.ConsumerImpact.SHIP_BLOCKER,
            FleetParityConsumerImpact.resolve(row, matrix),
        )
    }
}
