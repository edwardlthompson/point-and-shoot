package dev.pointandshoot

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.PI

/** Roll angle (signed degrees from nearest cardinal) is "level" when within this tolerance. */
private const val LEVEL_TOLERANCE_DEG = 1.5f

/**
 * Sony-style horizon indicator that **always appears horizontal in the user's head-upright
 * view, smoothly tracking gravity** — independently of chrome rotation, the activity's locked
 * landscape, or the camera buffer.
 *
 * The bar:
 *  - Stays perfectly horizontal as the user tilts the phone (continuous gravity-driven
 *    rotation, **not** chrome's snapped cardinal rotation, so it never wobbles or lags).
 *  - Turns green when the phone is at any cardinal orientation (within ±[LEVEL_TOLERANCE_DEG]°
 *    of 0/90/180/270° from natural portrait), white otherwise.
 *  - Has perpendicular tick marks at both ends so it stays visible against busy scenes.
 *
 * The math is in [horizonDrawAngleCwDeg]: given the display rotation and the live gravity
 * reading, it returns the [drawscope.rotate] argument (CW-positive — Android's documented
 * convention) that rotates a canvas-local horizontal line to be world-horizontal in the user's
 * view. See that function's KDoc for the derivation.
 */
@Composable
fun HorizonLevelOverlay(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var ax by remember { mutableFloatStateOf(0f) }
    var ay by remember { mutableFloatStateOf(Float.NaN) }

    val displayDeg = remember {
        when (context.displayRotationCompat()) {
            Surface.ROTATION_0 -> 0f
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> 0f
        }
    }

    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // Light low-pass filter — gravity sensor is already filtered, accelerometer benefits
        // from a tiny smoothing pass to keep the horizon bar stable at small jitter without
        // introducing visible lag.
        var sx = Float.NaN
        var sy = Float.NaN
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val rx = event.values[0]
                    val ry = event.values[1]
                    sx = if (sx.isNaN()) rx else sx + (rx - sx) * 0.25f
                    sy = if (sy.isNaN()) ry else sy + (ry - sy) * 0.25f
                    ax = sx
                    ay = sy
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
        if (sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sm.unregisterListener(listener) }
    }

    val strokePx = with(density) { 6.dp.toPx() }
    val tickLenPx = with(density) { 14.dp.toPx() }
    val centerDotPx = with(density) { 4.dp.toPx() }

    // Continuous physical roll (degrees CCW from natural-portrait); only valid when gravity has
    // a meaningful in-plane component (≥ 30 % of g). Face-up / face-down → keep the bar
    // horizontal and grey-toned rather than spinning around.
    val planar2 = ax * ax + ay * ay
    val gravityValid = planar2 >= 9f && !ay.isNaN()
    val rollDeg =
        if (!gravityValid) 0f
        else (atan2(-ax.toDouble(), ay.toDouble()) * (180.0 / PI)).toFloat()
    val drawCwDeg = horizonDrawAngleCwDeg(displayDeg, rollDeg)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val safeMargin = with(density) { 24.dp.toPx() }
        val len = (min(size.width, size.height) - 2f * safeMargin).coerceAtLeast(0f) * 0.5f

        val isLevel = gravityValid && abs(computeRollDeltaFromCardinal(rollDeg)) <= LEVEL_TOLERANCE_DEG
        val liveColor = if (isLevel) PnsColors.OkGreen else Color.White
        val refColor = Color.White.copy(alpha = 0.30f)

        // Centre dot — always visible regardless of bar position.
        drawCircle(color = refColor, radius = centerDotPx, center = Offset(cx, cy))

        // Rotate the canvas's drawing matrix so a "horizontal line in canvas space" ends up
        // world-horizontal in the user's view.
        rotate(degrees = drawCwDeg, pivot = Offset(cx, cy)) {
            val ex1 = cx - len
            val ex2 = cx + len
            drawLine(
                color = liveColor,
                start = Offset(ex1, cy),
                end = Offset(ex2, cy),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
            // Vertical end ticks (perpendicular to the bar) — visible against bright/busy scenes.
            drawLine(
                color = liveColor,
                start = Offset(ex1, cy - tickLenPx),
                end = Offset(ex1, cy + tickLenPx),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = liveColor,
                start = Offset(ex2, cy - tickLenPx),
                end = Offset(ex2, cy + tickLenPx),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Returns the [androidx.compose.ui.graphics.drawscope.rotate] argument (degrees, **CW-positive
 * per [android.graphics.Canvas.rotate]**) that rotates a canvas-local horizontal line so it
 * appears world-horizontal in the user's head-upright view.
 *
 * Derivation
 * ----------
 * Locked-landscape activity (display rotation = `displayDeg`) means the activity's content is
 * composed onto the framebuffer pre-rotated by `displayDeg`, so when the device is held at the
 * matching physical orientation `physicalDeg = displayDeg`, the activity content appears
 * upright.
 *
 * For an **arbitrary** physical hold `physicalDeg` (CCW from natural-portrait, per
 * [snapPhysicalCardinal] / `atan2(-ax, ay)`), the activity is rotated `(displayDeg − physicalDeg)`
 * **CCW** in the user's view.
 *
 * A line drawn in the canvas at `θ_canvas` (CCW from canvas-+X) lands at user-view angle
 * `θ_canvas + (displayDeg − physicalDeg)` (CCW from user-+X). To get the line **horizontal in
 * the user's view** (`θ_user = 0` modulo 180°) we need `θ_canvas = physicalDeg − displayDeg`.
 *
 * `drawscope.rotate(α)` rotates the drawing matrix CW by `α` degrees (Android `Canvas.rotate`
 * convention with screen coords +Y down). Pre-rotating the matrix CW by `α` then drawing a
 * horizontal line yields a line at canvas-angle `−α` (CCW). Setting `−α = physicalDeg −
 * displayDeg` gives `α = displayDeg − physicalDeg`.
 *
 * Cardinal sanity checks (all give world-horizontal in the user's view):
 *   - `displayDeg=90, physicalDeg=0`   → α = +90° → vertical-in-canvas line, which is
 *     horizontal-in-user-view in natural-portrait hold of a locked-landscape activity. ✓
 *   - `displayDeg=90, physicalDeg=90`  → α = 0   → horizontal in canvas, horizontal in
 *     user view (matches the activity's intended hold). ✓
 *   - `displayDeg=90, physicalDeg=180` → α = −90 → vertical-in-canvas line, horizontal in
 *     user view in upside-down portrait hold. ✓
 *   - `displayDeg=90, physicalDeg=270` → α = −180 (= 180°) → horizontal-in-canvas line,
 *     horizontal in user view in natural-RIGHT-UP hold. ✓
 */
internal fun horizonDrawAngleCwDeg(displayDeg: Float, physicalDeg: Float): Float {
    val raw = displayDeg - physicalDeg
    // Normalize to (-180, 180] for cosmetic stability; the value is fed to a rotation matrix
    // so any 360°-equivalent is accepted, but normalising avoids sudden wraps that would make
    // the bar visually flicker if we ever displayed the angle.
    var a = raw % 360f
    if (a > 180f) a -= 360f
    if (a <= -180f) a += 360f
    return a
}

/**
 * Returns the smallest signed angle (in degrees) between [rollDeg] and the nearest cardinal
 * orientation (0/90/180/270°). Accepts any input range; result is in (-45°, +45°].
 *
 * "Level" means the phone is held cleanly at any of the four cardinal orientations — that's
 * the canonical Sony Pro / camera-app interpretation of level when the activity supports
 * multiple landscape/portrait holds.
 */
internal fun computeRollDeltaFromCardinal(rollDeg: Float): Float {
    var d = rollDeg % 90f
    if (d > 45f) d -= 90f
    if (d < -45f) d += 90f
    return d
}

/**
 * @deprecated kept for compatibility with any test that still references it; the overlay now
 * uses [computeRollDeltaFromCardinal] which treats every cardinal as "level".
 */
@Deprecated("Use computeRollDeltaFromCardinal instead.", ReplaceWith("computeRollDeltaFromCardinal(rollDeg)"))
internal fun computeRollDeltaFromHorizontal(rollDeg: Float): Float {
    var d = rollDeg % 180f
    if (d > 90f) d -= 180f
    if (d < -90f) d += 180f
    return d
}
