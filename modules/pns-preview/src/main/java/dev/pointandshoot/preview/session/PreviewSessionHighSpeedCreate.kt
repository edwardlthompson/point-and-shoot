package dev.pointandshoot.preview.session

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import dev.pointandshoot.PreviewVideoConstants

/**
 * Constrained high-speed preview session create path (H.CRI-5 slice 8).
 *
 * Extracted from `PreviewEngineScreen.createSession` HFR branch.
 */
object PreviewSessionHighSpeedCreate {
    const val SURFACE_ABANDON_RETRY_DELAY_MS = 350L
    const val SURFACE_ABANDON_RETRY_CAP = 8

    fun shouldUseHighSpeedSession(
        highSpeedTarget: Pair<Size, Range<Int>>?,
        desiredFps: Int,
    ): Boolean =
        highSpeedTarget != null &&
            desiredFps >= PreviewVideoConstants.HFR_THRESHOLD_FPS

    fun shouldForceEncoderOutputSdr(
        inAppVideoRecordingArmed: Boolean,
        recorderPresent: Boolean,
        wantsMediaCodecPath: Boolean,
        hfrOutputCount: Int,
    ): Boolean =
        inAppVideoRecordingArmed &&
            recorderPresent &&
            wantsMediaCodecPath &&
            hfrOutputCount == 1

    fun isSurfaceAbandonedError(error: Throwable): Boolean =
        error is IllegalArgumentException &&
            error.message?.contains("abandoned", ignoreCase = true) == true

    fun shouldScheduleSurfaceAbandonRetry(retryCount: Int): Boolean =
        retryCount < SURFACE_ABANDON_RETRY_CAP

    data class HfrSessionLifecycle(
        val sessionGeneration: Long,
        val isStale: () -> Boolean,
        val onConfigured: (CameraCaptureSession) -> Unit,
        val onConfigureFailed: (CameraCaptureSession) -> Unit,
        val onStaleConfigured: (CameraCaptureSession) -> Unit,
    )

    fun hfrSessionStateCallback(
        logTag: String,
        lifecycle: HfrSessionLifecycle,
        onAsyncConfigurePendingCleared: () -> Unit,
    ): CameraCaptureSession.StateCallback =
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(sess: CameraCaptureSession) {
                try {
                    if (lifecycle.isStale()) {
                        Log.w(
                            logTag,
                            "HFR onConfigured ignored (stale gen=${lifecycle.sessionGeneration})",
                        )
                        lifecycle.onStaleConfigured(sess)
                        return
                    }
                    lifecycle.onConfigured(sess)
                } finally {
                    onAsyncConfigurePendingCleared()
                }
            }

            override fun onConfigureFailed(sess: CameraCaptureSession) {
                try {
                    if (!lifecycle.isStale()) {
                        lifecycle.onConfigureFailed(sess)
                    }
                } finally {
                    onAsyncConfigurePendingCleared()
                }
            }
        }

    fun shouldStartMediaCodecBeforeHfrRepeating(
        inAppVideoRecordingArmed: Boolean,
        recorderPresent: Boolean,
        wantsMediaCodecPath: Boolean,
        recorderStarted: Boolean,
    ): Boolean =
        inAppVideoRecordingArmed &&
            recorderPresent &&
            wantsMediaCodecPath &&
            !recorderStarted

    fun createHighSpeedCaptureSession(
        logTag: String,
        gateway: PreviewSessionCreateGateway,
        camera: CameraDevice,
        surfaces: List<Surface>,
        handler: android.os.Handler,
        lifecycle: HfrSessionLifecycle,
        forceEncoderOutputSdr: Boolean,
        onAsyncConfigurePendingSet: () -> Unit,
        onAsyncConfigurePendingCleared: () -> Unit,
    ): Throwable? {
        onAsyncConfigurePendingSet()
        val createErr =
            runCatching {
                gateway.createHighSpeedSession(
                    camera = camera,
                    surfaces = surfaces,
                    handler = handler,
                    callback =
                        hfrSessionStateCallback(
                            logTag = logTag,
                            lifecycle = lifecycle,
                            onAsyncConfigurePendingCleared = onAsyncConfigurePendingCleared,
                        ),
                    forceEncoderOutputSdr = forceEncoderOutputSdr,
                )
            }.exceptionOrNull()
        if (createErr != null) {
            onAsyncConfigurePendingCleared()
            Log.w(
                logTag,
                "createCaptureSession (high-speed) threw ${createErr::class.java.simpleName}: ${createErr.message}",
            )
        }
        return createErr
    }
}
