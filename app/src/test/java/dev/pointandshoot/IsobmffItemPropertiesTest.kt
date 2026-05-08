package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [IsobmffItemProperties].
 *
 * Pinned facts (per ISO/IEC 14496-12 §8.11.14):
 *
 *  * Both `ipco` and `iprp` are plain boxes — wire format is just
 *    `size + type + payload` (no version/flags).
 *  * `ipco` payload is an ordered concatenation of property child
 *    boxes; order matters because `ipma` references properties by
 *    1-based position.
 *  * `iprp` payload is exactly one `ipco` followed by 1+ `ipma`
 *    boxes.
 */
class IsobmffItemPropertiesTest {

    @Test
    fun `box type pins`() {
        assertEquals("ipco", IsobmffItemProperties.IPCO_BOX_TYPE)
        assertEquals("iprp", IsobmffItemProperties.IPRP_BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, IsobmffItemProperties.SCHEMA_VERSION)
    }

    @Test
    fun `MIN_ENCODED_BOX_SIZE matches IsobmffBox PLAIN_HEADER_SIZE`() {
        assertEquals(IsobmffBox.PLAIN_HEADER_SIZE, IsobmffItemProperties.MIN_ENCODED_BOX_SIZE)
    }

    // ------------------------------------------------------------------
    // encodeIpcoBox
    // ------------------------------------------------------------------

    @Test
    fun `encodeIpcoBox with empty properties produces 8-byte header-only box`() {
        val box = IsobmffItemProperties.encodeIpcoBox(emptyList())
        assertEquals(8, box.size)
        // size = 8
        assertArrayEquals(byteArrayOf(0, 0, 0, 8), box.copyOfRange(0, 4))
        // type = "ipco"
        assertArrayEquals("ipco".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
    }

    @Test
    fun `encodeIpcoBox with single colr property has documented byte layout`() {
        val colrBox = IsobmffBox.encodeBox(
            "colr",
            AvifColrPayload.encodeNclxPayload(WorkingSpace.SRGB.cicp),
        )
        // colr box = 8-byte header + 11-byte nclx payload = 19 bytes
        assertEquals(19, colrBox.size)
        val ipco = IsobmffItemProperties.encodeIpcoBox(listOf(colrBox))
        // ipco box = 8-byte header + 19-byte colr child = 27 bytes
        assertEquals(27, ipco.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 27), ipco.copyOfRange(0, 4))
        assertArrayEquals("ipco".toByteArray(Charsets.US_ASCII), ipco.copyOfRange(4, 8))
        // Inner box should match colrBox exactly
        assertArrayEquals(colrBox, ipco.copyOfRange(8, 27))
    }

    @Test
    fun `encodeIpcoBox preserves property order`() {
        val pasp = IsobmffBox.encodeBox(
            "pasp",
            IsobmffSampleAspect.encodePasp(IsobmffSampleAspect.PaspPayload.SQUARE),
        )
        val irot = IsobmffBox.encodeBox(
            "irot",
            byteArrayOf(AvifAuxiliaryBoxes.encodeIrot(AvifAuxiliaryBoxes.Rotation.Rot0).single()),
        )
        val ordered = IsobmffItemProperties.encodeIpcoBox(listOf(pasp, irot))
        val reversed = IsobmffItemProperties.encodeIpcoBox(listOf(irot, pasp))
        assertNotEquals(
            "ipco encoding must depend on property order",
            ordered.toList(),
            reversed.toList(),
        )
        // ordered: header + pasp + irot
        assertArrayEquals(pasp, ordered.copyOfRange(8, 8 + pasp.size))
        assertArrayEquals(irot, ordered.copyOfRange(8 + pasp.size, ordered.size))
    }

    @Test
    fun `encodeIpcoBox rejects properties shorter than 8 bytes`() {
        val tooShort = byteArrayOf(0, 0, 0, 4)
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffItemProperties.encodeIpcoBox(listOf(tooShort))
        }
    }

    @Test
    fun `encodeIpcoBox accepts a property with exactly 8 bytes (header-only box)`() {
        val emptyBox = IsobmffBox.encodeBox("free", ByteArray(0))
        assertEquals(8, emptyBox.size)
        val ipco = IsobmffItemProperties.encodeIpcoBox(listOf(emptyBox))
        assertEquals(8 + 8, ipco.size)
    }

    // ------------------------------------------------------------------
    // encodeIprpBox
    // ------------------------------------------------------------------

    @Test
    fun `encodeIprpBox with one ipco and one ipma has documented byte layout`() {
        val colrBox = IsobmffBox.encodeBox(
            "colr",
            AvifColrPayload.encodeNclxPayload(WorkingSpace.SRGB.cicp),
        )
        val ipco = IsobmffItemProperties.encodeIpcoBox(listOf(colrBox))
        val ipma = ItemPropertyAssociation.encodeBox(
            listOf(
                ItemPropertyAssociation.Entry(
                    itemId = 1L,
                    associations = listOf(ItemPropertyAssociation.Association(1, true)),
                ),
            ),
        )
        val iprp = IsobmffItemProperties.encodeIprpBox(ipco, listOf(ipma))
        // iprp box = 8-byte header + ipco + ipma
        assertEquals(8 + ipco.size + ipma.size, iprp.size)
        assertArrayEquals("iprp".toByteArray(Charsets.US_ASCII), iprp.copyOfRange(4, 8))
        assertArrayEquals(ipco, iprp.copyOfRange(8, 8 + ipco.size))
        assertArrayEquals(ipma, iprp.copyOfRange(8 + ipco.size, iprp.size))
    }

    @Test
    fun `encodeIprpBox concatenates multiple ipma boxes after ipco`() {
        val ipco = IsobmffItemProperties.encodeIpcoBox(emptyList())
        val ipmaA = IsobmffBox.encodeFullBox("ipma", 0, 0, byteArrayOf(0, 0, 0, 0))
        val ipmaB = IsobmffBox.encodeFullBox("ipma", 1, 0, byteArrayOf(0, 0, 0, 0))
        val iprp = IsobmffItemProperties.encodeIprpBox(ipco, listOf(ipmaA, ipmaB))
        assertEquals(8 + ipco.size + ipmaA.size + ipmaB.size, iprp.size)
        // ipma boxes follow ipco in order
        assertArrayEquals(ipmaA, iprp.copyOfRange(8 + ipco.size, 8 + ipco.size + ipmaA.size))
        assertArrayEquals(ipmaB, iprp.copyOfRange(8 + ipco.size + ipmaA.size, iprp.size))
    }

    @Test
    fun `encodeIprpBox rejects empty ipmaBoxes`() {
        val ipco = IsobmffItemProperties.encodeIpcoBox(emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffItemProperties.encodeIprpBox(ipco, emptyList())
        }
    }

    @Test
    fun `encodeIprpBox rejects too-short ipco`() {
        val tooShort = byteArrayOf(0, 0, 0, 4)
        val ipma = IsobmffBox.encodeFullBox("ipma", 0, 0, byteArrayOf(0, 0, 0, 0))
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffItemProperties.encodeIprpBox(tooShort, listOf(ipma))
        }
    }

    @Test
    fun `encodeIprpBox rejects too-short ipma`() {
        val ipco = IsobmffItemProperties.encodeIpcoBox(emptyList())
        val tooShort = byteArrayOf(0, 0, 0, 4)
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffItemProperties.encodeIprpBox(ipco, listOf(tooShort))
        }
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    @Test
    fun `Builder add returns 1-based indices in append order`() {
        val builder = IsobmffItemProperties.Builder()
        val colrIdx = builder.add(
            IsobmffBox.encodeBox(
                "colr",
                AvifColrPayload.encodeNclxPayload(WorkingSpace.SRGB.cicp),
            ),
        )
        val pixiIdx = builder.add(
            IsobmffBox.encodeBox(
                "pixi",
                AvifAuxiliaryBoxes.encodePixi(AvifAuxiliaryBoxes.PixiPayload.RGB_8),
            ),
        )
        val irotIdx = builder.add(
            IsobmffBox.encodeBox(
                "irot",
                byteArrayOf(AvifAuxiliaryBoxes.encodeIrot(AvifAuxiliaryBoxes.Rotation.Rot0).single()),
            ),
        )
        assertEquals(1, colrIdx)
        assertEquals(2, pixiIdx)
        assertEquals(3, irotIdx)
        assertEquals(3, builder.size())
    }

    @Test
    fun `Builder size returns 0 before any add`() {
        val builder = IsobmffItemProperties.Builder()
        assertEquals(0, builder.size())
    }

    @Test
    fun `Builder add rejects too-short encoded box`() {
        val builder = IsobmffItemProperties.Builder()
        assertThrows(IllegalArgumentException::class.java) {
            builder.add(byteArrayOf(0, 0, 0, 4))
        }
    }

    @Test
    fun `Builder defensively copies added boxes`() {
        val builder = IsobmffItemProperties.Builder()
        val colrBox = IsobmffBox.encodeBox(
            "colr",
            AvifColrPayload.encodeNclxPayload(WorkingSpace.SRGB.cicp),
        )
        val original = colrBox.copyOf()
        builder.add(colrBox)
        // Mutate the source buffer
        colrBox[0] = 0xFF.toByte()
        colrBox[1] = 0xFF.toByte()
        val ipco = builder.build()
        // The built ipco should still contain the original colr bytes (offset by the 8-byte ipco header)
        assertArrayEquals(original, ipco.copyOfRange(8, 8 + original.size))
    }

    @Test
    fun `Builder build matches encodeIpcoBox of the same properties`() {
        val colrBox = IsobmffBox.encodeBox(
            "colr",
            AvifColrPayload.encodeNclxPayload(WorkingSpace.SRGB.cicp),
        )
        val pixiBox = IsobmffBox.encodeBox(
            "pixi",
            AvifAuxiliaryBoxes.encodePixi(AvifAuxiliaryBoxes.PixiPayload.RGB_8),
        )
        val builder = IsobmffItemProperties.Builder()
        builder.add(colrBox)
        builder.add(pixiBox)
        val viaBuilder = builder.build()
        val viaFunction = IsobmffItemProperties.encodeIpcoBox(listOf(colrBox, pixiBox))
        assertArrayEquals(viaFunction, viaBuilder)
    }

    // ------------------------------------------------------------------
    // End-to-end AVIF property bundle integration
    // ------------------------------------------------------------------

    @Test
    fun `end-to-end canonical AVIF still produces a self-consistent iprp`() {
        // Step 1: build the property catalog (ipco) in spec order.
        val ipco = IsobmffItemProperties.Builder()
        val colrIdx = ipco.add(
            IsobmffBox.encodeBox(
                "colr",
                AvifColrPayload.encodeNclxPayload(WorkingSpace.SRGB.cicp),
            ),
        )
        val pixiIdx = ipco.add(
            IsobmffBox.encodeBox(
                "pixi",
                AvifAuxiliaryBoxes.encodePixi(AvifAuxiliaryBoxes.PixiPayload.RGB_8),
            ),
        )
        val paspIdx = ipco.add(
            IsobmffBox.encodeBox(
                "pasp",
                IsobmffSampleAspect.encodePasp(IsobmffSampleAspect.PaspPayload.SQUARE),
            ),
        )
        val ipcoBox = ipco.build()

        // Step 2: build the ipma association table referencing the indices.
        val ipmaBox = ItemPropertyAssociation.encodeBox(
            listOf(
                ItemPropertyAssociation.Entry(
                    itemId = 1L,
                    associations = listOf(
                        ItemPropertyAssociation.Association(propertyIndex = colrIdx, essential = true),
                        ItemPropertyAssociation.Association(propertyIndex = pixiIdx, essential = true),
                        ItemPropertyAssociation.Association(propertyIndex = paspIdx, essential = false),
                    ),
                ),
            ),
        )

        // Step 3: wrap in iprp.
        val iprpBox = IsobmffItemProperties.encodeIprpBox(ipcoBox, listOf(ipmaBox))

        // The iprp box must be exactly the sum of its parts plus its own 8-byte header
        assertEquals(8 + ipcoBox.size + ipmaBox.size, iprpBox.size)
        assertArrayEquals("iprp".toByteArray(Charsets.US_ASCII), iprpBox.copyOfRange(4, 8))

        // Decoding the ipma back must yield the original property indices in stable order
        // ipma box layout: 8-byte header + 4-byte version+flags + payload
        val ipmaPayload = ipmaBox.copyOfRange(12, ipmaBox.size)
        val decoded = ItemPropertyAssociation.decodePayload(ipmaPayload, version = 0, flags = 0)
        assertEquals(1, decoded.size)
        val entry = decoded.first()
        assertEquals(1L, entry.itemId)
        assertEquals(listOf(colrIdx, pixiIdx, paspIdx), entry.associations.map { it.propertyIndex })
        assertEquals(listOf(true, true, false), entry.associations.map { it.essential })
    }
}
