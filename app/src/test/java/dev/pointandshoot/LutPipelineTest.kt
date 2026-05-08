package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LutPipelineTest {

    // ---------- applyTrilinear ----------

    @Test
    fun `identity LUT preserves arbitrary samples within float precision`() {
        val lut = Lut3D.identity(33)
        val samples = listOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1f, 1f, 1f),
            floatArrayOf(0.5f, 0.5f, 0.5f),
            floatArrayOf(0.123f, 0.456f, 0.789f),
            floatArrayOf(0.95f, 0.05f, 0.5f),
        )
        for (rgb in samples) {
            val out = LutPipeline.applyTrilinear(rgb, lut)
            assertEquals("r ${rgb.toList()}", rgb[0], out[0], 1e-5f)
            assertEquals("g ${rgb.toList()}", rgb[1], out[1], 1e-5f)
            assertEquals("b ${rgb.toList()}", rgb[2], out[2], 1e-5f)
        }
    }

    @Test
    fun `inputs are clamped before lookup so out-of-gamut does not throw`() {
        val lut = Lut3D.identity(17)
        val out = LutPipeline.applyTrilinear(floatArrayOf(1.5f, -0.2f, 2f), lut)
        // After clamp, identity returns (1, 0, 1).
        assertEquals(1f, out[0], 1e-5f)
        assertEquals(0f, out[1], 1e-5f)
        assertEquals(1f, out[2], 1e-5f)
    }

    @Test
    fun `NaN input throws explicitly rather than corrupting the cube index`() {
        val lut = Lut3D.identity(17)
        val ex = runCatching { LutPipeline.applyTrilinear(floatArrayOf(Float.NaN, 0f, 0f), lut) }
            .exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `swap-channels LUT swaps r and b on identity input`() {
        // Build a LUT that swaps R and B at every grid point.
        val size = 17
        val s = FloatArray(size * size * size * 3)
        val denom = (size - 1).toFloat()
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val idx = ((b * size + g) * size + r) * 3
                    s[idx] = b / denom
                    s[idx + 1] = g / denom
                    s[idx + 2] = r / denom
                }
            }
        }
        val swap = Lut3D(size, s)
        val out = LutPipeline.applyTrilinear(floatArrayOf(0.25f, 0.5f, 0.75f), swap)
        assertEquals(0.75f, out[0], 1e-4f)
        assertEquals(0.5f, out[1], 1e-4f)
        assertEquals(0.25f, out[2], 1e-4f)
    }

    @Test
    fun `applyTrilinearInto writes at the requested offset without allocating`() {
        val lut = Lut3D.identity(17)
        val buf = FloatArray(9) { -1f }
        LutPipeline.applyTrilinearInto(0.5f, 0.25f, 0.75f, lut, buf, offset = 3)
        // Untouched entries remain sentinel.
        for (i in 0..2) assertEquals(-1f, buf[i], 0f)
        for (i in 6..8) assertEquals(-1f, buf[i], 0f)
        // Slots 3..5 hold the (r, g, b) sample.
        assertEquals(0.5f, buf[3], 1e-5f)
        assertEquals(0.25f, buf[4], 1e-5f)
        assertEquals(0.75f, buf[5], 1e-5f)
    }

    // ---------- parseCube + serializeCube ----------

    @Test
    fun `parseCube round-trips a small hand-written file`() {
        val cube = """
            # tiny test LUT
            TITLE "tiny"
            LUT_3D_SIZE 17
            DOMAIN_MIN 0.0 0.0 0.0
            DOMAIN_MAX 1.0 1.0 1.0
        """.trimIndent() + "\n" + identityBody(size = 17)
        val parsed = LutPipeline.parseCube(cube)
        assertEquals(17, parsed.size)
        assertTrue(parsed.isIdentity())
    }

    @Test
    fun `serializeCube round-trips through parseCube identically`() {
        val original = Lut3D.identity(17)
        val text = LutPipeline.serializeCube(original, title = "round-trip 17")
        val parsed = LutPipeline.parseCube(text)
        assertEquals(original.size, parsed.size)
        for (i in original.samples.indices) {
            assertEquals("sample $i", original.samples[i], parsed.samples[i], 1e-5f)
        }
    }

    @Test
    fun `parseCube rejects a non-default DOMAIN_MAX`() {
        val cube = """
            LUT_3D_SIZE 17
            DOMAIN_MAX 4.0 4.0 4.0
        """.trimIndent() + "\n" + identityBody(size = 17)
        val ex = runCatching { LutPipeline.parseCube(cube) }.exceptionOrNull()
        assertTrue("expected reject for non-[0,1] domain (was $ex)", ex is IllegalArgumentException)
    }

    @Test
    fun `parseCube rejects a 1D LUT`() {
        val ex = runCatching { LutPipeline.parseCube("LUT_1D_SIZE 64\n") }.exceptionOrNull()
        assertTrue(ex is IllegalStateException || ex is IllegalArgumentException)
    }

    @Test
    fun `parseCube rejects an unsupported size`() {
        val cube = "LUT_3D_SIZE 8\n" + identityBody(size = 8)
        val ex = runCatching { LutPipeline.parseCube(cube) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `parseCube rejects a body with the wrong sample count`() {
        val cube = "LUT_3D_SIZE 17\n0.0 0.0 0.0\n1.0 1.0 1.0\n"
        val ex = runCatching { LutPipeline.parseCube(cube) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `serializeCube escapes embedded quotes in title`() {
        val text = LutPipeline.serializeCube(Lut3D.identity(17), title = "say \"cheese\"")
        // Outer quotes must remain a single TITLE pair; inner quotes flipped to apostrophes.
        assertTrue(text.lineSequence().first().startsWith("TITLE \""))
        assertTrue(text.lineSequence().first().endsWith("\""))
        assertTrue(text.contains("say 'cheese'"))
    }

    // ---------- helpers ----------

    private fun identityBody(size: Int): String {
        val sb = StringBuilder()
        val denom = (size - 1).toFloat()
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    sb.append("%.6f %.6f %.6f%n".format(r / denom, g / denom, b / denom))
                }
            }
        }
        return sb.toString()
    }
}
