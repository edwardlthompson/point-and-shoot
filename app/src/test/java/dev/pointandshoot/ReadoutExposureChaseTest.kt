package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadoutExposureChaseTest {
    @Test
    fun medianBin_findsHistogramCenter() {
        val hist = IntArray(256)
        hist[100] = 50
        hist[101] = 50
        assertEquals(100, ReadoutExposureChase.medianBin(hist))
    }

    @Test
    fun adjustExposureNs_deadbandHoldsSteady() {
        val ema = (ReadoutExposureChase.TARGET_MEDIAN_BIN + 4).toDouble()
        val r = ReadoutExposureChase.adjustExposureNs(50_000_000L, ema, expRange = null)
        assertFalse(r.applied)
        assertEquals(50_000_000L, r.value)
    }

    @Test
    fun adjustExposureNs_blendsTowardBrighterScene() {
        var ema = Double.NaN
        ema = ReadoutExposureChase.smoothMedian(ema, 22)
        var ns = 50_000_000L
        repeat(12) {
            val r = ReadoutExposureChase.adjustExposureNs(ns, ema, expRange = null)
            ema = r.medianEma
            ns = r.value
        }
        assertTrue(ns > 50_000_000L)
    }

    @Test
    fun adjustIso_respectsBandClamp() {
        var ema = ReadoutExposureChase.smoothMedian(Double.NaN, 60)
        val r = ReadoutExposureChase.adjustIso(200, ema, isoRange = null, ReadoutIsoBand.fromBounds(100, 400))
        assertTrue(r.value in 100..400)
    }

    @Test
    fun adjustExposureNsFromEv_negativeEvShortens() {
        val r = ReadoutExposureChase.adjustExposureNsFromEv(40_000_000L, -0.12, null)
        assertTrue(r.applied)
        assertTrue(r.value < 40_000_000L)
    }

    @Test
    fun darkenExposureNs_oneStopHalves() {
        assertEquals(25_000_000L, ReadoutExposureChase.darkenExposureNs(50_000_000L, 1.0, null))
    }

    @Test
    fun darkenExposureNs_twoStopsHalves() {
        assertEquals(10_000_000L, ReadoutExposureChase.darkenExposureNs(40_000_000L, 2.0, null))
    }

    @Test
    fun needsYuvHistogramSample_trueForChaseOnly() {
        assertTrue(
            ReadoutExposureChase.needsYuvHistogramSample(
                wantHighlight = false,
                wantHist = false,
                wantZebra = false,
                wantFalseColor = false,
                wantChase = true,
            ),
        )
        assertFalse(
            ReadoutExposureChase.needsYuvHistogramSample(
                wantHighlight = false,
                wantHist = false,
                wantZebra = false,
                wantFalseColor = false,
                wantChase = false,
            ),
        )
    }
}
