package dev.pointandshoot

import dev.pointandshoot.LutExternalOesShaderProgram.Source
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** JVM contract tests for external-OES + LUT preview shaders (no EGL). */
class LutExternalOesShaderSourceTest {

    @Test
    fun `fragment shader declares external OES extension and samplerExternalOES`() {
        val src = assetFile(Source.FRAGMENT_ASSET_PATH).readText()
        assertTrue(src.contains("#extension GL_OES_EGL_image_external_essl3 : require"))
        assertTrue(src.contains("samplerExternalOES"))
        assertTrue(src.contains("sampler3D"))
    }

    @Test
    fun `every Kotlin-bound symbol appears in the combined shader sources`() {
        val combined =
            listOf(Source.VERTEX_ASSET_PATH, Source.FRAGMENT_ASSET_PATH)
                .joinToString("\n") { assetFile(it).readText() }
        for (symbol in Source.requiredSymbols()) {
            assertTrue(
                "shader sources must declare '$symbol'",
                combined.contains(symbol),
            )
        }
    }

    private fun assetFile(relative: String): File {
        val candidates =
            listOf(
                File("src/main/assets/$relative"),
                File("app/src/main/assets/$relative"),
            )
        return candidates.firstOrNull { it.isFile }
            ?: error("asset not found in any of: ${candidates.map { it.absolutePath }}")
    }
}
