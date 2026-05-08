package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

class SlantedEdgeMtfTest {

    // ---------- GrayPlane ----------

    @Test
    fun `GrayPlane build samples the supplied function`() {
        val g = GrayPlane.build(width = 4, height = 3) { x, y -> 0.1f * x + 0.2f * y }
        assertEquals(0f, g.luma(0, 0), 1e-6f)
        assertEquals(0.3f, g.luma(3, 0), 1e-6f)
        assertEquals(0.4f, g.luma(0, 2), 1e-6f) // 0.2 * 2 = 0.4
        assertEquals(0.7f, g.luma(3, 2), 1e-6f) // 0.3 + 0.4 = 0.7
    }

    @Test
    fun `GrayPlane transpose swaps width and height`() {
        val g = GrayPlane.build(width = 4, height = 2) { x, y -> 0.25f * x + 0.5f * y }
        val t = g.transpose()
        assertEquals(2, t.width)
        assertEquals(4, t.height)
        for (y in 0 until 2) {
            for (x in 0 until 4) {
                assertEquals(g.luma(x, y), t.luma(y, x), 1e-6f)
            }
        }
    }

    // ---------- input validation ----------

    @Test
    fun `measureMtf50 rejects oversample less than 1`() {
        val plane = GrayPlane.build(64, 64) { _, _ -> 0.5f }
        val ex = runCatching { SlantedEdgeMtf.measureMtf50(plane, oversampleFactor = 0) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `measureMtf50 rejects non-power-of-2 esfBins`() {
        val plane = GrayPlane.build(64, 64) { _, _ -> 0.5f }
        val ex = runCatching { SlantedEdgeMtf.measureMtf50(plane, esfBins = 100) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    // ---------- behavior on edge-free input ----------

    @Test
    fun `measureMtf50 returns null on a uniform plane (no edge)`() {
        val plane = GrayPlane.build(64, 64) { _, _ -> 0.5f }
        assertNull(SlantedEdgeMtf.measureMtf50(plane))
    }

    @Test
    fun `measureMtf50 returns null on too-few-rows`() {
        val plane = makeSlantedEdge(width = 64, height = 4, slope = 0.1f, intercept = 32f, sigma = 0f)
        assertNull(SlantedEdgeMtf.measureMtf50(plane))
    }

    // ---------- core property: sharper edges yield higher MTF50 ----------

    @Test
    fun `sharp edge gives higher MTF50 than blurred edge`() {
        val sharp = makeSlantedEdge(width = 96, height = 96, slope = 0.1f, intercept = 48f, sigma = 0.3f)
        val blurred = makeSlantedEdge(width = 96, height = 96, slope = 0.1f, intercept = 48f, sigma = 1.5f)
        val sharpMtf50 = SlantedEdgeMtf.measureMtf50(sharp)
        val blurredMtf50 = SlantedEdgeMtf.measureMtf50(blurred)
        assertNotNull(sharpMtf50)
        assertNotNull(blurredMtf50)
        assertTrue(
            "sharp ($sharpMtf50) should have higher MTF50 than blurred ($blurredMtf50)",
            sharpMtf50!! > blurredMtf50!!,
        )
    }

    @Test
    fun `progressively blurrier edges produce monotonically decreasing MTF50`() {
        val mtfs = listOf(0.5f, 1.0f, 1.5f, 2.0f).map { sigma ->
            sigma to SlantedEdgeMtf.measureMtf50(
                makeSlantedEdge(width = 96, height = 96, slope = 0.07f, intercept = 48f, sigma = sigma),
            )
        }
        for ((sigma, mtf50) in mtfs) {
            assertNotNull("sigma=$sigma should produce a valid MTF50", mtf50)
        }
        for (i in 0 until mtfs.size - 1) {
            val (sigmaA, mtfA) = mtfs[i]
            val (sigmaB, mtfB) = mtfs[i + 1]
            assertTrue(
                "MTF50($sigmaA)=$mtfA should be > MTF50($sigmaB)=$mtfB",
                mtfA!! > mtfB!!,
            )
        }
    }

    // ---------- numerical sanity vs Gaussian-blur analytic formula ----------

    /**
     * For a Gaussian-blurred ideal edge with sigma px, the analytic MTF50 in
     * cycles per pixel is `sqrt(ln(2) / (2 * pi^2 * sigma^2)) ≈ 0.1874 / sigma`.
     * Our discrete pipeline (binning + window + DFT) introduces a moderate bias,
     * but the measured value should land in the ballpark.
     */
    @Test
    fun `MTF50 of Gaussian-blurred edge is in the ballpark of the analytic formula`() {
        val sigma = 1.0f
        val plane = makeSlantedEdge(width = 96, height = 96, slope = 0.07f, intercept = 48f, sigma = sigma)
        val measured = SlantedEdgeMtf.measureMtf50(plane)
        assertNotNull(measured)
        val analytic = (sqrt(kotlin.math.ln(2.0) / (2.0 * kotlin.math.PI * kotlin.math.PI * sigma * sigma))).toFloat()
        // Our discrete pipeline (Hamming window narrows the LSF, finite oversampling
        // floors the resolution) typically reads slightly low; 50 % tolerance is
        // appropriate for a sanity check.
        assertTrue(
            "measured=$measured analytic=$analytic (sigma=$sigma)",
            measured!! in analytic * 0.5f..analytic * 1.5f,
        )
    }

    @Test
    fun `MTF50 stays bounded by Nyquist of the oversampled signal`() {
        val plane = makeSlantedEdge(width = 96, height = 96, slope = 0.1f, intercept = 48f, sigma = 0.1f)
        val mtf = SlantedEdgeMtf.measureMtf50(plane, oversampleFactor = 4, esfBins = 128)
        assertNotNull(mtf)
        // Nyquist of the oversampled signal in the cycles/pixel domain is `oversample/2 = 2.0`,
        // but for a 1-pixel-wide LSF on a real edge we expect MTF50 to land below ~0.6 c/px.
        assertTrue("$mtf should be a sane MTF50", mtf!! in 0.05f..2.0f)
    }

    // ---------- horizontal-edge variant ----------

    @Test
    fun `near-horizontal edge with NearHorizontal orientation yields a similar MTF50`() {
        val vertical = makeSlantedEdge(width = 96, height = 96, slope = 0.07f, intercept = 48f, sigma = 1.0f)
        val horizontal = vertical.transpose()
        val vertMtf = SlantedEdgeMtf.measureMtf50(vertical, orientation = SlantedEdgeMtf.EdgeOrientation.NearVertical)
        val horizMtf = SlantedEdgeMtf.measureMtf50(horizontal, orientation = SlantedEdgeMtf.EdgeOrientation.NearHorizontal)
        assertNotNull(vertMtf); assertNotNull(horizMtf)
        // Should be within 15 % of each other.
        val ratio = vertMtf!! / horizMtf!!
        assertTrue("vert/horiz ratio = $ratio (vert=$vertMtf horiz=$horizMtf)", ratio in 0.85f..1.18f)
    }

    // ---------- lp/ph helper ----------

    @Test
    fun `cyclesPerPixelToLpph multiplies by picture height`() {
        assertEquals(2400f, SlantedEdgeMtf.cyclesPerPixelToLpph(0.5f, 4800), 1e-3f)
        assertEquals(0f, SlantedEdgeMtf.cyclesPerPixelToLpph(0f, 4800), 0f)
    }

    // ---------- helpers ----------

    /**
     * Synthesize a slanted-edge ROI: pixel value drops from 1.0 (left of edge) to
     * 0.0 (right of edge) at column `slope * y + intercept`. When [sigma] > 0,
     * the edge is convolved with a Gaussian of that std-dev (in pixels) so we
     * can drive the MTF50 measurement against a known analytical reference.
     */
    private fun makeSlantedEdge(
        width: Int,
        height: Int,
        slope: Float,
        intercept: Float,
        sigma: Float,
    ): GrayPlane {
        return GrayPlane.build(width, height) { x, y ->
            val edgeX = slope * y + intercept
            if (sigma <= 0f) {
                if (x.toFloat() < edgeX) 1f else 0f
            } else {
                val z = (x.toFloat() - edgeX) / sigma
                // CDF of standard normal via erf approximation (Abramowitz 7.1.26).
                val cdf = 0.5f * (1f + erfApprox(z / sqrt(2f)))
                1f - cdf
            }
        }
    }

    private fun erfApprox(x: Float): Float {
        val a1 = 0.254829592f
        val a2 = -0.284496736f
        val a3 = 1.421413741f
        val a4 = -1.453152027f
        val a5 = 1.061405429f
        val p = 0.3275911f
        val sign = if (x < 0) -1f else 1f
        val ax = kotlin.math.abs(x)
        val t = 1f / (1f + p * ax)
        val y = 1f - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-ax * ax)
        return sign * y
    }
}
