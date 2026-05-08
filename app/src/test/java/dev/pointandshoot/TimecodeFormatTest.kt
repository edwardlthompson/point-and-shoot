package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class TimecodeFormatTest {

    @Test
    fun `zero elapsed renders all zeros`() {
        assertEquals("00:00:00:00", formatTimecode(elapsedMs = 0L, fps = 30))
    }

    @Test
    fun `frame field wraps at fps`() {
        // At 30fps, 33 ms == ~1 frame
        assertEquals("00:00:00:00", formatTimecode(elapsedMs = 33L, fps = 30))
        // At 30fps, 34 ms == frame 1 (34 * 30 / 1000 == 1)
        assertEquals("00:00:00:01", formatTimecode(elapsedMs = 34L, fps = 30))
    }

    @Test
    fun `seconds rollover increments seconds field`() {
        assertEquals("00:00:01:00", formatTimecode(elapsedMs = 1_000L, fps = 30))
        assertEquals("00:00:01:15", formatTimecode(elapsedMs = 1_500L, fps = 30))
    }

    @Test
    fun `minute and hour rollover`() {
        assertEquals("00:01:00:00", formatTimecode(elapsedMs = 60_000L, fps = 24))
        assertEquals("01:00:00:00", formatTimecode(elapsedMs = 3_600_000L, fps = 24))
    }

    @Test
    fun `non-positive fps clamps to 1`() {
        // Exactly 1 second elapsed, 1 fps -> no fractional frame.
        assertEquals("00:00:01:00", formatTimecode(elapsedMs = 1_000L, fps = 0))
        assertEquals("00:00:01:00", formatTimecode(elapsedMs = 1_000L, fps = -10))
    }

    @Test
    fun `negative elapsed clamps to zero`() {
        // Implementation never returns a negative timecode.
        assertEquals("00:00:00:00", formatTimecode(elapsedMs = -500L, fps = 30))
    }
}
