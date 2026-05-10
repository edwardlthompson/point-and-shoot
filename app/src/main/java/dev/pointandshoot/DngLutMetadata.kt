package dev.pointandshoot

/**
 * Pure-data formatter for the DNG metadata that records the active LUT
 * identity per BUILD_PLAN §7:
 *
 *   "RAW (DNG): never baked in. The active LUT name + SHA256 are written
 *    into the DNG's `UniqueCameraModel` / `Software` tag chain so desktop
 *    processors (`darktable`, `RawTherapee`) can apply the same LUT
 *    optionally."
 *
 * The actual `Software` string is written by `Dng12Saver.save` calling
 * `DngCreator.setDescription`. DNG tag 50708 (`UniqueCameraModel`) has **no** public
 * setter on `DngCreator`; `Dng12Saver.save(uniqueCameraModel = …)` post-processes the
 * written TIFF via `TiffUniqueCameraModel50708`. This module produces the **strings**
 * for `Software` (TIFF tag 305) and `UniqueCameraModel` (50708). Splitting formatters
 * from IO keeps LUT identity logic JVM-testable without Camera2.
 *
 * Why two tags:
 *   * `Software` is the primary marker. It's a free-form ASCII string
 *     displayed by every DNG viewer (`exiftool`, `darktable`, `Adobe
 *     Bridge`), and the "App + version + LUT" is exactly what users
 *     expect to find there.
 *   * `UniqueCameraModel` is the device + cameraId identity (per the
 *     DNG spec, "a unique, non-localized name for the camera model
 *     ... combined with a unique identifier for that particular
 *     instance"). We append a LUT marker token only when the LUT is
 *     non-trivial AND the engine has explicitly opted in via
 *     [includeLutMarkerInUniqueCameraModel]; the default behavior
 *     leaves the camera model untouched so RAW processors that key
 *     their per-device profiles off this string still find a stable
 *     match.
 *
 * No Android imports - safe for unit testing on the JVM.
 */
object DngLutMetadata {

    /** Bumped only when the formatted-string schema changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** The fixed app-identity prefix on the `Software` tag. */
    const val APP_NAME: String = "Point & Shoot"

    /**
     * Format the `Software` tag value. With no [activeLut] present (user
     * picked None / identity), returns just `"<APP_NAME> v<appVersion>"`;
     * with a non-identity LUT, appends a deterministic, regex-friendly
     * marker `"LUT=<name> SHA256=<sha>"` so desktop tooling can extract
     * the LUT identity with a single regex.
     *
     * Resulting format (no LUT):
     *   ```
     *   Point & Shoot v0.1.0
     *   ```
     *
     * Resulting format (with LUT):
     *   ```
     *   Point & Shoot v0.1.0 / LUT=PnsCinematic SHA256=a0bf60ef...0d138f
     *   ```
     */
    fun formatSoftwareTag(appVersion: String, activeLut: LutIdentity? = null): String {
        require(appVersion.isNotBlank()) { "appVersion must not be blank" }
        val sb = StringBuilder()
        sb.append(APP_NAME).append(" v").append(appVersion.trim())
        if (activeLut != null) {
            sb.append(" / ").append(formatLutMarker(activeLut))
        }
        return sb.toString()
    }

    /**
     * Format the `UniqueCameraModel` tag value. Defaults to
     * `"<deviceModel> (cameraId=<id>)"` so per-device profiles in desktop
     * RAW processors still resolve. Pass [includeLutMarkerInUniqueCameraModel]
     * `= true` only when the engine has explicitly opted in (e.g. for
     * forensic capture where the LUT identity must round-trip even when
     * the `Software` tag is overwritten by a later editor).
     */
    fun formatUniqueCameraModel(
        deviceModel: String,
        cameraId: String,
        activeLut: LutIdentity? = null,
        includeLutMarkerInUniqueCameraModel: Boolean = false,
    ): String {
        require(deviceModel.isNotBlank()) { "deviceModel must not be blank" }
        require(cameraId.isNotBlank()) { "cameraId must not be blank" }
        val sb = StringBuilder()
        sb.append(deviceModel.trim()).append(" (cameraId=").append(cameraId.trim()).append(')')
        if (activeLut != null && includeLutMarkerInUniqueCameraModel) {
            sb.append(" / ").append(formatLutMarker(activeLut))
        }
        return sb.toString()
    }

    /**
     * Just the `LUT=<name> SHA256=<sha>` segment. Useful when the engine
     * needs to stamp the marker into a custom XMP field without rebuilding
     * the entire `Software` string.
     */
    fun formatLutMarker(lut: LutIdentity): String =
        "LUT=${lut.markerName} SHA256=${lut.sha256}"

    /**
     * Recover the LUT identity from a previously-formatted `Software`
     * tag value (or any string containing the marker). Returns `null`
     * when the marker is absent or malformed; never throws.
     *
     * The regex enforces a 64-lowercase-hex SHA256 so a tag stamped by
     * a different tool (e.g. `LUT=foo SHA256=invalid`) does not silently
     * decode as ours.
     */
    fun parseLutMarker(softwareTag: String): ParsedMarker? {
        val match = MARKER_REGEX.find(softwareTag) ?: return null
        val name = match.groupValues[1]
        val sha = match.groupValues[2]
        if (name.isBlank() || sha.length != 64) return null
        return ParsedMarker(markerName = name, sha256 = sha)
    }

    /** Regex used by [parseLutMarker]. Exposed for the test corpus. */
    val MARKER_REGEX: Regex = Regex("""LUT=([^\s]+)\s+SHA256=([a-f0-9]{64})""")

    /**
     * LUT identity adapter so both bundled-catalog and user-imported
     * cubes can flow through [formatSoftwareTag] / [formatLutMarker].
     * Decoupled from `LutSidecar` types so the engine can also pass an
     * ad-hoc identity (e.g. for synthetic test patterns).
     */
    sealed class LutIdentity {
        abstract val markerName: String
        abstract val sha256: String

        /** A bundled catalog LUT - the marker name is the catalog id. */
        data class Bundled(val catalogId: String, override val sha256: String) : LutIdentity() {
            override val markerName: String get() = catalogId
            init {
                require(catalogId.isNotBlank()) { "catalogId must not be blank" }
                require(!catalogId.any { it.isWhitespace() }) {
                    "catalogId must not contain whitespace (was '$catalogId')"
                }
                require(sha256.matches(SHA256_PATTERN)) {
                    "sha256 must be 64 lowercase hex chars (was '$sha256')"
                }
            }
        }

        /** A user-imported `.cube` file - the marker name is the safe-filename. */
        data class CubeFile(val safeFilename: String, override val sha256: String) : LutIdentity() {
            override val markerName: String get() = safeFilename
            init {
                require(safeFilename.isNotBlank()) { "safeFilename must not be blank" }
                require(!safeFilename.any { it.isWhitespace() }) {
                    "safeFilename must not contain whitespace (was '$safeFilename')"
                }
                require(sha256.matches(SHA256_PATTERN)) {
                    "sha256 must be 64 lowercase hex chars (was '$sha256')"
                }
            }
        }
    }

    /** Result of [parseLutMarker] - the round-trippable identity tokens. */
    data class ParsedMarker(val markerName: String, val sha256: String)

    private val SHA256_PATTERN = Regex("[a-f0-9]{64}")
}
