package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [MediaDataBox].
 *
 * Pinned facts (per ISO/IEC 14496-12 §8.1.1):
 *
 *  * `mdat` is a regular Box (NOT a FullBox). No version+flags slot.
 *  * Payload is opaque bytes — the parser does not look inside.
 *  * Plain-header form is `8 + payload.size` bytes; large-header
 *    form is `16 + payload.size` bytes when the plain form would
 *    overflow the uint32 size field.
 */
class MediaDataBoxTest {

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("mdat", MediaDataBox.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, MediaDataBox.SCHEMA_VERSION)
    }

    @Test
    fun `header-size constants match IsobmffBox`() {
        assertEquals(IsobmffBox.PLAIN_HEADER_SIZE, MediaDataBox.PLAIN_HEADER_SIZE)
        assertEquals(IsobmffBox.LARGE_HEADER_SIZE, MediaDataBox.LARGE_HEADER_SIZE)
        assertEquals(8, MediaDataBox.PLAIN_HEADER_SIZE)
        assertEquals(16, MediaDataBox.LARGE_HEADER_SIZE)
    }

    // ------------------------------------------------------------------
    // headerSize
    // ------------------------------------------------------------------

    @Test
    fun `headerSize returns 8 for empty payload`() {
        assertEquals(MediaDataBox.PLAIN_HEADER_SIZE, MediaDataBox.headerSize(0L))
    }

    @Test
    fun `headerSize returns 8 for typical AVIF still payload`() {
        // ~17 KB AVIF payload
        assertEquals(MediaDataBox.PLAIN_HEADER_SIZE, MediaDataBox.headerSize(17_000L))
    }

    @Test
    fun `headerSize returns 8 just under the threshold`() {
        // Plain form encodes (8 + payload) in uint32; threshold is at
        // payload + 8 == 0xFFFFFFFF.
        val justUnder = IsobmffBox.LARGE_SIZE_THRESHOLD - MediaDataBox.PLAIN_HEADER_SIZE.toLong()
        assertEquals(MediaDataBox.PLAIN_HEADER_SIZE, MediaDataBox.headerSize(justUnder))
    }

    @Test
    fun `headerSize returns 16 just over the threshold`() {
        val justOver = IsobmffBox.LARGE_SIZE_THRESHOLD - MediaDataBox.PLAIN_HEADER_SIZE.toLong() + 1
        assertEquals(MediaDataBox.LARGE_HEADER_SIZE, MediaDataBox.headerSize(justOver))
    }

    @Test
    fun `headerSize rejects negative payloadSize`() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaDataBox.headerSize(-1L)
        }
    }

    // ------------------------------------------------------------------
    // encodeBox(ByteArray)
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox empty payload produces 8-byte header-only box`() {
        val box = MediaDataBox.encodeBox(ByteArray(0))
        assertEquals(8, box.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 8), box.copyOfRange(0, 4))
        assertArrayEquals("mdat".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
    }

    @Test
    fun `encodeBox with payload concatenates header and payload`() {
        val payload = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55)
        val box = MediaDataBox.encodeBox(payload)
        assertEquals(13, box.size)
        // size = 13
        assertArrayEquals(byteArrayOf(0, 0, 0, 13), box.copyOfRange(0, 4))
        assertArrayEquals("mdat".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        assertArrayEquals(payload, box.copyOfRange(8, 13))
    }

    @Test
    fun `encodeBox defensively copies payload`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val box = MediaDataBox.encodeBox(payload)
        // Mutate the source after encoding.
        payload[0] = 0xFF.toByte()
        // The encoded bytes must reflect the *original* payload.
        assertEquals(0x01.toByte(), box[8])
        assertEquals(0x02.toByte(), box[9])
        assertEquals(0x03.toByte(), box[10])
        assertEquals(0x04.toByte(), box[11])
    }

    // ------------------------------------------------------------------
    // encodeBox(List<ByteArray>)
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox empty list produces 8-byte header-only box`() {
        val box = MediaDataBox.encodeBox(emptyList())
        assertEquals(8, box.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 8), box.copyOfRange(0, 4))
        assertArrayEquals("mdat".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
    }

    @Test
    fun `encodeBox concatenates list payloads in order`() {
        val a = byteArrayOf(0x11, 0x22)
        val b = byteArrayOf(0x33, 0x44, 0x55)
        val box = MediaDataBox.encodeBox(listOf(a, b))
        assertEquals(8 + 2 + 3, box.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 13), box.copyOfRange(0, 4))
        assertArrayEquals("mdat".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55), box.copyOfRange(8, 13))
    }

    @Test
    fun `encodeBox single-element list equals direct encode`() {
        val payload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val viaSingle = MediaDataBox.encodeBox(payload)
        val viaList = MediaDataBox.encodeBox(listOf(payload))
        assertArrayEquals(viaSingle, viaList)
    }
}
