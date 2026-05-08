package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LutCatalogTest {

    @Test
    fun `every catalog entry has an SPDX in the allowed set`() {
        for (entry in LutCatalog.entries) {
            assertTrue(
                "${entry.name} has SPDX '${entry.spdx}' which is not in ALLOWED_SPDX",
                entry.spdx in LutCatalog.ALLOWED_SPDX,
            )
        }
    }

    @Test
    fun `every catalog entry loads successfully at the default size`() {
        for (entry in LutCatalog.entries) {
            val lut = entry.load()
            assertNotNull("${entry.name} loaded null", lut)
            assertEquals("${entry.name} default size", BuiltInLuts.DEFAULT_SIZE, lut.size)
        }
    }

    @Test
    fun `every catalog entry loads at every supported grid size`() {
        for (entry in LutCatalog.entries) {
            for (size in Lut3D.SUPPORTED_SIZES) {
                val lut = entry.load(size)
                assertEquals("${entry.name} at size=$size", size, lut.size)
            }
        }
    }

    @Test
    fun `None entry resolves to a true identity LUT`() {
        val lut = LutCatalog.None.load()
        assertTrue(lut.isIdentity())
    }

    @Test
    fun `forScope returns entries marked for that scope plus Both`() {
        val stills = LutCatalog.forScope(LutCatalog.Scope.Stills)
        val video = LutCatalog.forScope(LutCatalog.Scope.Video)
        // Every entry currently is Both, so both lists should contain everything.
        assertEquals(LutCatalog.entries.size, stills.size)
        assertEquals(LutCatalog.entries.size, video.size)
        assertTrue(stills.contains(LutCatalog.None))
        assertTrue(video.contains(LutCatalog.None))
    }

    @Test
    fun `defaultFor any scope is None (safe fallback)`() {
        for (scope in LutCatalog.Scope.entries) {
            assertEquals(LutCatalog.None, LutCatalog.defaultFor(scope))
        }
    }

    @Test
    fun `displayName description and source are non-empty for every entry`() {
        for (entry in LutCatalog.entries) {
            assertTrue("${entry.name} displayName", entry.displayName.isNotBlank())
            assertTrue("${entry.name} description", entry.description.isNotBlank())
            assertTrue("${entry.name} source", entry.source.isNotBlank())
        }
    }

    @Test
    fun `BW LUTs collapse to gray when loaded from the catalog`() {
        for (lutId in listOf(LutCatalog.BwBt601, LutCatalog.BwBt709)) {
            val lut = lutId.load()
            val out = LutPipeline.applyTrilinear(floatArrayOf(0.5f, 0.7f, 0.3f), lut)
            assertEquals("${lutId.name} R==G", out[0], out[1], 1e-3f)
            assertEquals("${lutId.name} G==B", out[1], out[2], 1e-3f)
        }
    }
}
