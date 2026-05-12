package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class RootPrivilegedDiagnosticsTest {

    @Test
    fun excerptForLog_collapsesNewlinesAndTrims() {
        val raw = "  a\nb\nc  "
        assertEquals("a b c", RootPrivilegedDiagnostics.excerptForLog(raw, maxLen = 80))
    }

    @Test
    fun excerptForLog_truncates() {
        val s = "x".repeat(200)
        val out = RootPrivilegedDiagnostics.excerptForLog(s, maxLen = 10)
        assertEquals(10, out.length)
        assertEquals("xxxxxxxxxx", out)
    }
}
