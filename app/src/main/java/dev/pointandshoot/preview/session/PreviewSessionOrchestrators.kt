package dev.pointandshoot.preview.session

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.os.Handler
import android.view.Surface
import dev.pointandshoot.createCaptureSessionHighSpeedOutputs
import dev.pointandshoot.createCaptureSessionRegularOutputs

/**
 * Narrow Camera2 seams used by preview session orchestration.
 *
 * Keeps `PreviewEngineScreen` logic testable without changing behavior.
 */
interface PreviewCameraOpenGateway {
    fun openCamera(
        cameraId: String,
        callback: CameraDevice.StateCallback,
        handler: Handler,
    )
}

class AndroidPreviewCameraOpenGateway(
    private val cameraManager: CameraManager,
) : PreviewCameraOpenGateway {
    @SuppressLint("MissingPermission")
    override fun openCamera(
        cameraId: String,
        callback: CameraDevice.StateCallback,
        handler: Handler,
    ) {
        cameraManager.openCamera(cameraId, callback, handler)
    }
}

interface PreviewSessionCreateGateway {
    fun createRegularSession(
        camera: CameraDevice,
        surfaces: List<Surface>,
        additionalOutputConfigurations: List<OutputConfiguration>,
        handler: Handler,
        callback: CameraCaptureSession.StateCallback,
        streamUseCaseHints: Boolean,
        previewDynamicRangeProfile: Long?,
        sessionParametersTemplate: CaptureRequest?,
        previewPhysicalCameraId: String?,
        physicalPinnedSurfaceIndices: Set<Int>?,
    )

    fun createHighSpeedSession(
        camera: CameraDevice,
        surfaces: List<Surface>,
        handler: Handler,
        callback: CameraCaptureSession.StateCallback,
        forceEncoderOutputSdr: Boolean,
    )
}

object AndroidPreviewSessionCreateGateway : PreviewSessionCreateGateway {
    override fun createRegularSession(
        camera: CameraDevice,
        surfaces: List<Surface>,
        additionalOutputConfigurations: List<OutputConfiguration>,
        handler: Handler,
        callback: CameraCaptureSession.StateCallback,
        streamUseCaseHints: Boolean,
        previewDynamicRangeProfile: Long?,
        sessionParametersTemplate: CaptureRequest?,
        previewPhysicalCameraId: String?,
        physicalPinnedSurfaceIndices: Set<Int>?,
    ) {
        camera.createCaptureSessionRegularOutputs(
            surfaces = surfaces,
            additionalOutputConfigurations = additionalOutputConfigurations,
            handler = handler,
            callback = callback,
            streamUseCaseHints = streamUseCaseHints,
            previewDynamicRangeProfile = previewDynamicRangeProfile,
            sessionParametersTemplate = sessionParametersTemplate,
            previewPhysicalCameraId = previewPhysicalCameraId,
            physicalPinnedSurfaceIndices = physicalPinnedSurfaceIndices,
        )
    }

    override fun createHighSpeedSession(
        camera: CameraDevice,
        surfaces: List<Surface>,
        handler: Handler,
        callback: CameraCaptureSession.StateCallback,
        forceEncoderOutputSdr: Boolean,
    ) {
        camera.createCaptureSessionHighSpeedOutputs(
            surfaces = surfaces,
            handler = handler,
            callback = callback,
            forceEncoderOutputSdr = forceEncoderOutputSdr,
        )
    }
}
