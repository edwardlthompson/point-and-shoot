package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [ItemInfoBox].
 *
 * Pinned facts (per ISO/IEC 14496-12 §8.11.6):
 *
 *  * `iinf` is `FullBox('iinf', version, 0)`.
 *  * v0 emits 16-bit entry_count; v1 emits 32-bit entry_count.
 *  * Payload after entry_count is the concatenation of pre-encoded
 *    `infe` boxes.
 */
class ItemInfoBoxTest {

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("iinf", ItemInfoBox.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, ItemInfoBox.SCHEMA_VERSION)
    }

    @Test
    fun `bound constant pins`() {
        assertEquals(0xFFFFL, ItemInfoBox.MAX_SMALL_ENTRY_COUNT)
        assertEquals(0xFFFFFFFFL, ItemInfoBox.MAX_LARGE_ENTRY_COUNT)
    }

    // ------------------------------------------------------------------
    // chooseVersion
    // ------------------------------------------------------------------

    @Test
    fun `chooseVersion picks 0 for entryCount in 0 to 65535 inclusive`() {
        assertEquals(0, ItemInfoBox.chooseVersion(0))
        assertEquals(0, ItemInfoBox.chooseVersion(1))
        assertEquals(0, ItemInfoBox.chooseVersion(0xFFFF))
    }

    @Test
    fun `chooseVersion picks 1 for entryCount beyond 65535`() {
        assertEquals(1, ItemInfoBox.chooseVersion(0x10000))
    }

    @Test
    fun `chooseVersion rejects negative entryCount`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoBox.chooseVersion(-1)
        }
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload v=0 with empty list emits 2 zero bytes`() {
        val payload = ItemInfoBox.encodePayload(emptyList(), version = 0)
        assertArrayEquals(byteArrayOf(0, 0), payload)
    }

    @Test
    fun `encodePayload v=1 with empty list emits 4 zero bytes`() {
        val payload = ItemInfoBox.encodePayload(emptyList(), version = 1)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), payload)
    }

    @Test
    fun `encodePayload v=0 with single infe concatenates after 16-bit count`() {
        val infe = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(itemId = 1L, itemType = "av01"),
        )
        val payload = ItemInfoBox.encodePayload(listOf(infe), version = 0)
        // entry_count(2) + infe(21) = 23 bytes
        assertEquals(2 + infe.size, payload.size)
        assertArrayEquals(byteArrayOf(0, 1), payload.copyOfRange(0, 2))
        assertArrayEquals(infe, payload.copyOfRange(2, payload.size))
    }

    @Test
    fun `encodePayload v=0 with two infe concatenates in order`() {
        val infe1 = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(itemId = 1L, itemType = "av01"),
        )
        val infe2 = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(itemId = 2L, itemType = "Exif"),
        )
        val payload = ItemInfoBox.encodePayload(listOf(infe1, infe2), version = 0)
        assertArrayEquals(byteArrayOf(0, 2), payload.copyOfRange(0, 2))
        assertArrayEquals(infe1, payload.copyOfRange(2, 2 + infe1.size))
        assertArrayEquals(infe2, payload.copyOfRange(2 + infe1.size, payload.size))
    }

    @Test
    fun `encodePayload v=1 with single infe emits 4-byte entry_count`() {
        val infe = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(itemId = 1L, itemType = "av01"),
        )
        val payload = ItemInfoBox.encodePayload(listOf(infe), version = 1)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1), payload.copyOfRange(0, 4))
        assertArrayEquals(infe, payload.copyOfRange(4, payload.size))
    }

    @Test
    fun `encodePayload rejects invalid version`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoBox.encodePayload(emptyList(), version = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoBox.encodePayload(emptyList(), version = -1)
        }
    }

    @Test
    fun `encodePayload rejects too-short infe`() {
        val tooShort = byteArrayOf(0, 0, 0, 4)
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoBox.encodePayload(listOf(tooShort), version = 0)
        }
    }

    // ------------------------------------------------------------------
    // encodeBox integration
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox empty list produces 14-byte FullBox envelope at v=0`() {
        // FullBox header(8) + version+flags(4) + entry_count(2) = 14 bytes
        val box = ItemInfoBox.encodeBox(emptyList())
        assertEquals(14, box.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 14), box.copyOfRange(0, 4))
        assertArrayEquals("iinf".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        assertEquals(0, box[8].toInt() and 0xFF) // version=0
        assertArrayEquals(byteArrayOf(0, 0), box.copyOfRange(12, 14))
    }

    @Test
    fun `encodeBox single av01 + Exif produces canonical 2-entry envelope`() {
        // Canonical AVIF still: itemId=1 av01 primary + itemId=2 Exif metadata.
        val infeAv01 = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(itemId = 1L, itemType = "av01"),
        )
        val infeExif = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(itemId = 2L, itemType = "Exif"),
        )
        val box = ItemInfoBox.encodeBox(listOf(infeAv01, infeExif))
        // Expected: FullBox header(8) + v+flags(4) + count(2) + 2*infe(21 each) = 56 bytes
        assertEquals(8 + 4 + 2 + infeAv01.size + infeExif.size, box.size)
        assertArrayEquals("iinf".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        assertEquals(0, box[8].toInt() and 0xFF) // version=0
        // entry_count = 2
        assertArrayEquals(byteArrayOf(0, 2), box.copyOfRange(12, 14))
        // First infe at offset 14
        assertArrayEquals(infeAv01, box.copyOfRange(14, 14 + infeAv01.size))
        // Second infe immediately after
        assertArrayEquals(infeExif, box.copyOfRange(14 + infeAv01.size, box.size))
    }
}
