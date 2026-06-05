package dev.pointandshoot.fleet

import android.util.Size
import dev.pointandshoot.PreviewChromePreferences
import dev.pointandshoot.VideoCodec
import dev.pointandshoot.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAdaptedCatalogTest {

    @Test
    fun videoColorSpaces_omitHdrWhenNoDcgFormats() {
        val formats =
            listOf(
                VideoFormat(
                    codec = VideoCodec.H264,
                    resolution = Size(1920, 1080),
                    frameRate = 30,
                    bitrate = 8_000_000,
                ),
            )
        val spaces = DeviceAdaptedCatalog.videoColorSpacesForDevice(formats)
        assertTrue(spaces.any { it.id == "rec709" })
        assertFalse(spaces.any { it.id == "hdr10" })
    }

    @Test
    fun sanitize_migratesStaleCodecOrdinal() {
        val catalog =
            listOf(
                VideoFormat(
                    codec = VideoCodec.H264,
                    resolution = Size(1920, 1080),
                    frameRate = 30,
                    bitrate = 8_000_000,
                ),
            )
        val chrome =
            PreviewChromePreferences(
                inAppVideoCodecOrdinal = VideoCodec.DCG.ordinal,
                inAppVideoColorSpaceOrdinal = 0,
            )
        val result =
            DeviceAdaptedPrefs.sanitizeVideoChrome(
                chrome = chrome,
                catalog = catalog,
                fallbackWidth = 1920,
                fallbackHeight = 1080,
                fallbackFps = 30,
            )
        assertTrue(result.migrated)
        assertEquals(VideoCodec.H264, VideoCodec.entries[result.chrome.inAppVideoCodecOrdinal])
    }

    @Test
    fun sanitize_downgrades4kWhenFourKRegularSessionNotOk() {
        val catalog =
            listOf(
                VideoFormat(
                    codec = VideoCodec.H264,
                    resolution = Size(1920, 1080),
                    frameRate = 30,
                    bitrate = 8_000_000,
                ),
            )
        val chrome =
            PreviewChromePreferences(
                inAppVideoCodecOrdinal = VideoCodec.H264.ordinal,
                inAppVideoEncodeWidth = 3840,
                inAppVideoEncodeHeight = 2160,
            )
        val result =
            DeviceAdaptedPrefs.sanitizeVideoChrome(
                chrome = chrome,
                catalog = catalog,
                fallbackWidth = 1920,
                fallbackHeight = 1080,
                fallbackFps = 30,
                hfrSessionOk = false,
                fourKRegularSessionOk = false,
            )
        assertTrue(result.migrated)
        assertTrue(result.chrome.inAppVideoEncodeWidth < 3840)
    }
}
