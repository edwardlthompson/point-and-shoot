package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewLutSelectionTest {

    @Test
    fun `recording uses video lut`() {
        val hud =
            HudSettings(
                selectedLutForStills = LutCatalog.BwBt601.name,
                selectedLutForVideo = LutCatalog.PnsCinematic.name,
            )
        assertEquals(
            LutCatalog.PnsCinematic,
            PreviewLutSelection.activeCatalog(isRecording = true, videoPrimary = true, hud = hud),
        )
    }

    @Test
    fun `video primary preview uses video lut before record`() {
        val hud =
            HudSettings(
                selectedLutForStills = LutCatalog.BwBt601.name,
                selectedLutForVideo = LutCatalog.PnsCinematic.name,
            )
        assertEquals(
            LutCatalog.PnsCinematic,
            PreviewLutSelection.activeCatalog(isRecording = false, videoPrimary = true, hud = hud),
        )
    }

    @Test
    fun `photo primary preview uses stills lut when idle`() {
        val hud =
            HudSettings(
                selectedLutForStills = LutCatalog.BwBt601.name,
                selectedLutForVideo = LutCatalog.PnsCinematic.name,
            )
        assertEquals(
            LutCatalog.BwBt601,
            PreviewLutSelection.activeCatalog(isRecording = false, videoPrimary = false, hud = hud),
        )
    }
}
