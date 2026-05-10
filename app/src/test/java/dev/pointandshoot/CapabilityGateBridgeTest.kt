package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityGateBridgeTest {

    @Test
    fun formatEvaluateLines_matches_capability_gate_order() {
        val caps = HardwareCaps(
            hasRawCapability = true,
            has12BitDepth = true,
            has120FpsHfr = false,
            hasFaceDetectFull = false,
            hasPreviewHistogram = false,
            aeCompensationStepsAvailable = 6,
            hasMacroMode = false,
            has10BitHdrPipeline = false,
            hasOpticalStabilization = false,
        )
        val lines = CapabilityGateBridge.formatEvaluateLines(CapabilityGate.evaluate(caps))
        assertTrue(lines.isNotEmpty())
        assertEquals(CapabilityGate.evaluate(caps).size, lines.size)
        val rawLine = lines.first { it.startsWith("RAW DNG:") }
        assertTrue(rawLine.contains(": ok"))
        val hfrLine = lines.first { it.startsWith("120 fps preview:") }
        assertTrue(hfrLine.contains(": off"))
        assertTrue(hfrLine.contains(" - "))
    }
}
