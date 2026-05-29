package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudSettingsWindNoiseFilterTest {
    @Test
    fun windNoiseFilterActive_onlyWhenEnabledAndCamcorder() {
        val on =
            HudSettings(
                windNoiseFilterEnabled = true,
                videoAudioSource = VideoAudioSource.Camcorder.storageId,
            )
        assertTrue(on.windNoiseFilterActive())
        assertFalse(on.copy(windNoiseFilterEnabled = false).windNoiseFilterActive())
        assertFalse(on.copy(videoAudioSource = VideoAudioSource.Mic.storageId).windNoiseFilterActive())
    }
}
