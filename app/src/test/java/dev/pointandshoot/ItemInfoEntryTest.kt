package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [ItemInfoEntry].
 *
 * Pinned facts (per ISO/IEC 14496-12 §8.11.6 v2/v3):
 *
 *  * v2 emits 16-bit item_ID; v3 emits 32-bit item_ID.
 *  * itemProtectionIndex is 16-bit unsigned.
 *  * itemType is 4 ASCII bytes.
 *  * itemName is NUL-terminated UTF-8.
 *  * For itemType == "mime" we emit contentType (and optional
 *    contentEncoding) NUL-terminated.
 *  * For itemType == "uri " we emit itemUriType NUL-terminated.
 *  * Hidden flag lives in FullBox flags bit 0.
 */
class ItemInfoEntryTest {

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("infe", ItemInfoEntry.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, ItemInfoEntry.SCHEMA_VERSION)
    }

    @Test
    fun `item type constant pins`() {
        assertEquals("av01", ItemInfoEntry.ITEM_TYPE_AV01)
        assertEquals("Exif", ItemInfoEntry.ITEM_TYPE_EXIF)
        assertEquals("mime", ItemInfoEntry.ITEM_TYPE_MIME)
        assertEquals("uri ", ItemInfoEntry.ITEM_TYPE_URI)
        assertEquals("avc1", ItemInfoEntry.ITEM_TYPE_AVC1)
        assertEquals("hvc1", ItemInfoEntry.ITEM_TYPE_HVC1)
    }

    @Test
    fun `bound and flag constant pins`() {
        assertEquals(0xFFFFL, ItemInfoEntry.MAX_SMALL_ITEM_ID)
        assertEquals(0xFFFFFFFFL, ItemInfoEntry.MAX_LARGE_ITEM_ID)
        assertEquals(0xFFFF, ItemInfoEntry.MAX_ITEM_PROTECTION_INDEX)
        assertEquals(0x000001, ItemInfoEntry.FLAG_ITEM_HIDDEN)
    }

    // ------------------------------------------------------------------
    // Entry constructor validation
    // ------------------------------------------------------------------

    @Test
    fun `Entry rejects negative itemId`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(itemId = -1L, itemType = "av01")
        }
    }

    @Test
    fun `Entry rejects itemId beyond MAX_LARGE`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(
                itemId = ItemInfoEntry.MAX_LARGE_ITEM_ID + 1,
                itemType = "av01",
            )
        }
    }

    @Test
    fun `Entry rejects itemProtectionIndex out of unsigned 16-bit range`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(itemId = 1L, itemType = "av01", itemProtectionIndex = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(itemId = 1L, itemType = "av01", itemProtectionIndex = 0x10000)
        }
    }

    @Test
    fun `Entry rejects non-4-char itemType`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(itemId = 1L, itemType = "abc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(itemId = 1L, itemType = "abcde")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(itemId = 1L, itemType = "")
        }
    }

    @Test
    fun `Entry rejects non-printable-ASCII itemType`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(itemId = 1L, itemType = "av\u0001x")
        }
    }

    @Test
    fun `Entry rejects NUL in string fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(itemId = 1L, itemType = "av01", itemName = "primary\u0000image")
        }
    }

    @Test
    fun `Entry rejects contentType when itemType is not mime`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(
                itemId = 1L,
                itemType = "av01",
                contentType = "image/avif",
            )
        }
    }

    @Test
    fun `Entry rejects itemUriType when itemType is not uri`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.Entry(
                itemId = 1L,
                itemType = "av01",
                itemUriType = "https://example.com",
            )
        }
    }

    @Test
    fun `Entry accepts boundary itemIds`() {
        ItemInfoEntry.Entry(itemId = 0L, itemType = "av01")
        ItemInfoEntry.Entry(itemId = ItemInfoEntry.MAX_LARGE_ITEM_ID, itemType = "av01")
    }

    // ------------------------------------------------------------------
    // chooseVersion
    // ------------------------------------------------------------------

    @Test
    fun `chooseVersion picks 2 for itemId in 0 to 65535 inclusive`() {
        assertEquals(2, ItemInfoEntry.chooseVersion(0L))
        assertEquals(2, ItemInfoEntry.chooseVersion(1L))
        assertEquals(2, ItemInfoEntry.chooseVersion(ItemInfoEntry.MAX_SMALL_ITEM_ID))
    }

    @Test
    fun `chooseVersion picks 3 for itemId beyond 65535`() {
        assertEquals(3, ItemInfoEntry.chooseVersion(65536L))
        assertEquals(3, ItemInfoEntry.chooseVersion(ItemInfoEntry.MAX_LARGE_ITEM_ID))
    }

    // ------------------------------------------------------------------
    // computeFlags
    // ------------------------------------------------------------------

    @Test
    fun `computeFlags is 0 by default and FLAG_ITEM_HIDDEN when hidden`() {
        val plain = ItemInfoEntry.Entry(itemId = 1L, itemType = "av01")
        assertEquals(0, ItemInfoEntry.computeFlags(plain))
        val hidden = ItemInfoEntry.Entry(itemId = 1L, itemType = "av01", hidden = true)
        assertEquals(ItemInfoEntry.FLAG_ITEM_HIDDEN, ItemInfoEntry.computeFlags(hidden))
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload v=2 av01 with empty itemName produces documented byte layout`() {
        // canonical AVIF primary image item, itemId=1, no name, no protection.
        val entry = ItemInfoEntry.Entry(itemId = 1L, itemType = "av01")
        val payload = ItemInfoEntry.encodePayload(entry, version = 2)
        // item_ID(2) + item_protection_index(2) + item_type(4) + item_name(1 NUL)
        //   = [00 01] + [00 00] + ['a','v','0','1'] + [00] = 9 bytes
        assertArrayEquals(
            byteArrayOf(
                0x00, 0x01,
                0x00, 0x00,
                'a'.code.toByte(), 'v'.code.toByte(), '0'.code.toByte(), '1'.code.toByte(),
                0x00,
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload v=2 av01 with itemName encodes NUL-terminated UTF-8`() {
        val entry = ItemInfoEntry.Entry(
            itemId = 5L,
            itemType = "av01",
            itemName = "primary",
        )
        val payload = ItemInfoEntry.encodePayload(entry, version = 2)
        // 2 + 2 + 4 + 7 + 1 = 16 bytes
        assertEquals(16, payload.size)
        assertArrayEquals(byteArrayOf(0x00, 0x05), payload.copyOfRange(0, 2))
        assertArrayEquals(byteArrayOf(0x00, 0x00), payload.copyOfRange(2, 4))
        assertArrayEquals(
            "av01".toByteArray(Charsets.US_ASCII),
            payload.copyOfRange(4, 8),
        )
        assertArrayEquals(
            "primary".toByteArray(Charsets.UTF_8),
            payload.copyOfRange(8, 15),
        )
        assertEquals(0x00, payload[15].toInt())
    }

    @Test
    fun `encodePayload v=2 protection index encodes in 2 BE bytes`() {
        val entry = ItemInfoEntry.Entry(
            itemId = 1L,
            itemType = "av01",
            itemProtectionIndex = 0x1234,
        )
        val payload = ItemInfoEntry.encodePayload(entry, version = 2)
        assertArrayEquals(byteArrayOf(0x12, 0x34), payload.copyOfRange(2, 4))
    }

    @Test
    fun `encodePayload v=2 mime entry includes contentType and optional contentEncoding`() {
        val entryNoEncoding = ItemInfoEntry.Entry(
            itemId = 7L,
            itemType = "mime",
            contentType = "image/avif",
        )
        val noEncoding = ItemInfoEntry.encodePayload(entryNoEncoding, version = 2)
        // 2 + 2 + 4 + 1 (empty name NUL) + 10 + 1 = 20 bytes
        assertEquals(20, noEncoding.size)
        assertArrayEquals(
            "image/avif".toByteArray(Charsets.US_ASCII) + byteArrayOf(0),
            noEncoding.copyOfRange(9, 20),
        )

        val entryWithEncoding = ItemInfoEntry.Entry(
            itemId = 7L,
            itemType = "mime",
            contentType = "image/avif",
            contentEncoding = "gzip",
        )
        val withEncoding = ItemInfoEntry.encodePayload(entryWithEncoding, version = 2)
        // adds 4 + 1 = 5 bytes for "gzip\0"
        assertEquals(25, withEncoding.size)
        assertArrayEquals(
            "gzip".toByteArray(Charsets.US_ASCII) + byteArrayOf(0),
            withEncoding.copyOfRange(20, 25),
        )
    }

    @Test
    fun `encodePayload v=2 uri entry includes itemUriType`() {
        val entry = ItemInfoEntry.Entry(
            itemId = 3L,
            itemType = "uri ",
            itemUriType = "https://example.com",
        )
        val payload = ItemInfoEntry.encodePayload(entry, version = 2)
        // 2 + 2 + 4 + 1 + 19 + 1 = 29 bytes
        assertEquals(29, payload.size)
        assertArrayEquals(
            "https://example.com".toByteArray(Charsets.US_ASCII) + byteArrayOf(0),
            payload.copyOfRange(9, 29),
        )
    }

    @Test
    fun `encodePayload v=3 emits 4-byte item_ID big-endian`() {
        val entry = ItemInfoEntry.Entry(itemId = 0x12345678L, itemType = "av01")
        val payload = ItemInfoEntry.encodePayload(entry, version = 3)
        // item_ID(4) + protection(2) + type(4) + name(1) = 11 bytes
        assertEquals(11, payload.size)
        assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78), payload.copyOfRange(0, 4))
        assertArrayEquals(
            "av01".toByteArray(Charsets.US_ASCII),
            payload.copyOfRange(6, 10),
        )
        assertEquals(0, payload[10].toInt())
    }

    @Test
    fun `encodePayload v=2 rejects itemId beyond MAX_SMALL`() {
        val entry = ItemInfoEntry.Entry(itemId = 65536L, itemType = "av01")
        assertThrows(IllegalArgumentException::class.java) {
            ItemInfoEntry.encodePayload(entry, version = 2)
        }
    }

    @Test
    fun `encodePayload rejects v0, v1, v4`() {
        val entry = ItemInfoEntry.Entry(itemId = 1L, itemType = "av01")
        for (badVersion in listOf(0, 1, 4)) {
            assertThrows(IllegalArgumentException::class.java) {
                ItemInfoEntry.encodePayload(entry, version = badVersion)
            }
        }
    }

    // ------------------------------------------------------------------
    // encodeBox integration with IsobmffBox
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox av01 itemId=1 produces canonical 21-byte FullBox envelope`() {
        // FullBox header(8) + version+flags(4) + payload(9) = 21 bytes
        val box = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(itemId = 1L, itemType = "av01"),
        )
        assertEquals(21, box.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 21), box.copyOfRange(0, 4))
        assertArrayEquals("infe".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        // version=2, flags=0
        assertEquals(2, box[8].toInt() and 0xFF)
        assertEquals(0, box[9].toInt() and 0xFF)
        assertEquals(0, box[10].toInt() and 0xFF)
        assertEquals(0, box[11].toInt() and 0xFF)
        // item_ID=1 in 2 BE bytes
        assertArrayEquals(byteArrayOf(0, 1), box.copyOfRange(12, 14))
    }

    @Test
    fun `encodeBox auto-promotes to v=3 when itemId exceeds 65535`() {
        val box = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(itemId = 100_000L, itemType = "av01"),
        )
        assertEquals(3, box[8].toInt() and 0xFF)
    }

    @Test
    fun `encodeBox surfaces hidden=true as flags bit 0`() {
        val box = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(itemId = 1L, itemType = "av01", hidden = true),
        )
        assertEquals(2, box[8].toInt() and 0xFF) // version
        assertEquals(0, box[9].toInt() and 0xFF) // flags hi
        assertEquals(0, box[10].toInt() and 0xFF)
        assertEquals(1, box[11].toInt() and 0xFF) // flags low byte = 0x01 = FLAG_ITEM_HIDDEN
    }

    @Test
    fun `encodeBox EXIF metadata item produces canonical layout`() {
        val box = ItemInfoEntry.encodeBox(
            ItemInfoEntry.Entry(itemId = 2L, itemType = "Exif"),
        )
        assertEquals(21, box.size)
        assertArrayEquals("infe".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        // Layout: header(8) + version+flags(4) + item_ID(2) + protection(2) + item_type(4) + name_NUL(1)
        // item_type is at offset 16..20.
        assertArrayEquals(
            "Exif".toByteArray(Charsets.US_ASCII),
            box.copyOfRange(16, 20),
        )
    }
}
