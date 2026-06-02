package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewStatusBarLineTest {

    @Test
    fun previewStatusBarLine_prefersCaptureHint() {
        assertEquals(
            "Saving DNG…",
            previewStatusBarLine(
                burstTelemetryHint = null,
                capturePipelineHint = "Saving DNG…",
                focalMapCalibratingHint = true,
                sessionStatus = "Ready",
            ),
        )
    }

    @Test
    fun previewStatusBarLine_focalMapWhenNoCaptureHint() {
        assertEquals(
            "Calibrating focal map…",
            previewStatusBarLine(
                burstTelemetryHint = null,
                capturePipelineHint = null,
                focalMapCalibratingHint = true,
                sessionStatus = "Ready",
            ),
        )
    }

    @Test
    fun previewStatusBarLine_ignoresIdleStatus() {
        assertNull(
            previewStatusBarLine(
                burstTelemetryHint = null,
                capturePipelineHint = null,
                focalMapCalibratingHint = false,
                sessionStatus = "Idle",
            ),
        )
    }

    @Test
    fun previewStatusBarLine_showsNonIdleSessionStatus() {
        assertEquals(
            "Opening camera…",
            previewStatusBarLine(
                burstTelemetryHint = null,
                capturePipelineHint = null,
                focalMapCalibratingHint = false,
                sessionStatus = "Opening camera…",
            ),
        )
    }

    @Test
    fun previewStatusBarLine_prefersBurstTelemetry() {
        assertEquals(
            "Burst 14.8 fps (target 30) q=2",
            previewStatusBarLine(
                burstTelemetryHint = "Burst 14.8 fps (target 30) q=2",
                capturePipelineHint = "Saving DNG…",
                focalMapCalibratingHint = true,
                sessionStatus = "Opening camera…",
            ),
        )
    }
}
