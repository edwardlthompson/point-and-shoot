package dev.pointandshoot

/**
 * A complete RGB working space - primaries + transfer function + the
 * ITU-T H.273 CICP codes (`colour_primaries` / `transfer_characteristics`
 * / `matrix_coefficients`) that downstream containers (AVIF, AV1,
 * HEVC, MP4) need to tag the bitstream so any decoder can render the
 * pixels correctly.
 *
 * Ties together [HdrCurves] (transfer functions) and [ColorSpaceMatrix]
 * (primaries + chromatic adaptation) into a single named-preset API
 * the capture engine can pass around as a single value:
 *
 *     val ws = WorkingSpace.SRGB
 *     val xyz = ws.toXyzD65(floatArrayOf(0.5f, 0.5f, 0.5f))   // -> CIE XYZ_D65 anchored
 *     val (cp, tc, mc) = ws.cicp                              // -> ITU-T H.273 codes
 *
 * The named presets are the ones Point & Shoot actually emits or
 * accepts:
 *   * [SRGB] - default sRGB / BT.709 SDR display
 *   * [REC709_SDR] - traditional broadcast SDR (BT.709 EOTF, narrow range)
 *   * [REC2020_PQ] - HDR10 / HLG10+ with PQ EOTF (10-bit AVIF target)
 *   * [REC2020_HLG] - HLG broadcast HDR
 *   * [DCI_P3] - DCI-P3 D65 (Display P3 wide-gamut SDR; iPhone / Pixel HDR display)
 *   * [ACES_AP1_LINEAR] - linear-light ACES AP1 (intermediate working space; never bitstream-tagged directly)
 *
 * All numeric data is CIE / IEC / ITU-R / SMPTE / ISO standards data
 * (no trademarked source); the H.273 CICP codes match the ISO/IEC
 * 14496-10 (H.264) / 23008-2 (H.265) / 23090-3 (VVC) tables.
 *
 * No Android imports - safe for unit testing on the JVM.
 */
sealed class WorkingSpace {

    /** Bumped only when the named-preset table changes incompatibly. */
    companion object {
        const val SCHEMA_VERSION: Int = 1

        /**
         * Every shipped preset in stable enum-order. Used by the LUT
         * import/export flow to enumerate the picker entries and by
         * the test corpus to assert no preset has been added without
         * a matching CICP entry.
         *
         * Backed by `by lazy` because the list references the nested
         * `object` declarations (`SRGB`, `REC709_SDR`, ...). At class
         * init time those nested objects haven't been initialized yet,
         * so a non-lazy `val` would resolve to `null` when the
         * companion's `<clinit>` runs first. Reading `ALL` outside of
         * `<clinit>` triggers normal lazy resolution.
         */
        val ALL: List<WorkingSpace> by lazy {
            listOf(
                SRGB,
                REC709_SDR,
                REC2020_PQ,
                REC2020_HLG,
                DCI_P3,
                ACES_AP1_LINEAR,
            )
        }

        /** Convenience accessor: find a preset by canonical id (e.g. `"srgb"`). */
        fun byId(id: String): WorkingSpace? = ALL.firstOrNull { it.id == id }
    }

    /** Stable canonical id (lowercase, no spaces). Suitable for JSON keys. */
    abstract val id: String

    /** Human-readable label for the picker UI. */
    abstract val displayName: String

    /** Primaries (R / G / B chromaticities + whitepoint). */
    abstract val primaries: ColorSpaceMatrix.Primaries

    /** EOTF: encoded `[0, 1]` -> linear-light `[0, 1]` (or higher for HDR). */
    abstract val toLinear: (Float) -> Float

    /** OETF: linear-light -> encoded `[0, 1]`. Inverse of [toLinear]. */
    abstract val fromLinear: (Float) -> Float

    /**
     * ITU-T H.273 CICP triple. These are the three small integers that
     * AVIF / HEVC / VVC / MP4 / WebP / JXL need in order to tag the
     * bitstream so a decoder can decide whether to apply the inverse
     * EOTF and gamut conversion.
     *
     * `colourPrimaries` / `transferCharacteristics` / `matrixCoefficients`
     * are the canonical names; `videoFullRangeFlag` is the YCbCr
     * scaling flag (full = 0..255 / narrow = 16..235 for 8-bit).
     */
    abstract val cicp: Cicp

    /**
     * Convenience: linear-RGB -> CIE XYZ in the working space's native
     * whitepoint (D65 for sRGB / Rec.709 / Rec.2020 / DCI-P3, D60-ish
     * for ACES). Uses the primaries-derived matrix from
     * [ColorSpaceMatrix.primariesToXyz].
     */
    fun linearToXyz(rgb: FloatArray): FloatArray {
        require(rgb.size == 3) { "rgb must be length 3 (was ${rgb.size})" }
        val m = ColorSpaceMatrix.primariesToXyz(primaries)
        return ColorSpaceMatrix.multiplyVec(m, rgb)
    }

    /**
     * Convenience: encoded RGB (after the [toLinear] EOTF is applied)
     * -> CIE XYZ_D65, applying Bradford chromatic adaptation when the
     * working space's whitepoint is not D65 (notably ACES). Useful
     * for cross-color-space comparisons (e.g. matching a Rec.2020 LUT
     * against an sRGB reference patch).
     */
    fun toXyzD65(linearRgb: FloatArray): FloatArray {
        require(linearRgb.size == 3) { "rgb must be length 3 (was ${linearRgb.size})" }
        val xyz = linearToXyz(linearRgb)
        if (primaries.whitepoint == ColorSpaceMatrix.Illuminants.D65) {
            return xyz
        }
        // Bradford adapt: source -> D65.
        val m = ColorSpaceMatrix.bradford(primaries.whitepoint, ColorSpaceMatrix.Illuminants.D65)
        return ColorSpaceMatrix.multiplyVec(m, xyz)
    }

    // ---------- Shipped presets ----------

    /**
     * Standard sRGB / BT.709 SDR display - the default working space
     * Point & Shoot encodes JPEG / 8-bit AVIF / 8-bit JXL / 8-bit
     * preview into.
     */
    object SRGB : WorkingSpace() {
        override val id: String = "srgb"
        override val displayName: String = "sRGB"
        override val primaries: ColorSpaceMatrix.Primaries = ColorSpaceMatrix.SRGB_PRIMARIES
        override val toLinear: (Float) -> Float = HdrCurves::srgbToLinear
        override val fromLinear: (Float) -> Float = HdrCurves::linearToSrgb
        override val cicp: Cicp = Cicp(
            colourPrimaries = 1,            // BT.709
            transferCharacteristics = 13,   // sRGB
            matrixCoefficients = 0,         // identity (RGB)
            videoFullRangeFlag = true,
        )
    }

    /**
     * Traditional broadcast Rec.709 SDR - same primaries as sRGB but
     * the BT.709 OETF (slightly different gamma than sRGB).
     */
    object REC709_SDR : WorkingSpace() {
        override val id: String = "rec709-sdr"
        override val displayName: String = "Rec.709 SDR"
        override val primaries: ColorSpaceMatrix.Primaries = ColorSpaceMatrix.SRGB_PRIMARIES
        override val toLinear: (Float) -> Float = HdrCurves::rec709ToLinear
        override val fromLinear: (Float) -> Float = HdrCurves::linearToRec709
        override val cicp: Cicp = Cicp(
            colourPrimaries = 1,            // BT.709
            transferCharacteristics = 1,    // BT.709
            matrixCoefficients = 1,         // BT.709 YCbCr
            videoFullRangeFlag = false,     // narrow-range (broadcast convention)
        )
    }

    /**
     * Rec.2020 with PQ EOTF (SMPTE ST 2084) - HDR10 / 10-bit AVIF
     * target. Peak luminance 10,000 cd/m^2 per the PQ spec.
     */
    object REC2020_PQ : WorkingSpace() {
        override val id: String = "rec2020-pq"
        override val displayName: String = "Rec.2020 PQ (HDR10)"
        override val primaries: ColorSpaceMatrix.Primaries = ColorSpaceMatrix.REC2020_PRIMARIES
        override val toLinear: (Float) -> Float = HdrCurves::pqToLinear
        override val fromLinear: (Float) -> Float = HdrCurves::linearToPq
        override val cicp: Cicp = Cicp(
            colourPrimaries = 9,            // BT.2020
            transferCharacteristics = 16,   // SMPTE ST 2084 (PQ)
            matrixCoefficients = 9,         // BT.2020 NCL
            videoFullRangeFlag = true,
        )
    }

    /**
     * Rec.2020 with HLG OETF (BT.2100) - broadcast HDR target.
     */
    object REC2020_HLG : WorkingSpace() {
        override val id: String = "rec2020-hlg"
        override val displayName: String = "Rec.2020 HLG"
        override val primaries: ColorSpaceMatrix.Primaries = ColorSpaceMatrix.REC2020_PRIMARIES
        override val toLinear: (Float) -> Float = HdrCurves::hlgToLinear
        override val fromLinear: (Float) -> Float = HdrCurves::linearToHlg
        override val cicp: Cicp = Cicp(
            colourPrimaries = 9,            // BT.2020
            transferCharacteristics = 18,   // ARIB STD-B67 (HLG)
            matrixCoefficients = 9,         // BT.2020 NCL
            videoFullRangeFlag = true,
        )
    }

    /**
     * Display P3 (Apple's wide-gamut SDR space) - DCI-P3 D65 primaries
     * with the sRGB EOTF (NOT the DCI-P3 Theater EOTF, which is
     * gamma 2.6). The CICP triple matches Apple / Pixel encodings.
     */
    object DCI_P3 : WorkingSpace() {
        override val id: String = "display-p3"
        override val displayName: String = "Display P3"
        override val primaries: ColorSpaceMatrix.Primaries = ColorSpaceMatrix.DCI_P3_PRIMARIES
        override val toLinear: (Float) -> Float = HdrCurves::srgbToLinear
        override val fromLinear: (Float) -> Float = HdrCurves::linearToSrgb
        override val cicp: Cicp = Cicp(
            colourPrimaries = 12,           // SMPTE RP 431-2 (DCI-P3)
            transferCharacteristics = 13,   // sRGB
            matrixCoefficients = 0,         // identity (RGB)
            videoFullRangeFlag = true,
        )
    }

    /**
     * Linear-light ACES AP1 - intermediate working space used by the
     * future ACES / Filmic LUT path. NEVER bitstream-tagged directly
     * (no canonical CICP entry exists for AP1 + linear); the engine
     * always converts to one of the displayable spaces above before
     * encoding. The CICP entry is the ITU-T H.273 sentinel
     * (2 / 8 / 0) which AVIF / HEVC interpret as "unspecified".
     */
    object ACES_AP1_LINEAR : WorkingSpace() {
        override val id: String = "aces-ap1-linear"
        override val displayName: String = "ACES AP1 (linear)"
        override val primaries: ColorSpaceMatrix.Primaries = ColorSpaceMatrix.ACES_AP1_PRIMARIES
        override val toLinear: (Float) -> Float = HdrCurves::identity
        override val fromLinear: (Float) -> Float = HdrCurves::identity
        override val cicp: Cicp = Cicp(
            colourPrimaries = 2,            // unspecified
            transferCharacteristics = 8,    // linear
            matrixCoefficients = 0,         // identity (RGB)
            videoFullRangeFlag = true,
        )
    }
}

/**
 * ITU-T H.273 CICP (Coding-Independent Code Points) triple - the
 * three small integers downstream containers (AVIF, AV1, HEVC, VVC,
 * MP4, WebP, JXL) need to tag the bitstream.
 *
 * Reference: ITU-T H.273 v2 Tables 2-3-4 (and ISO/IEC 14496-10 Annex E
 * for H.264, ISO/IEC 23008-2 Annex E for H.265, ISO/IEC 23091-2 for
 * the standalone H.273 spec).
 */
data class Cicp(
    /**
     * `colour_primaries` (Table 2). Selected entries:
     *   1  = BT.709 (sRGB / Rec.709 primaries)
     *   2  = unspecified
     *   9  = BT.2020 (Rec.2020 primaries)
     *  11  = SMPTE RP 431-2 (DCI-P3 Theater whitepoint)
     *  12  = SMPTE EG 432-1 (DCI-P3 D65 whitepoint, "Display P3")
     */
    val colourPrimaries: Int,

    /**
     * `transfer_characteristics` (Table 3). Selected entries:
     *   1  = BT.709
     *   2  = unspecified
     *   8  = linear
     *  13  = sRGB / IEC 61966-2-1
     *  16  = SMPTE ST 2084 (PQ)
     *  18  = ARIB STD-B67 (HLG)
     */
    val transferCharacteristics: Int,

    /**
     * `matrix_coefficients` (Table 4). Selected entries:
     *   0  = identity (RGB; no YCbCr conversion)
     *   1  = BT.709 (Kr = 0.2126 / Kb = 0.0722)
     *   2  = unspecified
     *   9  = BT.2020 NCL (Rec.2020 non-constant luminance)
     */
    val matrixCoefficients: Int,

    /**
     * `video_full_range_flag` (1 bit). `true` = full range (0..255 for
     * 8-bit), `false` = narrow / broadcast range (16..235 for 8-bit).
     */
    val videoFullRangeFlag: Boolean,
) {
    init {
        require(colourPrimaries in 0..255) { "colourPrimaries out of range: $colourPrimaries" }
        require(transferCharacteristics in 0..255) { "transferCharacteristics out of range: $transferCharacteristics" }
        require(matrixCoefficients in 0..255) { "matrixCoefficients out of range: $matrixCoefficients" }
    }

    /**
     * Returns a human-readable string identifying the named entries
     * for known codes. Useful in diagnostic dumps and the PROBE_RESULTS
     * markdown.
     */
    fun describe(): String {
        val cp = when (colourPrimaries) {
            1 -> "BT.709"
            2 -> "unspecified"
            9 -> "BT.2020"
            11 -> "DCI-P3 Theater"
            12 -> "Display P3 (D65)"
            else -> "primaries=$colourPrimaries"
        }
        val tc = when (transferCharacteristics) {
            1 -> "BT.709"
            2 -> "unspecified"
            8 -> "linear"
            13 -> "sRGB"
            16 -> "PQ"
            18 -> "HLG"
            else -> "transfer=$transferCharacteristics"
        }
        val mc = when (matrixCoefficients) {
            0 -> "RGB-identity"
            1 -> "BT.709"
            2 -> "unspecified"
            9 -> "BT.2020-NCL"
            else -> "matrix=$matrixCoefficients"
        }
        val range = if (videoFullRangeFlag) "full-range" else "narrow-range"
        return "$cp / $tc / $mc / $range"
    }
}
