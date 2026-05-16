package dev.pointandshoot

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
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
 *
 * **Logical multi-camera + RAW:** [physicalPinnedSurfaceIndices] is reserved for HALs that supply
 * per-physical [android.hardware.camera2.TotalCaptureResult] entries for still capture. On stacks
 * where [TotalCaptureResult.getPhysicalCameraTotalResults] is **empty** while RAW is still pinned
 * to a physical id, [android.hardware.camera2.DngCreator] gets **logical** metadata for **physical**
 * pixels → dark / green DNG. **Shipped default:** pass **null** so only output **0** (preview) is
 * pinned ([previewPhysicalCameraId] set, indices default to `setOf(0)`). Callers may pass a
 * non-null index set only with USB proof that physical totals populate for those outputs.
 */
internal fun outputConfigurationsWithOptionalStreamUseCases(
    surfaces: List<Surface>,
    enableHints: Boolean,
    previewDynamicRangeProfile: Long? = null,
    /** Logical multi-camera: physical camera id for pinned outputs (API 28+). */
    previewPhysicalCameraId: String? = null,
    /**
     * When non-null and [previewPhysicalCameraId] is set, pins these indices into [previewPhysicalCameraId].
     * When null and [previewPhysicalCameraId] is set, pins index **0** only (legacy).
     */
    physicalPinnedSurfaceIndices: Set<Int>? = null,
): List<OutputConfiguration> {
    val pinId = previewPhysicalCameraId?.takeIf { it.isNotBlank() }
    val indicesToPin: Set<Int>? =
        when {
            pinId == null -> null
            physicalPinnedSurfaceIndices != null -> physicalPinnedSurfaceIndices
            else -> setOf(0)
        }
    return surfaces.mapIndexed { index, surface ->
        OutputConfiguration(surface).apply {
            val pinHere = indicesToPin?.contains(index) == true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && pinHere) {
                runCatching { setPhysicalCameraId(pinId!!) }
                    .onFailure { e ->
                        Log.w(TAG, "setPhysicalCameraId=$pinId idx=$index: ${e.message}")
                    }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && index == 0 && previewDynamicRangeProfile != null) {
                runCatching { setDynamicRangeProfile(previewDynamicRangeProfile) }
                    .onFailure { e ->
                        Log.w(TAG, "setDynamicRangeProfile idx=0: ${e.message}")
                    }
            }
            if (enableHints && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
}

/**
 * Prefer [CameraDevice.createCaptureSession] with [SessionConfiguration] (API 28+) over the
 * deprecated list/handler overload.
 *
 * @param streamUseCaseHints When true (API 33+), applies [OutputConfiguration.setStreamUseCase]
 *   hints; callers should retry with **false** if [createCaptureSession] fails on picky HALs.
 * @param previewDynamicRangeProfile When non-null (API 33+), applies [OutputConfiguration.setDynamicRangeProfile]
 *   on the first output (preview surface). Callers should retry with **null** if session create fails.
 * @param sessionParametersTemplate When non-null (API 33+), applies [SessionConfiguration.setSessionParameters]
 *   before [createCaptureSession]. Callers should retry with **null** if the HAL rejects the bundle.
 */
internal fun CameraDevice.createCaptureSessionRegularOutputs(
    surfaces: List<Surface>,
    handler: Handler,
    callback: CameraCaptureSession.StateCallback,
    streamUseCaseHints: Boolean = false,
    previewDynamicRangeProfile: Long? = null,
    sessionParametersTemplate: CaptureRequest? = null,
    previewPhysicalCameraId: String? = null,
    physicalPinnedSurfaceIndices: Set<Int>? = null,
) {
    val outputConfigs =
        outputConfigurationsWithOptionalStreamUseCases(
            surfaces,
            enableHints = streamUseCaseHints,
            previewDynamicRangeProfile = previewDynamicRangeProfile,
            previewPhysicalCameraId = previewPhysicalCameraId,
            physicalPinnedSurfaceIndices = physicalPinnedSurfaceIndices,
        )
    val sessionConfig =
        SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputConfigs,
            handlerExecutor(handler),
            callback,
        )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && sessionParametersTemplate != null) {
        runCatching { sessionConfig.setSessionParameters(sessionParametersTemplate) }
            .onFailure { e ->
                Log.w(TAG, "setSessionParameters: ${e.message}")
            }
    }
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
