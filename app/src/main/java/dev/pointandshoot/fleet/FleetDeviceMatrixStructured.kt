package dev.pointandshoot.fleet

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import dev.pointandshoot.CameraXExtensionProbe
import dev.pointandshoot.LensInfoSummaryJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured per-camera blocks for [FleetDeviceMatrix] full tier (Milestone **16.1**).
 */
object FleetDeviceMatrixStructured {

    data class FeatureGate(
        val advertised: Boolean,
        val sessionOk: Boolean,
        val appEnabled: Boolean,
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                put("advertised", advertised)
                put("sessionOk", sessionOk)
                put("appEnabled", appEnabled)
            }
    }

    fun buildCameraEntry(
        shallow: JSONObject,
        deepCam: JSONObject?,
        sessionCam: JSONObject?,
        fleetProfile: FleetCameraProfile?,
        cc: CameraCharacteristics?,
    ): JSONObject {
        val cameraId = shallow.optString("cameraId")
        val merged = JSONObject(shallow.toString())
        val capsArr = deepCam?.optJSONArray("availableCapabilities")
        val capsInts = jsonIntArray(capsArr)
        val normalized = capabilitiesNormalized(capsInts, cc)
        merged.put("capabilitiesNormalized", normalized)
        merged.put("hardwareLevel", hardwareLevelLabel(deepCam, cc))
        merged.put("timestampSource", timestampSourceLabel(cc))
        merged.put("tenBitDynamicRange", tenBitDynamicRange(deepCam))
        merged.put("rawReadiness", rawReadiness(shallow, deepCam, cc))
        merged.put("featureGates", featureGates(shallow, deepCam, sessionCam, fleetProfile))
        merged.put("fleetPolicy", fleetProfile?.toJson() ?: JSONObject.NULL)
        merged.put("performanceProbes", performanceProbes(sessionCam))
        deepCam?.optJSONArray("faceDetectModes")?.let { merged.put("faceDetectModes", it) }
        if (deepCam?.has(LensInfoSummaryJson.KEY_LENS_INFO) == true) {
            merged.put(LensInfoSummaryJson.KEY_LENS_INFO, deepCam.optJSONObject(LensInfoSummaryJson.KEY_LENS_INFO))
        }
        return merged
    }

    fun cameraXSlice(): JSONObject {
        val matrix = CameraXExtensionProbe.cached
        return JSONObject().apply {
            put("informational", true)
            put("note", "CameraX extensions are informational; Camera2 remains the capture path.")
            if (matrix == null) {
                put("probeComplete", false)
                put("availableByCamera", JSONObject())
            } else {
                put("probeComplete", true)
                put("hasAny", matrix.hasAny())
                put("availableByCamera", cameraXByCameraJson(matrix))
            }
        }
    }

    private fun cameraXByCameraJson(matrix: CameraXExtensionProbe.ExtensionMatrix): JSONObject {
        val out = JSONObject()
        for ((id, modes) in matrix.availableByCamera) {
            out.put(
                id,
                JSONArray().apply {
                    modes.forEach { mode ->
                        put(
                            JSONObject().apply {
                                put("mode", mode)
                                put("label", cameraXModeLabel(mode))
                            },
                        )
                    }
                },
            )
        }
        return out
    }

    private fun cameraXModeLabel(mode: Int): String =
        when (mode) {
            androidx.camera.extensions.ExtensionMode.NIGHT -> "NIGHT"
            androidx.camera.extensions.ExtensionMode.BOKEH -> "BOKEH"
            androidx.camera.extensions.ExtensionMode.HDR -> "HDR"
            androidx.camera.extensions.ExtensionMode.FACE_RETOUCH -> "FACE_RETOUCH"
            androidx.camera.extensions.ExtensionMode.AUTO -> "AUTO"
            else -> mode.toString()
        }

    fun capabilitiesNormalized(caps: IntArray?, cc: CameraCharacteristics?): JSONArray {
        val names = linkedSetOf<String>()
        caps?.forEach { c ->
            when (c) {
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE ->
                    names.add("BACKWARD_COMPATIBLE")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR ->
                    names.add("MANUAL_SENSOR")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING ->
                    names.add("MANUAL_POST_PROCESSING")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW -> names.add("RAW")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING ->
                    names.add("PRIVATE_REPROCESSING")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS ->
                    names.add("READ_SENSOR_SETTINGS")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE ->
                    names.add("BURST_CAPTURE")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING ->
                    names.add("YUV_REPROCESSING")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT ->
                    names.add("DEPTH_OUTPUT")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO ->
                    names.add("CONSTRAINED_HIGH_SPEED_VIDEO")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA ->
                    names.add("LOGICAL_MULTI_CAMERA")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT ->
                    names.add("DYNAMIC_RANGE_TEN_BIT")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE ->
                    names.add("STREAM_USE_CASE")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES ->
                    names.add("COLOR_SPACE_PROFILES")
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR ->
                    names.add("ULTRA_HIGH_RESOLUTION_SENSOR")
                else -> names.add("CAP_$c")
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            cc?.get(CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES)?.let { useCases ->
                if (useCases.isNotEmpty()) names.add("STREAM_USE_CASES_ADVERTISED")
            }
        }
        return JSONArray(names.sorted())
    }

    private fun hardwareLevelLabel(deepCam: JSONObject?, cc: CameraCharacteristics?): String {
        val level =
            deepCam?.optInt("hardwareLevel", -1)
                ?: cc?.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                ?: -1
        return when (level) {
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN_$level"
        }
    }

    private fun timestampSourceLabel(cc: CameraCharacteristics?): String {
        val src = cc?.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ?: return "UNKNOWN"
        return when (src) {
            CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME -> "REALTIME"
            CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> "UNKNOWN"
            else -> "OTHER_$src"
        }
    }

    private fun tenBitDynamicRange(deepCam: JSONObject?): JSONObject {
        val pipeline = deepCam?.optJSONObject("pipelineAccess")
        val hdr = pipeline?.optJSONObject("hdrDynamicRange")
        return JSONObject().apply {
            put("supported", hdr?.optBoolean("supported", false) ?: false)
            put("profiles", hdr?.optJSONArray("profiles") ?: JSONArray())
        }
    }

    fun rawReadiness(
        shallow: JSONObject,
        deepCam: JSONObject?,
        cc: CameraCharacteristics?,
    ): JSONObject {
        val formats = JSONArray()
        if (!shallow.isNull("largestRawSensor")) formats.put("RAW_SENSOR")
        if (!shallow.isNull("largestRaw10")) formats.put("RAW10")
        if (!shallow.isNull("largestRaw12")) formats.put("RAW12")
        val caps = deepCam?.optJSONArray("availableCapabilities")
        val hasRawCap =
            jsonIntArray(caps).contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        return JSONObject().apply {
            put("formats", formats)
            put("rawPickEffective", shallow.opt("rawPickEffective"))
            put("rawPickSize", shallow.opt("rawPickSize"))
            put("rawCapabilityAdvertised", hasRawCap)
            put("maxNumOutputRaw", cc?.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW) ?: JSONObject.NULL)
            put(
                "matricesPresent",
                JSONObject().apply {
                    put("colorMatrix1", cc?.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1) != null)
                    put("colorMatrix2", cc?.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2) != null)
                    put("forwardMatrix1", cc?.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1) != null)
                    put("forwardMatrix2", cc?.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2) != null)
                },
            )
            put(
                "cfaPattern",
                cfaPatternLabel(cc?.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)),
            )
        }
    }

    private fun cfaPatternLabel(arrangement: Int?): String =
        when (arrangement) {
            CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> "RGGB"
            CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> "GRBG"
            CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> "GBRG"
            CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> "BGGR"
            else -> arrangement?.let { "OTHER_$it" } ?: "UNKNOWN"
        }

    fun featureGates(
        shallow: JSONObject,
        deepCam: JSONObject?,
        sessionCam: JSONObject?,
        fleetProfile: FleetCameraProfile?,
    ): JSONObject {
        val caps = jsonIntArray(deepCam?.optJSONArray("availableCapabilities"))
        val rawAdvertised = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        val rawSessionOk =
            rawAdvertised &&
                SessionMatrixProbeCore.sessionTestSupported(sessionCam, "regular_1920x1080")
        val rawAppEnabled = fleetProfile?.rawFormatsAdvertised?.isNotEmpty() == true && rawSessionOk

        val hfr1080 = shallow.optInt("hfrMaxFpsAt1080", 0)
        val hfrAdvertised = hfr1080 > 0
        val hfrSessionOk = SessionMatrixProbeCore.highSpeedSessionOk(sessionCam)
        val hfrAppEnabled = hfrAdvertised && hfrSessionOk

        val faceModes = jsonIntArray(deepCam?.optJSONArray("faceDetectModes"))
        val faceAdvertised = faceModes.any { it != CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF }
        val faceSessionOk = faceAdvertised
        val faceAppEnabled = faceModes.contains(CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL) ||
            faceModes.contains(CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE)

        val pipeline = deepCam?.optJSONObject("pipelineAccess")
        val zsl = pipeline?.optJSONObject("zslAndReprocess")
        val dcgAdvertised =
            zsl?.optBoolean("YUV_REPROCESSING", false) == true ||
                pipeline?.optJSONObject("hdrDynamicRange")?.optBoolean("supported", false) == true
        val dcgSessionOk = fleetProfile?.supportsDcgSession == true || dcgAdvertised
        val dcgAppEnabled = fleetProfile?.supportsDcgSession == true

        return JSONObject().apply {
            put("raw", FeatureGate(rawAdvertised, rawSessionOk, rawAppEnabled).toJson())
            put("hfr", FeatureGate(hfrAdvertised, hfrSessionOk, hfrAppEnabled).toJson())
            put("face", FeatureGate(faceAdvertised, faceSessionOk, faceAppEnabled).toJson())
            put("dcgZsl", FeatureGate(dcgAdvertised, dcgSessionOk, dcgAppEnabled).toJson())
        }
    }

    fun performanceProbes(sessionCam: JSONObject?): JSONObject =
        JSONObject().apply {
            val openMs = sessionCam?.optLong("openCameraMs", -1L)?.takeIf { it >= 0 }
            put("openCameraMs", openMs ?: JSONObject.NULL)
            put("jpeg1080CaptureMs", JSONObject.NULL)
            put("note", "jpeg1080CaptureMs deferred — informational only in full tier")
        }

    fun enrichDeepCamWithFaceModes(deepCam: JSONObject, cc: CameraCharacteristics) {
        val modes = cc.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) ?: intArrayOf()
        deepCam.put("faceDetectModes", JSONArray().apply { modes.forEach { put(it) } })
    }

    internal fun jsonIntArray(arr: JSONArray?): IntArray {
        if (arr == null) return intArrayOf()
        return IntArray(arr.length()) { i -> arr.getInt(i) }
    }
}
