package dev.pointandshoot

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewFlashPolicyTest {

    @Test
    fun aeModeForAutoProgram_respectsFlashOffVsAuto() {
        val withAutoFlash =
            intArrayOf(
                CaptureRequest.CONTROL_AE_MODE_ON,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH,
            )
        assertEquals(
            CaptureRequest.CONTROL_AE_MODE_ON,
            PreviewFlashPolicy.aeModeForAutoProgramWithFlashPref(withAutoFlash, PreviewFlashMode.Off),
        )
        assertEquals(
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH,
            PreviewFlashPolicy.aeModeForAutoProgramWithFlashPref(withAutoFlash, PreviewFlashMode.Auto),
        )
    }

    @Test
    fun aeModeForAutoProgram_torchUsesOn() {
        val modes = intArrayOf(CaptureRequest.CONTROL_AE_MODE_ON, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        assertEquals(
            CaptureRequest.CONTROL_AE_MODE_ON,
            PreviewFlashPolicy.aeModeForAutoProgramWithFlashPref(modes, PreviewFlashMode.Torch),
        )
    }

    @Test
    fun aeModeForAutoProgram_returnsNullWhenOnUnavailable() {
        assertNull(PreviewFlashPolicy.aeModeForAutoProgramWithFlashPref(intArrayOf(), PreviewFlashMode.Auto))
    }

    @Test
    fun previewFlashMode_cycleWraps() {
        assertEquals(PreviewFlashMode.On, PreviewFlashMode.Auto.cycle())
        assertEquals(PreviewFlashMode.Off, PreviewFlashMode.Torch.cycle())
    }

    @Test
    fun fromStorageOrdinal_clamps() {
        assertEquals(PreviewFlashMode.Torch, PreviewFlashMode.fromStorageOrdinal(99))
        assertEquals(PreviewFlashMode.Off, PreviewFlashMode.fromStorageOrdinal(0))
    }

    @Test
    fun flashStrengthLevelForHardware_nullWhenMaxBelowOne() {
        assertNull(PreviewFlashPolicy.flashStrengthLevelForHardware(1, 0))
    }

    @Test
    fun flashStrengthLevelForHardware_usesDefaultClamped() {
        assertEquals(2, PreviewFlashPolicy.flashStrengthLevelForHardware(2, 5))
        assertEquals(1, PreviewFlashPolicy.flashStrengthLevelForHardware(0, 3))
        assertEquals(5, PreviewFlashPolicy.flashStrengthLevelForHardware(99, 5))
    }

    @Test
    fun flashStrengthLevelForHardware_fallsBackToMaxWhenDefaultNull() {
        assertEquals(4, PreviewFlashPolicy.flashStrengthLevelForHardware(null, 4))
    }
}
