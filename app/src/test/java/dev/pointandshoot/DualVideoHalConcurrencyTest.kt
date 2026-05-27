package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DualVideoHalConcurrencyTest {
    @Test
    fun minRearFrames_lowerWhenConcurrentAdvertised() {
        val probe =
            DualVideoHalConcurrency.Probe(
                rearId = "0",
                frontId = "1",
                advertisedConcurrent = true,
                concurrentSets = 1,
            )
        assertEquals(24, DualVideoHalConcurrency.minRearFramesBeforeFrontOpen(probe))
        val slow =
            probe.copy(advertisedConcurrent = false)
        assertEquals(48, DualVideoHalConcurrency.minRearFramesBeforeFrontOpen(slow))
    }

    @Test
    fun allowFrontOpen_whenRecordingOnNonConcurrent() {
        val probe =
            DualVideoHalConcurrency.Probe(
                rearId = "2",
                frontId = "1",
                advertisedConcurrent = false,
                concurrentSets = 1,
            )
        assertFalse(DualVideoHalConcurrency.allowSimultaneousDualPreview(probe))
        assertFalse(
            DualVideoHalConcurrency.allowFrontCameraOpen(
                probe = probe,
                recordingArmed = false,
                delayedPreviewTry = false,
            ),
        )
        assert(
            DualVideoHalConcurrency.allowFrontCameraOpen(
                probe = probe,
                recordingArmed = true,
                delayedPreviewTry = false,
            ),
        )
    }
}
