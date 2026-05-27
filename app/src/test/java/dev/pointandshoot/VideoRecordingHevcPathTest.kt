package dev.pointandshoot

import android.util.Size
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint **15.2** — 8-bit HEVC ≤60 fps uses MediaCodec for BT.709 limited VUI. */
class VideoRecordingHevcPathTest {
    @Test
    fun eightBitHevc_at60fps_requiresMediaCodec() {
        val format =
            VideoFormat(
                codec = VideoCodec.H265,
                resolution = Size(1920, 1080),
                frameRate = 60,
                bitrate = VideoFormatPresets.calculateBitrate(1920, 1080, 60, VideoCodec.H265),
                isTenBit = false,
                isDcg = false,
            )
        assertTrue(format.requiresMediaCodec)
    }

    @Test
    fun eightBitHevc_at30fps_requiresMediaCodec() {
        val format =
            VideoFormat(
                codec = VideoCodec.H265,
                resolution = Size(1920, 1080),
                frameRate = 30,
                bitrate = VideoFormatPresets.calculateBitrate(1920, 1080, 30, VideoCodec.H265),
                isTenBit = false,
                isDcg = false,
            )
        assertTrue(format.requiresMediaCodec)
    }
}
