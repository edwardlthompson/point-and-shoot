package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewBatteryDrainEstimatorTest {
  @Test
  fun drainRate_requiresMinimumWindow() {
    val samples =
        listOf(
            PreviewBatteryDrainEstimator.Sample(80, 0L),
            PreviewBatteryDrainEstimator.Sample(79, 10_000L),
        )
    assertNull(PreviewBatteryDrainEstimator.estimateDrainPctPerHour(samples))
  }

  @Test
  fun drainRate_linearDrop() {
    val samples =
        listOf(
            PreviewBatteryDrainEstimator.Sample(100, 0L),
            PreviewBatteryDrainEstimator.Sample(90, 3_600_000L),
        )
    val rate = PreviewBatteryDrainEstimator.estimateDrainPctPerHour(samples)
    requireNotNull(rate)
    assertEquals(10f, rate, 0.01f)
  }

  @Test
  fun formatDrain_positiveDrain() {
    val text = PreviewBatteryDrainEstimator.formatDrainPctPerHour(12.4f)
    assertTrue(text.contains("12"))
    assertTrue(text.contains("/hr"))
  }
}
