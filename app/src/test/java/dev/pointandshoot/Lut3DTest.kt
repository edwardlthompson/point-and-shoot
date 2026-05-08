package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Lut3DTest {

    @Test
    fun `identity LUT returns true for isIdentity at every supported size`() {
        for (size in Lut3D.SUPPORTED_SIZES) {
            val lut = Lut3D.identity(size)
            assertTrue("size=$size should be identity", lut.isIdentity())
        }
    }

    @Test
    fun `identity LUT samples match normalized grid coordinates`() {
        val lut = Lut3D.identity(17)
        val denom = 16f
        for (b in 0 until 17) {
            for (g in 0 until 17) {
                for (r in 0 until 17) {
                    val idx = ((b * 17 + g) * 17 + r) * 3
                    assertEquals("r at ($r,$g,$b)", r / denom, lut.samples[idx], 1e-6f)
                    assertEquals("g at ($r,$g,$b)", g / denom, lut.samples[idx + 1], 1e-6f)
                    assertEquals("b at ($r,$g,$b)", b / denom, lut.samples[idx + 2], 1e-6f)
                }
            }
        }
    }

    @Test
    fun `unsupported size in constructor throws`() {
        val ex = runCatching { Lut3D(size = 32, samples = FloatArray(32 * 32 * 32 * 3)) }
            .exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `samples length mismatch throws`() {
        val ex = runCatching { Lut3D(size = 17, samples = FloatArray(10)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `unsupported size in identity factory throws`() {
        val ex = runCatching { Lut3D.identity(size = 4) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `non-identity LUT returns false for isIdentity`() {
        val lut = Lut3D.identity(17)
        // Bump the very first sample's red channel; everything else still on grid.
        lut.samples[0] = 0.5f
        assertFalse(lut.isIdentity())
    }

    @Test
    fun `tiny perturbations within tolerance still report identity`() {
        val lut = Lut3D.identity(17)
        // Within DEFAULT_IDENTITY_TOLERANCE so isIdentity should still be true.
        lut.samples[0] += 5e-7f
        assertTrue(lut.isIdentity())
        assertNotEquals(0f, lut.samples[0])
    }

    @Test
    fun `identity tolerance can be tightened`() {
        val lut = Lut3D.identity(17)
        lut.samples[0] += 5e-7f
        assertFalse(lut.isIdentity(tolerance = 1e-9f))
    }
}
