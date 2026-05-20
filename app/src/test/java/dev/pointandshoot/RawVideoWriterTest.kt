package dev.pointandshoot

import android.graphics.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RawVideoWriterTest {
    @Test
    fun finish_writesFooterMagic() {
        val bytes = ByteArrayOutputStream()
        val writer = RawVideoWriter(bytes, width = 4, height = 2, imageFormat = ImageFormat.RAW_SENSOR)
        assertEquals(0, writer.finish())
        val out = String(bytes.toByteArray(), Charsets.US_ASCII)
        assertTrue(out.contains(RawVideoWriter.MAGIC_FOOTER))
    }

    @Test
    fun header_startsWithMagic() {
        val bytes = ByteArrayOutputStream()
        RawVideoWriter(bytes, 100, 100, ImageFormat.RAW12)
        val magic = String(bytes.toByteArray(), 0, 8, Charsets.US_ASCII)
        assertEquals(RawVideoWriter.MAGIC_HEADER, magic)
    }

    @Test
    fun isValidFooter_requiresPositiveFrames() {
        assertTrue(RawVideoWriter.isValidFooter(RawVideoWriter.MAGIC_FOOTER, 3))
    }
}
