package dev.pointandshoot

/**
 * Capture-sidecar writer + parser per BUILD_PLAN §7 ("Capture sidecar: every
 * still or video written with a non-identity LUT also writes a sibling
 * `.cube.txt` (or, for bundled LUTs, a small `.lutref.txt` pointing at
 * `assets/luts/<name>` + SHA256) so the LUT'd output is reproducible offline").
 *
 * The format is intentionally line-oriented + ASCII so it survives `cat`,
 * `notepad`, and `adb pull` round-trips without encoding loss. Lines beginning
 * with `#` are comments; key/value pairs use `key = value` (whitespace-tolerant).
 *
 * Two flavors:
 *   * **`.lutref.txt`** — points at a bundled LUT entry by name. The
 *     applied-LUT identity is the catalog enum + size + a content SHA256;
 *     parsers can re-resolve from `LutCatalog.entries` and verify the SHA.
 *   * **`.cube.txt`** — points at a user-imported `.cube` file by relative
 *     path + the file's SHA256. The actual cube data is NOT inlined (the
 *     source `.cube` lives next to it).
 *
 * Both flavors share a common header (`pns-lut-sidecar v1`, capture
 * timestamp, source-file basename, capture target kind). No Android imports;
 * safe for unit testing on the JVM.
 */
object LutSidecar {

    /** Bumped only on incompatible schema changes. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical extension for the bundled-LUT flavor. */
    const val EXTENSION_LUTREF: String = ".lutref.txt"

    /** Canonical extension for the user-imported `.cube` flavor. */
    const val EXTENSION_CUBE: String = ".cube.txt"

    /** Header line - identifies sidecars to greps and human readers. */
    private const val MAGIC: String = "# pns-lut-sidecar"

    /**
     * Capture target the sidecar is for. Stills + video share the same format
     * because both consume LUTs through the same catalog seam.
     */
    enum class CaptureKind { Still, Video }

    /**
     * Bundled-LUT sidecar variant - the active LUT is one of the
     * [LutCatalog] entries.
     */
    data class BundledRef(
        val captureFilename: String,
        val captureKind: CaptureKind,
        val capturedAtUtc: String,
        val cataloguedAs: String,
        val lutSize: Int,
        val spdx: String,
        val source: String,
        val sha256: String,
    ) {
        init {
            require(captureFilename.isNotBlank()) { "captureFilename must not be blank" }
            require(capturedAtUtc.isNotBlank()) { "capturedAtUtc must not be blank" }
            require(cataloguedAs.isNotBlank()) { "cataloguedAs must not be blank" }
            require(spdx.isNotBlank()) { "spdx must not be blank" }
            require(source.isNotBlank()) { "source must not be blank" }
            require(sha256.matches(SHA256_PATTERN)) {
                "sha256 must be 64 lowercase hex chars (was '$sha256')"
            }
            require(lutSize in Lut3D.SUPPORTED_SIZES) {
                "lutSize must be in ${Lut3D.SUPPORTED_SIZES} (was $lutSize)"
            }
        }
    }

    /**
     * User-imported-cube sidecar variant - the active LUT is a `.cube` file
     * the user picked via SAF.
     */
    data class CubeFileRef(
        val captureFilename: String,
        val captureKind: CaptureKind,
        val capturedAtUtc: String,
        val cubeRelativePath: String,
        val lutSize: Int,
        val title: String?,
        val sha256: String,
    ) {
        init {
            require(captureFilename.isNotBlank()) { "captureFilename must not be blank" }
            require(capturedAtUtc.isNotBlank()) { "capturedAtUtc must not be blank" }
            require(cubeRelativePath.isNotBlank()) { "cubeRelativePath must not be blank" }
            require(sha256.matches(SHA256_PATTERN)) {
                "sha256 must be 64 lowercase hex chars (was '$sha256')"
            }
            require(lutSize in Lut3D.SUPPORTED_SIZES) {
                "lutSize must be in ${Lut3D.SUPPORTED_SIZES} (was $lutSize)"
            }
        }
    }

    // ---------- writers ----------

    fun encode(ref: BundledRef): String {
        val sb = StringBuilder()
        appendHeader(sb, ref.captureFilename, ref.captureKind, ref.capturedAtUtc)
        sb.appendLine("kind = bundled")
        sb.appendLine("cataloguedAs = ${ref.cataloguedAs}")
        sb.appendLine("lutSize = ${ref.lutSize}")
        sb.appendLine("spdx = ${ref.spdx}")
        sb.appendLine("source = ${ref.source}")
        sb.appendLine("sha256 = ${ref.sha256}")
        return sb.toString()
    }

    fun encode(ref: CubeFileRef): String {
        val sb = StringBuilder()
        appendHeader(sb, ref.captureFilename, ref.captureKind, ref.capturedAtUtc)
        sb.appendLine("kind = cube")
        sb.appendLine("cubeRelativePath = ${ref.cubeRelativePath}")
        sb.appendLine("lutSize = ${ref.lutSize}")
        if (ref.title != null) {
            sb.appendLine("title = ${ref.title}")
        }
        sb.appendLine("sha256 = ${ref.sha256}")
        return sb.toString()
    }

    /**
     * Convenience: build a [BundledRef] from a [LutCatalog] entry. The
     * `sha256` comes from the caller because computing it requires the
     * actual `Lut3D.samples` buffer (out of scope for this pure-data class).
     */
    fun bundledRefFor(
        catalog: LutCatalog,
        captureFilename: String,
        captureKind: CaptureKind,
        capturedAtUtc: String,
        lutSize: Int,
        sha256: String,
    ): BundledRef = BundledRef(
        captureFilename = captureFilename,
        captureKind = captureKind,
        capturedAtUtc = capturedAtUtc,
        cataloguedAs = catalog.name,
        lutSize = lutSize,
        spdx = catalog.spdx,
        source = catalog.source,
        sha256 = sha256,
    )

    /**
     * Default sibling filename for the sidecar. For `still_001.dng` →
     * `still_001.dng.lutref.txt` or `still_001.dng.cube.txt`. The sidecar
     * always lives next to the capture file with the appropriate extension.
     */
    fun siblingFilenameFor(captureFilename: String, isBundled: Boolean): String =
        captureFilename + if (isBundled) EXTENSION_LUTREF else EXTENSION_CUBE

    // ---------- parsers ----------

    /**
     * Parse a sidecar produced by either [encode] overload. Auto-detects the
     * `kind` field and returns either [BundledRef] or [CubeFileRef] in a
     * sealed result type.
     */
    fun decode(text: String): ParseResult {
        val map = mutableMapOf<String, String>()
        var captureKind: CaptureKind? = null
        var captureFilename: String? = null
        var capturedAtUtc: String? = null
        var sawMagic = false
        var version: Int? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                if (!sawMagic && line.startsWith(MAGIC)) {
                    sawMagic = true
                    val match = SCHEMA_REGEX.find(line)
                    require(match != null) {
                        "magic header missing schema version: '$line'"
                    }
                    version = match.groupValues[1].toInt()
                }
                continue
            }
            val eq = line.indexOf('=')
            require(eq > 0) { "expected key = value pair, got: '$line'" }
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            require(key.isNotBlank()) { "blank key in line: '$line'" }
            when (key) {
                "captureFilename" -> captureFilename = value
                "captureKind" -> captureKind = CaptureKind.valueOf(value)
                "capturedAtUtc" -> capturedAtUtc = value
                else -> map[key] = value
            }
        }
        require(sawMagic) { "not a pns-lut-sidecar (missing magic '$MAGIC')" }
        require(version == SCHEMA_VERSION) {
            "unsupported schema version $version (expected $SCHEMA_VERSION)"
        }
        require(captureFilename != null) { "missing captureFilename" }
        require(captureKind != null) { "missing captureKind" }
        require(capturedAtUtc != null) { "missing capturedAtUtc" }
        val kind = map["kind"] ?: throw IllegalArgumentException("missing kind")
        return when (kind) {
            "bundled" -> ParseResult.Bundled(
                BundledRef(
                    captureFilename = captureFilename,
                    captureKind = captureKind,
                    capturedAtUtc = capturedAtUtc,
                    cataloguedAs = requireKey(map, "cataloguedAs"),
                    lutSize = requireKey(map, "lutSize").toInt(),
                    spdx = requireKey(map, "spdx"),
                    source = requireKey(map, "source"),
                    sha256 = requireKey(map, "sha256"),
                ),
            )
            "cube" -> ParseResult.Cube(
                CubeFileRef(
                    captureFilename = captureFilename,
                    captureKind = captureKind,
                    capturedAtUtc = capturedAtUtc,
                    cubeRelativePath = requireKey(map, "cubeRelativePath"),
                    lutSize = requireKey(map, "lutSize").toInt(),
                    title = map["title"],
                    sha256 = requireKey(map, "sha256"),
                ),
            )
            else -> throw IllegalArgumentException("unknown kind '$kind' (expected 'bundled' or 'cube')")
        }
    }

    /** Sealed parse result so callers can pattern-match on the sidecar flavor. */
    sealed class ParseResult {
        data class Bundled(val ref: BundledRef) : ParseResult()
        data class Cube(val ref: CubeFileRef) : ParseResult()
    }

    // ---------- helpers ----------

    private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
    private val SCHEMA_REGEX = Regex("v(\\d+)")

    private fun appendHeader(
        sb: StringBuilder,
        captureFilename: String,
        captureKind: CaptureKind,
        capturedAtUtc: String,
    ) {
        sb.appendLine("$MAGIC v$SCHEMA_VERSION")
        sb.appendLine("# Generated by Point & Shoot. Reproducible LUT identity for the capture below.")
        sb.appendLine("captureFilename = $captureFilename")
        sb.appendLine("captureKind = ${captureKind.name}")
        sb.appendLine("capturedAtUtc = $capturedAtUtc")
    }

    private fun requireKey(map: Map<String, String>, key: String): String =
        map[key] ?: throw IllegalArgumentException("missing $key")
}
