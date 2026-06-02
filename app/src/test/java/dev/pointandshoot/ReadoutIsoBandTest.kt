package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadoutIsoBandTest {
    @Test
    fun band100_400_filtersStopsAndClampsWithoutHalRange() {
        val band = ReadoutIsoBand.fromBounds(100, 400)
        val stops = band.filterStops(range = null)
        assertTrue(stops.all { it in 100..400 })
        assertTrue(stops.contains(400))
        assertFalse(stops.contains(800))
        assertEquals(400, band.clampPick(range = null, value = 3200))
        assertEquals(200, band.clampPick(range = null, value = 200))
    }

    @Test
    fun fullRange_includesHighStopsWithoutHalRange() {
        val stops = ReadoutIsoBand.AUTO.filterStops(range = null)
        assertTrue(stops.contains(6400))
    }

    @Test
    fun clampPick_ceilingAndFloor() {
        val band = ReadoutIsoBand.fromBounds(100, 400)
        assertEquals(400, band.clampPick(range = null, value = 3200))
        assertEquals(100, band.clampPick(range = null, value = 50))
    }

    @Test
    fun parsePersisted_supportsLegacyEnumNames() {
        assertEquals(ReadoutIsoBand.AUTO, ReadoutIsoBand.parsePersisted("FULL"))
        assertEquals(ReadoutIsoBand.fromBounds(100, 800), ReadoutIsoBand.parsePersisted("BAND_100_800"))
    }

    @Test
    fun parsePersisted_supportsRangeToken() {
        assertEquals(ReadoutIsoBand.fromBounds(100, 800), ReadoutIsoBand.parsePersisted("range:100-800"))
    }

    @Test
    fun aeCoupling_fromOverrides() {
        assertEquals(ReadoutAeCoupling.AUTO, ReadoutAeCoupling.fromOverrides(null, null))
        assertEquals(ReadoutAeCoupling.LOCKED_ISO_AUTO_SS, ReadoutAeCoupling.fromOverrides(400, null))
        assertEquals(ReadoutAeCoupling.LOCKED_SS_AUTO_ISO, ReadoutAeCoupling.fromOverrides(null, 33_333_333L))
        assertEquals(ReadoutAeCoupling.MANUAL_BOTH, ReadoutAeCoupling.fromOverrides(800, 50_000_000L))
    }
}
