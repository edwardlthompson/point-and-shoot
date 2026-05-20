package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewVideoStorageEstimateTest {
  @Test
  fun minutesRemaining_encodedBitrate() {
    val bps = VideoFormatPresets.calculateBitrate(1920, 1080, 60, VideoCodec.H265)
    val bytesPerSec = PreviewVideoStorageEstimate.encodedBytesPerSecond(bps)
    val avail = bytesPerSec * 60 * 10 // 10 minutes
    val minutes = PreviewVideoStorageEstimate.minutesRemaining(avail, bytesPerSec)
    requireNotNull(minutes)
    assertEquals(10.0, minutes, 0.5)
  }

  @Test
  fun lowStorageWarning_belowFiveMinutes() {
    val bps = VideoFormatPresets.calculateBitrate(3840, 2160, 120, VideoCodec.H265)
    val bytesPerSec = PreviewVideoStorageEstimate.encodedBytesPerSecond(bps)
    val avail = bytesPerSec * 60 * 3 // 3 minutes
    val minutes = PreviewVideoStorageEstimate.minutesRemaining(avail, bytesPerSec)
    assertTrue(PreviewVideoStorageEstimate.isLowStorageWarning(minutes))
  }

  @Test
  fun lowStorageWarning_aboveFiveMinutes() {
    val bps = VideoFormatPresets.calculateBitrate(1920, 1080, 30, VideoCodec.H264)
    val bytesPerSec = PreviewVideoStorageEstimate.encodedBytesPerSecond(bps)
    val avail = bytesPerSec * 60 * 30
    val minutes = PreviewVideoStorageEstimate.minutesRemaining(avail, bytesPerSec)
    assertFalse(PreviewVideoStorageEstimate.isLowStorageWarning(minutes))
  }

  @Test
  fun rawLane_bytesPerSecond_scalesWithResolution() {
    val hd = PreviewVideoStorageEstimate.rawBytesPerSecond(1920, 1080, 30)
    val uhd = PreviewVideoStorageEstimate.rawBytesPerSecond(3840, 2160, 30)
    assertTrue(uhd > hd)
  }

  @Test
  fun formatMinutes_shortValues() {
    assertEquals("<1 min", PreviewVideoStorageEstimate.formatMinutesRemaining(0.5))
    assertEquals("12 min", PreviewVideoStorageEstimate.formatMinutesRemaining(12.4))
  }

  @Test
  fun withAvailableBytes_populatesWarning() {
    val session =
        PreviewVideoStorageEstimate.Session(
            encodeWidth = 1920,
            encodeHeight = 1080,
            targetFps = 60,
            rawVideoLane = false,
            enableResearchDcgHdr = false,
        )
    val base = PreviewVideoStorageEstimate.estimate(session)
    val bps = base.bitrateBps
    val bytesPerSec = PreviewVideoStorageEstimate.encodedBytesPerSecond(bps)
    val lowAvail = bytesPerSec * 60 * 2
    val result = PreviewVideoStorageEstimate.withAvailableBytes(base, lowAvail)
    assertTrue(result.lowStorageWarning)
    requireNotNull(result.minutesRemaining)
    assertTrue(result.minutesRemaining < 5.0)
  }
}
