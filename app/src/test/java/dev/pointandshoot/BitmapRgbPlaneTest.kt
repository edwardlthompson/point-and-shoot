package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Pure-data tests for [BitmapRgbPlane.srgbByteToLinear] +
 * [BitmapRgbPlane.scaledDimensionsFor]. The Bitmap-touching `fromBitmap`
 * helper requires Robolectric or instrumentation; we verify only the
 * Android-free helpers here.
 */
class BitmapRgbPlaneTest {

    // ---------- srgbByteToLinear ----------

    @Test
    fun `srgbByteToLinear 0 returns 0`() {
        assertEquals(0f, BitmapRgbPlane.srgbByteToLinear(0), 1e-6f)
    }

    @Test
    fun `srgbByteToLinear 255 returns 1`() {
        assertEquals(1f, BitmapRgbPlane.srgbByteToLinear(255), 1e-6f)
    }

    @Test
    fun `srgbByteToLinear small values use linear segment`() {
        // For v <= 0.04045 (i.e., byte <= 10), the curve is v/12.92.
        val byteVal = 8
        val expected = (byteVal / 255.0 / 12.92).toFloat()
        assertEquals(expected, BitmapRgbPlane.srgbByteToLinear(byteVal), 1e-6f)
    }

    @Test
    fun `srgbByteToLinear midtone matches 2_4 power law`() {
        // 0x80 = 128 -> v = 0.502 -> ((0.502 + 0.055)/1.055)^2.4 ~ 0.2159
        val v = 128 / 255.0
        val expected = ((v + 0.055) / 1.055).pow(2.4).toFloat()
        assertEquals(expected, BitmapRgbPlane.srgbByteToLinear(128), 1e-6f)
        // Sanity: midtone gray in linear-light is well below 0.5.
        assertTrue("midtone should be ~0.21, got $expected", expected in 0.20f..0.22f)
    }

    @Test
    fun `srgbByteToLinear is monotonically non-decreasing`() {
        var prev = -1f
        for (b in 0..255) {
            val now = BitmapRgbPlane.srgbByteToLinear(b)
            assertTrue("non-decreasing failed at $b: prev=$prev now=$now", now >= prev - 1e-7f)
            prev = now
        }
    }

    @Test
    fun `srgbByteToLinear clamps out-of-range bytes`() {
        // The Android Bitmap pipeline always produces 0..255, but the helper is defensive.
        assertEquals(0f, BitmapRgbPlane.srgbByteToLinear(-5), 1e-6f)
        assertEquals(1f, BitmapRgbPlane.srgbByteToLinear(300), 1e-6f)
    }

    // ---------- scaledDimensionsFor ----------

    @Test
    fun `scaledDimensionsFor returns input when below cap`() {
        assertEquals(800 to 600, BitmapRgbPlane.scaledDimensionsFor(800, 600, 1024))
        assertEquals(1024 to 1024, BitmapRgbPlane.scaledDimensionsFor(1024, 1024, 1024))
    }

    @Test
    fun `scaledDimensionsFor downsamples landscape preserving aspect`() {
        val (w, h) = BitmapRgbPlane.scaledDimensionsFor(4000, 3000, 1024)
        assertEquals(1024, w)
        // 3000 * 1024 / 4000 = 768
        assertEquals(768, h)
    }

    @Test
    fun `scaledDimensionsFor downsamples portrait preserving aspect`() {
        val (w, h) = BitmapRgbPlane.scaledDimensionsFor(2000, 4000, 1024)
        assertEquals(512, w)
        assertEquals(1024, h)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scaledDimensionsFor rejects zero dimensions`() {
        BitmapRgbPlane.scaledDimensionsFor(0, 1024, 1024)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scaledDimensionsFor rejects zero maxEdge`() {
        BitmapRgbPlane.scaledDimensionsFor(1024, 1024, 0)
    }
}
