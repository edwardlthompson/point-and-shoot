package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit tests for [Av1CodecConfiguration].
 *
 * Pinned facts (per AV1-ISOBMFF spec § 2.3.1 + AVIF spec § 2.2.1):
 *
 *  * Box type is `"av1C"`.
 *  * Payload begins with a fixed 4-byte prefix:
 *    `(marker | version) (seq_profile | seq_level_idx_0)
 *     (seq_tier_0 | high_bitdepth | twelve_bit | monochrome |
 *      chroma_subsampling_x | chroma_subsampling_y |
 *      chroma_sample_position) (reserved3 | ipd_present | ipd_or_reserved4)`.
 *  * `marker = 1` (always); `version = 1` (always).
 *  * `configOBUs` is variable-length and appended after the fixed
 *    prefix.
 */
class Av1CodecConfigurationTest {

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("av1C", Av1CodecConfiguration.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, Av1CodecConfiguration.SCHEMA_VERSION)
    }

    @Test
    fun `MARKER pin`() {
        assertEquals(1, Av1CodecConfiguration.MARKER)
    }

    @Test
    fun `VERSION pin`() {
        assertEquals(1, Av1CodecConfiguration.VERSION)
    }

    @Test
    fun `FIXED_PAYLOAD_PREFIX pin`() {
        assertEquals(4, Av1CodecConfiguration.FIXED_PAYLOAD_PREFIX)
    }

    @Test
    fun `MIN_PAYLOAD_SIZE pin`() {
        assertEquals(4, Av1CodecConfiguration.MIN_PAYLOAD_SIZE)
    }

    @Test
    fun `ALLOWED_SEQ_PROFILES pin`() {
        assertArrayEquals(intArrayOf(0, 1, 2), Av1CodecConfiguration.ALLOWED_SEQ_PROFILES)
    }

    @Test
    fun `MAX_SEQ_LEVEL_IDX pin`() {
        assertEquals(31, Av1CodecConfiguration.MAX_SEQ_LEVEL_IDX)
    }

    @Test
    fun `MAX_CHROMA_SAMPLE_POSITION pin`() {
        assertEquals(3, Av1CodecConfiguration.MAX_CHROMA_SAMPLE_POSITION)
    }

    @Test
    fun `MAX_INITIAL_PRESENTATION_DELAY_MINUS_ONE pin`() {
        assertEquals(15, Av1CodecConfiguration.MAX_INITIAL_PRESENTATION_DELAY_MINUS_ONE)
    }

    // ------------------------------------------------------------------
    // Config validation
    // ------------------------------------------------------------------

    @Test
    fun `Config rejects seqProfile out of allowed range`() {
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.Config(seqProfile = 3, seqLevelIdx0 = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.Config(seqProfile = -1, seqLevelIdx0 = 0)
        }
    }

    @Test
    fun `Config rejects seqLevelIdx0 out of range`() {
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.Config(seqProfile = 0, seqLevelIdx0 = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.Config(seqProfile = 0, seqLevelIdx0 = 32)
        }
    }

    @Test
    fun `Config rejects chromaSamplePosition out of range`() {
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.Config(
                seqProfile = 0,
                seqLevelIdx0 = 0,
                chromaSamplePosition = -1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.Config(
                seqProfile = 0,
                seqLevelIdx0 = 0,
                chromaSamplePosition = 4,
            )
        }
    }

    @Test
    fun `Config rejects twelveBit without highBitdepth`() {
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.Config(
                seqProfile = 0,
                seqLevelIdx0 = 0,
                highBitdepth = false,
                twelveBit = true,
            )
        }
    }

    @Test
    fun `Config rejects initialPresentationDelay out of range`() {
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.Config(
                seqProfile = 0,
                seqLevelIdx0 = 0,
                initialPresentationDelayMinusOne = -1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.Config(
                seqProfile = 0,
                seqLevelIdx0 = 0,
                initialPresentationDelayMinusOne = 16,
            )
        }
    }

    // ------------------------------------------------------------------
    // encodePayload: byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload DEFAULT_8BIT_YUV420 produces 4-byte canonical layout`() {
        val payload = Av1CodecConfiguration.encodePayload(
            Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
        )
        assertEquals(4, payload.size)
        // Byte 0: marker=1 | version=1 → 0x81
        // Byte 1: seq_profile=0 (3 bits) | seq_level_idx_0=8 (5 bits) → 0x08
        // Byte 2: subsampling_x=1, subsampling_y=1 → 0b00001100 = 0x0C
        // Byte 3: reserved=0, ipd_present=0, reserved=0 → 0x00
        assertArrayEquals(
            byteArrayOf(0x81.toByte(), 0x08, 0x0C, 0x00),
            payload,
        )
    }

    @Test
    fun `encodePayload DEFAULT_10BIT_YUV420 produces 4-byte canonical layout`() {
        val payload = Av1CodecConfiguration.encodePayload(
            Av1CodecConfiguration.Config.DEFAULT_10BIT_YUV420,
        )
        assertEquals(4, payload.size)
        // Byte 0: 0x81 (same as 8-bit)
        // Byte 1: seq_profile=0 | seq_level_idx_0=13 → 0x0D
        // Byte 2: high_bitdepth=1 (0x40) | subsampling_x=1 (0x08) | subsampling_y=1 (0x04) → 0x4C
        // Byte 3: 0x00
        assertArrayEquals(
            byteArrayOf(0x81.toByte(), 0x0D, 0x4C, 0x00),
            payload,
        )
    }

    @Test
    fun `encodePayload 12-bit YUV 4-4-4 high tier`() {
        val payload = Av1CodecConfiguration.encodePayload(
            Av1CodecConfiguration.Config(
                seqProfile = 2,
                seqLevelIdx0 = 13,
                seqTier0 = true,
                highBitdepth = true,
                twelveBit = true,
                chromaSubsamplingX = false,
                chromaSubsamplingY = false,
                chromaSamplePosition = 2,
            ),
        )
        // Byte 0: 0x81
        // Byte 1: seq_profile=2 (010_00000=0x40) | seq_level_idx_0=13 (0x0D) → 0x4D
        // Byte 2: tier=1 (0x80) | high=1 (0x40) | twelve=1 (0x20) | mono=0 |
        //         subx=0 | suby=0 | chroma_pos=2 → 0x80|0x40|0x20|0x02 = 0xE2
        // Byte 3: 0x00
        assertArrayEquals(
            byteArrayOf(0x81.toByte(), 0x4D, 0xE2.toByte(), 0x00),
            payload,
        )
    }

    @Test
    fun `encodePayload monochrome flag surfaces`() {
        val payload = Av1CodecConfiguration.encodePayload(
            Av1CodecConfiguration.Config(
                seqProfile = 0,
                seqLevelIdx0 = 0,
                monochrome = true,
                chromaSubsamplingX = true,
                chromaSubsamplingY = true,
            ),
        )
        // Byte 2: monochrome=1 (0x10) | subsampling x|y bits (0x0C) → 0x1C
        assertEquals(0x1C.toByte(), payload[2])
    }

    @Test
    fun `encodePayload initialPresentationDelay surfaces in byte 3`() {
        val payload = Av1CodecConfiguration.encodePayload(
            Av1CodecConfiguration.Config(
                seqProfile = 0,
                seqLevelIdx0 = 0,
                initialPresentationDelayMinusOne = 7,
            ),
        )
        // Byte 3: ipd_present=1 (0x10) | ipd_minus_one=7 (0x07) → 0x17
        assertEquals(0x17.toByte(), payload[3])
    }

    @Test
    fun `encodePayload appends configOBUs after fixed prefix`() {
        val obu = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D, 0x0E)
        val payload = Av1CodecConfiguration.encodePayload(
            Av1CodecConfiguration.Config(
                seqProfile = 0,
                seqLevelIdx0 = 8,
                chromaSubsamplingX = true,
                chromaSubsamplingY = true,
                configOBUs = obu,
            ),
        )
        assertEquals(4 + obu.size, payload.size)
        assertArrayEquals(obu, payload.copyOfRange(4, payload.size))
    }

    // ------------------------------------------------------------------
    // encodeBox integration
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox produces 12-byte canonical envelope for 8-bit default`() {
        val box = Av1CodecConfiguration.encodeBox(
            Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
        )
        // 8-byte header + 4-byte payload = 12 bytes
        assertEquals(12, box.size)
        // size field is 12 = 0x0000000C
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x0C), box.copyOfRange(0, 4))
        assertEquals("av1C", String(box.copyOfRange(4, 8), Charsets.US_ASCII))
        assertArrayEquals(
            byteArrayOf(0x81.toByte(), 0x08, 0x0C, 0x00),
            box.copyOfRange(8, 12),
        )
    }

    @Test
    fun `encodeBox grows by configOBUs size`() {
        val obu = byteArrayOf(0x01, 0x02, 0x03)
        val box = Av1CodecConfiguration.encodeBox(
            Av1CodecConfiguration.Config(
                seqProfile = 0,
                seqLevelIdx0 = 8,
                chromaSubsamplingX = true,
                chromaSubsamplingY = true,
                configOBUs = obu,
            ),
        )
        assertEquals(8 + 4 + 3, box.size)
    }

    // ------------------------------------------------------------------
    // decodePayload round-trips
    // ------------------------------------------------------------------

    @Test
    fun `decodePayload round-trips 8-bit default`() {
        val original = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420
        val payload = Av1CodecConfiguration.encodePayload(original)
        val decoded = Av1CodecConfiguration.decodePayload(payload)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload round-trips 10-bit default`() {
        val original = Av1CodecConfiguration.Config.DEFAULT_10BIT_YUV420
        val payload = Av1CodecConfiguration.encodePayload(original)
        val decoded = Av1CodecConfiguration.decodePayload(payload)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload round-trips 12-bit YUV 4-4-4 high tier`() {
        val original = Av1CodecConfiguration.Config(
            seqProfile = 2,
            seqLevelIdx0 = 13,
            seqTier0 = true,
            highBitdepth = true,
            twelveBit = true,
            chromaSubsamplingX = false,
            chromaSubsamplingY = false,
            chromaSamplePosition = 2,
        )
        val payload = Av1CodecConfiguration.encodePayload(original)
        val decoded = Av1CodecConfiguration.decodePayload(payload)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload round-trips monochrome with initial presentation delay and OBUs`() {
        val original = Av1CodecConfiguration.Config(
            seqProfile = 1,
            seqLevelIdx0 = 5,
            monochrome = true,
            chromaSubsamplingX = true,
            chromaSubsamplingY = true,
            initialPresentationDelayMinusOne = 3,
            configOBUs = byteArrayOf(0x10, 0x20, 0x30, 0x40),
        )
        val payload = Av1CodecConfiguration.encodePayload(original)
        val decoded = Av1CodecConfiguration.decodePayload(payload)
        assertEquals(original, decoded)
        assertEquals(3, decoded.initialPresentationDelayMinusOne)
    }

    @Test
    fun `decodePayload no presentation delay returns null`() {
        val payload = Av1CodecConfiguration.encodePayload(
            Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
        )
        val decoded = Av1CodecConfiguration.decodePayload(payload)
        assertNull(decoded.initialPresentationDelayMinusOne)
    }

    @Test
    fun `decodePayload rejects under 4 bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.decodePayload(ByteArray(3))
        }
    }

    @Test
    fun `decodePayload rejects wrong marker`() {
        // marker = 0 instead of 1 (high bit cleared)
        val bad = byteArrayOf(0x01, 0x08, 0x0C, 0x00)
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.decodePayload(bad)
        }
    }

    @Test
    fun `decodePayload rejects wrong version`() {
        // marker=1, version=2 (low 7 bits = 2 → 0x82)
        val bad = byteArrayOf(0x82.toByte(), 0x08, 0x0C, 0x00)
        assertThrows(IllegalArgumentException::class.java) {
            Av1CodecConfiguration.decodePayload(bad)
        }
    }

    // ------------------------------------------------------------------
    // Config equality + hash
    // ------------------------------------------------------------------

    @Test
    fun `Config equality compares configOBUs by content`() {
        val a = Av1CodecConfiguration.Config(
            seqProfile = 0,
            seqLevelIdx0 = 8,
            configOBUs = byteArrayOf(1, 2, 3),
        )
        val b = Av1CodecConfiguration.Config(
            seqProfile = 0,
            seqLevelIdx0 = 8,
            configOBUs = byteArrayOf(1, 2, 3),
        )
        val c = Av1CodecConfiguration.Config(
            seqProfile = 0,
            seqLevelIdx0 = 8,
            configOBUs = byteArrayOf(1, 2, 4),
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertTrue(a != c)
    }

    @Test
    fun `Config DEFAULT_8BIT_YUV420 has expected fields`() {
        val cfg = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420
        assertEquals(0, cfg.seqProfile)
        assertEquals(8, cfg.seqLevelIdx0)
        assertEquals(false, cfg.highBitdepth)
        assertEquals(false, cfg.twelveBit)
        assertEquals(true, cfg.chromaSubsamplingX)
        assertEquals(true, cfg.chromaSubsamplingY)
    }

    @Test
    fun `Config DEFAULT_10BIT_YUV420 has expected fields`() {
        val cfg = Av1CodecConfiguration.Config.DEFAULT_10BIT_YUV420
        assertEquals(0, cfg.seqProfile)
        assertEquals(13, cfg.seqLevelIdx0)
        assertEquals(true, cfg.highBitdepth)
        assertEquals(false, cfg.twelveBit)
        assertEquals(true, cfg.chromaSubsamplingX)
        assertEquals(true, cfg.chromaSubsamplingY)
    }
}
