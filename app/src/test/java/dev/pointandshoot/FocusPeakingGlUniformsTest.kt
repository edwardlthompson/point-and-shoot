package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPeakingGlUniformsTest {

    @Test
    fun `fromHud disabled when color off`() {
        val u = FocusPeakingGlUniforms.fromHud(HudSettings())
        assertFalse(u.enabled)
    }

    @Test
    fun `fromHud enabled when color not off`() {
        val u =
            FocusPeakingGlUniforms.fromHud(
                HudSettings(focusPeakingColor = FocusPeakingColor.Green),
            )
        assertTrue(u.enabled)
        assertTrue(u.sensitivity > 0f)
    }

    @Test
    fun `forceForManualVideo enables red when hud peaking off`() {
        val u = FocusPeakingGlUniforms.fromHud(HudSettings(), forceForManualVideo = true)
        assertTrue(u.enabled)
        assertTrue(u.r > 0.9f)
    }
}
