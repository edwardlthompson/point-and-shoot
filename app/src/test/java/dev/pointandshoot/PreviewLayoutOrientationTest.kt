package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewLayoutOrientationTest {

    @Test
    fun `effective rotation follows static deg only portrait flag ignored`() {
        assertEquals(0, effectivePreviewStaticRotationDeg(0, true))
        assertEquals(0, effectivePreviewStaticRotationDeg(0, false))
        assertEquals(270, effectivePreviewStaticRotationDeg(270, true))
        assertEquals(90, effectivePreviewStaticRotationDeg(90, true))
    }

    @Test
    fun `effective rotation normalizes negatives`() {
        assertEquals(270, effectivePreviewStaticRotationDeg(-90, false))
    }
}
