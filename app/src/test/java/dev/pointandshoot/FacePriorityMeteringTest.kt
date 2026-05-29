package dev.pointandshoot

import org.junit.Assert.assertTrue
import org.junit.Test

class FacePriorityMeteringTest {
    @Test
    fun `eyeSubRegionInFace is shorter than full face box`() {
        val face = FacePriorityMetering.BufferRectF(100f, 200f, 300f, 600f)
        val eyes = listOf(150f to 280f, 250f to 285f)
        val sub = FacePriorityMetering.eyeSubRegionInFace(face, eyes)
        assertTrue(sub.height < face.height)
        assertTrue(sub.top >= face.top)
        assertTrue(sub.bottom <= face.bottom)
    }

    @Test
    fun `eyeSubRegionInFace without eyes uses upper band`() {
        val face = FacePriorityMetering.BufferRectF(0f, 0f, 100f, 100f)
        val sub = FacePriorityMetering.eyeSubRegionInFace(face, emptyList())
        assertTrue(sub.height <= 40f)
        assertTrue(sub.bottom <= 40f)
    }
}
