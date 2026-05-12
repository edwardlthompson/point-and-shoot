package dev.pointandshoot

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.view.Surface
import java.util.concurrent.Executor

private fun handlerExecutor(handler: Handler): Executor = Executor { cmd -> handler.post(cmd) }

/**
 * Prefer [CameraDevice.createCaptureSession] with [SessionConfiguration] (API 28+) over the
 * deprecated list/handler overload.
 */
internal fun CameraDevice.createCaptureSessionRegularOutputs(
    surfaces: List<Surface>,
    handler: Handler,
    callback: CameraCaptureSession.StateCallback,
) {
    val outputConfigs = surfaces.map { OutputConfiguration(it) }
    val sessionConfig =
        SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputConfigs,
            handlerExecutor(handler),
            callback,
        )
    createCaptureSession(sessionConfig)
}

/**
 * High-speed constrained session: [SessionConfiguration.SESSION_HIGH_SPEED] from API 33; older
 * releases use [CameraDevice.createConstrainedHighSpeedCaptureSession].
 */
internal fun CameraDevice.createCaptureSessionHighSpeedOutputs(
    surfaces: List<Surface>,
    handler: Handler,
    callback: CameraCaptureSession.StateCallback,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val outputConfigs = surfaces.map { OutputConfiguration(it) }
        val sessionConfig =
            SessionConfiguration(
                SessionConfiguration.SESSION_HIGH_SPEED,
                outputConfigs,
                handlerExecutor(handler),
                callback,
            )
        createCaptureSession(sessionConfig)
    } else {
        @Suppress("DEPRECATION")
        createConstrainedHighSpeedCaptureSession(surfaces, callback, handler)
    }
}
