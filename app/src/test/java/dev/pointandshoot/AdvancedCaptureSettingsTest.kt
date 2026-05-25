package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdvancedCaptureSettingsTest {
    @Test
    fun normalizeBurstCount_snapsToCatalog() {
        assertEquals(3, AdvancedCaptureSettings.normalizeBurstCount(4))
        assertEquals(20, AdvancedCaptureSettings.normalizeBurstCount(99))
    }

    @Test
    fun normalizeIntervalometerSec_off() {
        assertEquals(0, AdvancedCaptureSettings.normalizeIntervalometerSec(-1))
        assertEquals(5, AdvancedCaptureSettings.normalizeIntervalometerSec(6))
    }

    @Test
    fun intervalometerSecOptions_includesOff() {
        assertFalse(AdvancedCaptureSettings.intervalometerSecOptions.contains(-1))
        assertEquals(0, AdvancedCaptureSettings.intervalometerSecOptions.first())
    }
}
