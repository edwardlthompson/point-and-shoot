package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the AVIF / HEIF Image Grid derived-item
 * content per ISO/IEC 23008-12 §6.6.2.3.
 *
 * The `grid` derived-image item type lets a HEIF / AVIF container
 * stitch together an `(rows × columns)` array of equally-sized
 * tile image items into one logical image. The tiles themselves
 * are normal AV1 / HEVC image items; the `grid` item is the
 * "recipe" that says "tile 1 is at row 0 col 0, tile 2 is at row
 * 0 col 1, ..." and declares the canvas size of the assembled
 * image. The grid item's content (this structure) takes the place
 * of a bitstream in `mdat` — a `grid` item has no bitstream, only
 * the structure described here.
 *
 * Common camera-app uses:
 *
 *  * **High-res stills that exceed AV1 / HEVC tile limits** —
 *    the OnePlus 13's 50 MP / 32 MP main wide produces
 *    `8192 × 6144` images that exceed AV1's max coded width of
 *    `7680` per AV1 spec §A.3 Level 6.0. The natural workaround
 *    is to encode the still as an `(2 × 2)` grid of `4096 × 3072`
 *    AV1 tiles + a `grid` item declaring `output_width = 8192,
 *    output_height = 6144`.
 *  * **Apple HEIC compatibility** — Apple's HEIC implementation
 *    chunks every image into a `grid` of (typically) `512 × 512`
 *    or `1024 × 1024` HEVC tiles + a `grid` item. A future Point
 *    & Shoot HEIC emitter would follow the same pattern.
 *
 * Wire format (per ISO/IEC 23008-12 §6.6.2.3):
 *
 * ```
 * aligned(8) class ImageGrid {
 *     unsigned int(8)  version;             // = 0
 *     unsigned int(8)  flags;               // bit 0: field_length
 *                                           //        (0 = uint16, 1 = uint32)
 *     unsigned int(8)  rows_minus_one;      // 0..255 -> 1..256 rows
 *     unsigned int(8)  columns_minus_one;   // 0..255 -> 1..256 cols
 *     if (flags & 1) {
 *         unsigned int(32) output_width;
 *         unsigned int(32) output_height;
 *     } else {
 *         unsigned int(16) output_width;
 *         unsigned int(16) output_height;
 *     }
 * }
 * ```
 *
 * The 4-byte fixed prefix (`version + flags + rows_minus_one +
 * columns_minus_one`) is followed by the canvas size in either
 * 2- or 4-byte big-endian fields. Total content size is **8
 * bytes** for canvases ≤ 65535 px on each side (the typical
 * camera-still case) and **12 bytes** for larger canvases.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 *
 * @see ItemReferenceBox.REFERENCE_TYPE_DIMG — the `iref` `dimg`
 *     reference type that links a `grid` item to its tile items;
 *     the tile order in the `iref` reference dictates the
 *     row-major fill order of the grid (tile 0 = row 0 col 0,
 *     tile 1 = row 0 col 1, ..., tile cols-1 = row 0 col cols-1,
 *     tile cols = row 1 col 0, ...).
 * @see ItemInfoEntry — emit a grid item via an `infe` entry with
 *     `itemType = "grid"`.
 */
object AvifImageGrid {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /**
     * Item type for a grid derived-image item per ISO/IEC
     * 23008-12 §6.6.2.3. Goes in the `infe` entry's
     * `item_type` field.
     */
    const val ITEM_TYPE: String = "grid"

    /** ImageGrid version pin (always `0` per spec). */
    const val VERSION: Int = 0

    /**
     * Flag bit 0: `field_length`. When set, `output_width` and
     * `output_height` are 4-byte big-endian fields. When clear,
     * they are 2-byte big-endian fields. The muxer auto-picks
     * this flag via [chooseFlags].
     */
    const val FLAG_FIELD_LENGTH_32: Int = 0x01

    /**
     * Total content size in bytes when [FLAG_FIELD_LENGTH_32] is
     * clear (16-bit canvas dims; the typical camera-still case).
     */
    const val PAYLOAD_SIZE_16: Int = 8

    /**
     * Total content size in bytes when [FLAG_FIELD_LENGTH_32] is
     * set (32-bit canvas dims; for canvases > 65535 px on a
     * side).
     */
    const val PAYLOAD_SIZE_32: Int = 12

    /**
     * Maximum number of rows the spec lets us encode in
     * [Payload.rows] — the wire field is `rows_minus_one`
     * (uint8), so the max grid row count is `256`.
     */
    const val MAX_ROWS: Int = 256

    /**
     * Maximum number of columns the spec lets us encode in
     * [Payload.columns] — the wire field is `columns_minus_one`
     * (uint8), so the max grid column count is `256`.
     */
    const val MAX_COLUMNS: Int = 256

    /**
     * Maximum canvas dimension when [FLAG_FIELD_LENGTH_32] is
     * clear (uint16 limit).
     */
    const val MAX_OUTPUT_DIMENSION_16: Long = 65535L

    /**
     * Maximum canvas dimension when [FLAG_FIELD_LENGTH_32] is
     * set (uint32 limit). In practice no camera ever produces
     * an image close to this size; the muxer never auto-picks
     * 32-bit fields below the 16-bit limit.
     */
    const val MAX_OUTPUT_DIMENSION_32: Long = 0xFFFFFFFFL

    /**
     * One image-grid recipe.
     *
     * @param rows the number of rows in the grid; must be in
     *     `[1, 256]` per spec.
     * @param columns the number of columns in the grid; must
     *     be in `[1, 256]` per spec.
     * @param outputWidth the assembled-canvas width in pixels;
     *     must be in `[1, 0xFFFFFFFF]` per spec. The tile width
     *     is implicit: `outputWidth / columns` (truncated;
     *     rightmost column may be padded with cropped pixels
     *     per §6.6.2.3).
     * @param outputHeight the assembled-canvas height in
     *     pixels; must be in `[1, 0xFFFFFFFF]` per spec. The
     *     tile height is implicit: `outputHeight / rows`.
     */
    data class Payload(
        val rows: Int,
        val columns: Int,
        val outputWidth: Long,
        val outputHeight: Long,
    ) {
        init {
            require(rows in 1..MAX_ROWS) {
                "rows must be in [1, $MAX_ROWS]; got $rows"
            }
            require(columns in 1..MAX_COLUMNS) {
                "columns must be in [1, $MAX_COLUMNS]; got $columns"
            }
            require(outputWidth in 1..MAX_OUTPUT_DIMENSION_32) {
                "outputWidth must be in [1, $MAX_OUTPUT_DIMENSION_32]; got $outputWidth"
            }
            require(outputHeight in 1..MAX_OUTPUT_DIMENSION_32) {
                "outputHeight must be in [1, $MAX_OUTPUT_DIMENSION_32]; got $outputHeight"
            }
        }
    }

    /**
     * Pick the minimum `flags` byte that can encode the canvas
     * dimensions in [payload] without truncation.
     *
     *  * Returns `0` when both `outputWidth` and `outputHeight`
     *    fit in 16 bits (the typical 8K-and-below case).
     *  * Returns [FLAG_FIELD_LENGTH_32] when at least one
     *    dimension overflows 16 bits.
     */
    fun chooseFlags(payload: Payload): Int {
        return if (payload.outputWidth > MAX_OUTPUT_DIMENSION_16 ||
            payload.outputHeight > MAX_OUTPUT_DIMENSION_16
        ) {
            FLAG_FIELD_LENGTH_32
        } else {
            0
        }
    }

    /**
     * Encode the grid content (the bytes that go in `mdat` as
     * the body of an `infe` entry whose `item_type = "grid"`).
     *
     * Auto-picks the minimum field length per [chooseFlags];
     * caller can force the 32-bit form by passing
     * `flags = FLAG_FIELD_LENGTH_32` (rare; only useful for
     * forensic byte-layout pinning).
     */
    fun encodePayload(payload: Payload, flags: Int = chooseFlags(payload)): ByteArray {
        val use32 = (flags and FLAG_FIELD_LENGTH_32) != 0
        if (!use32) {
            require(payload.outputWidth <= MAX_OUTPUT_DIMENSION_16) {
                "16-bit field-length cannot encode outputWidth=${payload.outputWidth}; pass flags=FLAG_FIELD_LENGTH_32"
            }
            require(payload.outputHeight <= MAX_OUTPUT_DIMENSION_16) {
                "16-bit field-length cannot encode outputHeight=${payload.outputHeight}; pass flags=FLAG_FIELD_LENGTH_32"
            }
        }
        val out = ByteArrayOutputStream(if (use32) PAYLOAD_SIZE_32 else PAYLOAD_SIZE_16)
        out.write(VERSION)
        out.write(flags and 0xFF)
        out.write((payload.rows - 1) and 0xFF)
        out.write((payload.columns - 1) and 0xFF)
        if (use32) {
            writeUint32BE(out, payload.outputWidth)
            writeUint32BE(out, payload.outputHeight)
        } else {
            writeUint16BE(out, payload.outputWidth.toInt())
            writeUint16BE(out, payload.outputHeight.toInt())
        }
        return out.toByteArray()
    }

    /**
     * Decode a grid content blob back into structured form.
     * Throws when the buffer is too short, the version byte is
     * wrong, or the declared field-length flag asks for more
     * bytes than the buffer carries.
     */
    fun decodePayload(bytes: ByteArray): Payload {
        require(bytes.size >= PAYLOAD_SIZE_16) {
            "grid content must be at least $PAYLOAD_SIZE_16 bytes; got ${bytes.size}"
        }
        val version = bytes[0].toInt() and 0xFF
        require(version == VERSION) {
            "grid content version must be $VERSION; got $version"
        }
        val flags = bytes[1].toInt() and 0xFF
        val rows = (bytes[2].toInt() and 0xFF) + 1
        val columns = (bytes[3].toInt() and 0xFF) + 1
        val use32 = (flags and FLAG_FIELD_LENGTH_32) != 0
        val (width, height) = if (use32) {
            require(bytes.size >= PAYLOAD_SIZE_32) {
                "grid content with 32-bit field-length flag must be at least $PAYLOAD_SIZE_32 bytes; got ${bytes.size}"
            }
            val w = readUint32BE(bytes, 4)
            val h = readUint32BE(bytes, 8)
            Pair(w, h)
        } else {
            val w = readUint16BE(bytes, 4).toLong()
            val h = readUint16BE(bytes, 6).toLong()
            Pair(w, h)
        }
        return Payload(rows = rows, columns = columns, outputWidth = width, outputHeight = height)
    }

    private fun writeUint16BE(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeUint32BE(out: ByteArrayOutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    private fun readUint16BE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readUint32BE(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }
}
