package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the AVIF / HEIF Item Property Association
 * box (`ipma`) per ISO/IEC 23008-12 §9.3.2 (which references back to
 * ISO/IEC 14496-12 §8.11.14 ItemPropertyAssociation).
 *
 * `ipma` is the linkage layer that maps each item in a HEIF / AVIF
 * file to the list of property indices in `ipco` that apply to that
 * item. Without `ipma`, every property box shipped in Rounds 17-20
 * (`colr`, `mdcv`, `clli`, `pasp`, `clap`, `irot`, `imir`, `pixi`)
 * would land in the file but no decoder would associate them with
 * any image item, so the AVIF muxer cannot produce a usable file
 * without this box.
 *
 * Wire format (per ISO/IEC 14496-12 §8.11.14 ItemPropertyAssociation,
 * which is a `FullBox('ipma', version, flags)`):
 *
 * ```
 * unsigned int(32) entry_count;
 * for (i = 0; i < entry_count; i++) {
 *     if (version < 1) {
 *         unsigned int(16) item_ID;
 *     } else {
 *         unsigned int(32) item_ID;
 *     }
 *     unsigned int(8) association_count;
 *     for (j = 0; j < association_count; j++) {
 *         bit(1) essential;
 *         if (flags & 1) {
 *             unsigned int(15) property_index;
 *         } else {
 *             unsigned int(7) property_index;
 *         }
 *     }
 * }
 * ```
 *
 * The two encoding switches are:
 *
 *  * **`version`** (`0` or `1`): controls the item-ID width.
 *    `version = 0` uses 16-bit item IDs (max `65535`); `version = 1`
 *    uses 32-bit item IDs (max `2^32 - 1`). Per HEIF best practice,
 *    `version = 1` is conservative for files with > 65535 items,
 *    but for an AVIF still image with one or two items `version = 0`
 *    saves 2 bytes per entry.
 *
 *  * **`flags & 1`** (the only meaningful bit in the FullBox flags):
 *    controls the property-index width. `0` uses a 7-bit property
 *    index (max `127`), packed alongside the `essential` bit into a
 *    single byte; `1` uses a 15-bit property index (max `32767`),
 *    packed alongside `essential` into 2 big-endian bytes. For an
 *    AVIF still with at most a handful of properties in `ipco`, the
 *    7-bit form (flags = 0) is the canonical pick.
 *
 * The `essential` bit (per ISO/IEC 14496-12 §8.11.14.3): when set,
 * the property is required for correct rendering — a decoder that
 * cannot understand the property MUST refuse to display the item.
 * For mandatory metadata like `colr` and `pixi` this should be
 * `true`; for optional / informational metadata like `irot` /
 * `imir` it is `false` per AVIF spec §6.5.10 / §6.5.12.
 *
 * Property indices are **1-based** per spec (index `0` is reserved
 * to mean "no property"). The Kotlin API enforces this at the
 * [Association] constructor via `require(propertyIndex >= 1)`.
 *
 * This module emits ONLY the FullBox *payload* (the bytes after the
 * 4-byte version+flags slot). The caller wraps with
 * `IsobmffBox.encodeFullBox("ipma", version, flags, payload)`.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object ItemPropertyAssociation {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type for `ipma`. */
    const val BOX_TYPE: String = "ipma"

    /**
     * Single-bit flag in the FullBox `flags` field that switches
     * property indices from 7-bit (one byte per association) to
     * 15-bit (two bytes per association). All other flag bits are
     * reserved zero per ISO/IEC 14496-12 §8.11.14.3.
     */
    const val FLAG_LARGE_PROPERTY_INDEX: Int = 0x000001

    /** Maximum property index when `flags & 1 == 0`. */
    const val MAX_SMALL_PROPERTY_INDEX: Int = 0x7F // 127

    /** Maximum property index when `flags & 1 == 1`. */
    const val MAX_LARGE_PROPERTY_INDEX: Int = 0x7FFF // 32767

    /** Maximum item ID when `version = 0`. */
    const val MAX_SMALL_ITEM_ID: Long = 0xFFFFL // 65535

    /** Maximum item ID when `version = 1`. */
    const val MAX_LARGE_ITEM_ID: Long = 0xFFFFFFFFL

    /**
     * Maximum association count per entry — the spec stores it in a
     * single byte (`unsigned int(8) association_count`).
     */
    const val MAX_ASSOCIATION_COUNT: Int = 0xFF // 255

    /**
     * A single (essential, propertyIndex) pair per ISO/IEC 14496-12
     * §8.11.14.3.
     *
     * `propertyIndex` is **1-based** per spec — index `0` is reserved
     * to mean "no property" and would silently corrupt the bitstream.
     * The constructor rejects `< 1` so a caller cannot accidentally
     * emit a zero index.
     */
    data class Association(
        val propertyIndex: Int,
        val essential: Boolean,
    ) {
        init {
            require(propertyIndex >= 1) {
                "propertyIndex must be >= 1 (1-based per ISO/IEC 14496-12 §8.11.14)"
            }
            require(propertyIndex <= MAX_LARGE_PROPERTY_INDEX) {
                "propertyIndex must be <= MAX_LARGE_PROPERTY_INDEX ($MAX_LARGE_PROPERTY_INDEX); got $propertyIndex"
            }
        }
    }

    /**
     * One `(itemId, [associations...])` row of the `ipma` table.
     *
     * `itemId` corresponds to a `iloc` / `iinf` item ID elsewhere in
     * the `meta` box; an AVIF still typically has two items (the
     * primary image item plus its `Exif` metadata item). Constructor
     * accepts `>= 0` and `<= MAX_LARGE_ITEM_ID`; the wire-format
     * width (16-bit vs 32-bit) is decided later by the chosen
     * `version`.
     */
    data class Entry(
        val itemId: Long,
        val associations: List<Association>,
    ) {
        init {
            require(itemId >= 0L) { "itemId must be >= 0; got $itemId" }
            require(itemId <= MAX_LARGE_ITEM_ID) {
                "itemId must be <= MAX_LARGE_ITEM_ID ($MAX_LARGE_ITEM_ID); got $itemId"
            }
            require(associations.isNotEmpty()) {
                "associations must be non-empty (an item with zero properties does not need an ipma entry)"
            }
            require(associations.size <= MAX_ASSOCIATION_COUNT) {
                "associations.size must be <= MAX_ASSOCIATION_COUNT ($MAX_ASSOCIATION_COUNT); got ${associations.size}"
            }
        }
    }

    /**
     * Pick the minimum `(version, flags)` combination that can encode
     * every entry without truncation.
     *
     *  * `version = 0` is selected if every itemId fits in 16 bits;
     *    otherwise `version = 1`.
     *  * `flags = 0` is selected if every property index fits in 7
     *    bits; otherwise `flags = FLAG_LARGE_PROPERTY_INDEX`.
     *
     * For an AVIF still with `<=` 65535 items and `<=` 127 properties
     * in `ipco`, this returns `(0, 0)` — the most compact form per
     * HEIF best practice.
     */
    fun chooseVersionAndFlags(entries: List<Entry>): Pair<Int, Int> {
        var version = 0
        var flags = 0
        for (entry in entries) {
            if (entry.itemId > MAX_SMALL_ITEM_ID) version = 1
            for (association in entry.associations) {
                if (association.propertyIndex > MAX_SMALL_PROPERTY_INDEX) {
                    flags = FLAG_LARGE_PROPERTY_INDEX
                }
            }
        }
        return version to flags
    }

    /**
     * Encode the `ipma` FullBox payload (the bytes after the 4-byte
     * version+flags slot). Caller wraps with
     * `IsobmffBox.encodeFullBox("ipma", version, flags, payload)`.
     *
     * Validates that every itemId / propertyIndex fits the chosen
     * encoding; throws [IllegalArgumentException] if not.
     */
    fun encodePayload(entries: List<Entry>, version: Int, flags: Int): ByteArray {
        require(version == 0 || version == 1) {
            "version must be 0 or 1 per ISO/IEC 14496-12 §8.11.14; got $version"
        }
        require(flags in 0..0xFFFFFF) {
            "flags must be a 24-bit unsigned value; got $flags"
        }
        val largeItemIds = version == 1
        val largePropertyIndex = (flags and FLAG_LARGE_PROPERTY_INDEX) != 0
        val maxItemId = if (largeItemIds) MAX_LARGE_ITEM_ID else MAX_SMALL_ITEM_ID
        val maxPropIndex = if (largePropertyIndex) MAX_LARGE_PROPERTY_INDEX else MAX_SMALL_PROPERTY_INDEX

        val out = ByteArrayOutputStream()
        writeUint32Be(out, entries.size)
        for (entry in entries) {
            require(entry.itemId <= maxItemId) {
                "itemId ${entry.itemId} exceeds the chosen version's max ($maxItemId); use version=1 to enable 32-bit item IDs"
            }
            if (largeItemIds) {
                writeUint32Be(out, entry.itemId.toInt())
            } else {
                writeUint16Be(out, entry.itemId.toInt())
            }
            out.write(entry.associations.size and 0xFF)
            for (association in entry.associations) {
                require(association.propertyIndex <= maxPropIndex) {
                    "propertyIndex ${association.propertyIndex} exceeds the chosen flags' max ($maxPropIndex); use flags |= FLAG_LARGE_PROPERTY_INDEX to enable 15-bit property indices"
                }
                val essentialBit = if (association.essential) 1 else 0
                if (largePropertyIndex) {
                    val packed = (essentialBit shl 15) or (association.propertyIndex and 0x7FFF)
                    writeUint16Be(out, packed)
                } else {
                    val packed = (essentialBit shl 7) or (association.propertyIndex and 0x7F)
                    out.write(packed and 0xFF)
                }
            }
        }
        return out.toByteArray()
    }

    /**
     * Decode an `ipma` FullBox payload back into the structured
     * `(version, flags, entries)` triple, per the same wire format
     * as [encodePayload]. Throws [IllegalArgumentException] when
     * the payload is truncated or claims a per-entry association
     * count that the remaining bytes cannot satisfy.
     */
    fun decodePayload(bytes: ByteArray, version: Int, flags: Int): List<Entry> {
        require(version == 0 || version == 1) {
            "version must be 0 or 1; got $version"
        }
        require(flags in 0..0xFFFFFF) {
            "flags must be a 24-bit unsigned value; got $flags"
        }
        val largeItemIds = version == 1
        val largePropertyIndex = (flags and FLAG_LARGE_PROPERTY_INDEX) != 0
        require(bytes.size >= 4) { "ipma payload must be at least 4 bytes (entry_count); got ${bytes.size}" }

        var cursor = 0
        val entryCount = readUint32Be(bytes, cursor)
        cursor += 4
        require(entryCount >= 0) { "ipma entry_count overflow: 2^31..2^32-1 entries are not supported on a 32-bit JVM int" }

        val entries = ArrayList<Entry>(entryCount)
        repeat(entryCount) {
            val itemId: Long = if (largeItemIds) {
                require(cursor + 4 <= bytes.size) { "ipma payload truncated at item_ID (large)" }
                val v = readUint32Be(bytes, cursor).toLong() and 0xFFFFFFFFL
                cursor += 4
                v
            } else {
                require(cursor + 2 <= bytes.size) { "ipma payload truncated at item_ID (small)" }
                val v = readUint16Be(bytes, cursor).toLong()
                cursor += 2
                v
            }
            require(cursor + 1 <= bytes.size) { "ipma payload truncated at association_count" }
            val associationCount = bytes[cursor].toInt() and 0xFF
            cursor += 1
            val associations = ArrayList<Association>(associationCount)
            repeat(associationCount) {
                if (largePropertyIndex) {
                    require(cursor + 2 <= bytes.size) { "ipma payload truncated at association (large)" }
                    val packed = readUint16Be(bytes, cursor)
                    cursor += 2
                    val essential = (packed and 0x8000) != 0
                    val propertyIndex = packed and 0x7FFF
                    associations += Association(propertyIndex, essential)
                } else {
                    require(cursor + 1 <= bytes.size) { "ipma payload truncated at association (small)" }
                    val packed = bytes[cursor].toInt() and 0xFF
                    cursor += 1
                    val essential = (packed and 0x80) != 0
                    val propertyIndex = packed and 0x7F
                    associations += Association(propertyIndex, essential)
                }
            }
            entries += Entry(itemId, associations)
        }
        return entries
    }

    /**
     * Convenience: encode the entries with the most compact
     * `(version, flags)` combination via [chooseVersionAndFlags] and
     * wrap the result with `IsobmffBox.encodeFullBox` so the caller
     * gets a complete, mux-ready `ipma` box (header + payload) in
     * one call.
     */
    fun encodeBox(entries: List<Entry>): ByteArray {
        val (version, flags) = chooseVersionAndFlags(entries)
        val payload = encodePayload(entries, version, flags)
        return IsobmffBox.encodeFullBox(BOX_TYPE, version, flags, payload)
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

    private fun readUint16Be(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readUint32Be(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}
