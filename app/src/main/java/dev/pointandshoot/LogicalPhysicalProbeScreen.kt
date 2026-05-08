package dev.pointandshoot

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
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

private const val TAG = "PNS.LogicalPhysical"

private object LogicalPhysicalRunGuard {
    val running = AtomicBoolean(false)
}

private object LogicalPhysicalWorkScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun LogicalPhysicalProbeScreen(
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
        if (!LogicalPhysicalRunGuard.running.compareAndSet(false, true)) {
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
        scanLines.add("${Instant.now()} - Logical vs physical camera comparison (Phase 8)")
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val outFile = "logical_physical_$ts.json"
        Log.i(SWEEP_SIGNAL_TAG, "LOGICAL_PHYSICAL_START file=$outFile")
        lateinit var workJob: Job
        workJob = LogicalPhysicalWorkScope.launch {
            try {
                val path = runLogicalPhysicalProbe(context.applicationContext, outFile) { scanLines.appendProbeLine(it) }
                withContext(Dispatchers.Main) {
                    status = "OK saved=$path"
                    isRunning = false
                    LogicalPhysicalRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "LOGICAL_PHYSICAL_DONE file=$outFile ok=true")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        status = "Cancelled"
                        isRunning = false
                        LogicalPhysicalRunGuard.running.set(false)
                        Log.i(SWEEP_SIGNAL_TAG, "LOGICAL_PHYSICAL_DONE file=$outFile ok=false")
                    }
                }
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "probe failed", e)
                withContext(Dispatchers.Main) {
                    status = "FAILED: ${e.message}"
                    isRunning = false
                    LogicalPhysicalRunGuard.running.set(false)
                    Log.i(SWEEP_SIGNAL_TAG, "LOGICAL_PHYSICAL_DONE file=$outFile ok=false")
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
            Button(onClick = { launch() }, enabled = !isRunning) { Text("Run logical / physical probe") }
        }
        Text("Phase 8: REGULAR preview session spot-check on each id; physical children of logical cameras")
        Text(status)
        ProbeLiveLogPanel("Live log", scanLines, Modifier.weight(1f))
    }
}

private fun pickPreviewSize(map: android.hardware.camera2.params.StreamConfigurationMap?): Size? {
    val previewSizes = runCatching {
        map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
    }.getOrDefault(emptyList())
    if (previewSizes.isEmpty()) return null
    return previewSizes.minByOrNull { abs(it.width - 1280) + abs(it.height - 720) }
        ?: previewSizes.minByOrNull { it.width * it.height }
}

private fun sensorSyncName(cc: CameraCharacteristics): Any {
    val syncType = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cc.get(CameraCharacteristics.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE)
        } else {
            null
        }
    }.getOrNull()
    return when (syncType) {
        null -> JSONObject.NULL
        CameraMetadata.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE_APPROXIMATE -> "APPROXIMATE"
        CameraMetadata.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE_CALIBRATED -> "CALIBRATED"
        else -> syncType.toString()
    }
}

private fun openCameraId(
    cm: CameraManager,
    cameraId: String,
    handler: Handler,
): CameraDevice? {
    val latch = CountDownLatch(1)
    val ref = AtomicReference<CameraDevice?>(null)
    cm.openCamera(
        cameraId,
        object : CameraDevice.StateCallback() {
            override fun onOpened(cd: CameraDevice) {
                ref.set(cd)
                latch.countDown()
            }
            override fun onDisconnected(cd: CameraDevice) {
                runCatching { cd.close() }
                latch.countDown()
            }
            override fun onError(cd: CameraDevice, error: Int) {
                runCatching { cd.close() }
                latch.countDown()
            }
        },
        handler,
    )
    return if (latch.await(6, TimeUnit.SECONDS)) ref.get() else null
}

private suspend fun runLogicalPhysicalProbe(
    appContext: Context,
    outFileName: String,
    onProgress: suspend (String) -> Unit,
): String =
    withContext(Dispatchers.IO) {
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList()).toSet()
        val ordered = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())

        val root = JSONObject().apply {
            put("generatedAt", Instant.now().toString())
            put("phase", 8)
            put("probe", "logical_physical_compare")
            put("device", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("sdkInt", Build.VERSION.SDK_INT)
            })
            put("cameras", JSONArray())
        }
        val cams = root.getJSONArray("cameras")
        onProgress("Camera ids: ${ordered.joinToString(",")}")

        fun probeOneCamera(camId: String, previewSize: Size): Boolean? {
            val ht = HandlerThread("PNS.LogPhy-$camId")
            ht.start()
            val h = Handler(ht.looper)
            var dev: CameraDevice? = null
            return try {
                dev = openCameraId(cm, camId, h)
                if (dev == null) return null
                val ok = isSessionSupportedWithDynamicRange(
                    dev,
                    SessionConfiguration.SESSION_REGULAR,
                    previewSize,
                    null,
                )
                ok
            } finally {
                runCatching { dev?.close() }
                runCatching { ht.quitSafely() }
            }
        }

        for ((idx, camId) in ordered.withIndex()) {
            onProgress("--- $camId (${idx + 1}/${ordered.size}) ---")
            val camJson = JSONObject().put("cameraId", camId)
            val cc = runCatching { cm.getCameraCharacteristics(camId) }.getOrNull()
            if (cc == null) {
                camJson.put("error", "no_characteristics")
                cams.put(camJson)
                continue
            }
            val caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val logicalMulti = caps.any {
                it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
            }
            camJson.put("logicalMultiCameraCapability", logicalMulti)
            val facing = cc.get(CameraCharacteristics.LENS_FACING)
            camJson.put(
                "lensFacing",
                when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                    CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                    else -> JSONObject.NULL
                },
            )
            val hwLevel = cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            camJson.put(
                "hardwareLevel",
                when (hwLevel) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                    else -> hwLevel?.let { "UNKNOWN_$it" } ?: JSONObject.NULL
                },
            )
            val physIds = runCatching {
                cc.physicalCameraIds?.toList()?.sorted().orEmpty()
            }.getOrDefault(emptyList())
            camJson.put("physicalCameraIds", JSONArray(physIds))
            camJson.put("sensorSyncType", sensorSyncName(cc))

            val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSize = pickPreviewSize(map)
            if (previewSize == null) {
                camJson.put("error", "no_preview_sizes")
                cams.put(camJson)
                continue
            }
            camJson.put("spotPreviewW", previewSize.width)
            camJson.put("spotPreviewH", previewSize.height)

            val selfSupported = probeOneCamera(camId, previewSize)
            camJson.put("regularSessionSupportedSelf", selfSupported ?: JSONObject.NULL)
            Log.i(
                SWEEP_SIGNAL_TAG,
                "LOGICAL_PHYSICAL_ROW cam=$camId self_supported=${selfSupported ?: "open_fail"} ${previewSize.width}x${previewSize.height}",
            )
            onProgress("$camId self -> $selfSupported")

            val children = JSONArray()
            for (pid in physIds) {
                val childObj = JSONObject().apply {
                    put("physicalId", pid)
                    put("inCameraIdList", pid in cameraIds)
                }
                if (pid in cameraIds) {
                    val pcc = runCatching { cm.getCameraCharacteristics(pid) }.getOrNull()
                    val pmap = pcc?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val pSize = pickPreviewSize(pmap) ?: previewSize
                    childObj.put("spotPreviewW", pSize.width)
                    childObj.put("spotPreviewH", pSize.height)
                    val sup = probeOneCamera(pid, pSize)
                    childObj.put("regularSessionSupported", sup ?: JSONObject.NULL)
                    Log.i(
                        SWEEP_SIGNAL_TAG,
                        "LOGICAL_PHYSICAL_CHILD logical=$camId physical=$pid supported=${sup ?: "open_fail"}",
                    )
                    onProgress("  child $pid -> $sup")
                } else {
                    childObj.put("regularSessionSupported", JSONObject.NULL)
                    childObj.put("note", "physical id not in cameraIdList; skipped open")
                }
                children.put(childObj)
            }
            camJson.put("physicalChildren", children)
            cams.put(camJson)
        }

        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val out = File(dir, outFileName)
        out.writeText(root.toString(2), Charsets.UTF_8)
        Log.i(SWEEP_SIGNAL_TAG, "LOGICAL_PHYSICAL_JSON file=${out.name} bytes=${out.length()}")
        onProgress("Wrote ${out.name}")
        out.absolutePath
    }
