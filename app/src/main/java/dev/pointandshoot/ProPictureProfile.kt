package dev.pointandshoot

/**
 * Sprint **CC.3** — named still/video looks (LUT + ISP + optional imaging folder).
 * Not a RAW editor; applies via [HudSettings] + session restart where needed.
 */
data class ProPictureProfile(
    val id: String,
    val label: String,
    val description: String,
    val stillsLut: LutCatalog,
    val videoLut: LutCatalog,
    val hardwareJpegIspBias: Int,
    val softwareJpegCompanionQuality: Int,
    val imagingProfile: ImagingProfile? = null,
) {
    fun applyToHud(current: HudSettings): HudSettings =
        current.copy(
            selectedLutForStills = stillsLut.name,
            selectedLutForVideo = videoLut.name,
            hardwareJpegIspBias = hardwareJpegIspBias.coerceIn(-2, 2),
            softwareJpegCompanionQuality =
                softwareJpegCompanionQuality.coerceIn(
                    HudSettings.SOFTWARE_JPEG_COMPANION_QUALITY_MIN,
                    HudSettings.SOFTWARE_JPEG_COMPANION_QUALITY_MAX,
                ),
            selectedPictureProfileId = id,
        )
}

object ProPictureProfiles {
    val presets: List<ProPictureProfile> =
        listOf(
            ProPictureProfile(
                id = "neutral",
                label = "Neutral",
                description = "No LUT, natural ISP (minimal sharpening).",
                stillsLut = LutCatalog.None,
                videoLut = LutCatalog.None,
                hardwareJpegIspBias = CalibrationWorkflow.NATURAL_HARDWARE_JPEG_ISP_BIAS,
                softwareJpegCompanionQuality = 92,
            ),
            ProPictureProfile(
                id = "cinematic",
                label = "Cinematic",
                description = "Teal-orange stills LUT; video identity.",
                stillsLut = LutCatalog.PnsCinematic,
                videoLut = LutCatalog.None,
                hardwareJpegIspBias = 0,
                softwareJpegCompanionQuality = 92,
            ),
            ProPictureProfile(
                id = "mono709",
                label = "B&W HD",
                description = "BT.709 luma monochrome for stills and video.",
                stillsLut = LutCatalog.BwBt709,
                videoLut = LutCatalog.BwBt709,
                hardwareJpegIspBias = 0,
                softwareJpegCompanionQuality = 90,
            ),
            ProPictureProfile(
                id = "ultra_raw",
                label = "Ultra RAW",
                description = "Ultra-Max imaging folder + identity LUT.",
                stillsLut = LutCatalog.None,
                videoLut = LutCatalog.None,
                hardwareJpegIspBias = -1,
                softwareJpegCompanionQuality = 95,
                imagingProfile = ImagingProfile.UltraMax,
            ),
        )

    fun byId(id: String?): ProPictureProfile? =
        id?.let { want -> presets.firstOrNull { it.id == want } }

    fun normalizeId(raw: String?): String? = byId(raw)?.id
}
