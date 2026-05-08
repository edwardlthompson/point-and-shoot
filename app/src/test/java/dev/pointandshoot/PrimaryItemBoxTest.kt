package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [PrimaryItemBox].
 *
 * Pinned facts (per ISO/IEC 14496-12 §8.11.4):
 *
 *  * `pitm` is `FullBox('pitm', version, 0)`.
 *  * `version=0` → 2-byte big-endian item_ID (max 65535).
 *  * `version=1` → 4-byte big-endian item_ID (max 4_294_967_295).
 *  * `flags` is always `0`.
 */
class PrimaryItemBoxTest {

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("pitm", PrimaryItemBox.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, PrimaryItemBox.SCHEMA_VERSION)
    }

    @Test
    fun `MAX_ITEM_ID pins`() {
        assertEquals(0xFFFFL, PrimaryItemBox.MAX_SMALL_ITEM_ID)
        assertEquals(0xFFFFFFFFL, PrimaryItemBox.MAX_LARGE_ITEM_ID)
    }

    @Test
    fun `PAYLOAD_SIZE pins`() {
        assertEquals(2, PrimaryItemBox.PAYLOAD_SIZE_V0)
        assertEquals(4, PrimaryItemBox.PAYLOAD_SIZE_V1)
    }

    // ------------------------------------------------------------------
    // chooseVersion
    // ------------------------------------------------------------------

    @Test
    fun `chooseVersion picks 0 for itemId in 0 to 65535 inclusive`() {
        assertEquals(0, PrimaryItemBox.chooseVersion(0L))
        assertEquals(0, PrimaryItemBox.chooseVersion(1L))
        assertEquals(0, PrimaryItemBox.chooseVersion(255L))
        assertEquals(0, PrimaryItemBox.chooseVersion(PrimaryItemBox.MAX_SMALL_ITEM_ID))
    }

    @Test
    fun `chooseVersion picks 1 for itemId beyond 65535`() {
        assertEquals(1, PrimaryItemBox.chooseVersion(65536L))
        assertEquals(1, PrimaryItemBox.chooseVersion(100_000L))
        assertEquals(1, PrimaryItemBox.chooseVersion(PrimaryItemBox.MAX_LARGE_ITEM_ID))
    }

    @Test
    fun `chooseVersion rejects negative itemId`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.chooseVersion(-1L)
        }
    }

    @Test
    fun `chooseVersion rejects itemId beyond MAX_LARGE_ITEM_ID`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.chooseVersion(PrimaryItemBox.MAX_LARGE_ITEM_ID + 1)
        }
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload v=0 itemId=1 produces 2 big-endian bytes`() {
        val payload = PrimaryItemBox.encodePayload(itemId = 1L, version = 0)
        assertArrayEquals(byteArrayOf(0, 1), payload)
    }

    @Test
    fun `encodePayload v=0 itemId=MAX_SMALL produces 0xFF 0xFF`() {
        val payload = PrimaryItemBox.encodePayload(
            itemId = PrimaryItemBox.MAX_SMALL_ITEM_ID,
            version = 0,
        )
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), payload)
    }

    @Test
    fun `encodePayload v=0 high-byte itemId encodes in big-endian order`() {
        val payload = PrimaryItemBox.encodePayload(itemId = 0x1234L, version = 0)
        assertArrayEquals(byteArrayOf(0x12, 0x34), payload)
    }

    @Test
    fun `encodePayload v=0 rejects itemId beyond MAX_SMALL`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.encodePayload(itemId = 65536L, version = 0)
        }
    }

    @Test
    fun `encodePayload v=1 itemId=1 produces 4 big-endian bytes`() {
        val payload = PrimaryItemBox.encodePayload(itemId = 1L, version = 1)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1), payload)
    }

    @Test
    fun `encodePayload v=1 itemId=0x12345678 encodes in big-endian order`() {
        val payload = PrimaryItemBox.encodePayload(itemId = 0x12345678L, version = 1)
        assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78), payload)
    }

    @Test
    fun `encodePayload v=1 itemId=MAX_LARGE produces 0xFF FF FF FF`() {
        val payload = PrimaryItemBox.encodePayload(
            itemId = PrimaryItemBox.MAX_LARGE_ITEM_ID,
            version = 1,
        )
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            payload,
        )
    }

    @Test
    fun `encodePayload v=1 rejects itemId beyond MAX_LARGE`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.encodePayload(itemId = PrimaryItemBox.MAX_LARGE_ITEM_ID + 1, version = 1)
        }
    }

    @Test
    fun `encodePayload rejects negative itemId`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.encodePayload(itemId = -1L, version = 0)
        }
    }

    @Test
    fun `encodePayload rejects invalid version`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.encodePayload(itemId = 1L, version = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.encodePayload(itemId = 1L, version = -1)
        }
    }

    // ------------------------------------------------------------------
    // decodePayload
    // ------------------------------------------------------------------

    @Test
    fun `decodePayload v=0 round-trips arbitrary itemIds`() {
        for (id in listOf(0L, 1L, 255L, 256L, 0x1234L, 0xFFFEL, PrimaryItemBox.MAX_SMALL_ITEM_ID)) {
            val payload = PrimaryItemBox.encodePayload(id, 0)
            assertEquals("v=0 round-trip for $id", id, PrimaryItemBox.decodePayload(payload, 0))
        }
    }

    @Test
    fun `decodePayload v=1 round-trips arbitrary itemIds`() {
        for (id in listOf(
            0L,
            1L,
            65535L,
            65536L,
            0x12345678L,
            0xFFFFFFFEL,
            PrimaryItemBox.MAX_LARGE_ITEM_ID,
        )) {
            val payload = PrimaryItemBox.encodePayload(id, 1)
            assertEquals("v=1 round-trip for $id", id, PrimaryItemBox.decodePayload(payload, 1))
        }
    }

    @Test
    fun `decodePayload v=0 throws on wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.decodePayload(byteArrayOf(0), version = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.decodePayload(byteArrayOf(0, 0, 0), version = 0)
        }
    }

    @Test
    fun `decodePayload v=1 throws on wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.decodePayload(byteArrayOf(0, 0, 0), version = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.decodePayload(byteArrayOf(0, 0, 0, 0, 0), version = 1)
        }
    }

    @Test
    fun `decodePayload rejects invalid version`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrimaryItemBox.decodePayload(byteArrayOf(0, 1), version = 2)
        }
    }

    // ------------------------------------------------------------------
    // encodeBox integration with IsobmffBox
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox itemId=1 produces canonical 14-byte FullBox envelope`() {
        // size(4) + type(4) + version+flags(4) + payload(2) = 14 bytes
        val box = PrimaryItemBox.encodeBox(itemId = 1L)
        assertEquals(14, box.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 14), box.copyOfRange(0, 4))
        assertArrayEquals("pitm".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        // version=0, flags=0
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), box.copyOfRange(8, 12))
        // itemId=1 in 2 BE bytes
        assertArrayEquals(byteArrayOf(0, 1), box.copyOfRange(12, 14))
    }

    @Test
    fun `encodeBox itemId=100000 auto-promotes to version=1 and produces 16-byte envelope`() {
        // size(4) + type(4) + version+flags(4) + payload(4) = 16 bytes
        val box = PrimaryItemBox.encodeBox(itemId = 100_000L)
        assertEquals(16, box.size)
        assertEquals(1, box[8].toInt() and 0xFF) // version byte
        assertEquals(0, box[9].toInt() and 0xFF) // flags hi
        assertEquals(0, box[10].toInt() and 0xFF)
        assertEquals(0, box[11].toInt() and 0xFF) // flags lo
        // itemId=100000 = 0x000186A0 in 4 BE bytes
        assertArrayEquals(
            byteArrayOf(0x00, 0x01, 0x86.toByte(), 0xA0.toByte()),
            box.copyOfRange(12, 16),
        )
    }

    @Test
    fun `encodeBox itemId=MAX_LARGE produces 16-byte envelope with all-FF payload`() {
        val box = PrimaryItemBox.encodeBox(itemId = PrimaryItemBox.MAX_LARGE_ITEM_ID)
        assertEquals(16, box.size)
        assertEquals(1, box[8].toInt() and 0xFF) // version=1
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            box.copyOfRange(12, 16),
        )
    }

    @Test
    fun `encodeBox itemId=MAX_SMALL stays at version=0 (boundary)`() {
        val box = PrimaryItemBox.encodeBox(itemId = PrimaryItemBox.MAX_SMALL_ITEM_ID)
        assertEquals(14, box.size)
        assertEquals(0, box[8].toInt() and 0xFF) // version=0
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), box.copyOfRange(12, 14))
    }
}
