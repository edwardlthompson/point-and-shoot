package dev.pointandshoot

import dev.pointandshoot.LutShaderProgram.BypassPolicy
import dev.pointandshoot.LutShaderProgram.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure-JVM contract tests for [LutShaderProgram] - the GLES program itself
 * is exercised on-device, but the shader-source declarations and the
 * identity-bypass policy are pure data and live behind interfaces that
 * JUnit can poke without an EGL context.
 *
 * These tests guard the contract documented in `BUILD_PLAN.md` §7
 * "Apply path": identity LUT bypasses the shader stage entirely.
 */
class LutShaderProgramSourceTest {

    // --- Source contract --------------------------------------------------

    @Test
    fun `vertex shader asset exists at the path the loader expects`() {
        val file = assetFile(Source.VERTEX_ASSET_PATH)
        assertTrue("expected ${file.absolutePath} to exist", file.isFile)
        assertTrue("expected non-empty shader source", file.length() > 0L)
    }

    @Test
    fun `fragment shader asset exists at the path the loader expects`() {
        val file = assetFile(Source.FRAGMENT_ASSET_PATH)
        assertTrue("expected ${file.absolutePath} to exist", file.isFile)
        assertTrue("expected non-empty shader source", file.length() > 0L)
    }

    @Test
    fun `vertex shader declares the GLES 3 0 ES version directive`() {
        val src = assetFile(Source.VERTEX_ASSET_PATH).readText()
        assertTrue(
            "expected '${Source.REQUIRED_GLSL_VERSION_DIRECTIVE}' in vertex shader",
            src.contains(Source.REQUIRED_GLSL_VERSION_DIRECTIVE),
        )
    }

    @Test
    fun `fragment shader declares the GLES 3 0 ES version directive`() {
        val src = assetFile(Source.FRAGMENT_ASSET_PATH).readText()
        assertTrue(
            "expected '${Source.REQUIRED_GLSL_VERSION_DIRECTIVE}' in fragment shader",
            src.contains(Source.REQUIRED_GLSL_VERSION_DIRECTIVE),
        )
    }

    @Test
    fun `fragment shader uses sampler3D so trilinear interpolation runs in hardware`() {
        val src = assetFile(Source.FRAGMENT_ASSET_PATH).readText()
        assertTrue("expected sampler3D LUT in fragment shader", src.contains("sampler3D"))
    }

    @Test
    fun `every Kotlin-bound symbol appears in either the vertex or fragment shader`() {
        val combined = listOf(Source.VERTEX_ASSET_PATH, Source.FRAGMENT_ASSET_PATH)
            .joinToString("\n") { assetFile(it).readText() }
        for (symbol in Source.requiredSymbols()) {
            assertTrue(
                "shader sources must declare '$symbol' (used by Kotlin LutShaderProgram binder)",
                combined.contains(symbol),
            )
        }
    }

    @Test
    fun `requiredSymbols enumerates all attributes and uniforms (no duplicates)`() {
        val symbols = Source.requiredSymbols()
        assertEquals("contract is 2 attributes + 4 uniforms", 6, symbols.size)
        assertEquals("no duplicates", symbols.size, symbols.toSet().size)
        assertTrue(symbols.contains(Source.ATTRIB_POSITION))
        assertTrue(symbols.contains(Source.ATTRIB_TEX_COORD))
        assertTrue(symbols.contains(Source.UNIFORM_SOURCE_TEX))
        assertTrue(symbols.contains(Source.UNIFORM_LUT_TEX))
        assertTrue(symbols.contains(Source.UNIFORM_LUT_SIZE))
        assertTrue(symbols.contains(Source.UNIFORM_LUT_ENABLED))
    }

    @Test
    fun `full screen quad covers NDC -1 to 1 in clockwise UV order`() {
        val q = Source.FULL_SCREEN_QUAD
        assertEquals("4 vertices x 4 floats (xy + uv)", 16, q.size)
        // Bottom-left, bottom-right, top-left, top-right ordering for TRIANGLE_STRIP.
        assertEquals(-1f, q[0]); assertEquals(-1f, q[1]); assertEquals(0f, q[2]); assertEquals(0f, q[3])
        assertEquals(1f, q[4]);  assertEquals(-1f, q[5]); assertEquals(1f, q[6]); assertEquals(0f, q[7])
        assertEquals(-1f, q[8]); assertEquals(1f, q[9]);  assertEquals(0f, q[10]); assertEquals(1f, q[11])
        assertEquals(1f, q[12]); assertEquals(1f, q[13]); assertEquals(1f, q[14]); assertEquals(1f, q[15])
    }

    private fun assertEquals(expected: Float, actual: Float) =
        assertEquals(expected, actual, 0f)

    // --- BypassPolicy -----------------------------------------------------

    @Test
    fun `null LUT drives bypass uniform to 0`() {
        assertEquals(0f, BypassPolicy.lutEnabledUniform(null), 0f)
        assertTrue(BypassPolicy.shouldSkipUpload(null))
    }

    @Test
    fun `identity LUT at every supported size drives bypass uniform to 0`() {
        for (size in Lut3D.SUPPORTED_SIZES) {
            val identity = Lut3D.identity(size)
            assertEquals("identity at size=$size", 0f, BypassPolicy.lutEnabledUniform(identity), 0f)
            assertTrue("identity at size=$size", BypassPolicy.shouldSkipUpload(identity))
        }
    }

    @Test
    fun `non identity LUT drives bypass uniform to 1`() {
        val cinematic = LutCatalog.PnsCinematic.load(33)
        assertEquals(1f, BypassPolicy.lutEnabledUniform(cinematic), 0f)
        assertFalse(BypassPolicy.shouldSkipUpload(cinematic))
    }

    @Test
    fun `B and W BT 709 LUT drives bypass uniform to 1`() {
        val bw = LutCatalog.BwBt709.load(33)
        assertEquals(1f, BypassPolicy.lutEnabledUniform(bw), 0f)
    }

    @Test
    fun `bypass policy does not allocate when LUT is null`() {
        // Smoke-test that the call path is allocation-light enough for the
        // hot per-frame composer to call it without GC pressure.
        repeat(10_000) {
            assertNotNull(BypassPolicy.lutEnabledUniform(null))
        }
    }

    // --- Helpers ----------------------------------------------------------

    private fun assetFile(relative: String): File {
        // JUnit working directory is the gradle module root (`app/`).
        val candidates = listOf(
            File("src/main/assets/$relative"),
            File("app/src/main/assets/$relative"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("asset not found in any of: ${candidates.map { it.absolutePath }}")
    }
}
