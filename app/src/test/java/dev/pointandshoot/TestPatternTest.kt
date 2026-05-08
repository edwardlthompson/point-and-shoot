package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the procedural test pattern that backs
 * `GLPreviewScreen`. Validates the bar layout, wedge step values, and the
 * sRGB endpoint encoding so a future refactor cannot silently change what
 * the user sees through the LUT pipeline on device.
 */
class TestPatternTest {

    @Test
    fun `color bars expose 8 entries in screen order`() {
        val bars = TestPattern.COLOR_BARS
        assertEquals(8, bars.size)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), bars[0], 0f)
        assertArrayEquals(floatArrayOf(1f, 1f, 0f), bars[1], 0f)
        assertArrayEquals(floatArrayOf(0f, 1f, 1f), bars[2], 0f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), bars[3], 0f)
        assertArrayEquals(floatArrayOf(1f, 0f, 1f), bars[4], 0f)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), bars[5], 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), bars[6], 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), bars[7], 0f)
    }

    @Test
    fun `colorBarFor partitions the width into 8 equal slabs`() {
        val w = 1024
        val left = TestPattern.colorBarFor(0, w)
        val rightmost = TestPattern.colorBarFor(w - 1, w)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), left, 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), rightmost, 0f)
        val centerOfFirstBar = TestPattern.colorBarFor(w / 16, w)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), centerOfFirstBar, 0f)
        val centerOfLastBar = TestPattern.colorBarFor(w - w / 16, w)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), centerOfLastBar, 0f)
    }

    @Test
    fun `wedgeStepFor produces 11 monotonic gray levels`() {
        val w = 1024
        val seen = mutableSetOf<Float>()
        for (x in 0 until w) {
            val step = TestPattern.wedgeStepFor(x, w)
            assertEquals(step[0], step[1], 0f)
            assertEquals(step[1], step[2], 0f)
            seen += step[0]
        }
        assertEquals(TestPattern.WEDGE_STEPS, seen.size)
        val sorted = seen.sorted()
        assertEquals(0f, sorted.first(), 0f)
        assertEquals(1f, sorted.last(), 0f)
        for (i in 1 until sorted.size) {
            assertTrue("wedge steps must be strictly increasing", sorted[i] > sorted[i - 1])
        }
    }

    @Test
    fun `smoothRampFor is monotonic across the full width`() {
        val w = 1024
        var prev = -1f
        for (x in 0 until w) {
            val ramp = TestPattern.smoothRampFor(x, w)
            assertEquals(ramp[0], ramp[1], 0f)
            assertEquals(ramp[1], ramp[2], 0f)
            assertTrue("ramp must increase with x", ramp[0] >= prev)
            prev = ramp[0]
        }
        assertEquals(0f, TestPattern.smoothRampFor(0, w)[0], 0f)
        assertEquals(1f, TestPattern.smoothRampFor(w - 1, w)[0], 0f)
    }

    @Test
    fun `generateRgb returns expected length and stays in range`() {
        val w = 32
        val h = 24
        val rgb = TestPattern.generateRgb(w, h)
        assertEquals(w * h * 3, rgb.size)
        for (v in rgb) {
            assertTrue("value $v out of [0, 1]", v in 0f..1f)
        }
    }

    @Test
    fun `generateRgb top band reproduces the color bars`() {
        val w = 32
        val h = 24
        val rgb = TestPattern.generateRgb(w, h)
        val firstRow = FloatArray(3)
        for (x in 0 until w) {
            val idx = x * 3
            firstRow[0] = rgb[idx]
            firstRow[1] = rgb[idx + 1]
            firstRow[2] = rgb[idx + 2]
            val expected = TestPattern.colorBarFor(x, w)
            assertArrayEquals("col $x of top band", expected, firstRow, 0f)
        }
    }

    @Test
    fun `generateRgb bottom band reproduces the smooth ramp`() {
        val w = 32
        val h = 24
        val rgb = TestPattern.generateRgb(w, h)
        val lastRow = h - 1
        val triple = FloatArray(3)
        for (x in 0 until w) {
            val idx = (lastRow * w + x) * 3
            triple[0] = rgb[idx]
            triple[1] = rgb[idx + 1]
            triple[2] = rgb[idx + 2]
            val expected = TestPattern.smoothRampFor(x, w)
            assertArrayEquals("col $x of bottom band", expected, triple, 0f)
        }
    }

    @Test
    fun `generateArgb has correct length and full alpha`() {
        val w = 16
        val h = 12
        val argb = TestPattern.generateArgb(w, h)
        assertEquals(w * h, argb.size)
        for (v in argb) {
            val a = (v ushr 24) and 0xFF
            assertEquals(0xFF, a)
        }
    }

    @Test
    fun `linearToSrgb8 hits the documented endpoints`() {
        assertEquals(0, TestPattern.linearToSrgb8(0f))
        assertEquals(255, TestPattern.linearToSrgb8(1f))
        assertEquals(0, TestPattern.linearToSrgb8(-0.5f))
        assertEquals(255, TestPattern.linearToSrgb8(1.5f))
        val mid = TestPattern.linearToSrgb8(0.5f)
        assertTrue("0.5 linear should encode well above 0.5 sRGB ($mid / 255)", mid >= 187)
    }

    @Test
    fun `generateRgb rejects non-positive dimensions`() {
        var threw = false
        try {
            TestPattern.generateRgb(0, 10)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("generateRgb(0, 10) must throw", threw)
        threw = false
        try {
            TestPattern.generateRgb(10, -1)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("generateRgb(10, -1) must throw", threw)
    }

    @Test
    fun `pure-channel bars do not collide with adjacent bars`() {
        val w = 1024
        val red = TestPattern.colorBarFor(w * 11 / 16, w)
        val blue = TestPattern.colorBarFor(w * 13 / 16, w)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), red, 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), blue, 0f)
        assertNotEquals("red and blue bars must differ", red.contentHashCode(), blue.contentHashCode())
    }

    @Test
    fun `default reference dimensions match the documented constants`() {
        assertEquals(1024, TestPattern.WIDTH)
        assertEquals(768, TestPattern.HEIGHT)
        val rgb = TestPattern.generateRgb()
        assertEquals(TestPattern.WIDTH * TestPattern.HEIGHT * 3, rgb.size)
    }
}
