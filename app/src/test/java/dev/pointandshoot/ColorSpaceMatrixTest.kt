package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSpaceMatrixTest {

    private val tol = 5e-4f

    private fun assertMatrixEquals(expected: Array<FloatArray>, actual: Array<FloatArray>, t: Float = tol) {
        for (i in 0..2) {
            for (j in 0..2) {
                assertEquals("at [$i][$j]", expected[i][j], actual[i][j], t)
            }
        }
    }

    // ---------- sRGB -> XYZ_D65 (Lindbloom reference) ----------

    @Test
    fun `sRGB primaries match the published Lindbloom matrix to 5e-4`() {
        // Bruce Lindbloom's published linear-sRGB -> XYZ_D65 matrix:
        val expected = arrayOf(
            floatArrayOf(0.4124564f, 0.3575761f, 0.1804375f),
            floatArrayOf(0.2126729f, 0.7151522f, 0.0721750f),
            floatArrayOf(0.0193339f, 0.1191920f, 0.9503041f),
        )
        assertMatrixEquals(expected, ColorSpaceMatrix.SRGB_TO_XYZ_D65)
    }

    @Test
    fun `sRGB row 1 sums to whitepoint Y under D65`() {
        // Row 1 (Y row) summed yields Y of the whitepoint = 1.
        val sum = ColorSpaceMatrix.SRGB_TO_XYZ_D65[1].sum()
        assertEquals(1f, sum, 1e-3f)
    }

    @Test
    fun `Rec2020 primaries match the published BT 2020 matrix`() {
        // Reference: ITU-R BT.2020 published matrix.
        val expected = arrayOf(
            floatArrayOf(0.6369580f, 0.1446169f, 0.1688810f),
            floatArrayOf(0.2627002f, 0.6779981f, 0.0593017f),
            floatArrayOf(0.0000000f, 0.0280727f, 1.0609851f),
        )
        assertMatrixEquals(expected, ColorSpaceMatrix.REC2020_TO_XYZ_D65, 1e-3f)
    }

    @Test
    fun `DCI-P3 primaries match the published RP 431-2 matrix to 5e-3`() {
        // Reference: SMPTE RP 431-2 published matrix (D65 whitepoint variant).
        val expected = arrayOf(
            floatArrayOf(0.4865709f, 0.2656677f, 0.1982173f),
            floatArrayOf(0.2289746f, 0.6917385f, 0.0792869f),
            floatArrayOf(0.0000000f, 0.0451134f, 1.0439444f),
        )
        assertMatrixEquals(expected, ColorSpaceMatrix.DCI_P3_TO_XYZ_D65, 5e-3f)
    }

    @Test
    fun `ACES AP1 primaries are well-formed`() {
        val m = ColorSpaceMatrix.ACES_AP1_TO_XYZ_D60
        for (row in m) {
            for (v in row) {
                assertTrue("AP1 entry out of bounds: $v", v in -2f..2f)
            }
        }
        // Y row sums to 1 (whitepoint Y).
        assertEquals(1f, m[1].sum(), 1e-3f)
    }

    // ---------- Bradford CAT ----------

    @Test
    fun `Bradford D65 to D50 matches the canonical ICC matrix`() {
        // Bruce Lindbloom's published Bradford D65 -> D50 matrix:
        val expected = arrayOf(
            floatArrayOf(1.0478112f, 0.0228866f, -0.0501270f),
            floatArrayOf(0.0295424f, 0.9904844f, -0.0170491f),
            floatArrayOf(-0.0092345f, 0.0150436f, 0.7521316f),
        )
        assertMatrixEquals(expected, ColorSpaceMatrix.BRADFORD_D65_TO_D50, 1e-3f)
    }

    @Test
    fun `Bradford D65 to D50 round-trips through D50 to D65`() {
        val rt = ColorSpaceMatrix.multiply(
            ColorSpaceMatrix.BRADFORD_D50_TO_D65,
            ColorSpaceMatrix.BRADFORD_D65_TO_D50,
        )
        val identity = arrayOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f),
        )
        assertMatrixEquals(identity, rt, 1e-3f)
    }

    @Test
    fun `Bradford D65 to D50 maps D65 whitepoint to D50 whitepoint`() {
        val d65 = ColorSpaceMatrix.whitepointXyz(ColorSpaceMatrix.Illuminants.D65)
        val d50 = ColorSpaceMatrix.whitepointXyz(ColorSpaceMatrix.Illuminants.D50)
        val out = ColorSpaceMatrix.multiplyVec(ColorSpaceMatrix.BRADFORD_D65_TO_D50, d65)
        assertEquals(d50[0], out[0], 1e-3f)
        assertEquals(d50[1], out[1], 1e-3f)
        assertEquals(d50[2], out[2], 1e-3f)
    }

    // ---------- Matrix utilities ----------

    @Test
    fun `invert times original equals identity`() {
        val m = arrayOf(
            floatArrayOf(2f, 0f, 1f),
            floatArrayOf(1f, 1f, 1f),
            floatArrayOf(0f, 1f, 0f),
        )
        val rt = ColorSpaceMatrix.multiply(m, ColorSpaceMatrix.invert3x3(m))
        val id = arrayOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f),
        )
        assertMatrixEquals(id, rt, 1e-5f)
    }

    @Test
    fun `invert throws on singular matrix`() {
        val singular = arrayOf(
            floatArrayOf(1f, 2f, 3f),
            floatArrayOf(2f, 4f, 6f),
            floatArrayOf(0f, 0f, 0f),
        )
        try {
            ColorSpaceMatrix.invert3x3(singular)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `multiplyVec rejects wrong-length input`() {
        val m = ColorSpaceMatrix.SRGB_TO_XYZ_D65
        try {
            ColorSpaceMatrix.multiplyVec(m, floatArrayOf(1f, 2f))
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---------- Chromaticity validation ----------

    @Test
    fun `Chromaticity rejects grossly out-of-range values`() {
        try {
            ColorSpaceMatrix.Chromaticity(-2f, 0.5f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        try {
            ColorSpaceMatrix.Chromaticity(0.5f, 2f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        try {
            ColorSpaceMatrix.Chromaticity(0.5f, 0f)
            org.junit.Assert.fail("expected IllegalArgumentException for y=0")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `Chromaticity allows above-spectral-locus primaries (ACES AP1 red)`() {
        // ACES AP1 red is (0.713, 0.293) - x + y > 1, intentionally above the spectral locus.
        val c = ColorSpaceMatrix.Chromaticity(0.713f, 0.293f)
        assertEquals(0.713f, c.x, 0f)
        assertEquals(0.293f, c.y, 0f)
    }

    // ---------- Per-row sanity for the canonical primaries ----------

    @Test
    fun `whitepoint of every working space lands on its declared illuminant`() {
        val rgbOnes = floatArrayOf(1f, 1f, 1f)
        for ((label, primaries, m) in listOf(
            Triple("sRGB", ColorSpaceMatrix.SRGB_PRIMARIES, ColorSpaceMatrix.SRGB_TO_XYZ_D65),
            Triple("Rec2020", ColorSpaceMatrix.REC2020_PRIMARIES, ColorSpaceMatrix.REC2020_TO_XYZ_D65),
            Triple("DCI-P3", ColorSpaceMatrix.DCI_P3_PRIMARIES, ColorSpaceMatrix.DCI_P3_TO_XYZ_D65),
            Triple("ACES AP1", ColorSpaceMatrix.ACES_AP1_PRIMARIES, ColorSpaceMatrix.ACES_AP1_TO_XYZ_D60),
        )) {
            val xyz = ColorSpaceMatrix.multiplyVec(m, rgbOnes)
            val wp = ColorSpaceMatrix.whitepointXyz(primaries.whitepoint)
            assertEquals("$label X", wp[0], xyz[0], 1e-2f)
            assertEquals("$label Y", wp[1], xyz[1], 1e-3f)
            assertEquals("$label Z", wp[2], xyz[2], 1e-2f)
        }
    }

    @Test
    fun `schema version is pinned`() {
        assertEquals(1, ColorSpaceMatrix.SCHEMA_VERSION)
    }
}
