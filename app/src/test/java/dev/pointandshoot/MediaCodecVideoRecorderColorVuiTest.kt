package dev.pointandshoot

import android.media.MediaCodecInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCodecVideoRecorderColorVuiTest {
    @Test
    fun colorVuiTag_eightBitMain_isBt709() {
        assertEquals(
            "bt709",
            MediaCodecVideoRecorder.colorVuiTagForConfig(
                MediaCodecVideoRecorder.Config(
                    width = 1920,
                    height = 1080,
                    fps = 120,
                    bitrate = 20_000_000,
                    isTenBit = false,
                    hdrProfile = 0,
                    isHdr10 = false,
                ),
            ),
        )
    }

    @Test
    fun colorVuiTag_tenBit_surfaceSdr_isBt709() {
        assertEquals(
            "bt709",
            MediaCodecVideoRecorder.colorVuiTagForConfig(
                MediaCodecVideoRecorder.Config(
                    width = 1920,
                    height = 1080,
                    fps = 120,
                    bitrate = 30_000_000,
                    isTenBit = true,
                ),
            ),
        )
    }

    @Test
    fun colorVuiTag_hlgMain10_isBt2020Hlg() {
        assertEquals(
            "bt2020-hlg",
            MediaCodecVideoRecorder.colorVuiTagForConfig(
                MediaCodecVideoRecorder.Config(
                    width = 1920,
                    height = 1080,
                    fps = 60,
                    bitrate = 30_000_000,
                    isTenBit = true,
                    videoColorProfile = VideoColorProfile.Hlg,
                ),
            ),
        )
    }

    @Test
    fun colorVuiTag_hdr10_isBt2020Pq() {
        assertEquals(
            "bt2020-pq",
            MediaCodecVideoRecorder.colorVuiTagForConfig(
                MediaCodecVideoRecorder.Config(
                    width = 1920,
                    height = 1080,
                    fps = 60,
                    bitrate = 40_000_000,
                    hdrProfile = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                    isHdr10 = true,
                ),
            ),
        )
    }
}
