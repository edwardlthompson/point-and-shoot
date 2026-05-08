package dev.pointandshoot

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * File-IO wrapper around [LutSidecar.encode] for the engine-side capture
 * pipeline. The pure-data sidecar format lives in [LutSidecar]; this class
 * adds the disk write (atomic rename via temp file) and the SHA-256 helpers
 * the engine needs to populate [LutSidecar.BundledRef]/`CubeFileRef`.
 *
 * The write contract:
 *   * Sidecars are siblings of the capture file (per BUILD_PLAN §7
 *     "Capture sidecar"): `pns_<utc>_<profile>_001.dng` →
 *     `pns_<utc>_<profile>_001.dng.lutref.txt` (bundled) or `.cube.txt`
 *     (user-imported). The naming logic is delegated to
 *     [LutSidecar.siblingFilenameFor].
 *   * Writes are atomic at the filesystem level: the encoded text lands
 *     in `<sibling>.tmp.<timestamp>` first, `flush()` + `close()` happen,
 *     then we `renameTo` (or fall back to delete + rename on Windows
 *     when the target already exists). A crashed write therefore never
 *     leaves a half-formed sidecar that a desktop tool would mis-parse.
 *   * The writer never overwrites the capture file itself - if the
 *     capture file is missing on disk, the sidecar still writes (the
 *     capture pipeline may have streamed it directly to MediaStore via
 *     a `ContentResolver` pipe, in which case the sidecar lands in a
 *     parallel `parentDir` the caller picks).
 *
 * No Android dependencies on the pure-IO surface here so JUnit can
 * exercise the writer against `JUnit @TempDir`-style scratch dirs.
 */
object LutSidecarWriter {

    /** Suffix used for the temp file before atomic rename. */
    const val TEMP_SUFFIX: String = ".pns-tmp"

    /**
     * Write a bundled-LUT sidecar for [captureFile]. Returns the sidecar
     * file actually written.
     *
     * @throws IOException on disk failure (caller decides whether to retry
     *   or surface to the user via toast).
     */
    fun writeBundled(captureFile: File, ref: LutSidecar.BundledRef): File {
        require(ref.captureFilename == captureFile.name) {
            "ref.captureFilename ('${ref.captureFilename}') must equal captureFile.name ('${captureFile.name}')"
        }
        val sibling = siblingFor(captureFile, isBundled = true)
        atomicWrite(sibling, LutSidecar.encode(ref))
        return sibling
    }

    /**
     * Write a user-cube sidecar for [captureFile]. Returns the sidecar
     * file actually written.
     */
    fun writeCube(captureFile: File, ref: LutSidecar.CubeFileRef): File {
        require(ref.captureFilename == captureFile.name) {
            "ref.captureFilename ('${ref.captureFilename}') must equal captureFile.name ('${captureFile.name}')"
        }
        val sibling = siblingFor(captureFile, isBundled = false)
        atomicWrite(sibling, LutSidecar.encode(ref))
        return sibling
    }

    /**
     * Resolve the sidecar file path that pairs with [captureFile]. Pure
     * function; does no disk IO. Used by the engine to surface the path
     * in PNS.Capture logs before writing.
     */
    fun siblingFor(captureFile: File, isBundled: Boolean): File {
        val name = LutSidecar.siblingFilenameFor(captureFile.name, isBundled)
        val parent = captureFile.parentFile ?: File(".")
        return File(parent, name)
    }

    /**
     * Lowercase hex SHA-256 over [bytes]. Use this on the encoded `.cube`
     * text or on the [Lut3D.samples] buffer to populate the `sha256` field
     * required by both [LutSidecar.BundledRef] and [LutSidecar.CubeFileRef].
     */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /**
     * Lowercase hex SHA-256 over the IEEE-754 little-endian byte
     * representation of [Lut3D.samples]. The byte order is fixed (LE) so
     * the same LUT yields the same hash on every host (Android happens to
     * be LE, but pinning this guarantees a desktop hashing tool sees the
     * same digest). Bundled-LUT sidecars use this to compute the `sha256`
     * field for code-generated entries that have no on-disk asset.
     */
    fun sha256ForLut(lut: Lut3D): String {
        val buffer = java.nio.ByteBuffer
            .allocate(lut.samples.size * 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (sample in lut.samples) buffer.putFloat(sample)
        return sha256Hex(buffer.array())
    }

    // -------- internals ---------

    private fun atomicWrite(target: File, contents: String) {
        val parent = target.parentFile ?: File(".")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Cannot create parent directory ${parent.absolutePath}")
        }
        val temp = File(parent, target.name + TEMP_SUFFIX + "." + System.nanoTime())
        try {
            temp.writeText(contents, Charsets.UTF_8)
            // On Windows, renameTo fails if the target exists. Delete-and-rename
            // is the standard work-around for "atomic from a crash perspective"
            // semantics (a power loss between delete and rename leaves the OS
            // with no sidecar; partial sidecars never exist on disk).
            if (target.exists() && !target.delete()) {
                temp.delete()
                throw IOException("Cannot replace existing sidecar ${target.absolutePath}")
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Atomic rename failed: ${temp.name} -> ${target.name}")
            }
        } catch (t: Throwable) {
            if (temp.exists()) temp.delete()
            throw t
        }
    }

    private val HEX: CharArray = "0123456789abcdef".toCharArray()
}
