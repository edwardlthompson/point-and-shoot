package dev.pointandshoot

/**
 * Pure-data validator for user-imported `.cube` files per BUILD_PLAN §7
 * "User-imported LUTs land in `getExternalFilesDir(null)/luts/imported/`;
 * SAF "Import LUT…" picker reads the user's `.cube` file, validates it
 * (size + grid spacing + value range), and copies it in. Invalid files are
 * rejected with a toast."
 *
 * Wraps [LutPipeline.parseCube] with a structured result type so the SAF
 * picker (and any future CLI wrapper) can surface human-friendly toast
 * messages without parsing exception strings. The validator is the
 * single seam where every user-supplied LUT must pass before it is copied
 * into the imported-LUTs directory.
 *
 * Validation:
 *   * **Format**: must parse as Adobe Cube (`LUT_3D_SIZE`, `[0, 1]` domain,
 *     `size^3` RGB triples, 1D LUTs rejected).
 *   * **Size**: must be one of [Lut3D.SUPPORTED_SIZES] (17 / 33 / 65); other
 *     sizes are valid Cube files but our GLES + CPU paths only know how to
 *     trilinearly sample the supported sizes.
 *   * **Value range**: every interior sample must lie in `[ALLOWED_MIN,
 *     ALLOWED_MAX]` (default `[-0.001, 1.001]`); we tolerate a tiny
 *     floating-point fuzz around the canonical `[0, 1]` so LUTs exported
 *     from professional tools that round trip through float32 still pass.
 *   * **Grid spacing** (informational; not currently enforced because the
 *     Cube format implies uniform spacing once the size is fixed): future
 *     work may sample the diagonals to detect non-uniform-spaced cubes.
 *   * **Size limit**: the raw text payload must be smaller than
 *     [MAX_TEXT_BYTES] so a runaway file does not OOM the parser.
 *
 * No Android imports - safe for unit testing on the JVM.
 */
object LutImportValidator {

    /**
     * Hard cap on the raw `.cube` payload size (in bytes). 65³ × 3 floats at
     * ~12 chars per float ≈ 10 MB upper bound; we round up to 16 MB so a
     * pathological-but-well-formed file with extreme float widths still
     * fits, but a multi-gigabyte upload is rejected before we allocate.
     */
    const val MAX_TEXT_BYTES: Long = 16L * 1024 * 1024

    /** Minimum allowed sample value (small negative to tolerate fp fuzz). */
    const val ALLOWED_MIN: Float = -0.001f

    /** Maximum allowed sample value (small positive to tolerate fp fuzz). */
    const val ALLOWED_MAX: Float = 1.001f

    /**
     * Validate a candidate LUT import. Always returns a [Result];
     * never throws. Routes by content sniff: `.cube` text is recognized by
     * the `LUT_3D_SIZE` keyword; `.3dl` text is recognized by the
     * absence of `LUT_3D_SIZE` plus the presence of an integer-only
     * shaper line. Use [validate] when you have only the file body
     * (the SAF picker may have stripped the original filename); use
     * [validateForFormat] when the format is already known.
     */
    fun validate(text: String): Result {
        val byteSize = text.toByteArray(Charsets.UTF_8).size.toLong()
        if (byteSize > MAX_TEXT_BYTES) {
            return Result.Failure(
                category = FailureCategory.TooLarge,
                message = "LUT text exceeds the $MAX_TEXT_BYTES-byte limit (got $byteSize bytes).",
                cause = null,
            )
        }
        return validateForFormat(text, sniffFormat(text))
    }

    /**
     * Sniff the LUT text format: the `.cube` and `.spi3d` header
     * keywords win unambiguously; otherwise we assume `.3dl` (Autodesk
     * Mesh, integer-only, no header keyword) when the first integer
     * line is long enough to be a shaper, falling back to `.cube` so
     * the canonical `MalformedHeader` failure category is preserved
     * for body-only `.cube` payloads.
     */
    fun sniffFormat(text: String): Format {
        val firstNonComment = text.lineSequence()
            .map { it.substringBefore('#').trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return Format.Cube
        val upper = firstNonComment.uppercase()
        if (upper.startsWith("SPILUT")) return Format.Spi3d
        if (upper.startsWith("LUT_3D_SIZE") ||
            upper.startsWith("TITLE") ||
            upper.startsWith("DOMAIN_") ||
            upper.startsWith("LUT_1D_SIZE")
        ) {
            return Format.Cube
        }
        val tokens = firstNonComment.split(Regex("\\s+"))
        // .3dl shapers always have >= MIN supported size (17) integers
        // on a single line; anything shorter is a `.cube` body row that
        // happens to contain only integers (e.g. "0 0 0"). Routing those
        // shorter lines to `.cube` preserves the canonical `MalformedHeader`
        // failure category for body-only `.cube` payloads.
        val minDl3Shaper = Lut3D.SUPPORTED_SIZES.min()
        return if (tokens.size >= minDl3Shaper && tokens.all { it.toIntOrNull() != null }) {
            Format.Dl3
        } else {
            Format.Cube
        }
    }

    /** Validate against the explicit [format]; useful for round-trip tests. */
    fun validateForFormat(text: String, format: Format): Result {
        val byteSize = text.toByteArray(Charsets.UTF_8).size.toLong()
        if (byteSize > MAX_TEXT_BYTES) {
            return Result.Failure(
                category = FailureCategory.TooLarge,
                message = "LUT text exceeds the $MAX_TEXT_BYTES-byte limit (got $byteSize bytes).",
                cause = null,
            )
        }

        val lut: Lut3D = try {
            when (format) {
                Format.Cube -> LutPipeline.parseCube(text)
                Format.Dl3 -> LutPipeline.parseDl3(text)
                Format.Spi3d -> LutPipeline.parseSpi3d(text)
            }
        } catch (ex: IllegalArgumentException) {
            return Result.Failure(
                category = categorize(ex.message ?: ""),
                message = ex.message ?: "Invalid LUT file.",
                cause = ex,
            )
        } catch (ex: IllegalStateException) {
            return Result.Failure(
                category = categorize(ex.message ?: ""),
                message = ex.message ?: "Invalid LUT file.",
                cause = ex,
            )
        }

        // Range check (parseCube only enforces the domain, not the cell values).
        var outOfRange = 0
        var firstIdx = -1
        var firstValue = Float.NaN
        for (i in lut.samples.indices) {
            val v = lut.samples[i]
            if (v.isNaN() || v < ALLOWED_MIN || v > ALLOWED_MAX) {
                if (firstIdx < 0) {
                    firstIdx = i
                    firstValue = v
                }
                outOfRange += 1
            }
        }
        if (outOfRange > 0) {
            return Result.Failure(
                category = FailureCategory.OutOfRange,
                message = "LUT contains $outOfRange value(s) outside [$ALLOWED_MIN, $ALLOWED_MAX]; " +
                    "first offender at sample index $firstIdx (= $firstValue).",
                cause = null,
            )
        }

        return Result.Success(lut = lut)
    }

    /** Convenience: validate the body of a file just read from disk. */
    fun validate(bytes: ByteArray): Result {
        if (bytes.size.toLong() > MAX_TEXT_BYTES) {
            return Result.Failure(
                category = FailureCategory.TooLarge,
                message = "LUT file exceeds the $MAX_TEXT_BYTES-byte limit (got ${bytes.size} bytes).",
                cause = null,
            )
        }
        return validate(bytes.toString(Charsets.UTF_8))
    }

    /**
     * Map an exception message from [LutPipeline.parseCube] to a structured
     * failure category so callers can branch on intent (e.g., the toast for
     * an unsupported size differs from the toast for a malformed body).
     */
    private fun categorize(message: String): FailureCategory {
        val lower = message.lowercase()
        return when {
            lower.contains("did not declare lut_3d_size") -> FailureCategory.MalformedHeader
            lower.contains("unsupported lut_3d_size") -> FailureCategory.UnsupportedSize
            lower.contains("unsupported 3dl shaper size") -> FailureCategory.UnsupportedSize
            lower.contains("lut_3d_size") -> FailureCategory.MalformedHeader
            lower.contains("lut_1d_size") -> FailureCategory.OneDLut
            lower.contains("domain_min") || lower.contains("domain_max") -> FailureCategory.NonUnitDomain
            lower.contains("expected rgb triple") -> FailureCategory.MalformedBody
            lower.contains("not a float") -> FailureCategory.MalformedBody
            lower.contains("cube body has") -> FailureCategory.SizeMismatch
            lower.contains("3dl body has") -> FailureCategory.SizeMismatch
            lower.contains("3dl shaper") -> FailureCategory.MalformedHeader
            lower.contains("3dl file contained no integer rows") -> FailureCategory.MalformedHeader
            lower.contains("3dl body row") -> FailureCategory.MalformedBody
            lower.contains("spi3d only supports 3-input") -> FailureCategory.OneDLut
            lower.contains("unsupported spi3d size") -> FailureCategory.UnsupportedSize
            lower.contains("spi3d only supports cubic luts") -> FailureCategory.UnsupportedSize
            lower.contains("spi3d header") -> FailureCategory.MalformedHeader
            lower.contains("spi3d dim line") -> FailureCategory.MalformedHeader
            lower.contains("spi3d size line") -> FailureCategory.MalformedHeader
            lower.contains("spi3d size_") -> FailureCategory.MalformedHeader
            lower.contains("spi3d file too short") -> FailureCategory.MalformedHeader
            lower.contains("spi3d body has") -> FailureCategory.SizeMismatch
            lower.contains("spi3d body row") -> FailureCategory.MalformedBody
            lower.contains("spi3d duplicate entry") -> FailureCategory.MalformedBody
            lower.contains("spi3d missing entry") -> FailureCategory.MalformedBody
            else -> FailureCategory.MalformedBody
        }
    }

    /** Recognized LUT text formats. */
    enum class Format { Cube, Dl3, Spi3d }

    /** Validation outcome - sealed so callers can pattern-match. */
    sealed class Result {
        data class Success(val lut: Lut3D) : Result()
        data class Failure(
            val category: FailureCategory,
            val message: String,
            val cause: Throwable?,
        ) : Result() {
            /** Short toast text - "Imported LUT rejected: <category>" + the message tail. */
            fun toastMessage(): String = "Imported LUT rejected (${category.toastLabel}): $message"
        }
    }

    /**
     * Categorical failure reason. The `toastLabel` is the short tag for
     * the user-facing toast; the human-readable detail lives in
     * [Result.Failure.message].
     */
    enum class FailureCategory(val toastLabel: String) {
        TooLarge("file too large"),
        MalformedHeader("malformed header"),
        MalformedBody("malformed body"),
        UnsupportedSize("unsupported size"),
        OneDLut("1D LUT"),
        NonUnitDomain("non-[0,1] domain"),
        SizeMismatch("size/body mismatch"),
        OutOfRange("out-of-range value"),
    }
}
