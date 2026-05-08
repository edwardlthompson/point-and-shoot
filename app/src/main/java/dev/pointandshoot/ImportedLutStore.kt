package dev.pointandshoot

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Android-side filesystem wrapper for user-imported `.cube` LUTs per
 * BUILD_PLAN \u00a77 ("User-imported LUTs land in
 * `getExternalFilesDir(null)/luts/imported/`; SAF "Import LUT\u2026" picker
 * reads the user's `.cube` file, validates it (size + grid spacing + value
 * range), and copies it in. Invalid files are rejected with a toast").
 *
 * Validation is delegated to [LutImportValidator]; this object owns only the
 * directory + filename + sidecar conventions:
 *
 *   * Files land under `getExternalFilesDir(null)/luts/imported/<safeName>.cube`.
 *   * `<safeName>` is the user-supplied filename run through
 *     [sanitizeFilename] (alphanumerics / dot / dash / underscore only;
 *     other chars become `_`); collisions get a `_<n>` suffix.
 *   * On successful import we also write a SHA-256 sidecar at
 *     `<safeName>.cube.sha256.txt` so the [LutSidecar] capture-sidecar can
 *     reference imported LUTs by their content hash without re-reading the
 *     file.
 *
 * Pure file IO + a couple of pure helpers; no Android UI types so the
 * sanitization + collision logic can be unit-tested headless.
 */
object ImportedLutStore {

    /** Subdirectory under `getExternalFilesDir(null)`. */
    const val SUBDIR_PATH: String = "luts/imported"

    /** Mandatory extension for every imported LUT file we keep on disk. */
    const val LUT_EXTENSION: String = ".cube"

    /** Sidecar extension storing the SHA-256 of the LUT bytes. */
    const val SHA256_SIDECAR_EXTENSION: String = ".cube.sha256.txt"

    /**
     * Resolve (and create on demand) the imported-LUTs directory, returning
     * `null` when external storage is unavailable.
     */
    fun directory(context: Context): File? {
        val base = context.getExternalFilesDir(null) ?: return null
        val dir = File(base, SUBDIR_PATH)
        if (!dir.exists()) {
            if (!dir.mkdirs() && !dir.exists()) return null
        }
        return dir
    }

    /**
     * Save a validated LUT body to the imported-LUTs directory under a
     * collision-free filename derived from [requestedDisplayName]. Writes
     * via temp + atomic rename so a crashed write never leaves a half-written
     * file. Also writes a `<name>.cube.sha256.txt` sidecar with the computed
     * digest.
     *
     * @return the [File] that was written + the SHA-256 hex string, or
     *     `null` when external storage is unavailable.
     */
    @Throws(java.io.IOException::class)
    fun save(
        context: Context,
        requestedDisplayName: String,
        lutBytes: ByteArray,
    ): SavedImport? {
        val dir = directory(context) ?: return null
        val safeStem = sanitizeFilename(requestedDisplayName).removeSuffix(LUT_EXTENSION)
        val finalFile = pickAvailableFilename(dir, safeStem)
        val tmpFile = File(dir, "${finalFile.name}.tmp")
        try {
            tmpFile.writeBytes(lutBytes)
            if (!tmpFile.renameTo(finalFile)) {
                throw java.io.IOException("Atomic rename failed: $tmpFile -> $finalFile")
            }
            val digest = sha256(lutBytes)
            val sidecar = File(dir, "${finalFile.nameWithoutExtension}$SHA256_SIDECAR_EXTENSION")
            sidecar.writeText("$digest  ${finalFile.name}\n", Charsets.UTF_8)
            return SavedImport(file = finalFile, sha256 = digest)
        } catch (ex: Throwable) {
            tmpFile.delete()
            throw ex
        }
    }

    /** Enumerate every imported LUT, newest-first by mtime. */
    fun list(context: Context): List<File> {
        val dir = directory(context) ?: return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(LUT_EXTENSION) }
            ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    /**
     * Replace every char that is not `[A-Za-z0-9._-]` with `_`, collapse
     * runs of underscores, and strip leading dots so we don't accidentally
     * land hidden files.
     */
    fun sanitizeFilename(raw: String): String {
        val cleaned = raw.map { c -> if (allowed(c)) c else '_' }
            .joinToString("")
        val collapsed = cleaned.replace(Regex("_+"), "_").trimStart('.', '_')
        // Cap to a reasonable length to avoid overflowing the filesystem name limits.
        return if (collapsed.length > 96) collapsed.substring(0, 96) else collapsed.ifEmpty { "imported_lut" }
    }

    /**
     * Pick a non-colliding `<stem>.cube` (or `<stem>_<n>.cube`) under [dir].
     */
    fun pickAvailableFilename(dir: File, stem: String): File {
        val first = File(dir, "$stem$LUT_EXTENSION")
        if (!first.exists()) return first
        var n = 2
        while (true) {
            val candidate = File(dir, "${stem}_$n$LUT_EXTENSION")
            if (!candidate.exists()) return candidate
            n += 1
            if (n > 9999) {
                // Pathological case - 9999 collisions on the same stem. Fall back to a hash so we don't loop forever.
                val tail = sha256(stem.toByteArray(Charsets.UTF_8)).take(8)
                return File(dir, "${stem}_$tail$LUT_EXTENSION")
            }
        }
    }

    /** Lowercase hex SHA-256 of [bytes]. */
    fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun allowed(c: Char): Boolean =
        (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9') || c == '.' || c == '_' || c == '-'

    data class SavedImport(val file: File, val sha256: String)
}
