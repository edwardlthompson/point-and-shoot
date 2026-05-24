package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-safe checks for picker HS truth helpers (no [android.util.Size] stubs). */
class InAppVideoRecordingSupportHsTruthTest {
    @Test
    fun hasExactHighSpeedFps_nullMap_false() {
        assertFalse(InAppVideoRecordingSupport.hasExactHighSpeedFps(null, 3840, 2160, 120))
        assertFalse(InAppVideoRecordingSupport.hasExactHighSpeedFps(null, 1920, 1080, 120))
    }

    @Test
    fun supportsMediaRecorderOutputSize_nullMap_false() {
        assertFalse(InAppVideoRecordingSupport.supportsMediaRecorderOutputSize(null, 3840, 2160))
        assertFalse(InAppVideoRecordingSupport.supportsMediaRecorderOutputSize(null, 1920, 1080))
    }

    @Test
    fun highSpeedFpsForEncodeSize_nullMap_empty() {
        assertTrue(InAppVideoRecordingSupport.highSpeedFpsForEncodeSize(null, 1920, 1080).isEmpty())
    }
}
