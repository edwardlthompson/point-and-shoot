package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit tests for [MetaBox].
 *
 * Pinned facts (per ISO/IEC 14496-12 §8.11.1):
 *
 *  * `meta` is a `FullBox('meta', version = 0, flags = 0)`.
 *  * The first child MUST be `hdlr`.
 *  * No child type may appear more than once.
 *  * The remaining child boxes are optional and appear in the
 *    order documented in §8.11.1 (`pitm`, `iinf`, `iloc`, `iref`,
 *    `iprp`).
 */
class MetaBoxTest {

    private val hdlr = HandlerReferenceBox.encodePictBox()
    private val pitm = PrimaryItemBox.encodeBox(itemId = 1L)
    private val infe = ItemInfoEntry.encodeBox(
        ItemInfoEntry.Entry(itemId = 1L, itemType = ItemInfoEntry.ITEM_TYPE_AV01),
    )
    private val iinf = ItemInfoBox.encodeBox(listOf(infe))
    private val iloc = ItemLocationBox.encodeBox(
        listOf(
            ItemLocationBox.Item(
                itemId = 1L,
                extents = listOf(ItemLocationBox.Extent(offset = 0x12000L, length = 0x4500L)),
            ),
        ),
    )

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("meta", MetaBox.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, MetaBox.SCHEMA_VERSION)
    }

    @Test
    fun `MIN_CHILD_BOX_SIZE pin`() {
        assertEquals(8, MetaBox.MIN_CHILD_BOX_SIZE)
        assertEquals(IsobmffBox.PLAIN_HEADER_SIZE, MetaBox.MIN_CHILD_BOX_SIZE)
    }

    // ------------------------------------------------------------------
    // encodePayload
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload with only hdlr equals hdlr bytes`() {
        val payload = MetaBox.encodePayload(handlerBox = hdlr)
        assertArrayEquals(hdlr, payload)
    }

    @Test
    fun `encodePayload concatenates children in canonical order`() {
        val payload = MetaBox.encodePayload(
            handlerBox = hdlr,
            primaryItemBox = pitm,
            itemInfoBox = iinf,
            itemLocationBox = iloc,
        )
        // payload = hdlr || pitm || iinf || iloc
        val expected = hdlr + pitm + iinf + iloc
        assertArrayEquals(expected, payload)
    }

    @Test
    fun `encodePayload size equals sum of children`() {
        val payload = MetaBox.encodePayload(
            handlerBox = hdlr,
            primaryItemBox = pitm,
            itemInfoBox = iinf,
            itemLocationBox = iloc,
        )
        assertEquals(hdlr.size + pitm.size + iinf.size + iloc.size, payload.size)
    }

    @Test
    fun `encodePayload omits null children`() {
        val payload = MetaBox.encodePayload(
            handlerBox = hdlr,
            primaryItemBox = pitm,
            itemInfoBox = null,
            itemLocationBox = iloc,
        )
        assertArrayEquals(hdlr + pitm + iloc, payload)
    }

    // ------------------------------------------------------------------
    // encodePayloadOrdered
    // ------------------------------------------------------------------

    @Test
    fun `encodePayloadOrdered rejects empty list`() {
        assertThrows(IllegalArgumentException::class.java) {
            MetaBox.encodePayloadOrdered(emptyList())
        }
    }

    @Test
    fun `encodePayloadOrdered rejects too-short child`() {
        val tooShort = ByteArray(7)
        assertThrows(IllegalArgumentException::class.java) {
            MetaBox.encodePayloadOrdered(listOf(tooShort))
        }
    }

    @Test
    fun `encodePayloadOrdered rejects non-hdlr first child`() {
        // Putting pitm first (where hdlr must go).
        assertThrows(IllegalArgumentException::class.java) {
            MetaBox.encodePayloadOrdered(listOf(pitm, hdlr))
        }
    }

    @Test
    fun `encodePayloadOrdered rejects duplicate child type`() {
        // Two pitm children should be rejected.
        assertThrows(IllegalArgumentException::class.java) {
            MetaBox.encodePayloadOrdered(listOf(hdlr, pitm, pitm))
        }
    }

    @Test
    fun `encodePayloadOrdered preserves caller order`() {
        val custom = listOf(hdlr, iloc, pitm, iinf) // intentionally non-canonical order
        val payload = MetaBox.encodePayloadOrdered(custom)
        assertArrayEquals(hdlr + iloc + pitm + iinf, payload)
    }

    // ------------------------------------------------------------------
    // encodeBox
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox of hdlr-only meta box has the canonical envelope`() {
        val box = MetaBox.encodeBox(handlerBox = hdlr)
        // header(8) + version+flags(4) + hdlr(33) = 45 bytes
        assertEquals(45, box.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 45), box.copyOfRange(0, 4))
        assertArrayEquals("meta".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), box.copyOfRange(8, 12)) // version=0 flags=0
        assertArrayEquals(hdlr, box.copyOfRange(12, 45))
    }

    @Test
    fun `encodeBox canonical AVIF still composes hdlr+pitm+iinf+iloc`() {
        val box = MetaBox.encodeBox(
            handlerBox = hdlr,
            primaryItemBox = pitm,
            itemInfoBox = iinf,
            itemLocationBox = iloc,
        )
        // header(8) + v+f(4) + hdlr(33) + pitm(14) + iinf(...) + iloc(30)
        val expectedSize = 8 + 4 + hdlr.size + pitm.size + iinf.size + iloc.size
        assertEquals(expectedSize, box.size)
        // size BE
        val sizeBytes = box.copyOfRange(0, 4)
        val size = ((sizeBytes[0].toInt() and 0xFF) shl 24) or
            ((sizeBytes[1].toInt() and 0xFF) shl 16) or
            ((sizeBytes[2].toInt() and 0xFF) shl 8) or
            (sizeBytes[3].toInt() and 0xFF)
        assertEquals(expectedSize, size)
        assertArrayEquals("meta".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
    }

    @Test
    fun `encodeBox returns a value distinct from the payload`() {
        val payload = MetaBox.encodePayload(handlerBox = hdlr)
        val box = MetaBox.encodeBox(handlerBox = hdlr)
        assertNotEquals(payload.toList(), box.toList())
        // box prefixes a 12-byte FullBox header
        assertEquals(payload.size + 12, box.size)
    }

    @Test
    fun `encodeBox rejects null handler via type system`() {
        // This is a compile-time guarantee in Kotlin (handlerBox: ByteArray
        // is non-null), so the test instead exercises the runtime path
        // via Builder.build().
        val builder = MetaBox.Builder()
        assertThrows(IllegalStateException::class.java) {
            builder.build()
        }
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    @Test
    fun `Builder requires handler before build`() {
        val b = MetaBox.Builder()
        assertThrows(IllegalStateException::class.java) {
            b.build()
        }
    }

    @Test
    fun `Builder build with only handler matches encodeBox`() {
        val viaBuilder = MetaBox.Builder()
            .setHandler(hdlr)
            .build()
        val viaEncodeBox = MetaBox.encodeBox(handlerBox = hdlr)
        assertArrayEquals(viaEncodeBox, viaBuilder)
    }

    @Test
    fun `Builder build with full canonical AVIF still matches encodeBox`() {
        val viaBuilder = MetaBox.Builder()
            .setHandler(hdlr)
            .setPrimaryItem(pitm)
            .setItemInfo(iinf)
            .setItemLocation(iloc)
            .build()
        val viaEncodeBox = MetaBox.encodeBox(
            handlerBox = hdlr,
            primaryItemBox = pitm,
            itemInfoBox = iinf,
            itemLocationBox = iloc,
        )
        assertArrayEquals(viaEncodeBox, viaBuilder)
    }

    @Test
    fun `Builder defensively copies inputs`() {
        val mutable = hdlr.copyOf()
        val b = MetaBox.Builder().setHandler(mutable)
        // Mutate the original after handing it to the builder.
        mutable[0] = 0xFF.toByte()
        val box = b.build()
        // The encoded meta box should still reflect the *original* hdlr
        // bytes, not the post-mutation state.
        val viaEncodeBox = MetaBox.encodeBox(handlerBox = hdlr)
        assertArrayEquals(viaEncodeBox, box)
    }

    @Test
    fun `Builder is fluent`() {
        val b = MetaBox.Builder()
        assertTrue(b.setHandler(hdlr) === b)
        assertTrue(b.setPrimaryItem(pitm) === b)
        assertTrue(b.setItemInfo(iinf) === b)
        assertTrue(b.setItemLocation(iloc) === b)
    }

    // ------------------------------------------------------------------
    // End-to-end AVIF muxer integration
    // ------------------------------------------------------------------

    @Test
    fun `end-to-end full AVIF still meta box round-trips through every R22-R28 module`() {
        // Build the iprp + ipma chain (Rounds 23-24).
        val colrPayload = AvifColrPayload.encodeNclxPayload(WorkingSpace.SRGB.cicp)
        val colrBox = IsobmffBox.encodeBox("colr", colrPayload)
        val pixiBox = IsobmffBox.encodeFullBox(
            type = "pixi",
            version = 0,
            flags = 0,
            payload = AvifAuxiliaryBoxes.encodePixi(AvifAuxiliaryBoxes.PixiPayload.RGB_8),
        )
        val builder = IsobmffItemProperties.Builder()
        val colrIdx = builder.add(colrBox)
        val pixiIdx = builder.add(pixiBox)
        val ipco = builder.build()
        val ipma = ItemPropertyAssociation.encodeBox(
            listOf(
                ItemPropertyAssociation.Entry(
                    itemId = 1L,
                    associations = listOf(
                        ItemPropertyAssociation.Association(propertyIndex = colrIdx, essential = true),
                        ItemPropertyAssociation.Association(propertyIndex = pixiIdx, essential = true),
                    ),
                ),
            ),
        )
        val iprp = IsobmffItemProperties.encodeIprpBox(ipco, listOf(ipma))

        // Glue every module together via MetaBox.
        val meta = MetaBox.Builder()
            .setHandler(hdlr)
            .setPrimaryItem(pitm)
            .setItemInfo(iinf)
            .setItemLocation(iloc)
            .setItemProperties(iprp)
            .build()

        // Every byte of the meta box should be exactly the FullBox
        // header + the concatenation of the 5 children.
        val expectedPayload = hdlr + pitm + iinf + iloc + iprp
        val expectedSize = 8 + 4 + expectedPayload.size
        assertEquals(expectedSize, meta.size)
        assertArrayEquals("meta".toByteArray(Charsets.US_ASCII), meta.copyOfRange(4, 8))
        assertArrayEquals(expectedPayload, meta.copyOfRange(12, meta.size))
    }
}
