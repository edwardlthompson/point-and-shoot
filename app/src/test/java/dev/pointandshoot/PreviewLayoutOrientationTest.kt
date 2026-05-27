package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewLayoutOrientationTest {

    @Test
    fun `effective rotation applies 90 CCW correction from stored Spin steps`() {
        assertEquals(270, effectivePreviewStaticRotationDeg(0, true))
        assertEquals(270, effectivePreviewStaticRotationDeg(0, false))
        assertEquals(180, effectivePreviewStaticRotationDeg(270, true))
        assertEquals(0, effectivePreviewStaticRotationDeg(90, true))
    }

    @Test
    fun `effective rotation normalizes negatives`() {
        assertEquals(180, effectivePreviewStaticRotationDeg(-90, false))
    }

    @Test
    fun `previewBufferDimensionsForDisplay swaps only landscape WxH`() {
        assertEquals(1440 to 1920, previewBufferDimensionsForDisplay(1920, 1440, 90))
        assertEquals(1440 to 1920, previewBufferDimensionsForDisplay(1920, 1440, null))
        assertEquals(1440 to 1920, previewBufferDimensionsForDisplay(1440, 1920, 90))
        assertEquals(1440 to 1920, previewBufferDimensionsForDisplay(1440, 1920, null))
    }
}
