package dev.pointandshoot

import android.content.Context
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PNS.HfrEnc"

private object EncoderProbeRunGuard {
    val running = AtomicBoolean(false)
}

private object EncoderProbeWorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

@Composable
fun HfrEncoderProbeScreen(
    onBack: () -> Unit,
    startAutoProbe: Boolean = false,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Idle") }
    var isRunning by remember { mutableStateOf(false) }
    val scanLines = remember { mutableStateListOf<String>() }

    val insets = rememberSystemInsetsDp()
    val autoStartConsumed = remember { AtomicBoolean(false) }
    val runningEffectConsumed = remember { AtomicBoolean(false) }
    val workJobRef = remember { AtomicReference<Job?>(null) }

    fun launchProbe() {
        if (isRunning) return
        if (!EncoderProbeRunGuard.running.compareAndSet(false, true)) {
            status = "Already running (guarded)."
            return
        }
        isRunning = true
        status = "Running…"
    }

    LaunchedEffect(startAutoProbe) {
        if (!startAutoProbe) return@LaunchedEffect
        if (!autoStartConsumed.compareAndSet(false, true)) return@LaunchedEffect
        launchProbe()
    }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            runningEffectConsumed.set(false)
            return@LaunchedEffect
        }
        if (!runningEffectConsumed.compareAndSet(false, true)) return@LaunchedEffect
        scanLines.clear()
        scanLines.add("${Instant.now()} — HFR encoder sweep…")
        val runId = UUID.randomUUID().toString()
        Log.i(SWEEP_SIGNAL_TAG, "ENC_PROBE_START runId=$runId")
        val appCtx = context.applicationContext
        val job = EncoderProbeWorkScope.launch {
            try {
                val summary = runEncoderProbeSweep(
                    appCtx,
                    runId,
                    onProgress = { msg -> scanLines.appendProbeLine(msg) },
                )
                withContext(Dispatchers.Main) {
                    status = "OK\n$summary"
                    isRunning = false
                    EncoderProbeRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "ENC_PROBE_DONE runId=$runId ok=true")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        EncoderProbeRunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "ENC_PROBE_DONE runId=$runId ok=false")
                    }
                }
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "encoder probe failed", e)
                withContext(Dispatchers.Main) {
                    status = "FAILED: ${e::class.java.simpleName}: ${e.message}"
                    isRunning = false
                    EncoderProbeRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "ENC_PROBE_DONE runId=$runId ok=false")
                }
            } finally {
                workJobRef.compareAndSet(coroutineContext.job, null)
            }
        }
        workJobRef.set(job)
    }

    DisposableEffect(Unit) {
        onDispose { workJobRef.getAndSet(null)?.cancel() }
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
            Button(onClick = { launchProbe() }, enabled = !isRunning) { Text("Run encoder probe") }
        }

        Text("HFR encoder probe (MediaCodec surface)")
        Text(status)
        ProbeLiveLogPanel(
            title = "Live scan log",
            lines = scanLines,
            modifier = Modifier.weight(1f),
        )
    }
}

private suspend fun runEncoderProbeSweep(
    appContext: Context,
    runId: String,
    onProgress: suspend (String) -> Unit = {},
): String = withContext(Dispatchers.Default) {
    val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val allIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
    val preferred = listOf("2", "3", "4", "0")
    val sweepCameras = (preferred.filter { allIds.contains(it) } + allIds)
        .distinct()
        .filter { it != "1" && it != "0" }
    val sequence = listOf(120, 240, 480)
    val mimeOrder = listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)

    val jsonRoot = JSONObject().apply {
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
            put("fpsSequence", JSONArray(sequence))
            put("mimeOrder", JSONArray(mimeOrder))
            put("durationMs", 2500)
        })
        put("results", JSONArray())
    }

    val sb = StringBuilder()
    sb.appendLine("cameras=${sweepCameras.joinToString(",")} fps=${sequence.joinToString(",")} mimes=${mimeOrder.joinToString(",")}")

    onProgress("Sweep order: cameras=${sweepCameras.joinToString(",")} · fps=${sequence.joinToString(",")}")

    for ((ci, camId) in sweepCameras.withIndex()) {
        onProgress("━━ Camera $camId (${ci + 1}/${sweepCameras.size}) ━━")
        for (fps in sequence) {
            onProgress("$camId · ${fps}fps — enumerating targets / codecs…")
            val camObj = JSONObject().apply {
                put("cameraId", camId)
                put("fps", fps)
                put("attempts", JSONArray())
            }

            val attempts = mutableListOf<EncoderProbeResult>()
            for (mime in mimeOrder) {
                onProgress("  $camId · ${fps}fps · codec ${mime.substringAfterLast('/')}")
                if (!supportsSurfaceEncoding(mime)) {
                    onProgress("    → skip (encoder unavailable)")
                    attempts += EncoderProbeResult(
                        ok = false,
                        measuredFps = 0.0,
                        note = "mime=$mime unsupported",
                        size = null,
                        fpsRange = null,
                        sessionKind = "hfr",
                    )
                    continue
                }

                val cc = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
                val map = cc?.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val targets = enumerateHighSpeedTargets(map, fps)
                if (targets.isEmpty()) {
                    onProgress("    → no high-speed surface targets for this codec")
                    attempts += EncoderProbeResult(
                        ok = false,
                        measuredFps = 0.0,
                        note = "mime=$mime no_high_speed_target",
                        size = null,
                        fpsRange = null,
                        sessionKind = "hfr",
                    )
                    continue
                }

                for ((size, range) in targets) {
                    onProgress("    · try ${size.width}x${size.height} range ${range.lower}-${range.upper}")
                    val r = runSingleHfrEncoderProbe(
                        cm = cm,
                        camId = camId,
                        durationMs = 2500L,
                        mime = mime,
                        size = size,
                        fpsRange = range,
                    )
                    attempts += r
                    onProgress("      → ok=${r.ok} fps=${"%.1f".format(r.measuredFps)} ${r.note}")
                    if (r.ok && r.measuredFps >= fps * 0.92) break
                    runCatching { Thread.sleep(120) }
                }
            }

            val best = attempts.maxByOrNull { it.measuredFps }
            val summary = best ?: EncoderProbeResult(false, 0.0, "no_attempts", null, null, "hfr")

            onProgress("  ⇒ $camId ${fps}fps BEST ok=${summary.ok} fps=${"%.1f".format(summary.measuredFps)} (${summary.note})")

            sb.appendLine("cam=$camId fps=$fps bestOk=${summary.ok} bestFps=${"%.1f".format(summary.measuredFps)} bestNote=${summary.note}")

            val attemptsJson = camObj.getJSONArray("attempts")
            for (a in attempts) {
                attemptsJson.put(a.toJson())
                Log.i(
                    SWEEP_SIGNAL_TAG,
                    "ENC_ATTEMPT runId=$runId cameraId=$camId fps=$fps ok=${a.ok} measuredFps=${"%.1f".format(a.measuredFps)} note=${a.note}",
                )
            }
            camObj.put("best", summary.toJson())
            jsonRoot.getJSONArray("results").put(camObj)

            Log.i(
                SWEEP_SIGNAL_TAG,
                "ENC_SAMPLE runId=$runId cameraId=$camId fps=$fps ok=${summary.ok} measuredFps=${"%.1f".format(summary.measuredFps)} note=${summary.note}",
            )

            runCatching { Thread.sleep(450) }
        }
    }

    onProgress("Saving enc_probe_*.json …")
    runCatching {
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val out = File(dir, "enc_probe_$ts.json")
        out.writeText(jsonRoot.toString(2), Charsets.UTF_8)
        Log.i(TAG, "Saved encoder probe JSON: ${out.absolutePath} (${out.length()} bytes)")
        Log.i(SWEEP_SIGNAL_TAG, "ENC_PROBE_JSON file=${out.name} bytes=${out.length()}")
    }

    sb.toString()
}
