package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DngSaveBisectStateExposureExtrasTest {
    @Test
    fun resetClearsFleetExposureFields() {
        DngSaveBisectState.skipPureHalAeLockOnStill = true
        DngSaveBisectState.afterStopDebounceMsOverride = 420L
        DngSaveBisectState.skipProShotDefaultAeRegions = true
        DngSaveBisectState.stillAeExposureCompensationSteps = 1
        DngSaveBisectState.precaptureUseStillTemplate = true
        DngSaveBisectState.skipStillIq = true
        DngSaveBisectState.useProShotCapturePipeline = true
        DngSaveBisectState.reset()
        assertFalse(DngSaveBisectState.skipPureHalAeLockOnStill)
        assertEquals(0L, DngSaveBisectState.afterStopDebounceMsOverride)
        assertFalse(DngSaveBisectState.skipProShotDefaultAeRegions)
        assertEquals(0, DngSaveBisectState.stillAeExposureCompensationSteps)
        assertFalse(DngSaveBisectState.precaptureUseStillTemplate)
        assertFalse(DngSaveBisectState.skipStillIq)
        assertFalse(DngSaveBisectState.useProShotCapturePipeline)
    }

    @Test
    fun hybridBayerRHalB_trustedBand() {
        val patched = DngBayerAsnSyncPolicy.hybridBayerRHalB(0.60f, floatArrayOf(0.66f, 1f, 0.65f))
        assertTrue(patched != null)
        assertEquals(0.60f, patched!![0], 0.001f)
        assertEquals(1f, patched[1], 0.001f)
        assertEquals(0.65f, patched[2], 0.001f)
    }

    @Test
    fun hybridBayerRHalB_rejectsUnsafeRg() {
        assertTrue(DngBayerAsnSyncPolicy.hybridBayerRHalB(0.20f, floatArrayOf(0.66f, 1f, 0.65f)) == null)
        assertTrue(DngBayerAsnSyncPolicy.hybridBayerRHalB(0.98f, floatArrayOf(0.66f, 1f, 0.65f)) == null)
    }
}
