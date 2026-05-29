package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegIccEmbedderTest {
    @Test
    fun embed_inserts_app2_after_soi() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val icc = IccProfileBuilder.forColorSpaceTarget(ColorSpaceTarget.DisplayP3)
        val out = JpegIccEmbedder.embedAfterSoi(jpeg, icc)
        assertTrue(out.size > jpeg.size)
        assertEquals(0xFF.toByte(), out[0])
        assertEquals(0xD8.toByte(), out[1])
        assertEquals(0xFF.toByte(), out[2])
        assertEquals(0xE2.toByte(), out[3])
    }

    @Test
    fun embed_idempotent_when_icc_present() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val icc = IccProfileBuilder.forColorSpaceTarget(ColorSpaceTarget.DisplayP3)
        val once = JpegIccEmbedder.embedAfterSoi(jpeg, icc)
        val twice = JpegIccEmbedder.embedAfterSoi(once, icc)
        assertEquals(once.size, twice.size)
    }

    @Test
    fun embed_replaces_existing_app2_icc() {
        val iccOld = byteArrayOf(1, 2, 3, 4)
        val oldSeg = buildFakeApp2Icc(iccOld)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + oldSeg + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val icc = IccProfileBuilder.forColorSpaceTarget(ColorSpaceTarget.DisplayP3)
        val out = JpegIccEmbedder.embedAfterSoi(jpeg, icc)
        assertTrue(out.size > jpeg.size)
        assertTrue(String(out, Charsets.US_ASCII).contains("Display P3"))
    }

    private fun buildFakeApp2Icc(iccChunk: ByteArray): ByteArray {
        val id =
            byteArrayOf(
                'I'.code.toByte(),
                'C'.code.toByte(),
                'C'.code.toByte(),
                '_'.code.toByte(),
                'P'.code.toByte(),
                'R'.code.toByte(),
                'O'.code.toByte(),
                'F'.code.toByte(),
                'I'.code.toByte(),
                'L'.code.toByte(),
                'E'.code.toByte(),
                0,
            )
        val payloadSize = id.size + 2 + iccChunk.size
        val lengthField = 2 + payloadSize
        val seg = ByteArray(2 + lengthField)
        seg[0] = 0xFF.toByte()
        seg[1] = 0xE2.toByte()
        seg[2] = ((lengthField shr 8) and 0xFF).toByte()
        seg[3] = (lengthField and 0xFF).toByte()
        System.arraycopy(id, 0, seg, 4, id.size)
        seg[4 + id.size] = 1
        seg[5 + id.size] = 1
        System.arraycopy(iccChunk, 0, seg, 6 + id.size, iccChunk.size)
        return seg
    }
}
