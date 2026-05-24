package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppVideoFormatSelectionTest {
    @Test
    fun lacksTrueHfrUniqueFrames_hevc8BitAt120() {
        assertTrue(VideoRecordingController.lacksTrueHfrUniqueFrames(120, VideoCodec.H265))
    }

    @Test
    fun lacksTrueHfrUniqueFrames_hevc10BitAt240() {
        assertTrue(VideoRecordingController.lacksTrueHfrUniqueFrames(240, VideoCodec.H265_10BIT))
    }

    @Test
    fun lacksTrueHfrUniqueFrames_hevcDcgAt120_evenThoughDcgCappedAt60InPicker() {
        assertTrue(VideoRecordingController.lacksTrueHfrUniqueFrames(120, VideoCodec.DCG))
    }

    @Test
    fun lacksTrueHfrUniqueFrames_h264At120_notHevc() {
        assertFalse(VideoRecordingController.lacksTrueHfrUniqueFrames(120, VideoCodec.H264))
    }

    @Test
    fun lacksTrueHfrUniqueFrames_h264At480_allowed() {
        assertFalse(VideoRecordingController.lacksTrueHfrUniqueFrames(480, VideoCodec.H264))
    }

    @Test
    fun lacksTrueHfrUniqueFrames_hevcAt60_allowed() {
        assertFalse(VideoRecordingController.lacksTrueHfrUniqueFrames(60, VideoCodec.H265))
    }

    @Test
    fun lacksTrueHfrUniqueFrames_av1At120_hiddenUntilHwProof() {
        assertTrue(VideoRecordingController.lacksTrueHfrUniqueFrames(120, VideoCodec.AV1))
    }

    @Test
    fun lacksTrueHfrUniqueFrames_av1At60_allowed() {
        assertFalse(VideoRecordingController.lacksTrueHfrUniqueFrames(60, VideoCodec.AV1))
    }

    @Test
    fun buildVideoTruth_nullMap_mentionsNoHs() {
        val truth = InAppVideoFormatSelection.buildVideoTruth(highSpeedMap = null)
        assertTrue(
            truth.lines.any { it.contains("No constrained high-speed", ignoreCase = true) },
        )
    }

    @Test
    fun buildVideoTruth_includesHonestH264Policy() {
        val truth = InAppVideoFormatSelection.buildVideoTruth(highSpeedMap = null)
        assertTrue(truth.lines.any { it.contains("H.264 only", ignoreCase = true) })
    }

}
