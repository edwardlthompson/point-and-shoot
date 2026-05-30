package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorQualityIndexTest {
    @Test
    fun `Rec2020 HDR ranks above sRGB`() {
        val spaces = ColorQualityIndex.stillSpacesForPicker()
        val rec2020 = spaces.first { it.first == ColorSpaceTarget.Rec2020 }.second
        val srgb = spaces.first { it.first == ColorSpaceTarget.SrgbRec709 }.second
        assertTrue(rec2020 > srgb)
    }

    @Test
    fun `filterVideoFormats respects HDR10 codec set`() {
        val formats =
            listOf(
                VideoFormat(VideoCodec.H264, android.util.Size(1920, 1080), 30, 20_000_000),
                VideoFormat(VideoCodec.DCG, android.util.Size(1920, 1080), 30, 40_000_000, isTenBit = true, isDcg = true),
            )
        val hdr = ColorQualityIndex.videoSpacesForPicker().first { it.id == "hdr10" }
        val filtered = ColorQualityIndex.filterVideoFormats(formats, hdr)
        assertEquals(1, filtered.size)
        assertEquals(VideoCodec.DCG, filtered.first().codec)
    }
}
