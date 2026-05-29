package dev.pointandshoot.fleet

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Log
import android.util.Size
import android.graphics.ImageFormat
import android.media.MediaRecorder
import android.view.SurfaceHolder
import android.graphics.SurfaceTexture
import dev.pointandshoot.LensInfoExtractor
import dev.pointandshoot.LensInfoSummaryJson
import dev.pointandshoot.buildPipelineAccessProbe
import dev.pointandshoot.logPipelineAccessSummary
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Characteristics-only deep caps probe (Milestone **16.1**).
 *
 * Extracted from [dev.pointandshoot.DeepCapsProbeScreen] so [FleetDeviceMatrixBuilder]
 * and the hub UI share one implementation.
 */
object DeepCapsProbeCore {
    private const val TAG = "PNS.DeepCaps"

    suspend fun probe(
        appContext: Context,
        onProgress: suspend (String) -> Unit = {},
    ): JSONObject {
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        onProgress("Found ${cameraIds.size} camera id(s): ${cameraIds.joinToString(",")}")

        val root =
            JSONObject().apply {
                put("generatedAt", Instant.now().toString())
                put("device", deviceBlock())
            }

        val cams = JSONArray()
        for ((idx, cameraId) in cameraIds.withIndex()) {
            onProgress("Camera id=$cameraId (${idx + 1}/${cameraIds.size}): getCameraCharacteristics…")
            val camObj = JSONObject().put("cameraId", cameraId)
            val cc = runCatching { cm.getCameraCharacteristics(cameraId) }.getOrNull()
            if (cc == null) {
                onProgress(" id=$cameraId: FAILED characteristics")
                camObj.put("error", "getCameraCharacteristics_failed")
                cams.put(camObj)
                continue
            }

            onProgress(" id=$cameraId: stream map, AE ranges, key lists…")
            camObj.put("lensFacing", cc.get(CameraCharacteristics.LENS_FACING) ?: JSONObject.NULL)
            camObj.put("hardwareLevel", cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: JSONObject.NULL)
            camObj.put(
                "physicalCameraIds",
                JSONArray(runCatching { cc.physicalCameraIds.toList() }.getOrDefault(emptyList())),
            )

            onProgress(" id=$cameraId: lens info…")
            val lensInfoJson =
                runCatching { LensInfoExtractor.extractToJson(cameraId, cc) }.fold(
                    onSuccess = { it },
                    onFailure = { e ->
                        Log.e(TAG, "lensInfo failed for camera $cameraId", e)
                        JSONObject().apply {
                            put("schemaVersion", LensInfoSummaryJson.SCHEMA_VERSION)
                            put("cameraId", cameraId)
                            put("error", "${e::class.java.simpleName}: ${e.message}")
                        }
                    },
                )
            camObj.put(LensInfoSummaryJson.KEY_LENS_INFO, lensInfoJson)

            camObj.put(
                "availableCapabilities",
                intArrayToJson(cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)),
            )
            camObj.put(
                "aeTargetFpsRanges",
                rangeArrayToJson(cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)),
            )
            camObj.put("characteristicKeys", JSONArray(cc.keys.map { it.name }.sorted()))
            camObj.put(
                "requestKeys",
                JSONArray(
                    runCatching { cc.availableCaptureRequestKeys?.map { it.name } }
                        .getOrNull()
                        .orEmpty()
                        .sorted(),
                ),
            )
            camObj.put(
                "resultKeys",
                JSONArray(
                    runCatching { cc.availableCaptureResultKeys?.map { it.name } }
                        .getOrNull()
                        .orEmpty()
                        .sorted(),
                ),
            )
            camObj.put(
                "sessionKeys",
                JSONArray(
                    runCatching { cc.availableSessionKeys?.map { it.name } }
                        .getOrNull()
                        .orEmpty()
                        .sorted(),
                ),
            )

            val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val streamJson = streamConfigToJson(map)
            streamJson.put(
                "aeTargetFpsRanges",
                rangeArrayToJson(cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)),
            )
            camObj.put("streamConfigurationMap", streamJson)
            val faceModes = cc.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) ?: intArrayOf()
            camObj.put("faceDetectModes", JSONArray().apply { faceModes.forEach { put(it) } })

            onProgress(" id=$cameraId: pipelineAccess…")
            val pipelineAccess =
                runCatching { buildPipelineAccessProbe(cc) }.fold(
                    onSuccess = { it },
                    onFailure = { e ->
                        Log.e(TAG, "pipelineAccess failed for camera $cameraId", e)
                        JSONObject().apply {
                            put("error", "${e::class.java.simpleName}: ${e.message}")
                        }
                    },
                )
            camObj.put("pipelineAccess", pipelineAccess)
            if (!pipelineAccess.has("error")) {
                logPipelineAccessSummary(cameraId, pipelineAccess)
            }

            cams.put(camObj)
            onProgress(" id=$cameraId: done")
        }

        root.put("cameras", cams)
        return root
    }

    private fun deviceBlock(): JSONObject =
        JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("release", Build.VERSION.RELEASE)
        }

    internal fun intArrayToJson(arr: IntArray?): JSONArray {
        if (arr == null) return JSONArray()
        val ja = JSONArray()
        for (v in arr) ja.put(v)
        return ja
    }

    internal fun rangeArrayToJson(ranges: Array<android.util.Range<Int>>?): JSONArray {
        if (ranges == null) return JSONArray()
        val ja = JSONArray()
        for (r in ranges) {
            ja.put(
                JSONObject().apply {
                    put("lower", r.lower)
                    put("upper", r.upper)
                },
            )
        }
        return ja
    }

    internal fun sizeArrayToJson(sizes: Array<Size>?): JSONArray {
        if (sizes == null) return JSONArray()
        val ja = JSONArray()
        for (s in sizes) {
            ja.put(
                JSONObject().apply {
                    put("w", s.width)
                    put("h", s.height)
                },
            )
        }
        return ja
    }

    internal fun streamConfigToJson(map: StreamConfigurationMap?): JSONObject {
        if (map == null) return JSONObject().put("present", false)

        fun hsConfigs(): JSONArray {
            val out = JSONArray()
            val hsSizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
            for (s in hsSizes) {
                val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull()
                out.put(
                    JSONObject().apply {
                        put("w", s.width)
                        put("h", s.height)
                        put("fpsRanges", rangeArrayToJson(ranges))
                    },
                )
            }
            return out
        }

        val stSizes = runCatching { map.getOutputSizes(SurfaceTexture::class.java) }.getOrNull()
        val allSizeLists = listOfNotNull(
            stSizes?.toList(),
            runCatching { map.getOutputSizes(SurfaceHolder::class.java) }.getOrNull()?.toList(),
            runCatching { map.getOutputSizes(ImageFormat.JPEG) }.getOrNull()?.toList(),
            runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR) }.getOrNull()?.toList(),
            runCatching { map.getOutputSizes(ImageFormat.RAW10) }.getOrNull()?.toList(),
            runCatching { map.getOutputSizes(ImageFormat.RAW12) }.getOrNull()?.toList(),
            runCatching { map.getOutputSizes(MediaRecorder::class.java) }.getOrNull()?.toList(),
        )
        return JSONObject().apply {
            put("present", true)
            put(
                "outputSizes",
                JSONObject().apply {
                    put("surfaceTexture", sizeArrayToJson(stSizes))
                    put(
                        "surfaceHolder",
                        sizeArrayToJson(runCatching { map.getOutputSizes(SurfaceHolder::class.java) }.getOrNull()),
                    )
                },
            )
            put(
                "outputSizesByFormat",
                JSONObject().apply {
                    put("jpeg", sizeArrayToJson(runCatching { map.getOutputSizes(ImageFormat.JPEG) }.getOrNull()))
                    put("rawSensor", sizeArrayToJson(runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR) }.getOrNull()))
                    put("raw10", sizeArrayToJson(runCatching { map.getOutputSizes(ImageFormat.RAW10) }.getOrNull()))
                    put("raw12", sizeArrayToJson(runCatching { map.getOutputSizes(ImageFormat.RAW12) }.getOrNull()))
                    put("yuv420888", sizeArrayToJson(runCatching { map.getOutputSizes(ImageFormat.YUV_420_888) }.getOrNull()))
                    put(
                        "mediaRecorder",
                        sizeArrayToJson(runCatching { map.getOutputSizes(MediaRecorder::class.java) }.getOrNull()),
                    )
                },
            )
            put("highSpeedVideo", hsConfigs())
            put("aspectRatios", aspectRatiosJson(allSizeLists))
        }
    }

    internal fun aspectRatiosJson(sizeLists: List<List<Size>>): JSONArray {
        val ratios = linkedSetOf<String>()
        for (list in sizeLists) {
            for (s in list) {
                val g = gcdInt(s.width, s.height)
                if (g <= 0) continue
                ratios.add("${s.width / g}:${s.height / g}")
            }
        }
        return JSONArray().apply { ratios.sorted().forEach { put(it) } }
    }

    private fun gcdInt(a: Int, b: Int): Int {
        var x = kotlin.math.abs(a)
        var y = kotlin.math.abs(b)
        while (y != 0) {
            val t = y
            y = x % y
            x = t
        }
        return x
    }
}
