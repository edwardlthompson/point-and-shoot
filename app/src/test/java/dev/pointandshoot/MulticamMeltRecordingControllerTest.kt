package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MulticamMeltRecordingControllerTest {
    @Test
    fun arm_capsByThermalAndHal() {
        MulticamMeltRecordingController.disarm()
        val state =
            MulticamMeltRecordingController.arm(
                requestedCameras = 4,
                thermalStatus = android.os.PowerManager.THERMAL_STATUS_MODERATE,
                halMaxConcurrent = 4,
                cameraIds = listOf("2", "3", "4", "5"),
            )
        assertTrue(state.active)
        assertEquals(2, state.cameraIds.size)
        MulticamMeltRecordingController.disarm()
        assertEquals(null, MulticamMeltRecordingController.lastArmState)
    }

    @Test
    fun arm_inactiveWithSingleCamera() {
        val state =
            MulticamMeltRecordingController.arm(
                requestedCameras = 2,
                thermalStatus = android.os.PowerManager.THERMAL_STATUS_NONE,
                halMaxConcurrent = 1,
                cameraIds = listOf("2"),
            )
        assertFalse(state.active)
        assertEquals(1, state.cameraIds.size)
    }
}
