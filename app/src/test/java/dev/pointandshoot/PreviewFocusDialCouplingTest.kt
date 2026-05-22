package dev.pointandshoot

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewFocusDialCouplingTest {
    private val macroAf =
        PreviewFocusSelection.HalAf(CaptureRequest.CONTROL_AF_MODE_MACRO)
    private val caf =
        PreviewFocusSelection.HalAf(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

    @Test
    fun leavingMacroDialRestoresSavedFocus() {
        val r =
            PreviewFocusDialCoupling.onDialSelected(
                previousDial = CommandDialMode.Macro,
                newDial = CommandDialMode.Auto,
                currentFocus = macroAf,
                focusSavedForMacroDial = caf,
                dialSavedForMacroFocus = null,
                menuSelections = listOf(PreviewFocusSelection.Auto, macroAf),
            )
        assertEquals(caf, r.newFocus)
        assertNull(r.focusSavedForMacroDial)
    }

    @Test
    fun enteringMacroDialSavesFocusAndAppliesMacroAf() {
        val r =
            PreviewFocusDialCoupling.onDialSelected(
                previousDial = CommandDialMode.Auto,
                newDial = CommandDialMode.Macro,
                currentFocus = caf,
                focusSavedForMacroDial = null,
                dialSavedForMacroFocus = null,
                menuSelections = listOf(PreviewFocusSelection.Auto, macroAf),
            )
        assertEquals(caf, r.focusSavedForMacroDial)
        assertEquals(CommandDialMode.Auto, r.dialSavedForMacroFocus)
        assertEquals(macroAf, r.newFocus)
    }

    @Test
    fun leavingMacroFocusRestoresSavedDial() {
        val r =
            PreviewFocusDialCoupling.onFocusSelected(
                newFocus = PreviewFocusSelection.Auto,
                currentDial = CommandDialMode.Macro,
                dialSavedForMacroFocus = CommandDialMode.H,
            )
        assertEquals(CommandDialMode.H, r.newDial)
        assertNull(r.dialSavedForMacroFocus)
    }

    @Test
    fun leavingMacroDialWithoutSavedFocusClearsMacroAf() {
        val r =
            PreviewFocusDialCoupling.onDialSelected(
                previousDial = CommandDialMode.Macro,
                newDial = CommandDialMode.Auto,
                currentFocus = macroAf,
                focusSavedForMacroDial = null,
                dialSavedForMacroFocus = null,
                menuSelections = listOf(PreviewFocusSelection.Auto, macroAf),
            )
        assertEquals(PreviewFocusSelection.Auto, r.newFocus)
        assertNull(r.focusSavedForMacroDial)
    }

    @Test
    fun enteringMacroFocusSavesDialWithoutChangingIt() {
        val r =
            PreviewFocusDialCoupling.onFocusSelected(
                newFocus = macroAf,
                currentDial = CommandDialMode.H,
                dialSavedForMacroFocus = null,
            )
        assertNull(r.newDial)
        assertEquals(CommandDialMode.H, r.dialSavedForMacroFocus)
    }
}
