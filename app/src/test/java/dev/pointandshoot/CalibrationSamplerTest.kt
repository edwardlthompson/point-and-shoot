package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationSamplerTest {

    // ---------- RgbPlane ----------

    @Test
    fun `RgbPlane uniform produces a flat plane of the requested color`() {
        val p = RgbPlane.uniform(width = 4, height = 3, r = 0.2f, g = 0.4f, b = 0.6f)
        assertEquals(4, p.width)
        assertEquals(3, p.height)
        for (y in 0 until 3) {
            for (x in 0 until 4) {
                val pix = p.pixel(x, y)
                assertEquals(0.2f, pix[0], 0f)
                assertEquals(0.4f, pix[1], 0f)
                assertEquals(0.6f, pix[2], 0f)
            }
        }
    }

    @Test
    fun `RgbPlane pixel clamps out-of-bounds coordinates`() {
        val p = RgbPlane.uniform(2, 2, 0.5f, 0.5f, 0.5f)
        val a = p.pixel(-5, -5)
        val b = p.pixel(99, 99)
        assertEquals(0.5f, a[0], 0f)
        assertEquals(0.5f, b[2], 0f)
    }

    @Test
    fun `RgbPlane rejects mismatched array length`() {
        val ex = runCatching { RgbPlane(FloatArray(10), 4, 3) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    // ---------- ChartCorners.bilinearMap ----------

    @Test
    fun `bilinearMap maps unit corners to chart corners exactly`() {
        val c = ChartCorners(
            tl = Point2(10f, 20f),
            tr = Point2(110f, 22f),
            br = Point2(115f, 220f),
            bl = Point2(8f, 218f),
        )
        val tl = c.bilinearMap(0f, 0f)
        assertEquals(10f, tl.x, 1e-4f); assertEquals(20f, tl.y, 1e-4f)
        val tr = c.bilinearMap(1f, 0f)
        assertEquals(110f, tr.x, 1e-4f); assertEquals(22f, tr.y, 1e-4f)
        val br = c.bilinearMap(1f, 1f)
        assertEquals(115f, br.x, 1e-4f); assertEquals(220f, br.y, 1e-4f)
        val bl = c.bilinearMap(0f, 1f)
        assertEquals(8f, bl.x, 1e-4f); assertEquals(218f, bl.y, 1e-4f)
    }

    @Test
    fun `bilinearMap center is the average of the four corners`() {
        val c = ChartCorners(
            tl = Point2(0f, 0f),
            tr = Point2(100f, 0f),
            br = Point2(100f, 200f),
            bl = Point2(0f, 200f),
        )
        val mid = c.bilinearMap(0.5f, 0.5f)
        assertEquals(50f, mid.x, 1e-4f)
        assertEquals(100f, mid.y, 1e-4f)
    }

    // ---------- sampleAt ----------

    @Test
    fun `sampleAt on a uniform plane returns the exact color and zero variance`() {
        val plane = RgbPlane.uniform(50, 50, r = 0.42f, g = 0.17f, b = 0.93f)
        val s = CalibrationSampler.sampleAt(plane, cx = 25f, cy = 25f, halfW = 5f, halfH = 5f)
        assertEquals(0.42f, s.mean[0], 1e-5f)
        assertEquals(0.17f, s.mean[1], 1e-5f)
        assertEquals(0.93f, s.mean[2], 1e-5f)
        assertEquals(0f, s.variance[0], 1e-9f)
        assertEquals(0f, s.variance[1], 1e-9f)
        assertEquals(0f, s.variance[2], 1e-9f)
        assertFalse(s.rejected)
    }

    @Test
    fun `sampleAt computes correct mean across two-color stripes`() {
        // Plane with left half red, right half blue.
        val w = 20; val h = 20
        val rgb = FloatArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = (y * w + x) * 3
                if (x < w / 2) {
                    rgb[idx] = 1f; rgb[idx + 1] = 0f; rgb[idx + 2] = 0f
                } else {
                    rgb[idx] = 0f; rgb[idx + 1] = 0f; rgb[idx + 2] = 1f
                }
            }
        }
        val plane = RgbPlane(rgb, w, h)
        // Sample the full plane: mean R + mean B = 0.5 each.
        val s = CalibrationSampler.sampleAt(plane, cx = 10f, cy = 10f, halfW = 10f, halfH = 10f)
        assertEquals(0.5f, s.mean[0], 1e-5f)
        assertEquals(0f, s.mean[1], 1e-5f)
        assertEquals(0.5f, s.mean[2], 1e-5f)
        // Variance for R = 0.5 * (1-0.5)^2 + 0.5 * (0-0.5)^2 = 0.25.
        assertEquals(0.25f, s.variance[0], 1e-5f)
        assertEquals(0.25f, s.variance[2], 1e-5f)
        // The variance is 0.25, well above the default 5e-3, so this should be flagged rejected.
        assertTrue(s.rejected)
        assertNotNull(s.rejectReason)
    }

    @Test
    fun `sampleAt clips to plane bounds`() {
        val plane = RgbPlane.uniform(10, 10, 0.5f, 0.5f, 0.5f)
        val s = CalibrationSampler.sampleAt(plane, cx = 0f, cy = 0f, halfW = 100f, halfH = 100f)
        // Should sample the entire 10x10 plane.
        assertEquals(100, s.samples)
        assertEquals(0.5f, s.mean[0], 1e-5f)
    }

    @Test
    fun `sampleAt off-plane returns empty rejected sample`() {
        val plane = RgbPlane.uniform(10, 10, 0.5f, 0.5f, 0.5f)
        val s = CalibrationSampler.sampleAt(plane, cx = -50f, cy = -50f, halfW = 1f, halfH = 1f)
        assertTrue(s.rejected)
        assertEquals(0, s.samples)
        assertNotNull(s.rejectReason)
    }

    @Test
    fun `sampleAt rejects when variance exceeds maxVariance`() {
        // Build a plane with deliberately noisy patch.
        val w = 10; val h = 10
        val rgb = FloatArray(w * h * 3)
        for (i in 0 until w * h) {
            val v = if (i % 2 == 0) 0f else 1f
            rgb[i * 3] = v; rgb[i * 3 + 1] = v; rgb[i * 3 + 2] = v
        }
        val plane = RgbPlane(rgb, w, h)
        val s = CalibrationSampler.sampleAt(plane, cx = 5f, cy = 5f, halfW = 5f, halfH = 5f,
            maxVariance = 0.01f)
        assertTrue("expected rejected (got reason=${s.rejectReason})", s.rejected)
    }

    @Test
    fun `sampleAt accepts when variance below maxVariance`() {
        val plane = RgbPlane.uniform(10, 10, 0.5f, 0.5f, 0.5f)
        val s = CalibrationSampler.sampleAt(plane, cx = 5f, cy = 5f, halfW = 4f, halfH = 4f,
            maxVariance = 1e-6f)
        assertFalse(s.rejected)
        assertNull(s.rejectReason)
    }

    // ---------- sample (full target) ----------

    @Test
    fun `sample over a uniform plane returns the same mean for every patch`() {
        val plane = RgbPlane.uniform(640, 480, r = 0.5f, g = 0.5f, b = 0.5f)
        val target = BundledReferenceTargets.Generic24
        val corners = ChartCorners(
            tl = Point2(50f, 50f),
            tr = Point2(590f, 50f),
            br = Point2(590f, 430f),
            bl = Point2(50f, 430f),
        )
        val samples = CalibrationSampler.sample(plane, target, corners)
        assertEquals(24, samples.size)
        for (s in samples) {
            assertEquals(0.5f, s.mean[0], 1e-5f)
            assertEquals(0.5f, s.mean[1], 1e-5f)
            assertEquals(0.5f, s.mean[2], 1e-5f)
            assertFalse(s.rejected)
        }
    }

    @Test
    fun `sample over a synthetic chart finds the right color per patch`() {
        // Build a synthetic chart: 4 rows x 6 cols of distinct flat colors,
        // mapped 1:1 onto a 600x400 image. We then sample with corners that
        // exactly match the chart bounds + a 5% border. Each measured patch
        // should match its chart cell color.
        val target = BundledReferenceTargets.Generic24
        val w = 600; val h = 400
        val rgb = FloatArray(w * h * 3)
        // Default border = 5% so the chart spans rows [20, 380) and cols [30, 570).
        val border = ReferenceTarget.DEFAULT_BORDER_FRAC
        val chartX0 = (border * w).toInt()
        val chartY0 = (border * h).toInt()
        val chartX1 = ((1f - border) * w).toInt()
        val chartY1 = ((1f - border) * h).toInt()
        val cellW = (chartX1 - chartX0) / target.cols
        val cellH = (chartY1 - chartY0) / target.rows
        for (patch in target.patches) {
            val px0 = chartX0 + patch.col * cellW
            val py0 = chartY0 + patch.row * cellH
            for (y in py0 until py0 + cellH) {
                for (x in px0 until px0 + cellW) {
                    val idx = (y * w + x) * 3
                    rgb[idx] = patch.referenceRgb[0]
                    rgb[idx + 1] = patch.referenceRgb[1]
                    rgb[idx + 2] = patch.referenceRgb[2]
                }
            }
        }
        val plane = RgbPlane(rgb, w, h)
        val corners = ChartCorners(
            tl = Point2(0f, 0f),
            tr = Point2(w - 1f, 0f),
            br = Point2(w - 1f, h - 1f),
            bl = Point2(0f, h - 1f),
        )
        val samples = CalibrationSampler.sample(plane, target, corners)
        for (i in samples.indices) {
            val s = samples[i]
            val ref = target.patches[i].referenceRgb
            assertFalse("${target.patches[i].name} should not be rejected (reason=${s.rejectReason})", s.rejected)
            // Sampling the inner-60% of each cell should hit only the patch color, so means should match exactly.
            assertEquals("${target.patches[i].name} R", ref[0], s.mean[0], 1e-3f)
            assertEquals("${target.patches[i].name} G", ref[1], s.mean[1], 1e-3f)
            assertEquals("${target.patches[i].name} B", ref[2], s.mean[2], 1e-3f)
            assertEquals(target.patches[i], s.patchRef)
        }
    }

    @Test
    fun `sampleNormalized rejects halfNorm out of range`() {
        val plane = RgbPlane.uniform(100, 100, 0.5f, 0.5f, 0.5f)
        val corners = ChartCorners(Point2(0f, 0f), Point2(99f, 0f), Point2(99f, 99f), Point2(0f, 99f))
        val ex = runCatching {
            CalibrationSampler.sampleNormalized(plane, Point2(0.5f, 0.5f), halfNorm = 0.6f, corners = corners)
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }
}
