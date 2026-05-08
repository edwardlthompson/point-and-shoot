package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the AVIF / HEIF Image Spatial
 * Extents Property (`ispe`) FullBox per ISO/IEC 23008-12 §6.5.3.
 *
 * `ispe` declares the width and height (in pixels) of an image
 * item. It is **mandatory** for AVIF stills per AVIF spec §2.3
 * and for HEIF images per ISO/IEC 23008-12 §7.3. Without `ispe`,
 * a decoder cannot know the canvas size of the encoded image
 * before decoding.
 *
 * Wire format:
 *
 * ```
 * aligned(8) class ImageSpatialExtentsProperty extends FullBox('ispe', 0, 0) {
 *     unsigned int(32) image_width;
 *     unsigned int(32) image_height;
 * }
 * ```
 *
 * Payload is exactly 8 bytes (two `uint32_be`); the FullBox
 * header adds 12 bytes (4 size + 4 type + 1 version + 3 flags),
 * so the total mux-ready box is 20 bytes.
 *
 * `ispe` lives inside the `ipco` property container (Round 24)
 * and is referenced from `ipma` (Round 23) for every image item
 * that needs to declare a canvas size. For an AVIF still with a
 * single primary image item, `ispe` is part of the property
 * bundle alongside `colr` (Round 17), `pixi` (Round 20), and
 * (optionally) `pasp` (Round 19).
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object ImageSpatialExtents {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "ispe"

    /** Fixed payload size: two `uint32_be` fields. */
    const val PAYLOAD_SIZE: Int = 8

    /** Maximum encodable width or height (`uint32` upper bound). */
    const val MAX_DIMENSION: Long = 0xFFFFFFFFL

    /**
     * Encode the FullBox payload (the bytes after the 4-byte
     * version+flags slot). Caller wraps with
     * `IsobmffBox.encodeFullBox("ispe", 0, 0, payload)`, or uses
     * [encodeBox] which does the wrap in one call.
     *
     * @param widthPx image width in pixels; range `[1, 0xFFFFFFFF]`.
     * @param heightPx image height in pixels; range `[1, 0xFFFFFFFF]`.
     */
    fun encodePayload(widthPx: Long, heightPx: Long): ByteArray {
        require(widthPx in 1..MAX_DIMENSION) {
            "widthPx must be in [1, $MAX_DIMENSION]; got $widthPx"
        }
        require(heightPx in 1..MAX_DIMENSION) {
            "heightPx must be in [1, $MAX_DIMENSION]; got $heightPx"
        }
        val out = ByteArrayOutputStream(PAYLOAD_SIZE)
        writeUint32Be(out, widthPx)
        writeUint32Be(out, heightPx)
        return out.toByteArray()
    }

    /**
     * Decode the FullBox payload back into a `(widthPx, heightPx)`
     * pair. Throws `IllegalArgumentException` on wrong length.
     */
    fun decodePayload(bytes: ByteArray): Pair<Long, Long> {
        require(bytes.size == PAYLOAD_SIZE) {
            "ispe payload must be exactly $PAYLOAD_SIZE bytes; got ${bytes.size}"
        }
        val width = readUint32Be(bytes, 0)
        val height = readUint32Be(bytes, 4)
        return width to height
    }

    /**
     * Convenience: encode the payload and wrap with
     * `IsobmffBox.encodeFullBox("ispe", 0, 0, payload)` so the
     * caller gets a complete, mux-ready 20-byte `ispe` box
     * (header + payload) in one call.
     */
    fun encodeBox(widthPx: Long, heightPx: Long): ByteArray {
        val payload = encodePayload(widthPx, heightPx)
        return IsobmffBox.encodeFullBox(BOX_TYPE, version = 0, flags = 0, payload = payload)
    }

    /**
     * Convenience overload accepting `Int` dimensions (the
     * common case for camera previews / captures, where the
     * sensor dimensions fit easily in `Int`).
     */
    fun encodeBox(widthPx: Int, heightPx: Int): ByteArray {
        require(widthPx >= 1) { "widthPx must be >= 1; got $widthPx" }
        require(heightPx >= 1) { "heightPx must be >= 1; got $heightPx" }
        return encodeBox(widthPx.toLong(), heightPx.toLong())
    }

    private fun writeUint32Be(out: ByteArrayOutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    private fun readUint32Be(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }
}
