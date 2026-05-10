package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the horizon overlay's rotation math + cardinal-detection helper.
 *
 * The visual contract: the bar must be horizontal in the user's head-upright view at every
 * physical hold (smooth, no snapping). The colour switches to green only when the phone is at
 * a cardinal orientation.
 */
class HorizonLevelOverlayTest {

    @Test
    fun `0 deg is level`() {
        assertEquals(0f, computeRollDeltaFromCardinal(0f), 0.001f)
    }

    @Test
    fun `90 deg is level (canonical landscape)`() {
        assertEquals(0f, computeRollDeltaFromCardinal(90f), 0.001f)
    }

    @Test
    fun `180 deg is level (upside-down portrait)`() {
        assertEquals(0f, computeRollDeltaFromCardinal(180f), 0.001f)
        assertEquals(0f, computeRollDeltaFromCardinal(-180f), 0.001f)
    }

    @Test
    fun `270 deg is level (other landscape)`() {
        assertEquals(0f, computeRollDeltaFromCardinal(270f), 0.001f)
        assertEquals(0f, computeRollDeltaFromCardinal(-90f), 0.001f)
    }

    @Test
    fun `small positive tilt from any cardinal returns same`() {
        assertEquals(1.5f, computeRollDeltaFromCardinal(1.5f), 0.001f)
        assertEquals(1.5f, computeRollDeltaFromCardinal(91.5f), 0.001f)
        assertEquals(1.5f, computeRollDeltaFromCardinal(181.5f), 0.001f)
    }

    @Test
    fun `45 deg is the boundary - returns 45`() {
        assertEquals(45f, computeRollDeltaFromCardinal(45f), 0.001f)
        assertEquals(-45f, computeRollDeltaFromCardinal(-45f), 0.001f)
    }

    @Test
    fun `46 deg wraps near minus 44 (snaps to next cardinal)`() {
        assertEquals(-44f, computeRollDeltaFromCardinal(46f), 0.001f)
    }

    // Rotation contract: drawScope.rotate(α) is CW-positive and the formula must keep the
    // bar horizontal in the user's view at all four cardinal holds for the typical
    // locked-landscape activity (displayDeg = 90).

    @Test
    fun `displayDeg 90, natural portrait hold rotates 90 CW`() {
        // physicalDeg=0 (natural portrait): activity rotated 90° CCW in user's view, so a
        // canvas-vertical line is what reads horizontal to the user.
        assertEquals(90f, horizonDrawAngleCwDeg(displayDeg = 90f, physicalDeg = 0f), 0.001f)
    }

    @Test
    fun `displayDeg 90, intended landscape hold rotates 0`() {
        // physicalDeg=90 (matches the activity's natural landscape orientation): no rotation
        // needed — canvas-horizontal already reads horizontal to the user.
        assertEquals(0f, horizonDrawAngleCwDeg(displayDeg = 90f, physicalDeg = 90f), 0.001f)
    }

    @Test
    fun `displayDeg 90, upside-down portrait rotates -90 CW`() {
        // physicalDeg=180: activity rotated 90° CW in user's view; canvas needs the opposite.
        assertEquals(-90f, horizonDrawAngleCwDeg(displayDeg = 90f, physicalDeg = 180f), 0.001f)
    }

    @Test
    fun `displayDeg 90, opposite landscape hold rotates to 180-equivalent`() {
        // physicalDeg=270: 180°-flip of intended hold; horizontal-in-canvas is also
        // horizontal-in-user-view (just from the opposite side).
        val angle = horizonDrawAngleCwDeg(displayDeg = 90f, physicalDeg = 270f)
        // Normalised to (-180, 180]: -180 maps to +180 by convention here.
        assertEquals(180f, angle, 0.001f)
    }

    @Test
    fun `displayDeg 90, mid-tilt rotates smoothly`() {
        // 45° between portrait and landscape — bar should rotate by 45° CW (half-way).
        assertEquals(45f, horizonDrawAngleCwDeg(displayDeg = 90f, physicalDeg = 45f), 0.001f)
        // 30° tilt past portrait — bar tracks gravity continuously.
        assertEquals(60f, horizonDrawAngleCwDeg(displayDeg = 90f, physicalDeg = 30f), 0.001f)
    }

    @Test
    fun `displayDeg 270, intended landscape hold rotates 0`() {
        // For ROTATION_270 activities the matching physical hold is also physicalDeg=270.
        assertEquals(0f, horizonDrawAngleCwDeg(displayDeg = 270f, physicalDeg = 270f), 0.001f)
    }
}
