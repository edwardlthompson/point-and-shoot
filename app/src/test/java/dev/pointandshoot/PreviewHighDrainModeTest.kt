package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewHighDrainModeTest {
  @Test
  fun highDrain_hfrVideoPrimary() {
    val c =
        PreviewHighDrainMode.Context(
            videoPrimary = true,
            isRecording = false,
            selectedFps = 120,
            enableResearchDcgHdr = false,
        )
    assertTrue(PreviewHighDrainMode.isHighDrain(c))
    assertTrue(PreviewHighDrainMode.shouldShowPowerThermalOverlay(c, hudEnabled = true))
  }

  @Test
  fun highDrain_dcgWhileRecording() {
    val c =
        PreviewHighDrainMode.Context(
            videoPrimary = false,
            isRecording = true,
            selectedFps = 60,
            enableResearchDcgHdr = true,
        )
    assertTrue(PreviewHighDrainMode.isHighDrain(c))
    assertTrue(PreviewHighDrainMode.shouldShowPowerThermalOverlay(c, hudEnabled = true))
  }

  @Test
  fun notHighDrain_photoPrimary60fps() {
    val c =
        PreviewHighDrainMode.Context(
            videoPrimary = false,
            isRecording = false,
            selectedFps = 60,
            enableResearchDcgHdr = false,
        )
    assertFalse(PreviewHighDrainMode.isHighDrain(c))
    assertFalse(PreviewHighDrainMode.shouldShowPowerThermalOverlay(c, hudEnabled = true))
  }

  @Test
  fun adbForce_showsWithoutHfr() {
    val c =
        PreviewHighDrainMode.Context(
            videoPrimary = false,
            isRecording = false,
            selectedFps = 30,
            enableResearchDcgHdr = false,
            adbForceOverlay = true,
        )
    assertTrue(PreviewHighDrainMode.shouldShowPowerThermalOverlay(c, hudEnabled = true))
  }

  @Test
  fun hudDisabled_hidesOverlay() {
    val c =
        PreviewHighDrainMode.Context(
            videoPrimary = true,
            isRecording = false,
            selectedFps = 120,
            enableResearchDcgHdr = false,
        )
    assertFalse(PreviewHighDrainMode.shouldShowPowerThermalOverlay(c, hudEnabled = false))
  }
}
