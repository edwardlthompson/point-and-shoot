package dev.pointandshoot

private const val BIT_DEPTH_JPEG_SDR8 = 8

/**
 * Imaging profiles per BUILD_PLAN §4 / Part 3 spec.
 *
 * These describe the *intent* of a capture (what files we want, in what color
 * space, with what compression). The capture engine (Phase 1+) reads from this
 * to pick `DngCreator` parameters, the AVIF/JXL encoder configuration, and the
 * color-space tag on the resulting files.
 *
 * `RAW12 + uncompressed DNG` is intentionally a separate profile from the
 * default because the file size and IO cost are noticeably higher; only opt
 * into `UltraMax` when the user explicitly chooses it from the HUD.
 */
sealed class ImagingProfile(
    val id: String,
    val displayName: String,
    val rawMode: RawMode,
    val tonalContainer: TonalContainer,
    val colorSpace: ColorSpaceTarget,
) {
    /** Default profile: lossless DNG + 10-bit AVIF (HDR) + Display P3. */
    data object StandardPro : ImagingProfile(
        id = "standard_pro",
        displayName = "Standard Pro",
        rawMode = RawMode.LosslessCompressedDng,
        tonalContainer = TonalContainer.Avif10BitHdr,
        colorSpace = ColorSpaceTarget.DisplayP3,
    )

    /** Maximum quality profile: uncompressed RAW12 DNG + 12-bit JXL + Rec. 2020. */
    data object UltraMax : ImagingProfile(
        id = "ultra_max",
        displayName = "Ultra-Max",
        rawMode = RawMode.UncompressedRaw12Dng,
        tonalContainer = TonalContainer.JpegXl12Bit,
        colorSpace = ColorSpaceTarget.Rec2020,
    )

    /**
     * Hardware JPEG still only (no RAW [ImageReader], no DNG). Folder layout matches [StandardPro]
     * under `DCIM/Point & Shoot/` per BUILD_PLAN Milestone 10 Sprint 10.3.
     */
    data object JpegOnly : ImagingProfile(
        id = "jpeg_only",
        displayName = "JPEG only",
        rawMode = RawMode.None,
        tonalContainer = TonalContainer.JpegSdr8,
        colorSpace = ColorSpaceTarget.DisplayP3,
    )

    companion object {
        /**
         * Lazy getter so `listOf` runs after [StandardPro] / [UltraMax] finish initializing — on JVM,
         * eager `val all = listOf(StandardPro, UltraMax)` can briefly see null singleton fields during
         * sealed-class companion load (same pattern as [EncoderRoute.downgradedProfiles]).
         */
        val all: List<ImagingProfile>
            get() = listOf(StandardPro, UltraMax, JpegOnly)

        val default: ImagingProfile = StandardPro

        fun byId(id: String?): ImagingProfile =
            when (id) {
                StandardPro.id -> StandardPro
                UltraMax.id -> UltraMax
                JpegOnly.id -> JpegOnly
                else -> default
            }
    }
}

/**
 * Short labels for preview chrome / rail (three-way cycle: lossless DNG → RAW12-class DNG → JPG).
 * Readout chip detail still uses [PreviewReadoutStillPipeline] (`DNG`, `DNG+`, `DNG12`, `JPG`, …).
 */
val ImagingProfile.previewStillModeShortLabel: String
    get() =
        when (this) {
            ImagingProfile.StandardPro -> "DNG"
            ImagingProfile.UltraMax -> "DNG+"
            ImagingProfile.JpegOnly -> "JPG"
        }

/** Maps profile RAW intent to MediaStore kind for [CaptureStorage]. */
fun ImagingProfile.toDngCaptureKind(): CaptureStorage.CaptureKind =
    when (this) {
        ImagingProfile.JpegOnly ->
            error("JPEG-only profile has no DNG kind — use hardware JPEG still capture path")
        else ->
            when (rawMode) {
                RawMode.LosslessCompressedDng -> CaptureStorage.CaptureKind.DngLossless
                RawMode.UncompressedRaw12Dng -> CaptureStorage.CaptureKind.DngRaw12
                RawMode.None -> error("unexpected RawMode.None on non-JpegOnly profile")
            }
    }

enum class RawMode(val displayName: String) {
    LosslessCompressedDng("Lossless DNG"),
    UncompressedRaw12Dng("RAW12 (uncompressed)"),
    /** No RAW stream — [ImagingProfile.JpegOnly] hardware JPEG stills. */
    None("No RAW (JPEG still)"),
}

enum class TonalContainer(val displayName: String, val mimeType: String, val fileExtension: String, val bitDepth: Int) {
    Avif10BitHdr("AVIF (10-bit HDR)", "image/avif", "avif", 10),
    JpegXl12Bit("JPEG XL (12-bit)", "image/jxl", "jxl", 12),
    /** Primary sRGB JPEG (hardware still); does not require native AVIF/JXL encoders. */
    JpegSdr8("JPEG (8-bit sRGB)", "image/jpeg", "jpg", BIT_DEPTH_JPEG_SDR8),
}

enum class ColorSpaceTarget(val displayName: String) {
    Rec2020("Rec. 2020"),
    ProPhotoRgb("ProPhoto RGB"),
    AdobeRgb1998("Adobe RGB (1998)"),
    DisplayP3("Display P3"),
    SrgbRec709("sRGB / Rec.709"),
}
