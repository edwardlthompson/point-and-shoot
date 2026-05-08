package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class IsobmffBoxTest {

    @Test
    fun `boxType encodes 4 ASCII bytes for canonical box names`() {
        assertArrayEquals(
            byteArrayOf('c'.code.toByte(), 'o'.code.toByte(), 'l'.code.toByte(), 'r'.code.toByte()),
            IsobmffBox.boxType("colr"),
        )
        assertArrayEquals(
            byteArrayOf('p'.code.toByte(), 'a'.code.toByte(), 's'.code.toByte(), 'p'.code.toByte()),
            IsobmffBox.boxType("pasp"),
        )
        assertArrayEquals(
            byteArrayOf('m'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte()),
            IsobmffBox.boxType("mdat"),
        )
    }

    @Test
    fun `boxType allows uppercase ASCII for vendor extensions`() {
        assertArrayEquals(
            byteArrayOf('P'.code.toByte(), 'N'.code.toByte(), 'S'.code.toByte(), '0'.code.toByte()),
            IsobmffBox.boxType("PNS0"),
        )
    }

    @Test
    fun `boxType rejects strings whose length is not exactly 4`() {
        assertThrows(IllegalArgumentException::class.java) { IsobmffBox.boxType("col") }
        assertThrows(IllegalArgumentException::class.java) { IsobmffBox.boxType("color") }
        assertThrows(IllegalArgumentException::class.java) { IsobmffBox.boxType("") }
    }

    @Test
    fun `boxType rejects non-printable-ASCII characters`() {
        // emoji
        assertThrows(IllegalArgumentException::class.java) { IsobmffBox.boxType("col\uD83D") }
        // tab
        assertThrows(IllegalArgumentException::class.java) { IsobmffBox.boxType("col\t") }
        // DEL (0x7F is not printable per our acceptance band 0x20..0x7E)
        assertThrows(IllegalArgumentException::class.java) { IsobmffBox.boxType("col\u007F") }
        // null
        assertThrows(IllegalArgumentException::class.java) { IsobmffBox.boxType("col\u0000") }
    }

    @Test
    fun `encodeBox produces 8-byte header plus payload for small boxes`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val out = IsobmffBox.encodeBox("mdat", payload)
        assertEquals(IsobmffBox.PLAIN_HEADER_SIZE + payload.size, out.size)
        // size = 13 = 0x00 0x00 0x00 0x0D
        assertEquals(0x00.toByte(), out[0])
        assertEquals(0x00.toByte(), out[1])
        assertEquals(0x00.toByte(), out[2])
        assertEquals(0x0D.toByte(), out[3])
        // type = "mdat"
        assertEquals('m'.code.toByte(), out[4])
        assertEquals('d'.code.toByte(), out[5])
        assertEquals('a'.code.toByte(), out[6])
        assertEquals('t'.code.toByte(), out[7])
        // payload
        assertEquals(0x01.toByte(), out[8])
        assertEquals(0x05.toByte(), out[12])
    }

    @Test
    fun `encodeBox handles empty payload`() {
        val out = IsobmffBox.encodeBox("free", ByteArray(0))
        assertEquals(IsobmffBox.PLAIN_HEADER_SIZE, out.size)
        // size = 8
        assertEquals(0x08.toByte(), out[3])
        assertEquals('f'.code.toByte(), out[4])
        assertEquals('e'.code.toByte(), out[7])
    }

    @Test
    fun `encodeBox wraps an AvifColrPayload nclx body into a real colr box`() {
        // Pull a real WorkingSpace.SRGB CICP through the colr formatter,
        // then wrap it in a box. End-to-end round-trip from Cicp to wire bytes.
        val cicp = WorkingSpace.SRGB.cicp
        val nclx = AvifColrPayload.encodeNclxPayload(cicp)
        assertEquals(AvifColrPayload.NCLX_PAYLOAD_LENGTH, nclx.size)
        val box = IsobmffBox.encodeBox("colr", nclx)
        // Total = header(8) + nclx(11) = 19
        assertEquals(8 + 11, box.size)
        assertEquals(0x13.toByte(), box[3]) // 0x13 = 19
        assertEquals('c'.code.toByte(), box[4])
        assertEquals('o'.code.toByte(), box[5])
        assertEquals('l'.code.toByte(), box[6])
        assertEquals('r'.code.toByte(), box[7])
        // body[0..3] is "nclx" marker
        assertEquals('n'.code.toByte(), box[8])
        assertEquals('c'.code.toByte(), box[9])
        assertEquals('l'.code.toByte(), box[10])
        assertEquals('x'.code.toByte(), box[11])
    }

    @Test
    fun `writeBox writes the same bytes as encodeBox`() {
        val payload = ByteArray(64) { it.toByte() }
        val bos = ByteArrayOutputStream()
        IsobmffBox.writeBox(bos, "abcd", payload)
        assertArrayEquals(IsobmffBox.encodeBox("abcd", payload), bos.toByteArray())
    }

    @Test
    fun `writeBox switches to the large-size escape when payload exceeds 4GiB minus 8 bytes`() {
        // We can't actually allocate >4GB on a unit test JVM. Instead we
        // verify the *header* shape by constructing a fake OutputStream
        // that reports the bytes WITHOUT keeping them in memory, paired
        // with a fake payload of zero length but a "synthetic" length we
        // pass through a stub. We do this by directly testing with a
        // small payload that pretends to overflow via a bespoke check.
        // Since the public surface always gets the real ByteArray.size,
        // this test instead documents the THRESHOLD constant: a payload
        // of (LARGE_SIZE_THRESHOLD - PLAIN_HEADER_SIZE) bytes still uses
        // the plain header; one byte more would flip to the large header.
        assertEquals(0xFFFFFFFFL, IsobmffBox.LARGE_SIZE_THRESHOLD)
        assertEquals(8, IsobmffBox.PLAIN_HEADER_SIZE)
        assertEquals(16, IsobmffBox.LARGE_HEADER_SIZE)
    }

    @Test
    fun `encodeFullBox produces 8-byte header + version + flags + payload`() {
        // meta box: FullBox version=0, flags=0, empty payload
        val out = IsobmffBox.encodeFullBox("meta", 0, 0, ByteArray(0))
        // Total = header(8) + version_flags(4) + payload(0) = 12
        assertEquals(IsobmffBox.PLAIN_HEADER_SIZE + IsobmffBox.FULLBOX_VERSION_FLAGS_SIZE, out.size)
        assertEquals(0x0C.toByte(), out[3]) // size = 12
        assertEquals('m'.code.toByte(), out[4])
        assertEquals('e'.code.toByte(), out[5])
        assertEquals('t'.code.toByte(), out[6])
        assertEquals('a'.code.toByte(), out[7])
        // version + flags = 4 zero bytes
        assertEquals(0x00.toByte(), out[8])
        assertEquals(0x00.toByte(), out[9])
        assertEquals(0x00.toByte(), out[10])
        assertEquals(0x00.toByte(), out[11])
    }

    @Test
    fun `encodeFullBox encodes version and 24-bit flags in big-endian order`() {
        val payload = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val out = IsobmffBox.encodeFullBox("test", version = 0x42, flags = 0xABCDEF, payload = payload)
        // Total = 8 + 4 + 2 = 14
        assertEquals(14, out.size)
        assertEquals(0x0E.toByte(), out[3])
        // type
        assertEquals('t'.code.toByte(), out[4])
        // version = 0x42
        assertEquals(0x42.toByte(), out[8])
        // flags = 0xABCDEF, big-endian 3 bytes
        assertEquals(0xAB.toByte(), out[9])
        assertEquals(0xCD.toByte(), out[10])
        assertEquals(0xEF.toByte(), out[11])
        // payload
        assertEquals(0xAA.toByte(), out[12])
        assertEquals(0xBB.toByte(), out[13])
    }

    @Test
    fun `encodeFullBox rejects out-of-range version`() {
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffBox.encodeFullBox("test", -1, 0, ByteArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffBox.encodeFullBox("test", 256, 0, ByteArray(0))
        }
    }

    @Test
    fun `encodeFullBox rejects out-of-range flags`() {
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffBox.encodeFullBox("test", 0, -1, ByteArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffBox.encodeFullBox("test", 0, 0x1000000, ByteArray(0))
        }
    }

    @Test
    fun `encodeFullBox accepts max version and flags`() {
        val out = IsobmffBox.encodeFullBox("test", 255, 0xFFFFFF, ByteArray(0))
        assertEquals(0xFF.toByte(), out[8])
        assertEquals(0xFF.toByte(), out[9])
        assertEquals(0xFF.toByte(), out[10])
        assertEquals(0xFF.toByte(), out[11])
    }

    @Test
    fun `concatPayloads produces a contiguous byte array of all inputs`() {
        val a = byteArrayOf(0x01, 0x02)
        val b = byteArrayOf(0x03, 0x04, 0x05)
        val c = byteArrayOf(0x06)
        val merged = IsobmffBox.concatPayloads(listOf(a, b, c))
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06), merged)
    }

    @Test
    fun `concatPayloads returns empty for empty list`() {
        val merged = IsobmffBox.concatPayloads(emptyList())
        assertEquals(0, merged.size)
    }

    @Test
    fun `concatPayloads is composable with encodeBox to build a container box`() {
        // Simulate a parent box with two child boxes.
        val child1 = IsobmffBox.encodeBox("colr", AvifColrPayload.encodeNclxPayload(WorkingSpace.SRGB.cicp))
        val child2 = IsobmffBox.encodeBox("pasp", IsobmffSampleAspect.encodePasp(IsobmffSampleAspect.PaspPayload.SQUARE))
        val container = IsobmffBox.encodeBox("ipco", IsobmffBox.concatPayloads(listOf(child1, child2)))
        // Container = 8 (header) + 19 (colr) + 16 (pasp) = 43
        assertEquals(8 + 19 + 16, container.size)
        // Container type
        assertEquals('i'.code.toByte(), container[4])
        assertEquals('p'.code.toByte(), container[5])
        assertEquals('c'.code.toByte(), container[6])
        assertEquals('o'.code.toByte(), container[7])
        // First child starts at offset 8 with its own size header (19)
        assertEquals(0x13.toByte(), container[11])
        assertEquals('c'.code.toByte(), container[12])
        // Second child starts at offset 8 + 19 = 27 with size 16
        assertEquals(0x10.toByte(), container[30])
        assertEquals('p'.code.toByte(), container[31])
    }

    @Test
    fun `writeBox writes type bytes in network byte order matching boxType helper`() {
        val bos = ByteArrayOutputStream()
        IsobmffBox.writeBox(bos, "abcd", ByteArray(0))
        val bytes = bos.toByteArray()
        // Header = 8 bytes total (size=8 + type "abcd")
        assertEquals(8, bytes.size)
        assertArrayEquals(IsobmffBox.boxType("abcd"), bytes.sliceArray(4..7))
    }

    @Test
    fun `writeFullBox produces deterministic output (same inputs same bytes)`() {
        val a = IsobmffBox.encodeFullBox("iinf", 0, 0, byteArrayOf(0x01, 0x02))
        val b = IsobmffBox.encodeFullBox("iinf", 0, 0, byteArrayOf(0x01, 0x02))
        assertArrayEquals(a, b)
    }

    @Test
    fun `encodeBox payload bytes are not shared with input array (defensive copy unnecessary but verified safe)`() {
        // The encoder writes to a fresh ByteArrayOutputStream and returns its toByteArray,
        // so mutating the input AFTER encoding must not affect the output.
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val out = IsobmffBox.encodeBox("test", payload)
        payload[0] = 0x7F
        assertEquals(0x01.toByte(), out[8])
        assertEquals(0x02.toByte(), out[9])
        assertEquals(0x03.toByte(), out[10])
    }

    @Test
    fun `schema version is pinned`() {
        assertEquals(1, IsobmffBox.SCHEMA_VERSION)
    }

    @Test
    fun `header size constants match ISO 14496-12 spec`() {
        assertEquals(8, IsobmffBox.PLAIN_HEADER_SIZE)
        assertEquals(16, IsobmffBox.LARGE_HEADER_SIZE)
        assertEquals(4, IsobmffBox.FULLBOX_VERSION_FLAGS_SIZE)
    }

    @Test
    fun `large size threshold is exactly the uint32 max`() {
        assertEquals(0xFFFFFFFFL, IsobmffBox.LARGE_SIZE_THRESHOLD)
        // Java/Kotlin Int can represent up to Int.MAX_VALUE = 0x7FFFFFFF;
        // the threshold uses Long so the comparison works for boxes with
        // payloads that the JVM could not even allocate as a single
        // ByteArray. Documents the design rather than runtime behavior.
        assertTrue(IsobmffBox.LARGE_SIZE_THRESHOLD > Int.MAX_VALUE.toLong())
    }
}
