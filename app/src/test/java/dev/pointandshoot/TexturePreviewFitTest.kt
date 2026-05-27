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

    /** Center-crop: 16:9 buffer in a portrait view fills width and crops left/right (no side bars). */
    @Test
    fun `mapBufferToView center crops a 16x9 buffer into a portrait view`() {
        val (x0, y0) = TexturePreviewFit.mapBufferToView(0f, 0f, 1080, 1920, 1920, 1080)
        assertEquals(-1166.6666f, x0, 0.5f)
        assertEquals(0f, y0, 0.5f)

        val (xc, yc) = TexturePreviewFit.mapBufferToView(960f, 540f, 1080, 1920, 1920, 1080)
        assertEquals(540f, xc, 0.5f)
        assertEquals(960f, yc, 0.5f)
    }

    /** Center-crop: 4:3 buffer in 16:9 view fills width and crops top/bottom. */
    @Test
    fun `mapBufferToView center crops a 4x3 buffer into a 16x9 view`() {
        val (x0, y0) = TexturePreviewFit.mapBufferToView(0f, 0f, 1920, 1080, 4000, 3000)
        assertEquals(0f, x0, 0.5f)
        assertEquals(-180f, y0, 0.5f)

        val (cx, cy) = TexturePreviewFit.mapBufferToView(2000f, 1500f, 1920, 1080, 4000, 3000)
        assertEquals(960f, cx, 0.5f)
        assertEquals(540f, cy, 0.5f)
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

    /** Finder GL host uses portrait display aspect (1440×1920 → 3:4); raw HAL fit is in composeExternalOesPreviewMatrix. */
    @Test
    fun `largestAxisAlignedRect uses display aspect in portrait finder`() {
        val (w, h) = TexturePreviewFit.largestAxisAlignedRectWithAspect(1080, 1440, 1440f / 1920f)
        assertEquals(1080, w)
        assertEquals(1440, h)
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
    // computeFitRect — under center-crop preview this is the full TextureView (visible image fills it).
    // ----------------------------------------------------------------------------

    @Test
    fun `computeFitRect fills viewport for center crop preview portrait`() {
        val r = TexturePreviewFit.computeFitRect(1080, 1920, 1920, 1080)
        assertEquals(0f, r.left, 0.5f)
        assertEquals(0f, r.top, 0.5f)
        assertEquals(1080f, r.width, 0.5f)
        assertEquals(1920f, r.height, 0.5f)
    }

    @Test
    fun `computeFitRect fills viewport for center crop preview landscape`() {
        val r = TexturePreviewFit.computeFitRect(1920, 1080, 4000, 3000)
        assertEquals(0f, r.left, 0.5f)
        assertEquals(0f, r.top, 0.5f)
        assertEquals(1920f, r.width, 0.5f)
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

    /** Sprint **15.6** — 16:9 buffer in 3:4 portrait tile → horizontal pillarbox. */
    @Test
    fun `computeFitRect shrink-to-fit 16x9 buffer in 3x4 portrait tile`() {
        val r = TexturePreviewFit.computeFitRect(1080, 1440, 1920, 1080, coverCrop = false)
        assertEquals(1080f, r.width, 0.5f)
        assertEquals(607.5f, r.height, 1f)
        assertEquals(0f, r.left, 0.5f)
        assertEquals(416.25f, r.top, 1f)
    }

    @Test
    fun `mapBufferToView shrink-to-fit centers 16x9 in 3x4 portrait tile`() {
        val (cx, cy) = TexturePreviewFit.mapBufferToView(960f, 540f, 1080, 1440, 1920, 1080, coverCrop = false)
        assertEquals(540f, cx, 0.5f)
        assertEquals(720f, cy, 0.5f)
    }

    /** Sprint **15.6** — dual-video stacked halves use the same contain math per band. */
    @Test
    fun `computeFitRect stacked dual half 16x9 shrink-to-fit in 3x4 tile`() {
        val tileW = 1080
        val tileH = 1440
        val frontH =
            (tileH * DualVideoRecordingController.STACKED_FRONT_HEIGHT_FRACTION).toInt()
                .coerceIn(1, tileH - 1)
        val rearH = tileH - frontH
        val rear = TexturePreviewFit.computeFitRect(tileW, rearH, 1920, 1080, coverCrop = false)
        assertEquals(1080f, rear.width, 0.5f)
        assertEquals(607.5f, rear.height, 1f)
        assertEquals(56.25f, rear.top, 1f)
        val front = TexturePreviewFit.computeFitRect(tileW, frontH, 1920, 1080, coverCrop = false)
        assertEquals(1080f, front.width, 0.5f)
        assertEquals(607.5f, front.height, 1f)
        assertEquals(56.25f, front.top, 1f)
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

    @Test
    fun `mapBufferToViewWithExternalOesInvert null st matches mapBufferToView`() {
        val a = TexturePreviewFit.mapBufferToView(30f, 40f, 200, 300, 800, 600, coverCrop = true)
        val b =
            TexturePreviewFit.mapBufferToViewWithExternalOesInvert(
                30f,
                40f,
                200,
                300,
                800,
                600,
                coverCrop = true,
                null,
            )
        assertEquals(a.first, b.first, 0.05f)
        assertEquals(a.second, b.second, 0.05f)
    }
}
