package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class TexturePreviewFitTest {

    @Test
    fun `mapBufferToView identity when view matches buffer`() {
        val (x, y) = TexturePreviewFit.mapBufferToView(10f, 20f, 100, 200, 100, 200)
        assertEquals(10f, x, 0.01f)
        assertEquals(20f, y, 0.01f)
    }

    @Test
    fun `mapBufferToView center maps to view center for uniform scale`() {
        val (x, y) = TexturePreviewFit.mapBufferToView(50f, 50f, 200, 200, 100, 100)
        assertEquals(100f, x, 0.5f)
        assertEquals(100f, y, 0.5f)
    }

    @Test
    fun `mapBufferToViewWithUiTwist 180 degrees maps corners around view center`() {
        val (x, y) = TexturePreviewFit.mapBufferToViewWithUiTwist(0f, 0f, 100, 100, 100, 100, 180f)
        assertEquals(100f, x, 0.01f)
        assertEquals(100f, y, 0.01f)
    }

    @Test
    fun `mapBufferToViewWithUiTwist 0 matches mapBufferToView`() {
        val a = TexturePreviewFit.mapBufferToView(12f, 34f, 200, 300, 80, 60)
        val b = TexturePreviewFit.mapBufferToViewWithUiTwist(12f, 34f, 200, 300, 80, 60, 0f)
        assertEquals(a.first, b.first, 0.01f)
        assertEquals(a.second, b.second, 0.01f)
    }

    /** Letterbox: 16:9 (1920×1080) buffer in a portrait 1080×1920 view leaves bars top + bottom. */
    @Test
    fun `mapBufferToView letterboxes a 16x9 buffer into a portrait view`() {
        // Buffer top-left should land at (0, 656.25) — the letterbox offset.
        val (x0, y0) = TexturePreviewFit.mapBufferToView(0f, 0f, 1080, 1920, 1920, 1080)
        assertEquals(0f, x0, 0.5f)
        assertEquals((1920f - 607.5f) / 2f, y0, 0.5f)

        val (x1, y1) = TexturePreviewFit.mapBufferToView(1920f, 1080f, 1080, 1920, 1920, 1080)
        assertEquals(1080f, x1, 0.5f)
        assertEquals(1920f - (1920f - 607.5f) / 2f, y1, 0.5f)
    }

    /** Pillarbox: 4:3 (4000×3000) buffer in a wider 16:9 (1920×1080) view leaves bars left + right. */
    @Test
    fun `mapBufferToView pillarboxes a 4x3 buffer into a 16x9 view`() {
        val (x0, y0) = TexturePreviewFit.mapBufferToView(0f, 0f, 1920, 1080, 4000, 3000)
        assertEquals((1920f - 1440f) / 2f, x0, 0.5f)
        assertEquals(0f, y0, 0.5f)

        val (x1, y1) = TexturePreviewFit.mapBufferToView(4000f, 3000f, 1920, 1080, 4000, 3000)
        assertEquals(1920f - (1920f - 1440f) / 2f, x1, 0.5f)
        assertEquals(1080f, y1, 0.5f)
    }

    /** Same aspect ratio: scale fills view exactly with no letterbox. */
    @Test
    fun `mapBufferToView fills exactly when aspects match`() {
        val (x0, y0) = TexturePreviewFit.mapBufferToView(0f, 0f, 800, 600, 1600, 1200)
        val (x1, y1) = TexturePreviewFit.mapBufferToView(1600f, 1200f, 800, 600, 1600, 1200)
        assertEquals(0f, x0, 0.5f)
        assertEquals(0f, y0, 0.5f)
        assertEquals(800f, x1, 0.5f)
        assertEquals(600f, y1, 0.5f)
    }

    /** Invalid sizes (zero / negative) should pass through unchanged so callers don't crash. */
    @Test
    fun `mapBufferToView passes through for invalid sizes`() {
        val (x, y) = TexturePreviewFit.mapBufferToView(7f, 9f, 0, 1080, 1920, 1080)
        assertEquals(7f, x, 0.001f)
        assertEquals(9f, y, 0.001f)
    }

    @Test
    fun `mapViewToBuffer inverts mapBufferToView for interior samples`() {
        val vw = 1080
        val vh = 1920
        val bw = 1920
        val bh = 1080
        for (bx in floatArrayOf(0f, 960f, 1919f)) {
            for (by in floatArrayOf(0f, 540f, 1079f)) {
                val (vx, vy) = TexturePreviewFit.mapBufferToView(bx, by, vw, vh, bw, bh)
                val (bx2, by2) = TexturePreviewFit.mapViewToBuffer(vx, vy, vw, vh, bw, bh)
                assertEquals(bx, bx2, 0.08f)
                assertEquals(by, by2, 0.08f)
            }
        }
    }

    @Test
    fun `mapViewToBufferWithUiTwist round trips with mapBufferToViewWithUiTwist at zero twist`() {
        val (vx, vy) = TexturePreviewFit.mapBufferToViewWithUiTwist(100f, 200f, 400, 300, 800, 600, 0f)
        val (bx, by) = TexturePreviewFit.mapViewToBufferWithUiTwist(vx, vy, 400, 300, 800, 600, 0f)
        assertEquals(100f, bx, 0.05f)
        assertEquals(200f, by, 0.05f)
    }

    // ----------------------------------------------------------------------------
    // computeFitRect — the rect callers should clip overlays to so the rule-of-thirds /
    // horizon level / focus peaking lines stay inside the visible (letterboxed) image.
    // ----------------------------------------------------------------------------

    @Test
    fun `computeFitRect letterboxes a 16x9 buffer into a portrait viewport`() {
        // 1920×1080 buffer (16:9, 1.778) inside 1080×1920 view (portrait):
        // limited by width: scale = 1080/1920 = 0.5625 → drawn = 1080×607.5
        // ⇒ left=0, top=(1920-607.5)/2 = 656.25
        val r = TexturePreviewFit.computeFitRect(1080, 1920, 1920, 1080)
        assertEquals(0f, r.left, 0.5f)
        assertEquals(656.25f, r.top, 0.5f)
        assertEquals(1080f, r.width, 0.5f)
        assertEquals(607.5f, r.height, 0.5f)
        assertEquals(1080f, r.right, 0.5f)
        assertEquals(656.25f + 607.5f, r.bottom, 0.5f)
    }

    @Test
    fun `computeFitRect pillarboxes a 4x3 buffer into a 16x9 viewport`() {
        // 4000×3000 (4:3, 1.333) into 1920×1080 (16:9, 1.778):
        // limited by height: scale = 1080/3000 = 0.36 → drawn = 1440×1080
        // ⇒ left=(1920-1440)/2 = 240, top=0
        val r = TexturePreviewFit.computeFitRect(1920, 1080, 4000, 3000)
        assertEquals(240f, r.left, 0.5f)
        assertEquals(0f, r.top, 0.5f)
        assertEquals(1440f, r.width, 0.5f)
        assertEquals(1080f, r.height, 0.5f)
    }

    @Test
    fun `computeFitRect fills exactly when aspects match`() {
        val r = TexturePreviewFit.computeFitRect(800, 600, 1600, 1200)
        assertEquals(0f, r.left, 0.5f)
        assertEquals(0f, r.top, 0.5f)
        assertEquals(800f, r.width, 0.5f)
        assertEquals(600f, r.height, 0.5f)
    }

    @Test
    fun `computeFitRect tolerates degenerate sizes`() {
        val r = TexturePreviewFit.computeFitRect(0, 1080, 1920, 1080)
        assertEquals(0f, r.left, 0.001f)
        assertEquals(0f, r.top, 0.001f)
        assertEquals(0f, r.width, 0.001f)
        assertEquals(1080f, r.height, 0.001f)
    }

    @Test
    fun `largestAxisAlignedRectWithAspect wide ratio pillarboxes in a square`() {
        val (w, h) = TexturePreviewFit.largestAxisAlignedRectWithAspect(100, 100, 2f)
        assertEquals(100, w)
        assertEquals(50, h)
    }

    @Test
    fun `largestAxisAlignedRectWithAspect tall ratio letterboxes in a square`() {
        val (w, h) = TexturePreviewFit.largestAxisAlignedRectWithAspect(100, 100, 0.5f)
        assertEquals(50, w)
        assertEquals(100, h)
    }

    @Test
    fun `largestAxisAlignedRectWithAspect matches footprint aspect`() {
        val aspect = 1920f / 1080f
        val (w, h) = TexturePreviewFit.largestAxisAlignedRectWithAspect(400, 300, aspect)
        assertEquals(400, w)
        assertEquals(225, h)
        assertEquals(aspect, w.toFloat() / h.toFloat(), 0.02f)
    }

    @Test
    fun `smallestCoveringAxisAlignedRectWithAspect fills wide viewport height limited`() {
        val aspect = 4000f / 3000f
        val (w, h) = TexturePreviewFit.smallestCoveringAxisAlignedRectWithAspect(1920, 1080, aspect)
        assertEquals(1920, w)
        assertEquals(1440, h)
    }

    @Test
    fun `smallestCoveringAxisAlignedRectWithAspect fills tall viewport width limited`() {
        val aspect = 16f / 9f
        val (w, h) = TexturePreviewFit.smallestCoveringAxisAlignedRectWithAspect(400, 800, aspect)
        assertEquals(1422, w)
        assertEquals(800, h)
    }
}
