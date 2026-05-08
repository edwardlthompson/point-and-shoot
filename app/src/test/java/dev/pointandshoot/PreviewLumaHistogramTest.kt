package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewLumaHistogramTest {

    @Test
    fun `reduceY8 produces 256 bins`() {
        val hist = PreviewLumaHistogram.reduceY8(byteArrayOf(0, 1, 2, 3), 2, 2)
        assertEquals(PreviewLumaHistogram.BIN_COUNT, hist.size)
    }

    @Test
    fun `reduceY8 counts every pixel exactly once`() {
        val plane = ByteArray(64) { it.toByte() } // 8x8 ramp 0..63
        val hist = PreviewLumaHistogram.reduceY8(plane, 8, 8)
        for (i in 0 until 64) {
            assertEquals("bin $i", 1, hist[i])
        }
        for (i in 64 until 256) {
            assertEquals("bin $i (unused)", 0, hist[i])
        }
        assertEquals(64L, PreviewLumaHistogram.pixelCount(hist))
    }

    @Test
    fun `reduceY8 treats bytes as unsigned`() {
        // -1 (signed Byte) == 255 (unsigned)
        val plane = byteArrayOf(-1, -1, -2, 0)
        val hist = PreviewLumaHistogram.reduceY8(plane, 2, 2)
        assertEquals(2, hist[255])
        assertEquals(1, hist[254])
        assertEquals(1, hist[0])
    }

    @Test
    fun `reduceY8 rejects non-positive dimensions`() {
        try {
            PreviewLumaHistogram.reduceY8(byteArrayOf(0, 1), 0, 1)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* expected */ }
        try {
            PreviewLumaHistogram.reduceY8(byteArrayOf(0, 1), 2, -1)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `reduceY8 rejects undersized buffer`() {
        try {
            PreviewLumaHistogram.reduceY8(byteArrayOf(0, 1, 2), 2, 2)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `reduceYuv420Y honors row stride padding`() {
        // 4x2 visible, stride = 8 (4 padding bytes per row).
        val plane = ByteArray(16)
        // Row 0: visible 1,2,3,4 then padding 0,0,0,0
        plane[0] = 1; plane[1] = 2; plane[2] = 3; plane[3] = 4
        // Row 1: visible 5,6,7,8 then padding
        plane[8] = 5; plane[9] = 6; plane[10] = 7; plane[11] = 8

        val hist = PreviewLumaHistogram.reduceYuv420Y(plane, 4, 2, 8)
        for (v in 1..8) assertEquals("bin $v", 1, hist[v])
        // Padding bytes (bin 0) must NOT be counted.
        assertEquals(0, hist[0])
        assertEquals(8L, PreviewLumaHistogram.pixelCount(hist))
    }

    @Test
    fun `reduceYuv420Y rejects rowStride less than width`() {
        try {
            PreviewLumaHistogram.reduceYuv420Y(ByteArray(16), 4, 2, 3)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `reduceYuv420YCenterWeighted equals base when centerWeight is 1`() {
        val plane = ByteArray(16) { it.toByte() }
        val base = PreviewLumaHistogram.reduceYuv420Y(plane, 4, 4, 4)
        val weighted = PreviewLumaHistogram.reduceYuv420YCenterWeighted(
            plane, 4, 4, 4, centerFrac = 0.5f, centerWeight = 1,
        )
        assertTrue(base.contentEquals(weighted))
    }

    @Test
    fun `reduceYuv420YCenterWeighted multiplies center pixels`() {
        // 4x4 frame, center 2x2 = pixels at (1,1), (2,1), (1,2), (2,2)
        // = indices 5, 6, 9, 10 (which carry byte values 5, 6, 9, 10).
        val plane = ByteArray(16) { it.toByte() }
        val weighted = PreviewLumaHistogram.reduceYuv420YCenterWeighted(
            plane, 4, 4, 4, centerFrac = 0.5f, centerWeight = 3,
        )
        // Each center pixel contributes 1 (base) + 2 (extra) = 3.
        assertEquals(3, weighted[5])
        assertEquals(3, weighted[6])
        assertEquals(3, weighted[9])
        assertEquals(3, weighted[10])
        // Non-center pixels are untouched.
        assertEquals(1, weighted[0])
        assertEquals(1, weighted[15])
        // Total = 16 base + 8 extra = 24.
        assertEquals(24L, PreviewLumaHistogram.pixelCount(weighted))
    }

    @Test
    fun `reduceYuv420YCenterWeighted clamps centerFrac`() {
        val plane = ByteArray(16) { it.toByte() }
        // centerFrac = 0 must short-circuit to base
        val zero = PreviewLumaHistogram.reduceYuv420YCenterWeighted(
            plane, 4, 4, 4, centerFrac = 0f, centerWeight = 5,
        )
        assertEquals(16L, PreviewLumaHistogram.pixelCount(zero))
    }

    @Test
    fun `reduceYuv420YCenterWeighted rejects out-of-range parameters`() {
        val plane = ByteArray(16)
        try {
            PreviewLumaHistogram.reduceYuv420YCenterWeighted(plane, 4, 4, 4, centerFrac = 1.5f)
            org.junit.Assert.fail("centerFrac > 1")
        } catch (_: IllegalArgumentException) { /* expected */ }
        try {
            PreviewLumaHistogram.reduceYuv420YCenterWeighted(plane, 4, 4, 4, centerWeight = 0)
            org.junit.Assert.fail("centerWeight < 1")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `histogram feeds HighlightMeter end-to-end`() {
        // Synthetic scene with 200 mid-luma + 56 highlight pixels.
        val plane = ByteArray(256)
        for (i in 0 until 200) plane[i] = 120.toByte() // mid
        for (i in 200 until 256) plane[i] = 240.toByte() // highlight
        val hist = PreviewLumaHistogram.reduceY8(plane, 16, 16)
        // The histogram should report 200 in bin 120 and 56 in bin 240.
        assertEquals(200, hist[120])
        assertEquals(56, hist[240])
        // 95th percentile lands at 240 == default ceiling; HighlightMeter
        // returns 0 EV (no correction needed because highlights are exactly
        // at the protected ceiling).
        val ev = HighlightMeter.suggestEvCorrection(hist)
        assertEquals(0.0, ev, 1e-6)
    }

    @Test
    fun `default center constants match the documented values`() {
        assertEquals(0.5f, PreviewLumaHistogram.DEFAULT_CENTER_FRAC, 0f)
        assertEquals(3, PreviewLumaHistogram.DEFAULT_CENTER_WEIGHT)
    }

    @Test
    fun `pixelCount sums every bin`() {
        val hist = IntArray(PreviewLumaHistogram.BIN_COUNT) { if (it < 10) it else 0 }
        // Sum of 0..9 = 45
        assertEquals(45L, PreviewLumaHistogram.pixelCount(hist))
    }
}
