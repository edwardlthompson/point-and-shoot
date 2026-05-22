package dev.pointandshoot

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Handler
import android.util.Log
import android.util.Size
import android.graphics.SurfaceTexture
import android.view.Surface

/**
 * Secondary front-camera session for Sprint **14.12** dual video (feeds auxiliary OES texture).
 */
internal class DualVideoFrontCameraController(
    private val cm: CameraManager,
    private val handler: Handler,
) {
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    @Volatile private var previewSurface: Surface? = null
    @Volatile private var frontCameraId: String? = null
    @Volatile private var openPending: Boolean = false
    @Volatile var sessionReady: Boolean = false
    @Volatile var hasValidFrames: Boolean = false
    private var lastFrameTime: Long = 0
    private val frameTimeoutMs = 3000L // 3 seconds timeout for frame validation

    fun setPreviewSurface(surface: Surface?) {
        // Validate surface before accepting it
        if (surface != null && !surface.isValid) {
            Log.w(DualVideoRecordingController.TAG, "dualFront rejecting invalid surface")
            return
        }
        previewSurface = surface
        val id = frontCameraId
        if (surface != null && surface.isValid && id != null && device != null) {
            handler.post { createSession(id, device!!) }
        }
    }

    fun open(frontId: String) {
        if (frontCameraId == frontId && device != null) {
            Log.d(DualVideoRecordingController.TAG, "dualFront already open for $frontId")
            return
        }
        close()
        frontCameraId = frontId
        hasValidFrames = false
        lastFrameTime = 0
        val surf = previewSurface
        if (surf == null || !surf.isValid) {
            Log.w(DualVideoRecordingController.TAG, "dualFront open deferred - invalid surface")
            return
        }
        
        // Validate camera characteristics before opening
        val chars = runCatching { cm.getCameraCharacteristics(frontId) }.getOrNull()
        if (chars == null) {
            Log.e(DualVideoRecordingController.TAG, "dualFront failed to get characteristics for $frontId")
            return
        }
        
        openPending = true
        Log.i(DualVideoRecordingController.TAG, "dualFront opening camera $frontId")
        handler.post {
            runCatching {
                cm.openCamera(
                    frontId,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            openPending = false
                            device = camera
                            Log.i(DualVideoRecordingController.TAG, "dualFront camera opened successfully")
                            createSession(frontId, camera)
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            Log.w(DualVideoRecordingController.TAG, "dualFront disconnected")
                            camera.close()
                            if (device === camera) device = null
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            Log.e(DualVideoRecordingController.TAG, "dualFront onError=$error for camera $frontId")
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
                Log.e(DualVideoRecordingController.TAG, "dualFront openCamera failed for $frontId: ${e.message}", e)
                sessionReady = false
                hasValidFrames = false
            }
        }
    }

    fun close() {
        Log.i(DualVideoRecordingController.TAG, "dualFront closing")
        frontCameraId = null
        openPending = false
        sessionReady = false
        hasValidFrames = false
        lastFrameTime = 0
        handler.post {
            runCatching { session?.stopRepeating() }
            runCatching { session?.close() }
            session = null
            runCatching { device?.close() }
            device = null
        }
    }

    private fun createSession(camId: String, camera: CameraDevice) {
        val surf =
            previewSurface?.takeIf { it.isValid } ?: run {
                Log.w(DualVideoRecordingController.TAG, "dualFront createSession: no surface")
                return
            }
        runCatching { session?.close() }
        session = null
        val outputs = listOf(surf)
        runCatching {
            camera.createCaptureSession(
                outputs,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        sessionReady = true
                        val req =
                            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(surf)
                                val fps = pickFpsRange(camId)
                                if (fps != null) {
                                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps)
                                }
                                // Add frame monitoring for validation
                                set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
                            }.build()
                        s.setRepeatingRequest(req, object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                super.onCaptureCompleted(session, request, result)
                                lastFrameTime = System.currentTimeMillis()
                                if (!hasValidFrames) {
                                    hasValidFrames = true
                                    Log.i(DualVideoRecordingController.TAG, "dualFront first valid frame received")
                                }
                            }
                            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                                super.onCaptureFailed(session, request, failure)
                                Log.w(DualVideoRecordingController.TAG, "dualFront capture failed frame=${failure.frameNumber} reason=${failure.reason}")
                            }
                        }, handler)
                        Log.i(
                            DualVideoRecordingController.TAG,
                            "dualFront session ready cameraId=$camId surface=${surf.isValid}",
                        )
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        Log.e(DualVideoRecordingController.TAG, "dualFront session configure failed for camera $camId")
                        sessionReady = false
                        hasValidFrames = false
                    }
                },
                handler,
            )
        }.onFailure { e ->
            Log.e(DualVideoRecordingController.TAG, "dualFront createSession: ${e.message}", e)
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

    /** Check if front camera is producing valid frames within timeout */
    fun isFrameFlowHealthy(): Boolean {
        if (!sessionReady || !hasValidFrames) return false
        val now = System.currentTimeMillis()
        return (now - lastFrameTime) < frameTimeoutMs
    }

    companion object {
        fun pickFrontPreviewSize(cm: CameraManager, frontId: String): Size {
            val map: StreamConfigurationMap? =
                runCatching {
                    cm.getCameraCharacteristics(frontId)
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                }.getOrNull()
            val sizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
            val target = Size(960, 540)
            return sizes.minByOrNull { dist(it, target) } ?: Size(1280, 720)
        }

        private fun dist(a: Size, b: Size): Long {
            val dw = (a.width - b.width).toLong()
            val dh = (a.height - b.height).toLong()
            return dw * dw + dh * dh
        }
    }
}
