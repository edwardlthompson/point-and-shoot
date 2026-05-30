package dev.pointandshoot

import android.util.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFormatQualityRankTest {
    @Test
    fun `10-bit DCG outranks 8-bit H264 at same resolution`() {
        val dcg =
            VideoFormat(
                codec = VideoCodec.DCG,
                resolution = Size(3840, 2160),
                frameRate = 30,
                bitrate = 80_000_000,
                isTenBit = true,
                isDcg = true,
            )
        val h264 =
            VideoFormat(
                codec = VideoCodec.H264,
                resolution = Size(3840, 2160),
                frameRate = 120,
                bitrate = 100_000_000,
            )
        assertTrue(VideoFormatQualityRank.score(dcg) > VideoFormatQualityRank.score(h264))
    }

    @Test
    fun `pickBest prefers DCG quality score over H264 HFR`() {
        val dcg =
            VideoFormat(
                codec = VideoCodec.DCG,
                resolution = Size(3840, 2160),
                frameRate = 30,
                bitrate = 80_000_000,
                isTenBit = true,
                isDcg = true,
            )
        val h264 =
            VideoFormat(
                codec = VideoCodec.H264,
                resolution = Size(3840, 2160),
                frameRate = 120,
                bitrate = 100_000_000,
            )
        assertTrue(VideoFormatQualityRank.score(dcg) > VideoFormatQualityRank.score(h264))
        assertTrue(VideoFormatQualityRank.compare(dcg, h264) < 0)
    }
}
