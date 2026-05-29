package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudSettingsVideoHistogramTest {
    @Test
    fun wantsPreviewHistogramPipeline_respectsModeAndFlags() {
        val photoHistogram = HudSettings(showHistogram = true, showHistogramDuringVideo = false)
        assertTrue(photoHistogram.wantsPreviewHistogramPipeline(primaryPhoto = true))
        assertTrue(photoHistogram.wantsPreviewHistogramPipeline(primaryPhoto = false))

        val videoOnly = HudSettings(showHistogram = false, showHistogramDuringVideo = true)
        assertFalse(videoOnly.wantsPreviewHistogramPipeline(primaryPhoto = true))
        assertTrue(videoOnly.wantsPreviewHistogramPipeline(primaryPhoto = false))
    }

    @Test
    fun wantsHistogramOverlayVisible_videoRequiresRecording() {
        val s = HudSettings(showHistogram = false, showHistogramDuringVideo = true)
        assertFalse(s.wantsHistogramOverlayVisible(primaryPhoto = false, isRecording = false))
        assertTrue(s.wantsHistogramOverlayVisible(primaryPhoto = false, isRecording = true))
        assertFalse(s.wantsHistogramOverlayVisible(primaryPhoto = true, isRecording = true))
    }
}
