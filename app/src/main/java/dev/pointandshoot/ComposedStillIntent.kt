package dev.pointandshoot

enum class ImgMenuTier {
    Ultra,
    Standard,
    Off,
}

/** Hint lines for the six-row IMG menu (`bit - token - gamut`); [Off] rows are hintless. */
object ImgMenuHints {
    fun rawRowSubtitle(tier: ImgMenuTier): String? =
        when (tier) {
            ImgMenuTier.Ultra -> "12 - RAW12 - Rec2020"
            ImgMenuTier.Standard -> "12 - Lossless - P3"
            ImgMenuTier.Off -> null
        }

    /** HDR tier when [ComposedStillIntent.raw] is not [ImgMenuTier.Off]. */
    fun jpegHdrRowSubtitle(tier: ImgMenuTier): String? =
        when (tier) {
            ImgMenuTier.Ultra -> "12 - JXL - Rec2020"
            ImgMenuTier.Standard -> "10 - AVIF - P3"
            ImgMenuTier.Off -> null
        }

    /**
     * Primary hardware JPEG encode hint when [ComposedStillIntent.raw] is [ImgMenuTier.Off]
     * (JPEG-only still mode; [ImgMenuTier.Off] here is forbidden — UI never offers it).
     */
    fun jpegOnlyPrimaryRowSubtitle(tier: ImgMenuTier): String? =
        when (tier) {
            ImgMenuTier.Ultra -> "8 - Max - P3"
            ImgMenuTier.Standard -> "8 - Bal - P3"
            ImgMenuTier.Off -> null
        }
}

/**
 * Hardware ISP bias + software companion re-encode quality for IMG **-JPEG-** Ultra / Standard.
 * Matches menu hints **8 - Max - P3** vs **8 - Bal - P3** ([HudSettings.hardwareJpegIspBias],
 * [HudSettings.softwareJpegCompanionQuality]).
 */
data class ImgMenuJpegEncodePreset(
    val hardwareJpegIspBias: Int,
    val softwareJpegCompanionQuality: Int,
)

object ImgMenuJpegEncodePresets {
    private const val QUALITY_MAX = 100
    private const val QUALITY_BALANCED = 92

    fun forTier(tier: ImgMenuTier): ImgMenuJpegEncodePreset =
        when (tier) {
            ImgMenuTier.Ultra ->
                ImgMenuJpegEncodePreset(
                    hardwareJpegIspBias = 2,
                    softwareJpegCompanionQuality = QUALITY_MAX,
                )
            ImgMenuTier.Standard, ImgMenuTier.Off ->
                ImgMenuJpegEncodePreset(
                    hardwareJpegIspBias = 0,
                    softwareJpegCompanionQuality = QUALITY_BALANCED,
                )
        }
}

/**
 * Independent IMG-menu tiers for RAW (DNG) vs tonal still (JPEG band → separate AVIF/JXL/JPEG file).
 * Any combination is allowed except [ImgMenuTier.Off] on **both** bands ([isForbiddenOffOff]).
 *
 * Tiers do **not** use a hardware JPEG companion on the RAW capture request: each enabled band is its
 * own still (DNG-only RAW path; hardware JPEG still → native tonal container when available).
 *
 * [hdrWhenJpegOff] is persisted for migration only; it is **not** used when [jpeg] is [ImgMenuTier.Off].
 */
data class ComposedStillIntent(
    val raw: ImgMenuTier,
    val jpeg: ImgMenuTier,
    /** Used only when [raw] != Off and [jpeg] == Off; must be [Ultra] or [Standard]. */
    val hdrWhenJpegOff: ImgMenuTier,
) {
    init {
        require(hdrWhenJpegOff != ImgMenuTier.Off) { "hdrWhenJpegOff must be Ultra or Standard" }
    }

    fun isForbiddenOffOff(): Boolean = raw == ImgMenuTier.Off && jpeg == ImgMenuTier.Off

    fun wantsRawDng(): Boolean = raw != ImgMenuTier.Off

    fun wantsTonalStill(): Boolean = jpeg != ImgMenuTier.Off

    /**
     * **-JPEG-** tier that drives hardware JPEG ISP + software re-encode quality for the
     * independent tonal still (never a RAW+JPEG companion surface on the DNG request).
     */
    fun jpegHardwareEncodeTier(): ImgMenuTier? =
        if (wantsTonalStill()) {
            jpeg
        } else {
            null
        }

    fun jpegEncodePreset(): ImgMenuJpegEncodePreset? =
        jpegHardwareEncodeTier()?.let { ImgMenuJpegEncodePresets.forTier(it) }

    /**
     * Split capture plan: optional RAW DNG bundle + optional independent tonal bundle.
     * [EncoderRoute.decide] on each part decides native AVIF/JXL vs standalone JPEG fallback.
     */
    fun resolveCapturePlan(): ComposedCapturePlan =
        ComposedCapturePlan(
            raw = resolveRawBundle(),
            tonal = resolveTonalBundle(),
        )

    /** Legacy single bundle — prefer [resolveCapturePlan] for capture wiring. */
    fun resolveBundle(): StillCaptureBundle {
        val plan = resolveCapturePlan()
        return plan.raw ?: plan.tonal ?: error("forbidden off+off")
    }

    private fun resolveRawBundle(): StillCaptureBundle? {
        if (!wantsRawDng()) {
            return null
        }
        val rawMode =
            when (raw) {
                ImgMenuTier.Ultra -> RawMode.UncompressedRaw12Dng
                ImgMenuTier.Standard -> RawMode.LosslessCompressedDng
                ImgMenuTier.Off -> error("raw off handled above")
            }
        val dngColor =
            when (raw) {
                ImgMenuTier.Ultra -> ColorSpaceTarget.Rec2020
                ImgMenuTier.Standard, ImgMenuTier.Off -> ColorSpaceTarget.DisplayP3
            }
        return StillCaptureBundle(
            rawMode = rawMode,
            tonalContainer = TonalContainer.JpegSdr8,
            colorSpace = dngColor,
            dngColorSpace = dngColor,
        )
    }

    private fun resolveTonalBundle(): StillCaptureBundle? {
        if (!wantsTonalStill()) {
            return null
        }
        val (tonal, hdrColor) =
            when (jpeg) {
                ImgMenuTier.Ultra -> TonalContainer.JpegXl12Bit to ColorSpaceTarget.Rec2020
                ImgMenuTier.Standard -> TonalContainer.Avif10BitHdr to ColorSpaceTarget.DisplayP3
                ImgMenuTier.Off -> error("jpeg off handled above")
            }
        return StillCaptureBundle(
            rawMode = RawMode.None,
            tonalContainer = tonal,
            colorSpace = hdrColor,
            dngColorSpace = ColorSpaceTarget.DisplayP3,
        )
    }

    /** [ImagingProfile] for [CaptureStorage] folders and stream-class key; driven by RAW tier only. */
    fun storageProfile(): ImagingProfile =
        when (raw) {
            ImgMenuTier.Off -> ImagingProfile.JpegOnly
            ImgMenuTier.Ultra -> ImagingProfile.UltraMax
            ImgMenuTier.Standard -> ImagingProfile.StandardPro
        }

    companion object {
        fun default(): ComposedStillIntent =
            ComposedStillIntent(
                raw = ImgMenuTier.Standard,
                jpeg = ImgMenuTier.Standard,
                hdrWhenJpegOff = ImgMenuTier.Standard,
            )

        fun fromLegacyImagingProfile(
            profile: ImagingProfile,
            jpegCompanionOn: Boolean,
        ): ComposedStillIntent =
            when (profile) {
                ImagingProfile.JpegOnly ->
                    ComposedStillIntent(
                        raw = ImgMenuTier.Off,
                        jpeg = ImgMenuTier.Standard,
                        hdrWhenJpegOff = ImgMenuTier.Standard,
                    )
                ImagingProfile.UltraMax ->
                    ComposedStillIntent(
                        raw = ImgMenuTier.Ultra,
                        jpeg = if (jpegCompanionOn) ImgMenuTier.Ultra else ImgMenuTier.Off,
                        hdrWhenJpegOff = ImgMenuTier.Ultra,
                    )
                ImagingProfile.StandardPro ->
                    ComposedStillIntent(
                        raw = ImgMenuTier.Standard,
                        jpeg = if (jpegCompanionOn) ImgMenuTier.Standard else ImgMenuTier.Off,
                        hdrWhenJpegOff = ImgMenuTier.Standard,
                    )
            }
    }
}

fun ComposedStillIntent.coerceNoOffOff(): ComposedStillIntent =
    if (!isForbiddenOffOff()) {
        this
    } else {
        copy(jpeg = ImgMenuTier.Standard)
    }

/** Independent RAW vs tonal outputs for one shutter press (no companion surfaces). */
data class ComposedCapturePlan(
    val raw: StillCaptureBundle?,
    val tonal: StillCaptureBundle?,
) {
    init {
        require(raw != null || tonal != null) { "At least one of raw or tonal must be set" }
    }
}

/**
 * Resolved packaging for one still capture (DNG + HDR + metadata targets).
 */
data class StillCaptureBundle(
    val rawMode: RawMode,
    val tonalContainer: TonalContainer,
    val colorSpace: ColorSpaceTarget,
    /** Tags / metadata for DNG when it should differ from HDR output color (mixed tiers). */
    val dngColorSpace: ColorSpaceTarget,
)

fun StillCaptureBundle.toDngCaptureKind(): CaptureStorage.CaptureKind =
    when (rawMode) {
        RawMode.LosslessCompressedDng -> CaptureStorage.CaptureKind.DngLossless
        RawMode.UncompressedRaw12Dng -> CaptureStorage.CaptureKind.DngRaw12
        RawMode.None -> error("JPEG-only bundle has no DNG kind")
    }

fun legacyStillBundle(profile: ImagingProfile): StillCaptureBundle =
    when (profile) {
        ImagingProfile.StandardPro ->
            StillCaptureBundle(
                rawMode = RawMode.LosslessCompressedDng,
                tonalContainer = TonalContainer.Avif10BitHdr,
                colorSpace = ColorSpaceTarget.DisplayP3,
                dngColorSpace = ColorSpaceTarget.DisplayP3,
            )
        ImagingProfile.UltraMax ->
            StillCaptureBundle(
                rawMode = RawMode.UncompressedRaw12Dng,
                tonalContainer = TonalContainer.JpegXl12Bit,
                colorSpace = ColorSpaceTarget.Rec2020,
                dngColorSpace = ColorSpaceTarget.Rec2020,
            )
        ImagingProfile.JpegOnly ->
            StillCaptureBundle(
                rawMode = RawMode.None,
                tonalContainer = TonalContainer.JpegSdr8,
                colorSpace = ColorSpaceTarget.DisplayP3,
                dngColorSpace = ColorSpaceTarget.DisplayP3,
            )
    }

/** Folder key for [CaptureStorage]; not the full tonal/raw truth when tiers are mixed. */
fun storageProfileFromBundle(bundle: StillCaptureBundle): ImagingProfile =
    when {
        bundle.rawMode == RawMode.None -> ImagingProfile.JpegOnly
        bundle.rawMode == RawMode.UncompressedRaw12Dng -> ImagingProfile.UltraMax
        else -> ImagingProfile.StandardPro
    }

/** Pushes IMG **-JPEG-** Ultra/Standard into [HudSettings] for hardware JPEG + companion re-encode. */
fun syncHudJpegEncodeFromImgMenu(intent: ComposedStillIntent, hudState: HudSettingsState) {
    val preset = intent.jpegEncodePreset() ?: return
    val s = hudState.current
    if (s.hardwareJpegIspBias == preset.hardwareJpegIspBias &&
        s.softwareJpegCompanionQuality == preset.softwareJpegCompanionQuality
    ) {
        return
    }
    hudState.update(
        s.copy(
            hardwareJpegIspBias = preset.hardwareJpegIspBias,
            softwareJpegCompanionQuality = preset.softwareJpegCompanionQuality,
        ),
    )
}
