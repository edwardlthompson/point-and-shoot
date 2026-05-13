package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PnsCapturePipelineDiagnosticsTest {

    @Test
    fun `ring keeps newest first order and caps capacity`() {
        PnsCapturePipelineDiagnostics.clear()
        repeat(140) { i ->
            PnsCapturePipelineDiagnostics.record("K", "m", mapOf("i" to "%04d".format(i)))
        }
        val text = PnsCapturePipelineDiagnostics.formatReportSection()
        assertTrue(text.contains("last 128 events"))
        assertTrue(text.contains("i=0139"))
        assertTrue(text.contains("i=0012"))
        assertTrue(!text.contains("i=0011"))
    }

    @Test
    fun `clear empties snapshot`() {
        PnsCapturePipelineDiagnostics.clear()
        PnsCapturePipelineDiagnostics.record("A", "x", emptyMap())
        PnsCapturePipelineDiagnostics.clear()
        val text = PnsCapturePipelineDiagnostics.formatReportSection()
        assertEquals(true, text.contains("(empty)"))
    }
}
