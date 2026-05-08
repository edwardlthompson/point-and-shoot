package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AvifColrPayloadTest {

    @Test
    fun `nclx type marker is exactly the four ASCII bytes 'nclx'`() {
        val expected = byteArrayOf(0x6E, 0x63, 0x6C, 0x78) // 'n','c','l','x'
        assertArrayEquals(expected, AvifColrPayload.NCLX_TYPE)
    }

    @Test
    fun `prof type marker is exactly the four ASCII bytes 'prof'`() {
        val expected = byteArrayOf(0x70, 0x72, 0x6F, 0x66) // 'p','r','o','f'
        assertArrayEquals(expected, AvifColrPayload.PROF_TYPE)
    }

    @Test
    fun `nclx payload length is exactly 11 bytes`() {
        assertEquals(11, AvifColrPayload.NCLX_PAYLOAD_LENGTH)
    }

    @Test
    fun `encodeNclxPayload produces exactly 11 bytes`() {
        val cicp = WorkingSpace.SRGB.cicp
        val payload = AvifColrPayload.encodeNclxPayload(cicp)
        assertEquals(11, payload.size)
    }

    @Test
    fun `encodeNclxPayload starts with the nclx ASCII marker`() {
        val cicp = WorkingSpace.SRGB.cicp
        val payload = AvifColrPayload.encodeNclxPayload(cicp)
        assertEquals('n'.code.toByte(), payload[0])
        assertEquals('c'.code.toByte(), payload[1])
        assertEquals('l'.code.toByte(), payload[2])
        assertEquals('x'.code.toByte(), payload[3])
    }

    @Test
    fun `encodeNclxPayload encodes sRGB CICP in big-endian byte order`() {
        // sRGB: cp=1, tc=13, mc=0 (identity), full=true
        val payload = AvifColrPayload.encodeNclxPayload(WorkingSpace.SRGB.cicp)
        assertEquals(0x00.toByte(), payload[4]) // cp hi
        assertEquals(0x01.toByte(), payload[5]) // cp lo
        assertEquals(0x00.toByte(), payload[6]) // tc hi
        assertEquals(0x0D.toByte(), payload[7]) // tc lo (13)
        assertEquals(0x00.toByte(), payload[8]) // mc hi
        assertEquals(0x00.toByte(), payload[9]) // mc lo (0 = identity)
        assertEquals(0x80.toByte(), payload[10]) // flags: full-range bit set
    }

    @Test
    fun `encodeNclxPayload encodes Rec2020-PQ CICP in big-endian byte order`() {
        // Rec2020-PQ: cp=9, tc=16, mc=9, full=true
        val payload = AvifColrPayload.encodeNclxPayload(WorkingSpace.REC2020_PQ.cicp)
        assertEquals(0x09.toByte(), payload[5]) // cp lo (9)
        assertEquals(0x10.toByte(), payload[7]) // tc lo (16)
        assertEquals(0x09.toByte(), payload[9]) // mc lo (9)
        assertEquals(0x80.toByte(), payload[10])
    }

    @Test
    fun `encodeNclxPayload encodes full_range_flag false as 0x00`() {
        val cicp = Cicp(colourPrimaries = 1, transferCharacteristics = 13, matrixCoefficients = 1, videoFullRangeFlag = false)
        val payload = AvifColrPayload.encodeNclxPayload(cicp)
        assertEquals(0x00.toByte(), payload[10])
    }

    @Test
    fun `encodeNclxPayload handles a CICP with high byte set (cp=255)`() {
        val cicp = Cicp(colourPrimaries = 255, transferCharacteristics = 13, matrixCoefficients = 1, videoFullRangeFlag = true)
        val payload = AvifColrPayload.encodeNclxPayload(cicp)
        assertEquals(0x00.toByte(), payload[4])
        assertEquals(0xFF.toByte(), payload[5])
    }

    @Test
    fun `encodeNclxPayload then decodeNclxPayload round-trips the CICP triple`() {
        for (ws in WorkingSpace.ALL) {
            val payload = AvifColrPayload.encodeNclxPayload(ws.cicp)
            val decoded = AvifColrPayload.decodeNclxPayload(payload)
            assertNotNull("Decode failed for ${ws.id}", decoded)
            assertEquals("CICP mismatch for ${ws.id}", ws.cicp, decoded)
        }
    }

    @Test
    fun `decodeNclxPayload returns null when the colour-type marker is not 'nclx'`() {
        val notNclx = ByteArray(11) { i ->
            if (i == 0) 'p'.code.toByte() else 0
        }
        assertNull(AvifColrPayload.decodeNclxPayload(notNclx))
    }

    @Test
    fun `decodeNclxPayload throws for wrong payload length`() {
        val tooShort = ByteArray(10)
        val tooLong = ByteArray(12)
        assertThrows(IllegalArgumentException::class.java) { AvifColrPayload.decodeNclxPayload(tooShort) }
        assertThrows(IllegalArgumentException::class.java) { AvifColrPayload.decodeNclxPayload(tooLong) }
    }

    @Test
    fun `decodeNclxPayload silently drops reserved bits 6 through 0`() {
        // Construct a payload with a non-zero reserved bits in flags byte.
        val payload = AvifColrPayload.encodeNclxPayload(WorkingSpace.SRGB.cicp)
        payload[10] = (0x80 or 0x3F).toByte() // full + reserved gunk
        val decoded = AvifColrPayload.decodeNclxPayload(payload)
        assertNotNull(decoded)
        assertEquals(true, decoded!!.videoFullRangeFlag)
    }

    @Test
    fun `encodeProfPayload prepends the 'prof' marker to the ICC bytes`() {
        val icc = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50)
        val payload = AvifColrPayload.encodeProfPayload(icc)
        assertEquals(4 + icc.size, payload.size)
        assertEquals('p'.code.toByte(), payload[0])
        assertEquals('r'.code.toByte(), payload[1])
        assertEquals('o'.code.toByte(), payload[2])
        assertEquals('f'.code.toByte(), payload[3])
        assertArrayEquals(icc, payload.copyOfRange(4, payload.size))
    }

    @Test
    fun `encodeProfPayload then decodeProfPayload round-trips the ICC bytes`() {
        val icc = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val payload = AvifColrPayload.encodeProfPayload(icc)
        val decoded = AvifColrPayload.decodeProfPayload(payload)
        assertNotNull(decoded)
        assertArrayEquals(icc, decoded)
    }

    @Test
    fun `encodeProfPayload rejects empty ICC profiles`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifColrPayload.encodeProfPayload(ByteArray(0))
        }
    }

    @Test
    fun `decodeProfPayload returns null when the marker is not 'prof'`() {
        val notProf = byteArrayOf('n'.code.toByte(), 'c'.code.toByte(), 'l'.code.toByte(), 'x'.code.toByte(), 1, 2, 3)
        assertNull(AvifColrPayload.decodeProfPayload(notProf))
    }

    @Test
    fun `decodeProfPayload throws for payloads shorter than the 4-byte marker`() {
        val tooShort = byteArrayOf(1, 2, 3)
        assertThrows(IllegalArgumentException::class.java) {
            AvifColrPayload.decodeProfPayload(tooShort)
        }
    }

    @Test
    fun `every WorkingSpace preset produces a valid 11-byte nclx payload`() {
        for (ws in WorkingSpace.ALL) {
            val payload = AvifColrPayload.encodeNclxPayload(ws.cicp)
            assertEquals("Wrong size for ${ws.id}", 11, payload.size)
            assertTrue(
                "Bad nclx marker for ${ws.id}",
                payload.copyOfRange(0, 4).contentEquals(AvifColrPayload.NCLX_TYPE),
            )
        }
    }

    @Test
    fun `SCHEMA_VERSION is pinned to 1`() {
        assertEquals(1, AvifColrPayload.SCHEMA_VERSION)
    }
}
