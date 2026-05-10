package dev.pointandshoot

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TiffUniqueCameraModel50708Test {

    private fun headerPrimaryIfdOffset(buf: ByteArray): Int =
        ByteBuffer.wrap(buf, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

    /** Reads ASCII UniqueCameraModel (50708) from the **primary** IFD (first in chain only). */
    private fun readTag50708Ascii(buf: ByteArray): String? {
        val ifd0 = headerPrimaryIfdOffset(buf)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(ifd0)
        val n = bb.short.toInt() and 0xffff
        repeat(n) {
            val tag = bb.short.toInt() and 0xffff
            val type = bb.short.toInt() and 0xffff
            val count = bb.int
            val raw = ByteArray(4)
            bb.get(raw)
            if (tag == 50708) {
                require(type == 2) { "expected ASCII" }
                return if (count <= 4) {
                    String(raw, 0, count, StandardCharsets.US_ASCII).trimEnd('\u0000')
                } else {
                    val dataOff = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).int
                    val bytes = ByteArray(count)
                    System.arraycopy(buf, dataOff, bytes, 0, count)
                    String(bytes, StandardCharsets.US_ASCII).trimEnd('\u0000')
                }
            }
        }
        return null
    }

    private fun minimalLeTiff(ifd0Offset: Int, entryCount: Int, buildEntries: (ByteBuffer) -> Unit): ByteArray {
        val ifdStart = ifd0Offset
        val ifdLen = 2 + entryCount * 12 + 4
        val total = ifdStart + ifdLen
        val out = ByteArray(total)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x49.toByte())
            put(0x49.toByte())
            putShort(42)
            putInt(ifd0Offset)
        }
        val ifd = ByteBuffer.wrap(out, ifdStart, ifdLen).order(ByteOrder.LITTLE_ENDIAN)
        ifd.putShort(entryCount.toShort())
        buildEntries(ifd)
        ifd.putInt(0)
        return out
    }

    @Test
    fun append_inline_short_model() {
        val base = minimalLeTiff(8, 0) {}
        val model = "Z"
        val patched = TiffUniqueCameraModel50708.appendTag50708(base, model)
        assertEquals(base.size + 2 + 12 + 4, patched.size)
        assertEquals(base.size, headerPrimaryIfdOffset(patched))
        assertEquals(model, readTag50708Ascii(patched))
    }

    @Test
    fun append_external_ascii_when_long() {
        val base = minimalLeTiff(8, 0) {}
        val model = "HelloWorld (cameraId=0)"
        val patched = TiffUniqueCameraModel50708.appendTag50708(base, model)
        assertEquals(model, readTag50708Ascii(patched))
        assertEquals(base.size, headerPrimaryIfdOffset(patched))
    }

    @Test
    fun replaces_existing_50708() {
        val asciiOld = "OLD (cameraId=0)\u0000".toByteArray(StandardCharsets.US_ASCII)
        require(asciiOld.size > 4)
        val ifd0 = 8
        val dataOld = ifd0 + 2 + 12 + 4 // one IFD entry + next-IFD slot
        val base =
            minimalLeTiff(ifd0, 1) { bb ->
                bb.putShort(50708.toShort())
                bb.putShort(2.toShort()) // ASCII
                bb.putInt(asciiOld.size)
                bb.putInt(dataOld)
            }
        val full = base.copyOf(dataOld + asciiOld.size)
        System.arraycopy(asciiOld, 0, full, dataOld, asciiOld.size)

        val modelNew = "NEW (cameraId=1)"
        val patched = TiffUniqueCameraModel50708.appendTag50708(full, modelNew)
        assertEquals(modelNew, readTag50708Ascii(patched))
    }

    @Test
    fun preserves_next_ifd_pointer() {
        val nextIfd = 12345
        val base = minimalLeTiff(8, 0) {}
        ByteBuffer.wrap(base, base.size - 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(nextIfd)
        val patched = TiffUniqueCameraModel50708.appendTag50708(base, "N (cameraId=0)")
        val newIfd = headerPrimaryIfdOffset(patched)
        assertEquals(nextIfd, ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN).run {
            position(newIfd + 2 + 12)
            int
        })
    }

    @Test
    fun rejects_non_printable_ascii() {
        val base = minimalLeTiff(8, 0) {}
        assertThrows(IllegalArgumentException::class.java) {
            TiffUniqueCameraModel50708.appendTag50708(base, "bad\u0080")
        }
    }

    @Test
    fun blank_model_rejected() {
        val base = minimalLeTiff(8, 0) {}
        assertThrows(IllegalArgumentException::class.java) {
            TiffUniqueCameraModel50708.appendTag50708(base, "   ")
        }
    }
}
