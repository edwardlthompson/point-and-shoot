package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VideoShutterAngleTest {
    @Test
    fun angle180_at30fps_is_half_frame() {
        val ns = VideoShutterAngle.Angle180.exposureNsForFps(30)!!
        assertTrue(abs(ns - 16_666_667L) < 500_000L)
    }

    @Test
    fun free_returns_null() {
        assertEquals(null, VideoShutterAngle.Free.exposureNsForFps(30))
    }
}
