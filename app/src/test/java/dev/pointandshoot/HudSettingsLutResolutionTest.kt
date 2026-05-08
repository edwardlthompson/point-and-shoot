package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-data tests for the per-mode LUT memory in [HudSettings].
 *
 * The `Context`-bound `load` / `save` helpers require either Robolectric or
 * instrumentation; we only exercise the [HudSettings.stillsLut] /
 * [HudSettings.videoLut] resolution + fallback rules here, which are Android-
 * free.
 *
 * Closes the persistence half of BUILD_PLAN \u00a77 "HUD chip 'LUT' alongside
 * the imaging-profile selector; per-mode memory (still vs video can carry
 * different defaults); 'None' (identity) is always the default and survives
 * app restart unless the user explicitly chose otherwise".
 */
class HudSettingsLutResolutionTest {

    @Test
    fun `default settings resolve to None for both modes`() {
        val s = HudSettings()
        assertEquals(LutCatalog.None, s.stillsLut())
        assertEquals(LutCatalog.None, s.videoLut())
    }

    @Test
    fun `selectedLutForStills resolves to the matching enum entry`() {
        val s = HudSettings(selectedLutForStills = LutCatalog.PnsCinematic.name)
        assertEquals(LutCatalog.PnsCinematic, s.stillsLut())
        // Video falls back to None when not explicitly set.
        assertEquals(LutCatalog.None, s.videoLut())
    }

    @Test
    fun `selectedLutForVideo resolves independently of stills`() {
        val s = HudSettings(
            selectedLutForStills = LutCatalog.BwBt709.name,
            selectedLutForVideo = LutCatalog.BwBt601.name,
        )
        assertEquals(LutCatalog.BwBt709, s.stillsLut())
        assertEquals(LutCatalog.BwBt601, s.videoLut())
    }

    @Test
    fun `unknown enum name falls back to None (handles enum rename or removal)`() {
        val s = HudSettings(
            selectedLutForStills = "removed-or-renamed-lut",
            selectedLutForVideo = "also-not-an-enum",
        )
        assertEquals(LutCatalog.None, s.stillsLut())
        assertEquals(LutCatalog.None, s.videoLut())
    }

    @Test
    fun `default constructor selectedLut fields equal LutCatalog_None_name`() {
        val s = HudSettings()
        assertEquals(LutCatalog.None.name, s.selectedLutForStills)
        assertEquals(LutCatalog.None.name, s.selectedLutForVideo)
    }
}
