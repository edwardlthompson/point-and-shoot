package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStorageFilenameTest {

    @Test
    fun `Standard Pro DNG filename has dng extension and standard_pro id`() {
        val name = CaptureStorage.filename(
            profile = ImagingProfile.StandardPro,
            kind = CaptureStorage.CaptureKind.DngLossless,
            sequence = 7,
        )
        assertTrue("expected .dng suffix; was $name", name.endsWith(".dng"))
        assertTrue("expected standard_pro id; was $name", name.contains("_standard_pro_"))
        assertTrue("expected pns_ prefix; was $name", name.startsWith("pns_"))
    }

    @Test
    fun `Ultra-Max RAW12 DNG filename has dng extension and ultra_max id`() {
        val name = CaptureStorage.filename(
            profile = ImagingProfile.UltraMax,
            kind = CaptureStorage.CaptureKind.DngRaw12,
            sequence = 1,
        )
        assertTrue("expected .dng suffix; was $name", name.endsWith(".dng"))
        assertTrue("expected ultra_max id; was $name", name.contains("_ultra_max_"))
    }

    @Test
    fun `AVIF capture has avif extension`() {
        val name = CaptureStorage.filename(
            profile = ImagingProfile.StandardPro,
            kind = CaptureStorage.CaptureKind.Avif10BitHdr,
            sequence = 42,
        )
        assertTrue("expected .avif suffix; was $name", name.endsWith(".avif"))
    }

    @Test
    fun `JXL capture has jxl extension`() {
        val name = CaptureStorage.filename(
            profile = ImagingProfile.UltraMax,
            kind = CaptureStorage.CaptureKind.JpegXl12Bit,
            sequence = 99,
        )
        assertTrue("expected .jxl suffix; was $name", name.endsWith(".jxl"))
    }

    @Test
    fun `sequence is zero-padded to four digits`() {
        val name = CaptureStorage.filename(
            profile = ImagingProfile.StandardPro,
            kind = CaptureStorage.CaptureKind.DngLossless,
            sequence = 7,
        )
        // Filename ends in _0007.dng (e.g., pns_<utc>_standard_pro_0007.dng).
        assertTrue("expected _0007.dng suffix; was $name", name.endsWith("_0007.dng"))
    }

    @Test
    fun `sequence wider than four digits is preserved verbatim`() {
        val name = CaptureStorage.filename(
            profile = ImagingProfile.StandardPro,
            kind = CaptureStorage.CaptureKind.DngLossless,
            sequence = 12_345,
        )
        assertTrue("expected _12345.dng suffix; was $name", name.endsWith("_12345.dng"))
    }

    @Test
    fun `MIME types are non-empty for every capture kind`() {
        for (kind in CaptureStorage.CaptureKind.entries) {
            val mime = kind.mimeType
            assertTrue("kind ${kind.name} has empty mime", mime.isNotEmpty())
            assertTrue("kind ${kind.name} mime missing slash: $mime", mime.contains("/"))
            // Kind extensions should not contain a dot.
            assertEquals(
                "kind ${kind.name} extension contained a dot",
                kind.extension,
                kind.extension.trim('.'),
            )
        }
    }
}
