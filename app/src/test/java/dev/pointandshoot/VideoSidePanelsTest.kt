package dev.pointandshoot

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSidePanelsTest {
    private val density = Density(2f)

    @Test
    fun computePillarBarWidthDp_noLetterbox_returnsZero() {
        val w = computePillarBarWidthDp(IntSize(800, 1200), IntSize(800, 900), density)
        assertEquals(0f, w.value, 0.01f)
    }

    @Test
    fun computePillarBarWidthDp_sideLetterbox_halvesDelta() {
        // 800px finder, 600px content → 100px pillars each side → 50dp at 2x density
        val w = computePillarBarWidthDp(IntSize(800, 1200), IntSize(600, 900), density)
        assertEquals(50f, w.value, 0.01f)
    }
}
