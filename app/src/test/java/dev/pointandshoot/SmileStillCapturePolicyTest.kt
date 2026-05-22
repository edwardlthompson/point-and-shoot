package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmileStillCapturePolicyTest {

    @Test
    fun shouldTrigger_respectsThresholdAndCooldown() {
        SmileStillCapturePolicy.resetCooldown()
        assertFalse(SmileStillCapturePolicy.shouldTrigger(0.5f))
        assertTrue(SmileStillCapturePolicy.shouldTrigger(0.75f))
        assertFalse(SmileStillCapturePolicy.shouldTrigger(0.95f))
    }
}
