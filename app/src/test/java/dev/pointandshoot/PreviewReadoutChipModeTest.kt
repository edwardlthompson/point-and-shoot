package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewReadoutChipModeTest {
    @Test
    fun photoMode_showsStillAndImg_hidesVideoFormat() {
        assertTrue(PreviewReadoutChipMode.showStillLutChip(primaryPhoto = true))
        assertFalse(PreviewReadoutChipMode.showImgChip(primaryPhoto = true))
        assertFalse(PreviewReadoutChipMode.showVideoLutChip(primaryPhoto = true))
        assertFalse(PreviewTrayVideoChrome.showVideoFormatFab(primaryPhoto = true))
        assertEquals("photo", PreviewReadoutChipMode.readoutModeLogValue(primaryPhoto = true))
    }

    @Test
    fun apertureChip_hiddenWhenNoHalStops() {
        assertFalse(PreviewReadoutChipMode.showApertureChip(availableCount = 0))
        assertTrue(PreviewReadoutChipMode.showApertureChip(availableCount = 1))
    }

    @Test
    fun apertureChip_interactiveOnlyWhenVariableAndControllable() {
        assertTrue(PreviewReadoutChipMode.apertureChipInteractive(variable = true, canControl = true))
        assertFalse(PreviewReadoutChipMode.apertureChipInteractive(variable = true, canControl = false))
        assertFalse(PreviewReadoutChipMode.apertureChipInteractive(variable = false, canControl = true))
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
