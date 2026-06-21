package dev.pointandshoot

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JpegExifPrivacyStripTest {

    @Test
    fun privacyTags_include_gps_and_device_identifiers() {
        val source = jpegExifPrivacyStripSource()
        assertTrue(source.contains("ExifInterface.TAG_MAKE"))
        assertTrue(source.contains("ExifInterface.TAG_MODEL"))
        assertTrue(source.contains("ExifInterface.TAG_GPS_LATITUDE"))
        assertTrue(source.contains("ExifInterface.TAG_DATETIME_ORIGINAL"))
        assertTrue(source.contains("ExifInterface.TAG_USER_COMMENT"))
        assertTrue(source.contains("fun stripInPlace"))
    }

    private fun jpegExifPrivacyStripSource(): String {
        var dir = File(System.getProperty("user.dir") ?: error("no user.dir"))
        while (true) {
            val candidate =
                File(
                    dir,
                    "modules/pns-capture/src/main/java/dev/pointandshoot/JpegExifPrivacyStrip.kt",
                )
            if (candidate.isFile) return candidate.readText()
            val parent = dir.parentFile ?: error("JpegExifPrivacyStrip.kt not found")
            dir = parent
        }
    }
}
