package dev.pointandshoot

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewFocusModeTest {

    @Test
    fun `menu includes auto and manual when off available`() {
        val modes =
            intArrayOf(
                CaptureRequest.CONTROL_AF_MODE_AUTO,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                CaptureRequest.CONTROL_AF_MODE_OFF,
            )
        val menu = PreviewFocusMode.menuSelections(modes)
        assertEquals(PreviewFocusSelection.Auto, menu.first())
        assertTrue(menu.any { it == PreviewFocusSelection.ManualDistance })
        assertTrue(menu.any { it is PreviewFocusSelection.HalAf })
    }

    @Test
    fun `parse adb manual and auto`() {
        assertEquals(PreviewFocusSelection.Auto, PreviewFocusMode.parseAdbExtra("auto"))
        assertEquals(PreviewFocusSelection.ManualDistance, PreviewFocusMode.parseAdbExtra("manual"))
    }

    @Test
    fun `chip label for manual infinity`() {
        assertEquals("∞", PreviewFocusMode.chipValue(PreviewFocusSelection.ManualDistance, 0f))
    }
}
