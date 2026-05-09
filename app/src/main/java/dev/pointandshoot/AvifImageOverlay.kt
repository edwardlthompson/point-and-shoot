package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the AVIF / HEIF Image Overlay
 * derived-item content per ISO/IEC 23008-12 §6.6.2.4.
 *
 * The `iovl` derived-image item type composites N source image
 * items at declared signed `(x, y)` offsets onto a fill-colored
 * canvas. Use cases:
 *
 *  * **Sticker / overlay compositions** — composite a primary
 *    image item with a separately-encoded watermark / sticker
 *    item on top.
 *  * **Multi-layer HDR-to-SDR** — composite an HDR image
 *    item with a tone-mapped SDR layer for fallback rendering.
 *  * **Image padding / framing** — composite a smaller image
 *    item onto a larger canvas with a chosen background fill
 *    color (e.g. center-crop padding).
 *
 * Wire format:
 *
 * ```
 * aligned(8) class ImageOverlay {
 *     unsigned int(8)  version = 0;
 *     unsigned int(8)  flags;                  // bit 0: field_length
 *     unsigned int(16) canvas_fill_value[4];   // R, G, B, A; always uint16
 *     FieldLength      output_width;
 *     FieldLength      output_height;
 *     for (i = 0; i < reference_count; i++) {
 *         FieldLength  horizontal_offset;      // SIGNED
 *         FieldLength  vertical_offset;        // SIGNED
 *     }
 * }
 *
 * where FieldLength is uint16 when (flags & 1) == 0, uint32 when
 * (flags & 1) == 1. horizontal_offset and vertical_offset are
 * SIGNED two's-complement integers in their declared FieldLength.
 * output_width and output_height are UNSIGNED.
 * ```
 *
 * `reference_count` is NOT stored in the iovl content itself —
 * it comes from the matching `iref` `dimg` reference's count of
 * `to_item_ID`s, so the per-reference offset table at the end
 * of the iovl content carries exactly that many `(h, v)` pairs.
 *
 * Minimum content size (no references, 16-bit fields):
 *   1 (version) + 1 (flags) + 8 (canvas_fill_value) + 4 (dims) = 14 bytes
 * Maximum content size (no references, 32-bit fields):
 *   1 + 1 + 8 + 8 = 18 bytes
 * Each additional reference adds 4 bytes (16-bit) or 8 bytes (32-bit).
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 *
 * @see AvifImageGrid for the simpler `grid` derived-item case.
 * @see ItemReferenceBox.REFERENCE_TYPE_DIMG — the `iref` `dimg`
 *     reference type that ties an iovl item to its source items;
 *     the source-item order in the `iref` dictates which
 *     `(h, v)` offset pair applies to which item.
 * @see ItemInfoEntry.ITEM_TYPE_IOVL — emit an iovl item via an
 *     `infe` entry with `itemType = "iovl"`.
 */
object AvifImageOverlay {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /**
     * Item type for an overlay derived-image item per ISO/IEC
     * 23008-12 §6.6.2.4. Goes in the `infe` entry's
     * `item_type` field.
     */
    const val ITEM_TYPE: String = "iovl"

    /** ImageOverlay version pin (always `0` per spec). */
    const val VERSION: Int = 0

    /**
     * Flag bit 0: `field_length`. When set, `output_width`,
     * `output_height`, and every `(h, v)` reference offset
     * pair are 4-byte big-endian fields. When clear, they are
     * 2-byte big-endian fields. The muxer auto-picks this flag
     * via [chooseFlags].
     */
    const val FLAG_FIELD_LENGTH_32: Int = 0x01

    /** Number of canvas fill-value channels: R, G, B, A (always 4). */
    const val CANVAS_FILL_CHANNELS: Int = 4

    /** Each canvas-fill-value channel is uint16 (always 2 bytes). */
    const val CANVAS_FILL_BYTES_PER_CHANNEL: Int = 2

    /** Total bytes occupied by the canvas_fill_value array (8 bytes). */
    const val CANVAS_FILL_TOTAL_BYTES: Int = CANVAS_FILL_CHANNELS * CANVAS_FILL_BYTES_PER_CHANNEL

    /**
     * Minimum payload size for an empty (zero-reference) iovl
     * with 16-bit field length: 1 (version) + 1 (flags) + 8
     * (canvas_fill_value) + 2 (output_width) + 2 (output_height)
     * = 12. Wait — that would mean a per-extent uint16. Re-
     * checking: 1 + 1 + 8 + 4 = 14.
     */
    const val MIN_PAYLOAD_SIZE_16: Int = 14

    /**
     * Minimum payload size for an empty (zero-reference) iovl
     * with 32-bit field length: 1 + 1 + 8 + 8 = 18.
     */
    const val MIN_PAYLOAD_SIZE_32: Int = 18

    /** Each reference contributes 4 bytes when 16-bit fields. */
    const val REFERENCE_BYTES_16: Int = 4

    /** Each reference contributes 8 bytes when 32-bit fields. */
    const val REFERENCE_BYTES_32: Int = 8

    /** Maximum unsigned canvas dimension (uint16 limit). */
    const val MAX_OUTPUT_DIMENSION_16: Long = 65535L

    /** Maximum unsigned canvas dimension (uint32 limit). */
    const val MAX_OUTPUT_DIMENSION_32: Long = 0xFFFFFFFFL

    /** Maximum signed reference offset (int16 limit). */
    const val MAX_REFERENCE_OFFSET_16: Long = 32767L

    /** Minimum signed reference offset (int16 limit). */
    const val MIN_REFERENCE_OFFSET_16: Long = -32768L

    /** Maximum signed reference offset (int32 limit). */
    const val MAX_REFERENCE_OFFSET_32: Long = 2147483647L

    /** Minimum signed reference offset (int32 limit). */
    const val MIN_REFERENCE_OFFSET_32: Long = -2147483648L

    /** Maximum canvas-fill-value channel value (uint16 limit). */
    const val MAX_CANVAS_FILL_VALUE: Int = 65535

    /**
     * One per-reference `(horizontal_offset, vertical_offset)`
     * pair. The pair's index in [Payload.references] dictates
     * which source item it applies to (the order matches the
     * `iref` `dimg`'s `to_item_ID` list).
     *
     * @param horizontalOffset signed pixel offset of the source
     *     item's top-left corner from the canvas's top-left
     *     corner. Positive moves right; negative moves left and
     *     crops the source item against the canvas's left edge
     *     per spec §6.6.2.4. Range depends on the chosen field
     *     length: `[MIN/MAX_REFERENCE_OFFSET_16]` for the
     *     compact form, `[MIN/MAX_REFERENCE_OFFSET_32]` for the
     *     wide form.
     * @param verticalOffset signed pixel offset; same semantics
     *     as [horizontalOffset] but for the y-axis.
     */
    data class Reference(
        val horizontalOffset: Long,
        val verticalOffset: Long,
    ) {
        init {
            require(horizontalOffset in MIN_REFERENCE_OFFSET_32..MAX_REFERENCE_OFFSET_32) {
                "horizontalOffset must be in [$MIN_REFERENCE_OFFSET_32, $MAX_REFERENCE_OFFSET_32]; got $horizontalOffset"
            }
            require(verticalOffset in MIN_REFERENCE_OFFSET_32..MAX_REFERENCE_OFFSET_32) {
                "verticalOffset must be in [$MIN_REFERENCE_OFFSET_32, $MAX_REFERENCE_OFFSET_32]; got $verticalOffset"
            }
        }
    }

    /**
     * One image-overlay recipe.
     *
     * @param canvasFillR uint16 R channel of the canvas fill
     *     color used wherever no source item covers a pixel.
     *     Range `[0, 65535]`. The fill color is in the canvas's
     *     declared color space (typically the parent
     *     [AvifColrPayload]).
     * @param canvasFillG uint16 G channel; same range as
     *     [canvasFillR].
     * @param canvasFillB uint16 B channel; same range.
     * @param canvasFillA uint16 A channel (alpha); 0 = fully
     *     transparent, 65535 = fully opaque.
     * @param outputWidth canvas width in pixels; must be in
     *     `[1, 0xFFFFFFFF]` per spec.
     * @param outputHeight canvas height in pixels; same range.
     * @param references the per-source-item offset pairs in the
     *     same order as the matching `iref` `dimg`'s
     *     `to_item_ID` list. Empty references is allowed by the
     *     spec (a degenerate iovl with no source items, just
     *     the canvas fill).
     */
    data class Payload(
        val canvasFillR: Int,
        val canvasFillG: Int,
        val canvasFillB: Int,
        val canvasFillA: Int,
        val outputWidth: Long,
        val outputHeight: Long,
        val references: List<Reference> = emptyList(),
    ) {
        init {
            require(canvasFillR in 0..MAX_CANVAS_FILL_VALUE) {
                "canvasFillR must be in [0, $MAX_CANVAS_FILL_VALUE]; got $canvasFillR"
            }
            require(canvasFillG in 0..MAX_CANVAS_FILL_VALUE) {
                "canvasFillG must be in [0, $MAX_CANVAS_FILL_VALUE]; got $canvasFillG"
            }
            require(canvasFillB in 0..MAX_CANVAS_FILL_VALUE) {
                "canvasFillB must be in [0, $MAX_CANVAS_FILL_VALUE]; got $canvasFillB"
            }
            require(canvasFillA in 0..MAX_CANVAS_FILL_VALUE) {
                "canvasFillA must be in [0, $MAX_CANVAS_FILL_VALUE]; got $canvasFillA"
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
     * dimensions AND every reference offset in [payload]
     * without truncation.
     *
     *  * Returns `0` when both canvas dimensions fit in
     *    `MAX_OUTPUT_DIMENSION_16` AND every reference offset
     *    fits in the signed 16-bit range.
     *  * Returns [FLAG_FIELD_LENGTH_32] otherwise.
     */
    fun chooseFlags(payload: Payload): Int {
        if (payload.outputWidth > MAX_OUTPUT_DIMENSION_16) return FLAG_FIELD_LENGTH_32
        if (payload.outputHeight > MAX_OUTPUT_DIMENSION_16) return FLAG_FIELD_LENGTH_32
        for (ref in payload.references) {
            if (ref.horizontalOffset !in MIN_REFERENCE_OFFSET_16..MAX_REFERENCE_OFFSET_16) {
                return FLAG_FIELD_LENGTH_32
            }
            if (ref.verticalOffset !in MIN_REFERENCE_OFFSET_16..MAX_REFERENCE_OFFSET_16) {
                return FLAG_FIELD_LENGTH_32
            }
        }
        return 0
    }

    /**
     * Compute the on-wire payload size for the given [payload]
     * and [flags] without doing the actual encode.
     */
    fun payloadSize(payload: Payload, flags: Int = chooseFlags(payload)): Int {
        val use32 = (flags and FLAG_FIELD_LENGTH_32) != 0
        val basePayload = if (use32) MIN_PAYLOAD_SIZE_32 else MIN_PAYLOAD_SIZE_16
        val perRef = if (use32) REFERENCE_BYTES_32 else REFERENCE_BYTES_16
        return basePayload + payload.references.size * perRef
    }

    /**
     * Encode the overlay content (the bytes that go in `mdat`
     * as the body of an `infe` entry whose
     * `item_type = "iovl"`).
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
            for ((idx, ref) in payload.references.withIndex()) {
                require(ref.horizontalOffset in MIN_REFERENCE_OFFSET_16..MAX_REFERENCE_OFFSET_16) {
                    "16-bit field-length cannot encode references[$idx].horizontalOffset=${ref.horizontalOffset}"
                }
                require(ref.verticalOffset in MIN_REFERENCE_OFFSET_16..MAX_REFERENCE_OFFSET_16) {
                    "16-bit field-length cannot encode references[$idx].verticalOffset=${ref.verticalOffset}"
                }
            }
        }

        val out = ByteArrayOutputStream(payloadSize(payload, flags))
        out.write(VERSION)
        out.write(flags and 0xFF)
        // canvas_fill_value[4] (R, G, B, A) - always uint16.
        writeUint16BE(out, payload.canvasFillR)
        writeUint16BE(out, payload.canvasFillG)
        writeUint16BE(out, payload.canvasFillB)
        writeUint16BE(out, payload.canvasFillA)
        if (use32) {
            writeUint32BE(out, payload.outputWidth)
            writeUint32BE(out, payload.outputHeight)
            for (ref in payload.references) {
                writeInt32BE(out, ref.horizontalOffset)
                writeInt32BE(out, ref.verticalOffset)
            }
        } else {
            writeUint16BE(out, payload.outputWidth.toInt())
            writeUint16BE(out, payload.outputHeight.toInt())
            for (ref in payload.references) {
                writeInt16BE(out, ref.horizontalOffset.toInt())
                writeInt16BE(out, ref.verticalOffset.toInt())
            }
        }
        return out.toByteArray()
    }

    /**
     * Decode an overlay content blob back into structured form.
     * The [referenceCount] must be supplied by the caller — it
     * is NOT stored in the iovl content; it is determined by
     * the matching `iref` `dimg` reference's `to_item_ID` count.
     *
     * Throws on under-minimum-size buffers, wrong version byte,
     * or truncation against the declared field length.
     */
    fun decodePayload(bytes: ByteArray, referenceCount: Int): Payload {
        require(referenceCount >= 0) {
            "referenceCount must be >= 0; got $referenceCount"
        }
        require(bytes.size >= MIN_PAYLOAD_SIZE_16) {
            "iovl content must be at least $MIN_PAYLOAD_SIZE_16 bytes; got ${bytes.size}"
        }
        val version = bytes[0].toInt() and 0xFF
        require(version == VERSION) {
            "iovl content version must be $VERSION; got $version"
        }
        val flags = bytes[1].toInt() and 0xFF
        val use32 = (flags and FLAG_FIELD_LENGTH_32) != 0
        val expected = if (use32) {
            MIN_PAYLOAD_SIZE_32 + referenceCount * REFERENCE_BYTES_32
        } else {
            MIN_PAYLOAD_SIZE_16 + referenceCount * REFERENCE_BYTES_16
        }
        require(bytes.size >= expected) {
            "iovl content must be at least $expected bytes for flags=$flags + $referenceCount references; got ${bytes.size}"
        }

        val r = readUint16BE(bytes, 2)
        val g = readUint16BE(bytes, 4)
        val b = readUint16BE(bytes, 6)
        val a = readUint16BE(bytes, 8)
        var cursor = 10
        val width: Long
        val height: Long
        if (use32) {
            width = readUint32BE(bytes, cursor); cursor += 4
            height = readUint32BE(bytes, cursor); cursor += 4
        } else {
            width = readUint16BE(bytes, cursor).toLong(); cursor += 2
            height = readUint16BE(bytes, cursor).toLong(); cursor += 2
        }
        val refs = ArrayList<Reference>(referenceCount)
        repeat(referenceCount) {
            if (use32) {
                val h = readInt32BE(bytes, cursor); cursor += 4
                val v = readInt32BE(bytes, cursor); cursor += 4
                refs.add(Reference(horizontalOffset = h, verticalOffset = v))
            } else {
                val h = readInt16BE(bytes, cursor).toLong(); cursor += 2
                val v = readInt16BE(bytes, cursor).toLong(); cursor += 2
                refs.add(Reference(horizontalOffset = h, verticalOffset = v))
            }
        }
        return Payload(
            canvasFillR = r,
            canvasFillG = g,
            canvasFillB = b,
            canvasFillA = a,
            outputWidth = width,
            outputHeight = height,
            references = refs,
        )
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

    private fun writeInt16BE(out: ByteArrayOutputStream, value: Int) {
        // Emit two's-complement 16-bit big-endian.
        val masked = value and 0xFFFF
        out.write((masked ushr 8) and 0xFF)
        out.write(masked and 0xFF)
    }

    private fun writeInt32BE(out: ByteArrayOutputStream, value: Long) {
        // Emit two's-complement 32-bit big-endian.
        val masked = value.toInt()
        out.write((masked ushr 24) and 0xFF)
        out.write((masked ushr 16) and 0xFF)
        out.write((masked ushr 8) and 0xFF)
        out.write(masked and 0xFF)
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

    private fun readInt16BE(bytes: ByteArray, offset: Int): Int {
        // Sign-extend the 16-bit big-endian value.
        val raw = readUint16BE(bytes, offset)
        return if ((raw and 0x8000) != 0) raw or 0xFFFF0000.toInt() else raw
    }

    private fun readInt32BE(bytes: ByteArray, offset: Int): Long {
        // Sign-extend the 32-bit big-endian value to Long.
        // (raw shl 32) shr 32 uses arithmetic shift to set the
        // high bits to match bit 31's value.
        val raw = readUint32BE(bytes, offset)
        return (raw shl 32) shr 32
    }
}
