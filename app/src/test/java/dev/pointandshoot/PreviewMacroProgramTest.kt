package dev.pointandshoot

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun `best macro camera prefers highest close-focus capability`() {
        val picked =
            PreviewMacroProgram.pickBestMacroCameraId(
                candidates =
                    listOf(
                        PreviewMacroProgram.MacroCameraCandidate("uw", minimumFocusDistanceDiopters = 22f, focalLengthMm = 1.8f),
                        PreviewMacroProgram.MacroCameraCandidate("macro", minimumFocusDistanceDiopters = 35f, focalLengthMm = 2.2f),
                        PreviewMacroProgram.MacroCameraCandidate("wide", minimumFocusDistanceDiopters = 10f, focalLengthMm = 4.5f),
                    ),
                fallbackOrder = listOf("uw", "wide", "macro"),
            )
        assertEquals("macro", picked)
    }

    @Test
    fun `best macro camera falls back to close-focus then fallback order`() {
        val picked =
            PreviewMacroProgram.pickBestMacroCameraId(
                candidates =
                    listOf(
                        PreviewMacroProgram.MacroCameraCandidate("uw", minimumFocusDistanceDiopters = 12f, focalLengthMm = 1.8f),
                        PreviewMacroProgram.MacroCameraCandidate("wide", minimumFocusDistanceDiopters = 6f, focalLengthMm = 4.5f),
                    ),
                fallbackOrder = listOf("wide", "uw"),
            )
        assertEquals("uw", picked)
    }
}
