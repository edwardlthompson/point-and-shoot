package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewReadoutChipModeTest {
    @Test
    fun photoMode_showsStillAndImg_hidesVideoFormat() {
        assertTrue(PreviewReadoutChipMode.showStillLutChip(primaryPhoto = true))
        assertTrue(PreviewReadoutChipMode.showImgChip(primaryPhoto = true))
        assertFalse(PreviewReadoutChipMode.showVideoLutChip(primaryPhoto = true))
        assertFalse(PreviewTrayVideoChrome.showVideoFormatFab(primaryPhoto = true))
        assertEquals("photo", PreviewReadoutChipMode.readoutModeLogValue(primaryPhoto = true))
    }

    @Test
    fun videoMode_showsVideoLutAndFormat_hidesStillAndImg() {
        assertFalse(PreviewReadoutChipMode.showStillLutChip(primaryPhoto = false))
        assertFalse(PreviewReadoutChipMode.showImgChip(primaryPhoto = false))
        assertTrue(PreviewReadoutChipMode.showVideoLutChip(primaryPhoto = false))
        assertTrue(PreviewTrayVideoChrome.showVideoFormatFab(primaryPhoto = false))
        assertEquals("video", PreviewReadoutChipMode.readoutModeLogValue(primaryPhoto = false))
    }
}
