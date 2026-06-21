package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StillCaptureMetadataTest {


    @Test
    fun `exposure fraction for sub-second`() {
        assertEquals("1/125", StillCaptureMetadata.exposureTimeExifString(8_000_000L))
    }

    @Test
    fun `exposure decimal for multi-second`() {
        assertEquals("2", StillCaptureMetadata.exposureTimeExifString(2_000_000_000L))
    }

    @Test
    fun applyToDngUri_sourceNeverCallsExifSaveAttributes() {
        val source = stillCaptureMetadataSource()
        val body =
            source
                .substringAfter("fun applyToDngUri")
                .substringBefore("fun applyToJpegUri")
        assertFalse("applyToDngUri must not call ExifInterface.saveAttributes()", body.contains("exif.saveAttributes()"))
        assertFalse("applyToDngUri must not construct ExifInterface", body.contains("ExifInterface("))
    }

    @Test
    fun applyToJpegUri_pathUsesExifSaveAttributes() {
        val source = stillCaptureMetadataSource()
        assertTrue(source.contains("exif.saveAttributes()"))
        val dngBody =
            source
                .substringAfter("fun applyToDngUri")
                .substringBefore("fun applyToJpegUri")
        assertFalse(dngBody.contains("exif.saveAttributes()"))
    }

    @Test
    fun dngIfd0Patches_preserveFixtureStripTable() {
        val fixtureBytes = referenceWideFixtureBytes()
        val beforeStrips =
            DngTiffStripTable.snapshot(fixtureBytes)
                ?: error("fixture strip table parse failed")
        assertTrue(beforeStrips.height > 0)

        var patchedBytes = TiffIfd0Software305.patchSoftwarePreservingLength(fixtureBytes, "Point & Shoot")
        patchedBytes =
            TiffIfd0Software305.patchPrimaryIfdAsciiTagPreservingLength(
                patchedBytes,
                TiffIfd0Software305.TAG_MAKE,
                "TestMake",
            )
        patchedBytes =
            TiffIfd0Software305.patchPrimaryIfdAsciiTagPreservingLength(
                patchedBytes,
                TiffIfd0Software305.TAG_MODEL,
                "TestModel",
            )
        patchedBytes =
            TiffIfd0Software305.patchPrimaryIfdAsciiTagPreservingLength(
                patchedBytes,
                TiffIfd0Software305.TAG_DATETIME,
                "2026:06:12 12:00:00",
            )

        val afterStrips =
            DngTiffStripTable.snapshot(patchedBytes)
                ?: error("patched strip table parse failed")
        assertEquals("row-strip tables must stay unchanged after in-place IFD0 patches", beforeStrips, afterStrips)
    }

    @Test
    fun applyToDngUri_skips_when_strip_privacy_enabled() {
        val source = stillCaptureMetadataSource()
        val body =
            source
                .substringAfter("fun applyToDngUri")
                .substringBefore("fun applyToJpegUri")
        assertTrue(body.contains("stripPrivacyExif"))
        assertTrue(body.contains("exifStrip dngMetadataSkipped ok=true"))
    }

    private fun stillCaptureMetadataSource(): String {
        var dir = File(System.getProperty("user.dir") ?: error("no user.dir"))
        while (true) {
            val candidate =
                File(
                    dir,
                    "modules/pns-capture/src/main/java/dev/pointandshoot/StillCaptureMetadata.kt",
                )
            if (candidate.isFile) return candidate.readText()
            val parent = dir.parentFile ?: error("StillCaptureMetadata.kt not found")
            dir = parent
        }
    }

    private fun referenceWideFixtureBytes(): ByteArray = referenceWideFixtureFile().readBytes()

    private fun referenceWideFixtureFile(): File {
        var dir = File(System.getProperty("user.dir") ?: error("no user.dir"))
        while (true) {
            val candidate =
                File(dir, "tests/fixtures/referenceapp_cph2655/referenceapp_wide_cam2.dng")
            if (candidate.isFile) return candidate
            val parent = dir.parentFile ?: error("referenceapp_wide_cam2.dng fixture not found")
            dir = parent
        }
    }
}
