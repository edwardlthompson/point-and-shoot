package dev.pointandshoot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Saved dial/focus for macro pairing (extracted from [PreviewEngineContent] for DEX size). */
class MacroFocusDialCouplingState {
    var focusRestoreBeforeMacroDial: PreviewFocusSelection? = null
    var dialRestoreBeforeMacroFocus: CommandDialMode? = null

    fun applyDialChange(
        previousDial: CommandDialMode,
        newDial: CommandDialMode,
        currentFocus: PreviewFocusSelection,
        menuSelections: List<PreviewFocusSelection>,
        setFocus: (PreviewFocusSelection) -> Unit,
    ): CommandDialMode {
        if (newDial == previousDial) return newDial
        val coupling =
            PreviewFocusDialCoupling.onDialSelected(
                previousDial = previousDial,
                newDial = newDial,
                currentFocus = currentFocus,
                focusSavedForMacroDial = focusRestoreBeforeMacroDial,
                dialSavedForMacroFocus = dialRestoreBeforeMacroFocus,
                menuSelections = menuSelections,
            )
        focusRestoreBeforeMacroDial = coupling.focusSavedForMacroDial
        dialRestoreBeforeMacroFocus = coupling.dialSavedForMacroFocus
        coupling.newFocus?.let { setFocus(it) }
        return newDial
    }

    fun applyFocusPick(
        pick: PreviewFocusSelection,
        currentDial: CommandDialMode,
        setFocus: (PreviewFocusSelection) -> Unit,
    ): CommandDialMode {
        val coupling =
            PreviewFocusDialCoupling.onFocusSelected(
                newFocus = pick,
                currentDial = currentDial,
                dialSavedForMacroFocus = dialRestoreBeforeMacroFocus,
            )
        dialRestoreBeforeMacroFocus = coupling.dialSavedForMacroFocus
        var dial = currentDial
        coupling.newDial?.let { dial = it }
        setFocus(pick)
        if (pick == PreviewFocusSelection.Auto && dial == CommandDialMode.M) {
            dial = CommandDialMode.Auto
        }
        return dial
    }
}

@Composable
fun rememberMacroFocusDialCouplingState(): MacroFocusDialCouplingState =
    remember { MacroFocusDialCouplingState() }
