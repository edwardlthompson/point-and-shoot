package dev.pointandshoot

import android.content.Context
import android.hardware.Camera
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
import kotlinx.coroutines.job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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

private const val TAG = "PNS.LegacyCam"

private object LegacyCam1RunGuard {
    val running = AtomicBoolean(false)
}

private object LegacyCam1WorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun LegacyCamera1ProbeScreen(
    onBack: () -> Unit,
    startAuto: Boolean = false,
) {
    val context = LocalContext.current
    val insets = rememberSystemInsetsDp()
    var status by remember { mutableStateOf("Idle") }
    var isRunning by remember { mutableStateOf(false) }
    val scanLines = remember { mutableStateListOf<String>() }
    val autoStartConsumed = remember { AtomicBoolean(false) }
    val runningEffectConsumed = remember { AtomicBoolean(false) }
    val workJobRef = remember { AtomicReference<Job?>(null) }

    fun launch() {
        if (isRunning) return
        if (!LegacyCam1RunGuard.running.compareAndSet(false, true)) {
            status = "Already running (guarded)."
            return
        }
        isRunning = true
    }

    LaunchedEffect(startAuto) {
        if (!startAuto) return@LaunchedEffect
        if (!autoStartConsumed.compareAndSet(false, true)) return@LaunchedEffect
        launch()
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
            Button(
                enabled = !isRunning,
                onClick = { launch() },
            ) { Text("Run Camera1 probe") }
        }

        Text("Legacy Camera API probe (Camera1)")
        Text(status)
        ProbeLiveLogPanel(
            title = "Live scan log",
            lines = scanLines,
            modifier = Modifier.weight(1f),
        )
    }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            runningEffectConsumed.set(false)
            return@LaunchedEffect
        }
        if (!runningEffectConsumed.compareAndSet(false, true)) return@LaunchedEffect
        scanLines.clear()
        scanLines.add("${Instant.now()} — Legacy Camera1 probe…")
        val runId = UUID.randomUUID().toString()
        Log.i(SWEEP_SIGNAL_TAG, "LEGACY_CAM1_START runId=$runId")
        status = "Running…"
        val appCtx = context.applicationContext
        val job = LegacyCam1WorkScope.launch {
            try {
                val out = runCatching {
                    runLegacyCamera1Probe(
                        appCtx,
                        runId,
                        onProgress = { msg -> scanLines.appendProbeLine(msg) },
                    )
                }.fold(
                    onSuccess = { it },
                    onFailure = {
                        Log.e(TAG, "Camera1 probe failed", it)
                        "FAILED: ${it::class.java.simpleName}: ${it.message}"
                    },
                )
                withContext(Dispatchers.Main) {
                    status = out
                    isRunning = false
                    LegacyCam1RunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "LEGACY_CAM1_DONE runId=$runId ok=${!out.startsWith("FAILED")}")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        LegacyCam1RunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "LEGACY_CAM1_DONE runId=$runId ok=false")
                    }
                }
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Camera1 probe failed", e)
                withContext(Dispatchers.Main) {
                    status = "FAILED: ${e::class.java.simpleName}: ${e.message}"
                    isRunning = false
                    LegacyCam1RunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "LEGACY_CAM1_DONE runId=$runId ok=false")
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
}

private suspend fun runLegacyCamera1Probe(
    appContext: Context,
    runId: String,
    onProgress: suspend (String) -> Unit = {},
): String = withContext(Dispatchers.IO) {
    val root = JSONObject()
    root.put("runId", runId)
    root.put("generatedAt", Instant.now().toString())
    root.put("device", JSONObject().apply {
        put("manufacturer", android.os.Build.MANUFACTURER)
        put("model", android.os.Build.MODEL)
        put("device", android.os.Build.DEVICE)
        put("sdkInt", android.os.Build.VERSION.SDK_INT)
    })
    val camerasJa = JSONArray()
    val sb = StringBuilder()
    val n = runCatching { Camera.getNumberOfCameras() }.getOrDefault(0)
    onProgress("Camera.getNumberOfCameras() = $n")
    sb.appendLine("Camera1 count=$n")
    root.put("cameraCount", n)
    for (i in 0 until n) {
        onProgress("Opening Camera.open($i) (${i + 1}/$n)…")
        val camObj = JSONObject()
        camObj.put("index", i)
        var cam: Camera? = null
        try {
            cam = Camera.open(i)
            val p = cam.parameters
            val fpsRanges = runCatching { p.supportedPreviewFpsRange }.getOrNull().orEmpty()
            val previewSizes = runCatching { p.supportedPreviewSizes }.getOrNull().orEmpty()
            val fpsJa = JSONArray()
            for (r in fpsRanges) {
                fpsJa.put(JSONObject().apply {
                    put("min1000", r[0])
                    put("max1000", r[1])
                })
            }
            val sizesJa = JSONArray()
            for (s in previewSizes) {
                sizesJa.put(JSONObject().apply {
                    put("w", s.width)
                    put("h", s.height)
                })
            }
            camObj.put("previewFpsRanges", fpsJa)
            camObj.put("previewSizes", sizesJa)
            camObj.put("error", JSONObject.NULL)
            onProgress(" index $i: ${previewSizes.size} preview sizes · ${fpsRanges.size} fps ranges")
            sb.appendLine(
                "cameraIndex=$i previewFpsRanges=" + fpsRanges.joinToString(",") { "[${it[0]},${it[1]}]" },
            )
            sb.appendLine(
                "cameraIndex=$i previewSizes=" + previewSizes.take(24).joinToString(",") { "${it.width}x${it.height}" } +
                    (if (previewSizes.size > 24) " …(${previewSizes.size})" else ""),
            )
        } catch (t: Throwable) {
            onProgress(" index $i: ERROR ${t::class.java.simpleName}: ${t.message}")
            camObj.put("previewFpsRanges", JSONArray())
            camObj.put("previewSizes", JSONArray())
            camObj.put("error", "${t::class.java.simpleName}:${t.message}")
            sb.appendLine("cameraIndex=$i FAILED ${t::class.java.simpleName}:${t.message}")
        } finally {
            runCatching { cam?.release() }
        }
        camerasJa.put(camObj)
    }
    root.put("cameras", camerasJa)
    onProgress("Writing legacy_camera1_*.json …")
    val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())
    val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
    val outFile = File(dir, "legacy_camera1_$ts.json")
    outFile.writeText(root.toString(2), Charsets.UTF_8)
    Log.i(TAG, "Saved legacy Camera1 JSON: ${outFile.absolutePath}")
    Log.i(SWEEP_SIGNAL_TAG, "LEGACY_CAM1_JSON file=${outFile.name} bytes=${outFile.length()}")
    sb.toString()
}
