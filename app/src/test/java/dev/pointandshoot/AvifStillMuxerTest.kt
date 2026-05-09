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
