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
}
