package dev.pointandshoot.fleet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetFocalRowPolicyTest {
    @Test
    fun `static 85 slot disabled when crop below 12MP`() {
        val spec =
            FleetFocalRowPolicy.FocalRowSpec(
                nativeUwLabel = "14",
                nativeWideLabel = "23",
                nativeTeleLabel = "73",
                staticCropMpGate = mapOf(85 to 8, 150 to 10),
            )
        val slots = FleetFocalRowPolicy.buildSlots(spec)
        val slot85 = slots.first { it.labelMm == "85" }
        assertFalse(slot85.enabled)
        assertEquals("N/A", slot85.subLabel)
    }

    @Test
    fun `parseFromProduct reads focalRow block`() {
        val product =
            JSONObject()
                .put(
                    "focalRow",
                    JSONObject()
                        .put("nativeUwMm", "14")
                        .put("nativeWideMm", "23")
                        .put("nativeTeleMm", "73")
                        .put(
                            "staticCropMp",
                            JSONObject().put("85", 14).put("150", 12),
                        ),
                )
        val spec = FleetFocalRowPolicy.parseFromProduct(product)
        assertEquals("14", spec.nativeUwLabel)
        assertTrue(FleetFocalRowPolicy.staticSlotEnabled(spec, 85))
    }
}
