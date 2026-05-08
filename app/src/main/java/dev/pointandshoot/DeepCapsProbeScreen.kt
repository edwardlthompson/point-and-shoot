package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.graphics.SurfaceTexture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PNS.DeepCaps"

private object DeepCapsRunGuard {
    val running = AtomicBoolean(false)
}

private object DeepCapsWorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun DeepCapsProbeScreen(
    onBack: () -> Unit,
    startAuto: Boolean = false,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Idle") }
    var isRunning by remember { mutableStateOf(false) }
    val scanLines = remember { mutableStateListOf<String>() }

    val insets = rememberSystemInsetsDp()
    val autoStartConsumed = remember { AtomicBoolean(false) }
    val runningEffectConsumed = remember { AtomicBoolean(false) }
    val workJobRef = remember { AtomicReference<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            workJobRef.getAndSet(null)?.cancel()
        }
    }

    fun launch() {
        if (isRunning) return
        if (!DeepCapsRunGuard.running.compareAndSet(false, true)) {
            status = "Already running (guarded)."
            return
        }
        isRunning = true
        status = "Running…"
    }

    LaunchedEffect(startAuto) {
        if (!startAuto) return@LaunchedEffect
        if (!autoStartConsumed.compareAndSet(false, true)) return@LaunchedEffect
        launch()
    }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            runningEffectConsumed.set(false)
            return@LaunchedEffect
        }
        if (!runningEffectConsumed.compareAndSet(false, true)) return@LaunchedEffect

        scanLines.clear()
        scanLines.add("${Instant.now()} — Deep caps probe…")

        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val outFile = "deep_caps_$ts.json"

        Log.i(SWEEP_SIGNAL_TAG, "DEEP_CAPS_START file=$outFile")

        lateinit var workJob: Job
        workJob = DeepCapsWorkScope.launch {
            try {
                val savedPath = runDeepCapsProbe(
                    context.applicationContext,
                    outFile,
                    onProgress = { msg -> scanLines.appendProbeLine(msg) },
                )
                withContext(Dispatchers.Main) {
                    status = "OK saved=$savedPath"
                    isRunning = false
                    DeepCapsRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "DEEP_CAPS_DONE file=$outFile ok=true")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        DeepCapsRunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "DEEP_CAPS_DONE file=$outFile ok=false")
                    }
                }
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "runDeepCapsProbe failed", e)
                withContext(Dispatchers.Main) {
                    status = "FAILED: ${e::class.java.simpleName}: ${e.message}"
                    isRunning = false
                    DeepCapsRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "DEEP_CAPS_DONE file=$outFile ok=false")
                }
            } finally {
                workJobRef.compareAndSet(workJob, null)
            }
        }
        workJobRef.set(workJob)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(insets.asPaddingValues(extra = 16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = { launch() }, enabled = !isRunning) { Text("Run deep caps") }
        }

        Text("Deep camera capabilities probe (JSON to external files)")
        Text(status)
        ProbeLiveLogPanel(
            title = "Live scan log",
            lines = scanLines,
            modifier = Modifier.weight(1f),
        )
    }
}

private suspend fun runDeepCapsProbe(
    appContext: Context,
    outFileName: String,
    onProgress: suspend (String) -> Unit = {},
): String =
    withContext(Dispatchers.IO) {
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())

        onProgress("Found ${cameraIds.size} camera id(s): ${cameraIds.joinToString(",")}")

        val root = JSONObject()
        root.put("generatedAt", Instant.now().toString())
        root.put("device", JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("release", Build.VERSION.RELEASE)
        })

        val cams = JSONArray()
        for ((idx, cameraId) in cameraIds.withIndex()) {
            onProgress("Camera id=$cameraId (${idx + 1}/${cameraIds.size}): getCameraCharacteristics…")
            val camObj = JSONObject()
            camObj.put("cameraId", cameraId)

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
            camObj.put("physicalCameraIds", JSONArray(runCatching { cc.physicalCameraIds.toList() }.getOrDefault(emptyList())))

            onProgress(" id=$cameraId: lens info (apertures / OIS / focus / sensor size)…")
            val lensInfoJson = runCatching { LensInfoExtractor.extractToJson(cameraId, cc) }.fold(
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

            camObj.put("availableCapabilities", intArrayToJson(cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)))
            camObj.put("aeTargetFpsRanges", rangeArrayToJson(cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)))

            camObj.put("characteristicKeys", JSONArray(cc.keys.map { it.name }.sorted()))
            camObj.put("requestKeys", JSONArray(runCatching { cc.availableCaptureRequestKeys?.map { it.name } }.getOrNull().orEmpty().sorted()))
            camObj.put("resultKeys", JSONArray(runCatching { cc.availableCaptureResultKeys?.map { it.name } }.getOrNull().orEmpty().sorted()))
            camObj.put("sessionKeys", JSONArray(runCatching { cc.availableSessionKeys?.map { it.name } }.getOrNull().orEmpty().sorted()))

            val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            camObj.put("streamConfigurationMap", streamConfigToJson(map))

            onProgress(" id=$cameraId: pipelineAccess (HDR/DCG/ZSL/DR profiles)…")
            val pipelineAccess = runCatching { buildPipelineAccessProbe(cc) }.fold(
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

        onProgress("Writing $outFileName (${cams.length()} cameras)…")
        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val out = File(dir, outFileName)
        out.writeText(root.toString(2), Charsets.UTF_8)

        Log.i(TAG, "Saved deep caps JSON: ${out.absolutePath} (${out.length()} bytes)")
        out.absolutePath
    }

private fun intArrayToJson(arr: IntArray?): JSONArray {
    if (arr == null) return JSONArray()
    val ja = JSONArray()
    for (v in arr) ja.put(v)
    return ja
}

private fun rangeArrayToJson(ranges: Array<android.util.Range<Int>>?): JSONArray {
    if (ranges == null) return JSONArray()
    val ja = JSONArray()
    for (r in ranges) {
        ja.put(JSONObject().apply {
            put("lower", r.lower)
            put("upper", r.upper)
        })
    }
    return ja
}

private fun sizeArrayToJson(sizes: Array<Size>?): JSONArray {
    if (sizes == null) return JSONArray()
    val ja = JSONArray()
    for (s in sizes) {
        ja.put(JSONObject().apply {
            put("w", s.width)
            put("h", s.height)
        })
    }
    return ja
}

private fun streamConfigToJson(map: StreamConfigurationMap?): JSONObject {
    if (map == null) return JSONObject().put("present", false)

    fun hsConfigs(): JSONArray {
        val out = JSONArray()
        val hsSizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
        for (s in hsSizes) {
            val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull()
            out.put(JSONObject().apply {
                put("w", s.width)
                put("h", s.height)
                put("fpsRanges", rangeArrayToJson(ranges))
            })
        }
        return out
    }

    return JSONObject().apply {
        put("present", true)
        put("outputSizes", JSONObject().apply {
            put("surfaceTexture", sizeArrayToJson(runCatching { map.getOutputSizes(SurfaceTexture::class.java) }.getOrNull()))
            put("surfaceHolder", sizeArrayToJson(runCatching { map.getOutputSizes(SurfaceHolder::class.java) }.getOrNull()))
        })
        put("highSpeedVideo", hsConfigs())
    }
}

