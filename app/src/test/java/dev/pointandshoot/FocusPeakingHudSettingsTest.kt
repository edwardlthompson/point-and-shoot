package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPeakingHudSettingsTest {

    @Test
    fun `default hud has peaking off`() {
        val s = HudSettings()
        assertFalse(s.focusPeakingEnabled())
        assertEquals(FocusPeakingColor.Off, s.focusPeakingColor)
        assertEquals(FocusPeakingStrength.Medium, s.focusPeakingStrength)
    }

    @Test
    fun `non-off color enables peaking flag`() {
        val s = HudSettings(focusPeakingColor = FocusPeakingColor.Magenta)
        assertTrue(s.focusPeakingEnabled())
    }
}
