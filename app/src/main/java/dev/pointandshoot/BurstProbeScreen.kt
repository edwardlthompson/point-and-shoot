package dev.pointandshoot

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Size
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PNS.Burst"
private const val BURST_COUNT = 8

private fun drainImageReader(ir: ImageReader?) {
    ir ?: return
    while (true) {
        val img = ir.acquireLatestImage() ?: break
        img.close()
    }
}

private object BurstRunGuard {
    val running = AtomicBoolean(false)
}

private object BurstWorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun BurstProbeScreen(
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
        onDispose { workJobRef.getAndSet(null)?.cancel() }
    }

    fun launch() {
        if (isRunning) return
        if (!BurstRunGuard.running.compareAndSet(false, true)) {
            status = "Already running (guarded)."
            return
        }
        isRunning = true
        status = "Running..."
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
        scanLines.add("${Instant.now()} - Burst + AE bracket (Phase 7, n=$BURST_COUNT)")
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val outFile = "burst_probe_$ts.json"
        Log.i(SWEEP_SIGNAL_TAG, "BURST_PROBE_START file=$outFile")
        lateinit var workJob: Job
        workJob = BurstWorkScope.launch {
            try {
                val path = runBurstProbe(context.applicationContext, outFile) { scanLines.appendProbeLine(it) }
                withContext(Dispatchers.Main) {
                    status = "OK saved=$path"
                    isRunning = false
                    BurstRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "BURST_PROBE_DONE file=$outFile ok=true")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        BurstRunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "BURST_PROBE_DONE file=$outFile ok=false")
                    }
                }
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "probe failed", e)
                withContext(Dispatchers.Main) {
                    status = "FAILED: ${e.message}"
                    isRunning = false
                    BurstRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "BURST_PROBE_DONE file=$outFile ok=false")
                }
            } finally {
                workJobRef.compareAndSet(workJob, null)
            }
        }
        workJobRef.set(workJob)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(insets.asPaddingValues(extra = 16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = { launch() }, enabled = !isRunning) { Text("Run burst probe") }
        }
        Text("Phase 7: JPEG burst ($BURST_COUNT) + AE compensation bracket (min/mid/max index)")
        Text(status)
        ProbeLiveLogPanel("Live log", scanLines, Modifier.weight(1f))
    }
}

private suspend fun runBurstProbe(
    appContext: Context,
    outFileName: String,
    onProgress: suspend (String) -> Unit,
): String =
    withContext(Dispatchers.IO) {
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        val root = JSONObject().apply {
            put("generatedAt", Instant.now().toString())
            put("phase", 7)
            put("probe", "still_jpeg_burst_ae_bracket")
            put("burstCount", BURST_COUNT)
            put(
                "probeScope",
                JSONObject().apply {
                    put("jpegCaptureBurst_wallMs_perShot_approxFps", true)
                    put("aeCompensationBracket_minMidMaxIndices", true)
                    put("burstAndAeRelatedMetadataKeyNames", true)
                    put("vendorSpecificBurstLimitKeys", false)
                },
            )
            put("device", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("sdkInt", Build.VERSION.SDK_INT)
            })
            put("cameras", JSONArray())
        }
        val cams = root.getJSONArray("cameras")
        onProgress("Cameras: ${cameraIds.joinToString(",")}")

        camLoop@ for (camId in cameraIds) {
            onProgress("--- camera $camId ---")
            val camJson = JSONObject().put("cameraId", camId)
            val cc = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
            if (cc == null) {
                camJson.put("error", "no_characteristics")
                cams.put(camJson)
                continue
            }
            val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = runCatching { map?.getOutputSizes(ImageFormat.JPEG)?.toList() }.getOrNull().orEmpty()
            val caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val burstCap = caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE }
            camJson.put("burstCaptureCapability", burstCap)
            val size = jpegSizes.minByOrNull { it.width * it.height } ?: Size(640, 480)

            runCatching {
                val keys = cc.availableCaptureRequestKeys?.toList().orEmpty()
                val filtered = keys.mapNotNull { k -> runCatching { k.name }.getOrNull() }
                    .filter { n ->
                        n.contains("AE", ignoreCase = true) ||
                            n.contains("BRACKET", ignoreCase = true) ||
                            n.contains("EXPOSURE", ignoreCase = true)
                    }
                    .distinct()
                    .sorted()
                    .take(128)
                camJson.put("captureRequestKeyNamesAeExposureBracketish", JSONArray(filtered))
            }.onFailure {
                camJson.put(
                    "captureRequestKeyNamesAeExposureBracketishError",
                    it.message ?: "error",
                )
            }

            if (Build.VERSION.SDK_INT >= 28) {
                runCatching {
                    val sk = cc.availableSessionKeys?.toList().orEmpty()
                    val names = sk.mapNotNull { k -> runCatching { k.name }.getOrNull() }
                        .filter { n ->
                            n.contains("AE", ignoreCase = true) ||
                                n.contains("BRACKET", ignoreCase = true) ||
                                n.contains("EXPOSURE", ignoreCase = true)
                        }
                        .distinct()
                        .sorted()
                        .take(64)
                    camJson.put("sessionKeyNamesAeExposureBracketish", JSONArray(names))
                }.onFailure {
                    camJson.put("sessionKeyNamesAeExposureBracketishError", it.message ?: "error")
                }
            }

            val ht = HandlerThread("PNS.Burst-$camId")
            ht.start()
            val h = Handler(ht.looper)
            var device: CameraDevice? = null
            var session: CameraCaptureSession? = null
            var reader: ImageReader? = null
            try {
                val openLatch = CountDownLatch(1)
                cm.openCamera(
                    camId,
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
                    camJson.put("openError", "timeout")
                    cams.put(camJson)
                    continue
                }
                val d = device!!
                reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, BURST_COUNT + 2)
                reader!!.setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, h)
                val surf = reader!!.surface
                val sessLatch = CountDownLatch(1)
                d.createCaptureSession(
                    listOf(surf),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            session = s
                            sessLatch.countDown()
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) {
                            sessLatch.countDown()
                        }
                    },
                    h,
                )
                if (!sessLatch.await(6, TimeUnit.SECONDS) || session == null) {
                    camJson.put("sessionError", "timeout")
                    cams.put(camJson)
                    continue
                }
                val sess = session!!
                val requests = List(BURST_COUNT) {
                    d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(surf)
                    }.build()
                }
                val completed = AtomicInteger(0)
                val failed = AtomicInteger(0)
                val done = CountDownLatch(BURST_COUNT)
                val t0 = SystemClock.elapsedRealtimeNanos()
                val burstOk = runCatching {
                    sess.captureBurst(
                        requests,
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: android.hardware.camera2.TotalCaptureResult,
                            ) {
                                completed.incrementAndGet()
                                done.countDown()
                            }
                            override fun onCaptureFailed(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: android.hardware.camera2.CaptureFailure,
                            ) {
                                failed.incrementAndGet()
                                done.countDown()
                            }
                        },
                        h,
                    )
                }
                if (burstOk.isFailure) {
                    camJson.put("burstSubmitError", burstOk.exceptionOrNull()?.message)
                    cams.put(camJson)
                    continue@camLoop
                }
                val finished = done.await(20, TimeUnit.SECONDS)
                val wallMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000L
                camJson.put("completed", completed.get())
                camJson.put("failed", failed.get())
                camJson.put("wallMs", wallMs)
                camJson.put(
                    "burstWallMsPerShot",
                    if (BURST_COUNT > 0) wallMs.toDouble() / BURST_COUNT else JSONObject.NULL,
                )
                camJson.put(
                    "approxBurstFps",
                    if (wallMs > 0) BURST_COUNT * 1000.0 / wallMs else JSONObject.NULL,
                )
                camJson.put("awaitFinished", finished)
                camJson.put("w", size.width)
                camJson.put("h", size.height)
                Log.i(
                    SWEEP_SIGNAL_TAG,
                    "BURST_ROW cam=$camId ok=${completed.get()}/${BURST_COUNT} failed=${failed.get()} wallMs=$wallMs",
                )
                onProgress("$camId burst ${completed.get()}/${BURST_COUNT} in ${wallMs}ms")

                drainImageReader(reader)
                val aeRange: Range<Int>? = cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                val aeStep = cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                val bracketJa = JSONArray()
                if (aeRange != null && aeStep != null && aeRange.lower < aeRange.upper) {
                    val lo = aeRange.lower
                    val hi = aeRange.upper
                    val mid = (lo + hi) / 2
                    val indices = listOf(lo, mid, hi).distinct().sorted()
                    camJson.put("aeCompensationRange", JSONObject().apply {
                        put("lower", lo)
                        put("upper", hi)
                        put("stepNumerator", aeStep.numerator)
                        put("stepDenominator", aeStep.denominator)
                    })
                    for (evIdx in indices.take(3)) {
                        val latch = CountDownLatch(1)
                        val tB0 = SystemClock.elapsedRealtimeNanos()
                        val capReq = runCatching {
                            d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                addTarget(surf)
                                set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evIdx)
                            }.build()
                        }.getOrNull()
                        if (capReq == null) {
                            bracketJa.put(JSONObject().apply {
                                put("aeCompensationIndex", evIdx)
                                put("error", "build_request_failed")
                            })
                            continue
                        }
                        val submit = runCatching {
                            sess.capture(
                                capReq,
                                object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureCompleted(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: android.hardware.camera2.TotalCaptureResult,
                                    ) {
                                        latch.countDown()
                                    }

                                    override fun onCaptureFailed(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        failure: android.hardware.camera2.CaptureFailure,
                                    ) {
                                        latch.countDown()
                                    }
                                },
                                h,
                            )
                        }
                        if (submit.isFailure) {
                            bracketJa.put(JSONObject().apply {
                                put("aeCompensationIndex", evIdx)
                                put("error", submit.exceptionOrNull()?.message)
                            })
                            continue
                        }
                        val okWait = latch.await(10, TimeUnit.SECONDS)
                        val bMs = (SystemClock.elapsedRealtimeNanos() - tB0) / 1_000_000L
                        drainImageReader(reader)
                        bracketJa.put(JSONObject().apply {
                            put("aeCompensationIndex", evIdx)
                            put("wallMs", bMs)
                            put("captureCompleted", okWait)
                        })
                    }
                    camJson.put("aeCompensationBracket", bracketJa)
                    Log.i(
                        SWEEP_SIGNAL_TAG,
                        "BURST_BRACKET_ROW cam=$camId shots=${bracketJa.length()} range=[$lo,$hi]",
                    )
                    onProgress("$camId AE bracket ${bracketJa.length()} spots")
                } else {
                    camJson.put("aeCompensationBracket", JSONArray())
                    camJson.put("aeBracketSkipped", "range_or_step_unavailable_or_flat")
                }

                cams.put(camJson)
            } finally {
                runCatching { session?.close() }
                runCatching { reader?.close() }
                runCatching { device?.close() }
                runCatching { ht.quitSafely() }
            }
        }

        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val out = File(dir, outFileName)
        out.writeText(root.toString(2), Charsets.UTF_8)
        Log.i(SWEEP_SIGNAL_TAG, "BURST_PROBE_JSON file=${out.name} bytes=${out.length()}")
        onProgress("Wrote ${out.name}")
        out.absolutePath
    }
