package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YuvPlaneLayoutTest {

    @Test
    fun `tight luma plane end is width times height when stride matches`() {
        assertEquals(64, YuvPlaneLayout.neededEnd(start = 0, width = 8, height = 8, rowStride = 8, pixelStride = 1))
    }

    @Test
    fun `accounts for buffer position as origin`() {
        assertEquals(
            20,
            YuvPlaneLayout.neededEnd(start = 4, width = 4, height = 4, rowStride = 4, pixelStride = 1),
        )
    }

    @Test
    fun `interleaved chroma uses pixelStride 2`() {
        // 4×2 chroma samples, rowStride 8, pixelStride 2: lastRowBytes=(4-1)*2+1=7.
        assertEquals(
            19,
            YuvPlaneLayout.neededEnd(start = 4, width = 4, height = 2, rowStride = 8, pixelStride = 2),
        )
    }

    @Test
    fun `rejects rowStride shorter than last sample`() {
        assertNull(YuvPlaneLayout.neededEnd(start = 0, width = 8, height = 2, rowStride = 4, pixelStride = 1))
    }

    @Test
    fun `inRange uses exclusive limit not capacity`() {
        assertTrue(YuvPlaneLayout.inRange(7, start = 0, end = 8))
        assertFalse(YuvPlaneLayout.inRange(8, start = 0, end = 8))
        assertFalse(YuvPlaneLayout.inRange(3, start = 4, end = 16))
        assertTrue(YuvPlaneLayout.inRange(4, start = 4, end = 16))
    }
}
