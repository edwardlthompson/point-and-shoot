package dev.pointandshoot

/**
 * Pure-data feature gate per BUILD_PLAN §9 ("UX safety nets - capability-driven UI"):
 *
 *   * UI only enables features supported by probe results
 *   * Unsupported options are disabled with a clear reason
 *   * Safe defaults always produce a valid capture path
 *
 * The gate is fed by hardware-capability flags collected by the
 * `CameraCapabilitiesProbe` (see `PROBE_BUILD_PLAN.md` §5). It returns a
 * [GateResult] for every UI feature: an enabled bit plus a human-readable
 * reason when disabled, so the UI can render an explanatory tooltip / toast.
 *
 * This file is intentionally **pure data**: zero Android imports. Build
 * [HardwareCaps] at the engine boundary from `CameraCharacteristics`, then
 * pass it into [CapabilityGate.evaluate] for the UI layer.
 */
object CapabilityGate {

    /** Evaluate every gate against the supplied [caps]. Order is stable. */
    fun evaluate(caps: HardwareCaps): List<GateResult> = listOf(
        gate(
            feature = Feature.RawDng,
            enabled = caps.hasRawCapability,
            disabledReason = "RAW capture is not advertised by this camera (no RAW capability bit).",
        ),
        gate(
            feature = Feature.UltraMaxProfile,
            enabled = caps.hasRawCapability && caps.has12BitDepth,
            disabledReason = "Ultra-Max requires RAW + 12-bit dynamic-range support.",
        ),
        gate(
            feature = Feature.HfrPreview120,
            enabled = caps.has120FpsHfr,
            disabledReason = "120 fps preview is not in the HFR configuration map for this camera.",
        ),
        gate(
            feature = Feature.EyeAfOverlay,
            enabled = caps.hasFaceDetectFull,
            disabledReason = "Eye-AF overlay requires STATISTICS_FACE_DETECT_MODE_FULL.",
        ),
        gate(
            feature = Feature.HighlightWeightedMetering,
            enabled = caps.hasPreviewHistogram,
            disabledReason = "Highlight metering needs a per-frame luma histogram from the preview path.",
        ),
        gate(
            feature = Feature.BracketBurst,
            enabled = caps.aeCompensationStepsAvailable > 0,
            disabledReason = "Bracket burst requires AE compensation control on this camera.",
        ),
        gate(
            feature = Feature.SuperMacroLock,
            enabled = caps.hasMacroMode,
            disabledReason = "Super Macro lock requires the macro vendor mode (ultra-wide camera).",
        ),
        gate(
            feature = Feature.TenBitHdrAvif,
            enabled = caps.has10BitHdrPipeline,
            disabledReason = "10-bit AVIF (HDR) requires the Android 16 hybrid AE / 10-bit pipeline.",
        ),
        gate(
            feature = Feature.OpticalStabilization,
            enabled = caps.hasOpticalStabilization,
            disabledReason = "Optical image stabilization is not advertised by this lens.",
        ),
        gate(
            feature = Feature.CameraExtensions,
            enabled = caps.supportedCameraExtensionLabels.isNotBlank(),
            disabledReason = "No Camera2 vendor extensions are advertised for this camera (API 31+ inventory).",
        ),
        gate(
            feature = Feature.ReprocessSession,
            enabled = caps.supportsYuvReprocessing || caps.supportsPrivateReprocessing,
            disabledReason = "YUV/private input reprocessing is not advertised in availableCapabilities.",
        ),
    )

    /**
     * Default-feature recommendation: the largest enabled subset that
     * preserves the "Standard Pro" baseline (DNG + 10-bit AVIF + Display P3).
     * If even the baseline fails, returns the empty set so the engine can
     * fall back to JPEG-only.
     */
    fun recommendedDefaults(caps: HardwareCaps): Set<Feature> {
        val standard = setOf(Feature.RawDng, Feature.TenBitHdrAvif)
        val all = evaluate(caps).filter { it.enabled }.map { it.feature }.toSet()
        return if (all.containsAll(standard)) all else emptySet()
    }

    private fun gate(feature: Feature, enabled: Boolean, disabledReason: String): GateResult =
        GateResult(
            feature = feature,
            enabled = enabled,
            disabledReason = if (enabled) null else disabledReason,
        )
}

/** Hardware capability bag, populated from the probe / characteristics. */
data class HardwareCaps(
    val hasRawCapability: Boolean,
    val has12BitDepth: Boolean,
    val has120FpsHfr: Boolean,
    val hasFaceDetectFull: Boolean,
    val hasPreviewHistogram: Boolean,
    val aeCompensationStepsAvailable: Int,
    val hasMacroMode: Boolean,
    val has10BitHdrPipeline: Boolean,
    /**
     * True when the active camera advertises any non-`OFF` mode in
     * `LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION`. Populated from
     * [LensInfoSummary.hasOpticalStabilization] at the engine boundary.
     */
    val hasOpticalStabilization: Boolean = false,
    /** Comma-separated extension labels from [CameraExtensionSupport] (empty when none / API < 31). */
    val supportedCameraExtensionLabels: String = "",
    val supportsYuvReprocessing: Boolean = false,
    val supportsPrivateReprocessing: Boolean = false,
    val reprocessMaxCaptureStall: Int? = null,
    val reprocessEffectiveExposureRequestKey: Boolean = false,
)

enum class Feature(val displayName: String) {
    RawDng("RAW DNG"),
    UltraMaxProfile("Ultra-Max profile"),
    HfrPreview120("120 fps preview"),
    EyeAfOverlay("Eye-AF overlay"),
    HighlightWeightedMetering("Highlight-weighted metering"),
    BracketBurst("Exposure bracketing"),
    SuperMacroLock("Super Macro lock"),
    TenBitHdrAvif("10-bit AVIF (HDR)"),
    OpticalStabilization("Optical image stabilization"),
    CameraExtensions("Camera2 extensions (HDR / night vendor modes)"),
    ReprocessSession("Input reprocess session (YUV / private)"),
}

/**
 * One feature's gate decision. UI binds the feature toggle's `enabled` to
 * [enabled] and, when disabled, surfaces [disabledReason] (tooltip / toast).
 */
data class GateResult(
    val feature: Feature,
    val enabled: Boolean,
    val disabledReason: String?,
)
