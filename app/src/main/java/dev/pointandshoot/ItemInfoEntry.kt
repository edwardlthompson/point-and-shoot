package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the AVIF / HEIF Item Info Entry (`infe`)
 * FullBox per ISO/IEC 14496-12 §8.11.6.
 *
 * `infe` declares the existence and *type* of a single item in a
 * HEIF / AVIF file. Each `infe` is a FullBox that lives inside the
 * `iinf` container. For an AVIF still image, the engine ships at
 * minimum two `infe` entries:
 *
 *  * One for the primary image item with `item_type = "av01"` (AV1
 *    image item per ISO/IEC 23008-12 §6.4).
 *  * Optionally one for the EXIF metadata item with `item_type =
 *    "Exif"` (shared with HEIF; the actual EXIF blob lives in
 *    `mdat` and is referenced via `iref` `cdsc` and `iloc`).
 *
 * Wire format (per ISO/IEC 14496-12 §8.11.6 *Item Information Entry*,
 * version >= 2 — the only versions Point & Shoot emits since v0/v1
 * are deprecated and ISO/IEC 14496-12:2020 marks them "shall not be
 * used"):
 *
 * ```
 * aligned(8) class ItemInfoEntry extends FullBox('infe', version, 0) {
 *     if (version == 2) {
 *         unsigned int(16) item_ID;
 *     } else if (version == 3) {
 *         unsigned int(32) item_ID;
 *     }
 *     unsigned int(16) item_protection_index;
 *     unsigned int(32) item_type;        // 4-char ASCII
 *     string item_name;                  // null-terminated UTF-8
 *     if (item_type == 'mime') {
 *         string content_type;
 *         string content_encoding;       // optional; trailing zero is
 *                                        // counted as the empty string
 *     } else if (item_type == 'uri ') {
 *         string item_uri_type;
 *     }
 * }
 * ```
 *
 * The FullBox `flags` field is repurposed to carry the
 * "(item_info_entry_)flags" bitfield per §8.11.6.2; the only bit
 * shipped today is bit 0 (LSB) which indicates the item is hidden
 * (not directly displayable; used for AVIF auxiliary alpha layers per
 * ISO/IEC 23008-12 §7.3 / §7.5).
 *
 * This module emits ONLY the FullBox *payload* (the bytes after the
 * 4-byte version+flags slot). The caller wraps with
 * `IsobmffBox.encodeFullBox("infe", version, flags, payload)`, or
 * uses the [encodeBox] convenience that picks the minimum version
 * automatically.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object ItemInfoEntry {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "infe"

    /** AVIF AV1 image item (ISO/IEC 23008-12 §6.4). */
    const val ITEM_TYPE_AV01: String = "av01"

    /** EXIF metadata item (HEIF + AVIF; payload lives in `mdat`). */
    const val ITEM_TYPE_EXIF: String = "Exif"

    /** Generic MIME-typed metadata item; uses `content_type` + `content_encoding`. */
    const val ITEM_TYPE_MIME: String = "mime"

    /** URI-typed metadata item; uses `item_uri_type`. */
    const val ITEM_TYPE_URI: String = "uri "

    /** HEIF AVC image item. Emitted by HEIF-AVC stills (rare on modern Android). */
    const val ITEM_TYPE_AVC1: String = "avc1"

    /** HEIF HEVC image item. Emitted by HEIF-HEVC stills (the legacy "HEIC" path). */
    const val ITEM_TYPE_HVC1: String = "hvc1"

    /**
     * AVIF / HEIF derived-image grid item (ISO/IEC 23008-12
     * §6.6.2.3). Stitches an `(rows × columns)` array of equally-
     * sized tile image items into one logical canvas. The tile
     * order in the `iref` `dimg` reference dictates the row-major
     * fill order of the grid. The actual grid recipe (rows /
     * columns / output dimensions) lives in the item's content
     * — see `AvifImageGrid` for the wire-format formatter.
     */
    const val ITEM_TYPE_GRID: String = "grid"

    /**
     * AVIF / HEIF derived-image overlay item (ISO/IEC 23008-12
     * §6.6.2.4). Composites multiple source items at declared
     * `(x, y)` offsets — used for sticker / overlay HDR-to-SDR
     * compositions. Pure-data carriers for `iovl` content are
     * not yet shipped; the constant is here as a forward-
     * looking placeholder so the muxer can recognize the
     * `item_type` without a magic-string lookup.
     */
    const val ITEM_TYPE_IOVL: String = "iovl"

    /**
     * AVIF / HEIF derived-image identity item (ISO/IEC 23008-12
     * §6.6.2.2). A 0-byte content item that wraps a single
     * source item with optional transformative properties
     * (`irot` / `imir` / `clap`). Equivalent of "alias" — useful
     * for emitting the same primary image with multiple
     * orientation hints without re-encoding.
     */
    const val ITEM_TYPE_IDEN: String = "iden"

    /** Maximum item ID encodable with `version = 2`. */
    const val MAX_SMALL_ITEM_ID: Long = 0xFFFFL

    /** Maximum item ID encodable with `version = 3`. */
    const val MAX_LARGE_ITEM_ID: Long = 0xFFFFFFFFL

    /** Maximum item_protection_index (16-bit unsigned per spec). */
    const val MAX_ITEM_PROTECTION_INDEX: Int = 0xFFFF

    /**
     * Bit 0 of the FullBox flags slot. When set, the item is hidden
     * and shall not be directly displayed (used for AVIF auxiliary
     * alpha layers per ISO/IEC 23008-12 §7.3 / §7.5).
     */
    const val FLAG_ITEM_HIDDEN: Int = 0x000001

    /**
     * One row of the `iinf` table, in the spec's v2/v3 form (the
     * only forms Point & Shoot emits). All v2/v3 entries carry the
     * same logical fields; the only difference between the two
     * versions is the wire-format width of [itemId] (16-bit vs
     * 32-bit big-endian).
     *
     * For an AVIF still, the engine ships a primary-image `Entry`
     * with `itemType = ITEM_TYPE_AV01` and (optionally) an EXIF
     * metadata `Entry` with `itemType = ITEM_TYPE_EXIF`.
     *
     *   * [itemProtectionIndex] is `0` when the item is unprotected
     *     (the canonical case for camera output).
     *   * [itemName] is a UTF-8 string written as NUL-terminated
     *     bytes by [encodePayload]. Empty (the default) emits a
     *     single `0` byte per spec.
     *   * [contentType] / [contentEncoding] are only emitted when
     *     [itemType] equals [ITEM_TYPE_MIME].
     *   * [itemUriType] is only emitted when [itemType] equals
     *     [ITEM_TYPE_URI].
     *   * [hidden] surfaces as `flags` bit 0; emitted FullBox flags
     *     are computed by [computeFlags] from this single boolean.
     */
    data class Entry(
        val itemId: Long,
        val itemType: String,
        val itemName: String = "",
        val itemProtectionIndex: Int = 0,
        val contentType: String = "",
        val contentEncoding: String = "",
        val itemUriType: String = "",
        val hidden: Boolean = false,
    ) {
        init {
            require(itemId >= 0L) { "itemId must be >= 0; got $itemId" }
            require(itemId <= MAX_LARGE_ITEM_ID) {
                "itemId must be <= MAX_LARGE_ITEM_ID ($MAX_LARGE_ITEM_ID); got $itemId"
            }
            require(itemProtectionIndex in 0..MAX_ITEM_PROTECTION_INDEX) {
                "itemProtectionIndex must be in [0, $MAX_ITEM_PROTECTION_INDEX]; got $itemProtectionIndex"
            }
            require(itemType.length == 4) {
                "itemType must be exactly 4 ASCII characters; got '${itemType}' (length ${itemType.length})"
            }
            for (c in itemType) {
                require(c.code in 0x20..0x7E) {
                    "itemType must be printable ASCII; got '${itemType}' (codepoint ${c.code})"
                }
            }
            // String fields cannot contain NUL — they're NUL-terminated on the wire.
            require(itemName.indexOf('\u0000') == -1) { "itemName cannot contain NUL" }
            require(contentType.indexOf('\u0000') == -1) { "contentType cannot contain NUL" }
            require(contentEncoding.indexOf('\u0000') == -1) { "contentEncoding cannot contain NUL" }
            require(itemUriType.indexOf('\u0000') == -1) { "itemUriType cannot contain NUL" }
            // contentType / contentEncoding only valid for ITEM_TYPE_MIME.
            if (itemType != ITEM_TYPE_MIME) {
                require(contentType.isEmpty() && contentEncoding.isEmpty()) {
                    "contentType / contentEncoding only valid for itemType '$ITEM_TYPE_MIME'; got '$itemType'"
                }
            }
            // itemUriType only valid for ITEM_TYPE_URI.
            if (itemType != ITEM_TYPE_URI) {
                require(itemUriType.isEmpty()) {
                    "itemUriType only valid for itemType '$ITEM_TYPE_URI'; got '$itemType'"
                }
            }
        }
    }

    /**
     * Pick the minimum FullBox version that can encode [itemId]
     * without truncation. Returns `2` for itemIds in `[0, 65535]`
     * (the canonical case for AVIF stills) and `3` for itemIds in
     * `(65535, 0xFFFFFFFF]`.
     */
    fun chooseVersion(itemId: Long): Int {
        require(itemId >= 0L) { "itemId must be >= 0; got $itemId" }
        require(itemId <= MAX_LARGE_ITEM_ID) {
            "itemId must be <= MAX_LARGE_ITEM_ID ($MAX_LARGE_ITEM_ID); got $itemId"
        }
        return if (itemId > MAX_SMALL_ITEM_ID) 3 else 2
    }

    /**
     * Compute the FullBox `flags` slot for [entry]. Today this
     * surfaces only [Entry.hidden] in bit 0; if the spec adds more
     * flag bits in the future they can be folded in here.
     */
    fun computeFlags(entry: Entry): Int = if (entry.hidden) FLAG_ITEM_HIDDEN else 0

    /**
     * Encode the `infe` FullBox payload (the bytes after the 4-byte
     * version+flags slot). The caller wraps with
     * `IsobmffBox.encodeFullBox("infe", version, flags, payload)`.
     *
     * @throws IllegalArgumentException for invalid version or
     *     itemId that overflows the chosen version's capacity.
     */
    fun encodePayload(entry: Entry, version: Int): ByteArray {
        require(version == 2 || version == 3) {
            "only v2/v3 are emitted by Point & Shoot per ISO/IEC 14496-12:2020 (v0/v1 deprecated); got $version"
        }
        val out = ByteArrayOutputStream()
        if (version == 2) {
            require(entry.itemId <= MAX_SMALL_ITEM_ID) {
                "itemId ${entry.itemId} exceeds version=2 capacity ($MAX_SMALL_ITEM_ID); use version=3"
            }
            writeUint16Be(out, entry.itemId.toInt())
        } else {
            writeUint32Be(out, entry.itemId.toInt())
        }
        writeUint16Be(out, entry.itemProtectionIndex)
        writeFourCc(out, entry.itemType)
        writeNulTerminated(out, entry.itemName)
        when (entry.itemType) {
            ITEM_TYPE_MIME -> {
                writeNulTerminated(out, entry.contentType)
                if (entry.contentEncoding.isNotEmpty()) {
                    writeNulTerminated(out, entry.contentEncoding)
                }
            }
            ITEM_TYPE_URI -> {
                writeNulTerminated(out, entry.itemUriType)
            }
        }
        return out.toByteArray()
    }

    /**
     * Convenience: pick the minimum version via [chooseVersion],
     * compute the flags via [computeFlags], encode the payload, and
     * wrap with `IsobmffBox.encodeFullBox` so the caller gets a
     * complete, mux-ready `infe` box (header + payload) in one
     * call.
     */
    fun encodeBox(entry: Entry): ByteArray {
        val version = chooseVersion(entry.itemId)
        val flags = computeFlags(entry)
        val payload = encodePayload(entry, version)
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

    private fun writeFourCc(out: ByteArrayOutputStream, fourCc: String) {
        for (c in fourCc) {
            out.write(c.code and 0xFF)
        }
    }

    /** Write [s] as UTF-8 bytes followed by a single trailing 0 byte. */
    private fun writeNulTerminated(out: ByteArrayOutputStream, s: String) {
        if (s.isNotEmpty()) {
            out.writeBytes(s.toByteArray(Charsets.UTF_8))
        }
        out.write(0)
    }
}
