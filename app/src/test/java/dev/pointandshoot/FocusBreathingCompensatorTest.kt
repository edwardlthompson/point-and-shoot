package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FocusBreathingCompensatorTest {

    private val config = FocusBreathingCompensator.Config(active = true, k = 0.005f)

    @Before
    fun reset() {
        FocusBreathingCompensator.reset()
    }

    @Test
    fun onFocusDistance_requiresDeltaThreshold() {
        assertEquals(false, FocusBreathingCompensator.onFocusDistance(1.0f, config))
        assertEquals(false, FocusBreathingCompensator.onFocusDistance(1.2f, config))
        assertTrue(FocusBreathingCompensator.onFocusDistance(1.6f, config))
        assertTrue(FocusBreathingCompensator.currentScale() > 1f)
    }

    @Test
    fun onFocusDistance_inactiveResetsScale() {
        FocusBreathingCompensator.onFocusDistance(0f, config)
        FocusBreathingCompensator.onFocusDistance(1f, config)
        assertTrue(FocusBreathingCompensator.currentScale() > 1f)
        FocusBreathingCompensator.onFocusDistance(1f, FocusBreathingCompensator.Config(active = false, k = 0.005f))
        assertEquals(1f, FocusBreathingCompensator.currentScale())
    }
}
