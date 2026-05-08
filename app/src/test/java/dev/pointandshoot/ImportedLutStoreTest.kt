package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pure-data tests for [ImportedLutStore]. The Context-bound methods are
 * exercised indirectly via the tempDir overloads of `pickAvailableFilename`
 * which take a `File` directly.
 */
class ImportedLutStoreTest {

    // ---------- sanitizeFilename ----------

    @Test
    fun `sanitizeFilename keeps allowed chars`() {
        assertEquals("MyLUT-3.cube", ImportedLutStore.sanitizeFilename("MyLUT-3.cube"))
        assertEquals("aces_srgb_to_acescct.cube", ImportedLutStore.sanitizeFilename("aces_srgb_to_acescct.cube"))
    }

    @Test
    fun `sanitizeFilename replaces disallowed chars with underscore`() {
        assertEquals("my_lut_v1.cube", ImportedLutStore.sanitizeFilename("my lut v1.cube"))
        assertEquals("evil_name.cube", ImportedLutStore.sanitizeFilename("evil/name.cube"))
        assertEquals("path_traversal.cube", ImportedLutStore.sanitizeFilename("path\\traversal.cube"))
    }

    @Test
    fun `sanitizeFilename collapses runs of underscores`() {
        assertEquals("a_b_c.cube", ImportedLutStore.sanitizeFilename("a   b!!!c.cube"))
    }

    @Test
    fun `sanitizeFilename strips leading dots and underscores`() {
        assertEquals("hidden.cube", ImportedLutStore.sanitizeFilename(".hidden.cube"))
        assertEquals("hidden.cube", ImportedLutStore.sanitizeFilename("..hidden.cube"))
        assertEquals("name.cube", ImportedLutStore.sanitizeFilename("___name.cube"))
    }

    @Test
    fun `sanitizeFilename caps to 96 characters`() {
        val raw = "x".repeat(200) + ".cube"
        val out = ImportedLutStore.sanitizeFilename(raw)
        assertTrue("expected <= 96 chars, got ${out.length}", out.length <= 96)
    }

    @Test
    fun `sanitizeFilename empty input falls back to imported_lut`() {
        assertEquals("imported_lut", ImportedLutStore.sanitizeFilename(""))
        assertEquals("imported_lut", ImportedLutStore.sanitizeFilename("..."))
    }

    // ---------- pickAvailableFilename ----------

    @Test
    fun `pickAvailableFilename returns base when no collision`() {
        val tmp = Files.createTempDirectory("pns-imp-").toFile()
        try {
            val picked = ImportedLutStore.pickAvailableFilename(tmp, "fresh")
            assertEquals(File(tmp, "fresh.cube"), picked)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `pickAvailableFilename increments suffix on collision`() {
        val tmp = Files.createTempDirectory("pns-imp-").toFile()
        try {
            File(tmp, "dup.cube").writeText("x")
            assertEquals(File(tmp, "dup_2.cube"), ImportedLutStore.pickAvailableFilename(tmp, "dup"))
            File(tmp, "dup_2.cube").writeText("x")
            File(tmp, "dup_3.cube").writeText("x")
            assertEquals(File(tmp, "dup_4.cube"), ImportedLutStore.pickAvailableFilename(tmp, "dup"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    // ---------- sha256 ----------

    @Test
    fun `sha256 is 64 hex chars`() {
        val h = ImportedLutStore.sha256("hello".toByteArray(Charsets.UTF_8))
        assertEquals(64, h.length)
        assertTrue(h.matches(Regex("^[0-9a-f]{64}\$")))
    }

    @Test
    fun `sha256 differs for different inputs and is stable`() {
        val a = ImportedLutStore.sha256(byteArrayOf(0))
        val b = ImportedLutStore.sha256(byteArrayOf(1))
        val a2 = ImportedLutStore.sha256(byteArrayOf(0))
        assertNotEquals(a, b)
        assertEquals(a, a2)
    }
}
