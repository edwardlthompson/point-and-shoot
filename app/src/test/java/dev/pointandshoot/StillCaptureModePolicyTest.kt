package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StillCaptureModePolicyTest {
    @Test
    fun parseAdbExtra_standardVariants() {
        assertEquals(StillCaptureMode.Standard, StillCaptureModePolicy.parseAdbExtra("standard"))
        assertEquals(StillCaptureMode.ZslStill, StillCaptureModePolicy.parseAdbExtra("zsl"))
        assertEquals(StillCaptureMode.HdrStill, StillCaptureModePolicy.parseAdbExtra("hdr_still"))
    }

    @Test
    fun parseAdbExtra_unknown_returnsNull() {
        assertNull(StillCaptureModePolicy.parseAdbExtra("motioncam"))
    }

    @Test
    fun effectiveForCapture_zslStill_whenImplemented() {
        assertEquals(
            StillCaptureMode.ZslStill,
            StillCaptureModePolicy.effectiveForCapture(StillCaptureMode.ZslStill),
        )
    }

    @Test
    fun effectiveForCapture_hdrStill_whenImplemented() {
        assertEquals(
            StillCaptureMode.HdrStill,
            StillCaptureModePolicy.effectiveForCapture(StillCaptureMode.HdrStill),
        )
    }

    @Test
    fun isImplemented_standardZslAndHdr() {
        assertTrue(StillCaptureModePolicy.isImplemented(StillCaptureMode.Standard))
        assertTrue(StillCaptureModePolicy.isImplemented(StillCaptureMode.ZslStill))
        assertTrue(StillCaptureModePolicy.isImplemented(StillCaptureMode.HdrStill))
    }
}
