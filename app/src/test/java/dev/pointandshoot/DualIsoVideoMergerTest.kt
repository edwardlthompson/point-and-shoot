package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualIsoVideoMergerTest {

    @Test
    fun merge_returnsInputUnchanged() {
        val frame = byteArrayOf(1, 2, 3, 4, 5)
        assertArrayEquals(frame, DualIsoVideoMerger.merge(frame))
    }

    @Test
    fun isSupportedOnDevice_followsMultiResProbe() {
        assertTrue(DualIsoVideoMerger.isSupportedOnDevice(multiResMapPresent = true))
        assertFalse(DualIsoVideoMerger.isSupportedOnDevice(multiResMapPresent = false))
    }

    @Test
    fun probeMultiResStreamConfigurationMap_nullCharsIsFalse() {
        assertFalse(DualIsoVideoMerger.probeMultiResStreamConfigurationMap(null))
    }

    @Test
    fun hudSettings_dualIsoVideoActive_requiresBoth() {
        val prefs = HudSettings(dualIsoVideoEnabled = true)
        assertTrue(prefs.dualIsoVideoActive(multiResSupported = true))
        assertFalse(prefs.dualIsoVideoActive(multiResSupported = false))
        assertFalse(prefs.copy(dualIsoVideoEnabled = false).dualIsoVideoActive(multiResSupported = true))
    }
}
