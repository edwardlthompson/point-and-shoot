package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaFormat
import android.util.Log
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PNS.Exhaustive"

/** Per matrix cell: abandon probe if the camera/codec stack blocks past this (avoids stalling the full sweep). */
private const val ENCODER_CELL_WALL_MS = 75_000L

private object ExhaustiveProbeRunGuard {
    val running = AtomicBoolean(false)
}

/** Not tied to composition — avoids [androidx.compose.runtime.LeftCompositionCancellationException] when the main thread or insets churn during a long probe. */
private object ExhaustiveProbeWorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

@Composable
fun ExhaustiveMediaProbeScreen(
    onBack: () -> Unit,
    startAuto: Boolean = false,
    includeLogicalCamera: Boolean = false,
    /** Skip regular (non-HFR) video matrix attempts; only constrained high-speed combos per camera. */
    hfrOnly: Boolean = false,
    durationMs: Long = 1800L,
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
        if (!ExhaustiveProbeRunGuard.running.compareAndSet(false, true)) {
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
        scanLines.add(
            "${Instant.now()} — Starting exhaustive probe…" +
                if (hfrOnly) " (HFR-only)" else "",
        )
        val runId = UUID.randomUUID().toString()
        Log.i(SWEEP_SIGNAL_TAG, "EXHAUSTIVE_PROBE_START runId=$runId hfrOnly=$hfrOnly")
        lateinit var workJob: Job
        workJob = ExhaustiveProbeWorkScope.launch {
            try {
                val result = try {
                    runExhaustiveMediaProbe(
                        context.applicationContext,
                        runId,
                        includeLogicalCamera,
                        hfrOnly,
                        durationMs,
                        onProgress = { msg -> scanLines.appendProbeLine(msg) },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "runExhaustiveMediaProbe failed", e)
                    "FAILED: ${e::class.java.simpleName}: ${e.message}"
                }
                withContext(Dispatchers.Main) {
                    status = result
                    isRunning = false
                    ExhaustiveProbeRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "EXHAUSTIVE_PROBE_DONE runId=$runId ok=${!result.startsWith("FAILED")}")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        ExhaustiveProbeRunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "EXHAUSTIVE_PROBE_DONE runId=$runId ok=false")
                    }
                }
                throw e
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
            Button(onClick = { launch() }, enabled = !isRunning) { Text("Run exhaustive probe") }
        }
        Text(
            if (hfrOnly) {
                "HFR-only matrix per camera (JSON); optional logical camera"
            } else {
                "Exhaustive HFR + regular video matrix (JSON); optional logical camera"
            },
        )
        Text(status)
        ProbeLiveLogPanel(
            title = "Live scan log",
            lines = scanLines,
            modifier = Modifier.weight(1f),
        )
    }
}

private suspend fun runHfrEncoderProbeBounded(
    cm: CameraManager,
    camId: String,
    durationMs: Long,
    mime: String,
    size: android.util.Size,
    fpsRange: android.util.Range<Int>,
): EncoderProbeResult =
    withContext(Dispatchers.IO) {
        val ex = Executors.newSingleThreadExecutor()
        try {
            val fut = ex.submit<EncoderProbeResult> {
                runSingleHfrEncoderProbe(cm, camId, durationMs, mime, size, fpsRange)
            }
            try {
                fut.get(ENCODER_CELL_WALL_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                fut.cancel(true)
                EncoderProbeResult(
                    false,
                    0.0,
                    "wall_clock_timeout_${ENCODER_CELL_WALL_MS}ms mime=$mime",
                    size,
                    fpsRange,
                    "hfr",
                )
            }
        } finally {
            ex.shutdownNow()
        }
    }

private suspend fun runRegularEncoderProbeBounded(
    cm: CameraManager,
    camId: String,
    durationMs: Long,
    mime: String,
    size: android.util.Size,
    fpsRange: android.util.Range<Int>,
): EncoderProbeResult =
    withContext(Dispatchers.IO) {
        val ex = Executors.newSingleThreadExecutor()
        try {
            val fut = ex.submit<EncoderProbeResult> {
                runSingleRegularEncoderProbe(cm, camId, durationMs, mime, size, fpsRange)
            }
            try {
                fut.get(ENCODER_CELL_WALL_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                fut.cancel(true)
                EncoderProbeResult(
                    false,
                    0.0,
                    "wall_clock_timeout_${ENCODER_CELL_WALL_MS}ms mime=$mime",
                    size,
                    fpsRange,
                    "regular",
                )
            }
        } finally {
            ex.shutdownNow()
        }
    }

private suspend fun runExhaustiveMediaProbe(
    appContext: Context,
    runId: String,
    includeLogicalCamera: Boolean,
    hfrOnly: Boolean,
    durationMs: Long,
    onProgress: suspend (String) -> Unit = {},
): String = withContext(Dispatchers.Default) {
    val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val allIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
    val preferred = listOf("2", "3", "4", "0", "1")
    val sweepCameras = (preferred.filter { allIds.contains(it) } + allIds)
        .distinct()
        .filter { id ->
            when {
                includeLogicalCamera -> true
                else -> id != "0"
            }
        }

    val mimeOrder = listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)

    val root = JSONObject().apply {
        put("runId", runId)
        put("generatedAt", Instant.now().toString())
        put("device", JSONObject().apply {
            put("manufacturer", android.os.Build.MANUFACTURER)
            put("model", android.os.Build.MODEL)
            put("device", android.os.Build.DEVICE)
            put("sdkInt", android.os.Build.VERSION.SDK_INT)
            put("release", android.os.Build.VERSION.RELEASE)
        })
        put("params", JSONObject().apply {
            put("cameras", JSONArray(sweepCameras))
            put("mimeOrder", JSONArray(mimeOrder))
            put("durationMs", durationMs)
            put("includeLogicalCamera", includeLogicalCamera)
            put("hfrOnly", hfrOnly)
        })
        put("cameras", JSONArray())
    }

    val camerasJson = root.getJSONArray("cameras")

    onProgress("Cameras in sweep: ${sweepCameras.joinToString(",")} (${sweepCameras.size} total)")
    onProgress("Run $runId · ${durationMs}ms per capture")

    for ((camIndex, camId) in sweepCameras.withIndex()) {
        val cc = runCatching { cm.getCameraCharacteristics(camId) }
            .recoverCatching {
                Thread.sleep(400)
                cm.getCameraCharacteristics(camId)
            }
            .getOrNull()

        val camObj = JSONObject().apply {
            put("cameraId", camId)
            put("error", JSONObject.NULL)
            put("capabilities", capabilityNames(cc?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)))
            put("physicalCameraIds", JSONArray(runCatching { cc?.physicalCameraIds?.toList() }.getOrDefault(emptyList())))
            put("vendorKeyHints", vendorKeyHintsJson(cc))
            put("hfrCombosAdvertised", JSONArray())
            put("hfrAttempts", JSONArray())
            put("regularCombosAdvertised", JSONArray())
            put("regularAttempts", JSONArray())
        }

        if (cc == null) {
            onProgress("Camera $camId: FAILED getCameraCharacteristics — skipping")
            camObj.put("error", "getCameraCharacteristics_failed")
            camerasJson.put(camObj)
            continue
        }

        onProgress("━━ Camera $camId (${camIndex + 1}/${sweepCameras.size}) ━━")

        val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val hfrCombos = enumerateAllHighSpeedCombos(map)
        val hfrAdvertised = camObj.getJSONArray("hfrCombosAdvertised")
        for ((sz, r) in hfrCombos) {
            hfrAdvertised.put(
                JSONObject().apply {
                    put("w", sz.width)
                    put("h", sz.height)
                    put("lower", r.lower)
                    put("upper", r.upper)
                },
            )
        }

        val regularCombos = if (hfrOnly) {
            emptyList()
        } else {
            val aeRanges = cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            enumerateRegularVideoCombos(map, aeRanges)
        }
        val regAdvertised = camObj.getJSONArray("regularCombosAdvertised")
        if (!hfrOnly) {
            for ((sz, r) in regularCombos) {
                regAdvertised.put(
                    JSONObject().apply {
                        put("w", sz.width)
                        put("h", sz.height)
                        put("lower", r.lower)
                        put("upper", r.upper)
                    },
                )
            }
        }

        onProgress(
            if (hfrOnly) {
                "$camId: ${hfrCombos.size} HFR size×fps combos (REG phase skipped)"
            } else {
                "$camId: ${hfrCombos.size} HFR size×fps combos · ${regularCombos.size} regular combos (advertised)"
            },
        )

        val hfrAttempts = camObj.getJSONArray("hfrAttempts")
        val hfrTotal = hfrCombos.size * mimeOrder.size
        var hfrIdx = 0
        for ((size, fpsRange) in hfrCombos) {
            for (mime in mimeOrder) {
                hfrIdx++
                if (!supportsSurfaceEncoding(mime)) {
                    onProgress("HFR $hfrIdx/$hfrTotal · $camId · ${size.width}x${size.height} · ${fpsRange.lower}-${fpsRange.upper} · ${mime.substringAfterLast('/')} · SKIP (no encoder)")
                    hfrAttempts.put(
                        EncoderProbeResult(
                            ok = false,
                            measuredFps = 0.0,
                            note = "mime=$mime unsupported",
                            size = size,
                            fpsRange = fpsRange,
                            sessionKind = "hfr",
                        ).toJson(),
                    )
                    continue
                }
                val r = runHfrEncoderProbeBounded(cm, camId, durationMs, mime, size, fpsRange)
                hfrAttempts.put(r.toJson())
                val st = if (r.ok) "OK" else "FAIL"
                onProgress(
                    "HFR $hfrIdx/$hfrTotal · $camId · ${size.width}x${size.height} · ${fpsRange.lower}-${fpsRange.upper} · ${mime.substringAfterLast('/')} · $st ${"%.0f".format(r.measuredFps)}fps",
                )
                Log.i(
                    SWEEP_SIGNAL_TAG,
                    "EXH_HFR cam=$camId ${size.width}x${size.height} ${fpsRange.lower}-${fpsRange.upper} mime=$mime ok=${r.ok} fps=${"%.1f".format(r.measuredFps)}",
                )
                runCatching { Thread.sleep(100) }
            }
            runCatching { Thread.sleep(200) }
        }

        if (!hfrOnly) {
            val regAttempts = camObj.getJSONArray("regularAttempts")
            val regWorkList = regularCombos.filter { it.second.upper <= 120 }
            val regTotal = regWorkList.sumOf {
                mimeOrder.count { supportsSurfaceEncoding(it) }
            }
            var regIdx = 0
            onProgress("$camId: regular video phase — $regTotal attempts (≤120fps targets)")
            for ((size, fpsRange) in regWorkList) {
                for (mime in mimeOrder) {
                    if (!supportsSurfaceEncoding(mime)) continue
                    regIdx++
                    val r = runRegularEncoderProbeBounded(cm, camId, durationMs, mime, size, fpsRange)
                    regAttempts.put(r.toJson())
                    val st = if (r.ok) "OK" else "FAIL"
                    onProgress(
                        "REG $regIdx/$regTotal · $camId · ${size.width}x${size.height} · ${fpsRange.lower}-${fpsRange.upper} · ${mime.substringAfterLast('/')} · $st ${"%.0f".format(r.measuredFps)}fps",
                    )
                    Log.i(
                        SWEEP_SIGNAL_TAG,
                        "EXH_REG cam=$camId ${size.width}x${size.height} ${fpsRange.lower}-${fpsRange.upper} mime=$mime ok=${r.ok} fps=${"%.1f".format(r.measuredFps)}",
                    )
                    runCatching { Thread.sleep(80) }
                }
                runCatching { Thread.sleep(150) }
            }
        }

        onProgress("$camId: feature keys / vendor probe…")
        camObj.put("featureProbe", featureProbeJson(cc))
        val pipelineAccess = runCatching { buildPipelineAccessProbe(cc) }.fold(
            onSuccess = { it },
            onFailure = { e ->
                Log.e(TAG, "pipelineAccess failed for camera $camId", e)
                JSONObject().apply {
                    put("error", "${e::class.java.simpleName}: ${e.message}")
                }
            },
        )
        camObj.put("pipelineAccess", pipelineAccess)
        if (!pipelineAccess.has("error")) {
            logPipelineAccessSummary(camId, pipelineAccess)
        }
        camerasJson.put(camObj)
        onProgress("$camId: done — next camera or finish")
        runCatching { Thread.sleep(350) }
    }

    onProgress("Writing exhaustive_probe_*.json …")
    val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())
    val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
    val out = File(dir, "exhaustive_probe_$ts.json")
    out.writeText(root.toString(2), Charsets.UTF_8)
    Log.i(TAG, "Saved exhaustive probe JSON: ${out.absolutePath} (${out.length()} bytes)")
    Log.i(SWEEP_SIGNAL_TAG, "EXHAUSTIVE_PROBE_JSON file=${out.name} bytes=${out.length()}")

    "OK saved=${out.absolutePath} cameras=${sweepCameras.size} hfrTotal=${countAttempts(root, "hfrAttempts")} regTotal=${countAttempts(root, "regularAttempts")}"
}

private fun intArrayToJson(arr: IntArray?): Any {
    if (arr == null) return JSONObject.NULL
    val ja = JSONArray()
    for (v in arr) ja.put(v)
    return ja
}

private fun countAttempts(root: JSONObject, key: String): Int {
    var n = 0
    val arr = root.getJSONArray("cameras")
    for (i in 0 until arr.length()) {
        n += arr.getJSONObject(i).getJSONArray(key).length()
    }
    return n
}

private fun vendorKeyHintsJson(cc: CameraCharacteristics?): JSONObject {
    val o = JSONObject()
    if (cc == null) return o
    val pool = runCatching {
        buildList {
            cc.availableCaptureRequestKeys?.forEach { add(it.name) }
            cc.availableSessionKeys?.forEach { add(it.name) }
            cc.availableCaptureResultKeys?.forEach { add(it.name) }
        }
    }.getOrDefault(emptyList())

    fun hits(vararg terms: String): JSONArray {
        val ja = JSONArray()
        pool.filter { k -> terms.any { t -> k.contains(t, ignoreCase = true) } }
            .distinct()
            .sorted()
            .forEach { ja.put(it) }
        return ja
    }
    o.put("hdr_dcg", hits("hdr", "dcg", "mfhdr", "lbmf"))
    o.put("raw_dng", hits("raw", "dng", "idealraw"))
    o.put("night_long_exposure", hits("night", "longexp", "slowshutter"))
    o.put("bokeh_portrait", hits("bokeh", "portrait", "depth"))
    o.put("zoom_fusion", hits("zoom", "fusion", "sat", "logical"))
    return o
}

private fun featureProbeJson(cc: CameraCharacteristics): JSONObject {
    val out = JSONObject()
    val caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
    val set = caps.toSet()
    fun ok(id: Int) = id in set
    out.put(
        "CONSTRAINED_HIGH_SPEED_VIDEO",
        ok(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO),
    )
    out.put("RAW", ok(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW))
    out.put("MANUAL_SENSOR", ok(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR))
    out.put(
        "MANUAL_POST_PROCESSING",
        ok(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING),
    )
    out.put("BURST_CAPTURE", ok(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE))
    out.put(
        "LOGICAL_MULTI_CAMERA",
        ok(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA),
    )
    out.put(
        "READ_SENSOR_SETTINGS",
        ok(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS),
    )
    out.put("DEPTH_OUTPUT", ok(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT))
    out.put(
        "YUV_REPROCESSING",
        ok(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING),
    )
    val ts = cc.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
    out.put("sensorTimestampSource", ts?.toString() ?: JSONObject.NULL)
    out.put(
        "maxRegionsAe",
        intArrayToJson(cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) as? IntArray),
    )
    out.put(
        "maxRegionsAf",
        intArrayToJson(cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) as? IntArray),
    )
    out.put(
        "maxRegionsAwb",
        intArrayToJson(cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) as? IntArray),
    )
    return out
}
