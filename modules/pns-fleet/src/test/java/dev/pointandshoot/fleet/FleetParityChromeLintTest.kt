package dev.pointandshoot.fleet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetParityChromeLintTest {
    @Test
    fun `safe closure plan passes lint`() {
        val text = "# Parity closure plan\n\n- **video.h264** — verify encoder gate"
        assertFalse(FleetParityChromeLint.containsForbiddenLayoutSuggestion(text))
        FleetParityChromeLint.assertClosurePlanSafe(text)
    }

    @Test
    fun `forbidden flex weight phrase detected`() {
        assertTrue(FleetParityChromeLint.containsForbiddenLayoutSuggestion("Adjust PreviewChromeFinderFlexWeight"))
    }
}
