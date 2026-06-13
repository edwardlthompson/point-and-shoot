package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionRegressionLocksTest {

    @Test
    fun regularSessionStreamHints_staysDisabled() {
        assertFalse(CaptureSessionRegressionLocks.REGULAR_SESSION_STREAM_HINTS_ENABLED)
    }

    @Test
    fun automationSuppressFacePipeline_onlyWhenBracketPatternSet() {
        assertFalse(CaptureSessionRegressionLocks.automationSuppressFacePipeline(null))
        assertTrue(CaptureSessionRegressionLocks.automationSuppressFacePipeline(BracketPattern.Three))
    }
}
