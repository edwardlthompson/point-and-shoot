package dev.pointandshoot

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

private const val TAG = "PNS.SessionMatrix"

private object SessionMatrixRunGuard {
    val running = AtomicBoolean(false)
}

private object SessionMatrixWorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun SessionMatrixProbeScreen(
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
        if (!SessionMatrixRunGuard.running.compareAndSet(false, true)) {
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
        scanLines.add("${Instant.now()} — Session configuration matrix…")

        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val outFile = "session_matrix_$ts.json"

        Log.i(SWEEP_SIGNAL_TAG, "SESSION_MATRIX_START file=$outFile")

        lateinit var workJob: Job
        workJob = SessionMatrixWorkScope.launch {
            try {
                val savedPath = runSessionMatrixProbe(
                    context.applicationContext,
                    outFile,
                    onProgress = { msg -> scanLines.appendProbeLine(msg) },
                )
                withContext(Dispatchers.Main) {
                    status = "OK saved=$savedPath"
                    isRunning = false
                    SessionMatrixRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "SESSION_MATRIX_DONE file=$outFile ok=true")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        SessionMatrixRunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "SESSION_MATRIX_DONE file=$outFile ok=false")
                    }
                }
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "runSessionMatrixProbe failed", e)
                withContext(Dispatchers.Main) {
                    status = "FAILED: ${e::class.java.simpleName}: ${e.message}"
                    isRunning = false
                    SessionMatrixRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "SESSION_MATRIX_DONE file=$outFile ok=false")
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
            Button(onClick = { launch() }, enabled = !isRunning) { Text("Run session matrix") }
        }
        Text("Phase 2: SessionConfiguration support (regular + high-speed) per camera id")
        Text(status)
        ProbeLiveLogPanel(
            title = "Live scan log",
            lines = scanLines,
            modifier = Modifier.weight(1f),
        )
    }
}

private suspend fun runSessionMatrixProbe(
    appContext: Context,
    outFileName: String,
    onProgress: suspend (String) -> Unit = {},
): String =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onProgress("API < 28: SessionConfiguration not supported; skipping.")
            val root = JSONObject().apply {
                put("generatedAt", Instant.now().toString())
                put("note", "requires API 28+")
                put("cameras", JSONArray())
            }
            val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            val out = File(dir, outFileName)
            out.writeText(root.toString(2), Charsets.UTF_8)
            return@withContext out.absolutePath
        }

        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        onProgress("Cameras: ${cameraIds.joinToString(",")} (${cameraIds.size})")

        val root = JSONObject()
        root.put("generatedAt", Instant.now().toString())
        root.put("phase", 2)
        root.put("probe", "session_configuration_matrix")
        root.put("device", JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("sdkInt", Build.VERSION.SDK_INT)
        })

        val camsOut = JSONArray()
        for ((idx, cameraId) in cameraIds.withIndex()) {
            onProgress("━━ Camera $cameraId (${idx + 1}/${cameraIds.size}) ━━")
            val camJson = JSONObject().put("cameraId", cameraId)
            val testsArr = JSONArray()
            val cc = runCatching { cm.getCameraCharacteristics(cameraId) }.getOrNull()
            if (cc == null) {
                camJson.put("error", "getCameraCharacteristics_failed")
                camsOut.put(camJson)
                continue
            }

            val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSizes = runCatching {
                map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
            }.getOrDefault(emptyList())

            fun pickSize(targetW: Int, targetH: Int): Size? =
                previewSizes.minByOrNull { abs(it.width - targetW) + abs(it.height - targetH) }

            val regularCases = mutableListOf<Pair<String, Size>>()
            pickSize(640, 480)?.let { regularCases += "regular_640x480" to it }
            pickSize(1280, 720)?.let { regularCases += "regular_1280x720" to it }
            pickSize(1920, 1080)?.let { regularCases += "regular_1920x1080" to it }

            val hsSizes = runCatching { map?.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
            val hsFirst = hsSizes.minByOrNull { it.width * it.height }

            val ht = HandlerThread("PNS.SessMx-$cameraId")
            ht.start()
            val h = Handler(ht.looper)
            var device: CameraDevice? = null
            try {
                val openLatch = CountDownLatch(1)
                cm.openCamera(
                    cameraId,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(cd: CameraDevice) {
                            device = cd
                            openLatch.countDown()
                        }

                        override fun onDisconnected(cd: CameraDevice) {
                            runCatching { cd.close() }
                            openLatch.countDown()
                        }

                        override fun onError(cd: CameraDevice, error: Int) {
                            runCatching { cd.close() }
                            openLatch.countDown()
                        }
                    },
                    h,
                )
                if (!openLatch.await(6, TimeUnit.SECONDS) || device == null) {
                    camJson.put("openError", "timeout_or_failed")
                    camsOut.put(camJson)
                    continue
                }
                val d = device!!

                for ((name, sz) in regularCases) {
                    val supported = testSessionSupport(d, SessionConfiguration.SESSION_REGULAR, listOf(sz))
                    testsArr.put(
                        JSONObject().apply {
                            put("name", name)
                            put("sessionType", "REGULAR")
                            put("w", sz.width)
                            put("h", sz.height)
                            put("supported", supported)
                        },
                    )
                    Log.i(SWEEP_SIGNAL_TAG, "SESS_CFG cam=$cameraId $name REGULAR supported=$supported ${sz.width}x${sz.height}")
                    onProgress("$cameraId $name REGULAR -> $supported")
                }

                if (hsFirst != null) {
                    val supportedHs = testSessionSupport(d, SessionConfiguration.SESSION_HIGH_SPEED, listOf(hsFirst))
                    testsArr.put(
                        JSONObject().apply {
                            put("name", "high_speed_first_advertised")
                            put("sessionType", "HIGH_SPEED")
                            put("w", hsFirst.width)
                            put("h", hsFirst.height)
                            put("supported", supportedHs)
                        },
                    )
                    Log.i(
                        SWEEP_SIGNAL_TAG,
                        "SESS_CFG cam=$cameraId high_speed_first HS supported=$supportedHs ${hsFirst.width}x${hsFirst.height}",
                    )
                    onProgress("$cameraId high_speed ${hsFirst.width}x${hsFirst.height} -> $supportedHs")
                }

                camJson.put("tests", testsArr)
                camsOut.put(camJson)
            } finally {
                runCatching { device?.close() }
                runCatching { ht.quitSafely() }
            }
        }

        root.put("cameras", camsOut)
        onProgress("Writing $outFileName…")
        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val out = File(dir, outFileName)
        out.writeText(root.toString(2), Charsets.UTF_8)
        Log.i(TAG, "Saved session matrix JSON: ${out.absolutePath} (${out.length()} bytes)")
        Log.i(SWEEP_SIGNAL_TAG, "SESSION_MATRIX_JSON file=${out.name} bytes=${out.length()}")
        out.absolutePath
    }

private fun testSessionSupport(
    device: CameraDevice,
    sessionType: Int,
    sizes: List<Size>,
): Boolean {
    val holders = mutableListOf<Triple<SurfaceTexture, Surface, OutputConfiguration>>()
    return try {
        val outputs = mutableListOf<OutputConfiguration>()
        for (sz in sizes) {
            val st = SurfaceTexture(0)
            st.setDefaultBufferSize(sz.width, sz.height)
            val surf = Surface(st)
            val oc = OutputConfiguration(surf)
            holders += Triple(st, surf, oc)
            outputs += oc
        }
        val config = buildSessionConfigurationCompat(sessionType, outputs)
        device.isSessionConfigurationSupported(config)
    } catch (e: Throwable) {
        Log.w(TAG, "isSessionConfigurationSupported threw: ${e.message}")
        false
    } finally {
        for ((st, surf, _) in holders) {
            runCatching { surf.release() }
            runCatching { st.release() }
        }
    }
}
