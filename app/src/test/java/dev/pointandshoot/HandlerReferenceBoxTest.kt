package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [HandlerReferenceBox].
 *
 * Pinned facts (per ISO/IEC 14496-12 §8.4.3):
 *
 *  * `hdlr` is always `FullBox('hdlr', version=0, flags=0)`.
 *  * Payload starts with `pre_defined = 0` (4 zero BE bytes).
 *  * `handler_type` is 4 ASCII bytes.
 *  * `reserved[3]` is 12 zero bytes.
 *  * `name` is NUL-terminated UTF-8.
 */
class HandlerReferenceBoxTest {

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("hdlr", HandlerReferenceBox.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, HandlerReferenceBox.SCHEMA_VERSION)
    }

    @Test
    fun `handler type constant pins`() {
        assertEquals("pict", HandlerReferenceBox.HANDLER_TYPE_PICT)
        assertEquals("vide", HandlerReferenceBox.HANDLER_TYPE_VIDE)
        assertEquals("soun", HandlerReferenceBox.HANDLER_TYPE_SOUN)
        assertEquals("meta", HandlerReferenceBox.HANDLER_TYPE_META)
        assertEquals("auxv", HandlerReferenceBox.HANDLER_TYPE_AUXV)
    }

    @Test
    fun `FIXED_PAYLOAD_PREFIX pin`() {
        assertEquals(20, HandlerReferenceBox.FIXED_PAYLOAD_PREFIX)
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload pict with empty name produces 21-byte payload`() {
        val payload = HandlerReferenceBox.encodePayload(handlerType = "pict")
        // 20-byte fixed prefix + 1-byte name NUL terminator = 21 bytes
        assertEquals(21, payload.size)
        // pre_defined = 0
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), payload.copyOfRange(0, 4))
        // handler_type = "pict"
        assertArrayEquals(
            "pict".toByteArray(Charsets.US_ASCII),
            payload.copyOfRange(4, 8),
        )
        // reserved[3] = 0
        assertArrayEquals(ByteArray(12), payload.copyOfRange(8, 20))
        // name = "" + 0
        assertEquals(0, payload[20].toInt())
    }

    @Test
    fun `encodePayload vide with name 'VideoHandler' produces documented byte layout`() {
        val payload = HandlerReferenceBox.encodePayload(
            handlerType = "vide",
            name = "VideoHandler",
        )
        // 20 prefix + 12 name bytes + 1 NUL = 33 bytes
        assertEquals(33, payload.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), payload.copyOfRange(0, 4))
        assertArrayEquals(
            "vide".toByteArray(Charsets.US_ASCII),
            payload.copyOfRange(4, 8),
        )
        assertArrayEquals(ByteArray(12), payload.copyOfRange(8, 20))
        assertArrayEquals(
            "VideoHandler".toByteArray(Charsets.UTF_8),
            payload.copyOfRange(20, 32),
        )
        assertEquals(0, payload[32].toInt())
    }

    @Test
    fun `encodePayload encodes UTF-8 multi-byte name correctly`() {
        val payload = HandlerReferenceBox.encodePayload(
            handlerType = "pict",
            name = "Photo",
        )
        assertEquals(20 + 5 + 1, payload.size)
        assertArrayEquals(
            "Photo".toByteArray(Charsets.UTF_8),
            payload.copyOfRange(20, 25),
        )
        assertEquals(0, payload[25].toInt())
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload rejects non-4-char handlerType`() {
        assertThrows(IllegalArgumentException::class.java) {
            HandlerReferenceBox.encodePayload(handlerType = "abc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HandlerReferenceBox.encodePayload(handlerType = "abcde")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HandlerReferenceBox.encodePayload(handlerType = "")
        }
    }

    @Test
    fun `encodePayload rejects non-printable-ASCII handlerType`() {
        assertThrows(IllegalArgumentException::class.java) {
            HandlerReferenceBox.encodePayload(handlerType = "pi\u0001t")
        }
    }

    @Test
    fun `encodePayload rejects NUL in name`() {
        assertThrows(IllegalArgumentException::class.java) {
            HandlerReferenceBox.encodePayload(handlerType = "pict", name = "Photo\u0000Library")
        }
    }

    // ------------------------------------------------------------------
    // encodeBox integration
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox pict empty name produces 33-byte FullBox envelope`() {
        // FullBox header(8) + version+flags(4) + payload(21) = 33 bytes
        val box = HandlerReferenceBox.encodeBox(handlerType = "pict")
        assertEquals(33, box.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 33), box.copyOfRange(0, 4))
        assertArrayEquals("hdlr".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        // version=0, flags=0
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), box.copyOfRange(8, 12))
        // pre_defined=0
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), box.copyOfRange(12, 16))
        // handler_type=pict at offset 16..20
        assertArrayEquals(
            "pict".toByteArray(Charsets.US_ASCII),
            box.copyOfRange(16, 20),
        )
        // reserved[3]=0 at offset 20..32
        assertArrayEquals(ByteArray(12), box.copyOfRange(20, 32))
        // name NUL at offset 32
        assertEquals(0, box[32].toInt())
    }

    @Test
    fun `encodePictBox is shorthand for encodeBox with handler_type=pict`() {
        val viaPict = HandlerReferenceBox.encodePictBox()
        val viaGeneric = HandlerReferenceBox.encodeBox(handlerType = "pict")
        assertArrayEquals(viaGeneric, viaPict)
    }

    @Test
    fun `encodePictBox with name encodes name in payload`() {
        val box = HandlerReferenceBox.encodePictBox(name = "Photo")
        // header(8) + v+flags(4) + 20 prefix + 5 name + 1 NUL = 38 bytes
        assertEquals(38, box.size)
        assertArrayEquals(
            "Photo".toByteArray(Charsets.UTF_8),
            box.copyOfRange(32, 37),
        )
        assertEquals(0, box[37].toInt())
    }

    @Test
    fun `encodeBox auxv produces aux-handler envelope`() {
        val box = HandlerReferenceBox.encodeBox(handlerType = "auxv")
        assertArrayEquals(
            "auxv".toByteArray(Charsets.US_ASCII),
            box.copyOfRange(16, 20),
        )
    }
}
