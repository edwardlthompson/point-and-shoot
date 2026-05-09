package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [AvifImageGrid].
 *
 * The grid derived-image item is the AVIF / HEIF mechanism for
 * stitching `rows × columns` equally-sized tile image items into
 * one logical canvas (ISO/IEC 23008-12 §6.6.2.3). These tests
 * verify:
 *
 *  * Every constant pin matches the spec.
 *  * `Payload` validation rejects out-of-range row / column /
 *    canvas counts.
 *  * `chooseFlags` picks 0 (16-bit fields) for typical
 *    sub-65535-px canvases and `FLAG_FIELD_LENGTH_32` only when
 *    a dimension overflows uint16.
 *  * `encodePayload` produces the byte-exact wire form for
 *    canonical OnePlus-13-class 8 K stills (`(2×2)` grid,
 *    8192×6144) and degenerate `(1×1)` cases.
 *  * `decodePayload` round-trips encoded payloads and rejects
 *    malformed input (short buffer, wrong version byte, missing
 *    32-bit tail).
 */
class AvifImageGridTest {

    // ------------------------------------------------------------------
    // Constant pins
    // ------------------------------------------------------------------

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, AvifImageGrid.SCHEMA_VERSION)
    }

    @Test
    fun `ITEM_TYPE pin matches spec`() {
        assertEquals("grid", AvifImageGrid.ITEM_TYPE)
    }

    @Test
    fun `VERSION pin matches spec`() {
        assertEquals(0, AvifImageGrid.VERSION)
    }

    @Test
    fun `FLAG_FIELD_LENGTH_32 pin is bit zero`() {
        assertEquals(0x01, AvifImageGrid.FLAG_FIELD_LENGTH_32)
    }

    @Test
    fun `PAYLOAD_SIZE_16 pin is 8 bytes`() {
        assertEquals(8, AvifImageGrid.PAYLOAD_SIZE_16)
    }

    @Test
    fun `PAYLOAD_SIZE_32 pin is 12 bytes`() {
        assertEquals(12, AvifImageGrid.PAYLOAD_SIZE_32)
    }

    @Test
    fun `MAX_ROWS pin is 256`() {
        assertEquals(256, AvifImageGrid.MAX_ROWS)
    }

    @Test
    fun `MAX_COLUMNS pin is 256`() {
        assertEquals(256, AvifImageGrid.MAX_COLUMNS)
    }

    @Test
    fun `MAX_OUTPUT_DIMENSION_16 pin is 65535`() {
        assertEquals(65535L, AvifImageGrid.MAX_OUTPUT_DIMENSION_16)
    }

    @Test
    fun `MAX_OUTPUT_DIMENSION_32 pin is 0xFFFFFFFF`() {
        assertEquals(0xFFFFFFFFL, AvifImageGrid.MAX_OUTPUT_DIMENSION_32)
    }

    // ------------------------------------------------------------------
    // Payload validation
    // ------------------------------------------------------------------

    @Test
    fun `Payload rejects zero rows`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.Payload(rows = 0, columns = 1, outputWidth = 100, outputHeight = 100)
        }
    }

    @Test
    fun `Payload rejects above-256 rows`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.Payload(rows = 257, columns = 1, outputWidth = 100, outputHeight = 100)
        }
    }

    @Test
    fun `Payload rejects zero columns`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.Payload(rows = 1, columns = 0, outputWidth = 100, outputHeight = 100)
        }
    }

    @Test
    fun `Payload rejects above-256 columns`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.Payload(rows = 1, columns = 257, outputWidth = 100, outputHeight = 100)
        }
    }

    @Test
    fun `Payload rejects zero outputWidth`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.Payload(rows = 1, columns = 1, outputWidth = 0, outputHeight = 100)
        }
    }

    @Test
    fun `Payload rejects negative outputHeight`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.Payload(rows = 1, columns = 1, outputWidth = 100, outputHeight = -1)
        }
    }

    @Test
    fun `Payload rejects above-uint32 outputWidth`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.Payload(
                rows = 1,
                columns = 1,
                outputWidth = 0x1_00000000L,
                outputHeight = 100,
            )
        }
    }

    @Test
    fun `Payload accepts canonical 2x2 8K grid`() {
        val payload = AvifImageGrid.Payload(
            rows = 2,
            columns = 2,
            outputWidth = 8192,
            outputHeight = 6144,
        )
        assertEquals(2, payload.rows)
        assertEquals(2, payload.columns)
        assertEquals(8192L, payload.outputWidth)
        assertEquals(6144L, payload.outputHeight)
    }

    @Test
    fun `Payload accepts maxed-out 256x256 grid`() {
        val payload = AvifImageGrid.Payload(
            rows = 256,
            columns = 256,
            outputWidth = 1,
            outputHeight = 1,
        )
        assertEquals(256, payload.rows)
        assertEquals(256, payload.columns)
    }

    // ------------------------------------------------------------------
    // chooseFlags
    // ------------------------------------------------------------------

    @Test
    fun `chooseFlags picks 0 for sub-65535 dims`() {
        val payload = AvifImageGrid.Payload(rows = 2, columns = 2, outputWidth = 8192, outputHeight = 6144)
        assertEquals(0, AvifImageGrid.chooseFlags(payload))
    }

    @Test
    fun `chooseFlags picks 0 at 65535 boundary`() {
        val payload = AvifImageGrid.Payload(
            rows = 1,
            columns = 1,
            outputWidth = AvifImageGrid.MAX_OUTPUT_DIMENSION_16,
            outputHeight = AvifImageGrid.MAX_OUTPUT_DIMENSION_16,
        )
        assertEquals(0, AvifImageGrid.chooseFlags(payload))
    }

    @Test
    fun `chooseFlags picks FLAG_FIELD_LENGTH_32 above-uint16 width`() {
        val payload = AvifImageGrid.Payload(
            rows = 1,
            columns = 1,
            outputWidth = 65536,
            outputHeight = 100,
        )
        assertEquals(AvifImageGrid.FLAG_FIELD_LENGTH_32, AvifImageGrid.chooseFlags(payload))
    }

    @Test
    fun `chooseFlags picks FLAG_FIELD_LENGTH_32 above-uint16 height`() {
        val payload = AvifImageGrid.Payload(
            rows = 1,
            columns = 1,
            outputWidth = 100,
            outputHeight = 65536,
        )
        assertEquals(AvifImageGrid.FLAG_FIELD_LENGTH_32, AvifImageGrid.chooseFlags(payload))
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload 1x1 1x1 produces canonical 8-byte payload`() {
        val payload = AvifImageGrid.Payload(rows = 1, columns = 1, outputWidth = 1, outputHeight = 1)
        val bytes = AvifImageGrid.encodePayload(payload)
        assertArrayEquals(
            byteArrayOf(
                0x00, // version = 0
                0x00, // flags = 0 (16-bit field length)
                0x00, // rows_minus_one = 0
                0x00, // columns_minus_one = 0
                0x00, 0x01, // output_width = 1
                0x00, 0x01, // output_height = 1
            ),
            bytes,
        )
    }

    @Test
    fun `encodePayload 2x2 8192x6144 OnePlus-13-class produces canonical 8-byte payload`() {
        val payload = AvifImageGrid.Payload(
            rows = 2,
            columns = 2,
            outputWidth = 8192,
            outputHeight = 6144,
        )
        val bytes = AvifImageGrid.encodePayload(payload)
        assertArrayEquals(
            byteArrayOf(
                0x00, // version = 0
                0x00, // flags = 0 (16-bit field length)
                0x01, // rows_minus_one = 1
                0x01, // columns_minus_one = 1
                0x20, 0x00, // output_width = 8192
                0x18, 0x00, // output_height = 6144
            ),
            bytes,
        )
    }

    @Test
    fun `encodePayload 256x256 1x1 max grid produces canonical 8-byte payload`() {
        val payload = AvifImageGrid.Payload(
            rows = 256,
            columns = 256,
            outputWidth = 1,
            outputHeight = 1,
        )
        val bytes = AvifImageGrid.encodePayload(payload)
        assertArrayEquals(
            byteArrayOf(
                0x00,
                0x00,
                0xFF.toByte(), // rows_minus_one = 255
                0xFF.toByte(), // columns_minus_one = 255
                0x00, 0x01,
                0x00, 0x01,
            ),
            bytes,
        )
    }

    @Test
    fun `encodePayload 1x1 boundary 65535x65535 produces canonical 8-byte payload`() {
        val payload = AvifImageGrid.Payload(
            rows = 1,
            columns = 1,
            outputWidth = 65535,
            outputHeight = 65535,
        )
        val bytes = AvifImageGrid.encodePayload(payload)
        assertArrayEquals(
            byteArrayOf(
                0x00,
                0x00,
                0x00,
                0x00,
                0xFF.toByte(), 0xFF.toByte(),
                0xFF.toByte(), 0xFF.toByte(),
            ),
            bytes,
        )
    }

    @Test
    fun `encodePayload above-uint16 width auto-promotes to 12-byte 32-bit field`() {
        val payload = AvifImageGrid.Payload(
            rows = 4,
            columns = 4,
            outputWidth = 100000,
            outputHeight = 50000,
        )
        val bytes = AvifImageGrid.encodePayload(payload)
        assertEquals(12, bytes.size)
        assertArrayEquals(
            byteArrayOf(
                0x00, // version
                0x01, // flags = FLAG_FIELD_LENGTH_32
                0x03, // rows_minus_one = 3
                0x03, // columns_minus_one = 3
                0x00, 0x01, 0x86.toByte(), 0xA0.toByte(), // output_width = 100000
                0x00, 0x00, 0xC3.toByte(), 0x50, // output_height = 50000
            ),
            bytes,
        )
    }

    @Test
    fun `encodePayload max-uint32 dims produces 12-byte all-FF tail`() {
        val payload = AvifImageGrid.Payload(
            rows = 1,
            columns = 1,
            outputWidth = AvifImageGrid.MAX_OUTPUT_DIMENSION_32,
            outputHeight = AvifImageGrid.MAX_OUTPUT_DIMENSION_32,
        )
        val bytes = AvifImageGrid.encodePayload(payload)
        assertEquals(12, bytes.size)
        assertEquals(0x01, bytes[1].toInt() and 0xFF) // flags = FLAG_FIELD_LENGTH_32
        for (i in 4..11) {
            assertEquals("byte $i must be 0xFF", 0xFF, bytes[i].toInt() and 0xFF)
        }
    }

    @Test
    fun `encodePayload rejects 16-bit flag with above-uint16 dim`() {
        val payload = AvifImageGrid.Payload(
            rows = 1,
            columns = 1,
            outputWidth = 65536,
            outputHeight = 100,
        )
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.encodePayload(payload, flags = 0)
        }
    }

    @Test
    fun `encodePayload force-32 flag emits 12-byte payload even for small dims`() {
        val payload = AvifImageGrid.Payload(rows = 1, columns = 1, outputWidth = 100, outputHeight = 100)
        val bytes = AvifImageGrid.encodePayload(payload, flags = AvifImageGrid.FLAG_FIELD_LENGTH_32)
        assertEquals(12, bytes.size)
        assertEquals(0x01, bytes[1].toInt() and 0xFF)
        // output_width = 100 (32-bit BE)
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x00, 0x64),
            bytes.copyOfRange(4, 8),
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x00, 0x64),
            bytes.copyOfRange(8, 12),
        )
    }

    // ------------------------------------------------------------------
    // decodePayload round-trips
    // ------------------------------------------------------------------

    @Test
    fun `decodePayload round-trips canonical 2x2 8K grid`() {
        val original = AvifImageGrid.Payload(
            rows = 2,
            columns = 2,
            outputWidth = 8192,
            outputHeight = 6144,
        )
        val bytes = AvifImageGrid.encodePayload(original)
        val decoded = AvifImageGrid.decodePayload(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload round-trips 1x1 degenerate grid`() {
        val original = AvifImageGrid.Payload(
            rows = 1,
            columns = 1,
            outputWidth = 1,
            outputHeight = 1,
        )
        val bytes = AvifImageGrid.encodePayload(original)
        val decoded = AvifImageGrid.decodePayload(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload round-trips 256x256 max grid`() {
        val original = AvifImageGrid.Payload(
            rows = 256,
            columns = 256,
            outputWidth = 65535,
            outputHeight = 65535,
        )
        val bytes = AvifImageGrid.encodePayload(original)
        val decoded = AvifImageGrid.decodePayload(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload round-trips 32-bit-field grid`() {
        val original = AvifImageGrid.Payload(
            rows = 16,
            columns = 8,
            outputWidth = 100000,
            outputHeight = 50000,
        )
        val bytes = AvifImageGrid.encodePayload(original)
        val decoded = AvifImageGrid.decodePayload(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload round-trips max-uint32 dims grid`() {
        val original = AvifImageGrid.Payload(
            rows = 1,
            columns = 1,
            outputWidth = AvifImageGrid.MAX_OUTPUT_DIMENSION_32,
            outputHeight = AvifImageGrid.MAX_OUTPUT_DIMENSION_32,
        )
        val bytes = AvifImageGrid.encodePayload(original)
        val decoded = AvifImageGrid.decodePayload(bytes)
        assertEquals(original, decoded)
    }

    // ------------------------------------------------------------------
    // decodePayload rejections
    // ------------------------------------------------------------------

    @Test
    fun `decodePayload rejects under-8-byte input`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.decodePayload(ByteArray(7))
        }
    }

    @Test
    fun `decodePayload rejects wrong version byte`() {
        val good = AvifImageGrid.encodePayload(
            AvifImageGrid.Payload(rows = 1, columns = 1, outputWidth = 100, outputHeight = 100),
        )
        val bad = good.copyOf()
        bad[0] = 0x01
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.decodePayload(bad)
        }
    }

    @Test
    fun `decodePayload rejects 32-bit flag with under-12-byte buffer`() {
        // 8-byte buffer that claims FLAG_FIELD_LENGTH_32.
        val malformed = byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertThrows(IllegalArgumentException::class.java) {
            AvifImageGrid.decodePayload(malformed)
        }
    }

    @Test
    fun `decodePayload tolerates trailing bytes (idempotent on 8-byte prefix)`() {
        val good = AvifImageGrid.encodePayload(
            AvifImageGrid.Payload(rows = 1, columns = 1, outputWidth = 100, outputHeight = 100),
        )
        val padded = good + byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        // Decode reads only the canonical 8-byte prefix when flags = 0.
        val decoded = AvifImageGrid.decodePayload(padded)
        assertEquals(1, decoded.rows)
        assertEquals(1, decoded.columns)
        assertEquals(100L, decoded.outputWidth)
        assertEquals(100L, decoded.outputHeight)
    }

    // ------------------------------------------------------------------
    // ItemInfoEntry.ITEM_TYPE_GRID / IOVL / IDEN constants
    // ------------------------------------------------------------------

    @Test
    fun `ItemInfoEntry exposes ITEM_TYPE_GRID matching AvifImageGrid_ITEM_TYPE`() {
        assertEquals(AvifImageGrid.ITEM_TYPE, ItemInfoEntry.ITEM_TYPE_GRID)
        assertEquals("grid", ItemInfoEntry.ITEM_TYPE_GRID)
    }

    @Test
    fun `ItemInfoEntry exposes ITEM_TYPE_IOVL constant pin`() {
        assertEquals("iovl", ItemInfoEntry.ITEM_TYPE_IOVL)
    }

    @Test
    fun `ItemInfoEntry exposes ITEM_TYPE_IDEN constant pin`() {
        assertEquals("iden", ItemInfoEntry.ITEM_TYPE_IDEN)
    }
}
