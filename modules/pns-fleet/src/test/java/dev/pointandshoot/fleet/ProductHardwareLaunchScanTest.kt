package dev.pointandshoot.fleet

import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductHardwareLaunchScanTest {
    @Test
    fun buildInteractiveProbeFromEvents_marksCameraAndFocus() {
        val events =
            listOf(
                ProductHardwareLaunchScan.HardwareKeyProbeEvent(
                    keyCode = KeyEvent.KEYCODE_FOCUS,
                    scanCode = 1,
                    actionLabel = "DOWN",
                    source = 0,
                    repeatCount = 0,
                    deviceId = 0,
                ),
                ProductHardwareLaunchScan.HardwareKeyProbeEvent(
                    keyCode = KeyEvent.KEYCODE_CAMERA,
                    scanCode = 2,
                    actionLabel = "UP",
                    source = 0,
                    repeatCount = 0,
                    deviceId = 0,
                ),
            )
        val probe = ProductHardwareLaunchScan.buildInteractiveProbeFromEvents(events)
        assertTrue(probe.getBoolean("cameraKeyConfirmed"))
        assertTrue(probe.getBoolean("focusKeyConfirmed"))
        assertEquals(2, probe.getJSONArray("distinctKeyCodes").length())
    }

    @Test
    fun hasDedicatedCameraKeyEvidence_readsLikelyFlag() {
        val root =
            JSONObject().apply {
                put(
                    FleetDeviceMatrix.KEY_PRODUCT,
                    JSONObject().apply {
                        put(
                            "hardwareButtons",
                            JSONObject().apply {
                                put("dedicatedCameraKeyLikely", true)
                            },
                        )
                    },
                )
            }
        assertTrue(ProductHardwareLaunchScan.hasDedicatedCameraKeyEvidence(root))
    }

    @Test
    fun extraShutterKeyCodes_excludesStandardVolumeAndMedia() {
        val root =
            JSONObject().apply {
                put(
                    FleetDeviceMatrix.KEY_PRODUCT,
                    JSONObject().apply {
                        put(
                            "hardwareButtons",
                            JSONObject().apply {
                                put(
                                    "interactiveProbe",
                                    JSONObject().apply {
                                        put(
                                            "distinctKeyCodes",
                                            JSONArray(listOf(KeyEvent.KEYCODE_CAMERA, 131)),
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        assertEquals(setOf(131), ProductHardwareLaunchScan.extraShutterKeyCodes(root))
    }
}
