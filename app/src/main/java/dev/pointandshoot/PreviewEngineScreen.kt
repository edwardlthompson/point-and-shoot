package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.util.Log
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun PreviewEngineScreen(
    onBack: () -> Unit,
    startAutoSweep: Boolean = false,
) {
    val context = LocalContext.current
    val controller = remember { PreviewController(context.applicationContext) }

    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var selectedFps by remember { mutableStateOf(60) }
    var status by remember { mutableStateOf("Idle") }
    var measuredFps by remember { mutableStateOf(0.0) }
    var surfaceInfo by remember { mutableStateOf("surface=?") }
    var sweepJob by remember { mutableStateOf<Job?>(null) }
    var sweepRunId by remember { mutableStateOf<String?>(null) }
    val autoSweepConsumed = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose { controller.stop() }
    }

    LaunchedEffect(controller) {
        while (true) {
            status = controller.status()
            measuredFps = controller.measuredFps()
            surfaceInfo = controller.surfaceDebug()
            delay(350)
        }
    }

    // Auto-pick first camera once, to enable hands-free sweeping.
    LaunchedEffect(controller) {
        if (selectedCameraId == null) {
            val ids = controller.cameraIds()
            selectedCameraId = ids.firstOrNull()
        }
    }

    LaunchedEffect(startAutoSweep) {
        if (!startAutoSweep) return@LaunchedEffect
        if (!autoSweepConsumed.compareAndSet(false, true)) return@LaunchedEffect
        if (sweepJob != null) return@LaunchedEffect
        // Start sweep after first camera id is available.
        while (selectedCameraId == null) delay(100)
        val runId = UUID.randomUUID().toString()
        sweepRunId = runId
        sweepJob = CoroutineScope(Dispatchers.Main).launch {
            val allIds = controller.cameraIds()
            if (allIds.isEmpty()) {
                Log.w("PNS.Preview", "SWEEP aborted: no camera ids")
                Log.i(SWEEP_SIGNAL_TAG, "SWEEP_DONE runId=$runId ok=false reason=no_camera_ids")
                sweepJob = null
                return@launch
            }

            val preferred = listOf("2", "3", "4", "0")
            val sweepCameras = (preferred.filter { allIds.contains(it) } + allIds)
                .distinct()
                .filter { it != "1" }

            val sequence = listOf(60, 120, 240, 480)
            Log.d("PNS.Preview", "SWEEP cameras=${sweepCameras.joinToString(",")} allIds=${allIds.joinToString(",")}")
            Log.i(SWEEP_SIGNAL_TAG, "SWEEP_START runId=$runId cameras=${sweepCameras.joinToString(",")} sequence=${sequence.joinToString(",")}")
            for (cam in sweepCameras) {
                selectedCameraId = cam
                Log.d("PNS.Preview", "SWEEP select cameraId=$cam")
                delay(700)

                for (fps in sequence) {
                    selectedFps = fps
                    Log.d("PNS.Preview", "SWEEP start cameraId=$cam fps=$fps")
                    delay(3000)
                    Log.d(
                        "PNS.Preview",
                        "SWEEP sample cameraId=$cam fps=$fps status=${controller.status()} fpsMeasured=${"%.1f".format(controller.measuredFps())} ${controller.surfaceDebug()}",
                    )
                }
            }

            Log.d("PNS.Preview", "SWEEP done cameras=${sweepCameras.joinToString(",")}")
            Log.i(SWEEP_SIGNAL_TAG, "SWEEP_DONE runId=$runId ok=true cameras=${sweepCameras.joinToString(",")}")
            sweepJob = null
        }
    }

    LaunchedEffect(selectedCameraId, selectedFps) {
        controller.setDesired(selectedCameraId = selectedCameraId, desiredFps = selectedFps)
    }

    val insets = rememberSystemInsetsDp()
    PreviewEngineContent(
        padding = insets.asPaddingValues(extra = 16.dp),
        selectedCameraId = selectedCameraId,
        selectedFps = selectedFps,
        status = status,
        measuredFps = measuredFps,
        surfaceInfo = surfaceInfo,
        isSweeping = sweepJob != null,
        onBack = {
            sweepJob?.cancel()
            controller.stop()
            onBack()
        },
        onPickFirstCamera = {
            val ids = controller.cameraIds()
            selectedCameraId = ids.firstOrNull()
            status = if (selectedCameraId == null) "No cameras" else "Selected cameraId=$selectedCameraId"
        },
        onSetFps = { selectedFps = it },
        onStartSweep = {
            if (sweepJob != null) return@PreviewEngineContent
            sweepJob = CoroutineScope(Dispatchers.Main).launch {
                val allIds = controller.cameraIds()
                if (allIds.isEmpty()) {
                    Log.w("PNS.Preview", "SWEEP aborted: no camera ids")
                    sweepJob = null
                    return@launch
                }

                // Prefer physical back cameras if present (dodge profile), then logical back.
                val preferred = listOf("2", "3", "4", "0")
                val sweepCameras = (preferred.filter { allIds.contains(it) } + allIds)
                    .distinct()
                    .filter { it != "1" } // skip front by default

                val sequence = listOf(60, 120, 240, 480)
                Log.d("PNS.Preview", "SWEEP cameras=${sweepCameras.joinToString(",")} allIds=${allIds.joinToString(",")}")
                for (cam in sweepCameras) {
                    selectedCameraId = cam
                    Log.d("PNS.Preview", "SWEEP select cameraId=$cam")
                    // Give the UI/controller a moment to propagate camera id change + surface resize.
                    delay(700)

                    for (fps in sequence) {
                        selectedFps = fps
                        Log.d("PNS.Preview", "SWEEP start cameraId=$cam fps=$fps")
                        // Allow surface sizing + camera open + session start.
                        delay(3000)
                        Log.d(
                            "PNS.Preview",
                            "SWEEP sample cameraId=$cam fps=$fps status=${controller.status()} fpsMeasured=${"%.1f".format(controller.measuredFps())} ${controller.surfaceDebug()}",
                        )
                    }
                }
                Log.d("PNS.Preview", "SWEEP done cameras=${sweepCameras.joinToString(",")}")
                sweepJob = null
            }
        },
        onStopSweep = {
            sweepJob?.cancel()
            sweepJob = null
        },
        controller = controller,
    )
}

@Composable
private fun PreviewEngineContent(
    padding: PaddingValues,
    selectedCameraId: String?,
    selectedFps: Int,
    status: String,
    measuredFps: Double,
    surfaceInfo: String,
    isSweeping: Boolean,
    onBack: () -> Unit,
    onPickFirstCamera: () -> Unit,
    onSetFps: (Int) -> Unit,
    onStartSweep: () -> Unit,
    onStopSweep: () -> Unit,
    controller: PreviewController,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = onPickFirstCamera) { Text("Pick first camera") }
            if (!isSweeping) {
                Button(onClick = onStartSweep) { Text("Auto-sweep") }
            } else {
                OutlinedButton(onClick = onStopSweep) { Text("Stop") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { onSetFps(60) }) { Text("60") }
            OutlinedButton(onClick = { onSetFps(120) }) { Text("120") }
            OutlinedButton(onClick = { onSetFps(240) }) { Text("240") }
            OutlinedButton(onClick = { onSetFps(480) }) { Text("480") }
            Text("fps=$selectedFps")
        }

        Text("cameraId=${selectedCameraId ?: "null"}")
        Text("Phase 1 (WIP): Preview engine (Camera2)")
        Text("Status: $status")
        Text(surfaceInfo)
        Text("Measured FPS: ${"%.1f".format(measuredFps)}")

        Spacer(Modifier.height(4.dp))

        // Live preview surface + Pro HUD overlays per BUILD_PLAN \u00a75 ("HUD
        // chip 'LUT' alongside the imaging-profile selector; default Pro HUD
        // surfaces command dial + readouts during live preview"). The
        // TextureView still owns the camera surface; the HUD chrome rides on
        // top via a Box stack.
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                controller.onSurfaceTextureAvailable(st, width, height)
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                controller.onSurfaceTextureSizeChanged(width, height)
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                                controller.onTextureUpdated()
                            }

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                controller.onSurfaceTextureDestroyed()
                                return true
                            }
                        }
                    }
                },
                update = {},
            )
            LivePreviewHudOverlay(
                measuredFps = measuredFps,
                desiredFps = selectedFps,
                cameraId = selectedCameraId,
            )
        }
    }
}

/**
 * Pro HUD chrome that rides on top of the live preview surface per
 * BUILD_PLAN \u00a75 ("default Pro HUD surfaces during live preview").
 * Reads [HudSettings] so the user's Settings > HUD toggles are honored
 * here too. EyeAfOverlay receives an empty target list - the face / eye
 * detection pipeline is shipped with placeholder output until the
 * Camera2-based detector lands.
 */
@Composable
private fun LivePreviewHudOverlay(
    measuredFps: Double,
    desiredFps: Int,
    cameraId: String?,
) {
    val state = rememberHudSettings()
    val settings = state.current

    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                if (settings.showFpsReadout || settings.showIsoShutterReadout) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        val text = buildString {
                            if (settings.showFpsReadout) {
                                append("fps ${"%.1f".format(measuredFps)} (target $desiredFps)")
                            }
                            if (settings.showIsoShutterReadout) {
                                if (isNotEmpty()) append("  ")
                                append("ISO -- 1/--")
                            }
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                }
                if (cameraId != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "cam $cameraId",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // Eye AF overlay (placeholder - empty list until Camera2 face metadata is wired).
        if (settings.showEyeAfOverlay) {
            EyeAfOverlay(eyes = emptyList(), modifier = Modifier.fillMaxSize())
        }

        // Bottom row: command dial + LUT chip.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(0.dp))
        }

        // Bottom-aligned dock so dial + LUT chip never overlap the top readouts.
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            LutChipRow(state = state, modifier = Modifier.padding(bottom = 8.dp))
            if (settings.showCommandDial) {
                CommandDial(
                    selected = CommandDialMode.M,
                    onSelect = {},
                )
            }
        }
    }
}

private class PreviewController(
    private val appContext: Context,
) {
    private val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val tag = "PNS.Preview"

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private var previewSurfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var selectedCameraId: String? = null
    private var desiredFps: Int = 60

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    private var lastStatus: String = "Idle"

    @Volatile private var desiredHighSpeedSize: Size? = null
    @Volatile private var desiredSurfaceSize: Size? = null
    @Volatile private var currentSurfaceSize: Size? = null
    @Volatile private var generation: Long = 0L

    // Frame-rate measurement (based on capture result timestamps).
    @Volatile private var lastTimestampNs: Long = 0L
    @Volatile private var smoothedFps: Double = 0.0
    @Volatile private var framesWithTimestamp: Long = 0L
    @Volatile private var framesMissingTimestamp: Long = 0L
    @Volatile private var lastWallNs: Long = 0L
    @Volatile private var smoothedWallFps: Double = 0.0
    @Volatile private var lastFrameNs: Long = 0L
    @Volatile private var smoothedFrameFps: Double = 0.0
    @Volatile private var framesFromTexture: Long = 0L
    @Volatile private var textureWindowStartNs: Long = 0L
    @Volatile private var textureWindowFrames: Long = 0L

    fun cameraIds(): List<String> =
        runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())

    fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        Log.d(tag, "textureAvailable ${width}x${height}")
        previewSurfaceTexture = st
        currentSurfaceSize = if (width > 0 && height > 0) Size(width, height) else null
        // Initialize buffer size to the current view size; we will override per-mode later.
        if (width > 0 && height > 0) {
            runCatching { st.setDefaultBufferSize(width, height) }
        }
        rebuildSurfaceIfPossible()
        maybeRestart()
    }

    fun onSurfaceTextureSizeChanged(width: Int, height: Int) {
        Log.d(tag, "textureSizeChanged ${width}x${height}")
        currentSurfaceSize = if (width > 0 && height > 0) Size(width, height) else null
        maybeRestart()
    }

    fun onSurfaceTextureDestroyed() {
        Log.d(tag, "textureDestroyed")
        previewSurfaceTexture = null
        runCatching { previewSurface?.release() }
        previewSurface = null
        currentSurfaceSize = null
        closeCamera()
    }

    fun onTextureUpdated() {
        // Called when TextureView has rendered a new frame.
        framesFromTexture++
        textureWindowFrames++
        val now = SystemClock.elapsedRealtimeNanos()
        if (textureWindowStartNs <= 0L) {
            textureWindowStartNs = now
        }
        val prev = lastFrameNs
        lastFrameNs = now
        if (prev <= 0L) return
        val dt = now - prev
        if (dt <= 0L) return
        val inst = 1e9 / dt.toDouble()
        smoothedFrameFps = if (smoothedFrameFps <= 0.0) inst else (smoothedFrameFps * 0.90 + inst * 0.10)
    }

    fun setDesired(selectedCameraId: String?, desiredFps: Int) {
        val changed = this.selectedCameraId != selectedCameraId || this.desiredFps != desiredFps
        this.selectedCameraId = selectedCameraId
        this.desiredFps = desiredFps
        if (changed) Log.d(tag, "setDesired cameraId=${selectedCameraId ?: "null"} fps=$desiredFps")
        if (changed) maybeRestart()
    }

    fun status(): String = lastStatus

    fun measuredFps(): Double {
        // Prefer sensor timestamps when present. Otherwise prefer rendered-frame rate from TextureView.
        // If only a few frames arrive (or 1 frame), smoothedFrameFps may be 0; use a windowed estimate too.
        if (framesWithTimestamp > 0L) return smoothedFps

        val now = SystemClock.elapsedRealtimeNanos()
        val winStart = textureWindowStartNs
        val winFrames = textureWindowFrames
        val windowFps =
            if (winStart > 0L && winFrames > 1L) {
                val dt = now - winStart
                if (dt > 0L) (winFrames.toDouble() * 1e9) / dt.toDouble() else 0.0
            } else {
                0.0
            }

        return when {
            smoothedFrameFps > 0.0 -> smoothedFrameFps
            windowFps > 0.0 -> windowFps
            else -> smoothedWallFps
        }
    }

    fun surfaceDebug(): String {
        val cur = currentSurfaceSize
        val want = desiredSurfaceSize
        val hs = desiredHighSpeedSize
        val mode =
            when {
                framesWithTimestamp > 0L -> "sensorTs"
                framesFromTexture > 0L -> "texture"
                else -> "wall"
            }
        return "surface cur=${cur?.width}x${cur?.height} want=${want?.width}x${want?.height} hs=${hs?.width}x${hs?.height} ts=${framesWithTimestamp}/${framesWithTimestamp + framesMissingTimestamp} tex=${framesFromTexture} texWin=${textureWindowFrames} fpsMode=$mode"
    }

    private fun closeCamera() {
        generation++
        runCatching { session?.close() }
        runCatching { device?.close() }
        session = null
        device = null

        lastTimestampNs = 0L
        smoothedFps = 0.0
        framesWithTimestamp = 0L
        framesMissingTimestamp = 0L
        lastWallNs = 0L
        smoothedWallFps = 0.0
        lastFrameNs = 0L
        smoothedFrameFps = 0.0
        framesFromTexture = 0L
        textureWindowStartNs = 0L
        textureWindowFrames = 0L
    }

    fun stop() {
        closeCamera()

        handler = null
        thread?.quitSafely()
        thread = null

        lastStatus = "Stopped"
    }

    private fun ensureThread() {
        if (thread != null && handler != null) return
        val t = HandlerThread("PNS.Preview")
        t.start()
        thread = t
        handler = Handler(t.looper)
    }

    private fun maybeRestart() {
        val camId = selectedCameraId
        val surf = previewSurface
        if (camId.isNullOrBlank() || surf == null) {
            lastStatus = "Waiting (cameraId=${camId ?: "null"}, surface=${if (surf == null) "null" else "ok"})"
            return
        }

        Log.d(tag, "maybeRestart cameraId=$camId fps=$desiredFps cur=${currentSurfaceSize?.width}x${currentSurfaceSize?.height}")

        val map = runCatching {
            cm.getCameraCharacteristics(camId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        }.getOrNull()

        desiredHighSpeedSize = pickHighSpeedTarget(
            map = runCatching {
                map
            }.getOrNull(),
            desiredFps = desiredFps,
        )?.first

        // Pick the surface size we want to drive for this mode.
        val wantedSurfaceSize =
            if (desiredFps >= 120) {
                // Constrained high-speed requires one of these exact sizes.
                desiredHighSpeedSize
            } else {
                pickNormalPreviewSize(map)
            }
        desiredSurfaceSize = wantedSurfaceSize

        // Ensure the SurfaceTexture buffer size matches what Camera2 expects.
        // Some devices never report this back via callbacks, so we treat this as authoritative.
        if (wantedSurfaceSize != null) {
            runCatching { previewSurfaceTexture?.setDefaultBufferSize(wantedSurfaceSize.width, wantedSurfaceSize.height) }
            currentSurfaceSize = Size(wantedSurfaceSize.width, wantedSurfaceSize.height)
            rebuildSurfaceIfPossible()
            Log.d(tag, "setDefaultBufferSize ${wantedSurfaceSize.width}x${wantedSurfaceSize.height}")
        }

        closeCamera()
        ensureThread()
        val latestSurface = previewSurface
        if (latestSurface == null) {
            lastStatus = "Waiting (surface=null)"
            return
        }
        Log.d(tag, "openAndStart (after close) cameraId=$camId fps=$desiredFps")
        openAndStart(camId, latestSurface)
    }

    private fun rebuildSurfaceIfPossible() {
        val st = previewSurfaceTexture ?: return
        runCatching { previewSurface?.release() }
        previewSurface = Surface(st)
    }

    // (TextureView drives frame counting via onSurfaceTextureUpdated.)

    private fun openAndStart(camId: String, surf: Surface) {
        val h = handler ?: return
        lastStatus = "Opening cameraId=$camId"
        Log.d(tag, "openCamera cameraId=$camId fps=$desiredFps")
        val gen = generation
        try {
            cm.openCamera(
                camId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (gen != generation) {
                            Log.w(tag, "onOpened ignored (stale gen=$gen current=$generation)")
                            runCatching { camera.close() }
                            return
                        }
                        device = camera
                        lastStatus = "Opened cameraId=$camId; creating session (fps=$desiredFps)"
                        Log.d(tag, "onOpened cameraId=$camId; creating session")
                        createSession(camera, camId, surf)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        lastStatus = "Disconnected cameraId=$camId"
                        Log.w(tag, "onDisconnected cameraId=$camId")
                        runCatching { camera.close() }
                        device = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        lastStatus = "Camera error cameraId=$camId error=$error"
                        Log.e(tag, "onError cameraId=$camId error=$error")
                        runCatching { camera.close() }
                        device = null
                    }
                },
                h,
            )
        } catch (e: SecurityException) {
            lastStatus = "Missing CAMERA permission"
        } catch (e: CameraAccessException) {
            lastStatus = "CameraAccessException: ${e.reason}"
        } catch (t: Throwable) {
            lastStatus = "Open failed: ${t::class.java.simpleName}"
        }
    }

    private fun createSession(camera: CameraDevice, camId: String, surf: Surface) {
        val h = handler ?: return
        val gen = generation
        val map = runCatching { cm.getCameraCharacteristics(camId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) }.getOrNull()
        val target = pickHighSpeedTarget(map, desiredFps)

        val useHighSpeed = target != null && desiredFps >= 120
        Log.d(tag, "createSession camId=$camId desiredFps=$desiredFps useHighSpeed=$useHighSpeed target=${target?.first?.width}x${target?.first?.height} ${target?.second}")
        if (!useHighSpeed) {
            camera.createCaptureSession(
                listOf(surf),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(sess: CameraCaptureSession) {
                        if (gen != generation || device == null) {
                            Log.w(tag, "onConfigured ignored (stale gen=$gen current=$generation)")
                            runCatching { sess.close() }
                            return
                        }
                        session = sess
                        val fpsRange = pickNormalFpsRange(camId, desiredFps)
                        startRepeating(sess, camera, surf, fpsRange = fpsRange)
                    }

                    override fun onConfigureFailed(sess: CameraCaptureSession) {
                        lastStatus = "Session configure failed (normal)"
                    }
                },
                h,
            )
            return
        }

        Log.d(tag, "Creating HFR session fps=$desiredFps size=${target.first.width}x${target.first.height} range=${target.second}")
        // Constrained high-speed session
        camera.createConstrainedHighSpeedCaptureSession(
            listOf(surf),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(sess: CameraCaptureSession) {
                    if (gen != generation || device == null) {
                        Log.w(tag, "HFR onConfigured ignored (stale gen=$gen current=$generation)")
                        runCatching { sess.close() }
                        return
                    }
                    session = sess
                    val fpsRange = target!!.second
                    startRepeating(sess, camera, surf, fpsRange = fpsRange)
                }

                override fun onConfigureFailed(sess: CameraCaptureSession) {
                    lastStatus = "High-speed session configure failed"
                }
            },
            h,
        )
    }

    private fun startRepeating(
        sess: CameraCaptureSession,
        camera: CameraDevice,
        surf: Surface,
        fpsRange: Range<Int>?,
    ) {
        val template = if (fpsRange != null && fpsRange.lower >= 120) {
            CameraDevice.TEMPLATE_RECORD
        } else {
            CameraDevice.TEMPLATE_PREVIEW
        }
        val req = camera.createCaptureRequest(template).apply {
            addTarget(surf)
            if (fpsRange != null) {
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            }
        }

        try {
            if (sess is CameraConstrainedHighSpeedSessionShim && fpsRange != null) {
                // Not used; see below.
            }
        } catch (_: Throwable) {
            // no-op
        }

        try {
            // If this is a constrained high-speed session, we must use a high-speed request list.
            val constrained = runCatching { sess.javaClass.name.contains("ConstrainedHighSpeed", ignoreCase = true) }.getOrDefault(false)
            if (constrained) {
                val list = (sess as? android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession)
                    ?.createHighSpeedRequestList(req.build())
                if (list != null) {
                    sess.setRepeatingBurst(list, fpsMeasuringCallback(), handler)
                    lastStatus = "Preview running (HFR ${fpsRange?.upper ?: "?"}fps)"
                    Log.d(tag, "HFR repeatingBurst started (n=${list.size})")
                    return
                }
            }

            sess.setRepeatingRequest(req.build(), fpsMeasuringCallback(), handler)
            lastStatus = "Preview running (normal)"
            Log.d(tag, "Normal repeatingRequest started")
        } catch (e: CameraAccessException) {
            lastStatus = "Repeating failed: ${e.reason}"
        } catch (t: Throwable) {
            lastStatus = "Repeating failed: ${t::class.java.simpleName}"
        }
    }

    private fun fpsMeasuringCallback(): CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                onWallTick()
                onCaptureResult(result)
            }

            override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult,
            ) {
                // Some devices deliver timestamps in partials; accept if present.
                onWallTick()
                val ts = partialResult.get(CaptureResult.SENSOR_TIMESTAMP)
                if (ts == null) {
                    framesMissingTimestamp++
                    return
                }
                framesWithTimestamp++
                onTimestamp(ts)
            }

            private fun onCaptureResult(result: TotalCaptureResult) {
                val ts = result.get(CaptureResult.SENSOR_TIMESTAMP)
                if (ts == null) {
                    framesMissingTimestamp++
                    return
                }
                framesWithTimestamp++
                onTimestamp(ts)
            }

            private fun onWallTick() {
                // Fallback FPS meter when SENSOR_TIMESTAMP is missing (some HFR paths).
                val now = SystemClock.elapsedRealtimeNanos()
                val prev = lastWallNs
                lastWallNs = now
                if (prev <= 0L) return
                val dt = now - prev
                if (dt <= 0L) return
                val inst = 1e9 / dt.toDouble()
                smoothedWallFps = if (smoothedWallFps <= 0.0) inst else (smoothedWallFps * 0.90 + inst * 0.10)
            }

            private fun onTimestamp(tsNs: Long) {
                val prev = lastTimestampNs
                lastTimestampNs = tsNs
                if (prev <= 0L) return
                val dt = tsNs - prev
                if (dt <= 0L) return
                val inst = 1e9 / dt.toDouble()
                smoothedFps = if (smoothedFps <= 0.0) inst else (smoothedFps * 0.90 + inst * 0.10)

                // Occasional log so we can verify live without spamming.
                if (dt < 25_000_000L || dt > 100_000_000L) {
                    Log.d(tag, "fps=${"%.1f".format(smoothedFps)} dtMs=${"%.1f".format(dt / 1e6)}")
                }
            }
        }

    private fun pickHighSpeedTarget(map: StreamConfigurationMap?, desiredFps: Int): Pair<Size, Range<Int>>? {
        if (map == null) return null
        val sizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
        if (sizes.isEmpty()) return null

        // Prefer 1920x1080 if available; else 1280x720; else any.
        val preferredOrder = listOf(Size(1920, 1080), Size(1280, 720))
        val candidateSizes = (preferredOrder.filter { p -> sizes.any { it == p } } + sizes).distinct()

        for (s in candidateSizes) {
            val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull() ?: continue
            val exact = ranges.firstOrNull { it.lower == desiredFps && it.upper == desiredFps }
            if (exact != null) return s to exact
            val capped = ranges.firstOrNull { it.upper == desiredFps }
            if (capped != null) return s to capped
        }
        return null
    }

    private fun pickNormalFpsRange(camId: String, desiredFps: Int): Range<Int>? {
        if (desiredFps <= 0) return null
        val ranges = runCatching {
            cm.getCameraCharacteristics(camId).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        }.getOrNull().orEmpty()
        if (ranges.isEmpty()) return null

        // Prefer exact fixed range (e.g., 60-60), else closest range that can include desired.
        ranges.firstOrNull { it.lower == desiredFps && it.upper == desiredFps }?.let { return it }
        ranges.firstOrNull { it.upper == desiredFps }?.let { return it }
        ranges.firstOrNull { it.lower <= desiredFps && it.upper >= desiredFps }?.let { return it }
        return null
    }

    private fun pickNormalPreviewSize(map: StreamConfigurationMap?): Size? {
        if (map == null) return null
        val sizes = runCatching { map.getOutputSizes(SurfaceTexture::class.java)?.toList() }.getOrNull().orEmpty()
        if (sizes.isEmpty()) return null

        // Prefer 1920x1080, else 1280x720, else the largest by area.
        sizes.firstOrNull { it.width == 1920 && it.height == 1080 }?.let { return it }
        sizes.firstOrNull { it.width == 1280 && it.height == 720 }?.let { return it }
        return sizes.maxByOrNull { it.width.toLong() * it.height.toLong() }
    }

}

// Marker shim (kept to avoid accidental imports/edits later).
private interface CameraConstrainedHighSpeedSessionShim

