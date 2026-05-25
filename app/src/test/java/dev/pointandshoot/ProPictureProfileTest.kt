package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProPictureProfileTest {
    @Test
    fun byId_findsKnownPresets() {
        assertNotNull(ProPictureProfiles.byId("cinematic"))
        assertNotNull(ProPictureProfiles.byId("ultra_raw"))
    }

    @Test
    fun byId_unknownReturnsNull() {
        assertNull(ProPictureProfiles.byId("not_a_profile"))
    }

    @Test
    fun applyToHud_setsLutAndProfileId() {
        val profile = ProPictureProfiles.byId("mono709")!!
        val next = profile.applyToHud(HudSettings())
        assertEquals(LutCatalog.BwBt709.name, next.selectedLutForStills)
        assertEquals("mono709", next.selectedPictureProfileId)
    }

    @Test
    fun normalizeId_rejectsUnknown() {
        assertNull(ProPictureProfiles.normalizeId("bogus"))
        assertEquals("neutral", ProPictureProfiles.normalizeId("neutral"))
    }
}
