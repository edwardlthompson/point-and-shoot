package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadoutIsoBandTest {
    @Test
    fun band100_400_filtersStopsAndClampsWithoutHalRange() {
        val stops = ReadoutIsoBand.BAND_100_400.filterStops(range = null)
        assertTrue(stops.all { it in 100..400 })
        assertTrue(stops.contains(400))
        assertFalse(stops.contains(800))
        assertEquals(400, ReadoutIsoBand.BAND_100_400.clampPick(range = null, value = 3200))
        assertEquals(200, ReadoutIsoBand.BAND_100_400.clampPick(range = null, value = 200))
    }

    @Test
    fun fullRange_includesHighStopsWithoutHalRange() {
        val stops = ReadoutIsoBand.FULL.filterStops(range = null)
        assertTrue(stops.contains(6400))
    }

    @Test
    fun clampPick_ceilingAndFloor() {
        assertEquals(400, ReadoutIsoBand.BAND_100_400.clampPick(range = null, value = 3200))
        assertEquals(100, ReadoutIsoBand.BAND_100_400.clampPick(range = null, value = 50))
    }

    @Test
    fun aeCoupling_fromOverrides() {
        assertEquals(ReadoutAeCoupling.AUTO, ReadoutAeCoupling.fromOverrides(null, null))
        assertEquals(ReadoutAeCoupling.LOCKED_ISO_AUTO_SS, ReadoutAeCoupling.fromOverrides(400, null))
        assertEquals(ReadoutAeCoupling.LOCKED_SS_AUTO_ISO, ReadoutAeCoupling.fromOverrides(null, 33_333_333L))
        assertEquals(ReadoutAeCoupling.MANUAL_BOTH, ReadoutAeCoupling.fromOverrides(800, 50_000_000L))
    }
}
