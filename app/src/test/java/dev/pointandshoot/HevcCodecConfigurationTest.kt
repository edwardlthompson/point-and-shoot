package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit tests for [HevcCodecConfiguration].
 *
 * The `hvcC` box is the HEVC counterpart to AV1's `av1C` (Round
 * 33). It is mandatory for every HEIF / HEIC image item per
 * ISO/IEC 23008-12 §10.2 and rides as an essential ItemProperty
 * inside `ipco`. These tests verify:
 *
 *  * Every constant pin matches ISO/IEC 14496-15 §8.3.3.1 and
 *    ITU-T H.265 §7.4.x.
 *  * `NalUnitArray` and `Config` validation reject out-of-range
 *    inputs.
 *  * `payloadSize` matches the encoded buffer size byte-exact.
 *  * `encodePayload` produces the byte-exact wire form for the
 *    standard scenarios:
 *      - canonical empty `DEFAULT_8BIT_MAIN_STILL` (23 bytes).
 *      - canonical empty `DEFAULT_10BIT_MAIN_10` (23 bytes,
 *        bit depth bytes differ).
 *      - typical HEIF still with VPS / SPS / PPS NAL units.
 *      - high-tier configurations.
 *      - all-bits-set fields (constraint flags, profile compat).
 *  * `encodeBox` wraps the payload via [IsobmffBox.encodeBox]
 *    with the canonical `hvcC` 8-byte box header.
 *  * `decodePayload` round-trips encoded payloads and rejects
 *    malformed input (under-23-byte / wrong configurationVersion
 *    / truncated NAL arrays).
 *  * Field accessors on the two pre-computed Config presets.
 *  * `Config` and `NalUnitArray` content-based equality / hashCode.
 */
class HevcCodecConfigurationTest {

    // ------------------------------------------------------------------
    // Constant pins
    // ------------------------------------------------------------------

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, HevcCodecConfiguration.SCHEMA_VERSION)
    }

    @Test
    fun `BOX_TYPE pin matches spec`() {
        assertEquals("hvcC", HevcCodecConfiguration.BOX_TYPE)
    }

    @Test
    fun `CONFIGURATION_VERSION pin is 1 per spec`() {
        assertEquals(1, HevcCodecConfiguration.CONFIGURATION_VERSION)
    }

    @Test
    fun `FIXED_PAYLOAD_PREFIX pin is 23`() {
        assertEquals(23, HevcCodecConfiguration.FIXED_PAYLOAD_PREFIX)
    }

    @Test
    fun `NAL_ARRAY_HEADER_BYTES pin is 3`() {
        assertEquals(3, HevcCodecConfiguration.NAL_ARRAY_HEADER_BYTES)
    }

    @Test
    fun `NAL_LENGTH_FIELD_BYTES pin is 2`() {
        assertEquals(2, HevcCodecConfiguration.NAL_LENGTH_FIELD_BYTES)
    }

    @Test
    fun `NAL unit type constants match spec`() {
        assertEquals(32, HevcCodecConfiguration.NAL_UNIT_TYPE_VPS)
        assertEquals(33, HevcCodecConfiguration.NAL_UNIT_TYPE_SPS)
        assertEquals(34, HevcCodecConfiguration.NAL_UNIT_TYPE_PPS)
        assertEquals(39, HevcCodecConfiguration.NAL_UNIT_TYPE_PREFIX_SEI)
        assertEquals(40, HevcCodecConfiguration.NAL_UNIT_TYPE_SUFFIX_SEI)
    }

    @Test
    fun `Profile IDC constants match H_265 Annex A`() {
        assertEquals(1, HevcCodecConfiguration.PROFILE_IDC_MAIN)
        assertEquals(2, HevcCodecConfiguration.PROFILE_IDC_MAIN_10)
        assertEquals(3, HevcCodecConfiguration.PROFILE_IDC_MAIN_STILL_PICTURE)
    }

    @Test
    fun `Chroma format constants match spec`() {
        assertEquals(0, HevcCodecConfiguration.CHROMA_FORMAT_MONOCHROME)
        assertEquals(1, HevcCodecConfiguration.CHROMA_FORMAT_YUV420)
        assertEquals(2, HevcCodecConfiguration.CHROMA_FORMAT_YUV422)
        assertEquals(3, HevcCodecConfiguration.CHROMA_FORMAT_YUV444)
    }

    @Test
    fun `Bit-width maxima pinned`() {
        assertEquals(3, HevcCodecConfiguration.MAX_GENERAL_PROFILE_SPACE)
        assertEquals(31, HevcCodecConfiguration.MAX_GENERAL_PROFILE_IDC)
        assertEquals(4095, HevcCodecConfiguration.MAX_MIN_SPATIAL_SEGMENTATION_IDC)
        assertEquals(3, HevcCodecConfiguration.MAX_PARALLELISM_TYPE)
        assertEquals(3, HevcCodecConfiguration.MAX_CHROMA_FORMAT)
        assertEquals(7, HevcCodecConfiguration.MAX_BIT_DEPTH_MINUS_8)
        assertEquals(3, HevcCodecConfiguration.MAX_CONSTANT_FRAME_RATE)
        assertEquals(7, HevcCodecConfiguration.MAX_NUM_TEMPORAL_LAYERS)
        assertEquals(3, HevcCodecConfiguration.MAX_LENGTH_SIZE_MINUS_ONE)
        assertEquals(63, HevcCodecConfiguration.MAX_NAL_UNIT_TYPE)
    }

    // ------------------------------------------------------------------
    // NalUnitArray validation
    // ------------------------------------------------------------------

    @Test
    fun `NalUnitArray rejects above-63 nalUnitType`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.NalUnitArray(
                arrayCompleteness = true,
                nalUnitType = 64,
                nalUnits = listOf(byteArrayOf(0x40, 0x01)),
            )
        }
    }

    @Test
    fun `NalUnitArray rejects negative nalUnitType`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.NalUnitArray(
                arrayCompleteness = true,
                nalUnitType = -1,
                nalUnits = listOf(byteArrayOf(0x40, 0x01)),
            )
        }
    }

    @Test
    fun `NalUnitArray rejects empty nalUnits`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.NalUnitArray(
                arrayCompleteness = true,
                nalUnitType = HevcCodecConfiguration.NAL_UNIT_TYPE_VPS,
                nalUnits = emptyList(),
            )
        }
    }

    @Test
    fun `NalUnitArray rejects empty NAL bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.NalUnitArray(
                arrayCompleteness = true,
                nalUnitType = HevcCodecConfiguration.NAL_UNIT_TYPE_VPS,
                nalUnits = listOf(byteArrayOf()),
            )
        }
    }

    @Test
    fun `NalUnitArray rejects NAL above-uint16 length`() {
        val tooBig = ByteArray(0x10000)
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.NalUnitArray(
                arrayCompleteness = true,
                nalUnitType = HevcCodecConfiguration.NAL_UNIT_TYPE_VPS,
                nalUnits = listOf(tooBig),
            )
        }
    }

    @Test
    fun `NalUnitArray content equals comparison`() {
        val a = HevcCodecConfiguration.NalUnitArray(
            arrayCompleteness = true,
            nalUnitType = 32,
            nalUnits = listOf(byteArrayOf(0x40, 0x01, 0x0C, 0x01)),
        )
        val b = HevcCodecConfiguration.NalUnitArray(
            arrayCompleteness = true,
            nalUnitType = 32,
            nalUnits = listOf(byteArrayOf(0x40, 0x01, 0x0C, 0x01)),
        )
        val c = HevcCodecConfiguration.NalUnitArray(
            arrayCompleteness = false,
            nalUnitType = 32,
            nalUnits = listOf(byteArrayOf(0x40, 0x01, 0x0C, 0x01)),
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    // ------------------------------------------------------------------
    // Config validation
    // ------------------------------------------------------------------

    @Test
    fun `Config rejects above-3 generalProfileSpace`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.Config(
                generalProfileSpace = 4,
                generalProfileIdc = 1,
                generalProfileCompatibilityFlags = 0,
                generalConstraintIndicatorFlags = 0,
                generalLevelIdc = 90,
                chromaFormat = 1,
                bitDepthLumaMinus8 = 0,
                bitDepthChromaMinus8 = 0,
            )
        }
    }

    @Test
    fun `Config rejects above-31 generalProfileIdc`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.Config(
                generalProfileIdc = 32,
                generalProfileCompatibilityFlags = 0,
                generalConstraintIndicatorFlags = 0,
                generalLevelIdc = 90,
                chromaFormat = 1,
                bitDepthLumaMinus8 = 0,
                bitDepthChromaMinus8 = 0,
            )
        }
    }

    @Test
    fun `Config rejects above-uint32 generalProfileCompatibilityFlags`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.Config(
                generalProfileIdc = 1,
                generalProfileCompatibilityFlags = 0x1_00000000L,
                generalConstraintIndicatorFlags = 0,
                generalLevelIdc = 90,
                chromaFormat = 1,
                bitDepthLumaMinus8 = 0,
                bitDepthChromaMinus8 = 0,
            )
        }
    }

    @Test
    fun `Config rejects above-48-bit constraint flags`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.Config(
                generalProfileIdc = 1,
                generalProfileCompatibilityFlags = 0,
                generalConstraintIndicatorFlags = 0x1_0000_0000_0000L,
                generalLevelIdc = 90,
                chromaFormat = 1,
                bitDepthLumaMinus8 = 0,
                bitDepthChromaMinus8 = 0,
            )
        }
    }

    @Test
    fun `Config rejects above-7 bitDepthLumaMinus8`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.Config(
                generalProfileIdc = 1,
                generalProfileCompatibilityFlags = 0,
                generalConstraintIndicatorFlags = 0,
                generalLevelIdc = 90,
                chromaFormat = 1,
                bitDepthLumaMinus8 = 8,
                bitDepthChromaMinus8 = 0,
            )
        }
    }

    @Test
    fun `Config rejects above-3 chromaFormat`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.Config(
                generalProfileIdc = 1,
                generalProfileCompatibilityFlags = 0,
                generalConstraintIndicatorFlags = 0,
                generalLevelIdc = 90,
                chromaFormat = 4,
                bitDepthLumaMinus8 = 0,
                bitDepthChromaMinus8 = 0,
            )
        }
    }

    @Test
    fun `Config rejects above-uint16 avgFrameRate`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.Config(
                generalProfileIdc = 1,
                generalProfileCompatibilityFlags = 0,
                generalConstraintIndicatorFlags = 0,
                generalLevelIdc = 90,
                chromaFormat = 1,
                bitDepthLumaMinus8 = 0,
                bitDepthChromaMinus8 = 0,
                avgFrameRate = 0x10000,
            )
        }
    }

    @Test
    fun `Config rejects above-3 lengthSizeMinusOne`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.Config(
                generalProfileIdc = 1,
                generalProfileCompatibilityFlags = 0,
                generalConstraintIndicatorFlags = 0,
                generalLevelIdc = 90,
                chromaFormat = 1,
                bitDepthLumaMinus8 = 0,
                bitDepthChromaMinus8 = 0,
                lengthSizeMinusOne = 4,
            )
        }
    }

    @Test
    fun `Config rejects above-255 nalUnitArrays`() {
        val arr = HevcCodecConfiguration.NalUnitArray(
            arrayCompleteness = true,
            nalUnitType = 32,
            nalUnits = listOf(byteArrayOf(0x40, 0x01)),
        )
        val tooMany = List(256) { arr }
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.Config(
                generalProfileIdc = 1,
                generalProfileCompatibilityFlags = 0,
                generalConstraintIndicatorFlags = 0,
                generalLevelIdc = 90,
                chromaFormat = 1,
                bitDepthLumaMinus8 = 0,
                bitDepthChromaMinus8 = 0,
                nalUnitArrays = tooMany,
            )
        }
    }

    // ------------------------------------------------------------------
    // payloadSize
    // ------------------------------------------------------------------

    @Test
    fun `payloadSize empty config is 23 bytes`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL
        assertEquals(23, HevcCodecConfiguration.payloadSize(cfg))
    }

    @Test
    fun `payloadSize counts 3 byte array header plus per-NAL 2 byte length plus body`() {
        val vps = byteArrayOf(0x40, 0x01, 0x0C, 0x01)
        val sps = byteArrayOf(0x42, 0x01, 0x01, 0x01, 0x60)
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            nalUnitArrays = listOf(
                HevcCodecConfiguration.NalUnitArray(true, 32, listOf(vps)),
                HevcCodecConfiguration.NalUnitArray(true, 33, listOf(sps)),
            ),
        )
        // 23 fixed + (3 + 2 + 4) for VPS + (3 + 2 + 5) for SPS = 23 + 9 + 10 = 42.
        assertEquals(42, HevcCodecConfiguration.payloadSize(cfg))
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload DEFAULT_8BIT_MAIN_STILL canonical 23-byte layout`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        assertEquals(23, bytes.size)
        assertArrayEquals(
            byteArrayOf(
                0x01, // configurationVersion = 1
                0x03, // profile_space=0 / tier_flag=0 / profile_idc=3 (Main Still)
                0x00, 0x00, 0x00, 0x08, // compat flags = 1 << 3 = 0x00000008
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 48-bit constraint flags = 0
                0x96.toByte(), // level_idc = 150
                0xF0.toByte(), 0x00, // 4 reserved + min_spatial_segmentation = 0
                0xFC.toByte(), // 6 reserved + parallelismType = 0
                0xFD.toByte(), // 6 reserved + chromaFormat = 1
                0xF8.toByte(), // 5 reserved + bitDepthLumaMinus8 = 0
                0xF8.toByte(), // 5 reserved + bitDepthChromaMinus8 = 0
                0x00, 0x00, // avgFrameRate = 0
                0x0B, // 00 (cfr=0) | 001 (temporalLayers=1) | 0 (nested) | 11 (lengthSize=3) = 0b00001011
                0x00, // numOfArrays = 0
            ),
            bytes,
        )
    }

    @Test
    fun `encodePayload DEFAULT_10BIT_MAIN_10 sets profile idc 2 and bit depth 2`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_10BIT_MAIN_10
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        assertEquals(23, bytes.size)
        // byte 1 = profile_idc=2 (Main 10), profile_space=0, tier=0 -> 0x02
        assertEquals(0x02, bytes[1].toInt() and 0xFF)
        // bytes 2..5 = compat flags = 1 << 2 = 4
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x04), bytes.copyOfRange(2, 6))
        // byte 12 = level_idc = 153
        assertEquals(153, bytes[12].toInt() and 0xFF)
        // bytes 17/18 = 5 reserved bits (11111) + bit depth minus 8 = 2
        // 0xFA = 11111010
        assertEquals(0xFA, bytes[17].toInt() and 0xFF)
        assertEquals(0xFA, bytes[18].toInt() and 0xFF)
    }

    @Test
    fun `encodePayload high-tier flag is bit 5 of byte 1`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            generalTierFlag = true,
        )
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        // profile_space=0 (00) | tier=1 (1) | profile_idc=3 (00011) = 0b00100011 = 0x23
        assertEquals(0x23, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `encodePayload generalProfileSpace surfaces in high 2 bits of byte 1`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            generalProfileSpace = 3,
        )
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        // 0b11 (space) | 0 (tier) | 00011 (idc=3) = 0b11000011 = 0xC3
        assertEquals(0xC3, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `encodePayload all-bits-set constraint flags fill 48 bits`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            generalConstraintIndicatorFlags = 0xFFFFFFFFFFFFL,
        )
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            bytes.copyOfRange(6, 12),
        )
    }

    @Test
    fun `encodePayload max compat flags fills uint32`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            generalProfileCompatibilityFlags = 0xFFFFFFFFL,
        )
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            bytes.copyOfRange(2, 6),
        )
    }

    @Test
    fun `encodePayload byte 21 packs constantFrameRate plus temporal plus lengthSize`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            constantFrameRate = 1,
            numTemporalLayers = 5,
            temporalIdNested = true,
            lengthSizeMinusOne = 1,
        )
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        // 0b01 (cfr=1) | 0b101 (temporal=5) | 0b1 (nested) | 0b01 (lengthSize=1)
        // = 01 101 1 01 = 0b01101101 = 0x6D
        assertEquals(0x6D, bytes[21].toInt() and 0xFF)
    }

    @Test
    fun `encodePayload min_spatial_segmentation packs into 12 bits across bytes 13-14`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            minSpatialSegmentationIdc = 0xFFF,
        )
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        // byte 13 = 1111 reserved + 1111 high nibble = 0xFF
        // byte 14 = low byte = 0xFF
        assertEquals(0xFF, bytes[13].toInt() and 0xFF)
        assertEquals(0xFF, bytes[14].toInt() and 0xFF)
    }

    @Test
    fun `encodePayload appends NAL unit arrays after fixed prefix`() {
        val vps = byteArrayOf(0x40, 0x01, 0x0C, 0x01)
        val sps = byteArrayOf(0x42, 0x01, 0x01, 0x01, 0x60)
        val pps = byteArrayOf(0x44, 0x01, 0xC1.toByte(), 0x73)
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            nalUnitArrays = listOf(
                HevcCodecConfiguration.NalUnitArray(true, 32, listOf(vps)),
                HevcCodecConfiguration.NalUnitArray(true, 33, listOf(sps)),
                HevcCodecConfiguration.NalUnitArray(true, 34, listOf(pps)),
            ),
        )
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        // 23 fixed + 3 arrays * (3 array header + 2 length) + 4 + 5 + 4 NAL bytes
        assertEquals(23 + 3 * 5 + 13, bytes.size)
        // numOfArrays = 3 at offset 22
        assertEquals(3, bytes[22].toInt() and 0xFF)
        // Array 0 header = completeness=1 + type=32 = 0b10100000 = 0xA0
        assertEquals(0xA0, bytes[23].toInt() and 0xFF)
        // Array 0 numNalus = 1
        assertEquals(0x00, bytes[24].toInt() and 0xFF)
        assertEquals(0x01, bytes[25].toInt() and 0xFF)
        // Array 0 NAL length = 4 (uint16 BE)
        assertEquals(0x00, bytes[26].toInt() and 0xFF)
        assertEquals(0x04, bytes[27].toInt() and 0xFF)
        // Array 0 NAL bytes (offset 28..31)
        assertArrayEquals(vps, bytes.copyOfRange(28, 32))
    }

    @Test
    fun `encodePayload arrayCompleteness=false sets bit 7 to 0`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            nalUnitArrays = listOf(
                HevcCodecConfiguration.NalUnitArray(false, 32, listOf(byteArrayOf(0x01))),
            ),
        )
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        // Array header at offset 23: completeness=0 + type=32 = 0b00100000 = 0x20
        assertEquals(0x20, bytes[23].toInt() and 0xFF)
    }

    @Test
    fun `encodePayload multi-NAL array emits multiple length-prefixed payloads`() {
        val sps1 = byteArrayOf(0x42, 0x01, 0x01)
        val sps2 = byteArrayOf(0x42, 0x01, 0x02, 0x03)
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            nalUnitArrays = listOf(
                HevcCodecConfiguration.NalUnitArray(true, 33, listOf(sps1, sps2)),
            ),
        )
        val bytes = HevcCodecConfiguration.encodePayload(cfg)
        // 23 fixed + 3 array header + (2 + 3) + (2 + 4) = 23 + 3 + 5 + 6 = 37
        assertEquals(37, bytes.size)
        // Array header at 23: completeness=1 + type=33 = 0xA1
        assertEquals(0xA1, bytes[23].toInt() and 0xFF)
        // numNalus at 24..25 = 2
        assertEquals(0x00, bytes[24].toInt() and 0xFF)
        assertEquals(0x02, bytes[25].toInt() and 0xFF)
        // First NAL length at 26..27 = 3
        assertEquals(0x03, bytes[27].toInt() and 0xFF)
        // First NAL body at 28..30
        assertArrayEquals(sps1, bytes.copyOfRange(28, 31))
        // Second NAL length at 31..32 = 4
        assertEquals(0x04, bytes[32].toInt() and 0xFF)
        // Second NAL body at 33..36
        assertArrayEquals(sps2, bytes.copyOfRange(33, 37))
    }

    // ------------------------------------------------------------------
    // encodeBox
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox empty MAIN_STILL is 31 bytes (8 header + 23 payload)`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL
        val bytes = HevcCodecConfiguration.encodeBox(cfg)
        assertEquals(31, bytes.size)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x1F), bytes.copyOfRange(0, 4))
        // Bytes 4..8 = "hvcC"
        assertArrayEquals(
            byteArrayOf('h'.code.toByte(), 'v'.code.toByte(), 'c'.code.toByte(), 'C'.code.toByte()),
            bytes.copyOfRange(4, 8),
        )
    }

    @Test
    fun `encodeBox grows by NAL array bytes`() {
        val vps = byteArrayOf(0x40, 0x01, 0x0C, 0x01)
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            nalUnitArrays = listOf(
                HevcCodecConfiguration.NalUnitArray(true, 32, listOf(vps)),
            ),
        )
        val bytes = HevcCodecConfiguration.encodeBox(cfg)
        // 8 header + 23 fixed + 3 array header + 2 length + 4 NAL = 40 bytes
        assertEquals(40, bytes.size)
        // Size BE = 0x28 at offset 3
        assertEquals(0x28, bytes[3].toInt() and 0xFF)
    }

    // ------------------------------------------------------------------
    // decodePayload round-trips
    // ------------------------------------------------------------------

    @Test
    fun `decodePayload round-trips DEFAULT_8BIT_MAIN_STILL`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL
        val encoded = HevcCodecConfiguration.encodePayload(cfg)
        val decoded = HevcCodecConfiguration.decodePayload(encoded)
        assertEquals(cfg, decoded)
    }

    @Test
    fun `decodePayload round-trips DEFAULT_10BIT_MAIN_10`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_10BIT_MAIN_10
        val encoded = HevcCodecConfiguration.encodePayload(cfg)
        val decoded = HevcCodecConfiguration.decodePayload(encoded)
        assertEquals(cfg, decoded)
    }

    @Test
    fun `decodePayload round-trips canonical HEIF still with VPS-SPS-PPS`() {
        val vps = byteArrayOf(0x40, 0x01, 0x0C, 0x01, 0xFF.toByte())
        val sps = byteArrayOf(0x42, 0x01, 0x01, 0x01, 0x60, 0x00, 0x00)
        val pps = byteArrayOf(0x44, 0x01, 0xC1.toByte(), 0x73)
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            generalConstraintIndicatorFlags = 0x4000_0000_0000L,
            nalUnitArrays = listOf(
                HevcCodecConfiguration.NalUnitArray(true, 32, listOf(vps)),
                HevcCodecConfiguration.NalUnitArray(true, 33, listOf(sps)),
                HevcCodecConfiguration.NalUnitArray(true, 34, listOf(pps)),
            ),
        )
        val encoded = HevcCodecConfiguration.encodePayload(cfg)
        val decoded = HevcCodecConfiguration.decodePayload(encoded)
        assertEquals(cfg, decoded)
        assertEquals(3, decoded.nalUnitArrays.size)
        assertArrayEquals(vps, decoded.nalUnitArrays[0].nalUnits[0])
        assertArrayEquals(sps, decoded.nalUnitArrays[1].nalUnits[0])
        assertArrayEquals(pps, decoded.nalUnitArrays[2].nalUnits[0])
    }

    @Test
    fun `decodePayload round-trips multi-NAL arrays`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            nalUnitArrays = listOf(
                HevcCodecConfiguration.NalUnitArray(
                    true, 33,
                    listOf(byteArrayOf(0x42, 0x01, 0x01), byteArrayOf(0x42, 0x01, 0x02)),
                ),
            ),
        )
        val encoded = HevcCodecConfiguration.encodePayload(cfg)
        val decoded = HevcCodecConfiguration.decodePayload(encoded)
        assertEquals(cfg, decoded)
    }

    @Test
    fun `decodePayload round-trips high-tier max-flag config`() {
        val cfg = HevcCodecConfiguration.Config(
            generalProfileSpace = 3,
            generalTierFlag = true,
            generalProfileIdc = 31,
            generalProfileCompatibilityFlags = 0xFFFFFFFFL,
            generalConstraintIndicatorFlags = 0xFFFFFFFFFFFFL,
            generalLevelIdc = 255,
            minSpatialSegmentationIdc = 0xFFF,
            parallelismType = 3,
            chromaFormat = 3,
            bitDepthLumaMinus8 = 7,
            bitDepthChromaMinus8 = 7,
            avgFrameRate = 0xFFFF,
            constantFrameRate = 3,
            numTemporalLayers = 7,
            temporalIdNested = true,
            lengthSizeMinusOne = 3,
        )
        val encoded = HevcCodecConfiguration.encodePayload(cfg)
        val decoded = HevcCodecConfiguration.decodePayload(encoded)
        assertEquals(cfg, decoded)
    }

    // ------------------------------------------------------------------
    // decodePayload rejections
    // ------------------------------------------------------------------

    @Test
    fun `decodePayload rejects under-23-byte input`() {
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.decodePayload(ByteArray(22))
        }
    }

    @Test
    fun `decodePayload rejects wrong configurationVersion`() {
        val good = HevcCodecConfiguration.encodePayload(
            HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL,
        )
        val bad = good.copyOf()
        bad[0] = 0x02
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.decodePayload(bad)
        }
    }

    @Test
    fun `decodePayload rejects truncated NAL array header`() {
        // 23 fixed bytes + numOfArrays = 1 but no array header bytes.
        val truncated = HevcCodecConfiguration.encodePayload(
            HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL,
        )
        truncated[22] = 0x01
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.decodePayload(truncated)
        }
    }

    @Test
    fun `decodePayload rejects truncated NAL body`() {
        // Build a config with one NAL of length 10 but with only 5 bytes after the length field.
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL.copy(
            nalUnitArrays = listOf(
                HevcCodecConfiguration.NalUnitArray(true, 32, listOf(ByteArray(10) { 0x01 })),
            ),
        )
        val encoded = HevcCodecConfiguration.encodePayload(cfg)
        // Truncate before the full NAL body (drop last 5 bytes).
        val truncated = encoded.copyOfRange(0, encoded.size - 5)
        assertThrows(IllegalArgumentException::class.java) {
            HevcCodecConfiguration.decodePayload(truncated)
        }
    }

    // ------------------------------------------------------------------
    // Preset field accessors
    // ------------------------------------------------------------------

    @Test
    fun `DEFAULT_8BIT_MAIN_STILL field accessors`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_8BIT_MAIN_STILL
        assertEquals(0, cfg.generalProfileSpace)
        assertFalse(cfg.generalTierFlag)
        assertEquals(3, cfg.generalProfileIdc)
        assertEquals(8L, cfg.generalProfileCompatibilityFlags)
        assertEquals(150, cfg.generalLevelIdc)
        assertEquals(1, cfg.chromaFormat)
        assertEquals(0, cfg.bitDepthLumaMinus8)
        assertEquals(0, cfg.bitDepthChromaMinus8)
        assertEquals(1, cfg.numTemporalLayers)
        assertEquals(3, cfg.lengthSizeMinusOne)
        assertTrue(cfg.nalUnitArrays.isEmpty())
    }

    @Test
    fun `DEFAULT_10BIT_MAIN_10 field accessors`() {
        val cfg = HevcCodecConfiguration.Config.DEFAULT_10BIT_MAIN_10
        assertEquals(2, cfg.generalProfileIdc)
        assertEquals(4L, cfg.generalProfileCompatibilityFlags)
        assertEquals(153, cfg.generalLevelIdc)
        assertEquals(1, cfg.chromaFormat)
        assertEquals(2, cfg.bitDepthLumaMinus8)
        assertEquals(2, cfg.bitDepthChromaMinus8)
    }

    // ------------------------------------------------------------------
    // ItemInfoEntry cross-check
    // ------------------------------------------------------------------

    @Test
    fun `ItemInfoEntry_ITEM_TYPE_HVC1 matches the spec`() {
        assertEquals("hvc1", ItemInfoEntry.ITEM_TYPE_HVC1)
    }
}
