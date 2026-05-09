package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the AV1 Codec Configuration Box (`av1C`)
 * per the AV1 ISOBMFF Specification §2.3.1 (`AV1CodecConfigurationBox`).
 * In the AVIF / HEIF item world, `av1C` rides as an ItemProperty
 * inside the `ipco` container (Round 24) and is associated to every
 * AV1 image item via `ipma` (Round 23) as **essential** per AVIF
 * spec § 2.2.1.
 *
 * Without `av1C`, no decoder can find the AV1 sequence header
 * needed to bootstrap the AV1 frame decoder — the AV1 image item's
 * bitstream alone is not sufficient because the still-image OBU
 * sequence does NOT carry a temporal-delimiter OBU. AVIF spec
 * § 2.2.1: "The AV1 Image Item shall have an associated `av1C`
 * Item Property [AV1ImageItemProperty] that includes the `av1C`
 * box. The values of fields in the `av1C` Item Property shall be
 * set such that they are consistent with the AV1 bitstream of the
 * AV1 Image Item." So the `av1C` is mandatory, NOT optional.
 *
 * Wire format (5 bytes fixed prefix + variable `configOBUs`):
 *
 * ```
 * unsigned int(1)  marker = 1
 * unsigned int(7)  version = 1
 * unsigned int(3)  seq_profile         // [0, 7]
 * unsigned int(5)  seq_level_idx_0     // [0, 31]
 * unsigned int(1)  seq_tier_0
 * unsigned int(1)  high_bitdepth
 * unsigned int(1)  twelve_bit
 * unsigned int(1)  monochrome
 * unsigned int(1)  chroma_subsampling_x
 * unsigned int(1)  chroma_subsampling_y
 * unsigned int(2)  chroma_sample_position
 * unsigned int(3)  reserved = 0
 * unsigned int(1)  initial_presentation_delay_present
 * if (initial_presentation_delay_present) {
 *     unsigned int(4) initial_presentation_delay_minus_one
 * } else {
 *     unsigned int(4) reserved = 0
 * }
 * unsigned int(8 * N) configOBUs       // optional sequence header OBU
 * ```
 *
 * For typical AVIF stills (single frame, no presentation-delay
 * scheduling), `initial_presentation_delay_present = 0` and the
 * presentation-delay nibble is ignored. The `configOBUs` field is
 * spec-optional but most encoders emit the AV1 sequence header
 * OBU there to make the file fully self-contained.
 *
 * Bit-depth / chroma-subsampling encoding (per AV1-ISOBMFF
 * § 2.3.1 Table 1):
 *
 *  * **8-bit**: `high_bitdepth = 0`, `twelve_bit = 0`.
 *  * **10-bit**: `high_bitdepth = 1`, `twelve_bit = 0`.
 *  * **12-bit**: `high_bitdepth = 1`, `twelve_bit = 1`.
 *
 *  * **YUV 4:2:0**: `chroma_subsampling_x = 1`, `chroma_subsampling_y = 1`.
 *  * **YUV 4:2:2**: `chroma_subsampling_x = 1`, `chroma_subsampling_y = 0`.
 *  * **YUV 4:4:4**: `chroma_subsampling_x = 0`, `chroma_subsampling_y = 0`.
 *  * **Monochrome**: `monochrome = 1`, `chroma_subsampling_x = 1`,
 *    `chroma_subsampling_y = 1` (per AV1 spec § 5.5.2 the chroma
 *    subsampling fields default to 1 when monochrome=1).
 *
 * `chroma_sample_position` per AV1 spec § 6.4.2:
 *
 *  * `0` = unknown
 *  * `1` = vertical (co-sited horizontally, interstitially placed
 *    vertically; legacy MPEG-2 alignment)
 *  * `2` = co-located (co-sited both horizontally and vertically;
 *    Rec. BT.709 / Rec. BT.2020 alignment)
 *  * `3` = reserved
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object Av1CodecConfiguration {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "av1C"

    /** Spec-required marker value (high bit of the first byte). */
    const val MARKER: Int = 1

    /** Spec-required version (low 7 bits of the first byte). */
    const val VERSION: Int = 1

    /**
     * Total byte length of the fixed `av1C` payload prefix
     * (before any optional `configOBUs` bytes).
     */
    const val FIXED_PAYLOAD_PREFIX: Int = 4

    /**
     * Pre-computed payload size when `configOBUs` is empty.
     * Equal to [FIXED_PAYLOAD_PREFIX].
     */
    const val MIN_PAYLOAD_SIZE: Int = FIXED_PAYLOAD_PREFIX

    /** AV1 spec § 6.4.1 — `seq_profile` allowed values. */
    val ALLOWED_SEQ_PROFILES: IntArray = intArrayOf(0, 1, 2)

    /**
     * AV1 spec § A — `seq_level_idx` is a 5-bit field; values
     * `[0, 23]` are defined; `24..31` are reserved. We accept the
     * full 5-bit range and let the encoder caller choose the
     * value matching its bitstream.
     */
    const val MAX_SEQ_LEVEL_IDX: Int = 31

    /** AV1 spec § 6.4.2 — `chroma_sample_position` is a 2-bit field. */
    const val MAX_CHROMA_SAMPLE_POSITION: Int = 3

    /** AV1-ISOBMFF § 2.3.1 — `initial_presentation_delay_minus_one` is a 4-bit field. */
    const val MAX_INITIAL_PRESENTATION_DELAY_MINUS_ONE: Int = 15

    // ------------------------------------------------------------------
    // High-level data-class carrier
    // ------------------------------------------------------------------

    /**
     * Pure-data carrier for the structured `av1C` fields. Produced
     * by an AV1 encoder (or a parser of the AV1 sequence header
     * OBU) and consumed by [encodePayload] / [encodeBox].
     *
     * @param seqProfile AV1 `seq_profile` (spec § 6.4.1; one of [ALLOWED_SEQ_PROFILES]).
     * @param seqLevelIdx0 AV1 `seq_level_idx[0]` ([0, [MAX_SEQ_LEVEL_IDX]]).
     * @param seqTier0 AV1 `seq_tier[0]` (boolean; main vs. high tier).
     * @param highBitdepth AV1 `high_bitdepth` (boolean; 10-/12-bit
     *     vs. 8-bit per spec § 5.5.2).
     * @param twelveBit AV1 `twelve_bit` (boolean; only meaningful
     *     when `highBitdepth = true`; selects 12-bit vs. 10-bit).
     * @param monochrome AV1 `mono_chrome` (boolean; single-channel
     *     image).
     * @param chromaSubsamplingX AV1 `subsampling_x` (boolean).
     * @param chromaSubsamplingY AV1 `subsampling_y` (boolean).
     * @param chromaSamplePosition AV1 `chroma_sample_position`
     *     (spec § 6.4.2; range `[0, 3]`).
     * @param initialPresentationDelayMinusOne if non-null, this
     *     value is encoded in the 4-bit
     *     `initial_presentation_delay_minus_one` field and the
     *     `initial_presentation_delay_present` flag is set; if
     *     null (the typical still-image case), the flag is `0` and
     *     the nibble is reserved zero.
     * @param configOBUs zero-or-more bytes of AV1 OBU data
     *     (typically the sequence-header OBU). Spec-optional but
     *     strongly recommended; passing an empty array signals
     *     "no embedded sequence header" and most decoders will
     *     fail to bootstrap without one.
     */
    data class Config(
        val seqProfile: Int,
        val seqLevelIdx0: Int,
        val seqTier0: Boolean = false,
        val highBitdepth: Boolean = false,
        val twelveBit: Boolean = false,
        val monochrome: Boolean = false,
        val chromaSubsamplingX: Boolean = true,
        val chromaSubsamplingY: Boolean = true,
        val chromaSamplePosition: Int = 0,
        val initialPresentationDelayMinusOne: Int? = null,
        val configOBUs: ByteArray = EMPTY_BYTES,
    ) {
        init {
            require(seqProfile in ALLOWED_SEQ_PROFILES) {
                "seqProfile must be in ${ALLOWED_SEQ_PROFILES.toList()}; got $seqProfile"
            }
            require(seqLevelIdx0 in 0..MAX_SEQ_LEVEL_IDX) {
                "seqLevelIdx0 must be in [0, $MAX_SEQ_LEVEL_IDX]; got $seqLevelIdx0"
            }
            require(chromaSamplePosition in 0..MAX_CHROMA_SAMPLE_POSITION) {
                "chromaSamplePosition must be in [0, $MAX_CHROMA_SAMPLE_POSITION]; got $chromaSamplePosition"
            }
            if (twelveBit) {
                require(highBitdepth) {
                    "twelveBit = true requires highBitdepth = true (per AV1 § 5.5.2)"
                }
            }
            if (initialPresentationDelayMinusOne != null) {
                require(initialPresentationDelayMinusOne in 0..MAX_INITIAL_PRESENTATION_DELAY_MINUS_ONE) {
                    "initialPresentationDelayMinusOne must be in [0, $MAX_INITIAL_PRESENTATION_DELAY_MINUS_ONE]; " +
                        "got $initialPresentationDelayMinusOne"
                }
            }
        }

        /**
         * Equality for [configOBUs] is content-based per
         * `ByteArray` convention.
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Config) return false
            return seqProfile == other.seqProfile &&
                seqLevelIdx0 == other.seqLevelIdx0 &&
                seqTier0 == other.seqTier0 &&
                highBitdepth == other.highBitdepth &&
                twelveBit == other.twelveBit &&
                monochrome == other.monochrome &&
                chromaSubsamplingX == other.chromaSubsamplingX &&
                chromaSubsamplingY == other.chromaSubsamplingY &&
                chromaSamplePosition == other.chromaSamplePosition &&
                initialPresentationDelayMinusOne == other.initialPresentationDelayMinusOne &&
                configOBUs.contentEquals(other.configOBUs)
        }

        override fun hashCode(): Int {
            var h = seqProfile
            h = 31 * h + seqLevelIdx0
            h = 31 * h + seqTier0.hashCode()
            h = 31 * h + highBitdepth.hashCode()
            h = 31 * h + twelveBit.hashCode()
            h = 31 * h + monochrome.hashCode()
            h = 31 * h + chromaSubsamplingX.hashCode()
            h = 31 * h + chromaSubsamplingY.hashCode()
            h = 31 * h + chromaSamplePosition
            h = 31 * h + (initialPresentationDelayMinusOne ?: -1)
            h = 31 * h + configOBUs.contentHashCode()
            return h
        }

        companion object {
            private val EMPTY_BYTES = ByteArray(0)

            /**
             * Canonical default config for an 8-bit YUV 4:2:0
             * still image (the most common AVIF camera output).
             * `seq_profile = 0`, `seq_level_idx_0 = 8` (Level 4.0,
             * suitable for HD), `chroma_sample_position = 0`
             * (unknown — let the decoder default).
             */
            val DEFAULT_8BIT_YUV420: Config = Config(
                seqProfile = 0,
                seqLevelIdx0 = 8,
                highBitdepth = false,
                twelveBit = false,
                chromaSubsamplingX = true,
                chromaSubsamplingY = true,
            )

            /**
             * Canonical default config for a 10-bit YUV 4:2:0
             * HDR10 still image. `seq_profile = 0`,
             * `seq_level_idx_0 = 13` (Level 5.1, suitable for 4K).
             */
            val DEFAULT_10BIT_YUV420: Config = Config(
                seqProfile = 0,
                seqLevelIdx0 = 13,
                highBitdepth = true,
                twelveBit = false,
                chromaSubsamplingX = true,
                chromaSubsamplingY = true,
            )

            /**
             * Canonical default config for an 8-bit monochrome
             * AV1 still — the typical alpha-channel auxiliary
             * image bitstream for AVIF spec § 3.4. `seq_profile
             * = 0`, `seq_level_idx_0 = 8` (Level 4.0). Chroma
             * subsampling fields default to 1 per AV1 spec when
             * `monochrome = 1` (the encoder writes them but
             * decoders ignore them in monochrome mode).
             */
            val DEFAULT_8BIT_MONOCHROME: Config = Config(
                seqProfile = 0,
                seqLevelIdx0 = 8,
                highBitdepth = false,
                twelveBit = false,
                monochrome = true,
                chromaSubsamplingX = true,
                chromaSubsamplingY = true,
            )
        }
    }

    // ------------------------------------------------------------------
    // Encoders
    // ------------------------------------------------------------------

    /**
     * Encode the `av1C` payload (the bytes after the box header).
     * Caller wraps with `IsobmffBox.encodeBox("av1C", payload)`,
     * or uses [encodeBox] which does the wrap in one call.
     */
    fun encodePayload(config: Config): ByteArray {
        val out = ByteArrayOutputStream(FIXED_PAYLOAD_PREFIX + config.configOBUs.size)

        // Byte 0: marker (1) + version (7).
        // marker is the high bit; version occupies the low 7 bits.
        out.write((MARKER shl 7) or (VERSION and 0x7F))

        // Byte 1: seq_profile (3) + seq_level_idx_0 (5).
        out.write(((config.seqProfile and 0x7) shl 5) or (config.seqLevelIdx0 and 0x1F))

        // Byte 2: seq_tier_0 (1) + high_bitdepth (1) + twelve_bit (1) +
        //         monochrome (1) + chroma_subsampling_x (1) +
        //         chroma_subsampling_y (1) + chroma_sample_position (2).
        var b2 = 0
        if (config.seqTier0) b2 = b2 or 0x80
        if (config.highBitdepth) b2 = b2 or 0x40
        if (config.twelveBit) b2 = b2 or 0x20
        if (config.monochrome) b2 = b2 or 0x10
        if (config.chromaSubsamplingX) b2 = b2 or 0x08
        if (config.chromaSubsamplingY) b2 = b2 or 0x04
        b2 = b2 or (config.chromaSamplePosition and 0x3)
        out.write(b2)

        // Byte 3: reserved (3, all 0) + initial_presentation_delay_present (1) +
        //         initial_presentation_delay_minus_one (4) OR reserved (4).
        var b3 = 0
        if (config.initialPresentationDelayMinusOne != null) {
            b3 = b3 or 0x10
            b3 = b3 or (config.initialPresentationDelayMinusOne and 0xF)
        }
        out.write(b3)

        if (config.configOBUs.isNotEmpty()) {
            out.write(config.configOBUs)
        }
        return out.toByteArray()
    }

    /**
     * Convenience: encode the payload and wrap with
     * `IsobmffBox.encodeBox("av1C", payload)` so the caller gets a
     * complete, mux-ready `av1C` box (header + payload) in one
     * call.
     */
    fun encodeBox(config: Config): ByteArray {
        val payload = encodePayload(config)
        return IsobmffBox.encodeBox(BOX_TYPE, payload)
    }

    /**
     * Decode the `av1C` payload back into a [Config]. Useful for
     * round-trip tests and for parsers that consume bitstreams
     * produced by the matching [encodePayload].
     */
    fun decodePayload(bytes: ByteArray): Config {
        require(bytes.size >= FIXED_PAYLOAD_PREFIX) {
            "av1C payload must be >= $FIXED_PAYLOAD_PREFIX bytes; got ${bytes.size}"
        }

        val b0 = bytes[0].toInt() and 0xFF
        val marker = (b0 ushr 7) and 0x1
        val version = b0 and 0x7F
        require(marker == MARKER) { "av1C marker must be $MARKER; got $marker" }
        require(version == VERSION) { "av1C version must be $VERSION; got $version" }

        val b1 = bytes[1].toInt() and 0xFF
        val seqProfile = (b1 ushr 5) and 0x7
        val seqLevelIdx0 = b1 and 0x1F

        val b2 = bytes[2].toInt() and 0xFF
        val seqTier0 = (b2 and 0x80) != 0
        val highBitdepth = (b2 and 0x40) != 0
        val twelveBit = (b2 and 0x20) != 0
        val monochrome = (b2 and 0x10) != 0
        val chromaSubsamplingX = (b2 and 0x08) != 0
        val chromaSubsamplingY = (b2 and 0x04) != 0
        val chromaSamplePosition = b2 and 0x3

        val b3 = bytes[3].toInt() and 0xFF
        val ipdPresent = (b3 and 0x10) != 0
        val ipd = if (ipdPresent) b3 and 0xF else null

        val configOBUs = if (bytes.size > FIXED_PAYLOAD_PREFIX) {
            bytes.copyOfRange(FIXED_PAYLOAD_PREFIX, bytes.size)
        } else {
            ByteArray(0)
        }

        return Config(
            seqProfile = seqProfile,
            seqLevelIdx0 = seqLevelIdx0,
            seqTier0 = seqTier0,
            highBitdepth = highBitdepth,
            twelveBit = twelveBit,
            monochrome = monochrome,
            chromaSubsamplingX = chromaSubsamplingX,
            chromaSubsamplingY = chromaSubsamplingY,
            chromaSamplePosition = chromaSamplePosition,
            initialPresentationDelayMinusOne = ipd,
            configOBUs = configOBUs,
        )
    }
}
