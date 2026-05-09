package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit tests for [AvifImageOverlay].
 *
 * The iovl derived-image item is the AVIF / HEIF mechanism for
 * compositing N source items at signed `(x, y)` offsets onto a
 * fill-colored canvas (ISO/IEC 23008-12 §6.6.2.4). These tests
 * verify:
 *
 *  * Every constant pin matches the spec.
 *  * `Reference` and `Payload` validation reject out-of-range
 *    inputs.
 *  * `chooseFlags` picks the 16-bit form for compact iovls and
 *    auto-promotes to 32-bit when canvas dims OR any reference
 *    offset overflows the int16 range.
 *  * `payloadSize` matches the encoded buffer size byte-exact.
 *  * `encodePayload` produces the byte-exact wire form for
 *    several scenarios: empty references, two-reference 16-bit,
 *    auto-promoted 32-bit references, force-32 flag, signed
 *    negative offsets in two's-complement.
 *  * `decodePayload` round-trips encoded payloads (caller
 *    supplies the reference count from the matching iref dimg)
 *    and rejects malformed input.
 */
class AvifImageOverlayTest {

    // ------------------------------------------------------------------
    // Constant pins
    // ------------------------------------------------------------------

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, AvifImageOverlay.SCHEMA_VERSION)
    }

    @Test
    fun `ITEM_TYPE pin matches spec`() {
        assertEquals("iovl", AvifImageOverlay.ITEM_TYPE)
    }

    @Test
    fun `VERSION pin matches spec`() {
        assertEquals(0, AvifImageOverlay.VERSION)
    }

    @Test
    fun `FLAG_FIELD_LENGTH_32 pin is bit zero`() {
        assertEquals(0x01, AvifImageOverlay.FLAG_FIELD_LENGTH_32)
    }

    @Test
    fun `CANVAS_FILL_CHANNELS pin is 4 (RGBA)`() {
        assertEquals(4, AvifImageOverlay.CANVAS_FILL_CHANNELS)
    }

    @Test
    fun `CANVAS_FILL_BYTES_PER_CHANNEL pin is 2`() {
        assertEquals(2, AvifImageOverlay.CANVAS_FILL_BYTES_PER_CHANNEL)
    }

    @Test
    fun `CANVAS_FILL_TOTAL_BYTES pin is 8`() {
        assertEquals(8, AvifImageOverlay.CANVAS_FILL_TOTAL_BYTES)
    }

    @Test
    fun `MIN_PAYLOAD_SIZE_16 pin is 14 bytes`() {
        assertEquals(14, AvifImageOverlay.MIN_PAYLOAD_SIZE_16)
    }

    @Test
    fun `MIN_PAYLOAD_SIZE_32 pin is 18 bytes`() {
        assertEquals(18, AvifImageOverlay.MIN_PAYLOAD_SIZE_32)
    }

    @Test
    fun `REFERENCE_BYTES_16 pin is 4 bytes`() {
        assertEquals(4, AvifImageOverlay.REFERENCE_BYTES_16)
    }

    @Test
    fun `REFERENCE_BYTES_32 pin is 8 bytes`() {
        assertEquals(8, AvifImageOverlay.REFERENCE_BYTES_32)
    }

    @Test
    fun `MAX_OUTPUT_DIMENSION_16 pin is 65535`() {
        assertEquals(65535L, AvifImageOverlay.MAX_OUTPUT_DIMENSION_16)
    }

    @Test
    fun `MAX_OUTPUT_DIMENSION_32 pin is 0xFFFFFFFF`() {
        assertEquals(0xFFFFFFFFL, AvifImageOverlay.MAX_OUTPUT_DIMENSION_32)
    }

    @Test
    fun `MAX_REFERENCE_OFFSET_16 pin is 32767`() {
        assertEquals(32767L, AvifImageOverlay.MAX_REFERENCE_OFFSET_16)
    }

    @Test
    fun `MIN_REFERENCE_OFFSET_16 pin is -32768`() {
        assertEquals(-32768L, AvifImageOverlay.MIN_REFERENCE_OFFSET_16)
    }

    @Test
    fun `MAX_REFERENCE_OFFSET_32 pin is Int_MAX_VALUE`() {
        assertEquals(2147483647L, AvifImageOverlay.MAX_REFERENCE_OFFSET_32)
    }

    @Test
    fun `MIN_REFERENCE_OFFSET_32 pin is Int_MIN_VALUE`() {
        assertEquals(-2147483648L, AvifImageOverlay.MIN_REFERENCE_OFFSET_32)
    }

    @Test
    fun `MAX_CANVAS_FILL_VALUE pin is 65535`() {
        assertEquals(65535, AvifImageOverlay.MAX_CANVAS_FILL_VALUE)
    }

    // ------------------------------------------------------------------
    // Reference validation
    // ------------------------------------------------------------------

    @Test
    fun `Reference rejects above-int32 horizontalOffset`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.Reference(horizontalOffset = 2147483648L, verticalOffset = 0)
        }
    }

    @Test
    fun `Reference rejects below-int32 verticalOffset`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.Reference(horizontalOffset = 0, verticalOffset = -2147483649L)
        }
    }

    @Test
    fun `Reference accepts the int32 boundaries`() {
        val ref = AvifImageOverlay.Reference(
            horizontalOffset = 2147483647L,
            verticalOffset = -2147483648L,
        )
        assertEquals(2147483647L, ref.horizontalOffset)
        assertEquals(-2147483648L, ref.verticalOffset)
    }

    // ------------------------------------------------------------------
    // Payload validation
    // ------------------------------------------------------------------

    @Test
    fun `Payload rejects negative canvasFillR`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.Payload(
                canvasFillR = -1,
                canvasFillG = 0,
                canvasFillB = 0,
                canvasFillA = 0,
                outputWidth = 100,
                outputHeight = 100,
            )
        }
    }

    @Test
    fun `Payload rejects above-uint16 canvasFillG`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.Payload(
                canvasFillR = 0,
                canvasFillG = 65536,
                canvasFillB = 0,
                canvasFillA = 0,
                outputWidth = 100,
                outputHeight = 100,
            )
        }
    }

    @Test
    fun `Payload rejects zero outputWidth`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.Payload(
                canvasFillR = 0,
                canvasFillG = 0,
                canvasFillB = 0,
                canvasFillA = 0,
                outputWidth = 0,
                outputHeight = 100,
            )
        }
    }

    @Test
    fun `Payload rejects above-uint32 outputHeight`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.Payload(
                canvasFillR = 0,
                canvasFillG = 0,
                canvasFillB = 0,
                canvasFillA = 0,
                outputWidth = 100,
                outputHeight = 0x1_00000000L,
            )
        }
    }

    @Test
    fun `Payload accepts canonical sticker overlay`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0xFFFF,
            canvasFillG = 0xFFFF,
            canvasFillB = 0xFFFF,
            canvasFillA = 0,
            outputWidth = 1920,
            outputHeight = 1080,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = 0, verticalOffset = 0),
                AvifImageOverlay.Reference(horizontalOffset = 100, verticalOffset = 50),
            ),
        )
        assertEquals(1920L, payload.outputWidth)
        assertEquals(2, payload.references.size)
    }

    @Test
    fun `Payload defaults references to empty`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0,
            canvasFillG = 0,
            canvasFillB = 0,
            canvasFillA = 0,
            outputWidth = 100,
            outputHeight = 100,
        )
        assertTrue(payload.references.isEmpty())
    }

    // ------------------------------------------------------------------
    // chooseFlags
    // ------------------------------------------------------------------

    @Test
    fun `chooseFlags picks 0 for compact 16-bit case`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0,
            canvasFillG = 0,
            canvasFillB = 0,
            canvasFillA = 0xFFFF,
            outputWidth = 1920,
            outputHeight = 1080,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = -100, verticalOffset = -100),
                AvifImageOverlay.Reference(horizontalOffset = 32767, verticalOffset = -32768),
            ),
        )
        assertEquals(0, AvifImageOverlay.chooseFlags(payload))
    }

    @Test
    fun `chooseFlags promotes when outputWidth overflows int16`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0,
            canvasFillG = 0,
            canvasFillB = 0,
            canvasFillA = 0,
            outputWidth = 65536,
            outputHeight = 100,
        )
        assertEquals(AvifImageOverlay.FLAG_FIELD_LENGTH_32, AvifImageOverlay.chooseFlags(payload))
    }

    @Test
    fun `chooseFlags promotes when reference horizontalOffset overflows int16`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0,
            canvasFillG = 0,
            canvasFillB = 0,
            canvasFillA = 0,
            outputWidth = 100,
            outputHeight = 100,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = 32768, verticalOffset = 0),
            ),
        )
        assertEquals(AvifImageOverlay.FLAG_FIELD_LENGTH_32, AvifImageOverlay.chooseFlags(payload))
    }

    @Test
    fun `chooseFlags promotes when reference verticalOffset is below int16 min`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0,
            canvasFillG = 0,
            canvasFillB = 0,
            canvasFillA = 0,
            outputWidth = 100,
            outputHeight = 100,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = 0, verticalOffset = -32769),
            ),
        )
        assertEquals(AvifImageOverlay.FLAG_FIELD_LENGTH_32, AvifImageOverlay.chooseFlags(payload))
    }

    // ------------------------------------------------------------------
    // payloadSize
    // ------------------------------------------------------------------

    @Test
    fun `payloadSize empty 16-bit payload is 14`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
            outputWidth = 100, outputHeight = 100,
        )
        assertEquals(14, AvifImageOverlay.payloadSize(payload))
    }

    @Test
    fun `payloadSize empty 32-bit payload is 18`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
            outputWidth = 100, outputHeight = 100,
        )
        assertEquals(18, AvifImageOverlay.payloadSize(payload, AvifImageOverlay.FLAG_FIELD_LENGTH_32))
    }

    @Test
    fun `payloadSize 2-ref 16-bit payload is 22`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
            outputWidth = 100, outputHeight = 100,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = 0, verticalOffset = 0),
                AvifImageOverlay.Reference(horizontalOffset = 0, verticalOffset = 0),
            ),
        )
        assertEquals(14 + 2 * 4, AvifImageOverlay.payloadSize(payload))
    }

    @Test
    fun `payloadSize 2-ref 32-bit payload is 34`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
            outputWidth = 100000,
            outputHeight = 100,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = 0, verticalOffset = 0),
                AvifImageOverlay.Reference(horizontalOffset = 0, verticalOffset = 0),
            ),
        )
        assertEquals(18 + 2 * 8, AvifImageOverlay.payloadSize(payload))
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload empty references 16-bit produces 14-byte canonical layout`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0xFFFF,
            canvasFillG = 0,
            canvasFillB = 0,
            canvasFillA = 0xFFFF,
            outputWidth = 0x1234,
            outputHeight = 0x5678,
        )
        val bytes = AvifImageOverlay.encodePayload(payload)
        assertArrayEquals(
            byteArrayOf(
                0x00, // version
                0x00, // flags = 0
                0xFF.toByte(), 0xFF.toByte(), // canvas R
                0x00, 0x00, // canvas G
                0x00, 0x00, // canvas B
                0xFF.toByte(), 0xFF.toByte(), // canvas A
                0x12, 0x34, // output_width
                0x56, 0x78, // output_height
            ),
            bytes,
        )
    }

    @Test
    fun `encodePayload 2-ref 16-bit canonical sticker overlay layout`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0,
            canvasFillG = 0,
            canvasFillB = 0,
            canvasFillA = 0xFFFF,
            outputWidth = 1920,
            outputHeight = 1080,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = 0, verticalOffset = 0),
                AvifImageOverlay.Reference(horizontalOffset = 100, verticalOffset = 50),
            ),
        )
        val bytes = AvifImageOverlay.encodePayload(payload)
        assertArrayEquals(
            byteArrayOf(
                0x00,
                0x00,
                0x00, 0x00, // R = 0
                0x00, 0x00, // G = 0
                0x00, 0x00, // B = 0
                0xFF.toByte(), 0xFF.toByte(), // A = 0xFFFF
                0x07, 0x80.toByte(), // output_width = 1920
                0x04, 0x38, // output_height = 1080
                0x00, 0x00, 0x00, 0x00, // ref[0] (h, v) = (0, 0)
                0x00, 0x64, 0x00, 0x32, // ref[1] = (100, 50)
            ),
            bytes,
        )
    }

    @Test
    fun `encodePayload 16-bit signed negative offsets emit two's complement`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
            outputWidth = 100, outputHeight = 100,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = -1, verticalOffset = -32768),
            ),
        )
        val bytes = AvifImageOverlay.encodePayload(payload)
        // Last 4 bytes are the (h, v) pair, two's-complement BE.
        // -1 = 0xFFFF; -32768 = 0x8000.
        assertEquals(0xFF, bytes[bytes.size - 4].toInt() and 0xFF)
        assertEquals(0xFF, bytes[bytes.size - 3].toInt() and 0xFF)
        assertEquals(0x80, bytes[bytes.size - 2].toInt() and 0xFF)
        assertEquals(0x00, bytes[bytes.size - 1].toInt() and 0xFF)
    }

    @Test
    fun `encodePayload above-uint16 width auto-promotes to 18-byte 32-bit base`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
            outputWidth = 100000,
            outputHeight = 100,
        )
        val bytes = AvifImageOverlay.encodePayload(payload)
        assertEquals(18, bytes.size)
        assertEquals(0x01, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `encodePayload 32-bit refs auto-promote when offset overflows int16`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
            outputWidth = 100, outputHeight = 100,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = 32768, verticalOffset = -1),
            ),
        )
        val bytes = AvifImageOverlay.encodePayload(payload)
        // 18 (base) + 8 (1 ref @ 32-bit) = 26
        assertEquals(26, bytes.size)
        assertEquals(0x01, bytes[1].toInt() and 0xFF)
        // Verify the 32-bit signed (h, v) at the end.
        // h = 32768 = 0x00008000
        // v = -1 = 0xFFFFFFFF
        val tail = bytes.copyOfRange(bytes.size - 8, bytes.size)
        assertArrayEquals(
            byteArrayOf(
                0x00, 0x00, 0x80.toByte(), 0x00,
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            ),
            tail,
        )
    }

    @Test
    fun `encodePayload force-32 flag emits 18-byte base for small canvas`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
            outputWidth = 100, outputHeight = 100,
        )
        val bytes = AvifImageOverlay.encodePayload(payload, AvifImageOverlay.FLAG_FIELD_LENGTH_32)
        assertEquals(18, bytes.size)
        assertEquals(0x01, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `encodePayload rejects 16-bit flag when ref offset overflows`() {
        val payload = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
            outputWidth = 100, outputHeight = 100,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = 32768, verticalOffset = 0),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.encodePayload(payload, flags = 0)
        }
    }

    // ------------------------------------------------------------------
    // decodePayload round-trips
    // ------------------------------------------------------------------

    @Test
    fun `decodePayload round-trips empty 16-bit payload`() {
        val original = AvifImageOverlay.Payload(
            canvasFillR = 0xABCD,
            canvasFillG = 0x1234,
            canvasFillB = 0x5678,
            canvasFillA = 0x9ABC,
            outputWidth = 1920,
            outputHeight = 1080,
        )
        val bytes = AvifImageOverlay.encodePayload(original)
        val decoded = AvifImageOverlay.decodePayload(bytes, referenceCount = 0)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload round-trips 2-ref 16-bit signed offsets`() {
        val original = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0xFFFF,
            outputWidth = 1920,
            outputHeight = 1080,
            references = listOf(
                AvifImageOverlay.Reference(horizontalOffset = -100, verticalOffset = -32768),
                AvifImageOverlay.Reference(horizontalOffset = 32767, verticalOffset = 50),
            ),
        )
        val bytes = AvifImageOverlay.encodePayload(original)
        val decoded = AvifImageOverlay.decodePayload(bytes, referenceCount = 2)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload round-trips 32-bit signed offsets`() {
        val original = AvifImageOverlay.Payload(
            canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
            outputWidth = 100000,
            outputHeight = 50000,
            references = listOf(
                AvifImageOverlay.Reference(
                    horizontalOffset = 2147483647L,
                    verticalOffset = -2147483648L,
                ),
            ),
        )
        val bytes = AvifImageOverlay.encodePayload(original)
        val decoded = AvifImageOverlay.decodePayload(bytes, referenceCount = 1)
        assertEquals(original, decoded)
    }

    // ------------------------------------------------------------------
    // decodePayload rejections
    // ------------------------------------------------------------------

    @Test
    fun `decodePayload rejects negative referenceCount`() {
        val good = AvifImageOverlay.encodePayload(
            AvifImageOverlay.Payload(
                canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
                outputWidth = 100, outputHeight = 100,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.decodePayload(good, referenceCount = -1)
        }
    }

    @Test
    fun `decodePayload rejects under-14-byte input`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.decodePayload(ByteArray(13), referenceCount = 0)
        }
    }

    @Test
    fun `decodePayload rejects wrong version byte`() {
        val good = AvifImageOverlay.encodePayload(
            AvifImageOverlay.Payload(
                canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
                outputWidth = 100, outputHeight = 100,
            ),
        )
        val bad = good.copyOf()
        bad[0] = 0x01
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.decodePayload(bad, referenceCount = 0)
        }
    }

    @Test
    fun `decodePayload rejects truncated body when reference_count is non-zero`() {
        // A 14-byte buffer with flags=0 but caller claims 2 references
        // (which would need 14 + 2*4 = 22 bytes).
        val truncated = AvifImageOverlay.encodePayload(
            AvifImageOverlay.Payload(
                canvasFillR = 0, canvasFillG = 0, canvasFillB = 0, canvasFillA = 0,
                outputWidth = 100, outputHeight = 100,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageOverlay.decodePayload(truncated, referenceCount = 2)
        }
    }

    // ------------------------------------------------------------------
    // ItemInfoEntry constants cross-check
    // ------------------------------------------------------------------

    @Test
    fun `ItemInfoEntry_ITEM_TYPE_IOVL matches AvifImageOverlay_ITEM_TYPE`() {
        assertEquals(AvifImageOverlay.ITEM_TYPE, ItemInfoEntry.ITEM_TYPE_IOVL)
        assertEquals("iovl", ItemInfoEntry.ITEM_TYPE_IOVL)
    }
}
