package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceOverlayCalibrationTest {
    @Test
    fun applyViewPoint_identity() {
        val p = FaceOverlayCalibration.applyViewPoint(100f, 200f, FaceOverlayCalibration.Default, 540f, 960f)
        assertEquals(100f, p.x, 0.001f)
        assertEquals(200f, p.y, 0.001f)
    }

    @Test
    fun applyViewPoint_offsetAndScale() {
        val cal =
            FaceOverlayCalibration(
                offsetViewX = -10f,
                offsetViewY = -8f,
                positionScale = 1.1f,
            )
        val p = FaceOverlayCalibration.applyViewPoint(100f, 200f, cal, 0f, 0f)
        assertEquals(100f * 1.1f - 10f, p.x, 0.001f)
        assertEquals(200f * 1.1f - 8f, p.y, 0.001f)
    }

    @Test
    fun clamped_limitsScale() {
        val cal = FaceOverlayCalibration(positionScale = 5f, markerSizeScale = 0.01f).clamped()
        assertEquals(FaceOverlayCalibration.POSITION_SCALE_MAX, cal.positionScale, 0.001f)
        assertEquals(FaceOverlayCalibration.MARKER_SIZE_SCALE_MIN, cal.markerSizeScale, 0.001f)
    }
}
