package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LutSidecarTest {

    private val SHA64_A = "0".repeat(64)
    private val SHA64_B = "abcdef0123456789".repeat(4)

    private fun bundled(): LutSidecar.BundledRef = LutSidecar.BundledRef(
        captureFilename = "pns_20260508T2359Z_StandardPro_001.dng",
        captureKind = LutSidecar.CaptureKind.Still,
        capturedAtUtc = "2026-05-08T23:59:00Z",
        cataloguedAs = "PnsCinematic",
        lutSize = 33,
        spdx = "Apache-2.0",
        source = "Point & Shoot original (BT.709 luma + smoothstep, public-domain math)",
        sha256 = SHA64_A,
    )

    private fun cube(title: String? = "Test Cube"): LutSidecar.CubeFileRef = LutSidecar.CubeFileRef(
        captureFilename = "pns_20260508T2359Z_StandardPro_002.avif",
        captureKind = LutSidecar.CaptureKind.Still,
        capturedAtUtc = "2026-05-08T23:59:01Z",
        cubeRelativePath = "luts/imported/my-grade.cube",
        lutSize = 33,
        title = title,
        sha256 = SHA64_B,
    )

    // ---------- bundled round-trip ----------

    @Test
    fun `bundled encode then decode round-trips`() {
        val ref = bundled()
        val text = LutSidecar.encode(ref)
        val parsed = LutSidecar.decode(text)
        assertTrue(parsed is LutSidecar.ParseResult.Bundled)
        assertEquals(ref, (parsed as LutSidecar.ParseResult.Bundled).ref)
    }

    @Test
    fun `bundled encoding contains documented keys`() {
        val text = LutSidecar.encode(bundled())
        for (key in listOf("captureFilename", "captureKind", "capturedAtUtc",
            "kind = bundled", "cataloguedAs", "lutSize", "spdx", "source", "sha256")) {
            assertTrue("expected '$key' in:\n$text", text.contains(key))
        }
    }

    @Test
    fun `bundled encoding starts with the magic header and schema version`() {
        val text = LutSidecar.encode(bundled())
        assertTrue("expected magic header in:\n$text",
            text.lines().first().startsWith("# pns-lut-sidecar v${LutSidecar.SCHEMA_VERSION}"))
    }

    @Test
    fun `bundledRefFor pulls metadata from the LutCatalog entry`() {
        val ref = LutSidecar.bundledRefFor(
            catalog = LutCatalog.PnsCinematic,
            captureFilename = "x.dng",
            captureKind = LutSidecar.CaptureKind.Still,
            capturedAtUtc = "2026-05-08T00:00:00Z",
            lutSize = 33,
            sha256 = SHA64_A,
        )
        assertEquals(LutCatalog.PnsCinematic.name, ref.cataloguedAs)
        assertEquals(LutCatalog.PnsCinematic.spdx, ref.spdx)
        assertEquals(LutCatalog.PnsCinematic.source, ref.source)
    }

    // ---------- cube round-trip ----------

    @Test
    fun `cube encode then decode round-trips`() {
        val ref = cube()
        val text = LutSidecar.encode(ref)
        val parsed = LutSidecar.decode(text)
        assertTrue(parsed is LutSidecar.ParseResult.Cube)
        assertEquals(ref, (parsed as LutSidecar.ParseResult.Cube).ref)
    }

    @Test
    fun `cube encoding without a title omits the title line`() {
        val ref = cube(title = null)
        val text = LutSidecar.encode(ref)
        assertTrue("expected no 'title =' line in:\n$text", !text.contains("title ="))
        val parsed = LutSidecar.decode(text)
        assertTrue(parsed is LutSidecar.ParseResult.Cube)
        assertNull((parsed as LutSidecar.ParseResult.Cube).ref.title)
    }

    // ---------- siblingFilenameFor ----------

    @Test
    fun `siblingFilenameFor uses the correct extension per flavor`() {
        assertEquals("foo.dng.lutref.txt",
            LutSidecar.siblingFilenameFor("foo.dng", isBundled = true))
        assertEquals("foo.dng.cube.txt",
            LutSidecar.siblingFilenameFor("foo.dng", isBundled = false))
    }

    // ---------- decode failures ----------

    @Test
    fun `decode rejects text without the magic header`() {
        val text = "captureFilename = x.dng\ncaptureKind = Still\ncapturedAtUtc = z\nkind = bundled\n"
        val ex = runCatching { LutSidecar.decode(text) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("magic"))
    }

    @Test
    fun `decode rejects unknown schema version`() {
        val text = "# pns-lut-sidecar v99\ncaptureFilename = x\ncaptureKind = Still\ncapturedAtUtc = z\nkind = bundled\ncataloguedAs = X\nlutSize = 33\nspdx = MIT\nsource = z\nsha256 = ${SHA64_A}\n"
        val ex = runCatching { LutSidecar.decode(text) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("schema version"))
    }

    @Test
    fun `decode rejects malformed key value lines`() {
        val text = "# pns-lut-sidecar v1\nthis is not a key value\n"
        val ex = runCatching { LutSidecar.decode(text) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue("expected key=value error: ${ex!!.message}", ex.message!!.contains("key"))
    }

    @Test
    fun `decode rejects unknown kind`() {
        val text = "# pns-lut-sidecar v1\ncaptureFilename = x\ncaptureKind = Still\ncapturedAtUtc = z\nkind = bogus\n"
        val ex = runCatching { LutSidecar.decode(text) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("kind"))
    }

    @Test
    fun `decode rejects missing required bundled fields`() {
        val text = "# pns-lut-sidecar v1\ncaptureFilename = x\ncaptureKind = Still\ncapturedAtUtc = z\nkind = bundled\nlutSize = 33\n"
        val ex = runCatching { LutSidecar.decode(text) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue("expected missing-field error: ${ex!!.message}",
            ex.message!!.contains("cataloguedAs") || ex.message!!.contains("spdx"))
    }

    @Test
    fun `decode tolerates blank lines and inline comments`() {
        val text = """
            |# pns-lut-sidecar v1
            |# this is a comment
            |
            |captureFilename = x.dng
            |captureKind = Still
            |capturedAtUtc = 2026-05-08T00:00:00Z
            |
            |# another comment
            |kind = bundled
            |cataloguedAs = PnsCinematic
            |lutSize = 33
            |spdx = Apache-2.0
            |source = z
            |sha256 = $SHA64_A
        """.trimMargin()
        val parsed = LutSidecar.decode(text)
        assertTrue(parsed is LutSidecar.ParseResult.Bundled)
    }

    // ---------- constructor validation ----------

    @Test
    fun `BundledRef rejects malformed sha256`() {
        val ex = runCatching {
            LutSidecar.BundledRef(
                captureFilename = "x", captureKind = LutSidecar.CaptureKind.Still,
                capturedAtUtc = "z", cataloguedAs = "X", lutSize = 33,
                spdx = "MIT", source = "y", sha256 = "TOO-SHORT",
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `CubeFileRef rejects unsupported lutSize`() {
        val ex = runCatching {
            LutSidecar.CubeFileRef(
                captureFilename = "x", captureKind = LutSidecar.CaptureKind.Still,
                capturedAtUtc = "z", cubeRelativePath = "p", lutSize = 19,
                title = null, sha256 = SHA64_A,
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `BundledRef rejects blank cataloguedAs`() {
        val ex = runCatching {
            LutSidecar.BundledRef(
                captureFilename = "x", captureKind = LutSidecar.CaptureKind.Still,
                capturedAtUtc = "z", cataloguedAs = "", lutSize = 33,
                spdx = "MIT", source = "y", sha256 = SHA64_A,
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `Video captureKind round-trips`() {
        val ref = bundled().copy(captureKind = LutSidecar.CaptureKind.Video,
            captureFilename = "video_001.mp4")
        val text = LutSidecar.encode(ref)
        val parsed = LutSidecar.decode(text)
        assertTrue(parsed is LutSidecar.ParseResult.Bundled)
        assertEquals(LutSidecar.CaptureKind.Video,
            (parsed as LutSidecar.ParseResult.Bundled).ref.captureKind)
    }
}
