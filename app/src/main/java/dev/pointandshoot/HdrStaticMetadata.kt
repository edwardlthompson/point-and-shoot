package dev.pointandshoot

/**
 * Pure-data formatters for the HDR static-metadata blobs that ride
 * alongside a `colr` (CICP) box in AVIF/HEVC containers:
 *
 *   * `MasteringDisplayMetadata` — SMPTE ST 2086 mastering-display
 *     colour volume (a.k.a. "MDCV", ISOBMFF `mdcv`, HEVC SEI 137 / 144,
 *     defined in ITU-T H.265 §D.2.27 + ISO/IEC 23001-17). Carries the
 *     RGB primaries + whitepoint + min/max luminance of the display
 *     used to grade the master, so a downstream tone mapper can clip
 *     instead of clamping.
 *   * `ContentLightLevel` — CTA-861.3 content light-level information
 *     (a.k.a. "CLLI", ISOBMFF `clli`, HEVC SEI 144 § "Content light
 *     level information"). Carries `MaxCLL` (peak content luminance)
 *     and `MaxFALL` (frame-average peak), so a tone mapper can scale
 *     to the destination display's luminance range.
 *
 * Both blobs are emitted by every reasonable HDR encoder (libavif,
 * libheif, x265) and the values are fundamental for the receiver's
 * tone-mapping path — without them, the decoder is blind to whether
 * the master was graded for a 1000-nit reference monitor or a 4000-nit
 * one.
 *
 * The byte-order semantics here are the **container-neutral** form;
 * specifically these match the ISOBMFF `mdcv` / `clli` box payloads
 * (and the HEVC SEI payloads, which are byte-identical). The capture
 * engine wraps these blobs with the standard BMFF size + box-type
 * prefix (`mdcv` / `clli`) at mux time.
 *
 * **GBR ordering**: ITU-T H.265 §D.2.27 specifies `display_primaries`
 * in **G, B, R order**, NOT R, G, B. This is one of the most-bitten
 * gotchas in the spec; we adopt the same convention in
 * [MasteringDisplayMetadata] so the wire-format conversion is
 * straight-forward.
 *
 * No Android imports - safe for unit testing on the JVM.
 */

/**
 * Mastering display colour volume per SMPTE ST 2086 / ITU-T H.265
 * §D.2.27.
 *
 * Stored in CIE 1931 chromaticities: each `xR`, `yR`, `xG`, ... is in
 * `[0, 1]` (relaxed to the same wide-gamut bounds as
 * [ColorSpaceMatrix.Chromaticity]). Luminance values are in cd/m^2
 * (a.k.a. nits) at full float precision.
 *
 * The on-the-wire encoding scales chromaticities by `50000` (so
 * 0.00002 per ULP) and luminances by `10000` (so 0.0001 cd/m^2 per
 * ULP). [encodeMdcvPayload] handles that scaling.
 */
data class MasteringDisplayMetadata(
    val xR: Float,
    val yR: Float,
    val xG: Float,
    val yG: Float,
    val xB: Float,
    val yB: Float,
    val xWhite: Float,
    val yWhite: Float,
    val maxLuminanceNits: Float,
    val minLuminanceNits: Float,
) {
    init {
        for ((label, x) in listOf("xR" to xR, "xG" to xG, "xB" to xB, "xWhite" to xWhite)) {
            require(x in -0.5f..1.5f) { "$label must be in [-0.5, 1.5] (was $x)" }
        }
        for ((label, y) in listOf("yR" to yR, "yG" to yG, "yB" to yB, "yWhite" to yWhite)) {
            require(y > 0f && y < 1.5f) { "$label must be in (0, 1.5) (was $y)" }
        }
        require(maxLuminanceNits >= 0f) { "maxLuminanceNits must be non-negative (was $maxLuminanceNits)" }
        require(minLuminanceNits >= 0f) { "minLuminanceNits must be non-negative (was $minLuminanceNits)" }
        require(maxLuminanceNits >= minLuminanceNits) {
            "maxLuminanceNits ($maxLuminanceNits) must be >= minLuminanceNits ($minLuminanceNits)"
        }
    }

    /**
     * The sub-blob's internal display order matches ITU-T H.265
     * §D.2.27: the first primary slot is GREEN, the second is BLUE,
     * and the third is RED. Helper that returns the bytes in the
     * spec-mandated order.
     */
    fun primariesGbr(): List<Pair<Float, Float>> = listOf(xG to yG, xB to yB, xR to yR)

    companion object {
        /** Bumped only when the byte-layout schema changes incompatibly. */
        const val SCHEMA_VERSION: Int = 1

        /** Total byte length of the `mdcv` payload (no BMFF box header). */
        const val MDCV_PAYLOAD_LENGTH: Int = 24

        /** Chromaticity-encoding scale factor per ITU-T H.265 §D.2.27. */
        const val CHROMATICITY_SCALE: Int = 50_000

        /** Luminance-encoding scale factor per ITU-T H.265 §D.2.27 (units of 0.0001 cd/m^2). */
        const val LUMINANCE_SCALE: Int = 10_000

        /**
         * Pre-computed Rec.2020 mastering-display profile graded for a
         * 1000-nit reference monitor (the most common HDR10 grade
         * target). Useful as a sane default the engine can ship until
         * the user specifies a different mastering display.
         */
        val REC2020_1000_NITS: MasteringDisplayMetadata = MasteringDisplayMetadata(
            xR = 0.708f, yR = 0.292f,
            xG = 0.170f, yG = 0.797f,
            xB = 0.131f, yB = 0.046f,
            xWhite = 0.3127f, yWhite = 0.3290f,
            maxLuminanceNits = 1000f,
            minLuminanceNits = 0.005f,
        )

        /** Display P3 mastering-display profile at 1000 nits (HDR10 / Apple HDR Photo grade target). */
        val DISPLAY_P3_1000_NITS: MasteringDisplayMetadata = MasteringDisplayMetadata(
            xR = 0.680f, yR = 0.320f,
            xG = 0.265f, yG = 0.690f,
            xB = 0.150f, yB = 0.060f,
            xWhite = 0.3127f, yWhite = 0.3290f,
            maxLuminanceNits = 1000f,
            minLuminanceNits = 0.005f,
        )
    }
}

/**
 * Content light-level information per CTA-861.3.
 *
 * `MaxCLL` is the peak luminance of any single pixel across the entire
 * sequence. `MaxFALL` is the maximum frame-average light level (i.e.
 * the highest per-frame mean luminance). Both are in cd/m^2 (nits)
 * but the wire-format clamps each to a 16-bit unsigned integer
 * (0..65535), so `MaxCLL > 65535` is non-conformant.
 */
data class ContentLightLevel(
    val maxCll: Int,
    val maxFall: Int,
) {
    init {
        require(maxCll in 0..0xFFFF) { "maxCll must be in [0, 65535] (was $maxCll)" }
        require(maxFall in 0..0xFFFF) { "maxFall must be in [0, 65535] (was $maxFall)" }
    }

    companion object {
        /** Bumped only when the byte-layout schema changes incompatibly. */
        const val SCHEMA_VERSION: Int = 1

        /** Total byte length of the `clli` payload (no BMFF box header). */
        const val CLLI_PAYLOAD_LENGTH: Int = 4
    }
}

/**
 * Pure-data byte-layout encoders / decoders for the `mdcv` and `clli`
 * payloads.
 *
 * The byte layouts here match BOTH the ISOBMFF box payloads (used in
 * AVIF / HEIF) AND the HEVC SEI payloads (used in raw HEVC streams) -
 * they're byte-identical per the specs. The capture engine's muxer
 * wraps these with the standard BMFF size + box-type prefix.
 */
object HdrStaticMetadata {

    /**
     * Encode an `mdcv` payload (24 bytes total, all big-endian):
     *
     *     +--------+--------+--------+--------+--------+--------+
     *     | xG (16)| yG (16)| xB (16)| yB (16)| xR (16)| yR (16)|
     *     +--------+--------+--------+--------+--------+--------+
     *     |xWP (16)|yWP (16)|maxLum (32)      |minLum (32)      |
     *     +--------+--------+-----------------+-----------------+
     *
     * Chromaticities are scaled by 50_000 (so 0.708 -> 35400);
     * luminances are scaled by 10_000 (so 1000 cd/m^2 -> 10_000_000).
     * Both scaling steps round to the nearest integer.
     */
    fun encodeMdcvPayload(meta: MasteringDisplayMetadata): ByteArray {
        val out = ByteArray(MasteringDisplayMetadata.MDCV_PAYLOAD_LENGTH)
        var off = 0
        for ((x, y) in meta.primariesGbr()) {
            writeUInt16Be(out, off, scaleChromaticity(x)); off += 2
            writeUInt16Be(out, off, scaleChromaticity(y)); off += 2
        }
        writeUInt16Be(out, off, scaleChromaticity(meta.xWhite)); off += 2
        writeUInt16Be(out, off, scaleChromaticity(meta.yWhite)); off += 2
        writeUInt32Be(out, off, scaleLuminance(meta.maxLuminanceNits)); off += 4
        writeUInt32Be(out, off, scaleLuminance(meta.minLuminanceNits)); off += 4
        check(off == out.size) { "off=$off, expected ${out.size}" }
        return out
    }

    /**
     * Decode an `mdcv` payload back into a [MasteringDisplayMetadata].
     * Throws [IllegalArgumentException] if the byte length is wrong.
     */
    fun decodeMdcvPayload(bytes: ByteArray): MasteringDisplayMetadata {
        require(bytes.size == MasteringDisplayMetadata.MDCV_PAYLOAD_LENGTH) {
            "mdcv payload must be exactly ${MasteringDisplayMetadata.MDCV_PAYLOAD_LENGTH} bytes (was ${bytes.size})"
        }
        var off = 0
        val xG = unscaleChromaticity(readUInt16Be(bytes, off)); off += 2
        val yG = unscaleChromaticity(readUInt16Be(bytes, off)); off += 2
        val xB = unscaleChromaticity(readUInt16Be(bytes, off)); off += 2
        val yB = unscaleChromaticity(readUInt16Be(bytes, off)); off += 2
        val xR = unscaleChromaticity(readUInt16Be(bytes, off)); off += 2
        val yR = unscaleChromaticity(readUInt16Be(bytes, off)); off += 2
        val xWp = unscaleChromaticity(readUInt16Be(bytes, off)); off += 2
        val yWp = unscaleChromaticity(readUInt16Be(bytes, off)); off += 2
        val maxL = unscaleLuminance(readUInt32Be(bytes, off)); off += 4
        val minL = unscaleLuminance(readUInt32Be(bytes, off)); off += 4
        check(off == bytes.size)
        return MasteringDisplayMetadata(
            xR = xR, yR = yR, xG = xG, yG = yG, xB = xB, yB = yB,
            xWhite = xWp, yWhite = yWp,
            maxLuminanceNits = maxL, minLuminanceNits = minL,
        )
    }

    /**
     * Encode a `clli` payload (4 bytes, big-endian):
     *
     *     +--------+--------+
     *     | maxCLL | maxFALL|
     *     +--------+--------+
     */
    fun encodeClliPayload(clli: ContentLightLevel): ByteArray {
        val out = ByteArray(ContentLightLevel.CLLI_PAYLOAD_LENGTH)
        writeUInt16Be(out, 0, clli.maxCll)
        writeUInt16Be(out, 2, clli.maxFall)
        return out
    }

    /** Decode a `clli` payload. Throws on wrong length. */
    fun decodeClliPayload(bytes: ByteArray): ContentLightLevel {
        require(bytes.size == ContentLightLevel.CLLI_PAYLOAD_LENGTH) {
            "clli payload must be exactly ${ContentLightLevel.CLLI_PAYLOAD_LENGTH} bytes (was ${bytes.size})"
        }
        return ContentLightLevel(
            maxCll = readUInt16Be(bytes, 0),
            maxFall = readUInt16Be(bytes, 2),
        )
    }

    internal fun scaleChromaticity(x: Float): Int {
        val scaled = (x * MasteringDisplayMetadata.CHROMATICITY_SCALE).let {
            if (it >= 0f) (it + 0.5f).toInt() else (it - 0.5f).toInt()
        }
        return scaled.coerceIn(0, 0xFFFF)
    }

    internal fun unscaleChromaticity(scaled: Int): Float =
        scaled.toFloat() / MasteringDisplayMetadata.CHROMATICITY_SCALE

    internal fun scaleLuminance(nits: Float): Long {
        val scaled = (nits.toDouble() * MasteringDisplayMetadata.LUMINANCE_SCALE + 0.5).toLong()
        return scaled.coerceIn(0L, 0xFFFFFFFFL)
    }

    internal fun unscaleLuminance(scaled: Long): Float =
        (scaled.toDouble() / MasteringDisplayMetadata.LUMINANCE_SCALE).toFloat()

    private fun writeUInt16Be(buf: ByteArray, off: Int, value: Int) {
        buf[off] = ((value ushr 8) and 0xFF).toByte()
        buf[off + 1] = (value and 0xFF).toByte()
    }

    private fun writeUInt32Be(buf: ByteArray, off: Int, value: Long) {
        buf[off] = ((value ushr 24) and 0xFF).toByte()
        buf[off + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[off + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[off + 3] = (value and 0xFF).toByte()
    }

    private fun readUInt16Be(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    private fun readUInt32Be(buf: ByteArray, off: Int): Long =
        ((buf[off].toLong() and 0xFF) shl 24) or
            ((buf[off + 1].toLong() and 0xFF) shl 16) or
            ((buf[off + 2].toLong() and 0xFF) shl 8) or
            (buf[off + 3].toLong() and 0xFF)
}
