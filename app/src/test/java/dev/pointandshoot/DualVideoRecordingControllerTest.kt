package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualVideoRecordingControllerTest {
    @Test
    fun wired_v1Caps() {
        assertTrue(DualVideoRecordingController.IS_WIRED)
        assertEquals(1920, DualVideoRecordingController.V1_MAX_LONG_EDGE_PX)
        assertEquals(30, DualVideoRecordingController.V1_TARGET_FPS)
        assertEquals(1080, DualVideoRecordingController.COMPOSITE_RECORD_WIDTH)
        assertEquals(1920, DualVideoRecordingController.COMPOSITE_RECORD_HEIGHT)
    }
}
