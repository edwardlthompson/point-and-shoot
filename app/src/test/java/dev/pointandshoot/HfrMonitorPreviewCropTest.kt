package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HfrMonitorPreviewCropTest {
    @Test
    fun centeredSpan_teleOnWide_approximatesFocalRatio() {
        val crop = HfrMonitorPreviewCrop.centeredSpanForLinearZoom(73.0 / 24.0)
        val span = crop.u1 - crop.u0
        assertEquals(24.0 / 73.0, span.toDouble(), 0.02)
    }

    @Test
    fun centeredSpan_uwRecordOnWide_isFullFrame() {
        val crop = HfrMonitorPreviewCrop.centeredSpanForLinearZoom(14.0 / 24.0)
        assertEquals(HfrMonitorTextureCrop.FULL, crop)
    }

    @Test
    fun centeredSpan_wideRecordOnUw_cropsUwToWideFov() {
        val crop = HfrMonitorPreviewCrop.centeredSpanForLinearZoom(24.0 / 14.0)
        val span = crop.u1 - crop.u0
        assertTrue(span < 0.7f)
        assertEquals(14.0 / 24.0, span.toDouble(), 0.02)
    }

    @Test
    fun roleEquivalent_teleRecordOnWide_uses73over23() {
        val roles =
            BackCameraRoleResolver.rolesFromEnumeratedPhysicalsForTests(
                listOf("2" to 14f, "3" to 24f, "4" to 73f),
            )
        val fTele = HfrMonitorPreviewCrop.roleEquivalentFocalMm("4", roles, null, false)
        val fWide = HfrMonitorPreviewCrop.roleEquivalentFocalMm("3", roles, null, false)
        assertEquals(73f, fTele)
        assertEquals(23f, fWide)
        val crop = HfrMonitorPreviewCrop.centeredSpanForLinearZoom((fTele!! / fWide!!).toDouble())
        assertTrue((crop.u1 - crop.u0) < 0.4f)
    }
}
