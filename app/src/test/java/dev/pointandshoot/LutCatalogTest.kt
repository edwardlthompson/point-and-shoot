package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `None has no DNG LUT identity and non-None entries do`() {
        assertNull(LutCatalog.None.identityForDngMetadata())
        val id = LutCatalog.PnsCinematic.identityForDngMetadata()
        assertNotNull(id)
        assertEquals("PnsCinematic", id!!.catalogId)
        assertEquals(64, id.sha256.length)
        assertTrue(id.sha256.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `sha256Hex is stable 64-char lowercase hex for None and cinematic`() {
        val a = LutCatalog.None.sha256Hex()
        val b = LutCatalog.None.sha256Hex()
        assertEquals(a, b)
        assertEquals(64, a.length)
        assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
        val c = LutCatalog.PnsCinematic.sha256Hex()
        assertEquals(64, c.length)
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
    fun `indexInScope matches forScope list order`() {
        val stills = LutCatalog.forScope(LutCatalog.Scope.Stills)
        for (i in stills.indices) {
            assertEquals(i, stills[i].indexInScope(LutCatalog.Scope.Stills))
        }
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
