package dev.pointandshoot

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Size
import android.view.Surface
import android.view.WindowManager

/**
 * RAW still helpers for Phase 1 Camera2 capture ([BUILD_PLAN.md] §4).
 */
object RawCaptureSupport {

    /**
     * Prefer [ImageFormat.RAW12], then [ImageFormat.RAW10], then [ImageFormat.RAW_SENSOR]
     * (matches [BUILD_PLAN.md] Milestone **10.1** `rawPickEffective` ordering).
     * Returns `(format, largestSizeByArea)` or `null` if no RAW output exists.
     */
    fun pickRawOutput(characteristics: CameraCharacteristics): Pair<Int, Size>? {
        val map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return null
        return pickRawOutputFromMaps(
            raw12 = runCatching { map.getOutputSizes(ImageFormat.RAW12)?.toList() }.getOrNull(),
            raw10 = runCatching { map.getOutputSizes(ImageFormat.RAW10)?.toList() }.getOrNull(),
            rawSensor = runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() }.getOrNull(),
        )
    }

    /**
     * Pure RAW pick used by [pickRawOutput] and unit tests (no [CameraCharacteristics] required).
     */
    internal fun pickRawOutputFromMaps(
        raw12: List<Size>?,
        raw10: List<Size>?,
        rawSensor: List<Size>?,
    ): Pair<Int, Size>? {
        fun largest(sizes: List<Size>?): Size? =
            sizes?.takeIf { it.isNotEmpty() }?.maxByOrNull { it.width.toLong() * it.height }
        largest(raw12)?.let { return ImageFormat.RAW12 to it }
        largest(raw10)?.let { return ImageFormat.RAW10 to it }
        largest(rawSensor)?.let { return ImageFormat.RAW_SENSOR to it }
        return null
    }

    /** Label for probe `rawPickEffective=` lines (Milestone **10.1**). */
    fun rawPickEffectiveLabel(format: Int?): String =
        when (format) {
            ImageFormat.RAW12 -> "RAW12"
            ImageFormat.RAW10 -> "RAW10"
            ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
            else -> "null"
        }

    /**
     * Largest JPEG still size from [StreamConfigurationMap], or `null` if the device
     * does not advertise [ImageFormat.JPEG] outputs.
     */
    fun pickLargestJpegSize(map: StreamConfigurationMap): Size? {
        val sizes =
            runCatching { map.getOutputSizes(ImageFormat.JPEG)?.toList() }.getOrNull().orEmpty()
        if (sizes.isEmpty()) return null
        return sizes.maxByOrNull { it.width.toLong() * it.height }
    }

    /**
     * Clockwise rotation (0, 90, 180, 270) for [Dng12Saver] / EXIF orientation,
     * from sensor characteristics + [Surface] rotation constant.
     */
    fun orientationClockwiseDegForDng(
        characteristics: CameraCharacteristics,
        surfaceRotation: Int,
    ): Int {
        val sensorOrientation =
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val deviceOrientationDegrees =
            when (surfaceRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        val degrees =
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation + deviceOrientationDegrees + 360) % 360
            } else {
                (sensorOrientation - deviceOrientationDegrees + 360) % 360
            }
        return degrees
    }

    /**
     * Maps [snapPhysicalCardinal] / [DeviceUiRotationState.physicalCardinalSnapDegrees] (0 =
     * natural portrait up, 90 = …, 270 = …) to [Surface.ROTATION_0]…[Surface.ROTATION_270] in
     * the same numeric space as [android.view.Display.getRotation], so it can be passed where
     * [orientationClockwiseDegForDng] expects a display rotation constant.
     *
     * See [DeviceUiRotationTest]: e.g. physical 270 (natural-RIGHT up, `ax ≈ +g`) matches
     * `Surface.ROTATION_90` on a portrait-natural phone.
     */
    fun surfaceRotationFromPhysicalCardinalSnap(physicalCardinalDeg: Int): Int {
        val d = ((physicalCardinalDeg % 360) + 360) % 360
        return when (d) {
            0 -> Surface.ROTATION_0
            90 -> Surface.ROTATION_270
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }
    }
}

fun Context.displayRotationCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
