package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit tests for [ItemPropertyAssociation].
 *
 * Pinned facts (per ISO/IEC 14496-12 §8.11.14 + ISO/IEC 23008-12 §9.3.2):
 *
 *  * `entry_count` is 4 bytes, big-endian.
 *  * `item_ID` is 2 bytes (`version=0`) or 4 bytes (`version=1`),
 *    big-endian.
 *  * `association_count` is 1 byte.
 *  * Each association is 1 byte (`flags & 1 == 0`) or 2 bytes
 *    (`flags & 1 == 1`), big-endian; the high bit is `essential`,
 *    the rest is the property index.
 */
class ItemPropertyAssociationTest {

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("ipma", ItemPropertyAssociation.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, ItemPropertyAssociation.SCHEMA_VERSION)
    }

    @Test
    fun `flag and bound constants pin`() {
        assertEquals(0x000001, ItemPropertyAssociation.FLAG_LARGE_PROPERTY_INDEX)
        assertEquals(0x7F, ItemPropertyAssociation.MAX_SMALL_PROPERTY_INDEX)
        assertEquals(0x7FFF, ItemPropertyAssociation.MAX_LARGE_PROPERTY_INDEX)
        assertEquals(0xFFFFL, ItemPropertyAssociation.MAX_SMALL_ITEM_ID)
        assertEquals(0xFFFFFFFFL, ItemPropertyAssociation.MAX_LARGE_ITEM_ID)
        assertEquals(0xFF, ItemPropertyAssociation.MAX_ASSOCIATION_COUNT)
    }

    // ------------------------------------------------------------------
    // Association data class
    // ------------------------------------------------------------------

    @Test
    fun `Association rejects propertyIndex less than 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.Association(propertyIndex = 0, essential = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.Association(propertyIndex = -1, essential = true)
        }
    }

    @Test
    fun `Association rejects propertyIndex above max`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.Association(
                propertyIndex = ItemPropertyAssociation.MAX_LARGE_PROPERTY_INDEX + 1,
                essential = true,
            )
        }
    }

    @Test
    fun `Association accepts boundary values`() {
        val lo = ItemPropertyAssociation.Association(propertyIndex = 1, essential = true)
        assertEquals(1, lo.propertyIndex)
        val hi = ItemPropertyAssociation.Association(
            propertyIndex = ItemPropertyAssociation.MAX_LARGE_PROPERTY_INDEX,
            essential = false,
        )
        assertEquals(ItemPropertyAssociation.MAX_LARGE_PROPERTY_INDEX, hi.propertyIndex)
    }

    // ------------------------------------------------------------------
    // Entry data class
    // ------------------------------------------------------------------

    @Test
    fun `Entry rejects negative itemId`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.Entry(
                itemId = -1L,
                associations = listOf(ItemPropertyAssociation.Association(1, true)),
            )
        }
    }

    @Test
    fun `Entry rejects itemId above MAX_LARGE_ITEM_ID`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.Entry(
                itemId = ItemPropertyAssociation.MAX_LARGE_ITEM_ID + 1,
                associations = listOf(ItemPropertyAssociation.Association(1, true)),
            )
        }
    }

    @Test
    fun `Entry rejects empty associations`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.Entry(itemId = 1L, associations = emptyList())
        }
    }

    @Test
    fun `Entry rejects associations beyond MAX_ASSOCIATION_COUNT`() {
        val tooMany = (1..256).map { ItemPropertyAssociation.Association(it.coerceAtMost(127), false) }
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.Entry(itemId = 1L, associations = tooMany)
        }
    }

    @Test
    fun `Entry accepts boundary values`() {
        val lo = ItemPropertyAssociation.Entry(
            itemId = 0L,
            associations = listOf(ItemPropertyAssociation.Association(1, true)),
        )
        assertEquals(0L, lo.itemId)
        val hi = ItemPropertyAssociation.Entry(
            itemId = ItemPropertyAssociation.MAX_LARGE_ITEM_ID,
            associations = listOf(ItemPropertyAssociation.Association(1, true)),
        )
        assertEquals(ItemPropertyAssociation.MAX_LARGE_ITEM_ID, hi.itemId)
    }

    // ------------------------------------------------------------------
    // chooseVersionAndFlags
    // ------------------------------------------------------------------

    @Test
    fun `chooseVersionAndFlags picks 0,0 for the canonical AVIF still case`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = 1L,
                associations = listOf(
                    ItemPropertyAssociation.Association(1, true),  // colr
                    ItemPropertyAssociation.Association(2, true),  // pixi
                    ItemPropertyAssociation.Association(3, false), // irot
                ),
            ),
        )
        val (version, flags) = ItemPropertyAssociation.chooseVersionAndFlags(entries)
        assertEquals(0, version)
        assertEquals(0, flags)
    }

    @Test
    fun `chooseVersionAndFlags picks version=1 when itemId exceeds 16 bits`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = 100_000L,
                associations = listOf(ItemPropertyAssociation.Association(1, true)),
            ),
        )
        val (version, flags) = ItemPropertyAssociation.chooseVersionAndFlags(entries)
        assertEquals(1, version)
        assertEquals(0, flags)
    }

    @Test
    fun `chooseVersionAndFlags picks flags=1 when propertyIndex exceeds 7 bits`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = 1L,
                associations = listOf(ItemPropertyAssociation.Association(200, true)),
            ),
        )
        val (version, flags) = ItemPropertyAssociation.chooseVersionAndFlags(entries)
        assertEquals(0, version)
        assertEquals(ItemPropertyAssociation.FLAG_LARGE_PROPERTY_INDEX, flags)
    }

    @Test
    fun `chooseVersionAndFlags picks 1,1 when both bounds are exceeded`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = 100_000L,
                associations = listOf(ItemPropertyAssociation.Association(200, true)),
            ),
        )
        val (version, flags) = ItemPropertyAssociation.chooseVersionAndFlags(entries)
        assertEquals(1, version)
        assertEquals(ItemPropertyAssociation.FLAG_LARGE_PROPERTY_INDEX, flags)
    }

    @Test
    fun `chooseVersionAndFlags picks 0,0 for empty entries`() {
        val (version, flags) = ItemPropertyAssociation.chooseVersionAndFlags(emptyList())
        assertEquals(0, version)
        assertEquals(0, flags)
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload emits 4-byte entry_count for empty entries`() {
        val payload = ItemPropertyAssociation.encodePayload(emptyList(), version = 0, flags = 0)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), payload)
    }

    @Test
    fun `encodePayload v=0 f=0 single essential property index 1 has documented byte sequence`() {
        val entry = ItemPropertyAssociation.Entry(
            itemId = 1L,
            associations = listOf(ItemPropertyAssociation.Association(propertyIndex = 1, essential = true)),
        )
        val payload = ItemPropertyAssociation.encodePayload(listOf(entry), version = 0, flags = 0)
        // entry_count(4) + item_ID(2) + association_count(1) + association(1) = 8 bytes
        // [0 0 0 1] + [0 1] + [01] + [0x81] (essential=1 << 7 | 1)
        assertArrayEquals(
            byteArrayOf(
                0, 0, 0, 1,
                0, 1,
                0x01,
                0x81.toByte(),
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload v=0 f=0 non-essential property index 1 packs essential=0`() {
        val entry = ItemPropertyAssociation.Entry(
            itemId = 1L,
            associations = listOf(ItemPropertyAssociation.Association(propertyIndex = 1, essential = false)),
        )
        val payload = ItemPropertyAssociation.encodePayload(listOf(entry), version = 0, flags = 0)
        // last byte: essential=0 << 7 | 1 = 0x01
        assertEquals(0x01, payload.last().toInt() and 0xFF)
    }

    @Test
    fun `encodePayload v=0 f=0 max small property index 127 packs as 0xFF when essential`() {
        val entry = ItemPropertyAssociation.Entry(
            itemId = 1L,
            associations = listOf(
                ItemPropertyAssociation.Association(
                    propertyIndex = ItemPropertyAssociation.MAX_SMALL_PROPERTY_INDEX,
                    essential = true,
                ),
            ),
        )
        val payload = ItemPropertyAssociation.encodePayload(listOf(entry), version = 0, flags = 0)
        // last byte: essential=1 << 7 | 127 = 0xFF
        assertEquals(0xFF, payload.last().toInt() and 0xFF)
    }

    @Test
    fun `encodePayload v=0 f=0 multi-association (colr, pixi, irot) byte sequence`() {
        // Canonical AVIF still-image case: item 1 = primary image,
        // associated with property 1 (colr, essential), 2 (pixi, essential),
        // 3 (irot, non-essential).
        val entry = ItemPropertyAssociation.Entry(
            itemId = 1L,
            associations = listOf(
                ItemPropertyAssociation.Association(propertyIndex = 1, essential = true),
                ItemPropertyAssociation.Association(propertyIndex = 2, essential = true),
                ItemPropertyAssociation.Association(propertyIndex = 3, essential = false),
            ),
        )
        val payload = ItemPropertyAssociation.encodePayload(listOf(entry), version = 0, flags = 0)
        assertArrayEquals(
            byteArrayOf(
                0, 0, 0, 1, // entry_count = 1
                0, 1, // item_ID = 1
                0x03, // association_count = 3
                0x81.toByte(), // essential=1 | propIdx=1
                0x82.toByte(), // essential=1 | propIdx=2
                0x03, // essential=0 | propIdx=3
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload v=1 f=0 emits 4-byte item_ID big-endian`() {
        val entry = ItemPropertyAssociation.Entry(
            itemId = 0x12345678L,
            associations = listOf(ItemPropertyAssociation.Association(1, true)),
        )
        val payload = ItemPropertyAssociation.encodePayload(listOf(entry), version = 1, flags = 0)
        assertArrayEquals(
            byteArrayOf(
                0, 0, 0, 1,
                0x12, 0x34, 0x56, 0x78,
                0x01,
                0x81.toByte(),
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload v=0 f=1 emits 2-byte association big-endian`() {
        val entry = ItemPropertyAssociation.Entry(
            itemId = 1L,
            associations = listOf(
                ItemPropertyAssociation.Association(
                    propertyIndex = 200,
                    essential = true,
                ),
            ),
        )
        val payload = ItemPropertyAssociation.encodePayload(
            listOf(entry),
            version = 0,
            flags = ItemPropertyAssociation.FLAG_LARGE_PROPERTY_INDEX,
        )
        // packed = essential(1) << 15 | 200 = 0x8000 | 0xC8 = 0x80C8
        assertArrayEquals(
            byteArrayOf(
                0, 0, 0, 1,
                0, 1,
                0x01,
                0x80.toByte(), 0xC8.toByte(),
            ),
            payload,
        )
    }

    @Test
    fun `encodePayload rejects invalid version`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.encodePayload(emptyList(), version = 2, flags = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.encodePayload(emptyList(), version = -1, flags = 0)
        }
    }

    @Test
    fun `encodePayload rejects flags outside 24-bit range`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.encodePayload(emptyList(), version = 0, flags = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.encodePayload(emptyList(), version = 0, flags = 0x1000000)
        }
    }

    @Test
    fun `encodePayload rejects itemId that overflows version=0 capacity`() {
        val entry = ItemPropertyAssociation.Entry(
            itemId = 100_000L,
            associations = listOf(ItemPropertyAssociation.Association(1, true)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.encodePayload(listOf(entry), version = 0, flags = 0)
        }
    }

    @Test
    fun `encodePayload rejects propertyIndex that overflows flags=0 capacity`() {
        val entry = ItemPropertyAssociation.Entry(
            itemId = 1L,
            associations = listOf(ItemPropertyAssociation.Association(200, true)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.encodePayload(listOf(entry), version = 0, flags = 0)
        }
    }

    // ------------------------------------------------------------------
    // Round-trip encode -> decode
    // ------------------------------------------------------------------

    @Test
    fun `encode and decode round-trip canonical AVIF still`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = 1L,
                associations = listOf(
                    ItemPropertyAssociation.Association(1, true),
                    ItemPropertyAssociation.Association(2, true),
                    ItemPropertyAssociation.Association(3, false),
                ),
            ),
        )
        val payload = ItemPropertyAssociation.encodePayload(entries, version = 0, flags = 0)
        val decoded = ItemPropertyAssociation.decodePayload(payload, version = 0, flags = 0)
        assertEquals(entries, decoded)
    }

    @Test
    fun `encode and decode round-trip v=1 large item ID`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = ItemPropertyAssociation.MAX_LARGE_ITEM_ID,
                associations = listOf(ItemPropertyAssociation.Association(1, true)),
            ),
        )
        val payload = ItemPropertyAssociation.encodePayload(entries, version = 1, flags = 0)
        val decoded = ItemPropertyAssociation.decodePayload(payload, version = 1, flags = 0)
        assertEquals(entries, decoded)
    }

    @Test
    fun `encode and decode round-trip flags=1 large property index`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = 1L,
                associations = listOf(
                    ItemPropertyAssociation.Association(
                        ItemPropertyAssociation.MAX_LARGE_PROPERTY_INDEX,
                        true,
                    ),
                    ItemPropertyAssociation.Association(
                        ItemPropertyAssociation.MAX_LARGE_PROPERTY_INDEX,
                        false,
                    ),
                ),
            ),
        )
        val payload = ItemPropertyAssociation.encodePayload(
            entries,
            version = 0,
            flags = ItemPropertyAssociation.FLAG_LARGE_PROPERTY_INDEX,
        )
        val decoded = ItemPropertyAssociation.decodePayload(
            payload,
            version = 0,
            flags = ItemPropertyAssociation.FLAG_LARGE_PROPERTY_INDEX,
        )
        assertEquals(entries, decoded)
    }

    @Test
    fun `encode and decode round-trip multiple entries`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = 1L,
                associations = listOf(ItemPropertyAssociation.Association(1, true)),
            ),
            ItemPropertyAssociation.Entry(
                itemId = 2L,
                associations = listOf(
                    ItemPropertyAssociation.Association(1, true),
                    ItemPropertyAssociation.Association(4, false),
                ),
            ),
            ItemPropertyAssociation.Entry(
                itemId = 65535L,
                associations = listOf(ItemPropertyAssociation.Association(127, true)),
            ),
        )
        val (version, flags) = ItemPropertyAssociation.chooseVersionAndFlags(entries)
        assertEquals(0, version)
        assertEquals(0, flags)
        val payload = ItemPropertyAssociation.encodePayload(entries, version, flags)
        val decoded = ItemPropertyAssociation.decodePayload(payload, version, flags)
        assertEquals(entries, decoded)
    }

    // ------------------------------------------------------------------
    // decodePayload error paths
    // ------------------------------------------------------------------

    @Test
    fun `decodePayload throws on under-4-byte input`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.decodePayload(byteArrayOf(0, 0, 0), version = 0, flags = 0)
        }
    }

    @Test
    fun `decodePayload throws on truncated item_ID`() {
        // Claims 1 entry but only has 4 bytes (entry_count, no item_ID).
        val truncated = byteArrayOf(0, 0, 0, 1)
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.decodePayload(truncated, version = 0, flags = 0)
        }
    }

    @Test
    fun `decodePayload throws on truncated association_count`() {
        // Claims 1 entry, has item_ID=1 but missing association_count.
        val truncated = byteArrayOf(0, 0, 0, 1, 0, 1)
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.decodePayload(truncated, version = 0, flags = 0)
        }
    }

    @Test
    fun `decodePayload throws on truncated association`() {
        // Claims 1 entry, item_ID=1, association_count=1 but missing the association byte.
        val truncated = byteArrayOf(0, 0, 0, 1, 0, 1, 0x01)
        assertThrows(IllegalArgumentException::class.java) {
            ItemPropertyAssociation.decodePayload(truncated, version = 0, flags = 0)
        }
    }

    // ------------------------------------------------------------------
    // encodeBox integration with IsobmffBox
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox wraps payload in canonical FullBox envelope`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = 1L,
                associations = listOf(ItemPropertyAssociation.Association(1, true)),
            ),
        )
        val box = ItemPropertyAssociation.encodeBox(entries)
        // FullBox header: size(4) + type(4) + version+flags(4) + payload(8) = 20 bytes
        // entry_count(4) + item_ID(2) + association_count(1) + association(1) = 8-byte payload
        assertEquals(20, box.size)
        // size = 20
        assertArrayEquals(byteArrayOf(0, 0, 0, 20), box.copyOfRange(0, 4))
        // type = "ipma"
        assertArrayEquals("ipma".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        // version=0, flags=0
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), box.copyOfRange(8, 12))
    }

    @Test
    fun `encodeBox auto-promotes version when itemId exceeds 16 bits`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = 100_000L,
                associations = listOf(ItemPropertyAssociation.Association(1, true)),
            ),
        )
        val box = ItemPropertyAssociation.encodeBox(entries)
        // version byte (offset 8) should be 1
        assertEquals(1, box[8].toInt() and 0xFF)
        // flags should still be 0
        assertEquals(0, box[9].toInt() and 0xFF)
        assertEquals(0, box[10].toInt() and 0xFF)
        assertEquals(0, box[11].toInt() and 0xFF)
    }

    @Test
    fun `encodeBox auto-promotes flags when propertyIndex exceeds 7 bits`() {
        val entries = listOf(
            ItemPropertyAssociation.Entry(
                itemId = 1L,
                associations = listOf(ItemPropertyAssociation.Association(200, true)),
            ),
        )
        val box = ItemPropertyAssociation.encodeBox(entries)
        // flags low byte should be 1
        assertEquals(1, box[11].toInt() and 0xFF)
    }

    // ------------------------------------------------------------------
    // Equality semantics for callers that build entries on the fly.
    // ------------------------------------------------------------------

    @Test
    fun `Entry equality treats associations list by content`() {
        val a = ItemPropertyAssociation.Entry(
            itemId = 1L,
            associations = listOf(
                ItemPropertyAssociation.Association(1, true),
                ItemPropertyAssociation.Association(2, false),
            ),
        )
        val b = ItemPropertyAssociation.Entry(
            itemId = 1L,
            associations = listOf(
                ItemPropertyAssociation.Association(1, true),
                ItemPropertyAssociation.Association(2, false),
            ),
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Association equality is by content not reference`() {
        val a = ItemPropertyAssociation.Association(1, true)
        val b = ItemPropertyAssociation.Association(1, true)
        assertTrue(a !== b)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
