package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [ItemLocationBox].
 *
 * Pinned facts (per ISO/IEC 14496-12 §8.11.3):
 *
 *  * Canonical AVIF still emits `version = 0`, `offset_size = 4`,
 *    `length_size = 4`, `base_offset_size = 0`, `index_size = 0`,
 *    one item with one extent → 18-byte payload, 30-byte total
 *    box.
 *  * `version = 1` adds the `construction_method` field (12-bit
 *    reserved + 4-bit construction method, packed in a uint16).
 *  * `version = 2` widens `item_count` and `item_ID` to 32 bits.
 */
class ItemLocationBoxTest {

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("iloc", ItemLocationBox.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, ItemLocationBox.SCHEMA_VERSION)
    }

    @Test
    fun `field-size limits pin`() {
        assertArrayEquals(intArrayOf(0, 4, 8), ItemLocationBox.ALLOWED_FIELD_SIZES)
        assertEquals(0xFFFFL, ItemLocationBox.MAX_SMALL_ITEM_ID)
        assertEquals(0xFFFFFFFFL, ItemLocationBox.MAX_LARGE_ITEM_ID)
        assertEquals(0xFFFFL, ItemLocationBox.MAX_SMALL_ITEM_COUNT)
        assertEquals(0xFFFFFFFFL, ItemLocationBox.MAX_LARGE_ITEM_COUNT)
        assertEquals(0xFFFF, ItemLocationBox.MAX_EXTENT_COUNT)
        assertEquals(0xFFFF, ItemLocationBox.MAX_DATA_REFERENCE_INDEX)
        assertEquals(0xF, ItemLocationBox.MAX_CONSTRUCTION_METHOD)
    }

    @Test
    fun `ConstructionMethod wire values match spec`() {
        assertEquals(0, ItemLocationBox.ConstructionMethod.FILE_OFFSET.wireValue)
        assertEquals(1, ItemLocationBox.ConstructionMethod.IDAT_OFFSET.wireValue)
        assertEquals(2, ItemLocationBox.ConstructionMethod.ITEM_OFFSET.wireValue)
    }

    // ------------------------------------------------------------------
    // Extent + Item validation
    // ------------------------------------------------------------------

    @Test
    fun `Extent rejects negative offset`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.Extent(offset = -1L, length = 10L)
        }
    }

    @Test
    fun `Extent rejects negative length`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.Extent(offset = 0L, length = -1L)
        }
    }

    @Test
    fun `Extent rejects negative index`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.Extent(offset = 0L, length = 10L, index = -1L)
        }
    }

    @Test
    fun `Extent zero length and zero offset accepted`() {
        val e = ItemLocationBox.Extent(offset = 0L, length = 0L)
        assertEquals(0L, e.offset)
        assertEquals(0L, e.length)
    }

    @Test
    fun `Item rejects negative itemId`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.Item(
                itemId = -1L,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            )
        }
    }

    @Test
    fun `Item rejects itemId beyond MAX_LARGE`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.Item(
                itemId = ItemLocationBox.MAX_LARGE_ITEM_ID + 1,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            )
        }
    }

    @Test
    fun `Item rejects empty extents list`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.Item(itemId = 1L, extents = emptyList())
        }
    }

    @Test
    fun `Item rejects out-of-range dataReferenceIndex`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.Item(
                itemId = 1L,
                dataReferenceIndex = -1,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.Item(
                itemId = 1L,
                dataReferenceIndex = 0x10000,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            )
        }
    }

    @Test
    fun `Item rejects negative baseOffset`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.Item(
                itemId = 1L,
                baseOffset = -1L,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            )
        }
    }

    // ------------------------------------------------------------------
    // FieldSizes validation
    // ------------------------------------------------------------------

    @Test
    fun `FieldSizes rejects values not in 0,4,8`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.FieldSizes(offsetSize = 1, lengthSize = 4, baseOffsetSize = 0, indexSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.FieldSizes(offsetSize = 4, lengthSize = 2, baseOffsetSize = 0, indexSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.FieldSizes(offsetSize = 4, lengthSize = 4, baseOffsetSize = 16, indexSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.FieldSizes(offsetSize = 4, lengthSize = 4, baseOffsetSize = 0, indexSize = 5)
        }
    }

    // ------------------------------------------------------------------
    // chooseFieldSizes
    // ------------------------------------------------------------------

    @Test
    fun `chooseFieldSizes for canonical AVIF still picks 4-4-0-0`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(ItemLocationBox.Extent(offset = 0x12000L, length = 0x4500L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        assertEquals(4, sizes.offsetSize)
        assertEquals(4, sizes.lengthSize)
        assertEquals(0, sizes.baseOffsetSize)
        assertEquals(0, sizes.indexSize)
    }

    @Test
    fun `chooseFieldSizes promotes offsetSize to 8 when offset exceeds 4 GB`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(
                    ItemLocationBox.Extent(offset = 0x1_0000_0000L, length = 100L),
                ),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        assertEquals(8, sizes.offsetSize)
        assertEquals(4, sizes.lengthSize)
    }

    @Test
    fun `chooseFieldSizes promotes lengthSize to 8 when length exceeds 4 GB`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(
                    ItemLocationBox.Extent(offset = 0L, length = 0x1_0000_0000L),
                ),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        assertEquals(8, sizes.lengthSize)
    }

    @Test
    fun `chooseFieldSizes promotes baseOffsetSize when any item has nonzero baseOffset`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                baseOffset = 0x10000L,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        assertEquals(4, sizes.baseOffsetSize)
    }

    // ------------------------------------------------------------------
    // chooseVersion
    // ------------------------------------------------------------------

    @Test
    fun `chooseVersion picks 0 for canonical AVIF still`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        assertEquals(0, ItemLocationBox.chooseVersion(items, sizes))
    }

    @Test
    fun `chooseVersion picks 1 when constructionMethod is non-FILE_OFFSET`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                constructionMethod = ItemLocationBox.ConstructionMethod.IDAT_OFFSET,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        assertEquals(1, ItemLocationBox.chooseVersion(items, sizes))
    }

    @Test
    fun `chooseVersion picks 2 when itemId exceeds 16 bits`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 0x1_0000L,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        assertEquals(2, ItemLocationBox.chooseVersion(items, sizes))
    }

    @Test
    fun `chooseVersion stays at 0 at boundary itemId 65535`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = ItemLocationBox.MAX_SMALL_ITEM_ID,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        assertEquals(0, ItemLocationBox.chooseVersion(items, sizes))
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins (canonical AVIF still)
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload v=0 canonical AVIF still produces 18-byte payload`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(ItemLocationBox.Extent(offset = 0x12000L, length = 0x4500L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        val payload = ItemLocationBox.encodePayload(items, version = 0, fieldSizes = sizes)
        // Layout (18 bytes):
        //   [0..2)  packed sizes: offsetSize=4, lengthSize=4, base=0, reserved=0  → 0x44 0x00
        //   [2..4)  item_count = 1                                                  → 0x00 0x01
        //   [4..6)  item_ID = 1                                                     → 0x00 0x01
        //   [6..8)  data_reference_index = 0                                        → 0x00 0x00
        //   [8..8)  base_offset (size=0): omitted
        //   [8..10) extent_count = 1                                                → 0x00 0x01
        //   [10..14) extent_offset = 0x12000 (4 bytes BE)                           → 0x00 0x01 0x20 0x00
        //   [14..18) extent_length = 0x4500 (4 bytes BE)                            → 0x00 0x00 0x45 0x00
        assertEquals(18, payload.size)
        assertArrayEquals(
            byteArrayOf(
                0x44, 0x00,
                0x00, 0x01,
                0x00, 0x01,
                0x00, 0x00,
                0x00, 0x01,
                0x00, 0x01, 0x20, 0x00,
                0x00, 0x00, 0x45, 0x00,
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload v=0 rejects indexSize gt 0`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            ),
        )
        val sizes = ItemLocationBox.FieldSizes(
            offsetSize = 4,
            lengthSize = 4,
            baseOffsetSize = 0,
            indexSize = 4,
        )
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.encodePayload(items, version = 0, fieldSizes = sizes)
        }
    }

    @Test
    fun `encodePayload v=0 rejects non-FILE_OFFSET construction`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                constructionMethod = ItemLocationBox.ConstructionMethod.IDAT_OFFSET,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.encodePayload(items, version = 0, fieldSizes = sizes)
        }
    }

    @Test
    fun `encodePayload v=1 surfaces construction_method field`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                constructionMethod = ItemLocationBox.ConstructionMethod.IDAT_OFFSET,
                extents = listOf(ItemLocationBox.Extent(offset = 0x100L, length = 10L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        val payload = ItemLocationBox.encodePayload(items, version = 1, fieldSizes = sizes)
        // v=1 layout (20 bytes):
        //   [0..2)  packed sizes 0x44 0x00
        //   [2..4)  item_count = 1                                                  → 0x00 0x01
        //   [4..6)  item_ID = 1                                                     → 0x00 0x01
        //   [6..8)  reserved(12)+constructionMethod(4) = 0x0001                     → 0x00 0x01
        //   [8..10) data_reference_index = 0                                        → 0x00 0x00
        //   [10..10) base_offset (size=0): omitted
        //   [10..12) extent_count = 1                                               → 0x00 0x01
        //   [12..16) extent_offset = 0x100                                          → 0x00 0x00 0x01 0x00
        //   [16..20) extent_length = 0x0A                                           → 0x00 0x00 0x00 0x0A
        assertEquals(20, payload.size)
        assertArrayEquals(
            byteArrayOf(
                0x44, 0x00,
                0x00, 0x01,
                0x00, 0x01,
                0x00, 0x01,
                0x00, 0x00,
                0x00, 0x01,
                0x00, 0x00, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x0A,
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload v=2 emits 32-bit item_count and item_ID`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 0x1_0000L,
                extents = listOf(ItemLocationBox.Extent(offset = 0x100L, length = 10L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        val payload = ItemLocationBox.encodePayload(items, version = 2, fieldSizes = sizes)
        // v=2 layout (24 bytes):
        //   [0..2)   packed sizes 0x44 0x00
        //   [2..6)   item_count = 1 (uint32_be)                                    → 0x00 0x00 0x00 0x01
        //   [6..10)  item_ID = 0x1_0000 (uint32_be)                                → 0x00 0x01 0x00 0x00
        //   [10..12) reserved(12)+constructionMethod(4) = 0                        → 0x00 0x00
        //   [12..14) data_reference_index = 0                                      → 0x00 0x00
        //   [14..14) base_offset (size=0): omitted
        //   [14..16) extent_count = 1                                              → 0x00 0x01
        //   [16..20) extent_offset = 0x100                                         → 0x00 0x00 0x01 0x00
        //   [20..24) extent_length = 10                                            → 0x00 0x00 0x00 0x0A
        assertEquals(24, payload.size)
        assertArrayEquals(
            byteArrayOf(
                0x44, 0x00,
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x01, 0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x01,
                0x00, 0x00, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x0A,
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload rejects invalid version`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.encodePayload(items, version = -1, fieldSizes = sizes)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.encodePayload(items, version = 3, fieldSizes = sizes)
        }
    }

    @Test
    fun `encodePayload rejects offset overflow at field size 4`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(
                    ItemLocationBox.Extent(offset = 0x1_0000_0000L, length = 10L),
                ),
            ),
        )
        // Force size=4 even though offset needs 8.
        val sizes = ItemLocationBox.FieldSizes(
            offsetSize = 4,
            lengthSize = 4,
            baseOffsetSize = 0,
            indexSize = 0,
        )
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.encodePayload(items, version = 0, fieldSizes = sizes)
        }
    }

    @Test
    fun `encodePayload rejects nonzero baseOffset when baseOffsetSize is 0`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                baseOffset = 0x100L,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            ),
        )
        val sizes = ItemLocationBox.FieldSizes(
            offsetSize = 4,
            lengthSize = 4,
            baseOffsetSize = 0,
            indexSize = 0,
        )
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.encodePayload(items, version = 0, fieldSizes = sizes)
        }
    }

    @Test
    fun `encodePayload v=2 rejects itemId beyond MAX_LARGE`() {
        // We can't construct an Item with a too-large itemId (init guards it),
        // but we can simulate by faking the count overflow when version < 2.
        val items = listOf(
            ItemLocationBox.Item(
                itemId = ItemLocationBox.MAX_LARGE_ITEM_ID,
                extents = listOf(ItemLocationBox.Extent(0L, 10L)),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        // version 0 cannot carry itemId > 65535
        assertThrows(IllegalArgumentException::class.java) {
            ItemLocationBox.encodePayload(items, version = 0, fieldSizes = sizes)
        }
    }

    @Test
    fun `encodePayload supports multiple extents`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(
                    ItemLocationBox.Extent(offset = 0x100L, length = 0x10L),
                    ItemLocationBox.Extent(offset = 0x200L, length = 0x20L),
                ),
            ),
        )
        val sizes = ItemLocationBox.chooseFieldSizes(items)
        val payload = ItemLocationBox.encodePayload(items, version = 0, fieldSizes = sizes)
        // 2 + 2 + 2 + 2 + 0 + 2 + (4+4)*2 = 26 bytes
        assertEquals(26, payload.size)
        // extent_count = 2 at offset [8..10)
        assertEquals(0x02, payload[9].toInt())
    }

    // ------------------------------------------------------------------
    // encodeBox integration
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox canonical AVIF still produces 30-byte FullBox envelope`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(ItemLocationBox.Extent(offset = 0x12000L, length = 0x4500L)),
            ),
        )
        val box = ItemLocationBox.encodeBox(items)
        // header(8) + version+flags(4) + payload(18) = 30 bytes
        assertEquals(30, box.size)
        // size BE = 30
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x1E), box.copyOfRange(0, 4))
        // type = "iloc"
        assertArrayEquals("iloc".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        // version=0, flags=0
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), box.copyOfRange(8, 12))
        // packed field sizes 0x44 0x00
        assertEquals(0x44, box[12].toInt() and 0xFF)
        assertEquals(0x00, box[13].toInt())
        // item_count = 1
        assertEquals(0x00, box[14].toInt())
        assertEquals(0x01, box[15].toInt())
        // item_ID = 1
        assertEquals(0x00, box[16].toInt())
        assertEquals(0x01, box[17].toInt())
    }

    @Test
    fun `encodeBox auto-promotes to v=2 for large itemId`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 0x1_0000L,
                extents = listOf(ItemLocationBox.Extent(offset = 0x100L, length = 10L)),
            ),
        )
        val box = ItemLocationBox.encodeBox(items)
        // version byte at offset 8
        assertEquals(0x02, box[8].toInt())
        // header(8) + v+f(4) + payload(24) = 36 bytes
        assertEquals(36, box.size)
    }

    @Test
    fun `encodeBox auto-promotes to v=1 for IDAT_OFFSET`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                constructionMethod = ItemLocationBox.ConstructionMethod.IDAT_OFFSET,
                extents = listOf(ItemLocationBox.Extent(offset = 0x100L, length = 10L)),
            ),
        )
        val box = ItemLocationBox.encodeBox(items)
        // version byte at offset 8 = 1
        assertEquals(0x01, box[8].toInt())
        // header(8) + v+f(4) + payload(20) = 32 bytes
        assertEquals(32, box.size)
    }

    @Test
    fun `encodeBox emits offset_size 8 for offset beyond 4 GB`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(
                    ItemLocationBox.Extent(offset = 0x1_0000_0000L, length = 10L),
                ),
            ),
        )
        val box = ItemLocationBox.encodeBox(items)
        // version=0, offsetSize=8, lengthSize=4, base=0, reserved=0 → packed 0x84 0x00
        assertEquals(0x84.toByte(), box[12])
        assertEquals(0x00.toByte(), box[13])
        // payload = 2 + 2 + 2 + 2 + 0 + 2 + 8 + 4 = 22 bytes; total = 12 + 22 = 34
        assertEquals(34, box.size)
    }

    @Test
    fun `encodeBox of two-item AVIF still (av01 + Exif) preserves order`() {
        val items = listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(ItemLocationBox.Extent(offset = 0x1000L, length = 0x4000L)),
            ),
            ItemLocationBox.Item(
                itemId = 2L,
                extents = listOf(ItemLocationBox.Extent(offset = 0x5000L, length = 0x100L)),
            ),
        )
        val box = ItemLocationBox.encodeBox(items)
        // item_count = 2 at offset [14..16)
        assertEquals(0x00.toByte(), box[14])
        assertEquals(0x02.toByte(), box[15])
        // first item_ID = 1 at offset [16..18)
        assertEquals(0x00.toByte(), box[16])
        assertEquals(0x01.toByte(), box[17])
        // we can't easily index into the second item without computing
        // sizes, but a re-encode with reversed input should differ:
        val reversed = ItemLocationBox.encodeBox(items.reversed())
        assertNotEquals(
            box.toList(),
            reversed.toList(),
        )
    }
}
