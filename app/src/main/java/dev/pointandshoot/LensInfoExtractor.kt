package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import org.json.JSONObject

/**
 * Android-side bridge from `CameraCharacteristics` to [LensInfoSummary] +
 * its JSON representation. Lives in the `main` source set (camera2 deps) so
 * the pure-data side ([LensInfoSummary]) can stay JVM-testable.
 *
 * Defensive throughout: every individual key read is wrapped in
 * `runCatching { ... }.getOrNull()` so a single missing or vendor-broken
 * key never aborts the rest of the lens-info extraction. Missing fields
 * round-trip as the JSON adapter's documented "absent" tokens (empty list,
 * null, or 0 diopters) per the [LensInfoSummary] contract.
 */
object LensInfoExtractor {

    /**
     * Build a [LensInfoSummary] from [cc] for [cameraId]. Never throws -
     * vendor-key misbehavior is captured as "missing" fields.
     */
    fun extract(cameraId: String, cc: CameraCharacteristics): LensInfoSummary {
        val lensFacing = runCatching { cc.get(CameraCharacteristics.LENS_FACING) }
            .getOrNull()
            ?.let { lensFacingToken(it) }

        val apertures = runCatching {
            cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        }.getOrNull()?.toList().orEmpty()

        val oisModes = runCatching {
            cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        }.getOrNull()?.map { oisModeToken(it) }.orEmpty()

        val minFocus = runCatching {
            cc.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        }.getOrDefault(0f)

        val hyperfocal = runCatching {
            cc.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE) ?: 0f
        }.getOrDefault(0f)

        val focalLengths = runCatching {
            cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        }.getOrNull()?.toList().orEmpty()

        val physSize = runCatching {
            cc.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let { s ->
                SensorPhysicalSize(widthMm = s.width, heightMm = s.height)
            }
        }.getOrNull()

        val activeArray = runCatching {
            cc.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { r ->
                SensorActiveArray(widthPx = r.width(), heightPx = r.height())
            }
        }.getOrNull()

        val orientation = runCatching {
            cc.get(CameraCharacteristics.SENSOR_ORIENTATION)
        }.getOrNull()

        return LensInfoSummary(
            cameraId = cameraId,
            lensFacing = lensFacing,
            availableApertures = apertures,
            opticalStabilizationModes = oisModes,
            minimumFocusDistanceDiopters = minFocus,
            hyperfocalDistanceDiopters = hyperfocal,
            availableFocalLengthsMm = focalLengths,
            sensorPhysicalSizeMm = physSize,
            sensorActiveArrayPx = activeArray,
            sensorOrientationDegrees = orientation,
        )
    }

    /**
     * Convenience: extract + serialize in one call. Returns the JSON
     * shape [LensInfoSummaryJson.encode] produces.
     */
    fun extractToJson(cameraId: String, cc: CameraCharacteristics): JSONObject =
        LensInfoSummaryJson.encode(extract(cameraId, cc))

    private fun lensFacingToken(facing: Int): String = when (facing) {
        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN($facing)"
    }

    private fun oisModeToken(mode: Int): String = when (mode) {
        CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF -> "OFF"
        CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON -> "ON"
        else -> "VENDOR($mode)"
    }
}
