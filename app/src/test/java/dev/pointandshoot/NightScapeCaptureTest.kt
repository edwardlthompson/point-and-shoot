package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightScapeCaptureTest {
    @Test
    fun normalizeFrameCount_snapsToSupportedOptions() {
        assertEquals(4, NightScapeCapture.normalizeFrameCount(3))
        assertEquals(6, NightScapeCapture.normalizeFrameCount(7))
        assertEquals(8, NightScapeCapture.normalizeFrameCount(99))
    }

    @Test
    fun downsampleLuma_averagesBlock() {
        val w = 8
        val h = 8
        val rgb = ByteArray(w * h * 3) { 100.toByte() }
        val luma = NightScapeCapture.downsampleLuma(rgb, w, h, factor = 4)
        assertEquals(2 * 2, luma.size)
        assertTrue(luma.all { it == 100 })
    }

    @Test
    fun estimateShiftDs_zeroForIdentical() {
        val w = 8
        val h = 8
        val ref = IntArray(w * h) { it }
        val (dx, dy) = NightScapeCapture.estimateShiftDs(ref, w, h, ref, w, h)
        assertEquals(0, dx)
        assertEquals(0, dy)
    }

    @Test
    fun averageBlend_averagesAlignedPixels() {
        val w = 2
        val h = 2
        val a = byteArrayOf(
            100.toByte(), 0, 0, 200.toByte(), 0, 0,
            50.toByte(), 0, 0, 150.toByte(), 0, 0,
        )
        val b = byteArrayOf(
            200.toByte(), 0, 0, 100.toByte(), 0, 0,
            150.toByte(), 0, 0, 50.toByte(), 0, 0,
        )
        val out =
            NightScapeCapture.averageBlend(
                listOf(a, b),
                w,
                h,
                listOf(0 to 0, 0 to 0),
            )
        assertEquals(150.toByte(), out[0])
        assertEquals(150.toByte(), out[3])
        assertEquals(100.toByte(), out[6])
        assertEquals(100.toByte(), out[9])
    }
}
