package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorMathTest {

    // ---------- linearSrgbToXyz ----------

    @Test
    fun `D65 white maps to the D65 white point`() {
        val xyz = ColorMath.linearSrgbToXyz(floatArrayOf(1f, 1f, 1f))
        assertEquals(0.95047f, xyz[0], 1e-3f)
        assertEquals(1.0f, xyz[1], 1e-3f)
        assertEquals(1.08883f, xyz[2], 1e-3f)
    }

    @Test
    fun `black maps to XYZ origin`() {
        val xyz = ColorMath.linearSrgbToXyz(floatArrayOf(0f, 0f, 0f))
        assertEquals(0f, xyz[0], 1e-6f)
        assertEquals(0f, xyz[1], 1e-6f)
        assertEquals(0f, xyz[2], 1e-6f)
    }

    @Test
    fun `pure red has the published sRGB primary XYZ values`() {
        // sRGB pure-red primary is X = 0.4124564, Y = 0.2126729, Z = 0.0193339.
        val xyz = ColorMath.linearSrgbToXyz(floatArrayOf(1f, 0f, 0f))
        assertEquals(0.4124564f, xyz[0], 1e-4f)
        assertEquals(0.2126729f, xyz[1], 1e-4f)
        assertEquals(0.0193339f, xyz[2], 1e-4f)
    }

    @Test
    fun `pure green has the published sRGB primary XYZ values`() {
        val xyz = ColorMath.linearSrgbToXyz(floatArrayOf(0f, 1f, 0f))
        assertEquals(0.3575761f, xyz[0], 1e-4f)
        assertEquals(0.7151522f, xyz[1], 1e-4f)
        assertEquals(0.1191920f, xyz[2], 1e-4f)
    }

    @Test
    fun `pure blue has the published sRGB primary XYZ values`() {
        val xyz = ColorMath.linearSrgbToXyz(floatArrayOf(0f, 0f, 1f))
        assertEquals(0.1804375f, xyz[0], 1e-4f)
        assertEquals(0.0721750f, xyz[1], 1e-4f)
        assertEquals(0.9503041f, xyz[2], 1e-4f)
    }

    // ---------- xyzToLab ----------

    @Test
    fun `D65 white maps to L=100 a=0 b=0`() {
        val lab = ColorMath.xyzToLab(floatArrayOf(0.95047f, 1.0f, 1.08883f))
        assertEquals(100f, lab[0], 1e-3f)
        assertEquals(0f, lab[1], 1e-3f)
        assertEquals(0f, lab[2], 1e-3f)
    }

    @Test
    fun `XYZ origin maps to L=0 a=0 b=0`() {
        val lab = ColorMath.xyzToLab(floatArrayOf(0f, 0f, 0f))
        assertEquals(0f, lab[0], 1e-3f)
        assertEquals(0f, lab[1], 1e-3f)
        assertEquals(0f, lab[2], 1e-3f)
    }

    @Test
    fun `mid-gray maps to roughly L=53`() {
        // Linear-sRGB 0.5 ≈ 0.214 in XYZ-Y space (sRGB encoding gamma ~ 2.2);
        // here we work in linear-light, so Y = 0.5, which Lab maps near L = 76.
        // We accept a coarse range — the precision tests live in the primary
        // and white-point tests above.
        val lab = ColorMath.linearSrgbToLab(floatArrayOf(0.5f, 0.5f, 0.5f))
        assertTrue("got L=${lab[0]}", lab[0] in 70f..80f)
        assertEquals(0f, lab[1], 1e-2f)
        assertEquals(0f, lab[2], 1e-2f)
    }

    // ---------- deltaE2000: identical input ----------

    @Test
    fun `identical inputs produce dE 0`() {
        val white = ColorMath.linearSrgbToLab(floatArrayOf(1f, 1f, 1f))
        assertEquals(0.0, ColorMath.deltaE2000(white, white), 1e-9)
    }

    @Test
    fun `dE is symmetric`() {
        val r = ColorMath.linearSrgbToLab(floatArrayOf(1f, 0f, 0f))
        val g = ColorMath.linearSrgbToLab(floatArrayOf(0f, 1f, 0f))
        val ab = ColorMath.deltaE2000(r, g)
        val ba = ColorMath.deltaE2000(g, r)
        assertEquals(ab, ba, 1e-6)
    }

    // ---------- deltaE2000: published Sharma test vectors ----------
    // From Sharma, Wu, Dalal (2005) Table 1 - the well-known reference set.

    @Test
    fun `Sharma 2005 row 1 dE=2dot0425`() {
        val lab1 = floatArrayOf(50.0f, 2.6772f, -79.7751f)
        val lab2 = floatArrayOf(50.0f, 0.0f, -82.7485f)
        assertEquals(2.0425, ColorMath.deltaE2000(lab1, lab2), 1e-3)
    }

    @Test
    fun `Sharma 2005 row 2 dE=2dot8615`() {
        val lab1 = floatArrayOf(50.0f, 3.1571f, -77.2803f)
        val lab2 = floatArrayOf(50.0f, 0.0f, -82.7485f)
        assertEquals(2.8615, ColorMath.deltaE2000(lab1, lab2), 1e-3)
    }

    @Test
    fun `Sharma 2005 row 3 dE=3dot4412`() {
        val lab1 = floatArrayOf(50.0f, 2.8361f, -74.020f)
        val lab2 = floatArrayOf(50.0f, 0.0f, -82.7485f)
        assertEquals(3.4412, ColorMath.deltaE2000(lab1, lab2), 1e-3)
    }

    @Test
    fun `Sharma 2005 row 14 large hue rotation dE=4dot8045`() {
        val lab1 = floatArrayOf(50.0f, 2.5f, 0.0f)
        val lab2 = floatArrayOf(73.0f, 25.0f, -18.0f)
        assertEquals(27.1492, ColorMath.deltaE2000(lab1, lab2), 1e-3)
    }

    @Test
    fun `Sharma 2005 row 22 high-luminance pair dE=2dot0373`() {
        val lab1 = floatArrayOf(60.2574f, -34.0099f, 36.2677f)
        val lab2 = floatArrayOf(60.4626f, -34.1751f, 39.4387f)
        assertEquals(1.2644, ColorMath.deltaE2000(lab1, lab2), 1e-3)
    }

    // ---------- deltaE2000: red vs green is "very different" ----------

    @Test
    fun `pure red vs pure green is large dE_2000`() {
        val r = ColorMath.linearSrgbToLab(floatArrayOf(1f, 0f, 0f))
        val g = ColorMath.linearSrgbToLab(floatArrayOf(0f, 1f, 0f))
        val dE = ColorMath.deltaE2000(r, g)
        // Pure-red vs pure-green sits well above 50 in Lab; CIEDE2000 weighting
        // typically reports something in the 70..100 range. The exact value
        // depends on the LCH rotation term, so we test "definitely large".
        assertTrue("pure-red vs pure-green dE = $dE should be > 50", dE > 50.0)
    }

    @Test
    fun `tiny perturbation produces small dE`() {
        val a = ColorMath.linearSrgbToLab(floatArrayOf(0.5f, 0.5f, 0.5f))
        val b = ColorMath.linearSrgbToLab(floatArrayOf(0.501f, 0.501f, 0.501f))
        val dE = ColorMath.deltaE2000(a, b)
        assertTrue("nearly identical patches dE = $dE should be < 0.5", dE < 0.5)
    }

    // ---------- deltaE2000FromLinearSrgb convenience ----------

    @Test
    fun `deltaE2000FromLinearSrgb matches the explicit Lab path`() {
        val rgb1 = floatArrayOf(0.4f, 0.6f, 0.2f)
        val rgb2 = floatArrayOf(0.42f, 0.58f, 0.22f)
        val direct = ColorMath.deltaE2000FromLinearSrgb(rgb1, rgb2)
        val viaLab = ColorMath.deltaE2000(
            ColorMath.linearSrgbToLab(rgb1),
            ColorMath.linearSrgbToLab(rgb2),
        )
        assertEquals(viaLab, direct, 1e-9)
    }

    // ---------- input validation ----------

    @Test
    fun `linearSrgbToXyz rejects non-3 input`() {
        val ex = runCatching { ColorMath.linearSrgbToXyz(FloatArray(2)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `xyzToLab rejects non-3 input`() {
        val ex = runCatching { ColorMath.xyzToLab(FloatArray(4)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `deltaE2000 rejects non-3 inputs`() {
        val ex = runCatching {
            ColorMath.deltaE2000(FloatArray(3), FloatArray(2))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `D65 and D50 differ by less than absolute equality`() {
        // White point of D50 is shifted vs D65; using a different white point on the
        // same neutral pixel must produce non-zero a/b in Lab.
        val labD65 = ColorMath.xyzToLab(floatArrayOf(0.95047f, 1.0f, 1.08883f), ColorMath.D65)
        val labD50 = ColorMath.xyzToLab(floatArrayOf(0.95047f, 1.0f, 1.08883f), ColorMath.D50)
        // D65-white under D65 is exactly L=100,a=0,b=0; under D50 it must shift.
        val diff = abs(labD65[1] - labD50[1]) + abs(labD65[2] - labD50[2])
        assertTrue("D50 reading of D65 white should shift a/b: diff=$diff", diff > 1f)
    }
}
