package dev.pointandshoot.preview.session

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class PreviewSessionHighSpeedOutputsTest {

    @Test
    fun isEncoderOnlyHfrRecording_requiresAllFlags() {
        assertTrue(
            PreviewSessionHighSpeedOutputs.isEncoderOnlyHfrRecording(
                useInterleavedMcPreview = true,
                inAppVideoRecordingArmed = true,
                recorderPresent = true,
            ),
        )
        assertEquals(
            false,
            PreviewSessionHighSpeedOutputs.isEncoderOnlyHfrRecording(
                useInterleavedMcPreview = false,
                inAppVideoRecordingArmed = true,
                recorderPresent = true,
            ),
        )
    }

    @Test
    fun resolveEncoderOutputPlan_passthroughWhenNotEncoderOnly() {
        val surface = mock(Surface::class.java)
        `when`(surface.isValid).thenReturn(true)
        val plan =
            PreviewSessionHighSpeedOutputs.resolveEncoderOutputPlan(
                PreviewSessionHighSpeedOutputs.EncoderOutputInput(
                    surfaces = listOf(surface),
                    previewSurface = surface,
                    encoderSurface = null,
                    encOnlyHfr = false,
                    encOnlyUhd = false,
                    skipEncoderOnlyMonitor = false,
                    hfrMonitorStartSucceeded = false,
                    uhdMonitorStartSucceeded = false,
                    desiredFps = 120,
                    bufferWidth = 1920,
                    bufferHeight = 1080,
                    preferInterleavedFallback = false,
                    hfrInterleavedTag = "HfrInterleaved",
                    uhd60Tag = "Uhd60",
                ),
            )
        assertEquals(listOf(surface), plan.outputs)
    }

    @Test
    fun resolveEncoderOutputPlan_encoderOnlyMonitor() {
        val enc = mock(Surface::class.java)
        `when`(enc.isValid).thenReturn(true)
        val preview = mock(Surface::class.java)
        val plan =
            PreviewSessionHighSpeedOutputs.resolveEncoderOutputPlan(
                PreviewSessionHighSpeedOutputs.EncoderOutputInput(
                    surfaces = emptyList(),
                    previewSurface = preview,
                    encoderSurface = enc,
                    encOnlyHfr = true,
                    encOnlyUhd = false,
                    skipEncoderOnlyMonitor = false,
                    hfrMonitorStartSucceeded = true,
                    uhdMonitorStartSucceeded = false,
                    desiredFps = 120,
                    bufferWidth = 1920,
                    bufferHeight = 1080,
                    preferInterleavedFallback = false,
                    hfrInterleavedTag = "HfrInterleaved",
                    uhd60Tag = "Uhd60",
                ),
            )
        assertEquals(listOf(enc), plan.outputs)
        assertEquals("encoder_only_monitor", plan.routeId)
    }
}
