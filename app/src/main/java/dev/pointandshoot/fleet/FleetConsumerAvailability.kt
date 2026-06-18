package dev.pointandshoot.fleet

import dev.pointandshoot.CapabilityGate
import dev.pointandshoot.Feature
import dev.pointandshoot.HardwareCaps
import dev.pointandshoot.PnsLog
import org.json.JSONObject

/**
 * Consumer chrome availability (Milestone **18.8** / fleet-honest UI).
 *
 * Uses matrix **`sessionOk && appEnabled`** for session-gated families; engineering hub
 * continues to read **`advertised`** from the matrix directly.
 */
object FleetConsumerAvailability {
    const val TAG = FleetUiVisibilityGate.TAG

    /** Matrix family for per-camera [FleetCapabilityGate.featureGate] lookup. */
    fun featureFamily(featureId: String): String? =
        when (featureId) {
            "face.eye_af", "face.detect", "face.priority_ae" -> "face"
            "raw.dng" -> "raw"
            "video.hfr" -> "hfr"
            "video.dcg_hdr" -> "dcgZsl"
            "video.av1" -> "av1"
            "video.hevc", "video.hevc10" -> "hevc10"
            "video.uhd60" -> "uhd60"
            "video.4k_regular" -> "fourKRegular"
            "video.raw", "video.raw_picker" -> "rawVideo"
            "video.dual" -> "dualVideo"
            "video.multicam_melt" -> "multicamMelt"
            "preview.pip" -> "pipPreview"
            else -> null
        }

    fun matrixConsumerSelectable(
        matrix: JSONObject?,
        activeCameraId: String?,
        family: String,
    ): Boolean? {
        if (matrix == null || activeCameraId.isNullOrBlank()) return null
        val gate = FleetCapabilityGate.featureGate(matrix, activeCameraId, family) ?: return false
        return gate.sessionOk && gate.appEnabled
    }

    fun matrixConsumerAdvertised(
        matrix: JSONObject?,
        activeCameraId: String?,
        family: String,
    ): Boolean? {
        if (matrix == null || activeCameraId.isNullOrBlank()) return null
        return FleetCapabilityGate.featureGate(matrix, activeCameraId, family)?.advertised
    }

    /**
     * True when the feature may appear in consumer pickers / tray (session-proven when matrix maps).
     */
    fun consumerSelectable(
        featureId: String,
        ctx: FleetUiVisibilityGate.VisibilityContext,
    ): Boolean {
        val family = featureFamily(featureId)
        if (family != null) {
            matrixConsumerSelectable(ctx.matrix, ctx.activeCameraId, family)?.let { selectable ->
                if (!selectable) {
                    PnsLog.d("FleetVisibility", "hidden reason=session_not_ok feature=$featureId family=$family")
                }
                return selectable
            }
        }
        return liveConsumerSupported(featureId, ctx.caps)
    }

    private fun liveConsumerSupported(featureId: String, caps: HardwareCaps): Boolean {
        val gateFeature =
            when (featureId) {
                "raw.dng" -> Feature.RawDng
                "raw.ultra_max" -> Feature.UltraMaxProfile
                "video.hfr" -> Feature.HfrPreview120
                "face.eye_af", "face.detect" -> Feature.EyeAfOverlay
                "hud.highlight_meter" -> Feature.HighlightWeightedMetering
                "still.bracket" -> Feature.BracketBurst
                "af.macro" -> Feature.SuperMacroLock
                "still.avif" -> Feature.TenBitHdrAvif
                "lens.ois" -> Feature.OpticalStabilization
                "camerax.hdr", "camerax.night", "camerax.bokeh" -> Feature.CameraExtensions
                "video.dcg_hdr" -> Feature.ReprocessSession
                else -> null
            }
        if (gateFeature != null) {
            return CapabilityGate.evaluate(caps).first { it.feature == gateFeature }.enabled
        }
        return when {
            featureId.startsWith("root.") -> true
            featureId.startsWith("fleet.") -> true
            featureId == "face.priority_ae" -> caps.hasFaceDetectFull
            featureId == "hud.zebra" || featureId == "hud.histogram" -> caps.hasPreviewHistogram
            featureId == "lens.eis" -> true
            featureId == "lens.aperture" -> caps.activeApertureCount > 0
            featureId == "lens.variable_aperture" -> caps.activeApertureCount > 1
            else -> false
        }
    }
}
