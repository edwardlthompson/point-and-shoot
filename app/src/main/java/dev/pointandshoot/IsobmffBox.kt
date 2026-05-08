package dev.pointandshoot

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Pure-data writer for the canonical ISOBMFF box envelope per
 * ISO/IEC 14496-12 §4.2 Object Structure.
 *
 * Every metadata payload Point & Shoot has built so far - `colr` (Round 17),
 * `mdcv` / `clli` (Round 18), `pasp` / `clap` (Round 19), `irot` / `imir` /
 * `pixi` (Round 20) - returns the *body* of an ISOBMFF box, NOT the box
 * itself. The box itself is the body wrapped in the canonical 8-byte
 * `(size, type)` header (or a 16-byte `(size=1, type, large_size)` header
 * for payloads > 4 GiB - 8 bytes). This module is the missing wrapper.
 *
 * Two box flavors are shipped:
 *
 *  * **Plain box** ([writeBox], [encodeBox]): just `size + type + payload`.
 *    Used for `mdat`, `colr`, `pasp`, `clap`, `irot`, `imir`, `pixi`,
 *    `mdcv`, `clli`, and most container boxes (`moov`, `trak`, `meta`'s
 *    children once they're framed by their parent's `meta` `FullBox`).
 *
 *  * **FullBox** ([writeFullBox], [encodeFullBox]): `size + type + version
 *    + flags + payload`. Per ISO/IEC 14496-12 §4.2 the FullBox flavor adds
 *    a 1-byte `version` and a 24-bit big-endian `flags` field BEFORE the
 *    payload. Used for `meta`, `iloc`, `iinf`, `pitm`, `ipma`, `ipco`'s
 *    parent `iprp`, the various `*sd` boxes in `stbl`, etc.
 *
 * The 4-byte `type` MUST be exactly 4 ASCII bytes per spec. Lower-case
 * ASCII is conventional for standard boxes (`colr`, `pasp`, `meta`, ...);
 * upper-case is reserved for vendor extensions. We accept both via
 * [boxType] but reject any byte outside the printable ASCII range
 * `[0x20, 0x7E]` AND any string whose length is not exactly 4.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object IsobmffBox {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 8-byte plain-box header size (`uint32 size + 4-byte type`). */
    const val PLAIN_HEADER_SIZE: Int = 8

    /**
     * Canonical 16-byte large-box header size
     * (`uint32 size = 1 + 4-byte type + uint64 large_size`).
     */
    const val LARGE_HEADER_SIZE: Int = 16

    /** Additional 4 bytes a FullBox prepends to the payload (`version + 3-byte flags`). */
    const val FULLBOX_VERSION_FLAGS_SIZE: Int = 4

    /**
     * Threshold above which we MUST use the large-size escape: any total
     * box size that does not fit in `uint32`. Per spec the literal value
     * `1` in the `size` field signals "read 64 bits of `large_size`
     * after the type"; we use that whenever the full box (header + body)
     * would exceed `0xFFFFFFFF` bytes.
     *
     * Stored as `Long` so callers can compare against a `Long` payload
     * length without overflowing.
     */
    const val LARGE_SIZE_THRESHOLD: Long = 0xFFFFFFFFL

    /**
     * Validate a 4-byte ISOBMFF box type marker. Per ISO/IEC 14496-12
     * §4.2 a box type is exactly 4 bytes; the conventional way to write
     * it is as 4 printable ASCII characters (e.g. `"colr"`, `"pasp"`,
     * `"mdat"`).
     *
     * Returns the type as a 4-byte `ByteArray` ready to write to the
     * stream. Throws [IllegalArgumentException] when [type] is not
     * exactly 4 ASCII characters.
     *
     * Why we reject non-ASCII: in principle the spec allows any 4 bytes,
     * but in practice every Point & Shoot box is named after a published
     * spec (which uses ASCII). Restricting to printable ASCII catches
     * caller mistakes (e.g. accidentally passing a UTF-8 string with an
     * emoji or a multi-byte character) at the validation boundary
     * instead of producing a bitstream a downstream tool will silently
     * mis-parse.
     */
    fun boxType(type: String): ByteArray {
        require(type.length == 4) {
            "ISOBMFF box type must be exactly 4 ASCII characters, got '${type}' (length=${type.length})"
        }
        val bytes = ByteArray(4)
        for (i in 0..3) {
            val ch = type[i].code
            require(ch in 0x20..0x7E) {
                "ISOBMFF box type must be printable ASCII (0x20..0x7E), got 0x${ch.toString(16)} at index $i in '${type}'"
            }
            bytes[i] = ch.toByte()
        }
        return bytes
    }

    /**
     * Write a plain ISOBMFF box (`size + type + payload`) to [out].
     *
     * If `PLAIN_HEADER_SIZE + payload.size <= LARGE_SIZE_THRESHOLD` the
     * canonical 8-byte header is used; otherwise the large-size escape
     * (`size = 1`, then `large_size: uint64`) is used per spec §4.2.
     */
    fun writeBox(out: OutputStream, type: String, payload: ByteArray) {
        val typeBytes = boxType(type)
        val totalSizePlain = PLAIN_HEADER_SIZE.toLong() + payload.size.toLong()
        if (totalSizePlain <= LARGE_SIZE_THRESHOLD) {
            writeUint32Be(out, totalSizePlain.toInt())
            out.write(typeBytes)
            out.write(payload)
        } else {
            val totalSizeLarge = LARGE_HEADER_SIZE.toLong() + payload.size.toLong()
            writeUint32Be(out, 1)
            out.write(typeBytes)
            writeUint64Be(out, totalSizeLarge)
            out.write(payload)
        }
    }

    /**
     * Convenience: encode a plain ISOBMFF box as a `ByteArray`.
     * Equivalent to `ByteArrayOutputStream().also { writeBox(it, type, payload) }.toByteArray()`.
     */
    fun encodeBox(type: String, payload: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        writeBox(bos, type, payload)
        return bos.toByteArray()
    }

    /**
     * Write a FullBox (`size + type + version + 3-byte flags + payload`)
     * to [out]. Version must fit in `uint8` (`[0, 255]`); flags must fit
     * in `uint24` (`[0, 0xFFFFFF]`).
     *
     * The size field accounts for the FullBox extension bytes
     * automatically.
     */
    fun writeFullBox(
        out: OutputStream,
        type: String,
        version: Int,
        flags: Int,
        payload: ByteArray,
    ) {
        require(version in 0..255) { "FullBox version must be in [0, 255], got $version" }
        require(flags in 0..0xFFFFFF) { "FullBox flags must be in [0, 0xFFFFFF], got 0x${flags.toString(16)}" }
        val typeBytes = boxType(type)
        val bodySize = FULLBOX_VERSION_FLAGS_SIZE.toLong() + payload.size.toLong()
        val totalSizePlain = PLAIN_HEADER_SIZE.toLong() + bodySize
        if (totalSizePlain <= LARGE_SIZE_THRESHOLD) {
            writeUint32Be(out, totalSizePlain.toInt())
            out.write(typeBytes)
        } else {
            val totalSizeLarge = LARGE_HEADER_SIZE.toLong() + bodySize
            writeUint32Be(out, 1)
            out.write(typeBytes)
            writeUint64Be(out, totalSizeLarge)
        }
        out.write(version and 0xFF)
        out.write((flags ushr 16) and 0xFF)
        out.write((flags ushr 8) and 0xFF)
        out.write(flags and 0xFF)
        out.write(payload)
    }

    /**
     * Convenience: encode a FullBox as a `ByteArray`.
     */
    fun encodeFullBox(type: String, version: Int, flags: Int, payload: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        writeFullBox(bos, type, version, flags, payload)
        return bos.toByteArray()
    }

    /**
     * Convenience: concatenate several pre-encoded child boxes into a
     * single payload. The result is suitable as the `payload` argument
     * to [writeBox] / [encodeBox] for container boxes (`moov`, `trak`,
     * `iprp`, ...).
     *
     * Total size is computed up-front so the underlying stream is sized
     * exactly once.
     */
    fun concatPayloads(payloads: List<ByteArray>): ByteArray {
        var total = 0L
        for (p in payloads) total += p.size.toLong()
        require(total <= Int.MAX_VALUE) {
            "concatPayloads total size $total exceeds Int.MAX_VALUE; build the bitstream incrementally with writeBox(stream, ...)"
        }
        val out = ByteArray(total.toInt())
        var off = 0
        for (p in payloads) {
            System.arraycopy(p, 0, out, off, p.size)
            off += p.size
        }
        return out
    }

    private fun writeUint32Be(out: OutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeUint64Be(out: OutputStream, value: Long) {
        out.write(((value ushr 56) and 0xFFL).toInt())
        out.write(((value ushr 48) and 0xFFL).toInt())
        out.write(((value ushr 40) and 0xFFL).toInt())
        out.write(((value ushr 32) and 0xFFL).toInt())
        out.write(((value ushr 24) and 0xFFL).toInt())
        out.write(((value ushr 16) and 0xFFL).toInt())
        out.write(((value ushr 8) and 0xFFL).toInt())
        out.write((value and 0xFFL).toInt())
    }
}
