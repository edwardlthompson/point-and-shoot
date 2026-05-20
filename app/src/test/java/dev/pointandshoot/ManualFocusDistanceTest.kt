package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualFocusDistanceTest {

    @Test
    fun `max diopters from minimum focus meters`() {
        assertEquals(10f, ManualFocusDistance.maxDioptersFromMinFocusMeters(0.1f), 0.01f)
    }

    @Test
    fun `clamp respects max`() {
        assertEquals(8f, ManualFocusDistance.clamp(20f, 8f), 0.001f)
    }

    @Test
    fun `drag down increases diopters`() {
        val max = 10f
        val next = ManualFocusDistance.clamp(2f + 40f * 0.0018f, max)
        assertTrue(next > 2f)
    }
}
