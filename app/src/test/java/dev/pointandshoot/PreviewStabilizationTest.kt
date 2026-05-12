package dev.pointandshoot

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewStabilizationTest {

    @Test
    fun pickOptical_prefersOnWhenPresent() {
        assertEquals(
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON,
            PreviewStabilization.pickOpticalStabilizationMode(
                intArrayOf(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON,
                ),
            ),
        )
    }

    @Test
    fun pickOptical_returnsNullWhenOnlyOff() {
        assertNull(
            PreviewStabilization.pickOpticalStabilizationMode(
                intArrayOf(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF),
            ),
        )
    }

    @Test
    fun pickVideo_prefersOnWhenPresent() {
        assertEquals(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
            PreviewStabilization.pickVideoStabilizationMode(
                intArrayOf(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
                ),
            ),
        )
    }
}
