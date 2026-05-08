package dev.pointandshoot

import java.util.Locale
import kotlin.math.floor

/**
 * Pure-data parser, serializer, and CPU apply path for 3D `.cube` LUTs per
 * BUILD_PLAN §7 ("Phase 4"). No Android dependencies; safe for unit testing.
 *
 * Adobe Cube spec (publicly documented, free to implement):
 * `LUT_3D_SIZE <int>`, optional `TITLE`, optional `DOMAIN_MIN` / `DOMAIN_MAX`
 * (we always assume `[0, 1]`), and one RGB triple per line. R varies fastest,
 * then G, then B. Lines starting with `#` are comments.
 *
 * The CPU apply path is the still-capture lane (post-encode, pre-compression);
 * the live-preview / video path will use a GLES `sampler3D` with hardware
 * trilinear interpolation. Keeping this Kotlin reference in lock-step with
 * the shader is what `LutPipelineTest` validates - if the GPU and CPU disagree
 * by more than 1 LSB on 8-bit, the still and video outputs would drift.
 */
object LutPipeline {

    /**
     * Trilinear interpolation of a single RGB sample through [lut].
     *
     * Inputs are clamped to `[0, 1]` before lookup so out-of-gamut input
     * cannot index off the end of the cube. Outputs are returned exactly as
     * the LUT produced them (no post-clamp) so a calibration LUT can briefly
     * round-trip out-of-gamut values without losing precision; the encoder
     * stage clamps to its own output range.
     *
     * Allocates a fresh `FloatArray(3)` per call; if you need to apply this
     * to a whole image plane in a hot loop, prefer [applyTrilinearInto].
     */
    fun applyTrilinear(rgb: FloatArray, lut: Lut3D): FloatArray {
        val out = FloatArray(3)
        applyTrilinearInto(rgb[0], rgb[1], rgb[2], lut, out, offset = 0)
        return out
    }

    /**
     * Allocation-free variant: writes the trilinear sample into [out] starting
     * at [offset]. Used by hot paths (per-pixel image apply) so we don't
     * thrash the GC in a 12 MP loop.
     */
    fun applyTrilinearInto(
        rIn: Float, gIn: Float, bIn: Float,
        lut: Lut3D,
        out: FloatArray,
        offset: Int = 0,
    ) {
        require(rIn.isFinite() && gIn.isFinite() && bIn.isFinite()) {
            "RGB sample contains NaN or Infinity (r=$rIn g=$gIn b=$bIn)"
        }
        require(out.size >= offset + 3) {
            "out must have room for 3 floats at offset=$offset (size was ${out.size})"
        }
        val maxIdx = lut.size - 1
        val r = rIn.coerceIn(0f, 1f) * maxIdx
        val g = gIn.coerceIn(0f, 1f) * maxIdx
        val b = bIn.coerceIn(0f, 1f) * maxIdx

        val r0 = floor(r).toInt().coerceAtMost(maxIdx)
        val g0 = floor(g).toInt().coerceAtMost(maxIdx)
        val b0 = floor(b).toInt().coerceAtMost(maxIdx)
        val r1 = (r0 + 1).coerceAtMost(maxIdx)
        val g1 = (g0 + 1).coerceAtMost(maxIdx)
        val b1 = (b0 + 1).coerceAtMost(maxIdx)
        val rd = r - r0
        val gd = g - g0
        val bd = b - b0

        for (ch in 0..2) {
            val c000 = sampleChannel(lut, r0, g0, b0, ch)
            val c100 = sampleChannel(lut, r1, g0, b0, ch)
            val c010 = sampleChannel(lut, r0, g1, b0, ch)
            val c110 = sampleChannel(lut, r1, g1, b0, ch)
            val c001 = sampleChannel(lut, r0, g0, b1, ch)
            val c101 = sampleChannel(lut, r1, g0, b1, ch)
            val c011 = sampleChannel(lut, r0, g1, b1, ch)
            val c111 = sampleChannel(lut, r1, g1, b1, ch)

            val c00 = c000 * (1 - rd) + c100 * rd
            val c01 = c001 * (1 - rd) + c101 * rd
            val c10 = c010 * (1 - rd) + c110 * rd
            val c11 = c011 * (1 - rd) + c111 * rd
            val c0 = c00 * (1 - gd) + c10 * gd
            val c1 = c01 * (1 - gd) + c11 * gd
            out[offset + ch] = c0 * (1 - bd) + c1 * bd
        }
    }

    private fun sampleChannel(lut: Lut3D, r: Int, g: Int, b: Int, channel: Int): Float {
        val idx = ((b * lut.size + g) * lut.size + r) * 3 + channel
        return lut.samples[idx]
    }

    /**
     * Parse Adobe Cube text into a [Lut3D]. Tolerates blank lines, comments,
     * `TITLE`, and `DOMAIN_MIN` / `DOMAIN_MAX` (we always assume the [0, 1]
     * domain - non-default domains are rejected so we cannot silently
     * mis-apply a Log-encoded LUT).
     */
    fun parseCube(text: String): Lut3D {
        var size = -1
        val triples = mutableListOf<Float>()
        var lineNo = 0
        for (raw in text.lineSequence()) {
            lineNo += 1
            val stripped = raw.substringBefore('#').trim()
            if (stripped.isEmpty()) continue
            val tokens = stripped.split(Regex("\\s+"))
            when {
                tokens[0].equals("TITLE", ignoreCase = true) -> Unit
                tokens[0].equals("LUT_3D_SIZE", ignoreCase = true) -> {
                    require(tokens.size >= 2) { "line $lineNo: LUT_3D_SIZE missing value" }
                    size = tokens[1].toIntOrNull()
                        ?: error("line $lineNo: LUT_3D_SIZE expects integer (got '${tokens[1]}')")
                }
                tokens[0].equals("LUT_1D_SIZE", ignoreCase = true) -> {
                    error("line $lineNo: LUT_1D_SIZE not supported (we only accept 3D LUTs)")
                }
                tokens[0].equals("DOMAIN_MIN", ignoreCase = true) -> {
                    requireDomain(tokens, expected = 0f, lineNo = lineNo, name = "DOMAIN_MIN")
                }
                tokens[0].equals("DOMAIN_MAX", ignoreCase = true) -> {
                    requireDomain(tokens, expected = 1f, lineNo = lineNo, name = "DOMAIN_MAX")
                }
                else -> {
                    require(tokens.size == 3) {
                        "line $lineNo: expected RGB triple (3 floats), got '$stripped'"
                    }
                    for (t in tokens) {
                        triples.add(
                            t.toFloatOrNull()
                                ?: error("line $lineNo: '$t' is not a float"),
                        )
                    }
                }
            }
        }
        require(size > 0) { "cube file did not declare LUT_3D_SIZE" }
        require(size in Lut3D.SUPPORTED_SIZES) {
            "Unsupported LUT_3D_SIZE=$size; allowed: ${Lut3D.SUPPORTED_SIZES}"
        }
        val expected = size * size * size * 3
        require(triples.size == expected) {
            "cube body has ${triples.size / 3} samples, expected ${expected / 3} for size=$size"
        }
        return Lut3D(size, triples.toFloatArray())
    }

    /**
     * Parse Autodesk Lustre `.3dl` (Mesh) text into a [Lut3D] per
     * BUILD_PLAN §7 ("`.3dl` import path"). The Autodesk format is:
     *
     *   1. Optional `#`-comment lines.
     *   2. A **shaper** line: N integers forming a uniform ramp from 0
     *      to `(2 ^ outputBits) - 1`. The bit depth is inferred from the
     *      shaper's max value (1023 -> 10-bit, 4095 -> 12-bit, etc.).
     *   3. **N^3 body lines**, each carrying one RGB integer triple.
     *      Per the Autodesk specification, **R varies fastest**, then G,
     *      then B (same convention as `.cube`).
     *
     * Integer values are normalized to `[0, 1]` floats by dividing by the
     * inferred max. Out-of-range values are NOT clamped - the validator
     * (`LutImportValidator`) is the single place that rejects values
     * outside `[ALLOWED_MIN, ALLOWED_MAX]` for both `.cube` and `.3dl`.
     *
     * The shaper ramp is checked for uniformity to within 1 step of
     * tolerance: a non-uniform shaper means the file is encoding a
     * `Log` or other non-linear domain mapping that we cannot apply
     * verbatim through a uniform `[0, 1]` 3D-texture sampler. Such files
     * are rejected with a clear error.
     */
    fun parseDl3(text: String): Lut3D {
        val tokens = mutableListOf<List<String>>()
        var lineNo = 0
        for (raw in text.lineSequence()) {
            lineNo += 1
            val stripped = raw.substringBefore('#').trim()
            if (stripped.isEmpty()) continue
            // Autodesk's docs allow keyword lines like "Mesh <inBits>
            // <outBits>" before the shaper. We tolerate any leading line
            // that has non-integer tokens by treating it as a header
            // hint that we ignore.
            val parts = stripped.split(Regex("\\s+"))
            if (parts.all { it.toIntOrNull() != null }) {
                tokens.add(parts)
            }
            // else: header / metadata line, ignored.
        }
        require(tokens.isNotEmpty()) { "3dl file contained no integer rows" }

        // The first all-integer row is the shaper; subsequent rows are the body.
        val shaper = tokens[0].map { it.toInt() }
        val body = tokens.drop(1)

        val size = shaper.size
        require(size in Lut3D.SUPPORTED_SIZES) {
            "Unsupported 3DL shaper size=$size; allowed: ${Lut3D.SUPPORTED_SIZES}"
        }
        require(shaper.first() == 0) {
            "3DL shaper must start at 0 (got ${shaper.first()})"
        }
        val maxValue = shaper.last()
        require(maxValue > 0) {
            "3DL shaper must end with a positive max value (got $maxValue)"
        }
        // Uniform ramp: consecutive deltas must agree to within 1 (the
        // ramp can only be approximately uniform when (maxValue / (size - 1))
        // is fractional, e.g. 4095 / 16 = 255.9375 -> deltas of 256 and 255).
        val nominalStep = maxValue.toDouble() / (size - 1).toDouble()
        for (i in 1 until size) {
            val delta = shaper[i] - shaper[i - 1]
            val deviation = kotlin.math.abs(delta - nominalStep)
            require(deviation <= 1.0 + 1e-6) {
                "3DL shaper is non-uniform at index $i: delta=$delta, expected ~$nominalStep " +
                    "(non-linear shapers are not supported - re-bake to a [0, 1] domain first)"
            }
        }
        require(body.size.toLong() == size.toLong() * size.toLong() * size.toLong()) {
            "3DL body has ${body.size} rows, expected ${size * size * size} for size=$size"
        }

        val samples = FloatArray(size * size * size * 3)
        val invMax = 1f / maxValue.toFloat()
        for ((idx, row) in body.withIndex()) {
            require(row.size == 3) {
                "3DL body row $idx must have 3 integers (got ${row.size})"
            }
            val r = row[0].toInt()
            val g = row[1].toInt()
            val b = row[2].toInt()
            samples[idx * 3] = r * invMax
            samples[idx * 3 + 1] = g * invMax
            samples[idx * 3 + 2] = b * invMax
        }
        return Lut3D(size, samples)
    }

    private fun requireDomain(tokens: List<String>, expected: Float, lineNo: Int, name: String) {
        require(tokens.size >= 4) { "line $lineNo: $name expects 3 floats" }
        for (i in 1..3) {
            val v = tokens[i].toFloatOrNull()
                ?: error("line $lineNo: $name component $i is not a float ('${tokens[i]}')")
            require(kotlin.math.abs(v - expected) < 1e-4f) {
                "line $lineNo: $name must be $expected per channel (we only support [0, 1] domain); got $v"
            }
        }
    }

    /**
     * Serialize a [Lut3D] to Adobe Cube text. The output round-trips through
     * [parseCube] exactly (modulo float-formatting precision); pinned at 6
     * decimal places (sufficient for an 8-bit display gamut).
     */
    fun serializeCube(lut: Lut3D, title: String? = null): String {
        val sb = StringBuilder()
        if (!title.isNullOrEmpty()) sb.appendLine("TITLE \"${escapeTitle(title)}\"")
        sb.appendLine("LUT_3D_SIZE ${lut.size}")
        sb.appendLine("DOMAIN_MIN 0.000000 0.000000 0.000000")
        sb.appendLine("DOMAIN_MAX 1.000000 1.000000 1.000000")
        for (b in 0 until lut.size) {
            for (g in 0 until lut.size) {
                for (r in 0 until lut.size) {
                    val idx = ((b * lut.size + g) * lut.size + r) * 3
                    sb.append(formatFloat(lut.samples[idx])).append(' ')
                    sb.append(formatFloat(lut.samples[idx + 1])).append(' ')
                    sb.appendLine(formatFloat(lut.samples[idx + 2]))
                }
            }
        }
        return sb.toString()
    }

    private fun escapeTitle(title: String): String =
        title.replace("\"", "'").replace('\n', ' ').replace('\r', ' ')

    private fun formatFloat(v: Float): String =
        String.format(Locale.US, "%.6f", v)
}
