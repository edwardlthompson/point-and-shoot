package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import dev.pointandshoot.fleet.FleetCameraProfile
import dev.pointandshoot.fleet.LegacyFleetPolicy

/**
 * ReferenceCam-aligned still capture IQ (Milestone **13.3b**): lens shading map + shading mode on RAW stills.
 */
object StillCaptureIqPolicy {

    /**
     * USB bisect: when false, skip EDGE/NR/TONEMAP/hot-pixel/optical/shading on still — only
     * TEMPLATE_STILL_CAPTURE defaults + AWB/AE elsewhere. ProShot tele Bayer R/G matches ASN;
     * P&S ASN matched while Bayer R/G lagged — isolate whether still-IQ keys desync RAW.
     */
    const val APPLY_REFERENCE_APP_STILL_IQ: Boolean = true

    /**
     * When [APPLY_REFERENCE_APP_STILL_IQ] is true: ProShot pref defaults ON, but OP13 tele ProShot
     * DNGs and P&S map-ON USB both show map does **not** change Bayer (only embeds OpcodeList2).
     * Keep OFF for ProShot DNG footprint (no GainMaps).
     */
    const val REQUEST_LENS_SHADING_MAP_ON_STILL: Boolean = false

    /**
     * USB bisect (ProShot stream set): when true + pure-HAL RAW session, omit face/hist/zebra
     * **YUV analysis** from the REGULAR session graph (preview + RAW + JPEG only). Does **not**
     * use [automationSuppressFacePipeline] (that lock broke sequential RAW on legacy). H dial /
     * readout chase still force YUV via [PreviewSessionRegularOutputsPolicy.wantsYuvAnalysis].
     */
    const val OMIT_YUV_ANALYSIS_FOR_PURE_HAL_RAW_SESSION: Boolean = true

    /**
     * ReferenceCam still IQ only — used by [ReferenceAppLeafStillCaptureRequest] (no duplicate pipeline pass).
     */
    fun applyReferenceAppLeafStillIq(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        profile: FleetCameraProfile?,
    ) {
        if (!APPLY_REFERENCE_APP_STILL_IQ || DngSaveBisectState.skipStillIq) return
        applyReferenceAppStillPipeline(req, chars)
        if (!shouldApplyLensShading(profile, chars)) return
        applyLensShadingMapAndMode(req, chars, profile)
    }

    fun applyToStillCaptureRequest(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        profile: FleetCameraProfile?,
    ) {
        if (!APPLY_REFERENCE_APP_STILL_IQ || DngSaveBisectState.skipStillIq) {
            if (DngSaveBisectState.skipStillIq) {
                Log.i("PNS.StillIq", "skipStillIq=true (fleet exposure bisect E11)")
            }
            return
        }
        val proShotLeaf =
            LegacyFleetPolicy.useExactReferenceAppLeafStillCaptureRequest() &&
                isLeafBackCharacteristics(chars)
        if (proShotLeaf) {
            applyReferenceAppLeafStillIq(req, chars, profile)
            return
        }
        // ProShot / ReferenceCam still path (A5 z2=true): advertised still IQ on all SKUs.
        // Capability-gated inside; no Build.MODEL / LegacySku branch.
        applyReferenceAppStillPipeline(req, chars)
        applyLensShadingMapAndMode(req, chars, profile)
    }

    private fun applyLensShadingMapAndMode(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        profile: FleetCameraProfile? = null,
    ) {
        if (!shouldApplyLensShading(profile, chars)) return
        val mapModes =
            chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)
                ?: return
        if (REQUEST_LENS_SHADING_MAP_ON_STILL || DngSaveBisectState.useProShotCapturePipeline) {
            if (mapModes.contains(CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)) {
                req.set(
                    CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                    CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON,
                )
            }
        } else if (mapModes.contains(CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_OFF)) {
            // Match ProShot tele DNG footprint (no OpcodeList2). Prefer explicit OFF over omitting.
            req.set(
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_OFF,
            )
        }
        if (AltReferenceAppInspiredStillPolicy.teleLensShadingMapOnly(profile)) {
            return
        }
        applyShadingMode(req, chars, preferHighQuality = true)
    }

    /**
     * ReferenceCam still (`C0353b0` z2 path): tonemap / edge / hot-pixel + optical correction on leaf RAW stills.
     */
    internal fun applyReferenceAppStillPipeline(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
    ) {
        applyReferenceAppOpticalCorrection(req, chars)
        val edgeModes = chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES) ?: intArrayOf()
        when {
            edgeModes.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY) ->
                req.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
            edgeModes.contains(CaptureRequest.EDGE_MODE_FAST) ->
                req.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
        }
        val nrModes =
            chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                ?: intArrayOf()
        when {
            nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY) ->
                req.set(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY,
                )
            nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_FAST) ->
                req.set(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_FAST,
                )
        }
        val tonemapModes =
            chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: intArrayOf()
        when {
            tonemapModes.contains(CaptureRequest.TONEMAP_MODE_HIGH_QUALITY) ->
                req.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
            tonemapModes.contains(CaptureRequest.TONEMAP_MODE_FAST) ->
                req.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_FAST)
        }
        val hotPixelModes = chars.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES) ?: intArrayOf()
        when {
            hotPixelModes.contains(CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY) ->
                req.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY)
        }
    }

    /** ReferenceCam still builder: aberration + distortion when advertised (leaf sessions). */
    internal fun applyReferenceAppOpticalCorrection(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
    ) {
        val aberrationModes =
            chars.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)
                ?: intArrayOf()
        when {
            aberrationModes.contains(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY) ->
                req.set(
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY,
                )
            aberrationModes.contains(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST) ->
                req.set(
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST,
                )
        }
        val distortionModes =
            chars.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)
                ?: intArrayOf()
        when {
            distortionModes.contains(CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY) ->
                req.set(
                    CaptureRequest.DISTORTION_CORRECTION_MODE,
                    CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY,
                )
            distortionModes.contains(CaptureRequest.DISTORTION_CORRECTION_MODE_FAST) ->
                req.set(
                    CaptureRequest.DISTORTION_CORRECTION_MODE,
                    CaptureRequest.DISTORTION_CORRECTION_MODE_FAST,
                )
        }
    }

    /**
     * USB bisect: when [SENSOR_INFO_LENS_SHADING_APPLIED] is true, skip / OFF [SHADING_MODE].
     * OP13 tele RAW already has shading in the buffer; HQ shading on top correlated with elevated
     * edge green (full-frame R/G ≪ center) while ProShot samples stay flatter.
     */
    const val SKIP_SHADING_MODE_WHEN_LENS_SHADING_APPLIED: Boolean = true

    private fun applyShadingMode(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        preferHighQuality: Boolean,
    ) {
        val shadingModes = chars.get(CameraCharacteristics.SHADING_AVAILABLE_MODES) ?: return
        if (
            SKIP_SHADING_MODE_WHEN_LENS_SHADING_APPLIED &&
            chars.get(CameraCharacteristics.SENSOR_INFO_LENS_SHADING_APPLIED) == true
        ) {
            if (shadingModes.contains(CaptureRequest.SHADING_MODE_OFF)) {
                req.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_OFF)
            }
            Log.d("PNS.StillIq", "shadingMode=OFF (lensShadingApplied=true)")
            return
        }
        when {
            preferHighQuality && shadingModes.contains(CaptureRequest.SHADING_MODE_HIGH_QUALITY) ->
                req.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY)
            shadingModes.contains(CaptureRequest.SHADING_MODE_FAST) ->
                req.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_FAST)
            shadingModes.contains(CaptureRequest.SHADING_MODE_HIGH_QUALITY) ->
                req.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY)
        }
    }

    internal fun shouldApplyLensShading(
        profile: FleetCameraProfile?,
        chars: CameraCharacteristics,
    ): Boolean {
        // Explicit fleet profile wins when present (built from HAL ads).
        profile?.let { return it.lensShadingMapOnStill }
        // ProShot default: LENS_SHADING_MAP on when STATISTICS_INFO advertises ON — no model gate.
        val facing = chars.get(CameraCharacteristics.LENS_FACING)
        if (facing != CameraCharacteristics.LENS_FACING_BACK) return false
        val mapModes =
            chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)
                ?: return false
        return mapModes.contains(CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)
    }

    internal fun isLeafBackCharacteristics(chars: CameraCharacteristics): Boolean {
        val physical = runCatching { chars.physicalCameraIds?.toSet().orEmpty() }.getOrDefault(emptySet())
        return physical.isEmpty()
    }
}
