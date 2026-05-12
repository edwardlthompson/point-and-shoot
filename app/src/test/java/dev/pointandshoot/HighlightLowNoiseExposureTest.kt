package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightLowNoiseExposureTest {

    @Test
    fun `darken prefers iso drop before shutter when both can move`() {
        val (iso, t) =
            HighlightLowNoiseExposure.applyEvLowNoiseFirst(
                iso = 800,
                exposureNs = 10_000_000L,
                ev = -0.5,
                isoLower = 100,
                isoUpper = 6400,
                expLower = 10_000L,
                expUpper = 100_000_000L,
                maxExposureNs = 33_333_333L,
            )
        assertTrue("expected iso < 800 for −0.5 EV darken; was $iso", iso < 800)
        assertEquals(10_000_000L, t)
    }

    @Test
    fun `darken at iso floor shortens exposure`() {
        val (iso, t) =
            HighlightLowNoiseExposure.applyEvLowNoiseFirst(
                iso = 100,
                exposureNs = 20_000_000L,
                ev = -1.0,
                isoLower = 100,
                isoUpper = 100,
                expLower = 10_000L,
                expUpper = 100_000_000L,
                maxExposureNs = 33_333_333L,
            )
        assertEquals(100, iso)
        assertTrue("expected faster shutter (shorter t); was $t", t < 20_000_000L)
    }

    @Test
    fun `brighten lengthens exposure before raising iso when headroom`() {
        val (iso, t) =
            HighlightLowNoiseExposure.applyEvLowNoiseFirst(
                iso = 400,
                exposureNs = 5_000_000L,
                ev = 0.5,
                isoLower = 100,
                isoUpper = 6400,
                expLower = 10_000L,
                expUpper = 100_000_000L,
                maxExposureNs = 33_333_333L,
            )
        assertEquals(400, iso)
        assertTrue("expected longer exposure; was $t", t > 5_000_000L)
    }
}
