package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pure-JVM tests for [LutSidecarWriter]'s file-IO surface (atomic rename,
 * sibling resolution, SHA-256 helpers). The pure-data
 * encode/decode/validation logic is covered separately by
 * [LutSidecarTest].
 */
class LutSidecarWriterTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    // ---- siblingFor (pure path math) -----------------------------------

    @Test
    fun `siblingFor bundled appends lutref txt and keeps the parent dir`() {
        val captureDir = tempFolder.newFolder("captures")
        val capture = File(captureDir, "pns_20260508_010101_StandardPro_001.dng")
        val sidecar = LutSidecarWriter.siblingFor(capture, isBundled = true)
        assertEquals(captureDir, sidecar.parentFile)
        assertEquals("pns_20260508_010101_StandardPro_001.dng.lutref.txt", sidecar.name)
    }

    @Test
    fun `siblingFor cube appends cube txt`() {
        val captureDir = tempFolder.newFolder("captures")
        val capture = File(captureDir, "still.avif")
        val sidecar = LutSidecarWriter.siblingFor(capture, isBundled = false)
        assertEquals("still.avif.cube.txt", sidecar.name)
    }

    // ---- writeBundled / writeCube ---------------------------------------

    @Test
    fun `writeBundled produces a sidecar that round-trips through decode`() {
        val captureDir = tempFolder.newFolder("captures")
        val capture = File(captureDir, "pns_video_001.mp4")
        val ref = LutSidecar.bundledRefFor(
            catalog = LutCatalog.PnsCinematic,
            captureFilename = capture.name,
            captureKind = LutSidecar.CaptureKind.Video,
            capturedAtUtc = "20260508T120000Z",
            lutSize = 33,
            sha256 = "0".repeat(64),
        )

        val sidecar = LutSidecarWriter.writeBundled(capture, ref)

        assertTrue(sidecar.isFile)
        val text = sidecar.readText()
        val parsed = LutSidecar.decode(text)
        assertTrue(parsed is LutSidecar.ParseResult.Bundled)
        val recovered = (parsed as LutSidecar.ParseResult.Bundled).ref
        assertEquals(ref, recovered)
    }

    @Test
    fun `writeCube produces a sidecar that round-trips through decode`() {
        val captureDir = tempFolder.newFolder("captures")
        val capture = File(captureDir, "pns_still_002.jpg")
        val ref = LutSidecar.CubeFileRef(
            captureFilename = capture.name,
            captureKind = LutSidecar.CaptureKind.Still,
            capturedAtUtc = "20260508T120000Z",
            cubeRelativePath = "luts/imported/sunset.cube",
            lutSize = 33,
            title = "Sunset (user-imported)",
            sha256 = "f".repeat(64),
        )

        val sidecar = LutSidecarWriter.writeCube(capture, ref)

        assertTrue(sidecar.isFile)
        val parsed = LutSidecar.decode(sidecar.readText())
        assertTrue(parsed is LutSidecar.ParseResult.Cube)
        assertEquals(ref, (parsed as LutSidecar.ParseResult.Cube).ref)
    }

    @Test
    fun `writeBundled rejects a ref whose captureFilename does not match the file name`() {
        val capture = File(tempFolder.newFolder("captures"), "actual.dng")
        val ref = LutSidecar.bundledRefFor(
            catalog = LutCatalog.BwBt709,
            captureFilename = "wrong.dng",
            captureKind = LutSidecar.CaptureKind.Still,
            capturedAtUtc = "20260508T120000Z",
            lutSize = 33,
            sha256 = "1".repeat(64),
        )
        try {
            LutSidecarWriter.writeBundled(capture, ref)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("captureFilename"))
        }
    }

    @Test
    fun `writeBundled overwrites an existing sidecar atomically`() {
        val capture = File(tempFolder.newFolder("captures"), "pns_001.dng")
        val first = LutSidecar.bundledRefFor(
            catalog = LutCatalog.BwBt601,
            captureFilename = capture.name,
            captureKind = LutSidecar.CaptureKind.Still,
            capturedAtUtc = "20260508T120000Z",
            lutSize = 33,
            sha256 = "1".repeat(64),
        )
        val second = first.copy(sha256 = "2".repeat(64))

        val sidecar = LutSidecarWriter.writeBundled(capture, first)
        val firstSize = sidecar.length()
        LutSidecarWriter.writeBundled(capture, second)
        assertTrue(sidecar.isFile)
        val recovered = (LutSidecar.decode(sidecar.readText()) as LutSidecar.ParseResult.Bundled).ref
        assertEquals(second, recovered)
        // No leaked temp files in the parent dir.
        val leaked = sidecar.parentFile!!.listFiles { f ->
            f.name.contains(LutSidecarWriter.TEMP_SUFFIX)
        }!!.toList()
        assertTrue("expected no leftover temp files: $leaked", leaked.isEmpty())
        // First-write byte size is still meaningful (avoids unused warning).
        assertNotNull(firstSize)
    }

    @Test
    fun `writeBundled creates parent directories as needed`() {
        val nested = File(tempFolder.root, "captures/2026/05/08")
        // Don't pre-create; the writer should mkdirs.
        assertFalse(nested.exists())
        val capture = File(nested, "pns_001.dng")
        val ref = LutSidecar.bundledRefFor(
            catalog = LutCatalog.None,
            captureFilename = capture.name,
            captureKind = LutSidecar.CaptureKind.Still,
            capturedAtUtc = "20260508T120000Z",
            lutSize = 33,
            sha256 = "3".repeat(64),
        )
        val sidecar = LutSidecarWriter.writeBundled(capture, ref)
        assertTrue("parent should have been created", nested.isDirectory)
        assertTrue(sidecar.isFile)
    }

    // ---- sha256Hex / sha256ForLut --------------------------------------

    @Test
    fun `sha256Hex of empty array is the canonical SHA-256 of empty input`() {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, LutSidecarWriter.sha256Hex(ByteArray(0)))
    }

    @Test
    fun `sha256Hex differs for different inputs and is stable across calls`() {
        val a = LutSidecarWriter.sha256Hex("hello".toByteArray(Charsets.UTF_8))
        val b = LutSidecarWriter.sha256Hex("world".toByteArray(Charsets.UTF_8))
        assertNotEquals(a, b)
        assertEquals(a, LutSidecarWriter.sha256Hex("hello".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `sha256Hex output is always 64 lowercase hex characters`() {
        val digest = LutSidecarWriter.sha256Hex(ByteArray(127) { it.toByte() })
        assertEquals(64, digest.length)
        for (c in digest) {
            assertTrue("expected lowercase hex, got '$c'", c in '0'..'9' || c in 'a'..'f')
        }
    }

    @Test
    fun `sha256ForLut is identical for two identical LUTs`() {
        val a = Lut3D.identity(33)
        val b = Lut3D.identity(33)
        assertEquals(LutSidecarWriter.sha256ForLut(a), LutSidecarWriter.sha256ForLut(b))
    }

    @Test
    fun `sha256ForLut differs across LUTs with different content`() {
        val identity = Lut3D.identity(33)
        val cinematic = LutCatalog.PnsCinematic.load(33)
        val identityHash = LutSidecarWriter.sha256ForLut(identity)
        val cinematicHash = LutSidecarWriter.sha256ForLut(cinematic)
        assertNotEquals(identityHash, cinematicHash)
        // And both still pass the lowercase-hex contract.
        assertEquals(64, identityHash.length)
        assertEquals(64, cinematicHash.length)
    }

    @Test
    fun `sha256ForLut differs across grid sizes (cube content is different)`() {
        val small = LutCatalog.BwBt709.load(17)
        val big = LutCatalog.BwBt709.load(65)
        assertNotEquals(LutSidecarWriter.sha256ForLut(small), LutSidecarWriter.sha256ForLut(big))
    }

    @Test
    fun `Bundled sidecar built end to end with sha256 from the LUT itself round trips`() {
        val captureDir = tempFolder.newFolder("captures")
        val capture = File(captureDir, "pns_still_007.jxl")
        val lut = LutCatalog.PnsCinematic.load(33)
        val sha = LutSidecarWriter.sha256ForLut(lut)
        val ref = LutSidecar.bundledRefFor(
            catalog = LutCatalog.PnsCinematic,
            captureFilename = capture.name,
            captureKind = LutSidecar.CaptureKind.Still,
            capturedAtUtc = "20260508T120000Z",
            lutSize = lut.size,
            sha256 = sha,
        )

        val sidecar = LutSidecarWriter.writeBundled(capture, ref)
        val parsed = LutSidecar.decode(sidecar.readText()) as LutSidecar.ParseResult.Bundled

        assertEquals(sha, parsed.ref.sha256)
        assertEquals("PnsCinematic", parsed.ref.cataloguedAs)
        assertEquals(LutSidecar.CaptureKind.Still, parsed.ref.captureKind)
        // No temp leftover.
        assertNull(sidecar.parentFile!!.listFiles { f ->
            f.name.contains(LutSidecarWriter.TEMP_SUFFIX)
        }?.firstOrNull())
    }
}
