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
     * Prefer [ImageFormat.RAW12] when advertised; else [ImageFormat.RAW_SENSOR].
     * Returns `(format, largestSizeByArea)` or `null` if no RAW output exists.
     */
    fun pickRawOutput(characteristics: CameraCharacteristics): Pair<Int, Size>? {
        val map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return null
        val raw12Sizes = runCatching { map.getOutputSizes(ImageFormat.RAW12)?.toList() }
            .getOrNull()
            .orEmpty()
        if (raw12Sizes.isNotEmpty()) {
            val sz = raw12Sizes.maxByOrNull { it.width.toLong() * it.height } ?: return null
            return ImageFormat.RAW12 to sz
        }
        val rawSensorSizes = runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() }
            .getOrNull()
            .orEmpty()
        if (rawSensorSizes.isNotEmpty()) {
            val sz =
                rawSensorSizes.maxByOrNull { it.width.toLong() * it.height } ?: return null
            return ImageFormat.RAW_SENSOR to sz
        }
        return null
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
}

fun Context.displayRotationCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
