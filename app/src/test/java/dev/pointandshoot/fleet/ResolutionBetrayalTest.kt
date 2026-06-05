package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolutionBetrayalTest {

    @Test
    fun computeFromMatrix_zeroWhenNoEntries() {
        val matrix = JSONObject().put(FleetDeviceMatrix.KEY_PRODUCT, JSONObject())
        assertEquals(0, ResolutionBetrayal.computeFromMatrix(matrix))
    }

    @Test
    fun computeFromMatrix_allBetrayedWhenHasLargerThanDefault() {
        val arr =
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("cameraId", "0")
                        put("hasLargerThanDefault", true)
                        put("defaultJpegMp", 12.0)
                        put("maxAdvertisedJpegMp", 50.0)
                    },
                )
                put(
                    JSONObject().apply {
                        put("cameraId", "2")
                        put("hasLargerThanDefault", false)
                        put("defaultJpegMp", 12.0)
                        put("maxAdvertisedJpegMp", 12.0)
                    },
                )
            }
        val matrix =
            JSONObject().put(
                FleetDeviceMatrix.KEY_PRODUCT,
                JSONObject().put("stillResolutionAdvertised", arr),
            )
        assertEquals(50, ResolutionBetrayal.computeFromMatrix(matrix))
    }

    @Test
    fun computeFromMatrix_ratioThresholdWithoutFlag() {
        val arr =
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("cameraId", "0")
                        put("hasLargerThanDefault", false)
                        put("defaultJpegMp", 10.0)
                        put("maxAdvertisedJpegMp", 50.0)
                    },
                )
            }
        val matrix =
            JSONObject().put(
                FleetDeviceMatrix.KEY_PRODUCT,
                JSONObject().put("stillResolutionAdvertised", arr),
            )
        assertEquals(100, ResolutionBetrayal.computeFromMatrix(matrix))
    }

    @Test
    fun computeFromMatrix_nestedSizeJson() {
        val arr =
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("cameraId", "2")
                        put("hasLargerThanDefault", false)
                        put(
                            "defaultJpeg",
                            JSONObject().apply {
                                put("width", 4096)
                                put("height", 3072)
                                put("mp", 12.58)
                            },
                        )
                    },
                )
            }
        val product =
            JSONObject().apply {
                put("stillResolutionAdvertised", arr)
                put(
                    "focalSlots",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("cameraId", "2")
                                put("focalMm35", 23)
                                put("megapixels", 50.0)
                            },
                        )
                    },
                )
            }
        val matrix = JSONObject().put(FleetDeviceMatrix.KEY_PRODUCT, product)
        assertEquals(100, ResolutionBetrayal.computeFromMatrix(matrix))
    }
}
