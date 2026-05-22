package dev.pointandshoot

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewAdaptiveFpsPolicyTest {
    @Test
    fun noCap_whenBatteryAndThermalOk() {
        val d = PreviewAdaptiveFpsPolicy.decide(120, 85, PowerManager.THERMAL_STATUS_NONE)
        assertEquals(120, d.effectiveFps)
        assertNull(d.capFps)
    }

    @Test
    fun lowBattery_caps120to60() {
        val d = PreviewAdaptiveFpsPolicy.decide(120, 18, PowerManager.THERMAL_STATUS_NONE)
        assertEquals(60, d.effectiveFps)
        assertEquals(60, d.capFps)
    }

    @Test
    fun severeThermal_caps120to60() {
        val d =
            PreviewAdaptiveFpsPolicy.decide(
                120,
                90,
                PowerManager.THERMAL_STATUS_SEVERE,
            )
        assertEquals(60, d.effectiveFps)
    }

    @Test
    fun criticalBattery_and_moderateThermal_useStricterCap() {
        val d =
            PreviewAdaptiveFpsPolicy.decide(
                240,
                8,
                PowerManager.THERMAL_STATUS_MODERATE,
            )
        assertEquals(30, d.effectiveFps)
    }

    @Test
    fun userFpsAlreadyBelowCap_unchanged() {
        val d = PreviewAdaptiveFpsPolicy.decide(30, 5, PowerManager.THERMAL_STATUS_SEVERE)
        assertEquals(30, d.effectiveFps)
        assertNull(d.capFps)
    }
}
