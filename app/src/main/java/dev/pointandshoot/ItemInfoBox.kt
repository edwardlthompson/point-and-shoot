package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the AVIF / HEIF Item Information Box
 * (`iinf`) FullBox per ISO/IEC 14496-12 §8.11.6.
 *
 * `iinf` is the container that holds every [ItemInfoEntry] (`infe`)
 * declaration in a HEIF / AVIF file. It is a FullBox whose only
 * payload is a count followed by the concatenation of pre-encoded
 * `infe` boxes.
 *
 * Wire format (per ISO/IEC 14496-12 §8.11.6):
 *
 * ```
 * aligned(8) class ItemInfoBox extends FullBox('iinf', version, 0) {
 *     if (version == 0) {
 *         unsigned int(16) entry_count;
 *     } else {
 *         unsigned int(32) entry_count;
 *     }
 *     ItemInfoEntry entries[entry_count];
 * }
 * ```
 *
 * `version = 0` (16-bit count) handles up to 65535 items per file —
 * which covers every realistic still-image use case the engine is
 * going to ship. `version = 1` is provided for spec completeness.
 *
 * This module emits ONLY the FullBox *payload* (the bytes after the
 * 4-byte version+flags slot). The caller wraps with
 * `IsobmffBox.encodeFullBox("iinf", version, flags = 0, payload)`,
 * or uses the [encodeBox] convenience that picks the minimum
 * version automatically.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object ItemInfoBox {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "iinf"

    /** Maximum entry_count encodable with `version = 0`. */
    const val MAX_SMALL_ENTRY_COUNT: Long = 0xFFFFL

    /** Maximum entry_count encodable with `version = 1`. */
    const val MAX_LARGE_ENTRY_COUNT: Long = 0xFFFFFFFFL

    /**
     * Pick the minimum version that can encode [entryCount] without
     * truncation. Returns `0` for counts in `[0, 65535]` and `1`
     * for counts in `(65535, 0xFFFFFFFF]`.
     */
    fun chooseVersion(entryCount: Int): Int {
        require(entryCount >= 0) { "entryCount must be >= 0; got $entryCount" }
        return if (entryCount.toLong() > MAX_SMALL_ENTRY_COUNT) 1 else 0
    }

    /**
     * Encode the `iinf` FullBox payload (the bytes after the 4-byte
     * version+flags slot): `entry_count` (uint16/uint32 big-endian)
     * followed by the concatenation of pre-encoded `infe` boxes.
     *
     * Each entry in [infeBoxes] must already include its own
     * canonical 8-byte ISOBMFF FullBox header (typical call site is
     * [ItemInfoEntry.encodeBox]).
     *
     * Caller wraps with
     * `IsobmffBox.encodeFullBox("iinf", version, 0, payload)`.
     *
     * @throws IllegalArgumentException on invalid version, or when
     *     the entry count exceeds the chosen version's capacity, or
     *     when any `infe` box is shorter than the canonical 8-byte
     *     ISOBMFF header.
     */
    fun encodePayload(infeBoxes: List<ByteArray>, version: Int): ByteArray {
        require(version == 0 || version == 1) {
            "version must be 0 or 1 per ISO/IEC 14496-12 §8.11.6; got $version"
        }
        val maxCount = if (version == 0) MAX_SMALL_ENTRY_COUNT else MAX_LARGE_ENTRY_COUNT
        require(infeBoxes.size.toLong() <= maxCount) {
            "infeBoxes.size ${infeBoxes.size} exceeds version=$version capacity ($maxCount); use version=1"
        }
        for ((idx, infe) in infeBoxes.withIndex()) {
            require(infe.size >= IsobmffBox.PLAIN_HEADER_SIZE) {
                "infeBoxes[$idx] must include the canonical ${IsobmffBox.PLAIN_HEADER_SIZE}-byte ISOBMFF header; got ${infe.size}"
            }
        }
        val out = ByteArrayOutputStream()
        if (version == 0) {
            writeUint16Be(out, infeBoxes.size)
        } else {
            writeUint32Be(out, infeBoxes.size)
        }
        for (infe in infeBoxes) {
            out.writeBytes(infe)
        }
        return out.toByteArray()
    }

    /**
     * Convenience: pick the minimum version via [chooseVersion],
     * encode the payload, and wrap with `IsobmffBox.encodeFullBox`
     * so the caller gets a complete, mux-ready `iinf` box (header
     * + payload) in one call. `flags` is always `0` per spec.
     */
    fun encodeBox(infeBoxes: List<ByteArray>): ByteArray {
        val version = chooseVersion(infeBoxes.size)
        val payload = encodePayload(infeBoxes, version)
        return IsobmffBox.encodeFullBox(BOX_TYPE, version, flags = 0, payload = payload)
    }

    private fun writeUint16Be(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeUint32Be(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
