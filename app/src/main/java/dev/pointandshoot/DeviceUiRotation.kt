package dev.pointandshoot

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Sony Photography-Pro–style chrome rotation.
 *
 * The **preview texture** is not following device rotation (static `graphicsLayer` offset only);
 * the window may still change orientation. The rails, settings cubes and HUD readouts
 * counter-rotate by this many degrees so that **chrome stays upright in the user's view** as
 * they tilt the phone — the same idea as Sony's Photography Pro: icons read upright while the
 * live image stays on a fixed spin.
 *
 * Math (see [computeUiRotationDegrees]):
 *   - Read the gravity vector to find which device cardinal is "world-up" right now.
 *   - Snap to the nearest 0/90/180/270 quadrant (with hysteresis) so chrome only ever sits at
 *     a 90° step — no continuous wobble at small tilts.
 *   - `uiRotation = (displayDegrees - physicalDegrees) mod 360` — counter-rotates chrome so
 *     "screen-up after rotation" lines up with "world-up" no matter how the phone is held.
 *
 * The returned [Float] is the rotation (degrees, +CCW per Compose's [androidx.compose.ui.graphics.graphicsLayer]
 * rotationZ convention) to apply via `Modifier.graphicsLayer { rotationZ = ... }` to chrome that
 * should stay upright. Apply it ONLY to chrome (rails, cubes, overlays), never to the preview
 * texture.
 */
@Composable
fun rememberDeviceUiRotationDegrees(): Float = rememberDeviceUiRotationState().snappedDegrees

/**
 * Snapped + un-snapped chrome rotation. The snapped value is what chrome should render at; the
 * smooth value is the continuous-rotation equivalent the snap was derived from. Diagnostic
 * overlays use both to verify the snap quantizer is doing what it should.
 *
 * `physicalDegrees` is the un-snapped roll angle from [computeRollAngleDegrees] (in the same
 * convention as [snapPhysicalCardinal]: 0° = natural-portrait top-up, +CCW). `null` when the
 * sensor isn't reporting enough planar gravity to trust the reading (face-up/face-down).
 */
data class DeviceUiRotationState(
    val snappedDegrees: Float,
    val smoothDegrees: Float,
    val physicalDegrees: Float?,
)

/**
 * State variant of [rememberDeviceUiRotationDegrees] that also exposes the un-snapped values.
 * Use this when something other than chrome (e.g. the debug-menu orientation panel) needs to
 * compare the chrome's quantized angle against the smooth gravity reading. Chrome itself should
 * keep using [rememberDeviceUiRotationDegrees] so it only ever lands on a 90° step.
 */
@Composable
fun rememberDeviceUiRotationState(): DeviceUiRotationState {
    val context = LocalContext.current
    // Re-read display rotation when the activity rotates between sensorLandscape's two
    // landscape endpoints (system handles the 180° flip on its own; we must update our
    // baseline so chrome rotation stays aligned).
    val configuration = LocalConfiguration.current
    val displayDeg = remember(configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
        when (context.displayRotationCompat()) {
            Surface.ROTATION_0 -> 0f
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> 0f
        }
    }
    var snappedRotation by remember { mutableFloatStateOf(0f) }
    var smoothRotation by remember { mutableFloatStateOf(0f) }
    var smoothPhysical by remember { mutableStateOf<Float?>(null) }
    var lastSnapped by remember { mutableFloatStateOf(displayDeg) }

    DisposableEffect(context, displayDeg) {
        // Reset to "no chrome rotation" whenever the system landscape flips, so we never
        // briefly show old chrome rotation while the new sensor reading is on its way in.
        snappedRotation = 0f
        smoothRotation = 0f
        smoothPhysical = null
        lastSnapped = displayDeg
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // [snapPhysicalCardinal] is written for the **accelerometer** convention: at rest the
        // sensor reports proper acceleration, which is the *opposite sign* of gravity in the
        // device frame (so natural-portrait at rest reads ay = +g, not -g). The gravity sensor
        // reports gravity itself with the opposite sign — feeding gravity values into the
        // formula leaves chrome 180° off in real-world holds, which is exactly the bug we hit
        // on devices that expose Sensor.TYPE_GRAVITY. Prefer the accelerometer; fall back to
        // gravity with sign-inverted readings only if the accelerometer is missing.
        // [Sensor.TYPE_GRAVITY] (sensor-fusion, low-pass filtered) is preferred when available
        // because it stays stable while the user is gently moving the phone; per Android docs
        // its convention matches the accelerometer (both report +g along the up-axis at rest).
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val rawPhysical = computeRollAngleDegrees(ax, ay)
                    if (rawPhysical != null) {
                        smoothPhysical = rawPhysical
                        val targetSmooth = computeUiRotationDegrees(displayDeg, rawPhysical)
                        if (abs(targetSmooth - smoothRotation) > 0.5f) {
                            smoothRotation = targetSmooth
                        }
                    }
                    val snapped = snapPhysicalCardinal(ax, ay, lastSnapped)
                    if (snapped != lastSnapped) {
                        lastSnapped = snapped
                    }
                    val target = computeUiRotationDegrees(displayDeg, snapped)
                    if (abs(target - snappedRotation) > 0.5f) {
                        snappedRotation = target
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
        if (sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }
    return DeviceUiRotationState(
        snappedDegrees = snappedRotation,
        smoothDegrees = smoothRotation,
        physicalDegrees = smoothPhysical,
    )
}

/**
 * Continuous roll angle (degrees, +CCW) in the same convention as [snapPhysicalCardinal].
 * Returns `null` when there isn't enough planar gravity to determine which way is up
 * (phone roughly face-up/face-down).
 */
internal fun computeRollAngleDegrees(ax: Float, ay: Float): Float? {
    val planar = ax * ax + ay * ay
    if (planar < 9f) return null
    val raw = (atan2(-ax.toDouble(), ay.toDouble()) * (180.0 / PI)).toFloat()
    return ((raw % 360f) + 360f) % 360f
}

/**
 * Snaps the device's roll angle to the nearest 0/90/180/270° cardinal with hysteresis around
 * each boundary so chrome never flips back-and-forth while the user holds the phone near 45°.
 *
 * Convention (matches [HorizonLevelOverlay]):
 *   - Natural portrait, top-edge up: `roll = atan2(-ax, ay) ≈ 0°`
 *   - Phone rotated 90° counter-clockwise (top-edge to user's left ≈ Surface.ROTATION_90): `≈ +90°`
 *   - Upside-down portrait: `≈ ±180°`
 *   - Phone rotated 90° clockwise (top-edge to user's right ≈ Surface.ROTATION_270): `≈ -90°`/`+270°`
 */
internal fun snapPhysicalCardinal(ax: Float, ay: Float, current: Float): Float {
    // Need at least ~30% of g on the in-screen plane before we trust the reading; otherwise
    // the device is roughly face-up/face-down and there's no meaningful "world-up" direction —
    // keep showing what we last had.
    val planar = ax * ax + ay * ay
    if (planar < 9f) return current
    val raw = (atan2(-ax.toDouble(), ay.toDouble()) * (180.0 / PI)).toFloat()
    val norm = ((raw % 360f) + 360f) % 360f
    // 15° hysteresis past each 45° boundary: only snap to a new quadrant when the reading is
    // clearly inside it. Helps when the user holds the phone exactly at a 45° corner.
    val hysteresis = 15f
    val cur = ((current % 360f) + 360f) % 360f
    return when {
        norm < 45f - hysteresis || norm >= 315f + hysteresis -> 0f
        norm in (45f + hysteresis)..(135f - hysteresis) -> 90f
        norm in (135f + hysteresis)..(225f - hysteresis) -> 180f
        norm in (225f + hysteresis)..(315f - hysteresis) -> 270f
        else -> cur
    }
}

/**
 * UI rotation (degrees, fed verbatim into Compose's `rotationZ`) chrome should apply so it
 * appears upright to a head-upright user, given the activity's current display rotation and
 * the device's snapped physical cardinal.
 *
 * Why `(360° − displayDeg − physicalDeg) mod 360`:
 *   - In our locked-landscape activity (`displayDeg=90`) the activity's TOP edge lands along
 *     the device's **natural-RIGHT** axis on the framebuffer, so chrome's rendered "up" axis
 *     is at device +X.
 *   - When the device is held at `physicalDeg` (CCW from natural-portrait, per
 *     [snapPhysicalCardinal]), device +X ends up at world angle `physicalDeg` in user space.
 *   - To rotate chrome's "up" from `physicalDeg` to world-UP (= 90°), we rotate chrome by
 *     `(90° − physicalDeg)` in the user's view. Empirically `Modifier.graphicsLayer { rotationZ }`
 *     in our render context behaves CW-positive (the "+CCW per docs" claim does not hold up
 *     against on-device screenshots), so the value we feed into `rotationZ` to achieve a
 *     `(90° − physicalDeg)` CCW user-view rotation is the **negative** of that — collapsing to
 *     `(360° − displayDeg − physicalDeg) mod 360`. Concretely:
 *       - `physicalDeg=270` (natural-RIGHT-UP, ax=+g) → 360 − 90 − 270 = **0°** rotation ✓
 *       - `physicalDeg=90`  (natural-LEFT-UP,  ax=-g) → 360 − 90 − 90  = **180°** ✓
 *       - `physicalDeg=0`   (natural portrait, ay=+g) → 360 − 90 − 0   = **270°**
 *       - `physicalDeg=180` (upside-down,      ay=-g) → 360 − 90 − 180 = **90°**
 */
internal fun computeUiRotationDegrees(displayDeg: Float, physicalDeg: Float): Float {
    val raw = 360f - displayDeg - physicalDeg
    return ((raw % 360f) + 360f) % 360f
}
