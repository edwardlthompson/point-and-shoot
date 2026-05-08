package dev.pointandshoot

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure-data summary of per-camera lens + sensor info per BUILD_PLAN §3
 * ("Hardware-to-software mapping") and the DODGE_PROFILE.md "Next probe
 * deltas needed" block. Populated by [LensInfoExtractor] from
 * `CameraCharacteristics` and serialized into the deep-caps JSON under
 * the `lensInfo` key per camera.
 *
 * Inference rules (engine-facing):
 *   * macro: `minimumFocusDistance` >= [MACRO_MIN_DIOPTERS_THRESHOLD]
 *           (1/m, i.e. closer-focusing lenses report a higher diopter value)
 *   * OIS:   `availableOpticalStabilization` contains a non-`OFF` mode
 *
 * The data carrier and the inference are intentionally pure-Kotlin so they
 * round-trip on the JVM unit-test classpath without touching the camera2
 * stack. The Android side ([LensInfoExtractor]) bridges to the framework
 * by emitting the same JSON shape this class can decode.
 */
data class LensInfoSummary(
    val cameraId: String,
    /** `LENS_FACING` token (`FRONT` / `BACK` / `EXTERNAL`) or null when missing. */
    val lensFacing: String?,
    /** `LENS_INFO_AVAILABLE_APERTURES` (f-number floats); empty when unsupported. */
    val availableApertures: List<Float>,
    /**
     * `LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION` mode tokens
     * (`OFF` / `ON` / `LENS_OPTICAL_STABILIZATION_MODE_*`). Empty when the
     * camera does not advertise optical stabilization at all.
     */
    val opticalStabilizationModes: List<String>,
    /**
     * `LENS_INFO_MINIMUM_FOCUS_DISTANCE` in diopters (1/m). `0f` means
     * fixed-focus or unknown; higher values mean closer-focusing.
     */
    val minimumFocusDistanceDiopters: Float,
    /** `LENS_INFO_HYPERFOCAL_DISTANCE` in diopters (1/m); 0f when unknown. */
    val hyperfocalDistanceDiopters: Float,
    /** `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` (mm). Empty when unsupported. */
    val availableFocalLengthsMm: List<Float>,
    /** Sensor physical size in mm, or null when unsupported. */
    val sensorPhysicalSizeMm: SensorPhysicalSize?,
    /** Active array size in pixels, or null when unsupported. */
    val sensorActiveArrayPx: SensorActiveArray?,
    /** `SENSOR_ORIENTATION` clockwise rotation degrees (0/90/180/270) or null. */
    val sensorOrientationDegrees: Int?,
) {
    /**
     * Inferred-macro flag per the [MACRO_MIN_DIOPTERS_THRESHOLD]: true when
     * the lens advertises a minimum focus distance closer than ~10 cm.
     */
    val isMacroCapable: Boolean
        get() = minimumFocusDistanceDiopters >= MACRO_MIN_DIOPTERS_THRESHOLD

    /** True when at least one OIS mode advertises a non-`OFF` token. */
    val hasOpticalStabilization: Boolean
        get() = opticalStabilizationModes.any { it != "OFF" && it.isNotBlank() }

    /**
     * Convenience: closest focusing distance in metres, or null when the
     * lens reports fixed focus (`minimumFocusDistanceDiopters == 0f`).
     */
    val minimumFocusDistanceMeters: Float?
        get() = if (minimumFocusDistanceDiopters > 0f) 1f / minimumFocusDistanceDiopters else null

    /** Compact human-readable summary; useful for the diagnostics dump. */
    fun describe(): String = buildString {
        append("cameraId=").append(cameraId)
        if (lensFacing != null) append(" facing=").append(lensFacing)
        if (availableApertures.isNotEmpty()) {
            append(" apertures=f/")
            append(availableApertures.joinToString(",f/") { "%.2f".format(it) })
        }
        if (availableFocalLengthsMm.isNotEmpty()) {
            append(" focal=")
            append(availableFocalLengthsMm.joinToString(",") { "${"%.2f".format(it)}mm" })
        }
        if (minimumFocusDistanceMeters != null) {
            append(" minFocus=")
            append("%.3fm".format(minimumFocusDistanceMeters))
        }
        if (opticalStabilizationModes.isNotEmpty()) {
            append(" ois=")
            append(opticalStabilizationModes.joinToString(","))
        }
        if (sensorPhysicalSizeMm != null) {
            append(" sensor=")
            append("%.2fx%.2fmm".format(sensorPhysicalSizeMm.widthMm, sensorPhysicalSizeMm.heightMm))
        }
        if (sensorOrientationDegrees != null) {
            append(" rot=").append(sensorOrientationDegrees).append("°")
        }
    }

    companion object {
        /**
         * Macro inference threshold (1/m). 15 diopters = ~6.7 cm minimum
         * focus distance: tight enough to exclude a typical phone main wide
         * (the OnePlus 13 LYT-808 reports exactly 10 diopters / 10 cm, which
         * is close-focus but NOT super-macro), and loose enough to include
         * a true macro UW (the OnePlus 13 S5KJN5 reports 25 diopters / 4 cm).
         * Validated against `hfr-runs/deep_caps_round11.json` per
         * `PROBE_BUILD_PLAN.md` §5.
         */
        const val MACRO_MIN_DIOPTERS_THRESHOLD: Float = 15f
    }
}

/**
 * Sensor physical size in mm. Cross-references against `android.sensor.info.physicalSize`
 * (`SizeF` in metres in the framework; we store mm to match the rest of the
 * lens metadata).
 */
data class SensorPhysicalSize(val widthMm: Float, val heightMm: Float) {
    init {
        require(widthMm > 0f && heightMm > 0f) {
            "sensor physical size must be positive (was ${widthMm}x${heightMm} mm)"
        }
    }
}

/**
 * Sensor active array rectangle in pixels (not the full array - the readout
 * region). Mirrors `SENSOR_INFO_ACTIVE_ARRAY_SIZE`.
 */
data class SensorActiveArray(val widthPx: Int, val heightPx: Int) {
    init {
        require(widthPx > 0 && heightPx > 0) {
            "sensor active array must be positive (was ${widthPx}x${heightPx} px)"
        }
    }
}

/**
 * JSON adapter for [LensInfoSummary]. The `version: 1` schema is mirrored
 * by [LensInfoExtractor] on the Android side so the round-trip works for
 * tests that load a real `deep_caps_*.json` blob.
 */
object LensInfoSummaryJson {
    const val SCHEMA_VERSION: Int = 1
    const val KEY_LENS_INFO: String = "lensInfo"

    /** Build the JSON shape for a [LensInfoSummary]. */
    fun encode(summary: LensInfoSummary): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("cameraId", summary.cameraId)
        if (summary.lensFacing != null) put("lensFacing", summary.lensFacing)
        put("availableApertures", JSONArray(summary.availableApertures.map { it.toDouble() }))
        put("opticalStabilizationModes", JSONArray(summary.opticalStabilizationModes))
        put("minimumFocusDistanceDiopters", summary.minimumFocusDistanceDiopters.toDouble())
        put("hyperfocalDistanceDiopters", summary.hyperfocalDistanceDiopters.toDouble())
        put("availableFocalLengthsMm", JSONArray(summary.availableFocalLengthsMm.map { it.toDouble() }))
        if (summary.sensorPhysicalSizeMm != null) {
            put("sensorPhysicalSizeMm", JSONObject().apply {
                put("widthMm", summary.sensorPhysicalSizeMm.widthMm.toDouble())
                put("heightMm", summary.sensorPhysicalSizeMm.heightMm.toDouble())
            })
        }
        if (summary.sensorActiveArrayPx != null) {
            put("sensorActiveArrayPx", JSONObject().apply {
                put("widthPx", summary.sensorActiveArrayPx.widthPx)
                put("heightPx", summary.sensorActiveArrayPx.heightPx)
            })
        }
        if (summary.sensorOrientationDegrees != null) {
            put("sensorOrientationDegrees", summary.sensorOrientationDegrees)
        }
    }

    /** Decode the JSON shape produced by [encode] back into a [LensInfoSummary]. */
    fun decode(obj: JSONObject): LensInfoSummary {
        val schema = obj.optInt("schemaVersion", 0)
        require(schema == SCHEMA_VERSION) {
            "Unsupported lensInfo schemaVersion=$schema (expected $SCHEMA_VERSION)"
        }
        val cameraId = obj.optString("cameraId", "").also {
            require(it.isNotBlank()) { "lensInfo.cameraId is required" }
        }
        return LensInfoSummary(
            cameraId = cameraId,
            lensFacing = obj.takeIfHas("lensFacing")?.optString("lensFacing"),
            availableApertures = obj.optFloatList("availableApertures"),
            opticalStabilizationModes = obj.optStringList("opticalStabilizationModes"),
            minimumFocusDistanceDiopters = obj.optDouble("minimumFocusDistanceDiopters", 0.0).toFloat(),
            hyperfocalDistanceDiopters = obj.optDouble("hyperfocalDistanceDiopters", 0.0).toFloat(),
            availableFocalLengthsMm = obj.optFloatList("availableFocalLengthsMm"),
            sensorPhysicalSizeMm = obj.optJSONObject("sensorPhysicalSizeMm")?.let { sizeObj ->
                SensorPhysicalSize(
                    widthMm = sizeObj.optDouble("widthMm", 0.0).toFloat(),
                    heightMm = sizeObj.optDouble("heightMm", 0.0).toFloat(),
                )
            },
            sensorActiveArrayPx = obj.optJSONObject("sensorActiveArrayPx")?.let { arrObj ->
                SensorActiveArray(
                    widthPx = arrObj.optInt("widthPx", 0),
                    heightPx = arrObj.optInt("heightPx", 0),
                )
            },
            sensorOrientationDegrees = if (obj.has("sensorOrientationDegrees"))
                obj.optInt("sensorOrientationDegrees", 0) else null,
        )
    }

    private fun JSONObject.takeIfHas(key: String): JSONObject? = if (has(key)) this else null

    private fun JSONObject.optFloatList(key: String): List<Float> {
        val arr = optJSONArray(key) ?: return emptyList()
        val out = ArrayList<Float>(arr.length())
        for (i in 0 until arr.length()) {
            out.add(arr.optDouble(i, 0.0).toFloat())
        }
        return out
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotBlank()) out.add(s)
        }
        return out
    }
}
