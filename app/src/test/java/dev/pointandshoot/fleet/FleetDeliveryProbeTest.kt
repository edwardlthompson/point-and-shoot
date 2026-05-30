package dev.pointandshoot.fleet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetDeliveryProbeTest {
    @Test
    fun `fps within three for 30fps tier`() {
        assertTrue(FleetDeliveryProbe.fpsMatches(30, 29.0))
        assertFalse(FleetDeliveryProbe.fpsMatches(30, 25.0))
    }

    @Test
    fun `hfr requires seventy five percent of target`() {
        assertTrue(FleetDeliveryProbe.fpsMatches(120, 90.0))
        assertFalse(FleetDeliveryProbe.fpsMatches(120, 80.0))
    }

    @Test
    fun `classify resolution mismatch`() {
        val probe =
            FleetDeliveryProbe.classify(
                FleetDeliveryProbe.Requested(3840, 2160, 30),
                FleetDeliveryProbe.Actual(1920, 1080, 30.0),
            )
        assertFalse(probe.matchOk)
        org.junit.Assert.assertEquals("resolution_mismatch", probe.mismatchReason)
    }
}
