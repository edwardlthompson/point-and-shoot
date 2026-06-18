package dev.pointandshoot

enum class ImgMenuTier {
    Ultra,
    Standard,
    Off,
}

enum class RawFormatOption(
    val label: String,
    val bitDepthLabel: String,
    val bitDepth: Int,
    val tier: ImgMenuTier,
) {
    Off("Off", "Disabled", 0, ImgMenuTier.Off),
    DngRaw12("DNG RAW12", "12-bit", 12, ImgMenuTier.Ultra),
    DngLossless("DNG Lossless", "12-bit lossless", 12, ImgMenuTier.Standard),
}

enum class CompressedFormatOption(
    val label: String,
    val bitDepthLabel: String,
    val bitDepth: Int,
    val exportKind: StillExportKind?,
    val tier: ImgMenuTier,
) {
    Off("Off", "Disabled", 0, null, ImgMenuTier.Off),
    Jpeg("JPEG", "8-bit", 8, StillExportKind.Jpeg, ImgMenuTier.Standard),
    Avif("AVIF", "10-bit", 10, StillExportKind.Avif, ImgMenuTier.Standard),
    MotionPhoto("Motion Photo", "8-bit", 8, StillExportKind.MotionPhoto, ImgMenuTier.Standard),
    Heic("HEIC", "10-bit", 10, StillExportKind.Heic, ImgMenuTier.Ultra),
    JpegXl("JPEG XL", "12-bit", 12, StillExportKind.JpegXl, ImgMenuTier.Ultra),
    Tiff16("TIFF", "16-bit", 16, StillExportKind.Tiff16, ImgMenuTier.Ultra),
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
            ImgMenuTier.Ultra -> "12 - JXL - Rec2020"
            ImgMenuTier.Standard -> "10 - AVIF - P3"
            ImgMenuTier.Off -> null
        }
}

object StillPhotoPickerMatrix {
    fun allowedRawFormats(colorSpace: ColorSpaceTarget?): List<RawFormatOption> =
        (when (colorSpace) {
            ColorSpaceTarget.Rec2020 -> listOf(RawFormatOption.DngRaw12, RawFormatOption.Off)
            else -> listOf(RawFormatOption.DngLossless, RawFormatOption.Off)
        }).sortedWith(
            compareByDescending<RawFormatOption> { it.bitDepth }.thenBy { it.label },
        )

    fun allowedCompressedFormats(colorSpace: ColorSpaceTarget?): List<CompressedFormatOption> {
        val tonal =
            when (colorSpace) {
                ColorSpaceTarget.Rec2020 ->
                    listOf(
                        CompressedFormatOption.JpegXl,
                        CompressedFormatOption.Heic,
                        CompressedFormatOption.Tiff16,
                    )
                else ->
                    listOf(
                        CompressedFormatOption.Avif,
                        CompressedFormatOption.MotionPhoto,
                        CompressedFormatOption.Jpeg,
                    )
            }
        val sortedTonal =
            tonal.sortedWith(
                compareByDescending<CompressedFormatOption> { it.bitDepth }.thenBy { it.label },
            )
        return sortedTonal + CompressedFormatOption.Off
    }

    fun allowedRawTiers(colorSpace: ColorSpaceTarget?): List<ImgMenuTier> =
        when (colorSpace) {
            ColorSpaceTarget.Rec2020 -> listOf(ImgMenuTier.Ultra, ImgMenuTier.Off)
            else -> listOf(ImgMenuTier.Standard, ImgMenuTier.Off)
        }

    fun allowedCompressedTiers(
        colorSpace: ColorSpaceTarget?,
        rawTier: ImgMenuTier,
    ): List<ImgMenuTier> {
        val base = if (colorSpace == ColorSpaceTarget.Rec2020) ImgMenuTier.Ultra else ImgMenuTier.Standard
        return if (rawTier == ImgMenuTier.Off) {
            listOf(base)
        } else {
            listOf(base, ImgMenuTier.Off)
        }
    }

    fun maxPhotoIntent(): ComposedStillIntent =
        ComposedStillIntent(
            raw = ImgMenuTier.Ultra,
            jpeg = ImgMenuTier.Ultra,
            hdrWhenJpegOff = ImgMenuTier.Ultra,
            photoResolutionMode = PhotoResolutionMode.MaxResolution,
        )

    fun maxCompressedForColor(colorSpace: ColorSpaceTarget?): CompressedFormatOption =
        if (colorSpace == ColorSpaceTarget.Rec2020) {
            CompressedFormatOption.Tiff16
        } else {
            CompressedFormatOption.Avif
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
    val photoResolutionMode: PhotoResolutionMode = PhotoResolutionMode.Binned,
) {
    init {
        require(hdrWhenJpegOff != ImgMenuTier.Off) { "hdrWhenJpegOff must be Ultra or Standard" }
    }

    fun isForbiddenOffOff(): Boolean = raw == ImgMenuTier.Off && jpeg == ImgMenuTier.Off

    fun wantsRawDng(): Boolean = raw != ImgMenuTier.Off

    fun wantsTonalStill(): Boolean = jpeg != ImgMenuTier.Off

    /**
     * When RAW and **-JPEG-** tiers match (both Ultra or both Standard), one shutter press emits
     * DNG + hardware JPEG sidecar on the same capture request — not a second still for AVIF/JXL.
     */
    fun wantsMatchedTierJpegSidecar(): Boolean =
        wantsRawDng() && wantsTonalStill() && raw == jpeg

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
    fun resolveCapturePlan(): ComposedCapturePlan {
        val sidecar = wantsMatchedTierJpegSidecar()
        return ComposedCapturePlan(
            raw = resolveRawBundle(),
            tonal = if (sidecar) null else resolveTonalBundle(),
            jpegSidecarPreset = if (sidecar) jpegEncodePreset() else null,
            photoResolutionMode = photoResolutionMode,
        )
    }

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
                photoResolutionMode = PhotoResolutionMode.Binned,
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
                        photoResolutionMode = PhotoResolutionMode.Binned,
                    )
                ImagingProfile.UltraMax ->
                    ComposedStillIntent(
                        raw = ImgMenuTier.Ultra,
                        jpeg = if (jpegCompanionOn) ImgMenuTier.Ultra else ImgMenuTier.Off,
                        hdrWhenJpegOff = ImgMenuTier.Ultra,
                        photoResolutionMode = PhotoResolutionMode.Binned,
                    )
                ImagingProfile.StandardPro ->
                    ComposedStillIntent(
                        raw = ImgMenuTier.Standard,
                        jpeg = if (jpegCompanionOn) ImgMenuTier.Standard else ImgMenuTier.Off,
                        hdrWhenJpegOff = ImgMenuTier.Standard,
                        photoResolutionMode = PhotoResolutionMode.Binned,
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

fun ComposedStillIntent.coerceForStillColorSpace(colorSpace: ColorSpaceTarget?): ComposedStillIntent {
    val allowedRaw = StillPhotoPickerMatrix.allowedRawTiers(colorSpace)
    val nextRaw = if (raw in allowedRaw) raw else allowedRaw.first()
    val allowedCompressed = StillPhotoPickerMatrix.allowedCompressedTiers(colorSpace, nextRaw)
    val nextJpeg = if (jpeg in allowedCompressed) jpeg else allowedCompressed.first()
    val nextHdrWhenOff =
        if (nextRaw == ImgMenuTier.Ultra || nextJpeg == ImgMenuTier.Ultra) {
            ImgMenuTier.Ultra
        } else {
            ImgMenuTier.Standard
        }
    return copy(
        raw = nextRaw,
        jpeg = nextJpeg,
        hdrWhenJpegOff = nextHdrWhenOff,
    ).coerceNoOffOff()
}

/** Independent RAW vs tonal outputs for one shutter press. */
data class ComposedCapturePlan(
    val raw: StillCaptureBundle?,
    val tonal: StillCaptureBundle?,
    /** Same-tier DNG + JPEG: one still request, hardware JPEG sidecar (not AVIF/JXL). */
    val jpegSidecarPreset: ImgMenuJpegEncodePreset? = null,
    val photoResolutionMode: PhotoResolutionMode = PhotoResolutionMode.Binned,
) {
    init {
        require(raw != null || tonal != null) { "At least one of raw or tonal must be set" }
        require(jpegSidecarPreset == null || tonal == null) {
            "jpegSidecarPreset and independent tonal plan are mutually exclusive"
        }
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

fun ComposedCapturePlan.withStillExportOverride(kind: StillExportKind?): ComposedCapturePlan {
    if (kind == null) return this
    val targetTonal =
        when (kind) {
            StillExportKind.Heic -> TonalContainer.Heic10Bit
            StillExportKind.MotionPhoto -> TonalContainer.MotionPhotoJpeg8
            StillExportKind.Tiff16 -> TonalContainer.Tiff16
            StillExportKind.JpegXl -> TonalContainer.JpegXl12Bit
            StillExportKind.Avif -> TonalContainer.Avif10BitHdr
            StillExportKind.Jpeg -> TonalContainer.JpegSdr8
            else -> return this
        }
    val targetColor =
        when (targetTonal) {
            TonalContainer.JpegXl12Bit, TonalContainer.Heic10Bit, TonalContainer.Tiff16 -> ColorSpaceTarget.Rec2020
            else -> ColorSpaceTarget.DisplayP3
        }
    return ComposedCapturePlan(
        raw = null,
        tonal =
            StillCaptureBundle(
                rawMode = RawMode.None,
                tonalContainer = targetTonal,
                colorSpace = targetColor,
                dngColorSpace = targetColor,
            ),
        jpegSidecarPreset = null,
        photoResolutionMode = photoResolutionMode,
    )
}

fun ComposedCapturePlan.withPreferredStillExportKind(kind: StillExportKind?): ComposedCapturePlan {
    if (kind == null || kind == StillExportKind.Dng) return this
    val targetTonal =
        when (kind) {
            StillExportKind.Heic -> TonalContainer.Heic10Bit
            StillExportKind.MotionPhoto -> TonalContainer.MotionPhotoJpeg8
            StillExportKind.Tiff16 -> TonalContainer.Tiff16
            StillExportKind.JpegXl -> TonalContainer.JpegXl12Bit
            StillExportKind.Avif -> TonalContainer.Avif10BitHdr
            StillExportKind.Jpeg -> TonalContainer.JpegSdr8
            StillExportKind.Dng -> return this
        }
    val targetColor =
        when (targetTonal) {
            TonalContainer.JpegXl12Bit, TonalContainer.Heic10Bit, TonalContainer.Tiff16 -> ColorSpaceTarget.Rec2020
            else -> ColorSpaceTarget.DisplayP3
        }
    val tonalBundle =
        StillCaptureBundle(
            rawMode = RawMode.None,
            tonalContainer = targetTonal,
            colorSpace = targetColor,
            dngColorSpace = targetColor,
        )
    return copy(
        tonal = tonalBundle,
        // Explicit export-kind choice means "independent tonal output", not RAW sidecar JPEG.
        jpegSidecarPreset = null,
    )
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
