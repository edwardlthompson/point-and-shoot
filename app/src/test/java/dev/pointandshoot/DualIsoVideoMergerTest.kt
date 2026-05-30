package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualIsoVideoMergerTest {

    @Test
    fun merge_blendsShortLongPairs() {
        val frame = byteArrayOf(0, 100.toByte(), 50.toByte(), 200.toByte())
        val merged = DualIsoVideoMerger.merge(frame)
        assertEquals(65, merged[0].toInt() and 0xFF)
        assertEquals(100, merged[1].toInt() and 0xFF)
        assertEquals(147, merged[2].toInt() and 0xFF)
        assertEquals(200, merged[3].toInt() and 0xFF)
    }

    @Test
    fun merge_oddLength_passThrough() {
        val frame = byteArrayOf(1, 2, 3)
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
