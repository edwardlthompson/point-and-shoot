package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the ISOBMFF / HEIF / AVIF Item Reference
 * Box (`iref`) per ISO/IEC 14496-12 §8.11.12.
 *
 * `iref` is a `FullBox('iref', version, 0)` whose payload is an
 * ordered sequence of `SingleItemTypeReferenceBox` sub-boxes.
 * Each sub-box's box-type is a 4-byte ASCII reference-type code
 * (e.g. `"cdsc"`, `"auxl"`, `"thmb"`, `"dimg"`) and the sub-box
 * payload is `from_item_ID` + `reference_count` + a list of
 * `to_item_ID`s. So `iref` is a "list of typed item references":
 *
 *  * **`cdsc`** (Content Describes) — an item describes another
 *    item. Used for EXIF / XMP metadata items that describe the
 *    primary image item per HEIF spec §11.4 / §11.6 and AVIF
 *    spec §3.5.
 *  * **`auxl`** (Auxiliary Image) — an auxiliary image (e.g. an
 *    alpha layer or depth map) auxiliary to its primary image
 *    item. Used by AVIF spec §3.4 for alpha channels (the alpha
 *    image item carries an `auxC` property whose `aux_type` is
 *    `"urn:mpeg:mpegB:cicp:systems:auxiliary:alpha"`).
 *  * **`thmb`** (Thumbnail) — a smaller preview image of its
 *    primary image item.
 *  * **`dimg`** (Derived Image) — a derived image that depends on
 *    one or more source items (e.g. a grid-derived image).
 *
 * Wire format:
 *
 * ```
 * aligned(8) class ItemReferenceBox extends FullBox('iref', version, 0) {
 *     if (version == 0) {
 *         SingleItemTypeReferenceBox      references[];
 *     } else if (version == 1) {
 *         SingleItemTypeReferenceBoxLarge references[];
 *     }
 * }
 *
 * aligned(8) class SingleItemTypeReferenceBox(referenceType)
 *     extends Box(referenceType) {
 *     unsigned int(16) from_item_ID;
 *     unsigned int(16) reference_count;
 *     for (j = 0; j < reference_count; j++) {
 *         unsigned int(16) to_item_ID;
 *     }
 * }
 *
 * aligned(8) class SingleItemTypeReferenceBoxLarge(referenceType)
 *     extends Box(referenceType) {
 *     unsigned int(32) from_item_ID;
 *     unsigned int(32) reference_count;
 *     for (j = 0; j < reference_count; j++) {
 *         unsigned int(32) to_item_ID;
 *     }
 * }
 * ```
 *
 * Version selection rule per spec: emit `version = 1` when any
 * itemId or `to_item_ID` exceeds 16 bits, otherwise `version = 0`.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object ItemReferenceBox {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "iref"

    /** Reference type for "Content Describes" (EXIF / XMP describes image). */
    const val REFERENCE_TYPE_CDSC: String = "cdsc"

    /** Reference type for "Auxiliary Image" (alpha / depth aux to primary). */
    const val REFERENCE_TYPE_AUXL: String = "auxl"

    /** Reference type for "Thumbnail" image. */
    const val REFERENCE_TYPE_THMB: String = "thmb"

    /** Reference type for "Derived Image" (grid / iden / etc.). */
    const val REFERENCE_TYPE_DIMG: String = "dimg"

    /** Maximum `from_item_ID` / `to_item_ID` for `version = 0` (16-bit field). */
    const val MAX_SMALL_ITEM_ID: Long = 65535L

    /** Maximum `from_item_ID` / `to_item_ID` for `version = 1` (32-bit field). */
    const val MAX_LARGE_ITEM_ID: Long = 0xFFFFFFFFL

    /** Maximum `reference_count` for `version = 0` (16-bit field). */
    const val MAX_SMALL_REFERENCE_COUNT: Int = 65535

    /** Maximum `reference_count` for `version = 1` (32-bit field). */
    const val MAX_LARGE_REFERENCE_COUNT: Long = 0xFFFFFFFFL

    /**
     * One `SingleItemTypeReferenceBox` row: an ordered list of
     * `to_item_ID`s declared from one `from_item_ID` under a
     * given 4-byte `referenceType`.
     *
     * @param referenceType 4-byte ASCII reference-type code (e.g.
     *     [REFERENCE_TYPE_CDSC], [REFERENCE_TYPE_AUXL],
     *     [REFERENCE_TYPE_THMB], [REFERENCE_TYPE_DIMG]). Validated
     *     against the printable-ASCII restriction at construction
     *     time.
     * @param fromItemId the `from_item_ID` field (the item that
     *     "is the subject" of the reference — e.g. for `cdsc`,
     *     the EXIF metadata item that describes the primary
     *     image; for `auxl`, the auxiliary image item).
     * @param toItemIds the ordered list of `to_item_ID`s. Per
     *     spec there must be at least one entry; the maximum is
     *     65535 for v=0 or 4_294_967_295 for v=1.
     */
    data class Reference(
        val referenceType: String,
        val fromItemId: Long,
        val toItemIds: List<Long>,
    ) {
        init {
            require(referenceType.length == 4) {
                "referenceType must be exactly 4 ASCII characters; got '$referenceType' (length ${referenceType.length})"
            }
            for (c in referenceType) {
                require(c.code in 0x20..0x7E) {
                    "referenceType must be printable ASCII; got '$referenceType' (codepoint ${c.code})"
                }
            }
            require(fromItemId in 0..MAX_LARGE_ITEM_ID) {
                "fromItemId must be in [0, $MAX_LARGE_ITEM_ID]; got $fromItemId"
            }
            require(toItemIds.isNotEmpty()) {
                "toItemIds must not be empty"
            }
            require(toItemIds.size.toLong() <= MAX_LARGE_REFERENCE_COUNT) {
                "toItemIds.size must be <= $MAX_LARGE_REFERENCE_COUNT; got ${toItemIds.size}"
            }
            for (toId in toItemIds) {
                require(toId in 0..MAX_LARGE_ITEM_ID) {
                    "toItemId must be in [0, $MAX_LARGE_ITEM_ID]; got $toId"
                }
            }
        }
    }

    /**
     * Pick the minimum FullBox version that can encode every
     * reference in [references] without truncation.
     *
     *  * `0` is selected if every `from_item_ID` and every
     *    `to_item_ID` fits in 16 bits AND every
     *    `reference_count` fits in 16 bits.
     *  * `1` is selected when at least one of those values
     *    overflows 16 bits.
     */
    fun chooseVersion(references: List<Reference>): Int {
        for (ref in references) {
            if (ref.fromItemId > MAX_SMALL_ITEM_ID) return 1
            if (ref.toItemIds.size > MAX_SMALL_REFERENCE_COUNT) return 1
            for (toId in ref.toItemIds) {
                if (toId > MAX_SMALL_ITEM_ID) return 1
            }
        }
        return 0
    }

    /**
     * Encode the FullBox payload (the bytes after the 4-byte
     * version+flags slot). Caller wraps with
     * `IsobmffBox.encodeFullBox("iref", version, 0, payload)`,
     * or uses [encodeBox] which does the wrap in one call.
     */
    fun encodePayload(references: List<Reference>, version: Int): ByteArray {
        require(version in 0..1) {
            "version must be 0 or 1; got $version"
        }
        if (version == 0) {
            for (ref in references) {
                require(ref.fromItemId <= MAX_SMALL_ITEM_ID) {
                    "v=0 requires fromItemId <= $MAX_SMALL_ITEM_ID; got ${ref.fromItemId}"
                }
                require(ref.toItemIds.size <= MAX_SMALL_REFERENCE_COUNT) {
                    "v=0 requires reference_count <= $MAX_SMALL_REFERENCE_COUNT; got ${ref.toItemIds.size}"
                }
                for (toId in ref.toItemIds) {
                    require(toId <= MAX_SMALL_ITEM_ID) {
                        "v=0 requires toItemId <= $MAX_SMALL_ITEM_ID; got $toId"
                    }
                }
            }
        }

        val out = ByteArrayOutputStream()
        for (ref in references) {
            val subBoxPayload = encodeSubBoxPayload(ref, version)
            val subBox = IsobmffBox.encodeBox(ref.referenceType, subBoxPayload)
            out.write(subBox)
        }
        return out.toByteArray()
    }

    /**
     * Convenience: encode the payload (auto-picking the minimum
     * version) and wrap with `IsobmffBox.encodeFullBox("iref",
     * version, 0, payload)` so the caller gets a complete,
     * mux-ready `iref` FullBox (header + payload) in one call.
     */
    fun encodeBox(references: List<Reference>): ByteArray {
        val version = chooseVersion(references)
        val payload = encodePayload(references, version)
        return IsobmffBox.encodeFullBox(BOX_TYPE, version = version, flags = 0, payload = payload)
    }

    /**
     * Encode the body of a single `SingleItemTypeReferenceBox`
     * (the bytes after the sub-box's 8-byte plain-box header).
     * Width of every field is determined by [version]:
     *
     *  * `version = 0`: 2-byte `from_item_ID`,
     *    2-byte `reference_count`, 2-byte each `to_item_ID`.
     *  * `version = 1`: 4-byte `from_item_ID`,
     *    4-byte `reference_count`, 4-byte each `to_item_ID`.
     */
    private fun encodeSubBoxPayload(ref: Reference, version: Int): ByteArray {
        val widthBytes = if (version == 0) 2 else 4
        val out = ByteArrayOutputStream(widthBytes * (2 + ref.toItemIds.size))
        writeUint(out, ref.fromItemId, widthBytes)
        writeUint(out, ref.toItemIds.size.toLong(), widthBytes)
        for (toId in ref.toItemIds) {
            writeUint(out, toId, widthBytes)
        }
        return out.toByteArray()
    }

    private fun writeUint(out: ByteArrayOutputStream, value: Long, widthBytes: Int) {
        when (widthBytes) {
            2 -> {
                out.write(((value ushr 8) and 0xFF).toInt())
                out.write((value and 0xFF).toInt())
            }
            4 -> {
                out.write(((value ushr 24) and 0xFF).toInt())
                out.write(((value ushr 16) and 0xFF).toInt())
                out.write(((value ushr 8) and 0xFF).toInt())
                out.write((value and 0xFF).toInt())
            }
            else -> error("unsupported widthBytes=$widthBytes")
        }
    }
}
