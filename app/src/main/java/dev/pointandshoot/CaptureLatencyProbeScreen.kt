package dev.pointandshoot

import dev.pointandshoot.preview.createCaptureSessionHighSpeedOutputs
import dev.pointandshoot.preview.createCaptureSessionRegularOutputs
import dev.pointandshoot.preview.outputConfigurationsWithOptionalStreamUseCases

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PNS.CaptureLatency"

private object CaptureLatencyRunGuard {
    val running = AtomicBoolean(false)
}

private object CaptureLatencyWorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun CaptureLatencyProbeScreen(
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
        if (!CaptureLatencyRunGuard.running.compareAndSet(false, true)) {
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
        scanLines.add("${Instant.now()} - Still capture latency (Phase 5)")
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val outFile = "capture_latency_$ts.json"
        Log.i(SWEEP_SIGNAL_TAG, "CAPTURE_LATENCY_START file=$outFile")
        lateinit var workJob: Job
        workJob = CaptureLatencyWorkScope.launch {
            try {
                val path = runCaptureLatencyProbe(context.applicationContext, outFile) { scanLines.appendProbeLine(it) }
                withContext(Dispatchers.Main) {
                    status = "OK saved=$path"
                    isRunning = false
                    CaptureLatencyRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "CAPTURE_LATENCY_DONE file=$outFile ok=true")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        CaptureLatencyRunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "CAPTURE_LATENCY_DONE file=$outFile ok=false")
                    }
                }
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "probe failed", e)
                withContext(Dispatchers.Main) {
                    status = "FAILED: ${e.message}"
                    isRunning = false
                    CaptureLatencyRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "CAPTURE_LATENCY_DONE file=$outFile ok=false")
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
            Button(onClick = { launch() }, enabled = !isRunning) { Text("Run capture latency") }
        }
        Text("Phase 5: JPEG still latency, ZSL on/off, zero-shutter-lag template, reprocess caps")
        Text(status)
        ProbeLiveLogPanel("Live log", scanLines, Modifier.weight(1f))
    }
}

private suspend fun runCaptureLatencyProbe(
    appContext: Context,
    outFileName: String,
    onProgress: suspend (String) -> Unit,
): String =
    withContext(Dispatchers.IO) {
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        val root = JSONObject().apply {
            put("generatedAt", Instant.now().toString())
            put("phase", 5)
            put("probe", "capture_latency_still_jpeg_zsl_reprocess")
            put(
                "probeScope",
                JSONObject().apply {
                    put("stillJpegLatencyMs_totalCaptureResultCallback", true)
                    put("zslToggleLatencyMs_whenControlEnableZslAvailable", true)
                    put("zeroShutterLagTemplateLatencyMs", true)
                    put("staticYuvPrivateReprocessCapabilityFlags", true)
                    put("reprocessInputToJpegSessionSupported_queryOnly", true)
                    put("fullReprocessPipelineLatency_endToEnd", false)
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

        for (camId in cameraIds) {
            onProgress("--- camera $camId ---")
            val camJson = JSONObject().put("cameraId", camId)
            val tests = JSONArray()
            val cc = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
            if (cc == null) {
                camJson.put("error", "no_characteristics")
                cams.put(camJson)
                continue
            }
            val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = runCatching { map?.getOutputSizes(ImageFormat.JPEG)?.toList() }.getOrNull().orEmpty()
            val size = jpegSizes.minByOrNull { it.width * it.height } ?: Size(640, 480)
            val caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            camJson.put(
                "yuvReprocessingCapability",
                caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING },
            )
            camJson.put(
                "privateReprocessingCapability",
                caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING },
            )
            val zslKeyAvail = cc.availableCaptureRequestKeys?.contains(CaptureRequest.CONTROL_ENABLE_ZSL) == true
            camJson.put("controlEnableZslInRequestKeys", zslKeyAvail)

            runCatching {
                cc.get(CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH)?.toInt()
            }.getOrNull()?.let { camJson.put("requestPipelineMaxDepth", it) }

            val ht = HandlerThread("PNS.CapLat-$camId")
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
                if (map != null) {
                    appendReprocessSessionProbe(camJson, tests, d, cc, map, size, h)
                } else {
                    camJson.put("reprocessProbeNote", "no_stream_configuration_map")
                }
                reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
                val surf = reader!!.surface
                val sessLatch = CountDownLatch(1)
                d.createCaptureSessionRegularOutputs(
                    listOf(surf),
                    h,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            session = s
                            sessLatch.countDown()
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) {
                            sessLatch.countDown()
                        }
                    },
                )
                if (!sessLatch.await(6, TimeUnit.SECONDS) || session == null) {
                    camJson.put("sessionError", "timeout")
                    cams.put(camJson)
                    continue
                }
                val sess = session!!

                fun measureOnce(enableZsl: Boolean?): Long? {
                    val done = CountDownLatch(1)
                    val endNs = AtomicLong(0L)
                    val req = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(surf)
                        if (enableZsl == true && zslKeyAvail) {
                            set(CaptureRequest.CONTROL_ENABLE_ZSL, true)
                        } else if (enableZsl == false && zslKeyAvail) {
                            set(CaptureRequest.CONTROL_ENABLE_ZSL, false)
                        }
                    }.build()
                    val startNs = SystemClock.elapsedRealtimeNanos()
                    runCatching {
                        sess.capture(
                            req,
                            object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: android.hardware.camera2.TotalCaptureResult,
                                ) {
                                    endNs.set(SystemClock.elapsedRealtimeNanos())
                                    done.countDown()
                                }
                                override fun onCaptureFailed(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    failure: android.hardware.camera2.CaptureFailure,
                                ) {
                                    done.countDown()
                                }
                            },
                            h,
                        )
                    }.onFailure { done.countDown() }
                    if (!done.await(12, TimeUnit.SECONDS)) return null
                    val e = endNs.get()
                    if (e == 0L) return null
                    return (e - startNs) / 1_000_000L
                }

                fun measureTemplate(template: Int, label: String): Long? {
                    val req = runCatching {
                        d.createCaptureRequest(template).apply { addTarget(surf) }.build()
                    }.getOrNull() ?: return null
                    val done = CountDownLatch(1)
                    val endNs = AtomicLong(0L)
                    val startNs = SystemClock.elapsedRealtimeNanos()
                    runCatching {
                        sess.capture(
                            req,
                            object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: android.hardware.camera2.TotalCaptureResult,
                                ) {
                                    endNs.set(SystemClock.elapsedRealtimeNanos())
                                    done.countDown()
                                }

                                override fun onCaptureFailed(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    failure: android.hardware.camera2.CaptureFailure,
                                ) {
                                    done.countDown()
                                }
                            },
                            h,
                        )
                    }.onFailure { done.countDown() }
                    if (!done.await(12, TimeUnit.SECONDS)) return null
                    val e = endNs.get()
                    if (e == 0L) return null
                    val ms = (e - startNs) / 1_000_000L
                    Log.i(SWEEP_SIGNAL_TAG, "CAP_LAT cam=$camId ${label}_ms=$ms")
                    return ms
                }

                val baselineMs = measureOnce(null)
                tests.put(JSONObject().apply {
                    put("name", "still_jpeg_baseline")
                    put("w", size.width)
                    put("h", size.height)
                    put("latencyMs", baselineMs ?: JSONObject.NULL)
                    put("ok", baselineMs != null)
                })
                Log.i(SWEEP_SIGNAL_TAG, "CAP_LAT cam=$camId baseline_ms=${baselineMs ?: -1}")
                onProgress("$camId baseline ${baselineMs ?: "fail"} ms")

                if (zslKeyAvail) {
                    val zslOffMs = measureOnce(false)
                    tests.put(JSONObject().apply {
                        put("name", "still_jpeg_zsl_false")
                        put("w", size.width)
                        put("h", size.height)
                        put("latencyMs", zslOffMs ?: JSONObject.NULL)
                        put("ok", zslOffMs != null)
                    })
                    Log.i(SWEEP_SIGNAL_TAG, "CAP_LAT cam=$camId zsl_false_ms=${zslOffMs ?: -1}")
                    onProgress("$camId ZSL=false ${zslOffMs ?: "fail"} ms")

                    val zslOnMs = measureOnce(true)
                    tests.put(JSONObject().apply {
                        put("name", "still_jpeg_zsl_true")
                        put("w", size.width)
                        put("h", size.height)
                        put("latencyMs", zslOnMs ?: JSONObject.NULL)
                        put("ok", zslOnMs != null)
                    })
                    Log.i(SWEEP_SIGNAL_TAG, "CAP_LAT cam=$camId zsl_true_ms=${zslOnMs ?: -1}")
                    onProgress("$camId ZSL=true ${zslOnMs ?: "fail"} ms")
                }

                val zslTemplateMs = measureTemplate(
                    CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG,
                    "zsl_template",
                )
                tests.put(JSONObject().apply {
                    put("name", "still_jpeg_template_zero_shutter_lag")
                    put("w", size.width)
                    put("h", size.height)
                    put("latencyMs", zslTemplateMs ?: JSONObject.NULL)
                    put("ok", zslTemplateMs != null)
                })

                camJson.put("tests", tests)
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
        Log.i(SWEEP_SIGNAL_TAG, "CAPTURE_LATENCY_JSON file=${out.name} bytes=${out.length()}")
        onProgress("Wrote ${out.name}")
        out.absolutePath
    }

@SuppressLint("NewApi")
private fun appendReprocessSessionProbe(
    camJson: JSONObject,
    tests: JSONArray,
    device: CameraDevice,
    cc: CameraCharacteristics,
    map: android.hardware.camera2.params.StreamConfigurationMap,
    jpegSize: Size,
    h: Handler,
) {
    if (android.os.Build.VERSION.SDK_INT < 31) {
        camJson.put("reprocessInputToJpegSessionSupported", false)
        tests.put(
            JSONObject().apply {
                put("name", "reprocess_input_to_jpeg_session")
                put("ok", false)
                put("reason", "requires_api_31")
            },
        )
        return
    }
    val formats = runCatching { map.inputFormats }.getOrNull() ?: intArrayOf()
    val fmtNames = JSONArray()
    for (f in formats) {
        fmtNames.put(imageFormatLabel(f))
    }
    camJson.put("reprocessInputFormats", fmtNames)
    camJson.put("reprocessInputFormatIds", JSONArray().apply { formats.forEach { put(it) } })

    val chosenFormat = when {
        formats.any { it == ImageFormat.YUV_420_888 } -> ImageFormat.YUV_420_888
        formats.isNotEmpty() -> formats[0]
        else -> {
            tests.put(
                JSONObject().apply {
                    put("name", "reprocess_input_to_jpeg_session")
                    put("ok", false)
                    put("reason", "no_input_formats")
                },
            )
            return
        }
    }
    val inputSizes = map.getInputSizes(chosenFormat) ?: emptyArray()
    val inSize = inputSizes.minByOrNull { it.width * it.height } ?: run {
        tests.put(
            JSONObject().apply {
                put("name", "reprocess_input_to_jpeg_session")
                put("ok", false)
                put("reason", "no_input_sizes")
                put("format", imageFormatLabel(chosenFormat))
            },
        )
        return
    }
    camJson.put(
        "reprocessProbeInput",
        JSONObject().apply {
            put("format", imageFormatLabel(chosenFormat))
            put("formatId", chosenFormat)
            put("w", inSize.width)
            put("h", inSize.height)
        },
    )

    val jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2)
    try {
        val oc = OutputConfiguration(jpegReader.surface)
        val ic = android.hardware.camera2.params.InputConfiguration(inSize.width, inSize.height, chosenFormat)
        val executor = Executor { r -> h.post(r) }
        val cfg = runCatching {
            buildSessionConfigurationWithInput(
                SessionConfiguration.SESSION_REGULAR,
                listOf(oc),
                ic,
                executor,
            )
        }.getOrElse { e ->
            camJson.put("reprocessInputToJpegSessionSupported", false)
            tests.put(
                JSONObject().apply {
                    put("name", "reprocess_input_to_jpeg_session")
                    put("ok", false)
                    put("reason", "session_configuration_build_failed")
                    put("detail", e.message ?: e.javaClass.simpleName)
                },
            )
            Log.w(TAG, "reprocess session config build failed", e)
            return
        }
        val supported = runCatching { device.isSessionConfigurationSupported(cfg) }.getOrDefault(false)
        camJson.put("reprocessInputToJpegSessionSupported", supported)
        tests.put(
            JSONObject().apply {
                put("name", "reprocess_input_to_jpeg_session")
                put("inputFormat", imageFormatLabel(chosenFormat))
                put("inputW", inSize.width)
                put("inputH", inSize.height)
                put("jpegW", jpegSize.width)
                put("jpegH", jpegSize.height)
                put("ok", supported)
            },
        )
        Log.i(
            SWEEP_SIGNAL_TAG,
            "CAP_LAT reprocess_session supported=$supported inFmt=${imageFormatLabel(chosenFormat)} ${inSize.width}x${inSize.height}",
        )
    } finally {
        runCatching { jpegReader.close() }
    }
}

private fun imageFormatLabel(format: Int): String =
    when (format) {
        ImageFormat.YUV_420_888 -> "YUV_420_888"
        ImageFormat.JPEG -> "JPEG"
        ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
        ImageFormat.RAW10 -> "RAW10"
        ImageFormat.PRIVATE -> "PRIVATE"
        ImageFormat.NV21 -> "NV21"
        ImageFormat.YUV_422_888 -> "YUV_422_888"
        ImageFormat.YUV_444_888 -> "YUV_444_888"
        else -> "FORMAT_$format"
    }
