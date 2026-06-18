package dev.pointandshoot.preview.session

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class PreviewRegularSessionCreateRetriesTest {

    private val bgThread = HandlerThread("retry-test").apply { start() }
    private val handler = Handler(bgThread.looper)
    private val camera = mock(CameraDevice::class.java)
    private val surface = mock(Surface::class.java)
    private val callback = mock(CameraCaptureSession.StateCallback::class.java)

    @Test
    fun succeedsOnFirstAttempt() {
        val gateway = RecordingGateway()
        val pin = PreviewRegularSessionCreateRetries.PhysicalPinState(null, null)
        var drShort: String? = "unset"
        val err =
            PreviewRegularSessionCreateRetries.create(
                logTag = "test",
                gateway = gateway,
                camera = camera,
                surfaces = listOf(surface),
                handler = handler,
                callback = callback,
                streamHints = false,
                chosenPreviewDr = null,
                sessionParametersTemplate = null,
                physicalPin = pin,
                onPreviewDynamicRangeShort = { drShort = it },
                onPhysicalPinCleared = {},
            )
        assertNull(err)
        assertEquals(1, gateway.attempts)
        assertNull(drShort)
    }

    private class RecordingGateway : PreviewSessionCreateGateway {
        var attempts = 0

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
            attempts++
        }

        override fun createHighSpeedSession(
            camera: CameraDevice,
            surfaces: List<Surface>,
            handler: Handler,
            callback: CameraCaptureSession.StateCallback,
            forceEncoderOutputSdr: Boolean,
        ) = Unit
    }
}
