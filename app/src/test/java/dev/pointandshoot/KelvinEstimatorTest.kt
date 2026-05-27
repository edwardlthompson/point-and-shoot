package dev.pointandshoot

import org.junit.Assert.assertTrue
import org.junit.Test

class KelvinEstimatorTest {
    @Test
    fun warm_tilt_is_low_kelvin() {
        assertTrue(KelvinEstimator.estimateFromRgGainTilt(0.7f) <= 3200)
    }

    @Test
    fun cool_tilt_is_high_kelvin() {
        assertTrue(KelvinEstimator.estimateFromRgGainTilt(1.4f) >= 6000)
    }
}
