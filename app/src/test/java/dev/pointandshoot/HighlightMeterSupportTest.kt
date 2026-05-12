package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightMeterSupportTest {

    @Test
    fun `pickYuv420AnalysisSize null map returns null`() {
        assertNull(HighlightMeterSupport.pickYuv420AnalysisSize(null))
    }

    @Test
    fun `compensationIndexFromEv uses floor for darken so small EV still moves`() {
        val step = 1.0 / 3.0
        // −0.25 EV ≈ −0.75 in "steps"; rounding would be −1 or 0 depending on device;
        // floor must land at −1 so AE comp is not stuck at 0.
        assertEquals(
            -1,
            HighlightMeterSupport.compensationIndexFromEv(-0.25, step, -12, 12),
        )
    }

    @Test
    fun `compensationIndexFromEv uses ceil for brighten`() {
        val step = 1.0 / 3.0
        assertEquals(
            1,
            HighlightMeterSupport.compensationIndexFromEv(0.08, step, -12, 12),
        )
    }

    @Test
    fun `compensationIndexFromEv zero EV is zero index`() {
        assertEquals(
            0,
            HighlightMeterSupport.compensationIndexFromEv(0.0, 1.0 / 6.0, -20, 20),
        )
    }
}
