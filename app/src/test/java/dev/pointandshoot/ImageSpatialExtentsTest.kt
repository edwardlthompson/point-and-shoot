package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [ImageSpatialExtents].
 *
 * Pinned facts (per ISO/IEC 23008-12 §6.5.3):
 *
 *  * `ispe` is a `FullBox('ispe', version = 0, flags = 0)`.
 *  * Payload is exactly 8 bytes: `image_width (uint32_be) +
 *    image_height (uint32_be)`.
 *  * Total mux-ready box is 20 bytes (8-byte header + 4-byte
 *    version+flags + 8-byte payload).
 */
class ImageSpatialExtentsTest {

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("ispe", ImageSpatialExtents.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, ImageSpatialExtents.SCHEMA_VERSION)
    }

    @Test
    fun `PAYLOAD_SIZE pin`() {
        assertEquals(8, ImageSpatialExtents.PAYLOAD_SIZE)
    }

    @Test
    fun `MAX_DIMENSION pin`() {
        assertEquals(0xFFFFFFFFL, ImageSpatialExtents.MAX_DIMENSION)
    }

    // ------------------------------------------------------------------
    // encodePayload
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload 1x1 produces canonical layout`() {
        val payload = ImageSpatialExtents.encodePayload(1L, 1L)
        assertEquals(8, payload.size)
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01),
            payload,
        )
    }

    @Test
    fun `encodePayload Legacy device main wide 4096x3072 produces canonical layout`() {
        val payload = ImageSpatialExtents.encodePayload(4096L, 3072L)
        assertEquals(8, payload.size)
        // 4096 = 0x1000; 3072 = 0xC00
        assertArrayEquals(
            byteArrayOf(
                0x00, 0x00, 0x10, 0x00,
                0x00, 0x00, 0x0C, 0x00,
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload max dimensions encode as all-FF`() {
        val payload = ImageSpatialExtents.encodePayload(
            ImageSpatialExtents.MAX_DIMENSION,
            ImageSpatialExtents.MAX_DIMENSION,
        )
        assertArrayEquals(
            byteArrayOf(
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload high-byte width encodes correctly`() {
        val payload = ImageSpatialExtents.encodePayload(0x12345678L, 0x9ABCDEF0L)
        assertArrayEquals(
            byteArrayOf(
                0x12, 0x34, 0x56, 0x78,
                0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte(),
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload rejects zero width`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSpatialExtents.encodePayload(0L, 100L)
        }
    }

    @Test
    fun `encodePayload rejects zero height`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSpatialExtents.encodePayload(100L, 0L)
        }
    }

    @Test
    fun `encodePayload rejects negative width`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSpatialExtents.encodePayload(-1L, 100L)
        }
    }

    @Test
    fun `encodePayload rejects negative height`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSpatialExtents.encodePayload(100L, -1L)
        }
    }

    @Test
    fun `encodePayload rejects width above MAX_DIMENSION`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSpatialExtents.encodePayload(0x1_0000_0000L, 100L)
        }
    }

    @Test
    fun `encodePayload rejects height above MAX_DIMENSION`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSpatialExtents.encodePayload(100L, 0x1_0000_0000L)
        }
    }

    // ------------------------------------------------------------------
    // decodePayload
    // ------------------------------------------------------------------

    @Test
    fun `decodePayload round-trips canonical AVIF still`() {
        val original = 4096L to 3072L
        val payload = ImageSpatialExtents.encodePayload(original.first, original.second)
        val decoded = ImageSpatialExtents.decodePayload(payload)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload round-trips MAX_DIMENSION boundary`() {
        val payload = ImageSpatialExtents.encodePayload(
            ImageSpatialExtents.MAX_DIMENSION,
            ImageSpatialExtents.MAX_DIMENSION,
        )
        val (w, h) = ImageSpatialExtents.decodePayload(payload)
        assertEquals(ImageSpatialExtents.MAX_DIMENSION, w)
        assertEquals(ImageSpatialExtents.MAX_DIMENSION, h)
    }

    @Test
    fun `decodePayload rejects 7-byte input`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSpatialExtents.decodePayload(ByteArray(7))
        }
    }

    @Test
    fun `decodePayload rejects 9-byte input`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSpatialExtents.decodePayload(ByteArray(9))
        }
    }

    // ------------------------------------------------------------------
    // encodeBox integration
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox 4096x3072 produces 20-byte canonical envelope`() {
        val box = ImageSpatialExtents.encodeBox(4096L, 3072L)
        // header(8) + version+flags(4) + payload(8) = 20 bytes
        assertEquals(20, box.size)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x14), box.copyOfRange(0, 4))
        assertArrayEquals("ispe".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), box.copyOfRange(8, 12))
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x10, 0x00, 0x00, 0x00, 0x0C, 0x00),
            box.copyOfRange(12, 20),
        )
    }

    @Test
    fun `encodeBox int overload matches long overload`() {
        val viaInt = ImageSpatialExtents.encodeBox(4096, 3072)
        val viaLong = ImageSpatialExtents.encodeBox(4096L, 3072L)
        assertArrayEquals(viaLong, viaInt)
    }

    @Test
    fun `encodeBox int overload rejects zero width`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSpatialExtents.encodeBox(0, 100)
        }
    }

    @Test
    fun `encodeBox int overload rejects zero height`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSpatialExtents.encodeBox(100, 0)
        }
    }
}
