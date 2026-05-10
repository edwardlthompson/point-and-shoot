package dev.pointandshoot

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Android-side file-IO wrapper around [CalibrationProfileJsonAdapter].
 *
 * Closes the file-IO portion of BUILD_PLAN.md §7:
 *
 *     Profile JSON saved to app-private storage
 *     `getExternalFilesDir(null)/calibration/<illuminant>_<utc>.json`;
 *     pulled into `<OutDir>/calibration/` (default `.\\hfr-runs\\calibration\\`)
 *     by `pns_hfr_autorun.ps1 -PullCalibration`.
 *
 * The storage layer is intentionally thin: the JSON encode/decode lives in
 * [CalibrationProfileJsonAdapter] (pure-data, JVM-testable), and this layer
 * adds only:
 *
 *   * Directory + filename convention enforcement
 *     (`getExternalFilesDir(null)/calibration/<illuminant>_<utc>.json`).
 *   * Atomic writes via temp-file + `renameTo` so a crashed write never
 *     leaves a half-formed JSON document on disk.
 *   * UTF-8 encoding for both writes and reads (defensive against the
 *     platform default charset, which on Windows host pulls would be
 *     CP-1252).
 *   * UTC timestamp generation that matches the rest of the project's
 *     timestamp convention (`yyyyMMdd_HHmmss` UTC, used by
 *     `pns_hfr_autorun.ps1` and the diagnostics dump).
 *
 * Listing existing profiles is also exposed so the in-app Calibrate flow
 * can offer "Use last D65 profile" without re-reading the JSON.
 */
object CalibrationProfileStorage {

    /** Subdirectory under `getExternalFilesDir(null)`. */
    const val SUBDIR_NAME: String = "calibration"

    /** Project-wide UTC timestamp format: `yyyyMMdd_HHmmss`. */
    const val TIMESTAMP_PATTERN: String = "yyyyMMdd_HHmmss"

    /**
     * Resolve (and create on demand) the calibration directory:
     * `getExternalFilesDir(null)/calibration/`.
     *
     * Returns `null` if external storage is unavailable (e.g., the device
     * is in a no-storage state). Callers should fall back gracefully -
     * the in-app Calibrate flow surfaces a toast when this returns `null`.
     */
    fun directory(context: Context): File? {
        val base = context.getExternalFilesDir(null) ?: return null
        val dir = File(base, SUBDIR_NAME)
        if (!dir.exists()) {
            if (!dir.mkdirs() && !dir.exists()) return null
        }
        return dir
    }

    /**
     * Save [profile] to `directory(context)/<illuminant>_<utc>.json`.
     *
     * The write is atomic: bytes are first written to a sibling
     * `*.json.tmp`, then `renameTo`d over the final path. A failed
     * intermediate write deletes the temp file before propagating the
     * exception, so the final path is either absent or fully-formed.
     *
     * Returns the [File] that was written, or `null` if external storage
     * was unavailable (so the caller can show the "no storage" toast).
     *
     * @param utcTimestamp explicit `yyyyMMdd_HHmmss` timestamp, or `null` to
     *     compute the current UTC time. Tests pass an explicit value to keep
     *     filenames deterministic.
     */
    @Throws(java.io.IOException::class)
    fun save(
        context: Context,
        profile: CalibrationProfile,
        utcTimestamp: String? = null,
    ): File? {
        val dir = directory(context) ?: return null
        val utc = utcTimestamp ?: nowUtcTimestamp()
        val filename = CalibrationProfileJsonAdapter.filenameFor(profile, utc)
        val finalFile = File(dir, filename)
        val tmpFile = File(dir, "$filename.tmp")
        val json = CalibrationProfileJsonAdapter.encode(profile)
        try {
            tmpFile.writeText(json, Charsets.UTF_8)
            if (finalFile.exists() && !finalFile.delete()) {
                throw java.io.IOException("Could not overwrite existing profile: $finalFile")
            }
            if (!tmpFile.renameTo(finalFile)) {
                throw java.io.IOException(
                    "Atomic rename failed: $tmpFile -> $finalFile",
                )
            }
            return finalFile
        } catch (ex: Throwable) {
            tmpFile.delete()
            throw ex
        }
    }

    /**
     * Load a single calibration profile by absolute [path].
     *
     * @throws java.io.IOException if the file cannot be read.
     * @throws IllegalArgumentException if the JSON is malformed (delegated
     *     to [CalibrationProfileJsonAdapter.decode]).
     */
    @Throws(java.io.IOException::class)
    fun load(path: File): CalibrationProfile {
        val text = path.readText(Charsets.UTF_8)
        return CalibrationProfileJsonAdapter.decode(text)
    }

    /**
     * Enumerate every saved calibration profile, newest first by
     * filesystem mtime. Returns an empty list when the directory does not
     * exist (storage unavailable, or no profile has been saved yet).
     */
    fun list(context: Context): List<File> {
        val dir = directory(context) ?: return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    /**
     * Most recent saved profile for [illuminant], or `null` when none
     * exists. Filename matching is exact-prefix (`<illuminant>_*.json`),
     * which is the convention enforced by [save] +
     * [CalibrationProfileJsonAdapter.filenameFor].
     */
    fun latestFor(
        context: Context,
        illuminant: CalibrationProfile.Illuminant,
    ): File? {
        val prefix = "${illuminant.name}_"
        return list(context).firstOrNull { it.name.startsWith(prefix) }
    }

    /**
     * Current UTC timestamp formatted per [TIMESTAMP_PATTERN]. Matches the
     * format used by `pns_hfr_autorun.ps1` for cross-tool consistency.
     */
    fun nowUtcTimestamp(): String {
        val fmt = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.ROOT)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}
