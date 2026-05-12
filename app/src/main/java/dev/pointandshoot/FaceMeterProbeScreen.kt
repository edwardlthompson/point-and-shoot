package dev.pointandshoot

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PNS.FaceMeterProbe"

private object FaceMeterRunGuard {
    val running = AtomicBoolean(false)
}

private object FaceMeterWorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Writes full probe markdown plus a compact **face / eye / metering** JSON under app external files.
 * ADB: `--es pns_screen facemeter --ez pns_autofacemeter true` (see `scripts/pns_face_meter_probe.ps1`).
 * Does **not** require runtime `CAMERA` permission (static characteristics only).
 */
@Composable
fun FaceMeterProbeScreen(
    onBack: () -> Unit,
    startAuto: Boolean,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Idle") }
    var isRunning by remember { mutableStateOf(false) }
    val scanLines = remember { mutableStateListOf<String>() }
    val insets = rememberSystemInsetsDp()
    val autoStartConsumed = remember { AtomicBoolean(false) }
    val runningEffectConsumed = remember { AtomicBoolean(false) }
    val workJobRef = remember { AtomicReference<Job?>(null) }

    BackHandler(onBack = onBack)

    DisposableEffect(Unit) {
        onDispose {
            workJobRef.getAndSet(null)?.cancel()
        }
    }

    fun launchRun() {
        if (isRunning) return
        if (!FaceMeterRunGuard.running.compareAndSet(false, true)) {
            status = "Already running (guarded)."
            return
        }
        isRunning = true
        status = "Running…"
    }

    LaunchedEffect(startAuto) {
        if (!startAuto) return@LaunchedEffect
        if (!autoStartConsumed.compareAndSet(false, true)) return@LaunchedEffect
        launchRun()
    }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            runningEffectConsumed.set(false)
            return@LaunchedEffect
        }
        if (!runningEffectConsumed.compareAndSet(false, true)) return@LaunchedEffect

        scanLines.clear()
        scanLines.add("${Instant.now()} — Face / eye / metering probe…")

        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val mdName = "face_meter_probe_$ts.md"
        val jsonName = "face_meter_probe_$ts.json"

        Log.i(SWEEP_SIGNAL_TAG, "FACE_METER_PROBE_START md=$mdName json=$jsonName")

        lateinit var workJob: Job
        workJob = FaceMeterWorkScope.launch {
            try {
                val appCtx = context.applicationContext
                val dir = appCtx.getExternalFilesDir(null) ?: appCtx.filesDir
                val md = File(dir, mdName)
                val js = File(dir, jsonName)
                val mdText = buildProbeReportMarkdown(appCtx)
                val jsonText = buildFaceMeterProbeSummaryJson(appCtx)
                md.writeText(mdText, Charsets.UTF_8)
                js.writeText(jsonText, Charsets.UTF_8)
                withContext(Dispatchers.Main) {
                    status = "OK — ${md.absolutePath}"
                    scanLines.add("Wrote ${md.length()} bytes (md), ${js.length()} bytes (json)")
                    isRunning = false
                    FaceMeterRunGuard.running.set(false)
                    Log.i(
                        SWEEP_SIGNAL_TAG,
                        "FACE_METER_PROBE_DONE mdPath=${md.absolutePath} jsonPath=${js.absolutePath} ok=true",
                    )
                    Log.i(TAG, "saved md=${md.absolutePath} json=${js.absolutePath}")
                    if (startAuto) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            (context as? ComponentActivity)?.finish()
                        }, 400L)
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        FaceMeterRunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "FACE_METER_PROBE_DONE ok=false reason=cancelled")
                    }
                }
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "face meter probe failed", e)
                withContext(Dispatchers.Main) {
                    status = "FAILED: ${e::class.java.simpleName}: ${e.message}"
                    isRunning = false
                    FaceMeterRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "FACE_METER_PROBE_DONE ok=false reason=${e::class.java.simpleName}")
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
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = { launchRun() }, enabled = !isRunning) { Text("Run face / metering probe") }
        }
        Text("Writes timestamped `face_meter_probe_*.md` + `.json` under app external files (no camera open).")
        Text(status)
        ProbeLiveLogPanel(
            title = "Log",
            lines = scanLines,
            modifier = Modifier.weight(1f),
        )
    }
}
