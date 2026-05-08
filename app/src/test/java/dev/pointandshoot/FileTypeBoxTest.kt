package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [FileTypeBox].
 *
 * Pinned facts (per ISO/IEC 14496-12 §4.3 and AVIF spec §4):
 *
 *  * `ftyp` is a regular Box (NOT a FullBox). No version+flags slot.
 *  * Payload = `major_brand (4 ASCII) + minor_version (uint32_be) +
 *    compatible_brands (N × 4 ASCII)`.
 *  * Canonical AVIF still: `major_brand = "avif"`, `minor_version = 0`,
 *    `compatible_brands = ["avif", "mif1", "miaf"]`.
 *  * `compatible_brands` is allowed to be empty per spec, but is
 *    always populated in practice.
 */
class FileTypeBoxTest {

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("ftyp", FileTypeBox.BOX_TYPE)
    }

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, FileTypeBox.SCHEMA_VERSION)
    }

    @Test
    fun `BRAND_LENGTH pin`() {
        assertEquals(4, FileTypeBox.BRAND_LENGTH)
    }

    @Test
    fun `brand constant pins`() {
        assertEquals("avif", FileTypeBox.BRAND_AVIF)
        assertEquals("avis", FileTypeBox.BRAND_AVIS)
        assertEquals("mif1", FileTypeBox.BRAND_MIF1)
        assertEquals("miaf", FileTypeBox.BRAND_MIAF)
        assertEquals("MA1A", FileTypeBox.BRAND_MA1A)
        assertEquals("MA1B", FileTypeBox.BRAND_MA1B)
        assertEquals("isom", FileTypeBox.BRAND_ISOM)
        assertEquals("heic", FileTypeBox.BRAND_HEIC)
    }

    // ------------------------------------------------------------------
    // encodePayload byte-layout pins
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload empty compatible brands produces 8-byte payload`() {
        val payload = FileTypeBox.encodePayload(
            majorBrand = "avif",
            minorVersion = 0L,
            compatibleBrands = emptyList(),
        )
        // 4-byte fourCC + 4-byte minorVersion = 8 bytes
        assertEquals(8, payload.size)
        assertArrayEquals(
            "avif".toByteArray(Charsets.US_ASCII),
            payload.copyOfRange(0, 4),
        )
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), payload.copyOfRange(4, 8))
    }

    @Test
    fun `encodePayload AVIF still produces canonical byte layout`() {
        val payload = FileTypeBox.encodePayload(
            majorBrand = "avif",
            minorVersion = 0L,
            compatibleBrands = listOf("avif", "mif1", "miaf"),
        )
        // 4 (major) + 4 (minor) + 3 × 4 (compatible) = 20 bytes
        assertEquals(20, payload.size)
        assertArrayEquals("avif".toByteArray(Charsets.US_ASCII), payload.copyOfRange(0, 4))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), payload.copyOfRange(4, 8))
        assertArrayEquals("avif".toByteArray(Charsets.US_ASCII), payload.copyOfRange(8, 12))
        assertArrayEquals("mif1".toByteArray(Charsets.US_ASCII), payload.copyOfRange(12, 16))
        assertArrayEquals("miaf".toByteArray(Charsets.US_ASCII), payload.copyOfRange(16, 20))
    }

    @Test
    fun `encodePayload high-byte minorVersion encodes as expected`() {
        val payload = FileTypeBox.encodePayload(
            majorBrand = "isom",
            minorVersion = 0x12345678L,
            compatibleBrands = listOf("isom"),
        )
        assertEquals(12, payload.size)
        assertArrayEquals(
            byteArrayOf(0x12, 0x34, 0x56, 0x78),
            payload.copyOfRange(4, 8),
        )
    }

    @Test
    fun `encodePayload max minorVersion encodes`() {
        val payload = FileTypeBox.encodePayload(
            majorBrand = "isom",
            minorVersion = 0xFFFFFFFFL,
            compatibleBrands = listOf("isom"),
        )
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            payload.copyOfRange(4, 8),
        )
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    fun `encodePayload rejects non-4-char majorBrand`() {
        assertThrows(IllegalArgumentException::class.java) {
            FileTypeBox.encodePayload("avi", 0L, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            FileTypeBox.encodePayload("avifx", 0L, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            FileTypeBox.encodePayload("", 0L, emptyList())
        }
    }

    @Test
    fun `encodePayload rejects non-printable-ASCII majorBrand`() {
        assertThrows(IllegalArgumentException::class.java) {
            FileTypeBox.encodePayload("av\u0001f", 0L, emptyList())
        }
    }

    @Test
    fun `encodePayload rejects non-4-char compatible brand`() {
        assertThrows(IllegalArgumentException::class.java) {
            FileTypeBox.encodePayload("avif", 0L, listOf("mif"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FileTypeBox.encodePayload("avif", 0L, listOf("miff1"))
        }
    }

    @Test
    fun `encodePayload rejects negative minorVersion`() {
        assertThrows(IllegalArgumentException::class.java) {
            FileTypeBox.encodePayload("avif", -1L, emptyList())
        }
    }

    @Test
    fun `encodePayload rejects minorVersion above uint32 range`() {
        assertThrows(IllegalArgumentException::class.java) {
            FileTypeBox.encodePayload("avif", 0x1_0000_0000L, emptyList())
        }
    }

    // ------------------------------------------------------------------
    // encodeBox integration
    // ------------------------------------------------------------------

    @Test
    fun `encodeBox AVIF still produces 28-byte canonical envelope`() {
        val box = FileTypeBox.encodeBox(
            majorBrand = "avif",
            minorVersion = 0L,
            compatibleBrands = listOf("avif", "mif1", "miaf"),
        )
        // 8-byte header + 20-byte payload = 28 bytes
        assertEquals(28, box.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 28), box.copyOfRange(0, 4))
        assertArrayEquals("ftyp".toByteArray(Charsets.US_ASCII), box.copyOfRange(4, 8))
        assertArrayEquals("avif".toByteArray(Charsets.US_ASCII), box.copyOfRange(8, 12))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), box.copyOfRange(12, 16))
        assertArrayEquals("avif".toByteArray(Charsets.US_ASCII), box.copyOfRange(16, 20))
        assertArrayEquals("mif1".toByteArray(Charsets.US_ASCII), box.copyOfRange(20, 24))
        assertArrayEquals("miaf".toByteArray(Charsets.US_ASCII), box.copyOfRange(24, 28))
    }

    @Test
    fun `encodeAvifStillBox is shorthand for canonical AVIF still ftyp`() {
        val viaShortcut = FileTypeBox.encodeAvifStillBox()
        val viaGeneric = FileTypeBox.encodeBox(
            majorBrand = FileTypeBox.BRAND_AVIF,
            minorVersion = 0L,
            compatibleBrands = listOf(
                FileTypeBox.BRAND_AVIF,
                FileTypeBox.BRAND_MIF1,
                FileTypeBox.BRAND_MIAF,
            ),
        )
        assertArrayEquals(viaGeneric, viaShortcut)
        assertEquals(28, viaShortcut.size)
    }
}
