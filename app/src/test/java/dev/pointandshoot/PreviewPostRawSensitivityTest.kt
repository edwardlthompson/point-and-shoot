package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewPostRawSensitivityTest {

    @Test
    fun pickBoost_midpoint() {
        assertEquals(5, PreviewPostRawSensitivity.pickBoostMidpoint(0, 10))
        assertEquals(7, PreviewPostRawSensitivity.pickBoostMidpoint(4, 10))
    }
}
