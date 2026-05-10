package dev.pointandshoot

import org.junit.Assert.assertNull
import org.junit.Test

class HighlightMeterSupportTest {

    @Test
    fun `pickYuv420AnalysisSize null map returns null`() {
        assertNull(HighlightMeterSupport.pickYuv420AnalysisSize(null))
    }
}
