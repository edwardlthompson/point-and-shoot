package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint **15.20** — PPM segment math. */
class PpmAudioMeterTest {
    @Test
    fun minus3_dbfs_is_about_0708_linear() {
        val linear = Math.pow(10.0, -3.0 / 20.0).toFloat()
        assertEquals(0.708f, linear, 0.02f)
    }

    @Test
    fun minus3_dbfs_maps_to_top_segments() {
        val linear = 0.708f
        val segmentCount = 12
        val lit = ((linear * segmentCount).toInt()).coerceIn(0, segmentCount)
        assertEquals(8, lit)
    }
}
