package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewStillCaptureHintsTest {

    @Test
    fun `normalize orientation wraps positive`() {
        assertEquals(0, PreviewStillCaptureHints.normalizeOrientationDegrees(360))
        assertEquals(90, PreviewStillCaptureHints.normalizeOrientationDegrees(450))
    }

    @Test
    fun `normalize orientation wraps negative`() {
        assertEquals(270, PreviewStillCaptureHints.normalizeOrientationDegrees(-90))
        assertEquals(180, PreviewStillCaptureHints.normalizeOrientationDegrees(-180))
    }
}
