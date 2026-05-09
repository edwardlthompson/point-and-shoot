package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [ItemReferenceBox].
 *
 * Pinned facts (per ISO/IEC 14496-12 §8.11.12):
 *
 *  * `iref` is a `FullBox('iref', version, 0)`.
 *  * Payload is an ordered list of `SingleItemTypeReferenceBox`
 *    sub-boxes, each with its own 8-byte plain header where the
 *    type is the 4-byte ASCII reference-type code (e.g. `"cdsc"`).
 *  * Sub-box body for v=0:
 *    `from_item_ID (uint16) + reference_count (uint16) +
 *     reference_count × to_item_ID (uint16)`.
 *  * Sub-box body for v=1: same but every field is 4 bytes wide.
 *  * Version is the **minimum** that can encode every itemId and
 *    reference_count without truncation.
 */
class ItemReferenceBoxTest {

    // ------------------------------------------------------------------
    // Constant pins
    // ------------------------------------------------------------------

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("iref", ItemReferenceBox.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, ItemReferenceBox.SCHEMA_VERSION)
    }

    @Test
    fun `REFERENCE_TYPE pins`() {
        assertEquals("cdsc", ItemReferenceBox.REFERENCE_TYPE_CDSC)
        assertEquals("auxl", ItemReferenceBox.REFERENCE_TYPE_AUXL)
        assertEquals("thmb", ItemReferenceBox.REFERENCE_TYPE_THMB)
        assertEquals("dimg", ItemReferenceBox.REFERENCE_TYPE_DIMG)
    }

    @Test
    fun `MAX_SMALL_ITEM_ID pin`() {
        assertEquals(65535L, ItemReferenceBox.MAX_SMALL_ITEM_ID)
    }

    @Test
    fun `MAX_LARGE_ITEM_ID pin`() {
        assertEquals(0xFFFFFFFFL, ItemReferenceBox.MAX_LARGE_ITEM_ID)
    }

    @Test
    fun `MAX_SMALL_REFERENCE_COUNT pin`() {
        assertEquals(65535, ItemReferenceBox.MAX_SMALL_REFERENCE_COUNT)
    }

    @Test
    fun `MAX_LARGE_REFERENCE_COUNT pin`() {
        assertEquals(0xFFFFFFFFL, ItemReferenceBox.MAX_LARGE_REFERENCE_COUNT)
    }

    // ------------------------------------------------------------------
    // Reference validation
    // ------------------------------------------------------------------

    @Test
    fun `Reference rejects non-4-char referenceType`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.Reference(referenceType = "cds", fromItemId = 1, toItemIds = listOf(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.Reference(referenceType = "cdscX", fromItemId = 1, toItemIds = listOf(2))
        }
    }

    @Test
    fun `Reference rejects non-printable-ASCII referenceType`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.Reference(referenceType = "cd\u0001c", fromItemId = 1, toItemIds = listOf(2))
        }
    }

    @Test
    fun `Reference rejects empty toItemIds`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.Reference(referenceType = "cdsc", fromItemId = 1, toItemIds = emptyList())
        }
    }

    @Test
    fun `Reference rejects negative fromItemId`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.Reference(referenceType = "cdsc", fromItemId = -1, toItemIds = listOf(2))
        }
    }

    @Test
    fun `Reference rejects fromItemId above MAX_LARGE_ITEM_ID`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.Reference(
                referenceType = "cdsc",
                fromItemId = 0x1_0000_0000L,
                toItemIds = listOf(2),
            )
        }
    }

    @Test
    fun `Reference rejects toItemId above MAX_LARGE_ITEM_ID`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.Reference(
                referenceType = "cdsc",
                fromItemId = 1,
                toItemIds = listOf(0x1_0000_0000L),
            )
        }
    }

    @Test
    fun `Reference accepts canonical AVIF EXIF cdsc shape`() {
        val ref = ItemReferenceBox.Reference(
            referenceType = ItemReferenceBox.REFERENCE_TYPE_CDSC,
            fromItemId = 2,
            toItemIds = listOf(1),
        )
        assertEquals("cdsc", ref.referenceType)
        assertEquals(2L, ref.fromItemId)
        assertEquals(listOf(1L), ref.toItemIds)
    }

    // ------------------------------------------------------------------
    // chooseVersion
    // ------------------------------------------------------------------

    @Test
    fun `chooseVersion returns 0 for empty list`() {
        assertEquals(0, ItemReferenceBox.chooseVersion(emptyList()))
    }

    @Test
    fun `chooseVersion returns 0 when all itemIds and counts fit 16 bits`() {
        val refs = listOf(
            ItemReferenceBox.Reference("cdsc", 2, listOf(1)),
            ItemReferenceBox.Reference("auxl", 65535, listOf(65535)),
        )
        assertEquals(0, ItemReferenceBox.chooseVersion(refs))
    }

    @Test
    fun `chooseVersion returns 1 for fromItemId beyond 16 bits`() {
        val refs = listOf(
            ItemReferenceBox.Reference("cdsc", 0x10000, listOf(1)),
        )
        assertEquals(1, ItemReferenceBox.chooseVersion(refs))
    }

    @Test
    fun `chooseVersion returns 1 for toItemId beyond 16 bits`() {
        val refs = listOf(
            ItemReferenceBox.Reference("cdsc", 1, listOf(0x10000)),
        )
        assertEquals(1, ItemReferenceBox.chooseVersion(refs))
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload empty list returns 0 bytes`() {
        val payload = ItemReferenceBox.encodePayload(emptyList(), version = 0)
        assertEquals(0, payload.size)
    }

    @Test
    fun `encodePayload v=0 canonical cdsc reference produces documented bytes`() {
        // EXIF item (id=2) describes primary image item (id=1).
        val payload = ItemReferenceBox.encodePayload(
            listOf(ItemReferenceBox.Reference("cdsc", fromItemId = 2, toItemIds = listOf(1))),
            version = 0,
        )
        // SingleItemTypeReferenceBox: 8-byte header + 6-byte body
        //   header: size=14 (BE) + type "cdsc"
        //   body: from_item_ID=2 (BE) + reference_count=1 (BE) + to_item_ID=1 (BE)
        val expected = byteArrayOf(
            0x00, 0x00, 0x00, 0x0E,
            'c'.code.toByte(), 'd'.code.toByte(), 's'.code.toByte(), 'c'.code.toByte(),
            0x00, 0x02,
            0x00, 0x01,
            0x00, 0x01,
        )
        assertArrayEquals(expected, payload)
    }

    @Test
    fun `encodePayload v=0 multi-target reference packs every to_item_ID`() {
        val payload = ItemReferenceBox.encodePayload(
            listOf(
                ItemReferenceBox.Reference(
                    "dimg",
                    fromItemId = 5,
                    toItemIds = listOf(1L, 2L, 3L, 4L),
                ),
            ),
            version = 0,
        )
        // body: from(2) + count(2) + 4 × to(2) = 12 bytes; header = 8 → total = 20
        assertEquals(20, payload.size)
        val expected = byteArrayOf(
            0x00, 0x00, 0x00, 0x14,
            'd'.code.toByte(), 'i'.code.toByte(), 'm'.code.toByte(), 'g'.code.toByte(),
            0x00, 0x05,
            0x00, 0x04,
            0x00, 0x01,
            0x00, 0x02,
            0x00, 0x03,
            0x00, 0x04,
        )
        assertArrayEquals(expected, payload)
    }

    @Test
    fun `encodePayload v=0 multiple references concatenated in order`() {
        // First a cdsc, then an auxl.
        val payload = ItemReferenceBox.encodePayload(
            listOf(
                ItemReferenceBox.Reference("cdsc", 2, listOf(1)),
                ItemReferenceBox.Reference("auxl", 3, listOf(1)),
            ),
            version = 0,
        )
        // Each sub-box is 14 bytes, total = 28.
        assertEquals(28, payload.size)
        // First sub-box should match canonical cdsc encoding.
        assertEquals("cdsc", String(payload.copyOfRange(4, 8), Charsets.US_ASCII))
        // Second sub-box should be at offset 14.
        assertEquals("auxl", String(payload.copyOfRange(14 + 4, 14 + 8), Charsets.US_ASCII))
    }

    @Test
    fun `encodePayload v=1 emits 4-byte fields`() {
        val payload = ItemReferenceBox.encodePayload(
            listOf(
                ItemReferenceBox.Reference("cdsc", fromItemId = 0x12345678L, toItemIds = listOf(0xCAFEBABEL)),
            ),
            version = 1,
        )
        // header(8) + from(4) + count(4) + to(4) = 20 bytes
        assertEquals(20, payload.size)
        val expected = byteArrayOf(
            0x00, 0x00, 0x00, 0x14,
            'c'.code.toByte(), 'd'.code.toByte(), 's'.code.toByte(), 'c'.code.toByte(),
            0x12, 0x34, 0x56, 0x78,
            0x00, 0x00, 0x00, 0x01,
            0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(),
        )
        assertArrayEquals(expected, payload)
    }

    @Test
    fun `encodePayload v=0 rejects fromItemId beyond 16 bits`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.encodePayload(
                listOf(ItemReferenceBox.Reference("cdsc", 0x10000, listOf(1))),
                version = 0,
            )
        }
    }

    @Test
    fun `encodePayload v=0 rejects toItemId beyond 16 bits`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.encodePayload(
                listOf(ItemReferenceBox.Reference("cdsc", 1, listOf(0x10000))),
                version = 0,
            )
        }
    }

    @Test
    fun `encodePayload rejects invalid version`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.encodePayload(emptyList(), version = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemReferenceBox.encodePayload(emptyList(), version = 2)
        }
    }

    // ------------------------------------------------------------------
    // encodeBox integration
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox empty references produces 12-byte FullBox envelope`() {
        val box = ItemReferenceBox.encodeBox(emptyList())
        // 8 header + 4 v+f = 12 bytes (no payload)
        assertEquals(12, box.size)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x0C), box.copyOfRange(0, 4))
        assertEquals("iref", String(box.copyOfRange(4, 8), Charsets.US_ASCII))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), box.copyOfRange(8, 12))
    }

    @Test
    fun `encodeBox canonical AVIF EXIF cdsc produces 26-byte envelope`() {
        val box = ItemReferenceBox.encodeBox(
            listOf(
                ItemReferenceBox.Reference(
                    ItemReferenceBox.REFERENCE_TYPE_CDSC,
                    fromItemId = 2,
                    toItemIds = listOf(1),
                ),
            ),
        )
        // header(8) + v+f(4) + sub-box(14) = 26 bytes
        assertEquals(26, box.size)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x1A), box.copyOfRange(0, 4))
        assertEquals("iref", String(box.copyOfRange(4, 8), Charsets.US_ASCII))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), box.copyOfRange(8, 12))
        // Sub-box at offset 12.
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x0E), box.copyOfRange(12, 16))
        assertEquals("cdsc", String(box.copyOfRange(16, 20), Charsets.US_ASCII))
    }

    @Test
    fun `encodeBox auto-promotes to v=1 for itemId beyond 16 bits`() {
        val box = ItemReferenceBox.encodeBox(
            listOf(
                ItemReferenceBox.Reference("cdsc", fromItemId = 0x10000, toItemIds = listOf(1)),
            ),
        )
        // version field is byte 8 of the FullBox; should be 0x01.
        assertEquals(0x01.toByte(), box[8])
        // sub-box body is 12 bytes (from=4 + count=4 + to=4) → header(8) + 12 = 20
        // total: header(8) + v+f(4) + 20 = 32
        assertEquals(32, box.size)
    }

    @Test
    fun `encodeBox auxl alpha aux pattern`() {
        // Alpha aux item (id=3) is auxiliary to primary image item (id=1).
        val box = ItemReferenceBox.encodeBox(
            listOf(
                ItemReferenceBox.Reference(
                    ItemReferenceBox.REFERENCE_TYPE_AUXL,
                    fromItemId = 3,
                    toItemIds = listOf(1),
                ),
            ),
        )
        assertEquals(26, box.size)
        assertEquals("auxl", String(box.copyOfRange(16, 20), Charsets.US_ASCII))
    }

    @Test
    fun `encodeBox EXIF + alpha bundle produces deterministic byte sequence`() {
        // Realistic AVIF still with EXIF metadata + alpha auxiliary:
        //   item 1 = primary av01 image
        //   item 2 = EXIF metadata (cdsc → 1)
        //   item 3 = alpha auxiliary (auxl → 1)
        val box = ItemReferenceBox.encodeBox(
            listOf(
                ItemReferenceBox.Reference("cdsc", 2, listOf(1)),
                ItemReferenceBox.Reference("auxl", 3, listOf(1)),
            ),
        )
        // Total: 12 (header+v+f) + 14 (cdsc) + 14 (auxl) = 40
        assertEquals(40, box.size)
        assertEquals("iref", String(box.copyOfRange(4, 8), Charsets.US_ASCII))
        assertEquals("cdsc", String(box.copyOfRange(16, 20), Charsets.US_ASCII))
        assertEquals("auxl", String(box.copyOfRange(30, 34), Charsets.US_ASCII))
    }

    @Test
    fun `encodeBox order-preserving across references`() {
        val first = ItemReferenceBox.encodeBox(
            listOf(
                ItemReferenceBox.Reference("cdsc", 2, listOf(1)),
                ItemReferenceBox.Reference("auxl", 3, listOf(1)),
            ),
        )
        val swapped = ItemReferenceBox.encodeBox(
            listOf(
                ItemReferenceBox.Reference("auxl", 3, listOf(1)),
                ItemReferenceBox.Reference("cdsc", 2, listOf(1)),
            ),
        )
        // Same total size, but different byte sequence (sub-box order matters).
        assertEquals(first.size, swapped.size)
        org.junit.Assert.assertFalse(first.contentEquals(swapped))
    }
}
