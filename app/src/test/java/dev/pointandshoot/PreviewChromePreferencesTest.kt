package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewChromePreferencesTest {
    @Test
    fun normalizeSelfTimerDelaySec_snapsUnknownToZero() {
        assertEquals(0, PreviewChromePreferences.normalizeSelfTimerDelaySec(-1))
        assertEquals(0, PreviewChromePreferences.normalizeSelfTimerDelaySec(7))
        assertEquals(3, PreviewChromePreferences.normalizeSelfTimerDelaySec(3))
    }

    @Test
    fun cycleSelfTimerDelaySec_wrapsAndNormalizes() {
        assertEquals(3, PreviewChromePreferences.cycleSelfTimerDelaySec(0))
        assertEquals(5, PreviewChromePreferences.cycleSelfTimerDelaySec(3))
        assertEquals(10, PreviewChromePreferences.cycleSelfTimerDelaySec(5))
        assertEquals(0, PreviewChromePreferences.cycleSelfTimerDelaySec(10))
        assertEquals(3, PreviewChromePreferences.cycleSelfTimerDelaySec(99))
    }
}
