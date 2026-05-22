package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest

/**
 * User-selected preview AF program (Sprint **14.8**). Dial **M** / **S** still override via
 * [PreviewController.applyScalerCropAndMetering]; tap / face metering take precedence when active.
 */
sealed class PreviewFocusSelection {
    /** Continuous AF (picture preferred, then video, then single AF). */
    data object Auto : PreviewFocusSelection()

    /** [CaptureRequest.CONTROL_AF_MODE_OFF] + [CaptureRequest.LENS_FOCUS_DISTANCE]. */
    data object ManualDistance : PreviewFocusSelection()

    /** A specific HAL [CaptureRequest.CONTROL_AF_MODE_*] constant. */
    data class HalAf(val mode: Int) : PreviewFocusSelection()
}

object PreviewFocusMode {
    fun chipValue(selection: PreviewFocusSelection, diopters: Float?): String =
        when (selection) {
            PreviewFocusSelection.Auto -> "CAF"
            PreviewFocusSelection.ManualDistance ->
                ManualFocusDistance.formatDioptersShort(diopters)
            is PreviewFocusSelection.HalAf -> afModeChipLabel(selection.mode)
        }

    fun chromeUxLogValue(selection: PreviewFocusSelection, diopters: Float?): String =
        when (selection) {
            PreviewFocusSelection.Auto -> "auto"
            PreviewFocusSelection.ManualDistance ->
                "manual diopters=${diopters?.let { "%.3f".format(it) } ?: "default"}"
            is PreviewFocusSelection.HalAf -> "hal_${afModeLogSlug(selection.mode)}"
        }

    /** Menu rows for the focus picker (HAL-filtered, stable order). */
    fun menuSelections(afModes: IntArray): List<PreviewFocusSelection> {
        val set = afModes.toSet()
        val out = mutableListOf<PreviewFocusSelection>(PreviewFocusSelection.Auto)
        if (set.contains(CaptureRequest.CONTROL_AF_MODE_OFF)) {
            out += PreviewFocusSelection.ManualDistance
        }
        val halOrder =
            intArrayOf(
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                CaptureRequest.CONTROL_AF_MODE_AUTO,
                CaptureRequest.CONTROL_AF_MODE_MACRO,
                CaptureRequest.CONTROL_AF_MODE_EDOF,
            )
        for (mode in halOrder) {
            if (set.contains(mode)) {
                out += PreviewFocusSelection.HalAf(mode)
            }
        }
        return out.distinct()
    }

    fun parseAdbExtra(raw: String?): PreviewFocusSelection? {
        val t = raw?.trim()?.lowercase() ?: return null
        return when (t) {
            "auto", "caf" -> PreviewFocusSelection.Auto
            "manual", "m", "manual_distance" -> PreviewFocusSelection.ManualDistance
            "caf_video", "continuous_video", "video" ->
                PreviewFocusSelection.HalAf(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            "macro" -> PreviewFocusSelection.HalAf(CaptureRequest.CONTROL_AF_MODE_MACRO)
            "edof" -> PreviewFocusSelection.HalAf(CaptureRequest.CONTROL_AF_MODE_EDOF)
            "single", "auto_af" -> PreviewFocusSelection.HalAf(CaptureRequest.CONTROL_AF_MODE_AUTO)
            "picture", "continuous_picture" ->
                PreviewFocusSelection.HalAf(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            else -> null
        }
    }

    fun afModeMenuLabel(mode: Int): String =
        when (mode) {
            CaptureRequest.CONTROL_AF_MODE_OFF -> "Off (manual distance)"
            CaptureRequest.CONTROL_AF_MODE_AUTO -> "Single AF"
            CaptureRequest.CONTROL_AF_MODE_MACRO -> "Macro AF"
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "Continuous video AF"
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "Continuous picture AF"
            CaptureRequest.CONTROL_AF_MODE_EDOF -> "EDOF"
            else -> "AF mode $mode"
        }

    private fun afModeChipLabel(mode: Int): String =
        when (mode) {
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "CAF-P"
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "CAF-V"
            CaptureRequest.CONTROL_AF_MODE_AUTO -> "AF"
            CaptureRequest.CONTROL_AF_MODE_MACRO -> "MAC"
            CaptureRequest.CONTROL_AF_MODE_EDOF -> "EDOF"
            else -> "AF$mode"
        }

    private fun afModeLogSlug(mode: Int): String =
        when (mode) {
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "continuous_picture"
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "continuous_video"
            CaptureRequest.CONTROL_AF_MODE_AUTO -> "single_af"
            CaptureRequest.CONTROL_AF_MODE_MACRO -> "macro"
            CaptureRequest.CONTROL_AF_MODE_EDOF -> "edof"
            else -> "mode_$mode"
        }

    fun availableAfModes(chars: CameraCharacteristics): IntArray =
        chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
}
