package dev.pointandshoot

import dev.pointandshoot.CalibrationProfile.Bias
import dev.pointandshoot.CalibrationProfile.Ccm
import dev.pointandshoot.CalibrationProfile.Illuminant
import dev.pointandshoot.CalibrationProfile.WbGains
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Pure-JVM tests for [DngColorTags] - the host-side math that converts a
 * [CalibrationProfile] into the three DNG color tags
 * (`AsShotNeutral` / `ColorMatrix1` / `ForwardMatrix1`).
 *
 * Critical math invariants:
 *   * AsShotNeutral max is exactly 1.0f.
 *   * Identity profile produces identity-equivalent tags (sanity).
 *   * Round-trip via the inverse contracts: applying ColorMatrix1 then
 *     undoing the chain via ForwardMatrix1 returns the original XYZ
 *     within float epsilon.
 *   * EXIF light-source codes match the DNG 1.7 spec table.
 */
class DngColorTagsTest {

    // -------- AsShotNeutral ------------------------------------------------

    @Test
    fun `AsShotNeutral for identity gains is 1 1 1`() {
        val n = DngColorTags.asShotNeutral(WbGains.Identity)
        assertVec(floatArrayOf(1f, 1f, 1f), n)
    }

    @Test
    fun `AsShotNeutral max is exactly 1 0`() {
        val warm = WbGains(r = 1.5f, g = 1f, b = 0.7f)
        val n = DngColorTags.asShotNeutral(warm)
        val mx = maxOf(n[0], n[1], n[2])
        assertEquals(1.0f, mx, 1e-6f)
    }

    @Test
    fun `AsShotNeutral inverts the gains`() {
        // Anchored on G (= 1). For warm-cast (r > 1), R must come out the
        // smallest because the camera saw less R for a neutral patch.
        val warm = WbGains(r = 1.6f, g = 1f, b = 0.8f)
        val n = DngColorTags.asShotNeutral(warm)
        // 1/r = 0.625, 1/g = 1, 1/b = 1.25 -> normalize by max (1.25)
        // expected = (0.5, 0.8, 1.0)
        assertVec(floatArrayOf(0.5f, 0.8f, 1.0f), n)
    }

    // -------- ColorMatrix1 / ForwardMatrix1 inverse round-trip -----------

    @Test
    fun `identity profile yields ColorMatrix1 equal to inverse of sRGB to XYZ D65`() {
        val identity = CalibrationProfile.identity(cameraId = "0", targetId = "Generic24")
        val cm1 = DngColorTags.colorMatrix1(identity)
        // ColorMatrix1 = inverse(sRGBtoXYZ_D65 * I * I) = inverse(sRGBtoXYZ_D65).
        // Round-trip via the matrix multiplication: cm1 * sRGBtoXYZ_D65 == I.
        val product = mulFlat3(cm1, SRGB_TO_XYZ_D65)
        assertMat(IDENTITY_3X3, product, tol = 1e-4f)
    }

    @Test
    fun `identity profile ForwardMatrix1 matches Bradford times sRGB to XYZ D65`() {
        val identity = CalibrationProfile.identity(cameraId = "0", targetId = "Generic24")
        val fm1 = DngColorTags.forwardMatrix1(identity)
        val expected = mulFlat3(BRADFORD_D65_TO_D50, SRGB_TO_XYZ_D65)
        assertMat(expected, fm1, tol = 1e-5f)
    }

    @Test
    fun `Identity profile bundle has correct values`() {
        val identity = CalibrationProfile.identity(cameraId = "0", targetId = "Generic24")
        val bundle = DngColorTags.forProfile(identity)
        assertEquals(21, bundle.calibrationIlluminant1) // D65
        assertEquals(3, bundle.asShotNeutral.size)
        assertEquals(9, bundle.colorMatrix1.size)
        assertEquals(9, bundle.forwardMatrix1.size)
        assertVec(floatArrayOf(1f, 1f, 1f), bundle.asShotNeutral)
    }

    @Test
    fun `non identity CCM produces a non identity ColorMatrix1`() {
        val swap = Ccm(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f) // R<->G swap
        val profile = CalibrationProfile(
            wbGains = WbGains.Identity,
            ccm = swap,
            bias = Bias.Zero,
            illuminant = Illuminant.D65,
            capturedAtMs = 0L,
            cameraId = "0",
            targetId = "Generic24",
        )
        val cm1 = DngColorTags.colorMatrix1(profile)
        // Should NOT be the same as the identity-profile version.
        val identityCm1 = DngColorTags.colorMatrix1(
            CalibrationProfile.identity(cameraId = "0", targetId = "Generic24"),
        )
        var anyDiffers = false
        for (i in 0 until 9) {
            if (abs(cm1[i] - identityCm1[i]) > 1e-4f) { anyDiffers = true; break }
        }
        assertTrue("expected non-identity ColorMatrix1 to differ from identity", anyDiffers)
    }

    @Test
    fun `WB gains shift AsShotNeutral but do not affect ForwardMatrix1`() {
        // ForwardMatrix1 is `Bradford * sRGBtoXYZ * CCM` - WB gains never
        // enter that product (they're applied to the camera RGB before
        // ForwardMatrix1 multiplies them, per DNG spec).
        val a = CalibrationProfile.identity(cameraId = "0", targetId = "G24")
        val b = a.copy(wbGains = WbGains(r = 1.4f, g = 1f, b = 0.7f))
        val fmA = DngColorTags.forwardMatrix1(a)
        val fmB = DngColorTags.forwardMatrix1(b)
        assertMat(fmA, fmB, tol = 1e-6f)

        val nA = DngColorTags.asShotNeutral(a.wbGains)
        val nB = DngColorTags.asShotNeutral(b.wbGains)
        assertNotEquals("expected AsShotNeutral to change with WB gains", nA.toList(), nB.toList())
    }

    // -------- CalibrationIlluminant code ---------------------------------

    @Test
    fun `EXIF light source codes match DNG 1 7 spec`() {
        assertEquals(23, DngColorTags.calibrationIlluminantCode(Illuminant.D50))
        assertEquals(20, DngColorTags.calibrationIlluminantCode(Illuminant.D55))
        assertEquals(21, DngColorTags.calibrationIlluminantCode(Illuminant.D65))
        assertEquals(17, DngColorTags.calibrationIlluminantCode(Illuminant.StdA))
        assertEquals(14, DngColorTags.calibrationIlluminantCode(Illuminant.F2))
    }

    @Test
    fun `forProfile picks the right illuminant code for each enum value`() {
        for (illum in Illuminant.values()) {
            val profile = CalibrationProfile.identity(
                cameraId = "0", targetId = "G24", illuminant = illum,
            )
            val expected = DngColorTags.calibrationIlluminantCode(illum)
            assertEquals(expected, DngColorTags.forProfile(profile).calibrationIlluminant1)
        }
    }

    // -------- DngColor data class equality (FloatArray-aware) ------------

    @Test
    fun `DngColor equals compares FloatArray contents not references`() {
        val identity = CalibrationProfile.identity(cameraId = "0", targetId = "G24")
        val a = DngColorTags.forProfile(identity)
        val b = DngColorTags.forProfile(identity)
        // Different array instances but byte-equal contents.
        assertTrue("DngColor.equals must compare contents", a == b)
        assertTrue("DngColor.hashCode must be content-derived", a.hashCode() == b.hashCode())
    }

    @Test
    fun `DngColor init rejects invalid AsShotNeutral max`() {
        try {
            DngColorTags.DngColor(
                asShotNeutral = floatArrayOf(0.5f, 0.5f, 0.5f),
                colorMatrix1 = FloatArray(9),
                forwardMatrix1 = FloatArray(9),
                calibrationIlluminant1 = 21,
            )
            fail("expected IllegalArgumentException for AsShotNeutral max != 1.0")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("asShotNeutral"))
        }
    }

    // -------- Helpers ----------------------------------------------------

    private fun assertVec(expected: FloatArray, actual: FloatArray, tol: Float = 1e-5f) {
        assertEquals("vector length", expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("vec[$i]", expected[i], actual[i], tol)
        }
    }

    private fun assertMat(expected: FloatArray, actual: FloatArray, tol: Float) {
        assertEquals("matrix length", expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("matrix[$i]", expected[i], actual[i], tol)
        }
    }

    private fun mulFlat3(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(9)
        for (i in 0..2) for (j in 0..2) {
            var s = 0f
            for (k in 0..2) s += a[i * 3 + k] * b[k * 3 + j]
            out[i * 3 + j] = s
        }
        return out
    }

    private val IDENTITY_3X3 = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    private val SRGB_TO_XYZ_D65: FloatArray = floatArrayOf(
        0.4124564f, 0.3575761f, 0.1804375f,
        0.2126729f, 0.7151522f, 0.0721750f,
        0.0193339f, 0.1191920f, 0.9503041f,
    )

    private val BRADFORD_D65_TO_D50: FloatArray = floatArrayOf(
        1.0478112f, 0.0228866f, -0.0501270f,
        0.0295424f, 0.9904844f, -0.0170491f,
        -0.0092345f, 0.0150436f, 0.7521316f,
    )
}
