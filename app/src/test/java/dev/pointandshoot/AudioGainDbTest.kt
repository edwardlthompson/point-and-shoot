package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioGainDbTest {
    @Test
    fun audioGainDbToLinear_standardSteps() {
        assertEquals(1f, HudSettings.audioGainDbToLinear(0f), 0.001f)
        assertEquals(2f, HudSettings.audioGainDbToLinear(6f), 0.05f)
        assertEquals(0.5f, HudSettings.audioGainDbToLinear(-6f), 0.05f)
    }

    @Test
    fun coerceAudioGainDb_roundsToHalfDb() {
        assertEquals(0f, HudSettings.coerceAudioGainDb(0.2f), 0.001f)
        assertEquals(0.5f, HudSettings.coerceAudioGainDb(0.4f), 0.001f)
        assertEquals(12f, HudSettings.coerceAudioGainDb(99f), 0.001f)
        assertEquals(-12f, HudSettings.coerceAudioGainDb(-99f), 0.001f)
    }

    @Test
    fun audioGainLinear_matchesDbHelper() {
        val settings = HudSettings(audioGainDb = 3f)
        assertEquals(
            HudSettings.audioGainDbToLinear(3f),
            settings.audioGainLinear(),
            0.001f,
        )
        assertTrue(abs(settings.audioGainLinear() - 1.41f) < 0.05f)
    }
}
