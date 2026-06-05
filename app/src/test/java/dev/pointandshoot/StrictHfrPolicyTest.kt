package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictHfrPolicyTest {
    @Test
    fun interleavedRouteLabel_sub4kWhenHsBelowEncodePref() {
        assertEquals(
            "interleaved_sub4k",
            StrictHfrPolicy.interleavedRouteLabel(3840, 2160, 1920, 1080),
        )
        assertEquals(
            "interleaved_primary",
            StrictHfrPolicy.interleavedRouteLabel(3840, 2160, 3840, 2160),
        )
    }

    @Test
    fun configureFail_ladder_interleavedThenEncoderPriorityThenSub4k() {
        var state =
            StrictHfrPolicy.ConfigureFailState(
                encoderOnlyActive = false,
                forceInterleaved = false,
                sub4kFallback = false,
                encoderPriorityTried = false,
                prefersInterleavedFor4k = true,
            )
        val interleavedRetry = StrictHfrPolicy.nextConfigureFailAction(state)
        assertNotNull(interleavedRetry)
        assertEquals("interleaved_fallback", interleavedRetry!!.route)
        state = interleavedRetry.next

        val encoderPriority = StrictHfrPolicy.nextConfigureFailAction(state)
        assertNotNull(encoderPriority)
        assertEquals("encoder_priority", encoderPriority!!.route)
        state = encoderPriority.next

        val sub4k = StrictHfrPolicy.nextConfigureFailAction(state)
        assertNotNull(sub4k)
        assertEquals("interleaved_sub4k", sub4k!!.route)
        state = sub4k.next

        assertNull(StrictHfrPolicy.nextConfigureFailAction(state))
    }

    @Test
    fun strictStart_retryThenBlockAfterBudgetExhausted() {
        val first =
            StrictHfrPolicy.evaluateStrictStart(
                desiredFps = 120,
                effectiveFps = 120,
                warmupHealthy = false,
                retryBudgetRemaining = 2,
                recoveryCapActive = false,
            )
        assertTrue(first is StrictHfrPolicy.StrictStartDecision.Retry)
        assertEquals(1, (first as StrictHfrPolicy.StrictStartDecision.Retry).retriesRemaining)

        val second =
            StrictHfrPolicy.evaluateStrictStart(
                desiredFps = 120,
                effectiveFps = 120,
                warmupHealthy = false,
                retryBudgetRemaining = 1,
                recoveryCapActive = false,
            )
        assertTrue(second is StrictHfrPolicy.StrictStartDecision.Retry)
        assertEquals(0, (second as StrictHfrPolicy.StrictStartDecision.Retry).retriesRemaining)

        val blocked =
            StrictHfrPolicy.evaluateStrictStart(
                desiredFps = 120,
                effectiveFps = 120,
                warmupHealthy = false,
                retryBudgetRemaining = 0,
                recoveryCapActive = false,
            )
        assertTrue(blocked is StrictHfrPolicy.StrictStartDecision.Block)
        assertEquals("warmup_unhealthy", (blocked as StrictHfrPolicy.StrictStartDecision.Block).reason)
    }

    @Test
    fun strictStart_passAfterRetryWhenWarmupHealthy() {
        val decision =
            StrictHfrPolicy.evaluateStrictStart(
                desiredFps = 120,
                effectiveFps = 120,
                warmupHealthy = true,
                retryBudgetRemaining = 0,
                recoveryCapActive = false,
            )
        assertTrue(decision is StrictHfrPolicy.StrictStartDecision.Allow)
    }

    @Test
    fun warmupHealthy_requiresFpsAndLowStall() {
        assertFalse(
            StrictHfrPolicy.isWarmupHealthy(
                desiredFps = 120,
                sessionReady = true,
                configurePending = false,
                stallMs = 500.0,
                smoothedFps = 60.0,
            ),
        )
        assertTrue(
            StrictHfrPolicy.isWarmupHealthy(
                desiredFps = 120,
                sessionReady = true,
                configurePending = false,
                stallMs = 500.0,
                smoothedFps = 95.0,
            ),
        )
    }

    @Test
    fun midRecordOutcome_classification() {
        assertEquals(
            StrictHfrPolicy.MidRecordOutcome.SUSTAINED,
            StrictHfrPolicy.classifyMidRecordOutcome(recovered = false, recordingStoppedCleanly = true),
        )
        assertEquals(
            StrictHfrPolicy.MidRecordOutcome.RECOVERED_ONCE,
            StrictHfrPolicy.classifyMidRecordOutcome(recovered = true, recordingStoppedCleanly = false),
        )
        assertEquals(
            StrictHfrPolicy.MidRecordOutcome.BLOCKED_UNSTABLE,
            StrictHfrPolicy.classifyMidRecordOutcome(recovered = false, recordingStoppedCleanly = false),
        )
    }
}
