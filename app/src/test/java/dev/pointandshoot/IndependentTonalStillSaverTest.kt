package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class IndependentTonalStillSaverTest {
    @Test
    fun fullResolutionNativeEncode_usesSensorDimensions() {
        val (w, h) = BitmapRgbPlane.scaledDimensionsFor(4096, 3072, Int.MAX_VALUE)
        assertEquals(4096, w)
        assertEquals(3072, h)
    }
}
