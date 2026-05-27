package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint **15.12** — dual RAW+tonal defers haptic until tonal completes.
 */
class DualCaptureHapticsTest {
    @Test
    fun deferPolicy_rawOnlyFiresWhenNotDeferred() {
        assertTrue(PreviewCaptureHapticsPolicy.shouldFireStillTick(rawComplete = true, deferUntilTonal = false))
        assertFalse(PreviewCaptureHapticsPolicy.shouldFireStillTick(rawComplete = true, deferUntilTonal = true))
        assertFalse(PreviewCaptureHapticsPolicy.shouldFireStillTick(rawComplete = false, deferUntilTonal = true))
    }
}
