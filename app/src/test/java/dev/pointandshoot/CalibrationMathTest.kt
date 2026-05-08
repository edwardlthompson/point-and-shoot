package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CalibrationMathTest {

    // ---------- computeWbGains ----------

    @Test
    fun `WB gains anchor green at 1`() {
        val gains = CalibrationMath.computeWbGains(
            listOf(floatArrayOf(0.4f, 0.5f, 0.6f), floatArrayOf(0.45f, 0.5f, 0.55f)),
        )
        assertEquals(1f, gains.g, 0f)
    }

    @Test
    fun `WB gains correct a magenta cast (low green)`() {
        // Average neutral patch is (0.5, 0.4, 0.5). To make it gray we need to scale G up to 0.5.
        // But our convention anchors G at 1 and scales R/B. So we need r = 0.4/0.5 = 0.8 and b = 0.4/0.5 = 0.8
        // (so R*r = G_avg = 0.4 and B*b = 0.4 when G stays at 0.4; effectively G is the new neutral).
        val gains = CalibrationMath.computeWbGains(listOf(floatArrayOf(0.5f, 0.4f, 0.5f)))
        assertEquals(0.8f, gains.r, 1e-4f)
        assertEquals(1f, gains.g, 0f)
        assertEquals(0.8f, gains.b, 1e-4f)
    }

    @Test
    fun `applying WB gains to neutrals yields a true gray (R = G = B)`() {
        val patch = floatArrayOf(0.6f, 0.4f, 0.5f) // warm, slightly cool blue
        val gains = CalibrationMath.computeWbGains(listOf(patch))
        val rOut = patch[0] * gains.r
        val gOut = patch[1] * gains.g
        val bOut = patch[2] * gains.b
        assertEquals("R == G after WB", rOut, gOut, 1e-3f)
        assertEquals("G == B after WB", gOut, bOut, 1e-3f)
    }

    @Test
    fun `averaging across multiple gray patches converges on a sensible mean`() {
        // 3 patches with the same hue but varying brightness; gain should be the same.
        val patches = listOf(
            floatArrayOf(0.2f, 0.25f, 0.3f),
            floatArrayOf(0.4f, 0.5f, 0.6f),
            floatArrayOf(0.6f, 0.75f, 0.9f),
        )
        val gains = CalibrationMath.computeWbGains(patches)
        // Each patch has R/G = 0.8 and B/G = 1.2; expect r ≈ 1/0.8 = 1.25 and b ≈ 1/1.2 = 0.833.
        assertEquals(1.25f, gains.r, 1e-3f)
        assertEquals(1f, gains.g, 0f)
        assertEquals(0.833f, gains.b, 1e-3f)
    }

    @Test
    fun `WB throws on empty input`() {
        val ex = runCatching { CalibrationMath.computeWbGains(emptyList()) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `WB throws when neutrals appear black`() {
        val ex = runCatching {
            CalibrationMath.computeWbGains(listOf(floatArrayOf(0.0001f, 0.00001f, 0.0001f)))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    // ---------- computeCcm ----------

    @Test
    fun `CCM recovers identity when measured equals target`() {
        val patches = listOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(0.5f, 0.5f, 0.5f),
        )
        val ccm = CalibrationMath.computeCcm(measured = patches, target = patches)
        assertNear(CalibrationProfile.Ccm.Identity, ccm, tolerance = 1e-3f)
    }

    @Test
    fun `CCM recovers a known channel-swap matrix`() {
        // Target = swap_rb * measured.
        val measured = listOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(0.3f, 0.5f, 0.7f),
            floatArrayOf(0.8f, 0.2f, 0.4f),
        )
        val target = measured.map { floatArrayOf(it[2], it[1], it[0]) }
        val ccm = CalibrationMath.computeCcm(measured, target)
        val expected = CalibrationProfile.Ccm(
            m00 = 0f, m01 = 0f, m02 = 1f,
            m10 = 0f, m11 = 1f, m12 = 0f,
            m20 = 1f, m21 = 0f, m22 = 0f,
        )
        assertNear(expected, ccm, tolerance = 1e-3f)
    }

    @Test
    fun `CCM recovers a non-trivial mixing matrix from synthetic patches`() {
        // True matrix: a small color-corrective rotation in RGB space.
        val truth = CalibrationProfile.Ccm(
            m00 = 1.10f, m01 = -0.05f, m02 = -0.05f,
            m10 = -0.10f, m11 = 1.20f, m12 = -0.10f,
            m20 = 0.00f, m21 = -0.10f, m22 = 1.10f,
        )
        val measured = listOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(0.5f, 0.5f, 0.5f),
            floatArrayOf(0.2f, 0.7f, 0.3f),
            floatArrayOf(0.9f, 0.1f, 0.6f),
            floatArrayOf(0.4f, 0.4f, 0.4f),
        )
        val target = measured.map { applyCcm(truth, it) }
        val solved = CalibrationMath.computeCcm(measured, target)
        assertNear(truth, solved, tolerance = 1e-3f)
    }

    @Test
    fun `CCM throws when measured and target lengths differ`() {
        val ex = runCatching {
            CalibrationMath.computeCcm(
                measured = listOf(floatArrayOf(1f, 0f, 0f)),
                target = listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)),
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `CCM throws when fewer than 3 patches supplied`() {
        val ex = runCatching {
            CalibrationMath.computeCcm(
                measured = listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)),
                target = listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)),
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `CCM throws on a singular system (rank-deficient measured matrix)`() {
        // Three colinear samples - measured matrix has rank 1.
        val measured = listOf(
            floatArrayOf(0.1f, 0.1f, 0.1f),
            floatArrayOf(0.2f, 0.2f, 0.2f),
            floatArrayOf(0.3f, 0.3f, 0.3f),
        )
        val target = measured // identity, but underdetermined.
        val ex = runCatching { CalibrationMath.computeCcm(measured, target) }.exceptionOrNull()
        assertTrue("expected singular-matrix failure (was $ex)", ex is IllegalArgumentException)
    }

    // ---------- helpers ----------

    private fun applyCcm(ccm: CalibrationProfile.Ccm, rgb: FloatArray): FloatArray {
        return floatArrayOf(
            ccm.m00 * rgb[0] + ccm.m01 * rgb[1] + ccm.m02 * rgb[2],
            ccm.m10 * rgb[0] + ccm.m11 * rgb[1] + ccm.m12 * rgb[2],
            ccm.m20 * rgb[0] + ccm.m21 * rgb[1] + ccm.m22 * rgb[2],
        )
    }

    private fun assertNear(expected: CalibrationProfile.Ccm, actual: CalibrationProfile.Ccm, tolerance: Float) {
        val pairs = listOf(
            "m00" to (expected.m00 to actual.m00),
            "m01" to (expected.m01 to actual.m01),
            "m02" to (expected.m02 to actual.m02),
            "m10" to (expected.m10 to actual.m10),
            "m11" to (expected.m11 to actual.m11),
            "m12" to (expected.m12 to actual.m12),
            "m20" to (expected.m20 to actual.m20),
            "m21" to (expected.m21 to actual.m21),
            "m22" to (expected.m22 to actual.m22),
        )
        for ((label, both) in pairs) {
            val (e, a) = both
            assertTrue("$label expected=$e actual=$a", abs(e - a) <= tolerance)
        }
    }
}
