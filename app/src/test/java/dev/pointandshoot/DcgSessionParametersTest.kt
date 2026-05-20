package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DcgSessionParametersTest {
    @Test
    fun shouldAttach_whenHudOrAdb() {
        assertTrue(
            DcgSessionParameters.shouldAttach(
                enableResearchDcgHdr = true,
                adbPreviewVideoDcg = false,
            ),
        )
        assertTrue(
            DcgSessionParameters.shouldAttach(
                enableResearchDcgHdr = false,
                adbPreviewVideoDcg = true,
            ),
        )
        assertFalse(
            DcgSessionParameters.shouldAttach(
                enableResearchDcgHdr = false,
                adbPreviewVideoDcg = false,
            ),
        )
    }
}
