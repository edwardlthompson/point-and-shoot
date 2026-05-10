package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the HEVC Codec Configuration Box
 * (`hvcC`) per ISO/IEC 14496-15 §8.3.3.1.
 *
 * In the HEIF / HEIC item world, `hvcC` rides as an ItemProperty
 * inside `ipco` (Round 24) and is associated to every HEVC image
 * item (`item_type = "hvc1"`) via `ipma` (Round 23) as
 * **essential** per ISO/IEC 23008-12 §10.2. This is the HEVC
 * counterpart to [Av1CodecConfiguration] (Round 33) — without
 * `hvcC`, no decoder can find the SPS / PPS / VPS NAL units
 * needed to bootstrap the HEVC frame decoder.
 *
 * Wire format per ISO/IEC 14496-15 §8.3.3.1 (the
 * `HEVCDecoderConfigurationRecord`):
 *
 * ```
 * aligned(8) class HEVCDecoderConfigurationRecord {
 *     unsigned int(8)  configurationVersion = 1;
 *     unsigned int(2)  general_profile_space;
 *     unsigned int(1)  general_tier_flag;
 *     unsigned int(5)  general_profile_idc;
 *     unsigned int(32) general_profile_compatibility_flags;
 *     unsigned int(48) general_constraint_indicator_flags;
 *     unsigned int(8)  general_level_idc;
 *     bit(4)           reserved = '1111'b;
 *     unsigned int(12) min_spatial_segmentation_idc;
 *     bit(6)           reserved = '111111'b;
 *     unsigned int(2)  parallelismType;
 *     bit(6)           reserved = '111111'b;
 *     unsigned int(2)  chromaFormat;
 *     bit(5)           reserved = '11111'b;
 *     unsigned int(3)  bitDepthLumaMinus8;
 *     bit(5)           reserved = '11111'b;
 *     unsigned int(3)  bitDepthChromaMinus8;
 *     bit(16)          avgFrameRate;
 *     bit(2)           constantFrameRate;
 *     bit(3)           numTemporalLayers;
 *     bit(1)           temporalIdNested;
 *     unsigned int(2)  lengthSizeMinusOne;
 *     unsigned int(8)  numOfArrays;
 *     for (j = 0; j < numOfArrays; j++) {
 *         bit(1)           array_completeness;
 *         unsigned int(1)  reserved = 0;
 *         unsigned int(6)  NAL_unit_type;
 *         unsigned int(16) numNalus;
 *         for (i = 0; i < numNalus; i++) {
 *             unsigned int(16)        nalUnitLength;
 *             bit(8 * nalUnitLength)  nalUnit;
 *         }
 *     }
 * }
 * ```
 *
 * The fixed header is **23 bytes** (offsets in [encodePayload]
 * comments) before the variable-length NAL-unit-array section.
 * Each NAL unit array contributes `3 + sum(2 + nal.size)` bytes
 * (3-byte array header + per-NAL 2-byte length + payload).
 *
 * For HEIF still images, `numNalus` per array is typically 1 (one
 * VPS, one SPS, one PPS — `array_completeness = 1` for all three),
 * giving a typical `hvcC` total size of ~50–80 bytes for HEVC Main
 * Profile, ~60–110 bytes for Main 10 (the common HDR10 config).
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 *
 * @see Av1CodecConfiguration — the AV1 / AVIF counterpart.
 * @see ItemInfoEntry.ITEM_TYPE_HVC1 — emit an HEVC image item via
 *     an `infe` entry with `itemType = "hvc1"`.
 */
object HevcCodecConfiguration {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Box type for the HEVC Codec Configuration Box. */
    const val BOX_TYPE: String = "hvcC"

    /** Pinned `configurationVersion` per ISO/IEC 14496-15 §8.3.3.1. */
    const val CONFIGURATION_VERSION: Int = 1

    /**
     * Fixed prefix length (bytes) of the HEVCDecoderConfigurationRecord
     * before the per-array NAL unit table starts. 1 (configVersion) +
     * 1 (profile_space/tier/profile_idc) + 4 (compat flags) +
     * 6 (constraint flags) + 1 (level_idc) + 2 (min_spatial_segmentation)
     * + 1 (parallelismType) + 1 (chromaFormat) + 1 (bit depth luma) +
     * 1 (bit depth chroma) + 2 (avgFrameRate) +
     * 1 (constantFrameRate / numTemporalLayers / temporalIdNested /
     *    lengthSizeMinusOne) + 1 (numOfArrays) = 23.
     */
    const val FIXED_PAYLOAD_PREFIX: Int = 23

    /** Per-NAL-unit header bytes inside an array (1 byte for completeness/type). */
    const val NAL_ARRAY_HEADER_BYTES: Int = 3

    /** Per-NAL-unit length field inside an array (uint16 BE). */
    const val NAL_LENGTH_FIELD_BYTES: Int = 2

    /** Maximum value for `general_profile_space` (2 bits). */
    const val MAX_GENERAL_PROFILE_SPACE: Int = 3

    /** Maximum value for `general_profile_idc` (5 bits). */
    const val MAX_GENERAL_PROFILE_IDC: Int = 31

    /** Maximum value for `min_spatial_segmentation_idc` (12 bits). */
    const val MAX_MIN_SPATIAL_SEGMENTATION_IDC: Int = 4095

    /** Maximum value for `parallelismType` (2 bits). */
    const val MAX_PARALLELISM_TYPE: Int = 3

    /** Maximum value for `chromaFormat` (2 bits). */
    const val MAX_CHROMA_FORMAT: Int = 3

    /** Maximum value for `bitDepthLumaMinus8` and `bitDepthChromaMinus8` (3 bits). */
    const val MAX_BIT_DEPTH_MINUS_8: Int = 7

    /** Maximum value for `constantFrameRate` (2 bits). */
    const val MAX_CONSTANT_FRAME_RATE: Int = 3

    /** Maximum value for `numTemporalLayers` (3 bits). */
    const val MAX_NUM_TEMPORAL_LAYERS: Int = 7

    /** Maximum value for `lengthSizeMinusOne` (2 bits); typically 3 (length-prefixed). */
    const val MAX_LENGTH_SIZE_MINUS_ONE: Int = 3

    /** Maximum value for `NAL_unit_type` (6 bits). */
    const val MAX_NAL_UNIT_TYPE: Int = 63

    /** HEVC NAL unit type for the Video Parameter Set (VPS). */
    const val NAL_UNIT_TYPE_VPS: Int = 32

    /** HEVC NAL unit type for the Sequence Parameter Set (SPS). */
    const val NAL_UNIT_TYPE_SPS: Int = 33

    /** HEVC NAL unit type for the Picture Parameter Set (PPS). */
    const val NAL_UNIT_TYPE_PPS: Int = 34

    /** HEVC NAL unit type for prefix supplemental enhancement information (SEI). */
    const val NAL_UNIT_TYPE_PREFIX_SEI: Int = 39

    /** HEVC NAL unit type for suffix supplemental enhancement information (SEI). */
    const val NAL_UNIT_TYPE_SUFFIX_SEI: Int = 40

    /** Profile IDC for HEVC Main Profile (8-bit YUV 4:2:0). */
    const val PROFILE_IDC_MAIN: Int = 1

    /** Profile IDC for HEVC Main 10 Profile (10-bit YUV 4:2:0; HDR10 default). */
    const val PROFILE_IDC_MAIN_10: Int = 2

    /** Profile IDC for HEVC Main Still Picture Profile (8-bit YUV 4:2:0 still). */
    const val PROFILE_IDC_MAIN_STILL_PICTURE: Int = 3

    /** Chroma format value for monochrome (4:0:0). */
    const val CHROMA_FORMAT_MONOCHROME: Int = 0

    /** Chroma format value for YUV 4:2:0. */
    const val CHROMA_FORMAT_YUV420: Int = 1

    /** Chroma format value for YUV 4:2:2. */
    const val CHROMA_FORMAT_YUV422: Int = 2

    /** Chroma format value for YUV 4:4:4. */
    const val CHROMA_FORMAT_YUV444: Int = 3

    /**
     * One NAL unit array entry per
     * `HEVCDecoderConfigurationRecord` table. Holds the
     * `array_completeness` flag, the NAL unit type (VPS / SPS /
     * PPS / SEI), and the list of NAL unit byte arrays.
     *
     * @param arrayCompleteness `true` when this is the complete
     *     set of NAL units of the given type for the stream
     *     (typical for HEIF still items: every HEVC still ships
     *     a complete VPS / SPS / PPS).
     * @param nalUnitType the NAL unit type code per ITU-T H.265
     *     §7.4.2.2 (e.g. [NAL_UNIT_TYPE_VPS] = 32, [NAL_UNIT_TYPE_SPS]
     *     = 33, [NAL_UNIT_TYPE_PPS] = 34).
     * @param nalUnits the actual NAL unit byte payloads (no
     *     start codes; just the raw NAL bytes including the
     *     2-byte HEVC NAL header). Each NAL unit's length is
     *     stored on the wire as a uint16 prefix; max length per
     *     NAL is `0xFFFF` bytes.
     */
    data class NalUnitArray(
        val arrayCompleteness: Boolean,
        val nalUnitType: Int,
        val nalUnits: List<ByteArray>,
    ) {
        init {
            require(nalUnitType in 0..MAX_NAL_UNIT_TYPE) {
                "nalUnitType must be in [0, $MAX_NAL_UNIT_TYPE]; got $nalUnitType"
            }
            require(nalUnits.isNotEmpty()) {
                "nalUnits must be non-empty (a NAL array with zero NAL units is degenerate)"
            }
            require(nalUnits.size <= 0xFFFF) {
                "nalUnits.size must fit in uint16; got ${nalUnits.size}"
            }
            for ((idx, nal) in nalUnits.withIndex()) {
                require(nal.isNotEmpty()) {
                    "nalUnits[$idx] is empty"
                }
                require(nal.size <= 0xFFFF) {
                    "nalUnits[$idx].size must fit in uint16; got ${nal.size}"
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NalUnitArray) return false
            if (arrayCompleteness != other.arrayCompleteness) return false
            if (nalUnitType != other.nalUnitType) return false
            if (nalUnits.size != other.nalUnits.size) return false
            for (i in nalUnits.indices) {
                if (!nalUnits[i].contentEquals(other.nalUnits[i])) return false
            }
            return true
        }

        override fun hashCode(): Int {
            var result = arrayCompleteness.hashCode()
            result = 31 * result + nalUnitType
            for (nal in nalUnits) {
                result = 31 * result + nal.contentHashCode()
            }
            return result
        }
    }

    /**
     * One HEVC Codec Configuration recipe carrying every field
     * of the spec's `HEVCDecoderConfigurationRecord`. Matches
     * the single-pass encoder output: every still image item
     * needs exactly one of these alongside its bitstream.
     *
     * @param generalProfileSpace 2-bit field per H.265 §7.4.3.1;
     *     `0` for the standard profile space (typical).
     * @param generalTierFlag false = Main tier, true = High tier.
     * @param generalProfileIdc 5-bit profile code per H.265 Annex
     *     A; typically [PROFILE_IDC_MAIN] = 1, [PROFILE_IDC_MAIN_10]
     *     = 2, [PROFILE_IDC_MAIN_STILL_PICTURE] = 3.
     * @param generalProfileCompatibilityFlags 32-bit bitmap per
     *     H.265 §7.4.3.1; bit `n` set indicates the stream is
     *     compatible with profile `n`.
     * @param generalConstraintIndicatorFlags 48-bit bitmap per
     *     H.265 §7.4.3.1 carrying constraint flags such as
     *     `general_progressive_source_flag` and
     *     `general_one_picture_only_constraint_flag` (the latter
     *     is `true` for HEIF still items).
     * @param generalLevelIdc 8-bit level code per H.265 Annex A
     *     Table A.6; 90 = Level 3.0, 120 = Level 4.0, 150 = Level
     *     5.0 (typical for 8K stills).
     * @param minSpatialSegmentationIdc 12-bit field per H.265
     *     §7.4.3.1; 0 means "no parallel processing recommended".
     * @param parallelismType 2-bit field per ISO/IEC 14496-15 §8.3.3.1;
     *     0 = unknown.
     * @param chromaFormat 2-bit field; one of
     *     [CHROMA_FORMAT_MONOCHROME], [CHROMA_FORMAT_YUV420],
     *     [CHROMA_FORMAT_YUV422], [CHROMA_FORMAT_YUV444].
     * @param bitDepthLumaMinus8 3-bit field; 0 = 8-bit, 2 = 10-bit
     *     (HDR10), 4 = 12-bit.
     * @param bitDepthChromaMinus8 3-bit field; same coding as
     *     [bitDepthLumaMinus8].
     * @param avgFrameRate 16-bit field; for still items, set to 0
     *     (no temporal information). For video items, the average
     *     frame rate in 256ths of Hz.
     * @param constantFrameRate 2-bit field; 0 = unknown / variable;
     *     1 = constant; 2 = constant within each segment.
     * @param numTemporalLayers 3-bit field; 1 for HEIF stills (no
     *     temporal scalability).
     * @param temporalIdNested 1-bit flag per H.265 §7.4.3.1.
     * @param lengthSizeMinusOne 2-bit field declaring the number
     *     of bytes used to encode each NAL unit's length prefix
     *     in the payload bitstream; 3 = 4-byte length prefix
     *     (typical for HEIF).
     * @param nalUnitArrays the per-NAL-type tables (typically
     *     one each for VPS, SPS, PPS for a HEIF still).
     */
    data class Config(
        val generalProfileSpace: Int = 0,
        val generalTierFlag: Boolean = false,
        val generalProfileIdc: Int,
        val generalProfileCompatibilityFlags: Long,
        val generalConstraintIndicatorFlags: Long,
        val generalLevelIdc: Int,
        val minSpatialSegmentationIdc: Int = 0,
        val parallelismType: Int = 0,
        val chromaFormat: Int,
        val bitDepthLumaMinus8: Int,
        val bitDepthChromaMinus8: Int,
        val avgFrameRate: Int = 0,
        val constantFrameRate: Int = 0,
        val numTemporalLayers: Int = 1,
        val temporalIdNested: Boolean = false,
        val lengthSizeMinusOne: Int = 3,
        val nalUnitArrays: List<NalUnitArray> = emptyList(),
    ) {
        init {
            require(generalProfileSpace in 0..MAX_GENERAL_PROFILE_SPACE) {
                "generalProfileSpace must be in [0, $MAX_GENERAL_PROFILE_SPACE]; got $generalProfileSpace"
            }
            require(generalProfileIdc in 0..MAX_GENERAL_PROFILE_IDC) {
                "generalProfileIdc must be in [0, $MAX_GENERAL_PROFILE_IDC]; got $generalProfileIdc"
            }
            require(generalProfileCompatibilityFlags in 0..0xFFFFFFFFL) {
                "generalProfileCompatibilityFlags must fit in uint32; got $generalProfileCompatibilityFlags"
            }
            require(generalConstraintIndicatorFlags in 0..0xFFFFFFFFFFFFL) {
                "generalConstraintIndicatorFlags must fit in 48 bits; got $generalConstraintIndicatorFlags"
            }
            require(generalLevelIdc in 0..255) {
                "generalLevelIdc must fit in uint8; got $generalLevelIdc"
            }
            require(minSpatialSegmentationIdc in 0..MAX_MIN_SPATIAL_SEGMENTATION_IDC) {
                "minSpatialSegmentationIdc must be in [0, $MAX_MIN_SPATIAL_SEGMENTATION_IDC]; got $minSpatialSegmentationIdc"
            }
            require(parallelismType in 0..MAX_PARALLELISM_TYPE) {
                "parallelismType must be in [0, $MAX_PARALLELISM_TYPE]; got $parallelismType"
            }
            require(chromaFormat in 0..MAX_CHROMA_FORMAT) {
                "chromaFormat must be in [0, $MAX_CHROMA_FORMAT]; got $chromaFormat"
            }
            require(bitDepthLumaMinus8 in 0..MAX_BIT_DEPTH_MINUS_8) {
                "bitDepthLumaMinus8 must be in [0, $MAX_BIT_DEPTH_MINUS_8]; got $bitDepthLumaMinus8"
            }
            require(bitDepthChromaMinus8 in 0..MAX_BIT_DEPTH_MINUS_8) {
                "bitDepthChromaMinus8 must be in [0, $MAX_BIT_DEPTH_MINUS_8]; got $bitDepthChromaMinus8"
            }
            require(avgFrameRate in 0..0xFFFF) {
                "avgFrameRate must fit in uint16; got $avgFrameRate"
            }
            require(constantFrameRate in 0..MAX_CONSTANT_FRAME_RATE) {
                "constantFrameRate must be in [0, $MAX_CONSTANT_FRAME_RATE]; got $constantFrameRate"
            }
            require(numTemporalLayers in 0..MAX_NUM_TEMPORAL_LAYERS) {
                "numTemporalLayers must be in [0, $MAX_NUM_TEMPORAL_LAYERS]; got $numTemporalLayers"
            }
            require(lengthSizeMinusOne in 0..MAX_LENGTH_SIZE_MINUS_ONE) {
                "lengthSizeMinusOne must be in [0, $MAX_LENGTH_SIZE_MINUS_ONE]; got $lengthSizeMinusOne"
            }
            require(nalUnitArrays.size <= 255) {
                "nalUnitArrays.size must fit in uint8; got ${nalUnitArrays.size}"
            }
        }

        companion object {
            /**
             * Canonical HEIF Main Still Picture configuration (8-bit
             * YUV 4:2:0, Level 5.0 — supports up to 8K stills).
             * Profile compatibility flag bit 3 (Main Still Picture)
             * is set per H.265 §A.3.4.
             *
             * `nalUnitArrays` is empty by default — caller fills in
             * the actual VPS / SPS / PPS bytes from the encoder.
             */
            val DEFAULT_8BIT_MAIN_STILL: Config = Config(
                generalProfileIdc = PROFILE_IDC_MAIN_STILL_PICTURE,
                generalProfileCompatibilityFlags = 1L shl PROFILE_IDC_MAIN_STILL_PICTURE,
                generalConstraintIndicatorFlags = 0L,
                generalLevelIdc = 150,
                chromaFormat = CHROMA_FORMAT_YUV420,
                bitDepthLumaMinus8 = 0,
                bitDepthChromaMinus8 = 0,
            )

            /**
             * Canonical HEIF Main 10 Still configuration (10-bit
             * YUV 4:2:0, Level 5.1 — the HDR10 still default).
             * Profile compatibility flag bit 2 (Main 10) is set
             * per H.265 §A.3.2.
             */
            val DEFAULT_10BIT_MAIN_10: Config = Config(
                generalProfileIdc = PROFILE_IDC_MAIN_10,
                generalProfileCompatibilityFlags = 1L shl PROFILE_IDC_MAIN_10,
                generalConstraintIndicatorFlags = 0L,
                generalLevelIdc = 153,
                chromaFormat = CHROMA_FORMAT_YUV420,
                bitDepthLumaMinus8 = 2,
                bitDepthChromaMinus8 = 2,
            )
        }
    }

    /**
     * Compute the byte-exact wire size of [config] without doing
     * the actual encode. Useful for muxers planning `iloc`
     * extents before they have the bytes.
     */
    fun payloadSize(config: Config): Int {
        var size = FIXED_PAYLOAD_PREFIX
        for (array in config.nalUnitArrays) {
            size += NAL_ARRAY_HEADER_BYTES
            for (nal in array.nalUnits) {
                size += NAL_LENGTH_FIELD_BYTES + nal.size
            }
        }
        return size
    }

    /**
     * Encode the HEVCDecoderConfigurationRecord (the body of the
     * `hvcC` box). Caller wraps with [IsobmffBox.encodeBox] (this
     * is a plain Box, not a FullBox).
     */
    fun encodePayload(config: Config): ByteArray {
        val out = ByteArrayOutputStream(payloadSize(config))
        // Offset 0: configurationVersion (uint8).
        out.write(CONFIGURATION_VERSION)
        // Offset 1: profile_space (2) + tier_flag (1) + profile_idc (5).
        val profileByte = ((config.generalProfileSpace and 0x03) shl 6) or
            ((if (config.generalTierFlag) 1 else 0) shl 5) or
            (config.generalProfileIdc and 0x1F)
        out.write(profileByte and 0xFF)
        // Offset 2..5: general_profile_compatibility_flags (uint32 BE).
        writeUint32BE(out, config.generalProfileCompatibilityFlags)
        // Offset 6..11: general_constraint_indicator_flags (48 bits BE).
        writeUint48BE(out, config.generalConstraintIndicatorFlags)
        // Offset 12: general_level_idc (uint8).
        out.write(config.generalLevelIdc and 0xFF)
        // Offset 13..14: 4 bits reserved (1111) + 12-bit min_spatial_segmentation_idc (BE).
        val minSeg = config.minSpatialSegmentationIdc and 0x0FFF
        out.write(0xF0 or ((minSeg ushr 8) and 0x0F))
        out.write(minSeg and 0xFF)
        // Offset 15: 6 bits reserved (111111) + 2-bit parallelismType.
        out.write(0xFC or (config.parallelismType and 0x03))
        // Offset 16: 6 bits reserved (111111) + 2-bit chromaFormat.
        out.write(0xFC or (config.chromaFormat and 0x03))
        // Offset 17: 5 bits reserved (11111) + 3-bit bitDepthLumaMinus8.
        out.write(0xF8 or (config.bitDepthLumaMinus8 and 0x07))
        // Offset 18: 5 bits reserved (11111) + 3-bit bitDepthChromaMinus8.
        out.write(0xF8 or (config.bitDepthChromaMinus8 and 0x07))
        // Offset 19..20: avgFrameRate (uint16 BE).
        writeUint16BE(out, config.avgFrameRate)
        // Offset 21: 2-bit constantFrameRate + 3-bit numTemporalLayers + 1-bit temporalIdNested + 2-bit lengthSizeMinusOne.
        val byte21 = ((config.constantFrameRate and 0x03) shl 6) or
            ((config.numTemporalLayers and 0x07) shl 3) or
            ((if (config.temporalIdNested) 1 else 0) shl 2) or
            (config.lengthSizeMinusOne and 0x03)
        out.write(byte21 and 0xFF)
        // Offset 22: numOfArrays (uint8).
        out.write(config.nalUnitArrays.size and 0xFF)
        // Variable section: per-array (1-byte completeness/type + uint16 numNalus + per-NAL uint16 length + payload).
        for (array in config.nalUnitArrays) {
            val arrayHeader = ((if (array.arrayCompleteness) 1 else 0) shl 7) or
                (array.nalUnitType and 0x3F)
            out.write(arrayHeader and 0xFF)
            writeUint16BE(out, array.nalUnits.size)
            for (nal in array.nalUnits) {
                writeUint16BE(out, nal.size)
                out.write(nal)
            }
        }
        return out.toByteArray()
    }

    /**
     * Wrap [encodePayload] with `IsobmffBox.encodeBox("hvcC", payload)`
     * for a complete, mux-ready box (header + payload). Convenience
     * for the muxer.
     */
    fun encodeBox(config: Config): ByteArray {
        return IsobmffBox.encodeBox(BOX_TYPE, encodePayload(config))
    }

    /**
     * Decode the HEVCDecoderConfigurationRecord from the payload
     * bytes (NOT the full box; caller has already stripped the
     * 8-byte plain-Box header). Throws on under-23-byte input,
     * wrong configurationVersion, or truncation against the
     * declared NAL-array table.
     */
    fun decodePayload(bytes: ByteArray): Config {
        require(bytes.size >= FIXED_PAYLOAD_PREFIX) {
            "hvcC payload must be at least $FIXED_PAYLOAD_PREFIX bytes; got ${bytes.size}"
        }
        val configVersion = bytes[0].toInt() and 0xFF
        require(configVersion == CONFIGURATION_VERSION) {
            "hvcC configurationVersion must be $CONFIGURATION_VERSION; got $configVersion"
        }
        val byte1 = bytes[1].toInt() and 0xFF
        val generalProfileSpace = (byte1 ushr 6) and 0x03
        val generalTierFlag = ((byte1 ushr 5) and 0x01) != 0
        val generalProfileIdc = byte1 and 0x1F
        val generalProfileCompatibilityFlags = readUint32BE(bytes, 2)
        val generalConstraintIndicatorFlags = readUint48BE(bytes, 6)
        val generalLevelIdc = bytes[12].toInt() and 0xFF
        val byte13 = bytes[13].toInt() and 0xFF
        val byte14 = bytes[14].toInt() and 0xFF
        val minSpatialSegmentationIdc = ((byte13 and 0x0F) shl 8) or byte14
        val parallelismType = bytes[15].toInt() and 0x03
        val chromaFormat = bytes[16].toInt() and 0x03
        val bitDepthLumaMinus8 = bytes[17].toInt() and 0x07
        val bitDepthChromaMinus8 = bytes[18].toInt() and 0x07
        val avgFrameRate = readUint16BE(bytes, 19)
        val byte21 = bytes[21].toInt() and 0xFF
        val constantFrameRate = (byte21 ushr 6) and 0x03
        val numTemporalLayers = (byte21 ushr 3) and 0x07
        val temporalIdNested = ((byte21 ushr 2) and 0x01) != 0
        val lengthSizeMinusOne = byte21 and 0x03
        val numOfArrays = bytes[22].toInt() and 0xFF
        val arrays = ArrayList<NalUnitArray>(numOfArrays)
        var cursor = FIXED_PAYLOAD_PREFIX
        repeat(numOfArrays) { arrayIdx ->
            require(cursor + NAL_ARRAY_HEADER_BYTES <= bytes.size) {
                "hvcC truncated at NAL array $arrayIdx header"
            }
            val arrayHeader = bytes[cursor].toInt() and 0xFF
            cursor += 1
            val arrayCompleteness = ((arrayHeader ushr 7) and 0x01) != 0
            val nalUnitType = arrayHeader and 0x3F
            val numNalus = readUint16BE(bytes, cursor)
            cursor += 2
            val nalUnits = ArrayList<ByteArray>(numNalus)
            repeat(numNalus) { nalIdx ->
                require(cursor + NAL_LENGTH_FIELD_BYTES <= bytes.size) {
                    "hvcC truncated at NAL array $arrayIdx NAL $nalIdx length"
                }
                val nalLength = readUint16BE(bytes, cursor)
                cursor += 2
                require(cursor + nalLength <= bytes.size) {
                    "hvcC truncated at NAL array $arrayIdx NAL $nalIdx body (need $nalLength bytes)"
                }
                nalUnits.add(bytes.copyOfRange(cursor, cursor + nalLength))
                cursor += nalLength
            }
            arrays.add(NalUnitArray(arrayCompleteness, nalUnitType, nalUnits))
        }
        return Config(
            generalProfileSpace = generalProfileSpace,
            generalTierFlag = generalTierFlag,
            generalProfileIdc = generalProfileIdc,
            generalProfileCompatibilityFlags = generalProfileCompatibilityFlags,
            generalConstraintIndicatorFlags = generalConstraintIndicatorFlags,
            generalLevelIdc = generalLevelIdc,
            minSpatialSegmentationIdc = minSpatialSegmentationIdc,
            parallelismType = parallelismType,
            chromaFormat = chromaFormat,
            bitDepthLumaMinus8 = bitDepthLumaMinus8,
            bitDepthChromaMinus8 = bitDepthChromaMinus8,
            avgFrameRate = avgFrameRate,
            constantFrameRate = constantFrameRate,
            numTemporalLayers = numTemporalLayers,
            temporalIdNested = temporalIdNested,
            lengthSizeMinusOne = lengthSizeMinusOne,
            nalUnitArrays = arrays,
        )
    }

    private fun writeUint16BE(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeUint32BE(out: ByteArrayOutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    private fun writeUint48BE(out: ByteArrayOutputStream, value: Long) {
        out.write(((value ushr 40) and 0xFF).toInt())
        out.write(((value ushr 32) and 0xFF).toInt())
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    private fun readUint16BE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readUint32BE(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    private fun readUint48BE(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 40) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 32) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 4].toLong() and 0xFF) shl 8) or
            (bytes[offset + 5].toLong() and 0xFF)
    }
}
