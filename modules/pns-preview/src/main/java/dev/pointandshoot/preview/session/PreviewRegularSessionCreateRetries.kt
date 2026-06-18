package dev.pointandshoot.preview.session

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.os.Handler
import android.util.Log
import android.view.Surface
import dev.pointandshoot.PreviewDynamicRangeLabels

/**
 * REGULAR preview session create with HAL-friendly fallbacks (H.CRI-5 slice 3).
 *
 * Extracted from `PreviewEngineScreen` — stream hints, HDR dynamic range, research session
 * parameters, and preview physical pin retries unchanged.
 */
object PreviewRegularSessionCreateRetries {
    data class PhysicalPinState(
        var previewPhysicalCameraId: String?,
        var physicalPinnedSurfaceIndices: Set<Int>?,
    )

    fun create(
        logTag: String,
        gateway: PreviewSessionCreateGateway,
        camera: CameraDevice,
        surfaces: List<Surface>,
        additionalOutputConfigurations: List<OutputConfiguration> = emptyList(),
        handler: Handler,
        callback: CameraCaptureSession.StateCallback,
        streamHints: Boolean,
        chosenPreviewDr: Long?,
        sessionParametersTemplate: CaptureRequest?,
        physicalPin: PhysicalPinState,
        onPreviewDynamicRangeShort: (String?) -> Unit,
        onPhysicalPinCleared: () -> Unit,
    ): Throwable? {
        var pinPhys = physicalPin.previewPhysicalCameraId?.takeIf { it.isNotBlank() }
        var pinSurfaceIndices = physicalPin.physicalPinnedSurfaceIndices
        if (pinPhys != null && surfaces.size > 1) {
            Log.w(
                logTag,
                "preview OutputConfiguration physical pin with surfaces=${surfaces.size} " +
                    "indices=${pinSurfaceIndices ?: setOf(0)} (HAL may require retry without pin)",
            )
        }

        fun tryOnce(hints: Boolean, previewDr: Long?, sessPar: CaptureRequest?): Throwable? =
            runCatching {
                gateway.createRegularSession(
                    camera = camera,
                    surfaces = surfaces,
                    additionalOutputConfigurations = additionalOutputConfigurations,
                    handler = handler,
                    callback = callback,
                    streamUseCaseHints = hints,
                    previewDynamicRangeProfile = previewDr,
                    sessionParametersTemplate = sessPar,
                    previewPhysicalCameraId = pinPhys,
                    physicalPinnedSurfaceIndices = pinSurfaceIndices,
                )
                onPreviewDynamicRangeShort(previewDr?.let { dr -> PreviewDynamicRangeLabels.shortLabel(dr) })
            }.exceptionOrNull()

        var createErr = tryOnce(streamHints, chosenPreviewDr, sessionParametersTemplate)
        if (createErr != null && pinPhys != null) {
            Log.w(
                logTag,
                "createCaptureSession retry without preview physical pin (was $pinPhys): " +
                    "${createErr::class.java.simpleName}: ${createErr.message}",
            )
            pinPhys = null
            pinSurfaceIndices = null
            physicalPin.previewPhysicalCameraId = null
            physicalPin.physicalPinnedSurfaceIndices = null
            onPhysicalPinCleared()
            createErr = tryOnce(streamHints, chosenPreviewDr, sessionParametersTemplate)
        }
        if (createErr != null && sessionParametersTemplate != null) {
            Log.w(
                logTag,
                "createCaptureSession retry without research session parameters " +
                    "(${createErr::class.java.simpleName}: ${createErr.message})",
            )
            createErr = tryOnce(streamHints, chosenPreviewDr, null)
        }
        if (createErr != null && chosenPreviewDr != null) {
            Log.w(
                logTag,
                "createCaptureSession retry without HDR dynamic range " +
                    "(${createErr::class.java.simpleName}: ${createErr.message})",
            )
            createErr = tryOnce(streamHints, null, null)
        }
        if (createErr != null && streamHints) {
            Log.w(
                logTag,
                "createCaptureSession (stream hints) threw ${createErr::class.java.simpleName}: " +
                    "${createErr.message}; retry without hints",
            )
            createErr = tryOnce(false, chosenPreviewDr, null)
            if (createErr != null && chosenPreviewDr != null) {
                createErr = tryOnce(false, null, null)
            }
        }
        return createErr
    }
}
