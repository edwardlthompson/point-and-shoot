package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-policy tests for [PnsLog.shouldEmitVerbose] - the gate that decides
 * whether `v` / `d` writes are emitted. We only test the decision matrix here;
 * the actual `Log.v` / `Log.d` calls are framework-touching and live behind
 * `init(context)` (covered by instrumentation later).
 */
class PnsLogTest {

    @Test
    fun `release build with diagnostics off mutes verbose`() {
        assertFalse(PnsLog.shouldEmitVerbose(isDebuggable = false, isDiagnosticsEnabled = false))
    }

    @Test
    fun `release build with diagnostics on emits verbose`() {
        assertTrue(PnsLog.shouldEmitVerbose(isDebuggable = false, isDiagnosticsEnabled = true))
    }

    @Test
    fun `debug build always emits verbose regardless of diagnostics`() {
        assertTrue(PnsLog.shouldEmitVerbose(isDebuggable = true, isDiagnosticsEnabled = false))
        assertTrue(PnsLog.shouldEmitVerbose(isDebuggable = true, isDiagnosticsEnabled = true))
    }
}
