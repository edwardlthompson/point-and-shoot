package dev.pointandshoot

import dev.pointandshoot.fleet.OnePlus13FleetPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetCameraStartupScanTest {
    @Test
    fun dngDeviceProfile_uwTele_onOnePlusModel() {
        assertNotNull(DngDeviceColorProfile.fmScaleForOp13Leaf(OnePlus13FleetPolicy.CANONICAL_UW))
        assertNotNull(DngDeviceColorProfile.fmScaleForOp13Leaf(OnePlus13FleetPolicy.CANONICAL_TELE))
        assertEquals(1.147f, DngDeviceColorProfile.fmScaleFor("CPH2655", "3")!!.scaleR, 0.001f)
    }

    @Test
    fun videoShutterAngle_chipLabels() {
        assertEquals("180°", VideoShutterAngle.Angle180.chipLabel())
    }

    @Test
    fun focalSlotAvailability_12MpGate_matchesFleetScanPolicy() {
        assertTrue(FocalSlotAvailability.digitalEqSlotsEnabled(4000, 4000))
        assertTrue(FocalSlotAvailability.megapixelsFromActiveArray(4000, 4000) >= 12.0)
        assertTrue(FocalSlotAvailability.megapixelsFromActiveArray(4000, 2999) < 12.0)
    }
}
