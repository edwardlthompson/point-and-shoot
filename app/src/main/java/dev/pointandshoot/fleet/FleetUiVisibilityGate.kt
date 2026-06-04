package dev.pointandshoot.fleet

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import android.widget.Toast
import dev.pointandshoot.CapabilityGate
import dev.pointandshoot.Feature
import dev.pointandshoot.HardwareCaps
import dev.pointandshoot.HardwareCapsSnapshot
import dev.pointandshoot.RootCapabilityStore
import org.json.JSONObject

/**
 * Device-tailored UI visibility (Milestone **17.2**).
 *
 * Consumer chrome: **hide** when unavailable; **root-only** rows stay visible in blue (see toast helper).
 * Engineering hub may show full inventory separately.
 */
object FleetUiVisibilityGate {
    const val TAG = "PNS.FleetVisibility"

    const val ROOT_ONLY_TOAST =
        "Root only — grant SU in Engineering hub → Platform & root to unlock."

    enum class Tier {
        /** Do not compose in consumer chrome. */
        Hidden,
        /** Normal chrome. */
        Visible,
        /** Show in blue; tap shows [ROOT_ONLY_TOAST] until root is granted. */
        RootOnly,
    }

    data class VisibilityContext(
        val matrix: JSONObject?,
        val caps: HardwareCaps,
        val rootGranted: Boolean,
        val activeCameraId: String?,
    )

    fun buildContext(
        appContext: android.content.Context,
        activeCameraId: String?,
        cameraIds: List<String>,
    ): VisibilityContext {
        val cm = appContext.getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
        return VisibilityContext(
            matrix = FleetCapabilityGate.loadMatrix(appContext),
            caps = HardwareCapsSnapshot.build(cm, activeCameraId, cameraIds),
            rootGranted = RootCapabilityStore.loadOrUnknown(appContext).grantsPrivileged,
            activeCameraId = activeCameraId,
        )
    }

    fun tier(featureId: String, ctx: VisibilityContext): Tier {
        val row = CameraCapabilityCatalog.registry.firstOrNull { it.id == featureId }
        if (row == null) {
            // Default-deny: unknown/unmapped catalog ids never surface on consumer chrome.
            return Tier.Hidden
        }
        val deviceSupported = deviceSupported(featureId, ctx)
        val policy = row.visibilityPolicy

        if (row.rootOnly || policy == CameraCapabilityCatalog.VisibilityPolicy.RootOnly) {
            return if (ctx.rootGranted && deviceSupported) Tier.Visible else Tier.RootOnly
        }

        return when (policy) {
            CameraCapabilityCatalog.VisibilityPolicy.AlwaysShow -> Tier.Visible
            CameraCapabilityCatalog.VisibilityPolicy.ShowDisabledEngineering -> Tier.Visible
            CameraCapabilityCatalog.VisibilityPolicy.HideWhenUnavailable ->
                if (deviceSupported) Tier.Visible else Tier.Hidden
            CameraCapabilityCatalog.VisibilityPolicy.RootOnly ->
                if (ctx.rootGranted && deviceSupported) Tier.Visible else Tier.RootOnly
        }
    }

    fun visible(featureId: String, ctx: VisibilityContext): Boolean = tier(featureId, ctx) != Tier.Hidden

    fun rootOnly(featureId: String, ctx: VisibilityContext): Boolean = tier(featureId, ctx) == Tier.RootOnly

    /** FPS chip: stock achievable → visible; else root-only (blue) unless hidden by policy. */
    fun fpsTier(requiresRoot: Boolean, ctx: VisibilityContext): Tier =
        when {
            !requiresRoot -> Tier.Visible
            ctx.rootGranted -> Tier.Visible
            else -> Tier.RootOnly
        }

    fun logHidden(featureId: String, surface: String) {
        Log.d(TAG, "hidden feature=$featureId surface=$surface")
    }

    fun logRootOnlyTap(featureId: String, rootGranted: Boolean) {
        Log.i(TAG, "rootOnlyTap feature=$featureId rootGranted=$rootGranted")
    }

    fun showRootOnlyToast(appContext: android.content.Context, featureId: String, rootGranted: Boolean) {
        logRootOnlyTap(featureId, rootGranted)
        Toast.makeText(appContext, ROOT_ONLY_TOAST, Toast.LENGTH_SHORT).show()
    }

    private fun deviceSupported(featureId: String, ctx: VisibilityContext): Boolean {
        when (featureId) {
            "product.hardware_camera_key" ->
                return ProductHardwareLaunchScan.hasDedicatedCameraKeyEvidence(ctx.matrix)
        }
        perCameraMatrixSupported(featureId, ctx.matrix, ctx.activeCameraId)?.let { return it }
        catalogDeviceSupported(featureId, ctx)?.let { return it }
        return liveCapsSupported(featureId, ctx.caps)
    }

    private fun perCameraMatrixSupported(
        featureId: String,
        matrix: JSONObject?,
        activeCameraId: String?,
    ): Boolean? {
        if (matrix == null || activeCameraId.isNullOrBlank()) return null
        val family =
            when (featureId) {
                "face.eye_af", "face.detect", "face.priority_ae" -> "face"
                "raw.dng" -> "raw"
                "video.hfr" -> "hfr"
                "video.dcg_hdr" -> "dcgZsl"
                "video.av1" -> "av1"
                "video.hevc", "video.hevc10" -> "hevc10"
                "video.uhd60" -> "uhd60"
                "video.raw", "video.raw_picker" -> "rawVideo"
                "video.dual" -> "dualVideo"
                "video.multicam_melt" -> "multicamMelt"
                "preview.pip" -> "pipPreview"
                else -> return null
            }
        return FleetCapabilityGate.featureGate(matrix, activeCameraId, family)?.advertised
    }

    private fun catalogDeviceSupported(featureId: String, ctx: VisibilityContext): Boolean? {
        val matrix = ctx.matrix ?: return null
        val arr = matrix.optJSONArray(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG) ?: return null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == featureId) {
                return o.optBoolean("deviceSupported", false)
            }
        }
        return null
    }

    private fun liveCapsSupported(featureId: String, caps: HardwareCaps): Boolean {
        val gateFeature = catalogIdToGateFeature(featureId) ?: return defaultLiveSupported(featureId, caps)
        return CapabilityGate.evaluate(caps).first { it.feature == gateFeature }.enabled
    }

    private fun catalogIdToGateFeature(featureId: String): Feature? =
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

    private fun defaultLiveSupported(featureId: String, caps: HardwareCaps): Boolean =
        when {
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
