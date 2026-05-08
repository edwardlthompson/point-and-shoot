package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-data tests for [LutPreviewRenderer.BITMAP_VFLIP_QUAD]. The renderer
 * uses a vertically-flipped variant of [LutShaderProgram.Source.FULL_SCREEN_QUAD]
 * because Android bitmaps and OpenGL textures disagree on row 0 orientation.
 * If a future refactor accidentally swaps the V coords back, the on-device
 * preview ends up upside-down (we already shipped that regression once);
 * these tests catch it before the GLES driver ever sees the program.
 */
class LutPreviewRendererQuadTest {

    @Test
    fun `vflip quad has 16 floats - 4 vertices x 4 components`() {
        assertEquals(16, LutPreviewRenderer.BITMAP_VFLIP_QUAD.size)
    }

    @Test
    fun `vflip quad covers NDC -1 to 1 in the same vertex order as Source FULL_SCREEN_QUAD`() {
        val q = LutPreviewRenderer.BITMAP_VFLIP_QUAD
        val ref = LutShaderProgram.Source.FULL_SCREEN_QUAD
        for (vertex in 0 until 4) {
            val base = vertex * 4
            assertEquals("vertex $vertex x", ref[base], q[base], 0f)
            assertEquals("vertex $vertex y", ref[base + 1], q[base + 1], 0f)
        }
    }

    @Test
    fun `vflip quad inverts only the V coordinate vs Source FULL_SCREEN_QUAD`() {
        val q = LutPreviewRenderer.BITMAP_VFLIP_QUAD
        val ref = LutShaderProgram.Source.FULL_SCREEN_QUAD
        for (vertex in 0 until 4) {
            val base = vertex * 4
            assertEquals("vertex $vertex u must match", ref[base + 2], q[base + 2], 0f)
            assertEquals(
                "vertex $vertex v must be 1 - ref",
                1f - ref[base + 3],
                q[base + 3],
                0f,
            )
        }
    }

    @Test
    fun `vflip quad bottom-left of screen samples bitmap top-left (uv 0 0 swap)`() {
        val q = LutPreviewRenderer.BITMAP_VFLIP_QUAD
        assertEquals(-1f, q[0], 0f)
        assertEquals(-1f, q[1], 0f)
        assertEquals(0f, q[2], 0f)
        assertEquals(1f, q[3], 0f)
    }

    @Test
    fun `vflip quad top-right of screen samples bitmap bottom-right (uv 1 0)`() {
        val q = LutPreviewRenderer.BITMAP_VFLIP_QUAD
        assertEquals(1f, q[12], 0f)
        assertEquals(1f, q[13], 0f)
        assertEquals(1f, q[14], 0f)
        assertEquals(0f, q[15], 0f)
    }
}
