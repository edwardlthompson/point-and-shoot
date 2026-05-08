package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightMeterTest {

    private fun emptyHist(): IntArray = IntArray(256)

    @Test
    fun `empty histogram returns zero correction`() {
        val ev = HighlightMeter.suggestEvCorrection(emptyHist())
        assertEquals(0.0, ev, 0.0)
    }

    @Test
    fun `histogram already at ceiling returns zero correction`() {
        val h = emptyHist().also { it[240] = 1_000 }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertEquals(0.0, ev, 1e-9)
    }

    @Test
    fun `bright histogram suggests negative EV (darken)`() {
        // All weight at 254 -> needs to darken so 95th percentile lands on 240.
        val h = emptyHist().also { it[254] = 1_000 }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected negative ev to darken; was $ev", ev < 0.0)
    }

    @Test
    fun `dark histogram suggests positive EV (brighten)`() {
        // All weight at bin 30 -> brighten so 95th percentile lands on 240.
        val h = emptyHist().also { it[30] = 1_000 }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected positive ev to brighten; was $ev", ev > 0.0)
    }

    @Test
    fun `correction is clamped to maxAbsEv`() {
        // Extremely bright histogram - raw correction would exceed 3 stops.
        val h = emptyHist().also { it[255] = 1_000 }
        val ev = HighlightMeter.suggestEvCorrection(h, maxAbsEv = 1.5)
        assertTrue("expected |ev| <= 1.5; was $ev", kotlin.math.abs(ev) <= 1.5 + 1e-9)
    }

    @Test
    fun `percentile finds the right bin`() {
        // 90% of pixels at bin 100, 10% at bin 250. With percentile=0.95 (default),
        // the 95th percentile is in bin 250 -> needs to darken.
        val h = emptyHist().apply {
            this[100] = 900
            this[250] = 100
        }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected darken (negative ev); was $ev", ev < 0.0)
    }

    @Test
    fun `lower percentile finds the dominant bin`() {
        // Same distribution but percentile=0.50 -> 50th percentile lands in bin 100,
        // which is well below the ceiling (240) -> we should brighten.
        val h = emptyHist().apply {
            this[100] = 900
            this[250] = 100
        }
        val ev = HighlightMeter.suggestEvCorrection(h, percentile = 0.50)
        assertTrue("expected brighten (positive ev); was $ev", ev > 0.0)
    }
}
