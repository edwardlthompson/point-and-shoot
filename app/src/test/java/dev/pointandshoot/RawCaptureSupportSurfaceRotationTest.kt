package dev.pointandshoot

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class RawCaptureSupportSurfaceRotationTest {
    @Test
    fun `physical 0 maps to ROTATION_0`() {
        assertEquals(Surface.ROTATION_0, RawCaptureSupport.surfaceRotationFromPhysicalCardinalSnap(0))
    }

    @Test
    fun `physical 90 maps to ROTATION_270`() {
        assertEquals(Surface.ROTATION_270, RawCaptureSupport.surfaceRotationFromPhysicalCardinalSnap(90))
    }

    @Test
    fun `physical 180 maps to ROTATION_180`() {
        assertEquals(Surface.ROTATION_180, RawCaptureSupport.surfaceRotationFromPhysicalCardinalSnap(180))
    }

    @Test
    fun `physical 270 maps to ROTATION_90`() {
        assertEquals(Surface.ROTATION_90, RawCaptureSupport.surfaceRotationFromPhysicalCardinalSnap(270))
    }

    @Test
    fun `negative angles normalize`() {
        assertEquals(Surface.ROTATION_90, RawCaptureSupport.surfaceRotationFromPhysicalCardinalSnap(-90))
    }
}
