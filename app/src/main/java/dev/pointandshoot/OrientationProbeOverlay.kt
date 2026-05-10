package dev.pointandshoot

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * **Diagnostic-only** overlay. Shows the inputs we use to align the preview, chrome rotation,
 * and horizon line, plus a small directional triangle anchored to **gravity** (independent
 * of the Compose rotation pipeline). Use this to confirm at a glance that:
 *
 *   1. Gravity readings make sense (one of the in-plane components is ≈ ±g, the other ≈ 0).
 *   2. The display rotation matches the current window rotation (`Surface.ROTATION_*`).
 *   3. The buffer dimensions are 4:3 landscape (e.g. 1920×1440), not portrait or stretched.
 *   4. The view dimensions hand into the preview transform are sane.
 *   5. The chrome rotation values match what the user is seeing.
 *
 * The little directional triangle is drawn **directly from the gravity vector** (not via the
 * snapped `uiRotationDeg` pipeline), so if it points at world-up, gravity is being read
 * correctly. If the chrome `▲` is **also** pointing the same way, the chrome rotation pipeline
 * is correct; if they disagree, the rotation formula is wrong.
 */
@Composable
fun OrientationProbeOverlay(
    bufferSize: android.util.Size?,
    centerViewSize: androidx.compose.ui.unit.IntSize,
    sensorOrientationDeg: Int?,
    chromeRotationDegSnapped: Float,
    chromeRotationDegSmooth: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ax by remember { mutableFloatStateOf(0f) }
    var ay by remember { mutableFloatStateOf(0f) }
    var az by remember { mutableFloatStateOf(0f) }
    var sensorKind by remember { mutableFloatStateOf(0f) } // 1=gravity, 2=accelerometer, 0=none

    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor = gravity ?: accel
        sensorKind =
            when (sensor?.type) {
                Sensor.TYPE_GRAVITY -> 1f
                Sensor.TYPE_ACCELEROMETER -> 2f
                else -> 0f
            }
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    ax = event.values[0]
                    ay = event.values[1]
                    az = event.values[2]
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
        if (sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }

    val rawDisplayRotation = context.displayRotationCompat()
    val displayDegFloat =
        when (rawDisplayRotation) {
            Surface.ROTATION_0 -> 0f
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> 0f
        }
    val displayRotationLabel =
        when (rawDisplayRotation) {
            Surface.ROTATION_0 -> "ROTATION_0 (0°)"
            Surface.ROTATION_90 -> "ROTATION_90 (device 90° CCW from natural)"
            Surface.ROTATION_180 -> "ROTATION_180"
            Surface.ROTATION_270 -> "ROTATION_270 (device 270° CCW = 90° CW from natural)"
            else -> "?"
        }
    val sensorKindLabel =
        when (sensorKind) {
            1f -> "TYPE_GRAVITY"
            2f -> "TYPE_ACCELEROMETER"
            else -> "none"
        }

    // Continuous physical roll (degrees CCW from natural-portrait), computed from gravity.
    // 0 ≈ natural portrait, 90 ≈ natural-LEFT-up, 180 ≈ upside-down, 270 ≈ natural-RIGHT-up.
    val planar = sqrt(ax * ax + ay * ay)
    val physicalDegRaw =
        if (planar < 3f) Float.NaN
        else {
            val raw = (atan2(-ax.toDouble(), ay.toDouble()) * (180.0 / PI)).toFloat()
            ((raw % 360f) + 360f) % 360f
        }

    // The gravity-tip arrow is rendered as chrome (an icon that should appear upright in the
    // user's view, with its tip at world-up). It uses the **same** rotation formula as the
    // chrome rails — see [computeUiRotationDegrees] in DeviceUiRotation.kt — but feeds it the
    // **continuous** physicalDeg instead of the snapped cardinal so the tip moves smoothly as
    // the user tilts the phone. Acts as a ground-truth reference for chrome rotation: if the
    // tip and the chrome rails disagree, the chrome-rotation pipeline is broken.
    val gravityTipAngleDeg =
        if (physicalDegRaw.isNaN()) 0f
        else ((360f - displayDegFloat - physicalDegRaw) % 360f + 360f) % 360f

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(8.dp),
    ) {
        Column {
            DebugLine("display: $displayRotationLabel")
            DebugLine("sensor:  $sensorKindLabel  ax=${"%.2f".format(ax)} ay=${"%.2f".format(ay)} az=${"%.2f".format(az)}")
            DebugLine("physicalDeg: ${if (physicalDegRaw.isNaN()) "(face-up/down)" else "%.1f°".format(physicalDegRaw)}")
            DebugLine("chrome rot snapped=${"%.0f°".format(chromeRotationDegSnapped)}  smooth=${"%.1f°".format(chromeRotationDegSmooth)}")
            DebugLine("buffer:  ${bufferSize?.let { "${it.width}×${it.height}  aspect=${"%.3f".format(it.width.toFloat() / it.height.coerceAtLeast(1).toFloat())}" } ?: "(none)"}")
            DebugLine("view:    ${centerViewSize.width}×${centerViewSize.height}  aspect=${"%.3f".format(centerViewSize.width.toFloat() / centerViewSize.height.coerceAtLeast(1).toFloat())}")
            DebugLine("sensor orientation: ${sensorOrientationDeg?.let { "$it°" } ?: "(none)"}")
            DebugLine("gravity-tip arrow ↓ should point at world-UP:")
            // Gravity-driven triangle. Drawn directly from the raw gravity vector — bypasses
            // the chrome rotation pipeline entirely, so its direction is a ground-truth
            // reference for "where is gravity pointing right now".
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(48.dp),
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                rotate(degrees = gravityTipAngleDeg, pivot = Offset(cx, cy)) {
                    val tip = Offset(cx, cy - size.minDimension * 0.45f)
                    val baseLeft = Offset(cx - size.minDimension * 0.30f, cy + size.minDimension * 0.30f)
                    val baseRight = Offset(cx + size.minDimension * 0.30f, cy + size.minDimension * 0.30f)
                    drawLine(Color(0xFFFF7A00), tip, baseLeft, strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(Color(0xFFFF7A00), tip, baseRight, strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(Color(0xFFFF7A00), baseLeft, baseRight, strokeWidth = 6f, cap = StrokeCap.Round)
                }
                // Center dot for reference.
                drawCircle(Color.White.copy(alpha = 0.4f), radius = 3f, center = Offset(cx, cy))
                // Tiny "label" tick on the right side so we can tell which way is +X in the
                // Canvas frame (always "right of center", regardless of rotation).
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(size.width - 8f, cy),
                    end = Offset(size.width - 2f, cy),
                    strokeWidth = 2f,
                )
            }
        }
    }
}

@Composable
private fun DebugLine(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified),
    )
}
