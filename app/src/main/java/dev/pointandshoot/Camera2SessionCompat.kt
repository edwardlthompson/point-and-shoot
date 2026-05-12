package dev.pointandshoot

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.Surface
import java.util.concurrent.Executor

private const val TAG = "PNS.StreamUseCase"

private fun handlerExecutor(handler: Handler): Executor = Executor { cmd -> handler.post(cmd) }

/**
 * Builds [OutputConfiguration] entries, optionally tagging stream use cases (API 33+) so the HAL
 * can optimize preview vs still surfaces (`BUILD_PLAN` stream use cases). Failures are swallowed
 * per-surface so we still produce a valid config list.
 */
internal fun outputConfigurationsWithOptionalStreamUseCases(
    surfaces: List<Surface>,
    enableHints: Boolean,
): List<OutputConfiguration> {
    if (!enableHints || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return surfaces.map { OutputConfiguration(it) }
    }
    return surfaces.mapIndexed { index, surface ->
        OutputConfiguration(surface).apply {
            runCatching {
                val useCase: Long =
                    if (index == 0) {
                        CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW.toLong()
                    } else {
                        CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE.toLong()
                    }
                setStreamUseCase(useCase)
            }.onFailure { e ->
                Log.w(TAG, "setStreamUseCase idx=$index: ${e.message}")
            }
        }
    }
}

/**
 * Prefer [CameraDevice.createCaptureSession] with [SessionConfiguration] (API 28+) over the
 * deprecated list/handler overload.
 *
 * @param streamUseCaseHints When true (API 33+), applies [OutputConfiguration.setStreamUseCase]
 *   hints; callers should retry with **false** if [createCaptureSession] fails on picky HALs.
 */
internal fun CameraDevice.createCaptureSessionRegularOutputs(
    surfaces: List<Surface>,
    handler: Handler,
    callback: CameraCaptureSession.StateCallback,
    streamUseCaseHints: Boolean = false,
) {
    val outputConfigs = outputConfigurationsWithOptionalStreamUseCases(surfaces, streamUseCaseHints)
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
