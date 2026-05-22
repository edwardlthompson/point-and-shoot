package dev.pointandshoot

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewMacroProgramTest {

    @Test
    fun `macro dial activates program`() {
        assertTrue(
            PreviewMacroProgram.wantsMacroProgram(
                CommandDialMode.Macro,
                PreviewFocusSelection.Auto,
            ),
        )
    }

    @Test
    fun `macro af focus activates program`() {
        assertTrue(
            PreviewMacroProgram.wantsMacroProgram(
                CommandDialMode.Auto,
                PreviewFocusSelection.HalAf(CaptureRequest.CONTROL_AF_MODE_MACRO),
            ),
        )
    }

    @Test
    fun `auto focus on auto dial is not macro program`() {
        assertFalse(
            PreviewMacroProgram.wantsMacroProgram(
                CommandDialMode.Auto,
                PreviewFocusSelection.Auto,
            ),
        )
    }
}
