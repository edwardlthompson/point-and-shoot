package dev.pointandshoot

import android.hardware.camera2.CaptureRequest

/**
 * Pairs command dial **MACRO** with focus picker **Macro AF**: leaving one restores the other.
 */
object PreviewFocusDialCoupling {
    fun isMacroFocusSelection(selection: PreviewFocusSelection): Boolean =
        selection is PreviewFocusSelection.HalAf &&
            selection.mode == CaptureRequest.CONTROL_AF_MODE_MACRO

    /**
     * User changed the shooting-mode dial.
     * @return focus to apply after the dial change, or null to leave focus unchanged.
     */
    fun onDialSelected(
        previousDial: CommandDialMode,
        newDial: CommandDialMode,
        currentFocus: PreviewFocusSelection,
        focusSavedForMacroDial: PreviewFocusSelection?,
        dialSavedForMacroFocus: CommandDialMode?,
        menuSelections: List<PreviewFocusSelection>,
    ): DialSelectionResult {
        val leavingMacroDial =
            previousDial == CommandDialMode.Macro && newDial != CommandDialMode.Macro
        val enteringMacroDial =
            newDial == CommandDialMode.Macro && previousDial != CommandDialMode.Macro
        var focusSaved = focusSavedForMacroDial
        var dialSaved = dialSavedForMacroFocus
        var newFocus: PreviewFocusSelection? = null

        if (leavingMacroDial) {
            newFocus =
                when {
                    focusSaved != null -> focusSaved.also { focusSaved = null }
                    isMacroFocusSelection(currentFocus) -> nonMacroFocus(menuSelections)
                    else -> null
                }
        }
        if (enteringMacroDial) {
            if (focusSaved == null && !isMacroFocusSelection(currentFocus)) {
                focusSaved = currentFocus
            }
            if (dialSaved == null) {
                dialSaved = previousDial
            }
            newFocus =
                PreviewMacroProgram.preferredFocusSelectionForDialMacro(menuSelections)
                    ?: newFocus
        }
        return DialSelectionResult(
            newFocus = newFocus,
            focusSavedForMacroDial = focusSaved,
            dialSavedForMacroFocus = dialSaved,
        )
    }

    /**
     * User picked a focus mode in the AF picker.
     * @return dial to apply after the focus change, or null to leave the dial unchanged.
     */
    fun onFocusSelected(
        newFocus: PreviewFocusSelection,
        currentDial: CommandDialMode,
        dialSavedForMacroFocus: CommandDialMode?,
    ): FocusSelectionResult {
        val willMacro = isMacroFocusSelection(newFocus)
        var dialSaved = dialSavedForMacroFocus
        var newDial: CommandDialMode? = null

        if (dialSaved != null && !willMacro) {
            newDial = dialSaved
            dialSaved = null
        }
        if (willMacro && currentDial != CommandDialMode.Macro && dialSaved == null) {
            dialSaved = currentDial
        }
        return FocusSelectionResult(
            newDial = newDial,
            dialSavedForMacroFocus = dialSaved,
        )
    }

    data class DialSelectionResult(
        val newFocus: PreviewFocusSelection?,
        val focusSavedForMacroDial: PreviewFocusSelection?,
        val dialSavedForMacroFocus: CommandDialMode?,
    )

    data class FocusSelectionResult(
        val newDial: CommandDialMode?,
        val dialSavedForMacroFocus: CommandDialMode?,
    )

    /** First picker row that is not Macro AF; used when leaving macro dial without a saved focus. */
    fun nonMacroFocus(menuSelections: List<PreviewFocusSelection>): PreviewFocusSelection =
        menuSelections.firstOrNull { !isMacroFocusSelection(it) }
            ?: PreviewFocusSelection.Auto
}
