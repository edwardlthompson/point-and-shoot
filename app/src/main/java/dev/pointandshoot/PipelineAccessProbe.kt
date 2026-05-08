package dev.pointandshoot

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.DynamicRangeProfiles
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "PNS.PipelineAccess"

/**
 * Static (characteristics-only) probe: HDR / DCG / ZSL / reprocess / dynamic-range access signals per camera id.
 * Runtime session tests belong in later phases (see PROBE_BUILD_PLAN.md).
 */
fun buildPipelineAccessProbe(cc: CameraCharacteristics): JSONObject =
    JSONObject().apply {
        put("zslAndReprocess", zslAndReprocessJson(cc))
        put("hdrDynamicRange", hdrDynamicRangeJson(cc))
        put("colorSpace", colorSpaceJson(cc))
        put("requestPipeline", requestPipelineJson(cc))
        put("vendorHdrDcgZslKeys", vendorPipelineKeyBucketsJson(cc))
        put("logicalPhysical", logicalPhysicalJson(cc))
        put("rawVsHdrHints", rawVsHdrHintsJson(cc))
    }

private fun zslAndReprocessJson(cc: CameraCharacteristics): JSONObject {
    val caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
    val set = caps.toSet()
    fun has(cap: Int) = cap in set
    return JSONObject().apply {
        put("YUV_REPROCESSING", has(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING))
        put(
            "PRIVATE_REPROCESSING",
            has(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING),
        )
        put("BURST_CAPTURE", has(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE))
        put("RAW", has(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW))
        put(
            "reprocessMaxCaptureStall",
            cc.get(CameraCharacteristics.REPROCESS_MAX_CAPTURE_STALL)?.toString() ?: JSONObject.NULL,
        )
    }
}

private fun hdrDynamicRangeJson(cc: CameraCharacteristics): JSONObject =
    JSONObject().apply {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            put("supported", false)
            put("note", "REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES requires API 33+")
            return@apply
        }
        val drp = runCatching {
            cc.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES) as? DynamicRangeProfiles
        }.getOrNull()
        if (drp == null) {
            put("supported", false)
            put("profiles", JSONArray())
            return@apply
        }
        put("supported", true)
        val arr = JSONArray()
        runCatching { drp.supportedProfiles }.getOrNull()?.forEach { p -> arr.put(p.toString()) }
        put("profiles", arr)
        val rec = runCatching {
            cc.get(CameraCharacteristics.REQUEST_RECOMMENDED_TEN_BIT_DYNAMIC_RANGE_PROFILE)
        }.getOrNull()
        put("recommendedTenBitProfile", rec?.toString() ?: JSONObject.NULL)
    }

@SuppressLint("NewApi")
private fun colorSpaceJson(cc: CameraCharacteristics): JSONObject =
    JSONObject().apply {
        if (Build.VERSION.SDK_INT < 34) {
            put("supported", false)
            put("note", "REQUEST_AVAILABLE_COLOR_SPACE_PROFILES requires API 34+")
            return@apply
        }
        val csp = runCatching {
            cc.get(CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES)
        }.getOrNull()
        if (csp == null) {
            put("supported", false)
            put("profiles", JSONArray())
            return@apply
        }
        put("supported", true)
        val arr = JSONArray()
        val enumErr = runCatching {
            val m =
                csp.javaClass.methods.find { it.name == "getSupportedColorSpaceProfiles" }
                    ?: csp.javaClass.methods.find { it.name == "getExportableColorSpaceProfiles" }
            if (m == null) {
                put("reflectionNote", "No profile enumerator on ${csp.javaClass.name}")
            } else {
                @Suppress("UNCHECKED_CAST")
                val set = m.invoke(csp) as? Iterable<Any?>
                set?.forEach { arr.put(it.toString()) }
            }
        }.exceptionOrNull()
        if (enumErr != null) put("enumerationError", enumErr.message)
        put("profiles", arr)
    }

private fun requestPipelineJson(cc: CameraCharacteristics): JSONObject =
    JSONObject().apply {
        put(
            "partialResultCount",
            cc.get(CameraCharacteristics.REQUEST_PARTIAL_RESULT_COUNT)?.toString() ?: JSONObject.NULL,
        )
        put(
            "pipelineMaxDepth",
            cc.get(CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH)?.toString() ?: JSONObject.NULL,
        )
        put(
            "maxNumOutputProc",
            cc.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC)?.toString() ?: JSONObject.NULL,
        )
        put(
            "maxNumOutputProcStalling",
            cc.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING)?.toString() ?: JSONObject.NULL,
        )
        put(
            "maxNumOutputRaw",
            cc.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW)?.toString() ?: JSONObject.NULL,
        )
        put(
            "sessionConfigurationQueryVersion",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cc.get(CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION)?.toString() ?: JSONObject.NULL
            } else {
                JSONObject.NULL
            },
        )
    }

private fun vendorPipelineKeyBucketsJson(cc: CameraCharacteristics): JSONObject {
    val pool = runCatching {
        buildSet {
            cc.availableCaptureRequestKeys?.forEach { add(it.name) }
            cc.availableSessionKeys?.forEach { add(it.name) }
            cc.availableCaptureResultKeys?.forEach { add(it.name) }
        }
    }.getOrDefault(emptySet())

    fun hits(vararg terms: String): JSONArray {
        val ja = JSONArray()
        pool.filter { k -> terms.any { t -> k.contains(t, ignoreCase = true) } }
            .sorted()
            .forEach { ja.put(it) }
        return ja
    }

    return JSONObject().apply {
        put("hdr_dcg", hits("hdr", "dcg", "mfhdr", "qhdr", "shdr", "xhdr", "tonemap", "expand.dynamic"))
        put("zsl_reprocess", hits("zsl", "reprocess", "offlinehal", "offline.hal", "still", "stall"))
        put("bracket_mfnr", hits("bracket", "bkt", "mfnr", "mfhdr", "grouping", "burst"))
        put("insensor_shdr", hits("insensor", "shdr", "in.sensor", "salinet"))
        put("raw_dng_ideal", hits("idealraw", "raw", "dng", "dcg"))
        put("depth_fusion", hits("depth", "fusion", "logical", "multicam", "spatial"))
    }
}

/** Static hints only; runtime RAW+HDR exclusivity belongs in a dedicated session matrix (Phase 6). */
@SuppressLint("NewApi")
private fun rawVsHdrHintsJson(cc: CameraCharacteristics): JSONObject =
    JSONObject().apply {
        val caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        put(
            "rawCapabilityAdvertised",
            caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val drp = runCatching {
                cc.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES) as? DynamicRangeProfiles
            }.getOrNull()
            val n = runCatching { drp?.supportedProfiles?.size ?: 0 }.getOrDefault(0)
            put("dynamicRangeProfileCount", n)
        } else {
            put("dynamicRangeProfileCount", JSONObject.NULL)
            put("note", "dynamic range profiles require API 33+")
        }
        put("analysisNote", "Compare with hdr_dcg_session JSON for runtime isSessionConfigurationSupported results.")
    }

private fun logicalPhysicalJson(cc: CameraCharacteristics): JSONObject {
    val caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
    val logical = caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA }
    val physicalIds = runCatching { cc.physicalCameraIds.toList().sorted() }.getOrDefault(emptyList())
    val syncType = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cc.get(CameraCharacteristics.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE)
        } else {
            null
        }
    }.getOrNull()

    val syncName = when (syncType) {
        null -> JSONObject.NULL
        CameraMetadata.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE_APPROXIMATE -> "APPROXIMATE"
        CameraMetadata.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE_CALIBRATED -> "CALIBRATED"
        else -> syncType.toString()
    }

    return JSONObject().apply {
        put("logicalMultiCameraCapability", logical)
        put("physicalCameraIds", JSONArray(physicalIds))
        put("sensorSyncType", syncName)
    }
}

/** Log a one-line summary for logcat during long probes. */
fun logPipelineAccessSummary(cameraId: String, probe: JSONObject) {
    val zsl = probe.optJSONObject("zslAndReprocess")
    val hdr = probe.optJSONObject("hdrDynamicRange")
    val nDr = hdr?.optJSONArray("profiles")?.length() ?: 0
    val vend = probe.optJSONObject("vendorHdrDcgZslKeys")
    val nHdrKeys = vend?.optJSONArray("hdr_dcg")?.length() ?: 0
    val nZslKeys = vend?.optJSONArray("zsl_reprocess")?.length() ?: 0
    Log.i(
        TAG,
        "cameraId=$cameraId yuvReproc=${zsl?.optBoolean("YUV_REPROCESSING")} drProfiles=$nDr vendorHdrKeys=$nHdrKeys vendorZslKeys=$nZslKeys",
    )
}
