package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImgMenuHintsTest {

    @Test
    fun rawRowSubtitlesMatchPlanChart() {
        assertEquals("12 - RAW12 - Rec2020", ImgMenuHints.rawRowSubtitle(ImgMenuTier.Ultra))
        assertEquals("12 - Lossless - P3", ImgMenuHints.rawRowSubtitle(ImgMenuTier.Standard))
        assertNull(ImgMenuHints.rawRowSubtitle(ImgMenuTier.Off))
    }

    @Test
    fun jpegHdrRowSubtitlesMatchPlanChart() {
        assertEquals("12 - JXL - Rec2020", ImgMenuHints.jpegHdrRowSubtitle(ImgMenuTier.Ultra))
        assertEquals("10 - AVIF - P3", ImgMenuHints.jpegHdrRowSubtitle(ImgMenuTier.Standard))
        assertNull(ImgMenuHints.jpegHdrRowSubtitle(ImgMenuTier.Off))
    }

    @Test
    fun jpegOnlyPrimaryHintsAreEightBitP3() {
        assertEquals("8 - Max - P3", ImgMenuHints.jpegOnlyPrimaryRowSubtitle(ImgMenuTier.Ultra))
        assertEquals("8 - Bal - P3", ImgMenuHints.jpegOnlyPrimaryRowSubtitle(ImgMenuTier.Standard))
        assertNull(ImgMenuHints.jpegOnlyPrimaryRowSubtitle(ImgMenuTier.Off))
    }

    @Test
    fun coerceNoOffOffBumpsJpegTier() {
        val bad = ComposedStillIntent(ImgMenuTier.Off, ImgMenuTier.Off, ImgMenuTier.Standard)
        val fixed = bad.coerceNoOffOff()
        assertEquals(ImgMenuTier.Standard, fixed.jpeg)
        assertEquals(ImgMenuTier.Off, fixed.raw)
    }
}
