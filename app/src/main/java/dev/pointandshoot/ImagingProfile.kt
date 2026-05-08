package dev.pointandshoot

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

    companion object {
        val all: List<ImagingProfile> = listOf(StandardPro, UltraMax)

        val default: ImagingProfile = StandardPro

        fun byId(id: String?): ImagingProfile = all.firstOrNull { it.id == id } ?: default
    }
}

enum class RawMode(val displayName: String) {
    LosslessCompressedDng("Lossless DNG"),
    UncompressedRaw12Dng("RAW12 (uncompressed)"),
}

enum class TonalContainer(val displayName: String, val mimeType: String, val fileExtension: String, val bitDepth: Int) {
    Avif10BitHdr("AVIF (10-bit HDR)", "image/avif", "avif", 10),
    JpegXl12Bit("JPEG XL (12-bit)", "image/jxl", "jxl", 12),
}

enum class ColorSpaceTarget(val displayName: String) {
    DisplayP3("Display P3"),
    Rec2020("Rec. 2020"),
}
