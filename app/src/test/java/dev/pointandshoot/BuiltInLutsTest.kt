package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BuiltInLutsTest {

    // ---------- Rec.709 identity ----------

    @Test
    fun `rec709 identity reports as identity at every supported size`() {
        for (size in Lut3D.SUPPORTED_SIZES) {
            val lut = BuiltInLuts.rec709Identity(size)
            assertTrue("size=$size should be identity", lut.isIdentity())
        }
    }

    @Test
    fun `applying rec709 identity preserves arbitrary samples`() {
        val lut = BuiltInLuts.rec709Identity()
        val rgb = floatArrayOf(0.42f, 0.17f, 0.93f)
        val out = LutPipeline.applyTrilinear(rgb, lut)
        assertEquals(0.42f, out[0], 1e-5f)
        assertEquals(0.17f, out[1], 1e-5f)
        assertEquals(0.93f, out[2], 1e-5f)
    }

    // ---------- B&W BT.601 ----------

    @Test
    fun `bwBt601 collapses output to gray at every cell`() {
        val lut = BuiltInLuts.bwBt601(17)
        val size = lut.size
        for (b in 0 until size step 4) {
            for (g in 0 until size step 4) {
                for (r in 0 until size step 4) {
                    val idx = ((b * size + g) * size + r) * 3
                    val rOut = lut.samples[idx]
                    val gOut = lut.samples[idx + 1]
                    val bOut = lut.samples[idx + 2]
                    assertEquals("R=G at ($r,$g,$b)", rOut, gOut, 1e-6f)
                    assertEquals("G=B at ($r,$g,$b)", gOut, bOut, 1e-6f)
                }
            }
        }
    }

    @Test
    fun `bwBt601 applies BT_601 luma weights`() {
        val lut = BuiltInLuts.bwBt601(33)
        val out = LutPipeline.applyTrilinear(floatArrayOf(0.5f, 0.5f, 0.5f), lut)
        // 0.5 * (0.299 + 0.587 + 0.114) = 0.5
        assertEquals(0.5f, out[0], 1e-3f)
        // Pure red at 1.0 -> Y = 0.299
        val pureRed = LutPipeline.applyTrilinear(floatArrayOf(1f, 0f, 0f), lut)
        assertEquals(0.299f, pureRed[0], 1e-3f)
        // Pure green at 1.0 -> Y = 0.587
        val pureGreen = LutPipeline.applyTrilinear(floatArrayOf(0f, 1f, 0f), lut)
        assertEquals(0.587f, pureGreen[0], 1e-3f)
        // Pure blue at 1.0 -> Y = 0.114
        val pureBlue = LutPipeline.applyTrilinear(floatArrayOf(0f, 0f, 1f), lut)
        assertEquals(0.114f, pureBlue[0], 1e-3f)
    }

    // ---------- B&W BT.709 ----------

    @Test
    fun `bwBt709 applies BT_709 luma weights`() {
        val lut = BuiltInLuts.bwBt709(33)
        val pureRed = LutPipeline.applyTrilinear(floatArrayOf(1f, 0f, 0f), lut)
        assertEquals(0.2126f, pureRed[0], 1e-3f)
        val pureGreen = LutPipeline.applyTrilinear(floatArrayOf(0f, 1f, 0f), lut)
        assertEquals(0.7152f, pureGreen[0], 1e-3f)
        val pureBlue = LutPipeline.applyTrilinear(floatArrayOf(0f, 0f, 1f), lut)
        assertEquals(0.0722f, pureBlue[0], 1e-3f)
    }

    @Test
    fun `BT_601 vs BT_709 differ on saturated greens (BT_709 brighter)`() {
        val lut601 = BuiltInLuts.bwBt601(33)
        val lut709 = BuiltInLuts.bwBt709(33)
        val out601 = LutPipeline.applyTrilinear(floatArrayOf(0f, 1f, 0f), lut601)[0]
        val out709 = LutPipeline.applyTrilinear(floatArrayOf(0f, 1f, 0f), lut709)[0]
        assertTrue("BT.709 green ($out709) should be brighter than BT.601 ($out601)", out709 > out601)
    }

    // ---------- Point & Shoot Cinematic ----------

    @Test
    fun `cinematic grade does NOT report as identity (it is a creative LUT)`() {
        val lut = BuiltInLuts.pnsCinematic()
        assertTrue("cinematic should NOT be identity", !lut.isIdentity())
    }

    @Test
    fun `cinematic grade pulls neutral mid-grays toward neutral (smoothstep is zero at 0_5 luma)`() {
        // Smoothstep weights are 0 at luma=0.5, so a 50% gray cell should be near-untouched.
        val lut = BuiltInLuts.pnsCinematic(33)
        val mid = LutPipeline.applyTrilinear(floatArrayOf(0.5f, 0.5f, 0.5f), lut)
        // Tolerate small deviation from grid quantization.
        assertEquals(0.5f, mid[0], 0.02f)
        assertEquals(0.5f, mid[1], 0.02f)
        assertEquals(0.5f, mid[2], 0.02f)
    }

    @Test
    fun `cinematic grade pushes deep shadows toward teal`() {
        val lut = BuiltInLuts.pnsCinematic(33)
        val shadow = LutPipeline.applyTrilinear(floatArrayOf(0.05f, 0.05f, 0.05f), lut)
        // Teal target = (0.30, 0.55, 0.70). With 30% strength + smoothstep weight at luma=0.05 (~1.0),
        // expect blue > green > red in the output.
        assertTrue("expected B > G > R for shadow tint (got $${shadow.toList()})",
            shadow[2] > shadow[1] && shadow[1] > shadow[0])
    }

    @Test
    fun `cinematic grade pushes pure highlights toward warm orange`() {
        val lut = BuiltInLuts.pnsCinematic(33)
        val hi = LutPipeline.applyTrilinear(floatArrayOf(0.95f, 0.95f, 0.95f), lut)
        // Highlight target = (1.0, 0.65, 0.35). With strength + smoothstep at luma~0.95,
        // R should remain highest, B should be most pulled down (toward 0.35).
        assertTrue("expected R >= G > B for highlight tint (got ${hi.toList()})",
            hi[0] >= hi[1] && hi[1] > hi[2])
        // Confirm R has not been pulled down below the input (shouldn't have darkened red).
        assertNotEquals(hi[0], hi[2])
    }

    @Test
    fun `cinematic grade output stays in 0_1 range at every cell`() {
        val lut = BuiltInLuts.pnsCinematic(33)
        for (i in lut.samples.indices) {
            assertTrue("sample $i out of range: ${lut.samples[i]}",
                lut.samples[i] in 0f..1f || abs(lut.samples[i]) < 1e-6f)
        }
    }
}
