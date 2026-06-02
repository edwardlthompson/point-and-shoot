package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import dev.pointandshoot.fleet.FleetCameraProfiles

/**
 * Macro shooting program (dial **MACRO** or focus picker **Macro AF**): ultra-wide camera,
 * HAL [CaptureRequest.CONTROL_AF_MODE_MACRO], and OPLUS close-up vendor key when advertised.
 */
object PreviewMacroProgram {
    data class MacroCameraCandidate(
        val cameraId: String,
        val minimumFocusDistanceDiopters: Float,
        val focalLengthMm: Float?,
    )

    fun wantsMacroProgram(
        commandDialMode: CommandDialMode,
        focusSelection: PreviewFocusSelection,
    ): Boolean =
        commandDialMode == CommandDialMode.Macro ||
            (
                focusSelection is PreviewFocusSelection.HalAf &&
                    focusSelection.mode == CaptureRequest.CONTROL_AF_MODE_MACRO
            )

    fun bestMacroCameraId(
        context: Context,
        cm: CameraManager,
        cameraIds: List<String>,
    ): String? {
        val roles = FleetCameraProfiles.resolvedRoles(context.applicationContext, cameraIds)
        val fallbackOrder =
            buildList {
                roles.ultraWide?.let { add(it) }
                roles.wide?.let { if (it !in this) add(it) }
                cameraIds
                    .asSequence()
                    .filter { it != "1" && it !in this }
                    .forEach { add(it) }
            }
        val candidates =
            cameraIds.mapNotNull { id ->
                val chars = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: return@mapNotNull null
                if (chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
                    return@mapNotNull null
                }
                MacroCameraCandidate(
                    cameraId = id,
                    minimumFocusDistanceDiopters =
                        chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
                    focalLengthMm =
                        chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull(),
                )
            }
        return pickBestMacroCameraId(candidates, fallbackOrder)
    }

    internal fun pickBestMacroCameraId(
        candidates: List<MacroCameraCandidate>,
        fallbackOrder: List<String>,
    ): String? {
        val threshold = LensInfoSummary.MACRO_MIN_DIOPTERS_THRESHOLD
        val fallbackIndex = fallbackOrder.withIndex().associate { it.value to it.index }
        val macroCapable =
            candidates
                .asSequence()
                .filter { it.minimumFocusDistanceDiopters >= threshold }
                .sortedWith(
                    compareByDescending<MacroCameraCandidate> { it.minimumFocusDistanceDiopters }
                        .thenBy { it.focalLengthMm ?: Float.MAX_VALUE }
                        .thenBy { fallbackIndex[it.cameraId] ?: Int.MAX_VALUE },
                ).toList()
        if (macroCapable.isNotEmpty()) return macroCapable.first().cameraId
        val fallbackCloseFocus =
            candidates
                .asSequence()
                .filter { it.minimumFocusDistanceDiopters > 0f }
                .sortedWith(
                    compareByDescending<MacroCameraCandidate> { it.minimumFocusDistanceDiopters }
                        .thenBy { fallbackIndex[it.cameraId] ?: Int.MAX_VALUE },
                ).map { it.cameraId }
                .firstOrNull()
        if (fallbackCloseFocus != null) return fallbackCloseFocus
        return fallbackOrder.firstOrNull()
    }

    fun preferredFocusSelectionForDialMacro(
        menuSelections: List<PreviewFocusSelection>,
    ): PreviewFocusSelection? =
        menuSelections.firstOrNull {
            it is PreviewFocusSelection.HalAf &&
                it.mode == CaptureRequest.CONTROL_AF_MODE_MACRO
        }
            ?: menuSelections.firstOrNull { it == PreviewFocusSelection.Auto }
}
