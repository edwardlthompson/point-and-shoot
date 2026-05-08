package dev.pointandshoot

/**
 * Pure-data formatter for the ISOBMFF / HEIF / AVIF Media Data
 * Box (`mdat`) per ISO/IEC 14496-12 §8.1.1.
 *
 * `mdat` is the bulk byte container that holds the actual
 * encoded media bitstreams an item-based file points at via
 * `iloc` (Round 28). For an AVIF still, `mdat` holds the AV1
 * encoded bitstream of the primary image plus (optionally) the
 * EXIF metadata blob.
 *
 * Wire format per §8.1.1:
 *
 * ```
 * aligned(8) class MediaDataBox extends Box('mdat') {
 *     bit(8) data[];
 * }
 * ```
 *
 * `mdat` is a regular Box (NOT a FullBox); its payload is opaque
 * — the parser does not look inside, it only knows where the
 * box ends. The pointers in `iloc` index into `mdat` (or into
 * the file as a whole, depending on `construction_method`) to
 * find the per-item byte ranges.
 *
 * ## Header-size precomputation
 *
 * One subtle constraint: the offsets the engine writes into
 * `iloc` for `construction_method = FILE_OFFSET` are
 * **absolute file offsets**, which means the muxer must know
 * exactly how big the `mdat` header will be **before** it knows
 * how big the payload is, so it can budget the file layout. For
 * payloads `<= 0xFFFFFFFE` bytes, `mdat` uses the canonical
 * 8-byte plain header (`size + type`). For payloads bigger than
 * `0xFFFFFFFFL - 8` bytes (the threshold at which the total
 * box size would overflow the `uint32` in the plain header),
 * `IsobmffBox` automatically promotes the writer to the
 * 16-byte `large_size` escape form (per §4.2.2). [headerSize]
 * exposes this calculation so the muxer can plan offsets.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object MediaDataBox {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "mdat"

    /** Canonical plain-box header size (8 bytes: `size` + `type`). */
    const val PLAIN_HEADER_SIZE: Int = IsobmffBox.PLAIN_HEADER_SIZE

    /** Large-box header size (16 bytes: `size = 1` + `type` + `largesize`). */
    const val LARGE_HEADER_SIZE: Int = IsobmffBox.LARGE_HEADER_SIZE

    /**
     * Compute the on-wire header size for an `mdat` box that
     * will carry `payloadSize` bytes of data. Returns
     * [PLAIN_HEADER_SIZE] (8) for normal payloads and
     * [LARGE_HEADER_SIZE] (16) when the box would otherwise
     * overflow the 32-bit `size` field.
     *
     * Useful to the muxer for offset planning (see KDoc above).
     */
    fun headerSize(payloadSize: Long): Int {
        require(payloadSize >= 0) { "payloadSize must be >= 0; got $payloadSize" }
        // Plain header carries (8-byte header + payload) in its
        // size field; promote to the large form when that exceeds
        // the uint32 max.
        return if (payloadSize + PLAIN_HEADER_SIZE.toLong() > IsobmffBox.LARGE_SIZE_THRESHOLD) {
            LARGE_HEADER_SIZE
        } else {
            PLAIN_HEADER_SIZE
        }
    }

    /**
     * Encode an `mdat` box whose payload is the supplied
     * [data] byte array. Returns the complete, mux-ready
     * `(header + data)` box.
     *
     * `data` is defensively copied by `IsobmffBox.encodeBox`
     * before writing, so a caller mutating the source buffer
     * after this call cannot corrupt the encoded box.
     */
    fun encodeBox(data: ByteArray): ByteArray {
        return IsobmffBox.encodeBox(BOX_TYPE, data)
    }

    /**
     * Encode an `mdat` box whose payload is the concatenation
     * of [datas] in the supplied order. The muxer can use this
     * to glue together, for example, the AV1 image bitstream
     * and the EXIF metadata blob in one go.
     *
     * Equivalent to `encodeBox(IsobmffBox.concatPayloads(datas))`.
     */
    fun encodeBox(datas: List<ByteArray>): ByteArray {
        return IsobmffBox.encodeBox(BOX_TYPE, IsobmffBox.concatPayloads(datas))
    }
}
