package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit tests for [AvifStillMuxer].
 *
 * The muxer is the integration round (Round 32) that ties
 * together every host-side AVIF primitive shipped in
 * Rounds 17 - 31. These tests verify:
 *
 *  * `Input` validation rejects malformed inputs.
 *  * The output starts with `ftyp` then `meta` then `mdat` per
 *    AVIF spec § 4.
 *  * The `ftyp` box matches `FileTypeBox.encodeAvifStillBox()`
 *    byte-for-byte.
 *  * The `mdat` box header sits at exactly the offset claimed by
 *    `iloc`, and the offset claimed by `iloc` plus the AV1
 *    bitstream length lands at the end of `mdat`.
 *  * The optional auxiliary properties (rotation, mirror, pasp,
 *    clap, mdcv, clli) surface in `ipco` only when set and grow
 *    the file by the expected amount.
 *  * The convenience `encodeSrgbStill(...)` is byte-exact equal
 *    to the explicit `encode(Input(...))` call.
 */
class AvifStillMuxerTest {

    private val tinyAv1 = byteArrayOf(0x12, 0x00, 0x0A, 0x05, 0xFF.toByte())

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, AvifStillMuxer.SCHEMA_VERSION)
    }

    @Test
    fun `PRIMARY_ITEM_ID pin`() {
        assertEquals(1, AvifStillMuxer.PRIMARY_ITEM_ID)
    }

    @Test
    fun `BIT_DEPTHS_RGB_8 pin`() {
        assertArrayEquals(intArrayOf(8, 8, 8), AvifStillMuxer.BIT_DEPTHS_RGB_8)
    }

    @Test
    fun `BIT_DEPTHS_RGB_10 pin`() {
        assertArrayEquals(intArrayOf(10, 10, 10), AvifStillMuxer.BIT_DEPTHS_RGB_10)
    }

    @Test
    fun `BIT_DEPTHS_RGB_12 pin`() {
        assertArrayEquals(intArrayOf(12, 12, 12), AvifStillMuxer.BIT_DEPTHS_RGB_12)
    }

    @Test
    fun `BIT_DEPTHS_MONO_8 pin`() {
        assertArrayEquals(intArrayOf(8), AvifStillMuxer.BIT_DEPTHS_MONO_8)
    }

    // ------------------------------------------------------------------
    // Input validation
    // ------------------------------------------------------------------

    @Test
    fun `Input rejects zero width`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifStillMuxer.Input(
                widthPx = 0,
                heightPx = 100,
                bitDepths = intArrayOf(8, 8, 8),
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
            )
        }
    }

    @Test
    fun `Input rejects zero height`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifStillMuxer.Input(
                widthPx = 100,
                heightPx = 0,
                bitDepths = intArrayOf(8, 8, 8),
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
            )
        }
    }

    @Test
    fun `Input rejects empty bitDepths`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifStillMuxer.Input(
                widthPx = 100,
                heightPx = 100,
                bitDepths = intArrayOf(),
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
            )
        }
    }

    @Test
    fun `Input rejects empty av1Bitstream`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifStillMuxer.Input(
                widthPx = 100,
                heightPx = 100,
                bitDepths = intArrayOf(8, 8, 8),
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = ByteArray(0),
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
            )
        }
    }

    @Test
    fun `Input rejects empty exifPayload when set`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifStillMuxer.Input(
                widthPx = 100,
                heightPx = 100,
                bitDepths = intArrayOf(8, 8, 8),
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
                exifPayload = ByteArray(0),
            )
        }
    }

    @Test
    fun `EXIF_ITEM_ID pin is 2 (distinct from PRIMARY_ITEM_ID)`() {
        assertEquals(2, AvifStillMuxer.EXIF_ITEM_ID)
        assertNotEquals(AvifStillMuxer.PRIMARY_ITEM_ID, AvifStillMuxer.EXIF_ITEM_ID)
    }

    @Test
    fun `EXIF_TIFF_HEADER_OFFSET_PREFIX_SIZE pin is 4`() {
        assertEquals(4, AvifStillMuxer.EXIF_TIFF_HEADER_OFFSET_PREFIX_SIZE)
    }

    // ------------------------------------------------------------------
    // Top-level structure: ftyp then meta then mdat
    // ------------------------------------------------------------------

    @Test
    fun `encode produces ftyp then meta then mdat top-level structure`() {
        val out = AvifStillMuxer.encode(canonicalInput())
        val ftypType = String(out.copyOfRange(4, 8), Charsets.US_ASCII)
        assertEquals("ftyp", ftypType)

        val ftypSize = readBoxSize(out, 0)
        val metaType = String(out.copyOfRange(ftypSize + 4, ftypSize + 8), Charsets.US_ASCII)
        assertEquals("meta", metaType)

        val metaSize = readBoxSize(out, ftypSize)
        val mdatType = String(
            out.copyOfRange(ftypSize + metaSize + 4, ftypSize + metaSize + 8),
            Charsets.US_ASCII,
        )
        assertEquals("mdat", mdatType)
    }

    @Test
    fun `encode prepends canonical AVIF ftyp box`() {
        val out = AvifStillMuxer.encode(canonicalInput())
        val expectedFtyp = FileTypeBox.encodeAvifStillBox()
        assertArrayEquals(expectedFtyp, out.copyOfRange(0, expectedFtyp.size))
    }

    @Test
    fun `encode total size equals ftyp plus meta plus mdat`() {
        val out = AvifStillMuxer.encode(canonicalInput())
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatSize = readBoxSize(out, ftypSize + metaSize)
        assertEquals(ftypSize + metaSize + mdatSize, out.size)
    }

    @Test
    fun `mdat box ends at end of file (no trailing bytes)`() {
        val out = AvifStillMuxer.encode(canonicalInput())
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatSize = readBoxSize(out, mdatStart)
        assertEquals(out.size, mdatStart + mdatSize)
    }

    @Test
    fun `mdat payload is the AV1 bitstream`() {
        val input = canonicalInput()
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatHeaderSize = MediaDataBox.headerSize(input.av1Bitstream.size.toLong())
        val payload = out.copyOfRange(mdatStart + mdatHeaderSize, out.size)
        assertArrayEquals(input.av1Bitstream, payload)
    }

    // ------------------------------------------------------------------
    // ipco property bundle (counts + ordering)
    // ------------------------------------------------------------------

    @Test
    fun `ipco contains exactly 4 mandatory properties when no auxiliaries set`() {
        val out = AvifStillMuxer.encode(canonicalInput())
        val ipco = findBoxPayload(out, "ipco")
        val children = splitChildBoxes(ipco)
        assertEquals(4, children.size)
        assertEquals("ispe", boxType(children[0]))
        assertEquals("pixi", boxType(children[1]))
        assertEquals("colr", boxType(children[2]))
        assertEquals("av1C", boxType(children[3]))
    }

    @Test
    fun `ipco appends rotation property when rotation set`() {
        val out = AvifStillMuxer.encode(
            canonicalInput().copy(rotation = AvifAuxiliaryBoxes.Rotation.Rot90),
        )
        val ipco = findBoxPayload(out, "ipco")
        val children = splitChildBoxes(ipco)
        assertEquals(5, children.size)
        assertEquals("irot", boxType(children[4]))
    }

    @Test
    fun `ipco appends mirror property when mirror set`() {
        val out = AvifStillMuxer.encode(
            canonicalInput().copy(mirror = AvifAuxiliaryBoxes.MirrorAxis.Vertical),
        )
        val ipco = findBoxPayload(out, "ipco")
        val children = splitChildBoxes(ipco)
        assertEquals(5, children.size)
        assertEquals("imir", boxType(children[4]))
    }

    @Test
    fun `ipco appends pasp property when pasp set`() {
        val out = AvifStillMuxer.encode(
            canonicalInput().copy(pasp = IsobmffSampleAspect.PaspPayload.SQUARE),
        )
        val ipco = findBoxPayload(out, "ipco")
        val children = splitChildBoxes(ipco)
        assertEquals(5, children.size)
        assertEquals("pasp", boxType(children[4]))
    }

    @Test
    fun `ipco appends mdcv and clli when both set`() {
        val out = AvifStillMuxer.encode(
            canonicalInput().copy(
                mdcv = MasteringDisplayMetadata.REC2020_1000_NITS,
                clli = ContentLightLevel(maxCll = 1000, maxFall = 400),
            ),
        )
        val ipco = findBoxPayload(out, "ipco")
        val children = splitChildBoxes(ipco)
        assertEquals(6, children.size)
        assertEquals("mdcv", boxType(children[4]))
        assertEquals("clli", boxType(children[5]))
    }

    @Test
    fun `ipco contains all 10 properties when every auxiliary is set`() {
        val out = AvifStillMuxer.encode(
            canonicalInput().copy(
                rotation = AvifAuxiliaryBoxes.Rotation.Rot180,
                mirror = AvifAuxiliaryBoxes.MirrorAxis.Horizontal,
                pasp = IsobmffSampleAspect.PaspPayload(hSpacing = 4, vSpacing = 3),
                clap = IsobmffSampleAspect.ClapPayload.centeredCropOf(
                    codedWidth = 4096,
                    codedHeight = 3072,
                    cropX = 0,
                    cropY = 0,
                    cropW = 4096,
                    cropH = 3072,
                ),
                mdcv = MasteringDisplayMetadata.REC2020_1000_NITS,
                clli = ContentLightLevel(maxCll = 1000, maxFall = 400),
            ),
        )
        val ipco = findBoxPayload(out, "ipco")
        val children = splitChildBoxes(ipco)
        val types = children.map { boxType(it) }
        assertEquals(
            listOf("ispe", "pixi", "colr", "av1C", "irot", "imir", "pasp", "clap", "mdcv", "clli"),
            types,
        )
    }

    @Test
    fun `ipco av1C box round-trips back to the input config`() {
        val out = AvifStillMuxer.encode(canonicalInput())
        val ipco = findBoxPayload(out, "ipco")
        val av1cBox = splitChildBoxes(ipco).first { boxType(it) == "av1C" }
        // Strip the 8-byte plain header to get just the av1C payload.
        val payload = av1cBox.copyOfRange(8, av1cBox.size)
        val decoded = Av1CodecConfiguration.decodePayload(payload)
        assertEquals(Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420, decoded)
    }

    // ------------------------------------------------------------------
    // iloc offset cross-check
    // ------------------------------------------------------------------

    @Test
    fun `iloc offset matches the actual av1 byte position in the file`() {
        val input = canonicalInput()
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatHeaderSize = MediaDataBox.headerSize(input.av1Bitstream.size.toLong())
        val expectedAv1Offset = (mdatStart + mdatHeaderSize).toLong()
        val ilocOffset = readIlocPrimaryOffset(out)
        assertEquals(expectedAv1Offset, ilocOffset)

        val claimedRange = out.copyOfRange(
            ilocOffset.toInt(),
            ilocOffset.toInt() + input.av1Bitstream.size,
        )
        assertArrayEquals(input.av1Bitstream, claimedRange)
    }

    // ------------------------------------------------------------------
    // Different inputs produce different outputs
    // ------------------------------------------------------------------

    @Test
    fun `different widths produce different outputs`() {
        val a = AvifStillMuxer.encode(canonicalInput().copy(widthPx = 1920))
        val b = AvifStillMuxer.encode(canonicalInput().copy(widthPx = 4096))
        assertNotEquals(a.size, b.size.let { -1 })
        assertTrue("outputs should differ when widths differ", !a.contentEquals(b))
    }

    @Test
    fun `different bitstreams produce different outputs`() {
        val a = AvifStillMuxer.encode(
            canonicalInput().copy(av1Bitstream = byteArrayOf(0x01)),
        )
        val b = AvifStillMuxer.encode(
            canonicalInput().copy(av1Bitstream = byteArrayOf(0x02, 0x03)),
        )
        assertTrue("outputs should differ when bitstreams differ", !a.contentEquals(b))
    }

    // ------------------------------------------------------------------
    // Convenience: encodeSrgbStill
    // ------------------------------------------------------------------

    @Test
    fun `encodeSrgbStill matches explicit Input call`() {
        val explicit = AvifStillMuxer.encode(
            AvifStillMuxer.Input(
                widthPx = 4096,
                heightPx = 3072,
                bitDepths = AvifStillMuxer.BIT_DEPTHS_RGB_8,
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
            ),
        )
        val convenient = AvifStillMuxer.encodeSrgbStill(4096, 3072, tinyAv1)
        assertArrayEquals(explicit, convenient)
    }

    // ------------------------------------------------------------------
    // HDR10 (10-bit Rec.2020 PQ) full integration smoke
    // ------------------------------------------------------------------

    @Test
    fun `HDR10 input produces a complete file with mdcv and clli`() {
        val out = AvifStillMuxer.encode(
            AvifStillMuxer.Input(
                widthPx = 3840,
                heightPx = 2160,
                bitDepths = AvifStillMuxer.BIT_DEPTHS_RGB_10,
                cicp = WorkingSpace.REC2020_PQ.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_10BIT_YUV420,
                mdcv = MasteringDisplayMetadata.REC2020_1000_NITS,
                clli = ContentLightLevel(maxCll = 1000, maxFall = 400),
            ),
        )
        // ftyp / meta / mdat sequence check
        assertEquals("ftyp", String(out.copyOfRange(4, 8), Charsets.US_ASCII))
        val ftypSize = readBoxSize(out, 0)
        assertEquals("meta", String(out.copyOfRange(ftypSize + 4, ftypSize + 8), Charsets.US_ASCII))

        // mdcv + clli appear in ipco
        val ipco = findBoxPayload(out, "ipco")
        val types = splitChildBoxes(ipco).map { boxType(it) }
        assertTrue("mdcv must appear in HDR ipco", types.contains("mdcv"))
        assertTrue("clli must appear in HDR ipco", types.contains("clli"))
    }

    // ------------------------------------------------------------------
    // EXIF item integration (Round 35)
    // ------------------------------------------------------------------

    /**
     * Minimal but valid little-endian TIFF block: byte order
     * marker `II`, magic 42, IFD0 offset at byte 8, then a
     * single IFD entry (count = 1) with tag 0x010E (`ImageDescription`),
     * type 2 (ASCII), count 5 ("PNS\0"), value "PNS\0" packed inline,
     * then the IFD terminator (next IFD offset = 0).
     */
    private val tinyExif: ByteArray = byteArrayOf(
        // TIFF header (II*\0 + IFD0 offset = 8)
        'I'.code.toByte(), 'I'.code.toByte(),
        0x2A, 0x00,
        0x08, 0x00, 0x00, 0x00,
        // IFD0: 1 entry
        0x01, 0x00,
        // Entry: tag 0x010E (ImageDescription), type 2 (ASCII), count = 4
        0x0E, 0x01,
        0x02, 0x00,
        0x04, 0x00, 0x00, 0x00,
        // Inline 4-byte value "PNS\0"
        'P'.code.toByte(), 'N'.code.toByte(), 'S'.code.toByte(), 0x00,
        // Next-IFD offset = 0
        0x00, 0x00, 0x00, 0x00,
    )

    @Test
    fun `exif null produces a single iinf entry for av01`() {
        val out = AvifStillMuxer.encode(canonicalInput())
        val iinfPayload = findBoxPayload(out, "iinf")
        // iinf is a FullBox; payload starts with 4 bytes version+flags,
        // then either uint16 or uint32 entry_count. Pick by version.
        val version = iinfPayload[0].toInt() and 0xFF
        val entryCount = if (version == 0) {
            ((iinfPayload[4].toInt() and 0xFF) shl 8) or (iinfPayload[5].toInt() and 0xFF)
        } else {
            ((iinfPayload[4].toInt() and 0xFF) shl 24) or
                ((iinfPayload[5].toInt() and 0xFF) shl 16) or
                ((iinfPayload[6].toInt() and 0xFF) shl 8) or
                (iinfPayload[7].toInt() and 0xFF)
        }
        assertEquals(1, entryCount)
    }

    @Test
    fun `exif null does not emit iref box`() {
        val out = AvifStillMuxer.encode(canonicalInput())
        // iref must not appear anywhere in the file when no EXIF.
        assertEquals(-1, findBoxOffset(out, "iref"))
    }

    @Test
    fun `exif set produces two iinf entries (av01 then Exif)`() {
        val out = AvifStillMuxer.encode(canonicalInput().copy(exifPayload = tinyExif))
        val iinfPayload = findBoxPayload(out, "iinf")
        val version = iinfPayload[0].toInt() and 0xFF
        val entryCount = if (version == 0) {
            ((iinfPayload[4].toInt() and 0xFF) shl 8) or (iinfPayload[5].toInt() and 0xFF)
        } else {
            ((iinfPayload[4].toInt() and 0xFF) shl 24) or
                ((iinfPayload[5].toInt() and 0xFF) shl 16) or
                ((iinfPayload[6].toInt() and 0xFF) shl 8) or
                (iinfPayload[7].toInt() and 0xFF)
        }
        assertEquals(2, entryCount)
        // Second infe should declare itemType="Exif" — scan for the
        // 4-byte ASCII anywhere after the first infe.
        val asString = String(iinfPayload, Charsets.US_ASCII)
        assertTrue("iinf payload must contain 'av01'", asString.contains("av01"))
        assertTrue("iinf payload must contain 'Exif'", asString.contains("Exif"))
    }

    @Test
    fun `exif set emits iref cdsc reference from Exif item to primary`() {
        val out = AvifStillMuxer.encode(canonicalInput().copy(exifPayload = tinyExif))
        val irefPayload = findBoxPayload(out, "iref")
        // iref is a FullBox; first 4 bytes are version+flags. Then a
        // sequence of SingleItemTypeReferenceBoxes. For canonical
        // small itemIds, version=0 (16-bit). The first sub-box is at
        // offset 4 in irefPayload.
        val subBoxStart = 4
        val subBoxSize = ((irefPayload[subBoxStart].toInt() and 0xFF) shl 24) or
            ((irefPayload[subBoxStart + 1].toInt() and 0xFF) shl 16) or
            ((irefPayload[subBoxStart + 2].toInt() and 0xFF) shl 8) or
            (irefPayload[subBoxStart + 3].toInt() and 0xFF)
        val subBoxType = String(
            irefPayload.copyOfRange(subBoxStart + 4, subBoxStart + 8),
            Charsets.US_ASCII,
        )
        assertEquals("cdsc", subBoxType)
        // SingleItemTypeReferenceBox v=0 body:
        //   uint16 from_item_ID (= EXIF_ITEM_ID = 2)
        //   uint16 reference_count (= 1)
        //   uint16 to_item_ID (= PRIMARY_ITEM_ID = 1)
        val bodyStart = subBoxStart + 8
        val fromItemId = ((irefPayload[bodyStart].toInt() and 0xFF) shl 8) or
            (irefPayload[bodyStart + 1].toInt() and 0xFF)
        val refCount = ((irefPayload[bodyStart + 2].toInt() and 0xFF) shl 8) or
            (irefPayload[bodyStart + 3].toInt() and 0xFF)
        val toItemId = ((irefPayload[bodyStart + 4].toInt() and 0xFF) shl 8) or
            (irefPayload[bodyStart + 5].toInt() and 0xFF)
        assertEquals(AvifStillMuxer.EXIF_ITEM_ID, fromItemId)
        assertEquals(1, refCount)
        assertEquals(AvifStillMuxer.PRIMARY_ITEM_ID, toItemId)
        assertEquals(subBoxStart + subBoxSize, irefPayload.size)
    }

    @Test
    fun `exif set places EXIF bytes immediately after AV1 in mdat with 4-byte zero prefix`() {
        val input = canonicalInput().copy(exifPayload = tinyExif)
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatHeaderSize = MediaDataBox.headerSize(
            input.av1Bitstream.size.toLong() +
                AvifStillMuxer.EXIF_TIFF_HEADER_OFFSET_PREFIX_SIZE.toLong() +
                tinyExif.size.toLong(),
        )
        val av1End = mdatStart + mdatHeaderSize + input.av1Bitstream.size
        // 4-byte big-endian exif_tiff_header_offset = 0
        assertEquals(0, out[av1End].toInt() and 0xFF)
        assertEquals(0, out[av1End + 1].toInt() and 0xFF)
        assertEquals(0, out[av1End + 2].toInt() and 0xFF)
        assertEquals(0, out[av1End + 3].toInt() and 0xFF)
        // Then the TIFF block byte-for-byte
        val tiff = out.copyOfRange(av1End + 4, av1End + 4 + tinyExif.size)
        assertArrayEquals(tinyExif, tiff)
    }

    @Test
    fun `exif iloc has two extents with EXIF offset right after AV1`() {
        val input = canonicalInput().copy(exifPayload = tinyExif)
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatHeaderSize = MediaDataBox.headerSize(
            input.av1Bitstream.size.toLong() +
                AvifStillMuxer.EXIF_TIFF_HEADER_OFFSET_PREFIX_SIZE.toLong() +
                tinyExif.size.toLong(),
        )
        val expectedAv1Offset = (mdatStart + mdatHeaderSize).toLong()
        val expectedExifOffset = expectedAv1Offset + input.av1Bitstream.size.toLong()

        val ilocPayload = findBoxPayload(out, "iloc")
        val body = ilocPayload.copyOfRange(4, ilocPayload.size)
        // v=0, fieldSizes (4,4,0,0): packed (1) + packed_base_index (1) +
        // item_count (uint16). Body:
        //   per item: item_ID (uint16) + data_ref (uint16) + extent_count (uint16)
        //             + extent_offset (uint32) + extent_length (uint32)
        // entry 1
        val entry1Start = 4
        val item1OffsetStart = entry1Start + 2 + 2 + 2
        val item1Offset = ((body[item1OffsetStart].toLong() and 0xFF) shl 24) or
            ((body[item1OffsetStart + 1].toLong() and 0xFF) shl 16) or
            ((body[item1OffsetStart + 2].toLong() and 0xFF) shl 8) or
            (body[item1OffsetStart + 3].toLong() and 0xFF)
        assertEquals(expectedAv1Offset, item1Offset)

        val entry2Start = entry1Start + 2 + 2 + 2 + 4 + 4
        val entry2ItemId = ((body[entry2Start].toInt() and 0xFF) shl 8) or
            (body[entry2Start + 1].toInt() and 0xFF)
        assertEquals(AvifStillMuxer.EXIF_ITEM_ID, entry2ItemId)
        val item2OffsetStart = entry2Start + 2 + 2 + 2
        val item2Offset = ((body[item2OffsetStart].toLong() and 0xFF) shl 24) or
            ((body[item2OffsetStart + 1].toLong() and 0xFF) shl 16) or
            ((body[item2OffsetStart + 2].toLong() and 0xFF) shl 8) or
            (body[item2OffsetStart + 3].toLong() and 0xFF)
        assertEquals(expectedExifOffset, item2Offset)
    }

    @Test
    fun `exif set keeps ipma binding properties only to primary item`() {
        val out = AvifStillMuxer.encode(canonicalInput().copy(exifPayload = tinyExif))
        val ipmaPayload = findBoxPayload(out, "ipma")
        // ipma is a FullBox: 4 bytes version+flags. Version = 0 for
        // small itemIds. Body:
        //   uint32_be entry_count
        //   per entry: uint16 itemId + uint8 association_count + ...
        val entryCount = ((ipmaPayload[4].toLong() and 0xFF) shl 24) or
            ((ipmaPayload[5].toLong() and 0xFF) shl 16) or
            ((ipmaPayload[6].toLong() and 0xFF) shl 8) or
            (ipmaPayload[7].toLong() and 0xFF)
        // Only primary image needs property associations; EXIF item
        // does not need any properties.
        assertEquals(1L, entryCount)
        val firstItemId = ((ipmaPayload[8].toInt() and 0xFF) shl 8) or
            (ipmaPayload[9].toInt() and 0xFF)
        assertEquals(AvifStillMuxer.PRIMARY_ITEM_ID, firstItemId)
    }

    @Test
    fun `exif set keeps file ftyp meta mdat top-level structure`() {
        val out = AvifStillMuxer.encode(canonicalInput().copy(exifPayload = tinyExif))
        val ftypType = String(out.copyOfRange(4, 8), Charsets.US_ASCII)
        assertEquals("ftyp", ftypType)
        val ftypSize = readBoxSize(out, 0)
        val metaType = String(out.copyOfRange(ftypSize + 4, ftypSize + 8), Charsets.US_ASCII)
        assertEquals("meta", metaType)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatType = String(
            out.copyOfRange(ftypSize + metaSize + 4, ftypSize + metaSize + 8),
            Charsets.US_ASCII,
        )
        assertEquals("mdat", mdatType)
    }

    @Test
    fun `exif set ends mdat exactly at the EXIF end (no trailing bytes)`() {
        val input = canonicalInput().copy(exifPayload = tinyExif)
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatSize = readBoxSize(out, mdatStart)
        assertEquals(out.size, mdatStart + mdatSize)
    }

    @Test
    fun `exif null vs exif set produce different outputs`() {
        val noExif = AvifStillMuxer.encode(canonicalInput())
        val withExif = AvifStillMuxer.encode(canonicalInput().copy(exifPayload = tinyExif))
        assertTrue("EXIF must change the output", !noExif.contentEquals(withExif))
        assertTrue(
            "EXIF must increase total file size by at least exif size",
            withExif.size > noExif.size + tinyExif.size,
        )
    }

    // ------------------------------------------------------------------
    // XMP item integration (Round 36)
    // ------------------------------------------------------------------

    /** Minimal XMP-class packet (not a full RDF tree, but the bytes
     *  the muxer carries are opaque so this is enough). */
    private val tinyXmp: ByteArray = (
        "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"></x:xmpmeta>" +
            "<?xpacket end=\"r\"?>"
        ).toByteArray(Charsets.UTF_8)

    @Test
    fun `Input rejects empty xmpPayload when set`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifStillMuxer.Input(
                widthPx = 100,
                heightPx = 100,
                bitDepths = intArrayOf(8, 8, 8),
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
                xmpPayload = ByteArray(0),
            )
        }
    }

    @Test
    fun `XMP_ITEM_ID pin is 3 (distinct from PRIMARY and EXIF)`() {
        assertEquals(3, AvifStillMuxer.XMP_ITEM_ID)
        assertNotEquals(AvifStillMuxer.PRIMARY_ITEM_ID, AvifStillMuxer.XMP_ITEM_ID)
        assertNotEquals(AvifStillMuxer.EXIF_ITEM_ID, AvifStillMuxer.XMP_ITEM_ID)
    }

    @Test
    fun `XMP_MIME_TYPE pin is application slash rdf+xml`() {
        assertEquals("application/rdf+xml", AvifStillMuxer.XMP_MIME_TYPE)
    }

    @Test
    fun `xmp set produces two iinf entries (av01 then mime)`() {
        val out = AvifStillMuxer.encode(canonicalInput().copy(xmpPayload = tinyXmp))
        val iinfPayload = findBoxPayload(out, "iinf")
        val version = iinfPayload[0].toInt() and 0xFF
        val entryCount = if (version == 0) {
            ((iinfPayload[4].toInt() and 0xFF) shl 8) or (iinfPayload[5].toInt() and 0xFF)
        } else {
            ((iinfPayload[4].toInt() and 0xFF) shl 24) or
                ((iinfPayload[5].toInt() and 0xFF) shl 16) or
                ((iinfPayload[6].toInt() and 0xFF) shl 8) or
                (iinfPayload[7].toInt() and 0xFF)
        }
        assertEquals(2, entryCount)
        val asString = String(iinfPayload, Charsets.US_ASCII)
        assertTrue("iinf payload must contain 'av01'", asString.contains("av01"))
        assertTrue("iinf payload must contain 'mime'", asString.contains("mime"))
        assertTrue(
            "iinf payload must contain 'application/rdf+xml' content_type",
            asString.contains("application/rdf+xml"),
        )
    }

    @Test
    fun `xmp set emits iref cdsc reference from XMP item to primary`() {
        val out = AvifStillMuxer.encode(canonicalInput().copy(xmpPayload = tinyXmp))
        val irefPayload = findBoxPayload(out, "iref")
        val subBoxStart = 4
        val subBoxType = String(
            irefPayload.copyOfRange(subBoxStart + 4, subBoxStart + 8),
            Charsets.US_ASCII,
        )
        assertEquals("cdsc", subBoxType)
        val bodyStart = subBoxStart + 8
        val fromItemId = ((irefPayload[bodyStart].toInt() and 0xFF) shl 8) or
            (irefPayload[bodyStart + 1].toInt() and 0xFF)
        val refCount = ((irefPayload[bodyStart + 2].toInt() and 0xFF) shl 8) or
            (irefPayload[bodyStart + 3].toInt() and 0xFF)
        val toItemId = ((irefPayload[bodyStart + 4].toInt() and 0xFF) shl 8) or
            (irefPayload[bodyStart + 5].toInt() and 0xFF)
        assertEquals(AvifStillMuxer.XMP_ITEM_ID, fromItemId)
        assertEquals(1, refCount)
        assertEquals(AvifStillMuxer.PRIMARY_ITEM_ID, toItemId)
    }

    @Test
    fun `xmp set places XMP bytes immediately after AV1 in mdat (no prefix)`() {
        val input = canonicalInput().copy(xmpPayload = tinyXmp)
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatHeaderSize = MediaDataBox.headerSize(
            input.av1Bitstream.size.toLong() + tinyXmp.size.toLong(),
        )
        val xmpStart = mdatStart + mdatHeaderSize + input.av1Bitstream.size
        // No 4-byte prefix for XMP — bytes start directly with the
        // XMP packet, beginning with `<?xpacket`.
        val xmpBytes = out.copyOfRange(xmpStart, xmpStart + tinyXmp.size)
        assertArrayEquals(tinyXmp, xmpBytes)
    }

    @Test
    fun `xmp iloc has two extents with XMP offset right after AV1`() {
        val input = canonicalInput().copy(xmpPayload = tinyXmp)
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatHeaderSize = MediaDataBox.headerSize(
            input.av1Bitstream.size.toLong() + tinyXmp.size.toLong(),
        )
        val expectedAv1Offset = (mdatStart + mdatHeaderSize).toLong()
        val expectedXmpOffset = expectedAv1Offset + input.av1Bitstream.size.toLong()

        val ilocPayload = findBoxPayload(out, "iloc")
        val body = ilocPayload.copyOfRange(4, ilocPayload.size)
        val entry1Start = 4
        val item1OffsetStart = entry1Start + 2 + 2 + 2
        val item1Offset = ((body[item1OffsetStart].toLong() and 0xFF) shl 24) or
            ((body[item1OffsetStart + 1].toLong() and 0xFF) shl 16) or
            ((body[item1OffsetStart + 2].toLong() and 0xFF) shl 8) or
            (body[item1OffsetStart + 3].toLong() and 0xFF)
        assertEquals(expectedAv1Offset, item1Offset)

        val entry2Start = entry1Start + 2 + 2 + 2 + 4 + 4
        val entry2ItemId = ((body[entry2Start].toInt() and 0xFF) shl 8) or
            (body[entry2Start + 1].toInt() and 0xFF)
        assertEquals(AvifStillMuxer.XMP_ITEM_ID, entry2ItemId)
        val item2OffsetStart = entry2Start + 2 + 2 + 2
        val item2Offset = ((body[item2OffsetStart].toLong() and 0xFF) shl 24) or
            ((body[item2OffsetStart + 1].toLong() and 0xFF) shl 16) or
            ((body[item2OffsetStart + 2].toLong() and 0xFF) shl 8) or
            (body[item2OffsetStart + 3].toLong() and 0xFF)
        assertEquals(expectedXmpOffset, item2Offset)
    }

    @Test
    fun `exif and xmp set produces three iinf entries`() {
        val out = AvifStillMuxer.encode(
            canonicalInput().copy(exifPayload = tinyExif, xmpPayload = tinyXmp),
        )
        val iinfPayload = findBoxPayload(out, "iinf")
        val version = iinfPayload[0].toInt() and 0xFF
        val entryCount = if (version == 0) {
            ((iinfPayload[4].toInt() and 0xFF) shl 8) or (iinfPayload[5].toInt() and 0xFF)
        } else {
            ((iinfPayload[4].toInt() and 0xFF) shl 24) or
                ((iinfPayload[5].toInt() and 0xFF) shl 16) or
                ((iinfPayload[6].toInt() and 0xFF) shl 8) or
                (iinfPayload[7].toInt() and 0xFF)
        }
        assertEquals(3, entryCount)
    }

    @Test
    fun `exif and xmp set produces iref with two cdsc sub-boxes (EXIF then XMP)`() {
        val out = AvifStillMuxer.encode(
            canonicalInput().copy(exifPayload = tinyExif, xmpPayload = tinyXmp),
        )
        val irefPayload = findBoxPayload(out, "iref")
        val subBoxes = mutableListOf<Pair<Int, Int>>() // from, to
        var i = 4 // skip version+flags
        while (i < irefPayload.size) {
            val size = ((irefPayload[i].toInt() and 0xFF) shl 24) or
                ((irefPayload[i + 1].toInt() and 0xFF) shl 16) or
                ((irefPayload[i + 2].toInt() and 0xFF) shl 8) or
                (irefPayload[i + 3].toInt() and 0xFF)
            val type = String(irefPayload.copyOfRange(i + 4, i + 8), Charsets.US_ASCII)
            assertEquals("cdsc", type)
            val from = ((irefPayload[i + 8].toInt() and 0xFF) shl 8) or
                (irefPayload[i + 9].toInt() and 0xFF)
            val to = ((irefPayload[i + 12].toInt() and 0xFF) shl 8) or
                (irefPayload[i + 13].toInt() and 0xFF)
            subBoxes.add(from to to)
            i += size
        }
        assertEquals(2, subBoxes.size)
        assertEquals(AvifStillMuxer.EXIF_ITEM_ID to AvifStillMuxer.PRIMARY_ITEM_ID, subBoxes[0])
        assertEquals(AvifStillMuxer.XMP_ITEM_ID to AvifStillMuxer.PRIMARY_ITEM_ID, subBoxes[1])
    }

    @Test
    fun `exif and xmp set lays out mdat as AV1 then EXIF then XMP`() {
        val input = canonicalInput().copy(exifPayload = tinyExif, xmpPayload = tinyXmp)
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val totalMetaSize = input.av1Bitstream.size +
            AvifStillMuxer.EXIF_TIFF_HEADER_OFFSET_PREFIX_SIZE +
            tinyExif.size +
            tinyXmp.size
        val mdatHeaderSize = MediaDataBox.headerSize(totalMetaSize.toLong())
        val av1End = mdatStart + mdatHeaderSize + input.av1Bitstream.size
        // 4-byte zero prefix before EXIF
        assertEquals(0, out[av1End].toInt() and 0xFF)
        assertEquals(0, out[av1End + 1].toInt() and 0xFF)
        assertEquals(0, out[av1End + 2].toInt() and 0xFF)
        assertEquals(0, out[av1End + 3].toInt() and 0xFF)
        val exifEnd = av1End + 4 + tinyExif.size
        assertArrayEquals(tinyExif, out.copyOfRange(av1End + 4, exifEnd))
        val xmpEnd = exifEnd + tinyXmp.size
        assertArrayEquals(tinyXmp, out.copyOfRange(exifEnd, xmpEnd))
        assertEquals(out.size, xmpEnd)
    }

    @Test
    fun `xmp null does not emit iref box (regression guard)`() {
        val out = AvifStillMuxer.encode(canonicalInput())
        assertEquals(-1, findBoxOffset(out, "iref"))
    }

    @Test
    fun `xmp set keeps file ftyp meta mdat top-level structure`() {
        val out = AvifStillMuxer.encode(canonicalInput().copy(xmpPayload = tinyXmp))
        val ftypType = String(out.copyOfRange(4, 8), Charsets.US_ASCII)
        assertEquals("ftyp", ftypType)
        val ftypSize = readBoxSize(out, 0)
        val metaType = String(out.copyOfRange(ftypSize + 4, ftypSize + 8), Charsets.US_ASCII)
        assertEquals("meta", metaType)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatType = String(
            out.copyOfRange(ftypSize + metaSize + 4, ftypSize + metaSize + 8),
            Charsets.US_ASCII,
        )
        assertEquals("mdat", mdatType)
    }

    @Test
    fun `xmp null vs xmp set produce different outputs`() {
        val noXmp = AvifStillMuxer.encode(canonicalInput())
        val withXmp = AvifStillMuxer.encode(canonicalInput().copy(xmpPayload = tinyXmp))
        assertTrue("XMP must change the output", !noXmp.contentEquals(withXmp))
        assertTrue(
            "XMP must grow the file by at least the XMP size",
            withXmp.size > noXmp.size + tinyXmp.size,
        )
    }

    // ------------------------------------------------------------------
    // Alpha auxiliary image item integration (Round 38)
    // ------------------------------------------------------------------

    /** Tiny non-empty placeholder for an alpha bitstream. */
    private val tinyAlpha = byteArrayOf(0x07, 0x42, 0x00, 0xAA.toByte(), 0x55, 0x33)

    @Test
    fun `ALPHA_ITEM_ID pin is 4 (distinct from PRIMARY EXIF XMP)`() {
        assertEquals(4, AvifStillMuxer.ALPHA_ITEM_ID)
        assertNotEquals(AvifStillMuxer.PRIMARY_ITEM_ID, AvifStillMuxer.ALPHA_ITEM_ID)
        assertNotEquals(AvifStillMuxer.EXIF_ITEM_ID, AvifStillMuxer.ALPHA_ITEM_ID)
        assertNotEquals(AvifStillMuxer.XMP_ITEM_ID, AvifStillMuxer.ALPHA_ITEM_ID)
    }

    @Test
    fun `BIT_DEPTHS_ALPHA_8 pin`() {
        assertArrayEquals(intArrayOf(8), AvifStillMuxer.BIT_DEPTHS_ALPHA_8)
    }

    @Test
    fun `BIT_DEPTHS_ALPHA_10 pin`() {
        assertArrayEquals(intArrayOf(10), AvifStillMuxer.BIT_DEPTHS_ALPHA_10)
    }

    @Test
    fun `BIT_DEPTHS_ALPHA_12 pin`() {
        assertArrayEquals(intArrayOf(12), AvifStillMuxer.BIT_DEPTHS_ALPHA_12)
    }

    @Test
    fun `Input rejects empty alphaBitstream when set`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifStillMuxer.Input(
                widthPx = 100,
                heightPx = 100,
                bitDepths = intArrayOf(8, 8, 8),
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
                alphaBitstream = ByteArray(0),
                alphaConfiguration = Av1CodecConfiguration.Config.DEFAULT_8BIT_MONOCHROME,
            )
        }
    }

    @Test
    fun `Input rejects alphaBitstream without alphaConfiguration`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifStillMuxer.Input(
                widthPx = 100,
                heightPx = 100,
                bitDepths = intArrayOf(8, 8, 8),
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
                alphaBitstream = tinyAlpha,
                alphaConfiguration = null,
            )
        }
    }

    @Test
    fun `Input rejects alphaConfiguration without alphaBitstream`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifStillMuxer.Input(
                widthPx = 100,
                heightPx = 100,
                bitDepths = intArrayOf(8, 8, 8),
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
                alphaBitstream = null,
                alphaConfiguration = Av1CodecConfiguration.Config.DEFAULT_8BIT_MONOCHROME,
            )
        }
    }

    @Test
    fun `Input rejects multi-channel alphaBitDepths`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifStillMuxer.Input(
                widthPx = 100,
                heightPx = 100,
                bitDepths = intArrayOf(8, 8, 8),
                cicp = WorkingSpace.SRGB.cicp,
                av1Bitstream = tinyAv1,
                av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
                alphaBitDepths = intArrayOf(8, 8),
            )
        }
    }

    @Test
    fun `alpha set produces two iinf entries (av01 primary + av01 alpha)`() {
        val out = AvifStillMuxer.encode(canonicalAlphaInput())
        val iinfPayload = findBoxPayload(out, "iinf")
        val version = iinfPayload[0].toInt() and 0xFF
        val entryCount = if (version == 0) {
            ((iinfPayload[4].toInt() and 0xFF) shl 8) or (iinfPayload[5].toInt() and 0xFF)
        } else {
            ((iinfPayload[4].toInt() and 0xFF) shl 24) or
                ((iinfPayload[5].toInt() and 0xFF) shl 16) or
                ((iinfPayload[6].toInt() and 0xFF) shl 8) or
                (iinfPayload[7].toInt() and 0xFF)
        }
        assertEquals(2, entryCount)
        // Both entries should declare itemType="av01" — count the
        // occurrences of "av01" in the iinf bytes (must be 2).
        val asString = String(iinfPayload, Charsets.US_ASCII)
        var idx = 0
        var occurrences = 0
        while (true) {
            val found = asString.indexOf("av01", idx)
            if (found == -1) break
            occurrences++
            idx = found + 4
        }
        assertEquals("two av01 entries (primary + alpha)", 2, occurrences)
    }

    @Test
    fun `alpha set emits iref auxl reference from alpha to primary`() {
        val out = AvifStillMuxer.encode(canonicalAlphaInput())
        val irefPayload = findBoxPayload(out, "iref")
        // iref has at least one sub-box; first sub-box starts at offset 4
        // (after version+flags). Walk the iref looking for an "auxl"
        // sub-box.
        var i = 4
        var foundAuxl = false
        while (i < irefPayload.size) {
            val size = ((irefPayload[i].toInt() and 0xFF) shl 24) or
                ((irefPayload[i + 1].toInt() and 0xFF) shl 16) or
                ((irefPayload[i + 2].toInt() and 0xFF) shl 8) or
                (irefPayload[i + 3].toInt() and 0xFF)
            val type = String(irefPayload.copyOfRange(i + 4, i + 8), Charsets.US_ASCII)
            if (type == "auxl") {
                foundAuxl = true
                val from = ((irefPayload[i + 8].toInt() and 0xFF) shl 8) or
                    (irefPayload[i + 9].toInt() and 0xFF)
                val refCount = ((irefPayload[i + 10].toInt() and 0xFF) shl 8) or
                    (irefPayload[i + 11].toInt() and 0xFF)
                val to = ((irefPayload[i + 12].toInt() and 0xFF) shl 8) or
                    (irefPayload[i + 13].toInt() and 0xFF)
                assertEquals(AvifStillMuxer.ALPHA_ITEM_ID, from)
                assertEquals(1, refCount)
                assertEquals(AvifStillMuxer.PRIMARY_ITEM_ID, to)
                break
            }
            i += size
        }
        assertTrue("auxl sub-box must be present in iref when alpha set", foundAuxl)
    }

    @Test
    fun `alpha set appends pixi av1C and auxC to ipco`() {
        val baseline = AvifStillMuxer.encode(canonicalInput())
        val withAlpha = AvifStillMuxer.encode(canonicalAlphaInput())

        val baselineIpco = findBoxPayload(baseline, "ipco")
        val baselineCount = splitChildBoxes(baselineIpco).size
        val alphaIpco = findBoxPayload(withAlpha, "ipco")
        val alphaChildren = splitChildBoxes(alphaIpco)
        val alphaTypes = alphaChildren.map { boxType(it) }

        // alpha appends: pixi (alpha), av1C (alpha), auxC. ispe is shared
        // with the primary, so ipco count = baselineCount + 3.
        assertEquals(baselineCount + 3, alphaChildren.size)

        // The last three properties must be pixi, av1C, auxC in that order.
        val last3 = alphaTypes.takeLast(3)
        assertEquals(listOf("pixi", "av1C", "auxC"), last3)
    }

    @Test
    fun `alpha set emits a second ipma entry binding ALPHA_ITEM_ID`() {
        val out = AvifStillMuxer.encode(canonicalAlphaInput())
        val ipmaPayload = findBoxPayload(out, "ipma")
        // FullBox: 4 bytes version+flags. Body:
        //   uint32_be entry_count
        //   per entry:
        //     uint16 itemId  // for v=0
        //     uint8  association_count
        //     association_count bytes (1 byte each, since flags=0).
        val entryCount = ((ipmaPayload[4].toLong() and 0xFF) shl 24) or
            ((ipmaPayload[5].toLong() and 0xFF) shl 16) or
            ((ipmaPayload[6].toLong() and 0xFF) shl 8) or
            (ipmaPayload[7].toLong() and 0xFF)
        assertEquals(2L, entryCount)

        // First entry is the primary; second is alpha.
        val firstItemId = ((ipmaPayload[8].toInt() and 0xFF) shl 8) or
            (ipmaPayload[9].toInt() and 0xFF)
        assertEquals(AvifStillMuxer.PRIMARY_ITEM_ID, firstItemId)
        val firstAssocCount = ipmaPayload[10].toInt() and 0xFF
        // Skip past primary's associations (1 byte each).
        val secondEntryStart = 11 + firstAssocCount
        val secondItemId = ((ipmaPayload[secondEntryStart].toInt() and 0xFF) shl 8) or
            (ipmaPayload[secondEntryStart + 1].toInt() and 0xFF)
        assertEquals(AvifStillMuxer.ALPHA_ITEM_ID, secondItemId)

        // Alpha must have exactly 4 associations: ispe + pixi + av1C + auxC.
        val secondAssocCount = ipmaPayload[secondEntryStart + 2].toInt() and 0xFF
        assertEquals(4, secondAssocCount)
    }

    @Test
    fun `alpha set associations all marked essential`() {
        val out = AvifStillMuxer.encode(canonicalAlphaInput())
        val ipmaPayload = findBoxPayload(out, "ipma")
        val firstAssocCount = ipmaPayload[10].toInt() and 0xFF
        val secondEntryStart = 11 + firstAssocCount
        val secondAssocCount = ipmaPayload[secondEntryStart + 2].toInt() and 0xFF
        for (i in 0 until secondAssocCount) {
            // Each association is a single byte: high bit = essential flag.
            val assoc = ipmaPayload[secondEntryStart + 3 + i].toInt() and 0xFF
            assertTrue(
                "alpha association $i must be essential (high bit set)",
                (assoc and 0x80) != 0,
            )
        }
    }

    @Test
    fun `alpha set places alpha bytes at end of mdat (no prefix)`() {
        val input = canonicalAlphaInput()
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatHeaderSize = MediaDataBox.headerSize(
            input.av1Bitstream.size.toLong() + tinyAlpha.size.toLong(),
        )
        val alphaStart = mdatStart + mdatHeaderSize + input.av1Bitstream.size
        val alphaBytes = out.copyOfRange(alphaStart, alphaStart + tinyAlpha.size)
        assertArrayEquals(tinyAlpha, alphaBytes)
        // mdat ends exactly at the alpha end (no trailing bytes).
        assertEquals(out.size, alphaStart + tinyAlpha.size)
    }

    @Test
    fun `alpha iloc has two extents with alpha offset right after AV1 primary`() {
        val input = canonicalAlphaInput()
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatHeaderSize = MediaDataBox.headerSize(
            input.av1Bitstream.size.toLong() + tinyAlpha.size.toLong(),
        )
        val expectedAv1Offset = (mdatStart + mdatHeaderSize).toLong()
        val expectedAlphaOffset = expectedAv1Offset + input.av1Bitstream.size.toLong()

        val ilocPayload = findBoxPayload(out, "iloc")
        val body = ilocPayload.copyOfRange(4, ilocPayload.size)
        val entry1Start = 4
        val item1OffsetStart = entry1Start + 2 + 2 + 2
        val item1Offset = ((body[item1OffsetStart].toLong() and 0xFF) shl 24) or
            ((body[item1OffsetStart + 1].toLong() and 0xFF) shl 16) or
            ((body[item1OffsetStart + 2].toLong() and 0xFF) shl 8) or
            (body[item1OffsetStart + 3].toLong() and 0xFF)
        assertEquals(expectedAv1Offset, item1Offset)

        val entry2Start = entry1Start + 2 + 2 + 2 + 4 + 4
        val entry2ItemId = ((body[entry2Start].toInt() and 0xFF) shl 8) or
            (body[entry2Start + 1].toInt() and 0xFF)
        assertEquals(AvifStillMuxer.ALPHA_ITEM_ID, entry2ItemId)
        val item2OffsetStart = entry2Start + 2 + 2 + 2
        val item2Offset = ((body[item2OffsetStart].toLong() and 0xFF) shl 24) or
            ((body[item2OffsetStart + 1].toLong() and 0xFF) shl 16) or
            ((body[item2OffsetStart + 2].toLong() and 0xFF) shl 8) or
            (body[item2OffsetStart + 3].toLong() and 0xFF)
        assertEquals(expectedAlphaOffset, item2Offset)
    }

    @Test
    fun `alpha set keeps file ftyp meta mdat top-level structure`() {
        val out = AvifStillMuxer.encode(canonicalAlphaInput())
        val ftypType = String(out.copyOfRange(4, 8), Charsets.US_ASCII)
        assertEquals("ftyp", ftypType)
        val ftypSize = readBoxSize(out, 0)
        val metaType = String(out.copyOfRange(ftypSize + 4, ftypSize + 8), Charsets.US_ASCII)
        assertEquals("meta", metaType)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatType = String(
            out.copyOfRange(ftypSize + metaSize + 4, ftypSize + metaSize + 8),
            Charsets.US_ASCII,
        )
        assertEquals("mdat", mdatType)
    }

    @Test
    fun `alpha set ends mdat exactly at the alpha end (no trailing bytes)`() {
        val input = canonicalAlphaInput()
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val mdatSize = readBoxSize(out, mdatStart)
        assertEquals(out.size, mdatStart + mdatSize)
    }

    @Test
    fun `alpha null does not emit auxC property`() {
        val out = AvifStillMuxer.encode(canonicalInput())
        val ipco = findBoxPayload(out, "ipco")
        val types = splitChildBoxes(ipco).map { boxType(it) }
        assertTrue("auxC must NOT appear when alpha is null", !types.contains("auxC"))
    }

    @Test
    fun `alpha null vs alpha set produce different outputs`() {
        val noAlpha = AvifStillMuxer.encode(canonicalInput())
        val withAlpha = AvifStillMuxer.encode(canonicalAlphaInput())
        assertTrue("alpha must change the output", !noAlpha.contentEquals(withAlpha))
        assertTrue(
            "alpha must grow the file by at least the alpha size",
            withAlpha.size > noAlpha.size + tinyAlpha.size,
        )
    }

    @Test
    fun `exif xmp and alpha all set lays out mdat as AV1 then EXIF then XMP then ALPHA`() {
        val input = canonicalInput().copy(
            exifPayload = tinyExif,
            xmpPayload = tinyXmp,
            alphaBitstream = tinyAlpha,
            alphaConfiguration = Av1CodecConfiguration.Config.DEFAULT_8BIT_MONOCHROME,
        )
        val out = AvifStillMuxer.encode(input)
        val ftypSize = readBoxSize(out, 0)
        val metaSize = readBoxSize(out, ftypSize)
        val mdatStart = ftypSize + metaSize
        val totalMetaSize = input.av1Bitstream.size +
            AvifStillMuxer.EXIF_TIFF_HEADER_OFFSET_PREFIX_SIZE +
            tinyExif.size +
            tinyXmp.size +
            tinyAlpha.size
        val mdatHeaderSize = MediaDataBox.headerSize(totalMetaSize.toLong())
        val av1End = mdatStart + mdatHeaderSize + input.av1Bitstream.size
        // 4-byte zero prefix before EXIF
        assertEquals(0, out[av1End].toInt() and 0xFF)
        val exifEnd = av1End + 4 + tinyExif.size
        assertArrayEquals(tinyExif, out.copyOfRange(av1End + 4, exifEnd))
        val xmpEnd = exifEnd + tinyXmp.size
        assertArrayEquals(tinyXmp, out.copyOfRange(exifEnd, xmpEnd))
        val alphaEnd = xmpEnd + tinyAlpha.size
        assertArrayEquals(tinyAlpha, out.copyOfRange(xmpEnd, alphaEnd))
        assertEquals(out.size, alphaEnd)
    }

    @Test
    fun `exif xmp and alpha all set produces four iinf entries`() {
        val input = canonicalInput().copy(
            exifPayload = tinyExif,
            xmpPayload = tinyXmp,
            alphaBitstream = tinyAlpha,
            alphaConfiguration = Av1CodecConfiguration.Config.DEFAULT_8BIT_MONOCHROME,
        )
        val out = AvifStillMuxer.encode(input)
        val iinfPayload = findBoxPayload(out, "iinf")
        val version = iinfPayload[0].toInt() and 0xFF
        val entryCount = if (version == 0) {
            ((iinfPayload[4].toInt() and 0xFF) shl 8) or (iinfPayload[5].toInt() and 0xFF)
        } else {
            ((iinfPayload[4].toInt() and 0xFF) shl 24) or
                ((iinfPayload[5].toInt() and 0xFF) shl 16) or
                ((iinfPayload[6].toInt() and 0xFF) shl 8) or
                (iinfPayload[7].toInt() and 0xFF)
        }
        assertEquals(4, entryCount)
    }

    @Test
    fun `exif xmp and alpha all set produces iref with three sub-boxes (cdsc cdsc auxl)`() {
        val input = canonicalInput().copy(
            exifPayload = tinyExif,
            xmpPayload = tinyXmp,
            alphaBitstream = tinyAlpha,
            alphaConfiguration = Av1CodecConfiguration.Config.DEFAULT_8BIT_MONOCHROME,
        )
        val out = AvifStillMuxer.encode(input)
        val irefPayload = findBoxPayload(out, "iref")
        val subBoxTypes = mutableListOf<String>()
        var i = 4
        while (i < irefPayload.size) {
            val size = ((irefPayload[i].toInt() and 0xFF) shl 24) or
                ((irefPayload[i + 1].toInt() and 0xFF) shl 16) or
                ((irefPayload[i + 2].toInt() and 0xFF) shl 8) or
                (irefPayload[i + 3].toInt() and 0xFF)
            val type = String(irefPayload.copyOfRange(i + 4, i + 8), Charsets.US_ASCII)
            subBoxTypes.add(type)
            i += size
        }
        assertEquals(listOf("cdsc", "cdsc", "auxl"), subBoxTypes)
    }

    @Test
    fun `Av1CodecConfiguration DEFAULT_8BIT_MONOCHROME has monochrome flag set`() {
        val cfg = Av1CodecConfiguration.Config.DEFAULT_8BIT_MONOCHROME
        assertTrue("DEFAULT_8BIT_MONOCHROME.monochrome must be true", cfg.monochrome)
        assertEquals(0, cfg.seqProfile)
        assertEquals(8, cfg.seqLevelIdx0)
        assertTrue("highBitdepth must be false for 8-bit", !cfg.highBitdepth)
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private fun canonicalInput(): AvifStillMuxer.Input = AvifStillMuxer.Input(
        widthPx = 4096,
        heightPx = 3072,
        bitDepths = intArrayOf(8, 8, 8),
        cicp = WorkingSpace.SRGB.cicp,
        av1Bitstream = tinyAv1,
        av1Configuration = Av1CodecConfiguration.Config.DEFAULT_8BIT_YUV420,
    )

    private fun canonicalAlphaInput(): AvifStillMuxer.Input = canonicalInput().copy(
        alphaBitstream = tinyAlpha,
        alphaConfiguration = Av1CodecConfiguration.Config.DEFAULT_8BIT_MONOCHROME,
    )

    private fun readBoxSize(buf: ByteArray, offset: Int): Int {
        val size = ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)
        require(size in 8..buf.size) { "implausible box size $size at offset $offset" }
        return size
    }

    private fun boxType(box: ByteArray): String = String(box.copyOfRange(4, 8), Charsets.US_ASCII)

    /**
     * Split a sequence of concatenated ISOBMFF child boxes into a list,
     * by walking forward and reading each box's `(size, type)` header.
     */
    private fun splitChildBoxes(payload: ByteArray): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        var i = 0
        while (i < payload.size) {
            val size = ((payload[i].toInt() and 0xFF) shl 24) or
                ((payload[i + 1].toInt() and 0xFF) shl 16) or
                ((payload[i + 2].toInt() and 0xFF) shl 8) or
                (payload[i + 3].toInt() and 0xFF)
            require(size in 8..(payload.size - i)) { "bad child box size $size at $i" }
            out.add(payload.copyOfRange(i, i + size))
            i += size
        }
        return out
    }

    /**
     * Locate the payload of the first box of `type` anywhere in
     * `buf`, by recursively descending into known container box
     * types (`meta`, `iprp`, `ipco`).
     */
    private fun findBoxPayload(buf: ByteArray, type: String): ByteArray {
        return findBoxPayloadRecursive(buf, 0, buf.size, type)
            ?: error("box type '$type' not found in buffer")
    }

    /**
     * Locate the absolute offset of the first box of `type`
     * anywhere in `buf`, by recursively descending into known
     * container box types (`meta`, `iprp`, `ipco`). Returns -1
     * when the type is not present.
     */
    private fun findBoxOffset(buf: ByteArray, type: String): Int {
        return findBoxOffsetRecursive(buf, 0, buf.size, type)
    }

    private fun findBoxOffsetRecursive(
        buf: ByteArray,
        start: Int,
        end: Int,
        type: String,
    ): Int {
        var i = start
        while (i < end) {
            val size = readBoxSize(buf, i)
            val t = String(buf.copyOfRange(i + 4, i + 8), Charsets.US_ASCII)
            if (t == type) return i
            val payloadStart = if (t == "meta") i + 12 else i + 8
            val payloadEnd = i + size
            if (t == "meta" || t == "iprp") {
                val nested = findBoxOffsetRecursive(buf, payloadStart, payloadEnd, type)
                if (nested != -1) return nested
            }
            i += size
        }
        return -1
    }

    private fun findBoxPayloadRecursive(
        buf: ByteArray,
        start: Int,
        end: Int,
        type: String,
    ): ByteArray? {
        var i = start
        while (i < end) {
            val size = readBoxSize(buf, i)
            val t = String(buf.copyOfRange(i + 4, i + 8), Charsets.US_ASCII)
            if (t == type) {
                // Strip the 8-byte plain header. For FullBoxes
                // (`meta`, `iprp` is plain — but `meta` is full),
                // strip another 4 bytes for version+flags only when
                // the type is one of the FullBox container types we
                // recurse into.
                val headerStrip = if (t == "meta") 12 else 8
                return buf.copyOfRange(i + headerStrip, i + size)
            }
            // Recurse into containers: meta, iprp.
            val payloadStart = if (t == "meta") i + 12 else i + 8
            val payloadEnd = i + size
            if (t == "meta" || t == "iprp") {
                val nested = findBoxPayloadRecursive(buf, payloadStart, payloadEnd, type)
                if (nested != null) return nested
            }
            i += size
        }
        return null
    }

    /**
     * Read the absolute file offset of the primary item from the
     * `iloc` box. Assumes the canonical AVIF still layout: v=0,
     * fieldSizes (4, 4, 0, 0), 1 item, 1 extent.
     */
    private fun readIlocPrimaryOffset(buf: ByteArray): Long {
        val ilocPayload = findBoxPayload(buf, "iloc")
        // iloc is a FullBox; the first 4 bytes of payload are
        // version+flags, then the iloc body.
        val body = ilocPayload.copyOfRange(4, ilocPayload.size)
        // body layout for v=0 fieldSizes (4,4,0,0):
        //   packed_sizes (1 byte: offset_size << 4 | length_size)
        //   packed_base_index (1 byte: base_offset_size << 4 | reserved/index_size)
        //   item_count (uint16_be)
        //   per item:
        //     item_ID (uint16_be)
        //     data_reference_index (uint16_be)
        //     extent_count (uint16_be)
        //     extent_offset (uint32_be)
        //     extent_length (uint32_be)
        val itemHeaderEnd = 2 + 2 + 2 + 2 + 2 // packed + item_count + (item_ID + data_ref + extent_count)
        val offsetStart = itemHeaderEnd
        val offset = ((body[offsetStart].toLong() and 0xFF) shl 24) or
            ((body[offsetStart + 1].toLong() and 0xFF) shl 16) or
            ((body[offsetStart + 2].toLong() and 0xFF) shl 8) or
            (body[offsetStart + 3].toLong() and 0xFF)
        return offset
    }
}
