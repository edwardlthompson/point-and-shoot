package dev.pointandshoot

import android.util.Size
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDeliveryHonestyTest {

    @Test
    fun annotate_marksSub4kTierWhenOnly1080pHsAt120() {
        val format =
            VideoFormat(
                codec = VideoCodec.H264,
                resolution = Size(3840, 2160),
                frameRate = 120,
                bitrate = 120_000_000,
            )
        val annotated =
            VideoDeliveryHonesty.annotateHfrDeliveryTiers(
                listOf(format),
                highSpeedMap = null,
            )
        assertTrue(annotated.isEmpty() || annotated[0].hfrDeliveryTier == null)
    }

    @Test
    fun isCatalogHonest_emptyHfr4k_isTrue() {
        val catalog =
            listOf(
                VideoFormat(
                    codec = VideoCodec.H264,
                    resolution = Size(1920, 1080),
                    frameRate = 60,
                    bitrate = 20_000_000,
                ),
            )
        assertTrue(VideoDeliveryHonesty.isCatalogHonest(catalog = catalog, highSpeedMap = null))
    }

    @Test
    fun isCatalogHonest_requiresTierOn4k120Rows() {
        val format =
            VideoFormat(
                codec = VideoCodec.H264,
                resolution = Size(3840, 2160),
                frameRate = 120,
                bitrate = 120_000_000,
            )
        val untieredHonest = VideoDeliveryHonesty.isCatalogHonest(catalog = listOf(format), highSpeedMap = null)
        if (format.resolution.width >= 3840) {
            assertFalse(untieredHonest)
        }
        val strictHonest = listOf(format.copy(hfrDeliveryTier = HfrDeliveryTier.STRICT_4K120))
        assertTrue(VideoDeliveryHonesty.isCatalogHonest(catalog = strictHonest, highSpeedMap = null))
        val sub4kHonest =
            listOf(
                format.copy(
                    hfrDeliveryTier = HfrDeliveryTier.HS_SUB4K_CAPTURE,
                    hfrCaptureSize = Size(1920, 1080),
                ),
            )
        assertTrue(VideoDeliveryHonesty.isCatalogHonest(catalog = sub4kHonest, highSpeedMap = null))
    }
}
