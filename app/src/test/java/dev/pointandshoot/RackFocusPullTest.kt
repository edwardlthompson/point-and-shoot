package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RackFocusPullTest {
    @Test
    fun interpolateDiopters_endpoints() {
        assertEquals(1f, RackFocusPull.interpolateDiopters(1f, 5f, 0f), 0.001f)
        assertEquals(5f, RackFocusPull.interpolateDiopters(1f, 5f, 1f), 0.001f)
        assertEquals(3f, RackFocusPull.interpolateDiopters(1f, 5f, 0.5f), 0.001f)
    }

    @Test
    fun stepCount_scalesWithDuration() {
        assertEquals(15, RackFocusPull.stepCount(500))
        assertEquals(30, RackFocusPull.stepCount(1000))
    }

    @Test
    fun rackReady_requiresBothWaypoints() {
        assertFalse(RackFocusPull.rackReady(null, 2f))
        assertFalse(RackFocusPull.rackReady(1f, null))
        assertTrue(RackFocusPull.rackReady(1f, 2f))
    }

    @Test
    fun canRack_mDialOrManualDistance() {
        assertTrue(
            RackFocusPull.canRack(
                CommandDialMode.M,
                PreviewFocusSelection.Auto,
            ),
        )
        assertTrue(
            RackFocusPull.canRack(
                CommandDialMode.H,
                PreviewFocusSelection.ManualDistance,
            ),
        )
        assertFalse(
            RackFocusPull.canRack(
                CommandDialMode.H,
                PreviewFocusSelection.Auto,
            ),
        )
    }
}
