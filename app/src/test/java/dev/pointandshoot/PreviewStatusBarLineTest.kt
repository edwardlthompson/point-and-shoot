package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewStatusBarLineTest {

    @Test
    fun previewStatusBarLine_prefersCaptureHint() {
        assertEquals(
            "Saving DNG…",
            previewStatusBarLine("Saving DNG…", focalMapCalibratingHint = true, sessionStatus = "Ready"),
        )
    }

    @Test
    fun previewStatusBarLine_focalMapWhenNoCaptureHint() {
        assertEquals(
            "Calibrating focal map…",
            previewStatusBarLine(null, focalMapCalibratingHint = true, sessionStatus = "Ready"),
        )
    }

    @Test
    fun previewStatusBarLine_ignoresIdleStatus() {
        assertNull(previewStatusBarLine(null, false, "Idle"))
    }

    @Test
    fun previewStatusBarLine_showsNonIdleSessionStatus() {
        assertEquals(
            "Opening camera…",
            previewStatusBarLine(null, false, "Opening camera…"),
        )
    }
}
