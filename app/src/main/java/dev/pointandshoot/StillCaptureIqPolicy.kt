package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import dev.pointandshoot.fleet.FleetCameraProfile
import dev.pointandshoot.fleet.LegacyFleetPolicy

/**
 * ReferenceCam-aligned still capture IQ (Milestone **13.3b**): lens shading map + shading mode on RAW stills.
 */
object StillCaptureIqPolicy {

    /**
     * ReferenceCam still IQ only — used by [ReferenceAppLeafStillCaptureRequest] (no duplicate pipeline pass).
     */
    fun applyReferenceAppLeafStillIq(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        profile: FleetCameraProfile?,
    ) {
        applyReferenceAppStillPipeline(req, chars)
        if (!shouldApplyLensShading(profile, chars)) return
        applyLensShadingMapAndMode(req, chars, profile)
    }

    fun applyToStillCaptureRequest(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        profile: FleetCameraProfile?,
    ) {
        val proShotLeaf =
            LegacyFleetPolicy.useExactReferenceAppLeafStillCaptureRequest() &&
                isLeafBackCharacteristics(chars)
        if (proShotLeaf) {
            applyReferenceAppLeafStillIq(req, chars, profile)
            return
        }
        val legacyReferenceAppLeaf =
            LegacyFleetPolicy.useReferenceAppPureDngSave() && isLeafBackCharacteristics(chars)
        if (legacyReferenceAppLeaf) {
            applyReferenceAppStillPipeline(req, chars)
        } else if (
            AltReferenceAppInspiredStillPolicy.applyReferenceAppOpticalCorrectionOnLeaf() &&
                LegacyFleetPolicy.appliesToDevice() &&
                isLeafBackCharacteristics(chars)
        ) {
            applyReferenceAppOpticalCorrection(req, chars)
        }
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
        if (mapModes.contains(CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)) {
            req.set(
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON,
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

    private fun applyShadingMode(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        preferHighQuality: Boolean,
    ) {
        val shadingModes = chars.get(CameraCharacteristics.SHADING_AVAILABLE_MODES) ?: return
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
        profile?.let { return it.lensShadingMapOnStill }
        if (!LegacyFleetPolicy.appliesToDevice()) return false
        val facing = chars.get(CameraCharacteristics.LENS_FACING)
        if (facing != CameraCharacteristics.LENS_FACING_BACK) return false
        return isLeafBackCharacteristics(chars)
    }

    internal fun isLeafBackCharacteristics(chars: CameraCharacteristics): Boolean {
        val physical = runCatching { chars.physicalCameraIds?.toSet().orEmpty() }.getOrDefault(emptySet())
        return physical.isEmpty()
    }
}
