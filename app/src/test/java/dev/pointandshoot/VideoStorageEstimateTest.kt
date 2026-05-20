package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStorageEstimateTest {
    @Test
    fun bytesPerSecond_1080p24_raw16() {
        val bps = VideoStorageEstimate.bytesPerSecond(1920, 1080, bytesPerPixel = 2, fps = 24)
        assertEquals(1920L * 1080 * 2 * 24, bps)
    }

    @Test
    fun formatMegabytesPerMinute_positive() {
        val label = VideoStorageEstimate.formatMegabytesPerMinute(10_000_000L)
        assertTrue(label.contains("MB/min"))
    }
}
