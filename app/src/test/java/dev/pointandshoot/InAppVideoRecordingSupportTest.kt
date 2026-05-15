package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppVideoRecordingSupportTest {

    @Test
    fun shortLabelForDims_common_presets() {
        assertEquals("720p", InAppVideoRecordingSupport.shortLabelForDims(1280, 720))
        assertEquals("1080p", InAppVideoRecordingSupport.shortLabelForDims(1920, 1080))
        assertEquals("4K", InAppVideoRecordingSupport.shortLabelForDims(3840, 2160))
    }

    @Test
    fun shortLabelForDims_fallback_dimensions() {
        assertEquals("1440×1080", InAppVideoRecordingSupport.shortLabelForDims(1440, 1080))
    }

    @Test
    fun bitrateForSize_scales_with_pixel_area() {
        val base = 12_000_000
        val hd = InAppVideoRecordingSupport.bitrateForSize(1920, 1080, base)
        val uhd = InAppVideoRecordingSupport.bitrateForSize(3840, 2160, base)
        assertTrue(uhd > hd)
    }

    @Test
    fun bitrateForSize_clamped() {
        val tiny = InAppVideoRecordingSupport.bitrateForSize(320, 240, 12_000_000)
        assertTrue(tiny >= 2_000_000)
    }
}
