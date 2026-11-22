package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceDetectorGeometryTest {

    @Test
    fun `midpoint maps eyes left and right of center`() {
        val hint =
            FaceDetectorGeometry.fromEyesMidpoint(
                midX = 100f,
                midY = 80f,
                eyesDistance = 40f,
                imageWidth = 200,
                imageHeight = 200,
                confidence = 0.9f,
            )
        assertNotNull(hint)
        val h = hint!!
        assertEquals(80f, h.leftEyeX, 0.01f)
        assertEquals(120f, h.rightEyeX, 0.01f)
        assertEquals(80f, h.leftEyeY, 0.01f)
        assertTrue(h.boundsLeft < 100)
        assertTrue(h.boundsRight > 100)
        assertTrue(h.boundsTop < 80)
        assertTrue(h.boundsBottom > 80)
        assertTrue(h.mouthTop >= h.leftEyeY)
        assertTrue(h.mouthBottom <= h.boundsBottom)
        assertEquals(0.9f, h.confidence, 0f)
    }

    @Test
    fun `clamps to image bounds`() {
        val hint =
            FaceDetectorGeometry.fromEyesMidpoint(
                midX = 4f,
                midY = 4f,
                eyesDistance = 50f,
                imageWidth = 64,
                imageHeight = 64,
                confidence = 1f,
            )
        assertNotNull(hint)
        val h = hint!!
        assertTrue(h.boundsLeft >= 0)
        assertTrue(h.boundsTop >= 0)
        assertTrue(h.boundsRight <= 64)
        assertTrue(h.boundsBottom <= 64)
        assertTrue(h.boundsRight > h.boundsLeft)
        assertTrue(h.boundsBottom > h.boundsTop)
    }

    @Test
    fun `rejects empty image`() {
        assertNull(
            FaceDetectorGeometry.fromEyesMidpoint(
                midX = 10f,
                midY = 10f,
                eyesDistance = 8f,
                imageWidth = 0,
                imageHeight = 10,
                confidence = 1f,
            ),
        )
    }

    @Test
    fun `0 rotation is identity`() {
        val mapped =
            FaceDetectorGeometry.mapUprightPointToYuv(
                detX = 10f,
                detY = 20f,
                detW = 100,
                detH = 80,
                rotationDegrees = 0,
                preRotateW = 100,
                preRotateH = 80,
                yuvW = 200,
                yuvH = 160,
            )
        assertNotNull(mapped)
        assertEquals(20f, mapped!!.first, 0.01f)
        assertEquals(40f, mapped.second, 0.01f)
    }

    @Test
    fun `90 CW inverse maps dest origin to source bottom-left`() {
        // src 4x2, 90 CW dest 2x4. Source (0,1) → dest (0,0): dx=h-1-sy=2-1-1=0, dy=sx=0
        val inv =
            FaceDetectorGeometry.inverseRotatePoint(
                dx = 0f,
                dy = 0f,
                detW = 2,
                detH = 4,
                srcW = 4,
                srcH = 2,
                rotationDegrees = 90,
            )
        assertNotNull(inv)
        assertEquals(0f, inv!!.first, 0.01f)
        assertEquals(1f, inv.second, 0.01f)
    }

    @Test
    fun `180 inverse maps dest origin to source bottom-right`() {
        val inv =
            FaceDetectorGeometry.inverseRotatePoint(
                dx = 0f,
                dy = 0f,
                detW = 4,
                detH = 2,
                srcW = 4,
                srcH = 2,
                rotationDegrees = 180,
            )
        assertNotNull(inv)
        assertEquals(3f, inv!!.first, 0.01f)
        assertEquals(1f, inv.second, 0.01f)
    }

    @Test
    fun `270 CW inverse maps dest origin to source top-right`() {
        // src 4x2, 270 CW dest 2x4. Source (3,0) → dest (0,0): dx=sy=0, dy=w-1-sx=4-1-3=0
        val inv =
            FaceDetectorGeometry.inverseRotatePoint(
                dx = 0f,
                dy = 0f,
                detW = 2,
                detH = 4,
                srcW = 4,
                srcH = 2,
                rotationDegrees = 270,
            )
        assertNotNull(inv)
        assertEquals(3f, inv!!.first, 0.01f)
        assertEquals(0f, inv.second, 0.01f)
    }

    @Test
    fun `evenFloor clears odd bit and stays at least 2`() {
        assertEquals(10, FaceDetectorGeometry.evenFloor(11))
        assertEquals(10, FaceDetectorGeometry.evenFloor(10))
        assertEquals(2, FaceDetectorGeometry.evenFloor(1))
        assertEquals(2, FaceDetectorGeometry.evenFloor(0))
    }
}
