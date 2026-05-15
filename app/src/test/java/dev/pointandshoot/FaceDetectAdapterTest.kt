package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FaceDetectAdapterTest {

    private val sensorW = 4000
    private val sensorH = 3000
    private val previewW = 1080
    private val previewH = 1920
    private val tolerance = 0.5f

    @Test
    fun `0deg rotation maps the sensor center to the preview center`() {
        val mark = FaceDetectAdapter.mapEyeToPreview(
            eyeSensor = SensorPoint(sensorW / 2, sensorH / 2),
            activeArrayWidth = sensorW,
            activeArrayHeight = sensorH,
            previewWidth = sensorW,           // identity scaling
            previewHeight = sensorH,
            sensorOrientationDeg = 0,
            mirrorHorizontally = false,
        )
        assertEquals(sensorW / 2f, mark.position.x, tolerance)
        assertEquals(sensorH / 2f, mark.position.y, tolerance)
        assertEquals(1f, mark.confidence, 0f)
    }

    @Test
    fun `90deg rotation swaps axes - top-left sensor maps to top-right of upright preview`() {
        // Sensor (0, 0) under a 90-degree rotation goes to (sensorH, 0) in upright frame,
        // which scales to (previewW, 0) when preview matches the rotated frame.
        val mark = FaceDetectAdapter.mapEyeToPreview(
            eyeSensor = SensorPoint(0, 0),
            activeArrayWidth = sensorW,
            activeArrayHeight = sensorH,
            previewWidth = sensorH,           // rotated frame is sensorH x sensorW
            previewHeight = sensorW,
            sensorOrientationDeg = 90,
            mirrorHorizontally = false,
        )
        assertEquals(sensorH.toFloat(), mark.position.x, tolerance)
        assertEquals(0f, mark.position.y, tolerance)
    }

    @Test
    fun `front-camera mirroring flips x but preserves y`() {
        val mark = FaceDetectAdapter.mapEyeToPreview(
            eyeSensor = SensorPoint(sensorW / 4, sensorH / 4),
            activeArrayWidth = sensorW,
            activeArrayHeight = sensorH,
            previewWidth = sensorW,
            previewHeight = sensorH,
            sensorOrientationDeg = 0,
            mirrorHorizontally = true,
        )
        assertEquals((sensorW - sensorW / 4).toFloat(), mark.position.x, tolerance)
        assertEquals((sensorH / 4).toFloat(), mark.position.y, tolerance)
    }

    @Test
    fun `confidence is clamped to 0_1 range`() {
        val low = FaceDetectAdapter.mapEyeToPreview(
            eyeSensor = SensorPoint(0, 0),
            activeArrayWidth = sensorW,
            activeArrayHeight = sensorH,
            previewWidth = previewW,
            previewHeight = previewH,
            sensorOrientationDeg = 0,
            mirrorHorizontally = false,
            confidence = -1f,
        )
        val high = FaceDetectAdapter.mapEyeToPreview(
            eyeSensor = SensorPoint(0, 0),
            activeArrayWidth = sensorW,
            activeArrayHeight = sensorH,
            previewWidth = previewW,
            previewHeight = previewH,
            sensorOrientationDeg = 0,
            mirrorHorizontally = false,
            confidence = 5f,
        )
        assertEquals(0f, low.confidence, 0f)
        assertEquals(1f, high.confidence, 0f)
    }

    @Test
    fun `face center proxy lands above the box center (between the eyes)`() {
        // Face box from (1000, 1000) to (2000, 2200) - height 1200.
        // Adapter places the proxy at top + height/3 = 1000 + 400 = 1400, NOT 1600 (true center).
        val mark = FaceDetectAdapter.mapFaceCenterToPreview(
            faceLeft = 1000, faceTop = 1000, faceRight = 2000, faceBottom = 2200,
            activeArrayWidth = sensorW,
            activeArrayHeight = sensorH,
            previewWidth = sensorW,
            previewHeight = sensorH,
            sensorOrientationDeg = 0,
            mirrorHorizontally = false,
        )
        assertEquals(1500f, mark.position.x, tolerance)
        assertEquals(1400f, mark.position.y, tolerance)
        assertEquals(0.5f, mark.confidence, 0f)
    }

    @Test
    fun `degenerate face rect is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            FaceDetectAdapter.mapFaceCenterToPreview(
                faceLeft = 100, faceTop = 100, faceRight = 100, faceBottom = 200,
                activeArrayWidth = sensorW,
                activeArrayHeight = sensorH,
                previewWidth = previewW,
                previewHeight = previewH,
                sensorOrientationDeg = 0,
                mirrorHorizontally = false,
            )
        }
    }

    @Test
    fun `non-quarter-turn rotation is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            FaceDetectAdapter.mapEyeToPreview(
                eyeSensor = SensorPoint(0, 0),
                activeArrayWidth = sensorW,
                activeArrayHeight = sensorH,
                previewWidth = previewW,
                previewHeight = previewH,
                sensorOrientationDeg = 45,
                mirrorHorizontally = false,
            )
        }
    }

    @Test
    fun `uniform center crop differs from legacy stretch when buffer aspect mismatches sensor`() {
        val sensorX = 3000
        val sensorY = sensorH / 2
        val stretch = FaceDetectAdapter.mapEyeToPreview(
            eyeSensor = SensorPoint(sensorX, sensorY),
            activeArrayWidth = sensorW,
            activeArrayHeight = sensorH,
            previewWidth = previewW,
            previewHeight = previewH,
            sensorOrientationDeg = 0,
            mirrorHorizontally = false,
            bufferScalePolicy = FaceDetectAdapter.PreviewBufferScalePolicy.Stretch,
        )
        val uniform = FaceDetectAdapter.mapEyeToPreview(
            eyeSensor = SensorPoint(sensorX, sensorY),
            activeArrayWidth = sensorW,
            activeArrayHeight = sensorH,
            previewWidth = previewW,
            previewHeight = previewH,
            sensorOrientationDeg = 0,
            mirrorHorizontally = false,
            bufferScalePolicy = FaceDetectAdapter.PreviewBufferScalePolicy.UniformCover,
        )
        assertEquals(810f, stretch.position.x, tolerance)
        assertEquals(1180f, uniform.position.x, tolerance)
        assertEquals(960f, stretch.position.y, tolerance)
        assertEquals(960f, uniform.position.y, tolerance)
    }
}
