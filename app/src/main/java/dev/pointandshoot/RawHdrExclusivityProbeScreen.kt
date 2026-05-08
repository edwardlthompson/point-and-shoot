package dev.pointandshoot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.DynamicRangeProfiles
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
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

private const val TAG = "PNS.RawHdrExcl"

private object RawHdrRunGuard {
    val running = AtomicBoolean(false)
}

private object RawHdrWorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun RawHdrExclusivityProbeScreen(
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
        if (!RawHdrRunGuard.running.compareAndSet(false, true)) {
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
        scanLines.add("${Instant.now()} - RAW + preview session matrix (Phase 6)")
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val outFile = "raw_hdr_exclusivity_$ts.json"
        Log.i(SWEEP_SIGNAL_TAG, "RAW_HDR_EXCL_START file=$outFile")
        lateinit var workJob: Job
        workJob = RawHdrWorkScope.launch {
            try {
                val path = runRawHdrExclusivityProbe(context.applicationContext, outFile) { scanLines.appendProbeLine(it) }
                withContext(Dispatchers.Main) {
                    status = "OK saved=$path"
                    isRunning = false
                    RawHdrRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "RAW_HDR_EXCL_DONE file=$outFile ok=true")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        RawHdrRunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "RAW_HDR_EXCL_DONE file=$outFile ok=false")
                    }
                }
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "probe failed", e)
                withContext(Dispatchers.Main) {
                    status = "FAILED: ${e.message}"
                    isRunning = false
                    RawHdrRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "RAW_HDR_EXCL_DONE file=$outFile ok=false")
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
            Button(onClick = { launch() }, enabled = !isRunning) { Text("Run RAW/HDR exclusivity") }
        }
        Text("Phase 6: SurfaceTexture / YUV preview + RAW, DR and recommended 10-bit on preview")
        Text(status)
        ProbeLiveLogPanel("Live log", scanLines, Modifier.weight(1f))
    }
}

@SuppressLint("NewApi")
private suspend fun runRawHdrExclusivityProbe(
    appContext: Context,
    outFileName: String,
    onProgress: suspend (String) -> Unit,
): String =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 28) {
            val root = JSONObject().apply {
                put("note", "OutputConfiguration multi-output path prefers API 28+")
                put(
                    "probeScope",
                    JSONObject().apply {
                        put("sessionConfigMatrix", false)
                        put("reason", "requiresApi28")
                    },
                )
                put("cameras", JSONArray())
            }
            val out = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, outFileName)
            out.writeText(root.toString(2), Charsets.UTF_8)
            return@withContext out.absolutePath
        }

        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        val root = JSONObject().apply {
            put("generatedAt", Instant.now().toString())
            put("phase", 6)
            put("probe", "raw_hdr_session_exclusivity_dr_enum")
            put(
                "probeScope",
                JSONObject().apply {
                    put("sessionConfigMatrix_previewPlusRaw", true)
                    put("sessionConfigMatrix_yuvPreviewPlusRaw", true)
                    put("dynamicRangeOnPreviewEnumPerProfile_whenApi33", true)
                    put("perCameraSessionSupportSummary", true)
                    put("exhaustiveHalExclusionDocumentation", false)
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

        fun pickRawSize(map: android.hardware.camera2.params.StreamConfigurationMap?): Size? {
            if (map == null) return null
            val raw = runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() }.getOrNull().orEmpty()
            if (raw.isNotEmpty()) return raw.minByOrNull { it.width * it.height }
            val raw10 = runCatching { map.getOutputSizes(ImageFormat.RAW10)?.toList() }.getOrNull().orEmpty()
            return raw10.minByOrNull { it.width * it.height }
        }

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
            val caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val hasRaw = caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW }
            camJson.put("rawCapability", hasRaw)
            val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val rawSize = pickRawSize(map)
            val previewSizes = runCatching {
                map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
            }.getOrDefault(emptyList())
            fun pickPreview(): Size? =
                previewSizes.minByOrNull { abs(it.width - 1280) + abs(it.height - 720) }
                    ?: previewSizes.minByOrNull { it.width * it.height }

            val previewSize = pickPreview()
            if (rawSize == null || previewSize == null) {
                camJson.put("skipReason", if (rawSize == null) "no_raw_sizes" else "no_preview_sizes")
                camJson.put("tests", tests)
                cams.put(camJson)
                continue
            }

            var recommendedTenBitDr: Long? = null
            if (Build.VERSION.SDK_INT >= 33) {
                recommendedTenBitDr = runCatching {
                    cc.get(CameraCharacteristics.REQUEST_RECOMMENDED_TEN_BIT_DYNAMIC_RANGE_PROFILE)
                }.getOrNull()
                camJson.put("recommendedTenBitDynamicRangeProfile", recommendedTenBitDr ?: JSONObject.NULL)
            }

            val ht = HandlerThread("PNS.RawHdr-$camId")
            ht.start()
            val h = Handler(ht.looper)
            var device: CameraDevice? = null
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

                fun testCombo(name: String, drOnPreview: Long?): Boolean {
                    val st = SurfaceTexture(0)
                    val prevSurf = Surface(st)
                    return try {
                        st.setDefaultBufferSize(previewSize.width, previewSize.height)
                        val ocP = OutputConfiguration(prevSurf)
                        if (Build.VERSION.SDK_INT >= 33 && drOnPreview != null) {
                            runCatching { ocP.setDynamicRangeProfile(drOnPreview) }
                        }
                        val ocR = OutputConfiguration(rawSize, ImageReader::class.java)
                        val cfg = buildSessionConfigurationCompat(
                            SessionConfiguration.SESSION_REGULAR,
                            listOf(ocP, ocR),
                        )
                        d.isSessionConfigurationSupported(cfg)
                    } catch (e: Throwable) {
                        Log.w(TAG, "$name: ${e.message}")
                        false
                    } finally {
                        runCatching { prevSurf.release() }
                        runCatching { st.release() }
                    }
                }

                fun testYuvPreviewRaw(drOnPreview: Long?): Boolean {
                    val ir = ImageReader.newInstance(
                        previewSize.width,
                        previewSize.height,
                        ImageFormat.YUV_420_888,
                        2,
                    )
                    val prevSurf = ir.surface
                    return try {
                        val ocP = OutputConfiguration(prevSurf)
                        if (Build.VERSION.SDK_INT >= 33 && drOnPreview != null) {
                            runCatching { ocP.setDynamicRangeProfile(drOnPreview) }
                        }
                        val ocR = OutputConfiguration(rawSize, ImageReader::class.java)
                        val cfg = buildSessionConfigurationCompat(
                            SessionConfiguration.SESSION_REGULAR,
                            listOf(ocP, ocR),
                        )
                        d.isSessionConfigurationSupported(cfg)
                    } catch (e: Throwable) {
                        Log.w(TAG, "yuv_preview_raw: ${e.message}")
                        false
                    } finally {
                        runCatching { ir.close() }
                    }
                }

                val plain = testCombo("preview_raw", null)
                tests.put(JSONObject().apply {
                    put("name", "preview_plus_raw")
                    put("dynamicRangeOnPreview", JSONObject.NULL)
                    put("supported", plain)
                })
                Log.i(SWEEP_SIGNAL_TAG, "RAW_HDR_ROW cam=$camId preview_plus_raw supported=$plain")
                onProgress("$camId preview+RAW plain -> $plain")

                val yuvPlain = testYuvPreviewRaw(null)
                tests.put(JSONObject().apply {
                    put("name", "yuv_preview_plus_raw")
                    put("dynamicRangeOnPreview", JSONObject.NULL)
                    put("supported", yuvPlain)
                })
                Log.i(SWEEP_SIGNAL_TAG, "RAW_HDR_ROW cam=$camId yuv_preview_plus_raw supported=$yuvPlain")
                onProgress("$camId YUV preview+RAW -> $yuvPlain")

                if (Build.VERSION.SDK_INT >= 33) {
                    val drp = runCatching {
                        cc.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES) as? DynamicRangeProfiles
                    }.getOrNull()
                    val allProfiles = drp?.supportedProfiles?.sorted() ?: emptyList()
                    camJson.put("dynamicRangeProfileCount", allProfiles.size)
                    camJson.put(
                        "dynamicRangeProfileIds",
                        JSONArray().apply { allProfiles.forEach { put(it) } },
                    )

                    for (dr in allProfiles) {
                        val prevOk = testCombo("preview_raw_dr_enum", dr)
                        tests.put(
                            JSONObject().apply {
                                put("name", "preview_plus_raw_dynamic_range")
                                put("dynamicRangeOnPreview", dr)
                                put("supported", prevOk)
                                put(
                                    "isRecommendedTenBit",
                                    recommendedTenBitDr != null && dr == recommendedTenBitDr,
                                )
                            },
                        )
                        Log.i(SWEEP_SIGNAL_TAG, "RAW_HDR_DR_ROW cam=$camId preview_raw dr=$dr supported=$prevOk")

                        val yuvOk = testYuvPreviewRaw(dr)
                        tests.put(
                            JSONObject().apply {
                                put("name", "yuv_preview_plus_raw_dynamic_range")
                                put("dynamicRangeOnPreview", dr)
                                put("supported", yuvOk)
                                put(
                                    "isRecommendedTenBit",
                                    recommendedTenBitDr != null && dr == recommendedTenBitDr,
                                )
                            },
                        )
                        Log.i(SWEEP_SIGNAL_TAG, "RAW_HDR_DR_ROW cam=$camId yuv_raw dr=$dr supported=$yuvOk")
                        onProgress("$camId DR=$dr preview+RAW -> $prevOk YUV+RAW -> $yuvOk")
                    }
                }

                camJson.put("sessionSupportSummary", summarizeRawHdrSessionTests(tests))
                camJson.put("tests", tests)
                cams.put(camJson)
            } finally {
                runCatching { device?.close() }
                runCatching { ht.quitSafely() }
            }
        }

        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val out = File(dir, outFileName)
        out.writeText(root.toString(2), Charsets.UTF_8)
        Log.i(SWEEP_SIGNAL_TAG, "RAW_HDR_EXCL_JSON file=${out.name} bytes=${out.length()}")
        onProgress("Wrote ${out.name}")
        out.absolutePath
    }

private fun summarizeRawHdrSessionTests(tests: JSONArray): JSONObject {
    val o = JSONObject()
    var previewPlain: Boolean? = null
    var yuvPlain: Boolean? = null
    var previewDrSupported = 0
    var previewDrTotal = 0
    var yuvDrSupported = 0
    var yuvDrTotal = 0
    for (i in 0 until tests.length()) {
        val t = tests.optJSONObject(i) ?: continue
        when (t.optString("name")) {
            "preview_plus_raw" -> previewPlain = t.optBoolean("supported")
            "yuv_preview_plus_raw" -> yuvPlain = t.optBoolean("supported")
            "preview_plus_raw_dynamic_range" -> {
                previewDrTotal++
                if (t.optBoolean("supported")) previewDrSupported++
            }
            "yuv_preview_plus_raw_dynamic_range" -> {
                yuvDrTotal++
                if (t.optBoolean("supported")) yuvDrSupported++
            }
        }
    }
    if (previewPlain == null) {
        o.put("previewPlusRawPlainSupported", JSONObject.NULL)
    } else {
        o.put("previewPlusRawPlainSupported", previewPlain)
    }
    if (yuvPlain == null) {
        o.put("yuvPreviewPlusRawPlainSupported", JSONObject.NULL)
    } else {
        o.put("yuvPreviewPlusRawPlainSupported", yuvPlain)
    }
    o.put("previewPlusRawDynamicRangeSupportedCount", previewDrSupported)
    o.put("previewPlusRawDynamicRangeTotalCount", previewDrTotal)
    o.put("yuvPreviewPlusRawDynamicRangeSupportedCount", yuvDrSupported)
    o.put("yuvPreviewPlusRawDynamicRangeTotalCount", yuvDrTotal)
    return o
}
