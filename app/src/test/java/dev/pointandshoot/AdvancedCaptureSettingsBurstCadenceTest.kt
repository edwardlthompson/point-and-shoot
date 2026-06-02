package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class AdvancedCaptureSettingsBurstCadenceTest {
    @Test
    fun burstCadencePresets_useSingleFleetPreset() {
        val presets = AdvancedCaptureSettings.burstCadencePresets
        assertEquals(listOf("fleet_max"), presets.map { it.key })
        assertEquals(17, presets.first().intervalMs)
    }

    @Test
    fun burstCadenceFps_matchesIntervalMath() {
        assertEquals(58.82, AdvancedCaptureSettings.burstCadenceFps(17), 0.01)
    }
}
