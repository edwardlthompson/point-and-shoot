package dev.pointandshoot

/**
 * Pure-data byte-layout formatter for the AVIF (`avif`/`avis`) `colr`
 * box's `nclx` colour-type payload, plus the parallel `prof`-typed
 * payload for ICC-profile-tagged AVIFs.
 *
 * Reference: ISO/IEC 14496-12 (ISOBMFF) §8.4.3 / §8.5.4 + ISO/IEC
 * 23000-22 (AVIF spec) §3.5 + Apple's `NCLX` flavor used by every
 * mainstream AVIF encoder (libavif, dav1d, ffmpeg).
 *
 * The `colr` box layout for the `nclx` colour-type is exactly:
 *
 *     +----+----+----+----+----+----+----+----+----+----+----+
 *     | 'n'|'c' |'l' |'x' |  cp[hi]| cp[lo] | tc[hi]| tc[lo]|
 *     +----+----+----+----+----+----+----+----+----+----+----+
 *     | mc[hi]| mc[lo]|  flags  |
 *     +----+----+----+----+----+
 *
 * Total 11 bytes:
 *   * 4-byte ASCII colour-type ('nclx')
 *   * 16-bit big-endian `colour_primaries`
 *   * 16-bit big-endian `transfer_characteristics`
 *   * 16-bit big-endian `matrix_coefficients`
 *   * 8-bit `flags`: bit 7 = `full_range_flag`; bits 6..0 = reserved (0)
 *
 * This module produces ONLY the payload that goes inside the `colr`
 * box - not the BMFF box header (size + 'colr'). The capture engine's
 * AVIF muxer wraps it with the standard BMFF size + box-type prefix.
 *
 * The `prof`-typed `colr` box wraps a raw ICC profile blob (variable
 * length); [encodeProfPayload] is the pass-through formatter for that
 * case, with a small length sanity check.
 *
 * No Android imports - safe for unit testing on the JVM.
 */
object AvifColrPayload {

    /** Bumped only when the byte-layout schema changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** ASCII bytes for the `nclx` colour type marker. */
    val NCLX_TYPE: ByteArray = byteArrayOf('n'.code.toByte(), 'c'.code.toByte(), 'l'.code.toByte(), 'x'.code.toByte())

    /** ASCII bytes for the `prof` colour type marker (ICC profile). */
    val PROF_TYPE: ByteArray = byteArrayOf('p'.code.toByte(), 'r'.code.toByte(), 'o'.code.toByte(), 'f'.code.toByte())

    /** The total byte length of an `nclx` payload (4 + 2 + 2 + 2 + 1 = 11). */
    const val NCLX_PAYLOAD_LENGTH: Int = 11

    /**
     * Encode an `nclx`-flavored `colr` payload from the given [Cicp]
     * triple. Returns exactly [NCLX_PAYLOAD_LENGTH] bytes.
     *
     * The function is total: any 8-bit unsigned value is accepted for
     * the three CICP fields (the [Cicp] init has already validated the
     * `[0, 255]` range). Big-endian per the BMFF spec.
     */
    fun encodeNclxPayload(cicp: Cicp): ByteArray {
        val out = ByteArray(NCLX_PAYLOAD_LENGTH)
        // colour_type: 'nclx'
        System.arraycopy(NCLX_TYPE, 0, out, 0, 4)
        // colour_primaries (16-bit BE)
        out[4] = ((cicp.colourPrimaries ushr 8) and 0xFF).toByte()
        out[5] = (cicp.colourPrimaries and 0xFF).toByte()
        // transfer_characteristics (16-bit BE)
        out[6] = ((cicp.transferCharacteristics ushr 8) and 0xFF).toByte()
        out[7] = (cicp.transferCharacteristics and 0xFF).toByte()
        // matrix_coefficients (16-bit BE)
        out[8] = ((cicp.matrixCoefficients ushr 8) and 0xFF).toByte()
        out[9] = (cicp.matrixCoefficients and 0xFF).toByte()
        // flags: bit 7 = full_range_flag, bits 6..0 = 0
        out[10] = if (cicp.videoFullRangeFlag) 0x80.toByte() else 0x00
        return out
    }

    /**
     * Decode an `nclx` payload back to a [Cicp]. Returns null for the
     * non-`nclx` colour types (caller can dispatch to [decodeProfPayload]
     * for `prof`); throws [IllegalArgumentException] for malformed
     * lengths.
     *
     * The decode reads bits 6..0 of the trailing flags byte and only
     * carries `bit 7` into [Cicp.videoFullRangeFlag] - reserved bits
     * are silently dropped per ISO/IEC 14496-12 (some pre-spec encoders
     * emit non-zero reserved bits).
     */
    fun decodeNclxPayload(bytes: ByteArray): Cicp? {
        require(bytes.size == NCLX_PAYLOAD_LENGTH) {
            "nclx payload must be exactly $NCLX_PAYLOAD_LENGTH bytes (was ${bytes.size})"
        }
        if (!bytes.copyOfRange(0, 4).contentEquals(NCLX_TYPE)) {
            return null
        }
        val cp = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        val tc = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        val mc = ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
        val full = (bytes[10].toInt() and 0x80) != 0
        return Cicp(
            colourPrimaries = cp,
            transferCharacteristics = tc,
            matrixCoefficients = mc,
            videoFullRangeFlag = full,
        )
    }

    /**
     * Encode a `prof`-flavored `colr` payload by prefixing the ICC
     * profile bytes with the 4-byte 'prof' colour-type marker.
     *
     * The capture engine should pass the raw ICC profile (4-byte BE
     * length header + profile-version + ... per ICC.1:2010). This
     * function does NOT validate the profile - upstream is expected
     * to source it from a known good provider (LittleCMS, Skia,
     * vendored BT.2020 PQ profile, etc.).
     */
    fun encodeProfPayload(iccProfileBytes: ByteArray): ByteArray {
        require(iccProfileBytes.isNotEmpty()) { "ICC profile must not be empty" }
        val out = ByteArray(4 + iccProfileBytes.size)
        System.arraycopy(PROF_TYPE, 0, out, 0, 4)
        System.arraycopy(iccProfileBytes, 0, out, 4, iccProfileBytes.size)
        return out
    }

    /**
     * Pull the raw ICC profile bytes out of a `prof`-typed `colr`
     * payload. Returns null if the type marker is not 'prof'; throws
     * for malformed lengths.
     */
    fun decodeProfPayload(bytes: ByteArray): ByteArray? {
        require(bytes.size >= 4) { "prof payload must be at least 4 bytes (was ${bytes.size})" }
        if (!bytes.copyOfRange(0, 4).contentEquals(PROF_TYPE)) {
            return null
        }
        return bytes.copyOfRange(4, bytes.size)
    }
}
