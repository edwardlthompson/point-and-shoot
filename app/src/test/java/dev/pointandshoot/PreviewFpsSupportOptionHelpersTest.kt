package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewFpsSupportOptionHelpersTest {

    @Test
    fun maxStockTargetFromOptions_picksHighestNonRoot() {
        val opts =
            listOf(
                PreviewFpsSupport.QuickFpsOption(60, requiresRoot = false),
                PreviewFpsSupport.QuickFpsOption(240, requiresRoot = true),
                PreviewFpsSupport.QuickFpsOption(120, requiresRoot = false),
            )
        assertEquals(120, PreviewFpsSupport.maxStockTargetFromOptions(opts))
    }

    @Test
    fun maxStockTargetFromOptions_allRoot_returnsNull() {
        val opts = listOf(PreviewFpsSupport.QuickFpsOption(480, requiresRoot = true))
        assertNull(PreviewFpsSupport.maxStockTargetFromOptions(opts))
    }

    @Test
    fun bestStockTargetAtOrBelow_respectsCeiling() {
        val opts =
            listOf(
                PreviewFpsSupport.QuickFpsOption(60, requiresRoot = false),
                PreviewFpsSupport.QuickFpsOption(120, requiresRoot = false),
                PreviewFpsSupport.QuickFpsOption(240, requiresRoot = false),
            )
        assertEquals(120, PreviewFpsSupport.bestStockTargetAtOrBelow(opts, 150))
        assertEquals(240, PreviewFpsSupport.bestStockTargetAtOrBelow(opts, 500))
    }
}
