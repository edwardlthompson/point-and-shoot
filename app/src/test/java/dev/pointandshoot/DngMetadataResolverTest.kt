package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DngMetadataResolverTest {

    @Test
    fun pickPhysical_prefersPreviewPinWhenInChildren() {
        val children = setOf("2", "3", "4")
        assertEquals(
            "4",
            DngMetadataResolver.pickPhysicalIdForDng(children, previewPhysicalCameraId = "4", activePhysicalFromResult = "3"),
        )
    }

    @Test
    fun pickPhysical_fallsBackToActiveResultWhenPinAbsentOrUnknown() {
        val children = setOf("2", "3", "4")
        assertEquals(
            "3",
            DngMetadataResolver.pickPhysicalIdForDng(children, previewPhysicalCameraId = "9", activePhysicalFromResult = "3"),
        )
    }

    @Test
    fun pickPhysical_returnsNullWhenNoChildren() {
        assertNull(DngMetadataResolver.pickPhysicalIdForDng(emptySet(), "4", "3"))
    }

    @Test
    fun pickPhysical_returnsNullWhenNoMatch() {
        val children = setOf("2", "3")
        assertNull(
            DngMetadataResolver.pickPhysicalIdForDng(children, previewPhysicalCameraId = "4", activePhysicalFromResult = "5"),
        )
    }
}
