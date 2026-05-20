package dev.pointandshoot

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewThermalLabelsTest {
  @Test
  fun thermalWarning_fromModerate() {
    assertFalse(PreviewThermalLabels.isThermalWarning(PowerManager.THERMAL_STATUS_LIGHT))
    assertTrue(PreviewThermalLabels.isThermalWarning(PowerManager.THERMAL_STATUS_MODERATE))
    assertTrue(PreviewThermalLabels.isThermalWarning(PowerManager.THERMAL_STATUS_SEVERE))
  }

  @Test
  fun labels_coverKnownStatuses() {
    assertEquals("OK", PreviewThermalLabels.labelForStatus(PowerManager.THERMAL_STATUS_NONE))
    assertEquals("HOT", PreviewThermalLabels.labelForStatus(PowerManager.THERMAL_STATUS_MODERATE))
  }
}
