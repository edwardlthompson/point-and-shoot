package dev.pointandshoot

/**
 * Source-of-truth enumeration of the runtime-available LUTs in the bundled
 * library per BUILD_PLAN §7 ("Phase 4"). Each entry carries the metadata the
 * HUD chip + About-page credits block render against, and a [generator]
 * callback that materializes the LUT on demand.
 *
 * Adding a new entry requires:
 *   1. SPDX in [ALLOWED_SPDX] (whitelist enforced by `LutCatalogTest`).
 *   2. A `generator` that returns a fully-populated [Lut3D].
 *   3. A matching row in `LICENSES.md` § "Bundled LUTs".
 *
 * **Asset-backed LUTs** (ACES, Filmic Blender) are intentionally NOT in this
 * enum yet; they require the build-time download task documented in BUILD_PLAN
 * §7. They will be added when `downloadBundledLuts` lands.
 */
enum class LutCatalog(
    val displayName: String,
    val description: String,
    val spdx: String,
    val source: String,
    val scope: Scope,
    private val generator: (Int) -> Lut3D,
) {
    None(
        displayName = "None",
        description = "Identity LUT - no color transform applied. Routes through the same shader path so toggling does not stall the GLES pipeline.",
        spdx = "public-domain",
        source = "BT.709 encoding identity (generated at runtime)",
        scope = Scope.Both,
        generator = BuiltInLuts::rec709Identity,
    ),
    BwBt601(
        displayName = "B&W BT.601",
        description = "Monochrome via NTSC / SDTV luma weights (Y = 0.299R + 0.587G + 0.114B).",
        spdx = "public-domain",
        source = "ITU-R BT.601 luma coefficients (generated at runtime)",
        scope = Scope.Both,
        generator = BuiltInLuts::bwBt601,
    ),
    BwBt709(
        displayName = "B&W BT.709",
        description = "Monochrome via HD / sRGB display luma weights (Y = 0.2126R + 0.7152G + 0.0722B).",
        spdx = "public-domain",
        source = "ITU-R BT.709 luma coefficients (generated at runtime)",
        scope = Scope.Both,
        generator = BuiltInLuts::bwBt709,
    ),
    PnsCinematic(
        displayName = "Point & Shoot Cinematic",
        description = "Original teal-orange grade: shadows pulled toward teal, highlights toward warm orange, smoothstep-blended at 30% strength.",
        spdx = "Apache-2.0",
        source = "Original work by the Point & Shoot project (BuiltInLuts.pnsCinematic)",
        scope = Scope.Both,
        generator = BuiltInLuts::pnsCinematic,
    ),
    ;

    /** Materialize this LUT at the requested grid size (default 33). */
    fun load(size: Int = BuiltInLuts.DEFAULT_SIZE): Lut3D = generator(size)

    /**
     * Stable SHA256 (IEEE-754 LE grid samples) for this catalog entry at [gridSize],
     * suitable for [DngLutMetadata.LutIdentity.Bundled] and sidecars.
     */
    fun sha256Hex(gridSize: Int = BuiltInLuts.DEFAULT_SIZE): String =
        LutSidecarWriter.sha256ForLut(load(gridSize))

    /**
     * LUT marker payload for DNG `Software` / description stamping when the user picked a
     * non-identity stills LUT. Returns `null` for [None].
     */
    fun identityForDngMetadata(gridSize: Int = BuiltInLuts.DEFAULT_SIZE): DngLutMetadata.LutIdentity.Bundled? {
        if (this == None) return null
        return DngLutMetadata.LutIdentity.Bundled(name, sha256Hex(gridSize))
    }

    enum class Scope { Stills, Video, Both }

    companion object {
        /**
         * Apache-2.0-compatible SPDX whitelist. Adding a new entry requires
         * either updating this set with explicit justification (and a CHANGELOG
         * note) OR using one of the existing values. No proprietary "free"
         * source is allowed in the bundled library; user-imported `.cube`
         * files via SAF skip this whitelist (the user owns their license
         * compliance for imported content).
         */
        val ALLOWED_SPDX: Set<String> = setOf(
            "Apache-2.0",
            "BSD-2-Clause",
            "BSD-3-Clause",
            "MIT",
            "CC0-1.0",
            "public-domain",
        )

        /** LUTs offered for [scope] in the HUD picker. */
        fun forScope(scope: Scope): List<LutCatalog> =
            entries.filter { it.scope == scope || it.scope == Scope.Both }

        /** LUT to use as the default for [scope]. Always falls back to [None]. */
        fun defaultFor(@Suppress("UNUSED_PARAMETER") scope: Scope): LutCatalog = None
    }
}
