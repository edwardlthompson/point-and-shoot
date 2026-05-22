package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualFocusDistanceTest {

    @Test
    fun `max diopters uses hal value directly in diopters`() {
        assertEquals(10f, ManualFocusDistance.maxDioptersFromHalMinimumFocus(10f), 0.01f)
        assertEquals(15f, ManualFocusDistance.maxDioptersFromHalMinimumFocus(15f), 0.01f)
    }

    @Test
    fun `zero hal means fixed at infinity`() {
        assertEquals(0f, ManualFocusDistance.maxDioptersFromHalMinimumFocus(0f), 0.001f)
    }

    @Test
    fun `unknown hal uses fallback`() {
        assertEquals(8f, ManualFocusDistance.maxDioptersFromHalMinimumFocus(null), 0.001f)
    }

    @Test
    fun `legacy alias matches hal diopters`() {
        assertEquals(
            ManualFocusDistance.maxDioptersFromHalMinimumFocus(12f),
            ManualFocusDistance.maxDioptersFromMinFocusMeters(12f),
            0.001f,
        )
    }

    @Test
    fun `clamp respects max`() {
        assertEquals(8f, ManualFocusDistance.clamp(20f, 8f), 0.001f)
    }

    @Test
    fun `drag right increases diopters`() {
        val max = 10f
        val next = ManualFocusDistance.clamp(2f + 40f * 0.0018f, max)
        assertTrue(next > 2f)
    }

    @Test
    fun `focus range slider disabled when fixed`() {
        val range =
            ManualFocusDistance.FocusRange(
                maxDiopters = 0f,
                fixedAtInfinity = true,
                halMinimumFocusDiopters = 0f,
            )
        assertFalse(range.sliderEnabled)
    }
}
