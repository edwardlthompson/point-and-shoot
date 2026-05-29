package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeLapseModeTest {

    @Test
    fun fromStorage_roundTrips() {
        TimeLapseMode.entries.forEach { mode ->
            assertEquals(mode, TimeLapseMode.fromStorage(mode.storageId))
        }
        assertEquals(TimeLapseMode.Off, TimeLapseMode.fromStorage(null))
        assertEquals(TimeLapseMode.Off, TimeLapseMode.fromStorage("unknown"))
    }

    @Test
    fun framePtsUs_advancesAt30Fps() {
        assertEquals(0L, TimeLapseVideoEncoder.framePtsUs(0))
        assertEquals(33_333L, TimeLapseVideoEncoder.framePtsUs(1))
        assertEquals(66_666L, TimeLapseVideoEncoder.framePtsUs(2))
    }

    @Test
    fun isTimelapseVideoSession_requiresVideoModeAndRunning() {
        val idle =
            HudSettings(
                timeLapseMode = TimeLapseMode.Video.storageId,
                intervalometerRunning = false,
                intervalometerIntervalSec = 5,
            )
        assertFalse(TimeLapseMode.isTimelapseVideoSession(idle))
        val running =
            idle.copy(intervalometerRunning = true)
        assertTrue(TimeLapseMode.isTimelapseVideoSession(running))
        val photo =
            running.copy(timeLapseMode = TimeLapseMode.Photo.storageId)
        assertFalse(TimeLapseMode.isTimelapseVideoSession(photo))
    }
}
