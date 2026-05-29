package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class FalseColorModeTest {
    @Test
    fun cycleNext_rotatesThroughModes() {
        assertEquals(FalseColorMode.ZebraOnly, FalseColorMode.Off.cycleNext())
        assertEquals(FalseColorMode.FalseColor, FalseColorMode.ZebraOnly.cycleNext())
        assertEquals(FalseColorMode.Off, FalseColorMode.FalseColor.cycleNext())
    }
}
