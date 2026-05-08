package dev.pointandshoot

import androidx.compose.ui.geometry.Offset

/**
 * Pure-data adapter that converts Camera2 face/eye landmarks (in active-array
 * pixel coordinates) into [EyeMark]s in **preview-relative pixel coordinates**
 * for [EyeAfOverlay].
 *
 * The capture engine reads `CaptureResult.STATISTICS_FACES` each frame; for
 * every `Face` we extract the eye landmarks (when face-detect FULL is
 * available) or the face bounding box midline (when only the face center is
 * known). The mapping handles:
 *
 *  * Active-array crop -> preview crop (the preview surface usually shows a
 *    sub-rectangle of the sensor).
 *  * Sensor orientation -> preview orientation (`SENSOR_ORIENTATION` quarter-turns).
 *  * Mirroring on the front camera (UX expectation: mirror eye marks too).
 *
 * Everything here is primitive arithmetic + `androidx.compose.ui.geometry.Offset`
 * so the adapter is JVM-unit-testable. It produces no Android runtime calls.
 */
object FaceDetectAdapter {

    /**
     * Inputs are intentionally typed as primitive coordinates so this stays
     * decoupled from `android.graphics.Rect` / `Point` (which need stubs in
     * pure-JVM tests). Callers do the boxing once at the engine boundary.
     *
     * @param eyeSensor             eye landmark, in active-array pixels.
     * @param activeArrayWidth      width of `SENSOR_INFO_ACTIVE_ARRAY_SIZE`.
     * @param activeArrayHeight     height of `SENSOR_INFO_ACTIVE_ARRAY_SIZE`.
     * @param previewWidth          width of the preview surface in pixels.
     * @param previewHeight         height of the preview surface in pixels.
     * @param sensorOrientationDeg  `SENSOR_ORIENTATION` (0/90/180/270).
     * @param mirrorHorizontally    typically true for the front camera.
     */
    fun mapEyeToPreview(
        eyeSensor: SensorPoint,
        activeArrayWidth: Int,
        activeArrayHeight: Int,
        previewWidth: Int,
        previewHeight: Int,
        sensorOrientationDeg: Int,
        mirrorHorizontally: Boolean,
        confidence: Float = 1f,
    ): EyeMark {
        require(activeArrayWidth > 0 && activeArrayHeight > 0) {
            "active-array dimensions must be positive (was ${activeArrayWidth}x$activeArrayHeight)"
        }
        require(previewWidth > 0 && previewHeight > 0) {
            "preview dimensions must be positive (was ${previewWidth}x$previewHeight)"
        }
        val rot = ((sensorOrientationDeg % 360) + 360) % 360
        require(rot % 90 == 0) {
            "sensorOrientationDeg must be a multiple of 90 (was $sensorOrientationDeg)"
        }

        // Step 1: rotate the sensor coords into the preview's "upright" frame.
        val (rotatedX, rotatedY, rotatedW, rotatedH) = rotateSensorPoint(
            eyeSensor = eyeSensor,
            activeArrayWidth = activeArrayWidth,
            activeArrayHeight = activeArrayHeight,
            rotationDeg = rot,
        )

        // Step 2: scale into the preview surface (assume center-fit / fill match;
        // the engine boundary is responsible for letterboxing / pillarboxing if
        // it differs - this adapter is the math, not the policy).
        val sx = previewWidth.toDouble() / rotatedW.toDouble()
        val sy = previewHeight.toDouble() / rotatedH.toDouble()
        val previewX = rotatedX * sx
        val previewY = rotatedY * sy

        // Step 3: optionally mirror horizontally (front camera UX).
        val finalX = if (mirrorHorizontally) previewWidth - previewX else previewX

        return EyeMark(
            position = Offset(finalX.toFloat(), previewY.toFloat()),
            confidence = confidence.coerceIn(0f, 1f),
        )
    }

    /**
     * Convenience overload for a face *bounding box* (no eye landmarks
     * available). Returns one [EyeMark] at the box midline, two-thirds up
     * from the bottom (a reasonable proxy for "between the eyes" on a
     * standard portrait face crop).
     */
    fun mapFaceCenterToPreview(
        faceLeft: Int,
        faceTop: Int,
        faceRight: Int,
        faceBottom: Int,
        activeArrayWidth: Int,
        activeArrayHeight: Int,
        previewWidth: Int,
        previewHeight: Int,
        sensorOrientationDeg: Int,
        mirrorHorizontally: Boolean,
        confidence: Float = 0.5f,
    ): EyeMark {
        require(faceRight > faceLeft && faceBottom > faceTop) {
            "face rect must have positive area (was [${faceLeft},${faceTop} -> ${faceRight},${faceBottom}])"
        }
        val cx = (faceLeft + faceRight) / 2
        val cy = faceTop + ((faceBottom - faceTop) / 3) // roughly between the eyes
        return mapEyeToPreview(
            eyeSensor = SensorPoint(cx, cy),
            activeArrayWidth = activeArrayWidth,
            activeArrayHeight = activeArrayHeight,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            sensorOrientationDeg = sensorOrientationDeg,
            mirrorHorizontally = mirrorHorizontally,
            confidence = confidence,
        )
    }

    /**
     * Rotate a sensor-frame point into the preview-frame. Returns the rotated
     * coordinates **and** the rotated frame dimensions, so the next scaling
     * step can use the correct denominators (90/270 swap W/H).
     */
    private fun rotateSensorPoint(
        eyeSensor: SensorPoint,
        activeArrayWidth: Int,
        activeArrayHeight: Int,
        rotationDeg: Int,
    ): RotatedPoint {
        val x = eyeSensor.x.toDouble()
        val y = eyeSensor.y.toDouble()
        return when (rotationDeg) {
            0 -> RotatedPoint(x, y, activeArrayWidth, activeArrayHeight)
            90 -> RotatedPoint(activeArrayHeight - y, x, activeArrayHeight, activeArrayWidth)
            180 -> RotatedPoint(activeArrayWidth - x, activeArrayHeight - y, activeArrayWidth, activeArrayHeight)
            270 -> RotatedPoint(y, activeArrayWidth - x, activeArrayHeight, activeArrayWidth)
            else -> error("unreachable - guarded by require(rot % 90 == 0)")
        }
    }

    private data class RotatedPoint(val x: Double, val y: Double, val frameW: Int, val frameH: Int)
}

/** Sensor-frame pixel coordinates (active-array space). */
data class SensorPoint(val x: Int, val y: Int)
