package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM unit tests for the math behind [rememberDeviceUiRotationDegrees] — the
 * activity-locked-to-landscape chrome rotation.
 *
 * The composable itself isn't testable in JVM (it taps `LocalContext` for the SensorManager),
 * but the cardinal-snap and counter-rotation math is — and that's where bugs show up as
 * "icons read upside-down when phone is in physical-landscape".
 *
 * Sensor convention: per Android docs, both [android.hardware.Sensor.TYPE_ACCELEROMETER] and
 * [android.hardware.Sensor.TYPE_GRAVITY] return identical values at rest — `+g along the axis
 * pointing up in world frame`. So natural-portrait at rest is `(ax=0, ay=+g)`. The composable
 * passes those values straight through to [snapPhysicalCardinal] without any sign inversion.
 */
class DeviceUiRotationTest {
    private val g = 9.81f

    // -------- snapPhysicalCardinal --------

    @Test
    fun `natural portrait snaps to 0 degrees`() {
        // Natural portrait at rest: sensor reads +g along +Y (the up-axis in world frame).
        assertEquals(0f, snapPhysicalCardinal(ax = 0f, ay = +g, current = 999f), 0.001f)
    }

    @Test
    fun `device rotated 90 deg clockwise snaps to 90`() {
        // Phone rotated 90° CW (when viewed from the front): natural-LEFT axis points up, so
        // the up-axis in world frame is the device's -X. Sensor reads `+g along -X` ⇒ ax=-g.
        // (This is also what `getRotation() == Surface.ROTATION_270` returns on a portrait-
        // natural device, since the system reports the device's own rotation, not the
        // compensating display rotation.)
        assertEquals(90f, snapPhysicalCardinal(ax = -g, ay = 0f, current = 999f), 0.001f)
    }

    @Test
    fun `upside-down portrait snaps to 180`() {
        // Top-edge points down: world up-axis is device's -Y, so sensor reads ay=-g.
        assertEquals(180f, snapPhysicalCardinal(ax = 0f, ay = -g, current = 999f), 0.001f)
    }

    @Test
    fun `device rotated 90 deg counter-clockwise snaps to 270`() {
        // Phone rotated 90° CCW (when viewed from the front): natural-RIGHT axis points up, so
        // sensor reads `+g along +X` ⇒ ax=+g. This is `getRotation() == Surface.ROTATION_90`
        // on a portrait-natural device — the orientation that matches the Sony Pro "right way
        // up" landscape and is the canonical hold for our locked-landscape activity.
        assertEquals(270f, snapPhysicalCardinal(ax = +g, ay = 0f, current = 999f), 0.001f)
    }

    @Test
    fun `face-up or face-down keeps the previous cardinal`() {
        // No meaningful in-plane gravity → don't churn chrome rotation.
        val previous = 90f
        assertEquals(previous, snapPhysicalCardinal(ax = 0.1f, ay = 0.1f, current = previous), 0.001f)
    }

    @Test
    fun `near-45 hysteresis keeps the previous quadrant`() {
        // Roll ≈ 45° (between portrait and landscape-CCW): falls in the hysteresis band, so
        // the function should NOT flip from "portrait" to "landscape-CCW" until well past the
        // boundary.
        val ax = -g * Math.sin(Math.toRadians(45.0)).toFloat()
        val ay = +g * Math.cos(Math.toRadians(45.0)).toFloat()
        assertEquals(0f, snapPhysicalCardinal(ax = ax, ay = ay, current = 0f), 0.001f)
    }

    @Test
    fun `well past 45 plus hysteresis snaps to landscape`() {
        // Roll = 70° → clearly in the "landscape-CCW" quadrant.
        val ax = -g * Math.sin(Math.toRadians(70.0)).toFloat()
        val ay = +g * Math.cos(Math.toRadians(70.0)).toFloat()
        assertEquals(90f, snapPhysicalCardinal(ax = ax, ay = ay, current = 0f), 0.001f)
    }

    // -------- computeUiRotationDegrees --------
    //
    // The rotation value is the literal `rotationZ` we feed into `Modifier.graphicsLayer` to
    // make chrome appear upright to a head-upright user. Empirically Compose's rotationZ in
    // our render context behaves CW-positive (despite docs claiming CCW) — see
    // [computeUiRotationDegrees] kdoc — so the values below are the "CW-positive degrees" needed
    // to bring chrome's render-up to world-up.

    @Test
    fun `display 90 with phone in canonical landscape yields zero ui rotation`() {
        // The "matching" landscape for `screenOrientation=landscape` (displayDeg=90) is the
        // physical orientation where natural-RIGHT points up (ax=+g, snapped → 270°). In this
        // hold the activity's TOP edge — which lands on the device's natural-RIGHT direction —
        // is at world-UP, so chrome is already upright in the user's view: NO rotation needed.
        assertEquals(0f, computeUiRotationDegrees(displayDeg = 90f, physicalDeg = 270f), 0.001f)
    }

    @Test
    fun `display 90 with phone in physical portrait rotates chrome 270 deg`() {
        // Activity locked landscape (display=90), user holds phone vertically (physical=0):
        // chrome's render-up sits along the device's natural-RIGHT axis = user's RIGHT, so
        // chrome must rotate 90° CCW in the user's view to move its up-axis from user-RIGHT
        // (3 o'clock) to user-UP (12 o'clock). With Compose's CW-positive rotationZ in our
        // render context, that rotation comes out as `rotationZ = 270` (= −90).
        assertEquals(270f, computeUiRotationDegrees(displayDeg = 90f, physicalDeg = 0f), 0.001f)
    }

    @Test
    fun `display 90 with phone in flipped landscape rotates chrome 180`() {
        // Phone rotated 90° CW from natural (natural-LEFT up, ax=-g, snapped → 90°): the
        // activity's render-up axis (= device's natural-RIGHT) ends up at world-DOWN, so chrome
        // is upside-down in user view. Counter-rotate 180° (sign-symmetric).
        assertEquals(180f, computeUiRotationDegrees(displayDeg = 90f, physicalDeg = 90f), 0.001f)
    }

    @Test
    fun `display 90 with phone in upside-down portrait rotates chrome 90`() {
        // Phone flipped 180° (top-edge down, ay=-g, snapped → 180°): chrome render-up sits at
        // user's LEFT, so we rotate 90° CW in the user's view (LEFT → UP). With Compose's
        // CW-positive rotationZ, that's `rotationZ = 90`.
        assertEquals(90f, computeUiRotationDegrees(displayDeg = 90f, physicalDeg = 180f), 0.001f)
    }

    @Test
    fun `result is always normalized into 0 to 360 range`() {
        val r = computeUiRotationDegrees(displayDeg = 90f, physicalDeg = 90f)
        assertEquals(true, r in 0f..360f)
        // Display in natural portrait (rotation 0), phone rotated 90° CCW (natural-RIGHT up,
        // physicalDeg=270): activity-up = device's natural-TOP = at user's LEFT, so chrome
        // rotates 90° CW in the user's view (LEFT → UP) → `rotationZ = 90`.
        val r2 = computeUiRotationDegrees(displayDeg = 0f, physicalDeg = 270f)
        assertEquals(90f, r2, 0.001f)
    }

    // -------- computeRollAngleDegrees (un-snapped, used by orientation probe) --------

    @Test
    fun `roll is null when planar gravity is too small`() {
        // Phone roughly face-up / face-down: there's not enough in-plane gravity to determine
        // a roll, so the function returns null instead of churning the readout.
        assertEquals(null, computeRollAngleDegrees(ax = 0.5f, ay = 0.5f))
    }

    @Test
    fun `roll matches snap function for cardinals`() {
        assertEquals(0f, computeRollAngleDegrees(ax = 0f, ay = +g)!!, 0.5f)
        assertEquals(90f, computeRollAngleDegrees(ax = -g, ay = 0f)!!, 0.5f)
        assertEquals(180f, computeRollAngleDegrees(ax = 0f, ay = -g)!!, 0.5f)
        assertEquals(270f, computeRollAngleDegrees(ax = +g, ay = 0f)!!, 0.5f)
    }

    @Test
    fun `roll smoothly interpolates between cardinals`() {
        // Halfway between portrait (0°) and natural-LEFT-up (90°).
        val ax = -g * Math.sin(Math.toRadians(45.0)).toFloat()
        val ay = +g * Math.cos(Math.toRadians(45.0)).toFloat()
        assertEquals(45f, computeRollAngleDegrees(ax = ax, ay = ay)!!, 0.5f)
    }
}
