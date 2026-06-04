package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewApertureSupportTest {
    @Test
    fun formatChipValue_proIStops() {
        assertEquals("f/2.0", PreviewApertureSupport.formatChipValue(2f))
        assertEquals("f/4.0", PreviewApertureSupport.formatChipValue(4f))
        assertEquals("f/1.6", PreviewApertureSupport.formatChipValue(1.6f))
    }

    @Test
    fun cycle_wrapsSortedStops() {
        val options = listOf(2f, 4f)
        assertEquals(4f, PreviewApertureSupport.cycleFromList(2f, options))
        assertEquals(2f, PreviewApertureSupport.cycleFromList(4f, options))
        assertEquals(
            2f,
            PreviewApertureSupport.cycleFromList(2f, options).let { PreviewApertureSupport.cycleFromList(it, options) },
        )
    }

    @Test
    fun fixedLens_singleOption() {
        val options = listOf(2.4f)
        assertFalse(PreviewApertureSupport.isVariableFromList(options))
        assertEquals(2.4f, PreviewApertureSupport.cycleFromList(2.4f, options))
        assertEquals(2.4f, PreviewApertureSupport.defaultAperture(PreviewApertureSupport.availableAperturesFromList(options)))
    }

    @Test
    fun variable_detectedWhenTwoOrMore() {
        assertTrue(PreviewApertureSupport.isVariableFromList(listOf(2f, 4f)))
        assertFalse(PreviewApertureSupport.isVariableFromList(listOf(1.6f)))
    }

    @Test
    fun defaultAperture_picksSmallestFNumber() {
        assertEquals(2f, PreviewApertureSupport.defaultAperture(listOf(2f, 4f)))
    }

    @Test
    fun matchesOption_withinEpsilon() {
        assertTrue(PreviewApertureSupport.matchesOption(2.01f, listOf(2f, 4f)))
        assertFalse(PreviewApertureSupport.matchesOption(3f, listOf(2f, 4f)))
    }
}
