package dev.pointandshoot

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Low-resolution preview on a **second** rear camera while the primary id records HFR to MediaCodec.
 */
internal class HfrRecordMonitorCameraController(
    private val cm: CameraManager,
    private val handler: Handler,
) {
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    @Volatile private var previewSurface: Surface? = null
    @Volatile private var monitorCameraId: String? = null
    @Volatile private var openPending: Boolean = false
    @Volatile var sessionReady: Boolean = false
    @Volatile var hasValidFrames: Boolean = false
    private var lastFrameTime: Long = 0L
    private val frameTimeoutMs = 3000L

    fun setPreviewSurface(surface: Surface?) {
        if (surface != null && !surface.isValid) {
            Log.w(HfrRecordMonitorSupport.TAG, "monitor rejecting invalid surface")
            return
        }
        previewSurface = surface
        val id = monitorCameraId
        if (surface != null && surface.isValid && id != null && device != null) {
            handler.post { createSession(id, device!!) }
        }
    }

    fun open(monitorId: String, previewSize: Size) {
        if (monitorCameraId == monitorId && device != null) {
            Log.d(HfrRecordMonitorSupport.TAG, "monitor already open for $monitorId")
            return
        }
        handler.post {
            closeImmediate()
            monitorCameraId = monitorId
            hasValidFrames = false
            lastFrameTime = 0L
            val surf = previewSurface
            if (surf == null || !surf.isValid) {
                Log.w(HfrRecordMonitorSupport.TAG, "monitor open deferred — invalid surface")
                return@post
            }
            openPending = true
            Log.i(
                HfrRecordMonitorSupport.TAG,
                "monitor opening cameraId=$monitorId preview=${previewSize.width}x${previewSize.height}",
            )
            runCatching {
                cm.openCamera(
                    monitorId,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            openPending = false
                            device = camera
                            createSession(monitorId, camera)
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            Log.w(HfrRecordMonitorSupport.TAG, "monitor disconnected")
                            camera.close()
                            if (device === camera) device = null
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            Log.e(HfrRecordMonitorSupport.TAG, "monitor onError=$error cameraId=$monitorId")
                            camera.close()
                            if (device === camera) {
                                device = null
                                sessionReady = false
                                hasValidFrames = false
                            }
                            openPending = false
                        }
                    },
                    handler,
                )
            }.onFailure { e ->
                openPending = false
                Log.e(HfrRecordMonitorSupport.TAG, "monitor openCamera failed: ${e.message}", e)
                sessionReady = false
                hasValidFrames = false
            }
        }
    }

    fun close() {
        handler.post { closeImmediate() }
    }

    /** Tear down on [handler] before opening another monitor (avoids abandoned BufferQueue). */
    fun closeBlocking(timeoutMs: Long = 3_000L) {
        if (Looper.myLooper() == handler.looper) {
            closeImmediate()
            return
        }
        val latch = CountDownLatch(1)
        handler.post {
            closeImmediate()
            latch.countDown()
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            Log.w(HfrRecordMonitorSupport.TAG, "monitor closeBlocking timed out after ${timeoutMs}ms")
        }
    }

    /** Must run on [handler] (serialized with ImageReader callbacks). */
    internal fun closeImmediateOnHandler() {
        check(Looper.myLooper() == handler.looper) { "closeImmediateOnHandler requires camera handler" }
        closeImmediate()
    }

    private fun closeImmediate() {
        Log.i(HfrRecordMonitorSupport.TAG, "monitor closing")
        monitorCameraId = null
        openPending = false
        sessionReady = false
        hasValidFrames = false
        lastFrameTime = 0L
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        session = null
        runCatching { device?.close() }
        device = null
    }

    fun isFrameFlowHealthy(): Boolean {
        if (!sessionReady || !hasValidFrames) return false
        return (System.currentTimeMillis() - lastFrameTime) < frameTimeoutMs
    }

    private fun createSession(camId: String, camera: CameraDevice) {
        val surf =
            previewSurface?.takeIf { it.isValid } ?: run {
                Log.w(HfrRecordMonitorSupport.TAG, "monitor createSession: no surface")
                return
            }
        runCatching { session?.close() }
        session = null
        runCatching {
            camera.createCaptureSession(
                listOf(surf),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        sessionReady = true
                        val req =
                            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(surf)
                                pickFpsRange(camId)?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                            }.build()
                        s.setRepeatingRequest(
                            req,
                            object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: TotalCaptureResult,
                                ) {
                                    lastFrameTime = System.currentTimeMillis()
                                    if (!hasValidFrames) {
                                        hasValidFrames = true
                                        Log.i(HfrRecordMonitorSupport.TAG, "monitor first frame cameraId=$camId")
                                    }
                                }

                                override fun onCaptureFailed(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    failure: CaptureFailure,
                                ) {
                                    Log.w(
                                        HfrRecordMonitorSupport.TAG,
                                        "monitor capture failed frame=${failure.frameNumber} reason=${failure.reason}",
                                    )
                                }
                            },
                            handler,
                        )
                        Log.i(HfrRecordMonitorSupport.TAG, "monitor session ready cameraId=$camId")
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        Log.e(HfrRecordMonitorSupport.TAG, "monitor session configure failed cameraId=$camId")
                        sessionReady = false
                        hasValidFrames = false
                    }
                },
                handler,
            )
        }.onFailure { e ->
            Log.e(HfrRecordMonitorSupport.TAG, "monitor createSession: ${e.message}", e)
        }
    }

    private fun pickFpsRange(camId: String): android.util.Range<Int>? {
        val ranges =
            runCatching {
                cm.getCameraCharacteristics(camId)
                    .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            }.getOrNull().orEmpty()
        return ranges.firstOrNull { it.lower == 30 && it.upper == 30 }
            ?: ranges.firstOrNull { it.lower <= 30 && it.upper >= 30 }
            ?: ranges.firstOrNull()
    }
}
