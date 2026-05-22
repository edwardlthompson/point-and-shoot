package dev.pointandshoot

import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest

/**
 * Macro shooting program (dial **MACRO** or focus picker **Macro AF**): ultra-wide camera,
 * HAL [CaptureRequest.CONTROL_AF_MODE_MACRO], and OPLUS close-up vendor key when advertised.
 */
object PreviewMacroProgram {
    fun wantsMacroProgram(
        commandDialMode: CommandDialMode,
        focusSelection: PreviewFocusSelection,
    ): Boolean =
        commandDialMode == CommandDialMode.Macro ||
            (
                focusSelection is PreviewFocusSelection.HalAf &&
                    focusSelection.mode == CaptureRequest.CONTROL_AF_MODE_MACRO
            )

    fun ultraWideCameraId(cm: CameraManager, cameraIds: List<String>): String? =
        BackCameraRoleResolver.resolve(cm, cameraIds).ultraWide

    fun preferredFocusSelectionForDialMacro(
        menuSelections: List<PreviewFocusSelection>,
    ): PreviewFocusSelection? =
        menuSelections.firstOrNull {
            it is PreviewFocusSelection.HalAf &&
                it.mode == CaptureRequest.CONTROL_AF_MODE_MACRO
        }
            ?: menuSelections.firstOrNull { it == PreviewFocusSelection.Auto }
}
