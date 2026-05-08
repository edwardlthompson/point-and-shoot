package dev.pointandshoot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.DynamicRangeProfiles
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
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
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

private const val TAG = "PNS.HdrDcgRuntime"

private object HdrDcgRuntimeRunGuard {
    val running = AtomicBoolean(false)
}

private object HdrDcgRuntimeWorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun HdrDcgRuntimeProbeScreen(
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
        if (!HdrDcgRuntimeRunGuard.running.compareAndSet(false, true)) {
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
        scanLines.add("${Instant.now()} - HDR / dynamic-range session probe (API 33+ profiles)")

        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val outFile = "hdr_dcg_session_$ts.json"

        Log.i(SWEEP_SIGNAL_TAG, "HDR_DCG_SESSION_START file=$outFile")

        lateinit var workJob: Job
        workJob = HdrDcgRuntimeWorkScope.launch {
            try {
                val savedPath = runHdrDcgRuntimeProbe(
                    context.applicationContext,
                    outFile,
                    onProgress = { msg -> scanLines.appendProbeLine(msg) },
                )
                withContext(Dispatchers.Main) {
                    status = "OK saved=$savedPath"
                    isRunning = false
                    HdrDcgRuntimeRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "HDR_DCG_SESSION_DONE file=$outFile ok=true")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        HdrDcgRuntimeRunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "HDR_DCG_SESSION_DONE file=$outFile ok=false")
                    }
                }
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "runHdrDcgRuntimeProbe failed", e)
                withContext(Dispatchers.Main) {
                    status = "FAILED: ${e::class.java.simpleName}: ${e.message}"
                    isRunning = false
                    HdrDcgRuntimeRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "HDR_DCG_SESSION_DONE file=$outFile ok=false")
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
            Button(onClick = { launch() }, enabled = !isRunning) { Text("Run HDR / DR session probe") }
        }
        Text("Phase 3: isSessionConfigurationSupported with dynamic range profiles (10-bit / HDR spots)")
        Text(status)
        ProbeLiveLogPanel(
            title = "Live scan log",
            lines = scanLines,
            modifier = Modifier.weight(1f),
        )
    }
}

@SuppressLint("NewApi")
private suspend fun runHdrDcgRuntimeProbe(
    appContext: Context,
    outFileName: String,
    onProgress: suspend (String) -> Unit = {},
): String =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 33) {
            onProgress("API < 33: dynamic range profiles not available; writing stub JSON.")
            val root = JSONObject().apply {
                put("generatedAt", Instant.now().toString())
                put("phase", 3)
                put("probe", "hdr_dcg_runtime_session")
                put("note", "requires API 33+ for REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES")
                put("cameras", JSONArray())
            }
            val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            val out = File(dir, outFileName)
            out.writeText(root.toString(2), Charsets.UTF_8)
            Log.i(SWEEP_SIGNAL_TAG, "HDR_DCG_SESSION_JSON file=${out.name} bytes=${out.length()}")
            return@withContext out.absolutePath
        }

        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        onProgress("Cameras: ${cameraIds.joinToString(",")} (${cameraIds.size})")

        val root = JSONObject()
        root.put("generatedAt", Instant.now().toString())
        root.put("phase", 3)
        root.put("probe", "hdr_dcg_runtime_session")
        root.put("device", JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("sdkInt", Build.VERSION.SDK_INT)
        })

        val camsOut = JSONArray()
        for ((idx, cameraId) in cameraIds.withIndex()) {
            onProgress("--- Camera $cameraId (${idx + 1}/${cameraIds.size}) ---")
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
            val previewSize = pickSize(1280, 720) ?: pickSize(640, 480)

            if (previewSize == null) {
                camJson.put("error", "no_preview_sizes")
                camsOut.put(camJson)
                continue
            }

            val drp = runCatching {
                cc.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES) as? DynamicRangeProfiles
            }.getOrNull()
            val profileLongs = runCatching {
                drp?.supportedProfiles?.toList()?.sorted()?.distinct() ?: emptyList()
            }.getOrDefault(emptyList())
            camJson.put("advertisedProfiles", JSONArray().apply { profileLongs.forEach { put(it) } })

            val recTenBit = runCatching {
                cc.get(CameraCharacteristics.REQUEST_RECOMMENDED_TEN_BIT_DYNAMIC_RANGE_PROFILE)
            }.getOrNull()
            if (recTenBit != null) {
                camJson.put("recommendedTenBitProfile", recTenBit)
            }

            val ht = HandlerThread("PNS.HdrDcg-$cameraId")
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

                for (p in profileLongs) {
                    if (recTenBit != null && p == recTenBit) continue
                    val supported = isSessionSupportedWithDynamicRange(
                        d,
                        SessionConfiguration.SESSION_REGULAR,
                        previewSize,
                        p,
                    )
                    testsArr.put(
                        JSONObject().apply {
                            put("name", "profile_$p")
                            put("dynamicRangeProfile", p)
                            put("sessionType", "REGULAR")
                            put("w", previewSize.width)
                            put("h", previewSize.height)
                            put("supported", supported)
                        },
                    )
                    Log.i(
                        SWEEP_SIGNAL_TAG,
                        "HDR_DCG_ROW cam=$cameraId profile=$p REGULAR supported=$supported ${previewSize.width}x${previewSize.height}",
                    )
                    onProgress("$cameraId profile=$p -> $supported (${previewSize.width}x${previewSize.height})")
                }

                if (recTenBit != null) {
                    val supportedTen = isSessionSupportedWithDynamicRange(
                        d,
                        SessionConfiguration.SESSION_REGULAR,
                        previewSize,
                        recTenBit,
                    )
                    testsArr.put(
                        JSONObject().apply {
                            put("name", "recommended_ten_bit")
                            put("dynamicRangeProfile", recTenBit)
                            put("sessionType", "REGULAR")
                            put("w", previewSize.width)
                            put("h", previewSize.height)
                            put("supported", supportedTen)
                            put("spot", "ten_bit_recommended")
                        },
                    )
                    Log.i(
                        SWEEP_SIGNAL_TAG,
                        "HDR_DCG_ROW cam=$cameraId profile=$recTenBit REGULAR spot=ten_bit_recommended supported=$supportedTen ${previewSize.width}x${previewSize.height}",
                    )
                    onProgress("$cameraId recommended_ten_bit -> $supportedTen")
                }

                // Phase 4: YUV_420_888 + dynamic range (processing / 10-bit path beyond SurfaceTexture preview)
                val yuvSizes = runCatching {
                    map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()
                }.getOrDefault(emptyList())
                val yuvSize = yuvSizes.minByOrNull {
                    abs(it.width - previewSize.width) + abs(it.height - previewSize.height)
                } ?: yuvSizes.minByOrNull { it.width * it.height }
                if (yuvSize != null) {
                    camJson.put(
                        "phase4YuvSize",
                        JSONObject().apply {
                            put("w", yuvSize.width)
                            put("h", yuvSize.height)
                        },
                    )
                    val stdOk = isSessionSupportedWithDynamicRangeImageReader(
                        d,
                        SessionConfiguration.SESSION_REGULAR,
                        yuvSize,
                        ImageFormat.YUV_420_888,
                        DynamicRangeProfiles.STANDARD,
                    )
                    testsArr.put(
                        JSONObject().apply {
                            put("name", "yuv420_standard_dr")
                            put("dynamicRangeProfile", DynamicRangeProfiles.STANDARD)
                            put("sessionType", "REGULAR")
                            put("format", "YUV_420_888")
                            put("w", yuvSize.width)
                            put("h", yuvSize.height)
                            put("supported", stdOk)
                            put("spot", "phase4_yuv_processing")
                        },
                    )
                    Log.i(
                        SWEEP_SIGNAL_TAG,
                        "HDR_DCG_ROW cam=$cameraId profile=STANDARD YUV420 REGULAR supported=$stdOk ${yuvSize.width}x${yuvSize.height}",
                    )
                    if (recTenBit != null) {
                        val yuvTenOk = isSessionSupportedWithDynamicRangeImageReader(
                            d,
                            SessionConfiguration.SESSION_REGULAR,
                            yuvSize,
                            ImageFormat.YUV_420_888,
                            recTenBit,
                        )
                        testsArr.put(
                            JSONObject().apply {
                                put("name", "yuv420_recommended_ten_bit_dr")
                                put("dynamicRangeProfile", recTenBit)
                                put("sessionType", "REGULAR")
                                put("format", "YUV_420_888")
                                put("w", yuvSize.width)
                                put("h", yuvSize.height)
                                put("supported", yuvTenOk)
                                put("spot", "phase4_yuv_ten_bit")
                            },
                        )
                        Log.i(
                            SWEEP_SIGNAL_TAG,
                            "HDR_DCG_ROW cam=$cameraId profile=$recTenBit YUV420 REGULAR spot=phase4_yuv_ten_bit supported=$yuvTenOk ${yuvSize.width}x${yuvSize.height}",
                        )
                    }
                    onProgress("$cameraId Phase4 YUV ${yuvSize.width}x${yuvSize.height}")
                } else {
                    camJson.put("phase4YuvSkipped", "no_yuv_sizes")
                }

                camJson.put("tests", testsArr)
                camsOut.put(camJson)
            } finally {
                runCatching { device?.close() }
                runCatching { ht.quitSafely() }
            }
        }

        root.put("cameras", camsOut)
        onProgress("Writing $outFileName...")
        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val out = File(dir, outFileName)
        out.writeText(root.toString(2), Charsets.UTF_8)
        Log.i(TAG, "Saved HDR/DCG runtime JSON: ${out.absolutePath} (${out.length()} bytes)")
        Log.i(SWEEP_SIGNAL_TAG, "HDR_DCG_SESSION_JSON file=${out.name} bytes=${out.length()}")
        out.absolutePath
    }
