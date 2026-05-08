package dev.pointandshoot

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Pure-data formatter for the AVIF / HEIF Item Location Box
 * (`iloc`) FullBox per ISO/IEC 14496-12 §8.11.3.
 *
 * `iloc` is the table of byte offsets + lengths into `mdat` (or
 * into the `idat` box, or into another item) for every item
 * declared in `iinf`. It is the bridge between the property /
 * info catalog and the actual encoded image bytes:
 *
 *  * `iinf` declares that item 1 has `item_type = "av01"`.
 *  * `iloc` declares that item 1's encoded bitstream lives at
 *    file-offset `0x12000` and is `0x4500` bytes long.
 *
 * Without `iloc`, no decoder can find the encoded image bytes.
 *
 * Wire format per §8.11.3.2 (v0 form):
 *
 * ```
 * aligned(8) class ItemLocationBox extends FullBox('iloc', version, 0) {
 *     unsigned int(4)  offset_size;
 *     unsigned int(4)  length_size;
 *     unsigned int(4)  base_offset_size;
 *     unsigned int(4)  reserved;            // v0; or index_size when v=1/v=2
 *     if (version < 2)
 *         unsigned int(16) item_count;
 *     else
 *         unsigned int(32) item_count;
 *     for (i = 0; i < item_count; i++) {
 *         if (version < 2)
 *             unsigned int(16) item_ID;
 *         else
 *             unsigned int(32) item_ID;
 *         if (version == 1 || version == 2) {
 *             unsigned int(12) reserved = 0;
 *             unsigned int(4)  construction_method;
 *         }
 *         unsigned int(16) data_reference_index;
 *         unsigned int(base_offset_size*8) base_offset;
 *         unsigned int(16) extent_count;
 *         for (j = 0; j < extent_count; j++) {
 *             if ((version == 1 || version == 2) && (index_size > 0))
 *                 unsigned int(index_size*8) extent_index;
 *             unsigned int(offset_size*8) extent_offset;
 *             unsigned int(length_size*8) extent_length;
 *         }
 *     }
 * }
 * ```
 *
 * For a single-image AVIF still, the canonical configuration is:
 *
 *  * `version = 0` (16-bit `item_count` + 16-bit `item_ID`, no
 *    `construction_method` field).
 *  * `offset_size = 4`, `length_size = 4`, `base_offset_size = 0`.
 *  * One [Item] for the primary image (`item_ID = 1`), with one
 *    [Extent] pointing at the encoded bitstream inside `mdat`.
 *
 * Multi-tile HEIF would push the engine to `version = 1` (so the
 * `construction_method` field surfaces).
 *
 * This module emits ONLY the FullBox payload (the bytes after
 * the 4-byte version+flags slot). The caller wraps with
 * `IsobmffBox.encodeFullBox("iloc", version, flags = 0, payload)`,
 * or uses the [encodeBox] convenience that picks the minimum
 * version and field widths automatically.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object ItemLocationBox {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "iloc"

    /** Maximum `item_ID` for v=0 / v=1 (16-bit field). */
    const val MAX_SMALL_ITEM_ID: Long = 0xFFFFL

    /** Maximum `item_ID` for v=2 (32-bit field). */
    const val MAX_LARGE_ITEM_ID: Long = 0xFFFFFFFFL

    /** Maximum `item_count` for v=0 / v=1 (16-bit field). */
    const val MAX_SMALL_ITEM_COUNT: Long = 0xFFFFL

    /** Maximum `item_count` for v=2 (32-bit field). */
    const val MAX_LARGE_ITEM_COUNT: Long = 0xFFFFFFFFL

    /** Maximum `extent_count` per item (16-bit field). */
    const val MAX_EXTENT_COUNT: Int = 0xFFFF

    /** Maximum `data_reference_index` (16-bit field). */
    const val MAX_DATA_REFERENCE_INDEX: Int = 0xFFFF

    /** Maximum `construction_method` (4-bit field). */
    const val MAX_CONSTRUCTION_METHOD: Int = 0xF

    /** Allowed sizes (in bytes) for offset / length / base_offset / index fields per §8.11.3. */
    val ALLOWED_FIELD_SIZES: IntArray = intArrayOf(0, 4, 8)

    /**
     * Per ISO/IEC 14496-12 §8.11.3.3, how the engine should
     * locate the extent's bytes:
     *
     *  * [FILE_OFFSET] (`0`): bytes are at `extent_offset` inside
     *    the file (the `mdat` case).
     *  * [IDAT_OFFSET] (`1`): bytes are inside the
     *    `meta`-resident `idat` box at `extent_offset`.
     *  * [ITEM_OFFSET] (`2`): bytes are inside another item
     *    referenced by `extent_index`.
     *
     * Only valid when `version >= 1`. For canonical AVIF stills
     * the engine emits `version = 0` and no `construction_method`
     * field is in the bitstream — this enum exists for the
     * multi-tile / `idat` cases the engine may grow into later.
     */
    enum class ConstructionMethod(val wireValue: Int) {
        FILE_OFFSET(0),
        IDAT_OFFSET(1),
        ITEM_OFFSET(2),
    }

    /**
     * One byte range inside `mdat` (or inside `idat`, or inside
     * another item) that contributes to an [Item]'s encoded
     * stream.
     *
     * @param offset the byte offset of this extent. When the
     *     parent [Item.constructionMethod] is [ConstructionMethod.FILE_OFFSET],
     *     this is the absolute file offset. When it is
     *     [ConstructionMethod.IDAT_OFFSET], this is the offset
     *     inside the `idat` box. Negative values are rejected.
     * @param length the byte length of this extent. Must be `>= 0`.
     * @param index the `extent_index` per §8.11.3 (only used
     *     when `index_size > 0`, which only happens for `version >= 1`
     *     and `constructionMethod = ITEM_OFFSET`). For the canonical
     *     AVIF still case this is `null`.
     */
    data class Extent(
        val offset: Long,
        val length: Long,
        val index: Long? = null,
    ) {
        init {
            require(offset >= 0) { "offset must be >= 0; got $offset" }
            require(length >= 0) { "length must be >= 0; got $length" }
            require(index == null || index >= 0) {
                "index must be >= 0 when present; got $index"
            }
        }
    }

    /**
     * One row of the `iloc` table.
     *
     * @param itemId the `item_ID` matching one of the `infe`
     *     entries declared in `iinf` (1-based per common HEIF
     *     practice; 0 is reserved).
     * @param constructionMethod how the bytes are located. Only
     *     surfaces in the bitstream when `version >= 1`. For
     *     canonical AVIF stills (single mdat-backed image item)
     *     the engine emits `version = 0` and this field is
     *     absent from the wire.
     * @param dataReferenceIndex the `data_reference_index`
     *     per §8.11.3; `0` means "this file" (the only realistic
     *     value for a self-contained AVIF still). Range `[0, 65535]`.
     * @param baseOffset the `base_offset` per §8.11.3; every
     *     extent's `extent_offset` is relative to this value. The
     *     canonical AVIF still uses `0` (extents carry absolute
     *     file offsets directly).
     * @param extents the list of byte ranges contributing to this
     *     item; must be non-empty and `<= 65535` entries.
     */
    data class Item(
        val itemId: Long,
        val constructionMethod: ConstructionMethod = ConstructionMethod.FILE_OFFSET,
        val dataReferenceIndex: Int = 0,
        val baseOffset: Long = 0,
        val extents: List<Extent>,
    ) {
        init {
            require(itemId in 0..MAX_LARGE_ITEM_ID) {
                "itemId must be in [0, $MAX_LARGE_ITEM_ID]; got $itemId"
            }
            require(dataReferenceIndex in 0..MAX_DATA_REFERENCE_INDEX) {
                "dataReferenceIndex must be in [0, $MAX_DATA_REFERENCE_INDEX]; got $dataReferenceIndex"
            }
            require(baseOffset >= 0) { "baseOffset must be >= 0; got $baseOffset" }
            require(extents.isNotEmpty()) { "extents must not be empty" }
            require(extents.size <= MAX_EXTENT_COUNT) {
                "extents.size must be <= $MAX_EXTENT_COUNT; got ${extents.size}"
            }
        }
    }

    /**
     * Field-size selection (in bytes) for `offset_size`,
     * `length_size`, `base_offset_size`, and `index_size`.
     *
     * Each value is one of [0, 4, 8] per §8.11.3 (Point & Shoot
     * supports the canonical sizes; arbitrary widths in `[0, 7]`
     * exist in spec but are not interoperable). A value of `0`
     * means "the corresponding field is omitted from the
     * bitstream" — used for `base_offset_size = 0` when every
     * item has `baseOffset = 0`, and `index_size = 0` when no
     * `ITEM_OFFSET` constructions are present.
     */
    data class FieldSizes(
        val offsetSize: Int,
        val lengthSize: Int,
        val baseOffsetSize: Int,
        val indexSize: Int,
    ) {
        init {
            require(offsetSize in ALLOWED_FIELD_SIZES) {
                "offsetSize must be in ${ALLOWED_FIELD_SIZES.toList()}; got $offsetSize"
            }
            require(lengthSize in ALLOWED_FIELD_SIZES) {
                "lengthSize must be in ${ALLOWED_FIELD_SIZES.toList()}; got $lengthSize"
            }
            require(baseOffsetSize in ALLOWED_FIELD_SIZES) {
                "baseOffsetSize must be in ${ALLOWED_FIELD_SIZES.toList()}; got $baseOffsetSize"
            }
            require(indexSize in ALLOWED_FIELD_SIZES) {
                "indexSize must be in ${ALLOWED_FIELD_SIZES.toList()}; got $indexSize"
            }
        }
    }

    /**
     * Pick the minimum [FieldSizes] that can encode every offset
     * / length / base_offset / index across `items` without
     * truncation.
     *
     * Each width is `0` if the corresponding field is always
     * zero (the canonical "no base_offset, no index" AVIF still
     * case), `4` when the largest value fits in a `uint32`, or
     * `8` when it does not.
     */
    fun chooseFieldSizes(items: List<Item>): FieldSizes {
        var maxOffset = 0L
        var maxLength = 0L
        var maxBaseOffset = 0L
        var maxIndex = 0L
        for (item in items) {
            if (item.baseOffset > maxBaseOffset) maxBaseOffset = item.baseOffset
            for (e in item.extents) {
                if (e.offset > maxOffset) maxOffset = e.offset
                if (e.length > maxLength) maxLength = e.length
                if (e.index != null && e.index > maxIndex) maxIndex = e.index
            }
        }
        return FieldSizes(
            offsetSize = pickFieldSize(maxOffset, allowZero = isAlwaysZero(items) { e, _ -> e.offset }),
            lengthSize = pickFieldSize(maxLength, allowZero = isAlwaysZero(items) { e, _ -> e.length }),
            baseOffsetSize = pickFieldSize(maxBaseOffset, allowZero = items.all { it.baseOffset == 0L }),
            indexSize = pickFieldSize(maxIndex, allowZero = items.all { it.extents.all { e -> e.index == null || e.index == 0L } }),
        )
    }

    /**
     * Pick the minimum FullBox version that can encode `items`
     * given `fieldSizes`.
     *
     *  * `0` is selected if every itemId fits in 16 bits,
     *    every constructionMethod is FILE_OFFSET, and no extent
     *    needs an index field.
     *  * `1` is selected when at least one item has a
     *    non-FILE_OFFSET construction method, or `indexSize > 0`,
     *    while every itemId still fits in 16 bits.
     *  * `2` is selected when any itemId exceeds 16 bits or the
     *    item count exceeds 16 bits.
     */
    fun chooseVersion(items: List<Item>, fieldSizes: FieldSizes): Int {
        val needsLargeItemId = items.any { it.itemId > MAX_SMALL_ITEM_ID } ||
            items.size.toLong() > MAX_SMALL_ITEM_COUNT
        val needsConstructionMethod = items.any {
            it.constructionMethod != ConstructionMethod.FILE_OFFSET
        } || fieldSizes.indexSize > 0
        return when {
            needsLargeItemId -> 2
            needsConstructionMethod -> 1
            else -> 0
        }
    }

    /**
     * Encode the FullBox payload (the bytes after the 4-byte
     * version+flags slot).
     *
     * Caller wraps with `IsobmffBox.encodeFullBox("iloc",
     * version, 0, payload)`.
     */
    fun encodePayload(
        items: List<Item>,
        version: Int,
        fieldSizes: FieldSizes,
    ): ByteArray {
        require(version in 0..2) { "version must be in [0, 2]; got $version" }
        require(items.size.toLong() <= when (version) {
            0, 1 -> MAX_SMALL_ITEM_COUNT
            else -> MAX_LARGE_ITEM_COUNT
        }) {
            "items.size exceeds version $version capacity"
        }
        if (version == 0) {
            require(fieldSizes.indexSize == 0) {
                "version 0 cannot carry index_size > 0"
            }
            for (item in items) {
                require(item.constructionMethod == ConstructionMethod.FILE_OFFSET) {
                    "version 0 cannot carry construction_method != FILE_OFFSET"
                }
                for (e in item.extents) {
                    require(e.index == null || e.index == 0L) {
                        "version 0 extents must not carry an index"
                    }
                }
            }
        }
        for (item in items) {
            require(item.itemId <= when (version) {
                0, 1 -> MAX_SMALL_ITEM_ID
                else -> MAX_LARGE_ITEM_ID
            }) {
                "itemId ${item.itemId} exceeds version $version capacity"
            }
            requireFits(item.baseOffset, fieldSizes.baseOffsetSize, "baseOffset")
            for (e in item.extents) {
                requireFits(e.offset, fieldSizes.offsetSize, "extent.offset")
                requireFits(e.length, fieldSizes.lengthSize, "extent.length")
                if (fieldSizes.indexSize > 0 && e.index != null) {
                    requireFits(e.index, fieldSizes.indexSize, "extent.index")
                }
            }
        }

        val out = ByteArrayOutputStream()
        val packedSizes = (
            ((fieldSizes.offsetSize and 0xF) shl 12) or
                ((fieldSizes.lengthSize and 0xF) shl 8) or
                ((fieldSizes.baseOffsetSize and 0xF) shl 4) or
                (if (version >= 1) fieldSizes.indexSize and 0xF else 0)
            )
        writeUint16Be(out, packedSizes)

        if (version < 2) {
            writeUint16Be(out, items.size)
        } else {
            writeUint32Be(out, items.size.toLong())
        }

        for (item in items) {
            if (version < 2) {
                writeUint16Be(out, item.itemId.toInt())
            } else {
                writeUint32Be(out, item.itemId)
            }
            if (version >= 1) {
                writeUint16Be(out, item.constructionMethod.wireValue and 0xF)
            }
            writeUint16Be(out, item.dataReferenceIndex)
            writeFieldValue(out, item.baseOffset, fieldSizes.baseOffsetSize)
            writeUint16Be(out, item.extents.size)
            for (e in item.extents) {
                if (version >= 1 && fieldSizes.indexSize > 0) {
                    writeFieldValue(out, e.index ?: 0L, fieldSizes.indexSize)
                }
                writeFieldValue(out, e.offset, fieldSizes.offsetSize)
                writeFieldValue(out, e.length, fieldSizes.lengthSize)
            }
        }
        return out.toByteArray()
    }

    /**
     * Convenience: pick the minimum field sizes via
     * [chooseFieldSizes], the minimum version via
     * [chooseVersion], encode the payload, and wrap with
     * `IsobmffBox.encodeFullBox("iloc", version, 0, payload)` so
     * the caller gets a complete, mux-ready `iloc` box (header +
     * payload) in one call.
     */
    fun encodeBox(items: List<Item>): ByteArray {
        val fieldSizes = chooseFieldSizes(items)
        val version = chooseVersion(items, fieldSizes)
        val payload = encodePayload(items, version, fieldSizes)
        return IsobmffBox.encodeFullBox(BOX_TYPE, version = version, flags = 0, payload = payload)
    }

    private inline fun isAlwaysZero(
        items: List<Item>,
        selector: (Extent, Item) -> Long,
    ): Boolean {
        for (item in items) {
            for (e in item.extents) {
                if (selector(e, item) != 0L) return false
            }
        }
        return true
    }

    private fun pickFieldSize(maxValue: Long, allowZero: Boolean): Int {
        return when {
            allowZero -> 0
            maxValue <= 0xFFFFFFFFL -> 4
            else -> 8
        }
    }

    private fun requireFits(value: Long, size: Int, label: String) {
        when (size) {
            0 -> require(value == 0L) {
                "$label must be 0 when its field is 0 bytes; got $value"
            }
            4 -> require(value in 0..0xFFFFFFFFL) {
                "$label exceeds 4-byte capacity; got $value"
            }
            8 -> require(value >= 0) {
                "$label must be >= 0; got $value"
            }
            else -> error("unsupported field size $size for $label")
        }
    }

    private fun writeFieldValue(out: OutputStream, value: Long, size: Int) {
        when (size) {
            0 -> Unit
            4 -> writeUint32Be(out, value)
            8 -> writeUint64Be(out, value)
            else -> error("unsupported field size $size")
        }
    }

    private fun writeUint16Be(out: OutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeUint32Be(out: OutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    private fun writeUint64Be(out: OutputStream, value: Long) {
        out.write(((value ushr 56) and 0xFF).toInt())
        out.write(((value ushr 48) and 0xFF).toInt())
        out.write(((value ushr 40) and 0xFF).toInt())
        out.write(((value ushr 32) and 0xFF).toInt())
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }
}
