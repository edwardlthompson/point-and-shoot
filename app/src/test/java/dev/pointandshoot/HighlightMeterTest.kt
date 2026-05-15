package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightMeterTest {

    private fun emptyHist(): IntArray = IntArray(256)

    @Test
    fun `suggestEvCorrection matches breakdown times instant engagement`() {
        val h = emptyHist().also { it[254] = 1_000 }
        val full = HighlightMeter.suggestEvCorrection(h)
        val b = HighlightMeter.suggestEvCorrectionBreakdown(h)
        val fromBreakdown =
            if (b.evCore < 0.0) {
                b.evCore * b.darkenEngagement
            } else {
                b.evCore
            }
        assertEquals(fromBreakdown, full, 1e-9)
    }

    @Test
    fun `empty histogram returns zero correction`() {
        val ev = HighlightMeter.suggestEvCorrection(emptyHist())
        assertEquals(0.0, ev, 0.0)
    }

    @Test
    fun `histogram already at ceiling returns zero correction`() {
        val h = emptyHist().also { it[HighlightMeter.DEFAULT_HIGHLIGHT_CEILING] = 1_000 }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertEquals(0.0, ev, 1e-9)
    }

    @Test
    fun `bright histogram suggests negative EV (darken)`() {
        // All weight at 254 -> needs to darken toward default ceiling.
        val h = emptyHist().also { it[254] = 1_000 }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected negative ev to darken; was $ev", ev < 0.0)
    }

    @Test
    fun `evenly dark histogram without bright tail stays neutral`() {
        // No pixels near white / no upper tail → H mode should not lift exposure vs normal AE.
        val h = emptyHist().also { it[30] = 1_000 }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected near-zero ev; was $ev", kotlin.math.abs(ev) < 0.02)
    }

    @Test
    fun `correction is clamped to maxAbsEv`() {
        // Extremely bright histogram - raw correction would exceed the clamp.
        val h = emptyHist().also { it[255] = 1_000 }
        val ev = HighlightMeter.suggestEvCorrection(h, maxAbsEv = 1.5)
        assertTrue("expected |ev| <= 1.5; was $ev", kotlin.math.abs(ev) <= 1.5 + 1e-9)
    }

    @Test
    fun `percentile finds the right bin`() {
        // 90% of pixels at bin 100, 10% at bin 250. Tail + peak both land in bin 250 -> darken.
        val h = emptyHist().apply {
            this[100] = 900
            this[250] = 100
        }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected darken (negative ev); was $ev", ev < 0.0)
    }

    @Test
    fun `small bright region still darkens with default bright tail`() {
        // Classic failure of a low "P95" style meter: 98% dark, 2% very bright sky/specular.
        // Bulk lower-tail stays in bin 40; only the bright tail should pull exposure down.
        val h = emptyHist().apply {
            this[40] = 980
            this[250] = 20
        }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected darken (negative ev); was $ev", ev < 0.0)
    }

    @Test
    fun `lower percentile finds the dominant bin`() {
        // percentile=0.50 -> 50th percentile lands in bin 100; use a high ceiling so 100 < ceiling -> brighten.
        val h = emptyHist().apply {
            this[100] = 900
            this[250] = 100
        }
        val ev = HighlightMeter.suggestEvCorrection(h, percentile = 0.50, ceilingValue = 120)
        assertTrue("expected brighten (positive ev); was $ev", ev > 0.0)
    }

    @Test
    fun `tiny sun disk against huge dark bulk still darkens`() {
        // Sun is ~0.1% of pixels; lower-tail alone can sit in the shadows. Peak detector must win.
        val h = emptyHist().apply {
            this[50] = 999_000
            this[255] = 1_000
        }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected darken (negative ev); was $ev", ev < 0.0)
    }

    @Test
    fun `near clip applies minimum darken step`() {
        val h = emptyHist().also { it[255] = 10_000 }
        val ev = HighlightMeter.suggestEvCorrection(h, maxAbsEv = 10.0)
        assertTrue("expected strong near-clip darken after gain; was $ev", ev <= -9.0)
    }

    @Test
    fun `single hot pixel still darkens aggressively`() {
        val h = emptyHist().apply {
            this[40] = 99_999
            this[255] = 1
        }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected strong darken; was $ev", ev <= -12.0)
    }

    @Test
    fun `bright upper tail still pulls darken with floor`() {
        // Bulk mid + enough mass high that engagement ramps in; floor still applies vs ceiling.
        val h =
            emptyHist().apply {
                this[48] = 398_500
                this[236] = 2_500
            }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected strong darken after engagement + gain; was $ev", ev <= -3.5)
    }

    @Test
    fun `uniform bright mid key without near clip mass stays neutral`() {
        val h = emptyHist().apply {
            for (b in 160..195) this[b] = 5_000
        }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected near-neutral; was $ev", kotlin.math.abs(ev) < 0.06)
    }

    @Test
    fun `even mid key scene without bright tail returns near zero`() {
        val h = emptyHist().apply {
            for (b in 90..130) this[b] = 2_000
        }
        val ev = HighlightMeter.suggestEvCorrection(h)
        assertTrue("expected near-neutral; was $ev", kotlin.math.abs(ev) < 0.08)
    }

    @Test
    fun `diffuse ceiling blend is low for specular-like statistics`() {
        val w = HighlightMeter.diffuseCeilingBlendWeight(0.001, 48)
        assertTrue("expected near-zero diffuse blend; was $w", w < 0.06)
    }

    @Test
    fun `diffuse ceiling blend rises when bulk and hot fraction are both high`() {
        val w = HighlightMeter.diffuseCeilingBlendWeight(0.06, 102)
        assertTrue("expected substantial diffuse blend; was $w", w > 0.35)
    }

    @Test
    fun `uniform high-key scene requests less darken than tiny hotspot on dark bulk`() {
        val tinyHot =
            emptyHist().apply {
                this[45] = 998_000
                this[248] = 2_000
            }
        val highKey =
            emptyHist().apply {
                for (b in 115..165) this[b] = 4_000
                this[198] = 40_000
            }
        val evTiny = HighlightMeter.suggestEvCorrection(tinyHot)
        val evKey = HighlightMeter.suggestEvCorrection(highKey)
        assertTrue(
            "expected tiny-hot (specular) correction more negative than high-key; tiny=$evTiny key=$evKey",
            evTiny < evKey,
        )
    }
}
