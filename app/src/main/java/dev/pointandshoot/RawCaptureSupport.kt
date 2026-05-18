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
 * Host / ADB override for which advertised RAW stream to attach in preview (see
 * `pns_preview_raw_stream` in `CameraCapabilitiesProbe`).
 *
 * **Default** (in-tree): **RAW12 → RAW_SENSOR → RAW10** when all three are advertised — **USB-verified** scripted DNG
 * on **CPH2655-class** stacks (RAW10-first Milestone **10.1** order produced **ImageFormat 37** buffers that
 * **`DngCreator.writeImage`** rejected). Prefer **`pns_preview_raw_stream`** / **[Raw10Only]** for HAL matrix work.
 * Other values exist for OEM matrix testing where the advertised format does not deliver buffers.
 */
enum class RawStreamPreference {
    /** RAW12 → RAW_SENSOR → RAW10 (fleet default; probe `rawPickEffective=` matches this). */
    Default,

    /** RAW_SENSOR → RAW12 → RAW10 — tests HALs that advertise RAW12 but only fill RAW_SENSOR. */
    RawSensorFirst,

    /** Largest RAW12 only; `null` if unsupported. */
    Raw12Only,

    /** Largest RAW_SENSOR only; `null` if unsupported. */
    RawSensorOnly,

    /** Largest RAW10 only; `null` if unsupported (often incompatible with [DngCreator]). */
    Raw10Only,
}

/**
 * RAW still helpers for Phase 1 Camera2 capture ([BUILD_PLAN.md] §4).
 */
object RawCaptureSupport {

    /** Same as [pickRawOutput] with [RawStreamPreference.Default] (RAW12 → RAW_SENSOR → RAW10). */
    fun pickRawOutput(characteristics: CameraCharacteristics): Pair<Int, Size>? =
        pickRawOutput(characteristics, RawStreamPreference.Default)

    /**
     * Same as [pickRawOutput] but honors [preference] for matrix / OEM diagnostics
     * (`pns_preview_raw_stream` ADB extra).
     */
    fun pickRawOutput(
        characteristics: CameraCharacteristics,
        preference: RawStreamPreference,
    ): Pair<Int, Size>? {
        val map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return null
        return pickRawOutputFromMaps(
            raw12 = runCatching { map.getOutputSizes(ImageFormat.RAW12)?.toList() }.getOrNull(),
            raw10 = runCatching { map.getOutputSizes(ImageFormat.RAW10)?.toList() }.getOrNull(),
            rawSensor = runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() }.getOrNull(),
            preference = preference,
        )
    }

    /**
     * Pure RAW pick used by [pickRawOutput] and unit tests (no [CameraCharacteristics] required).
     */
    internal fun pickRawOutputFromMaps(
        raw12: List<Size>?,
        raw10: List<Size>?,
        rawSensor: List<Size>?,
        preference: RawStreamPreference = RawStreamPreference.Default,
    ): Pair<Int, Size>? {
        fun largest(sizes: List<Size>?): Size? =
            sizes?.takeIf { it.isNotEmpty() }?.maxByOrNull { it.width.toLong() * it.height }
        return when (preference) {
            RawStreamPreference.Default ->
                largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
            RawStreamPreference.RawSensorFirst ->
                largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
                    ?: largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
            RawStreamPreference.Raw12Only -> largest(raw12)?.let { ImageFormat.RAW12 to it }
            RawStreamPreference.RawSensorOnly -> largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
            RawStreamPreference.Raw10Only -> largest(raw10)?.let { ImageFormat.RAW10 to it }
        }
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
