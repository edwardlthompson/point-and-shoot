package dev.pointandshoot

/**
 * Pure-data byte-layout formatters for two ancillary ISOBMFF
 * "transformative" boxes that ride alongside the `colr` (CICP) and
 * `mdcv` / `clli` boxes in AVIF / HEVC samples:
 *
 *   * `pasp` - Pixel Aspect Ratio (ISO/IEC 14496-12 §12.1.4.3). Tells
 *     a renderer that the stored pixels aren't square (e.g. anamorphic
 *     captures). Phone-camera output is always square-pixel so this
 *     defaults to `1:1`, but the engine has to emit it explicitly for
 *     bit-exact AVIF compliance with libavif's strict mode.
 *
 *   * `clap` - Clean Aperture (ISO/IEC 14496-12 §12.1.4.3). The
 *     "visible" rectangle within the encoded sample. AVIF muxers use
 *     this to describe an arbitrary crop relative to the encoded
 *     image without re-encoding (e.g. 1:1 square crops of the LYT-808
 *     50 MP sensor that aren't a multiple of the codec's macroblock
 *     size).
 *
 * Both boxes are mandatory in the AVIF spec when the engine wants to
 * cleanly express a non-square pixel grid or a content rectangle that
 * isn't aligned to the codec's coded-image rectangle.
 *
 * The byte layouts here are the **container-neutral** payload (no
 * BMFF size + box-type prefix); the muxer wraps these blobs at mux
 * time. All multi-byte fields are big-endian.
 *
 * No Android imports - safe for unit testing on the JVM.
 */
object IsobmffSampleAspect {

    /** Bumped only when the byte-layout schema changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Total byte length of the `pasp` payload (no BMFF box header). */
    const val PASP_PAYLOAD_LENGTH: Int = 8

    /** Total byte length of the `clap` payload (no BMFF box header). */
    const val CLAP_PAYLOAD_LENGTH: Int = 32

    /**
     * Pixel aspect ratio expressed as the integer ratio
     * `hSpacing : vSpacing`. Phone-camera pixels are always square
     * (`1:1`); cinema cameras and broadcast captures might emit
     * `4:3`, `40:33`, `16:11`, etc.
     *
     * Both fields are 32-bit unsigned per the spec; we accept any
     * `Int >= 1` (negative spacings have no spec-defined meaning).
     */
    data class PaspPayload(val hSpacing: Int, val vSpacing: Int) {
        init {
            require(hSpacing >= 1) { "hSpacing must be >= 1 (was $hSpacing)" }
            require(vSpacing >= 1) { "vSpacing must be >= 1 (was $vSpacing)" }
        }

        companion object {
            /** Square-pixel default (every modern phone/DSLR/mirrorless camera). */
            val SQUARE: PaspPayload = PaspPayload(1, 1)
        }
    }

    /**
     * Clean-aperture rectangle. Each dimension is a rational
     * `numerator / denominator`. Width/height denominators must be
     * `>= 1`; offsets are signed.
     *
     * The clean-aperture rectangle is centered at
     * `((codedWidth - 1) / 2 + horizOffN/horizOffD,
     *   (codedHeight - 1) / 2 + vertOffN/vertOffD)`
     * with size `(widthN/widthD, heightN/heightD)`. Per the spec the
     * rectangle must lie entirely within the coded image.
     */
    data class ClapPayload(
        val widthN: Int,
        val widthD: Int,
        val heightN: Int,
        val heightD: Int,
        val horizOffN: Int,
        val horizOffD: Int,
        val vertOffN: Int,
        val vertOffD: Int,
    ) {
        init {
            require(widthD >= 1) { "widthD must be >= 1 (was $widthD)" }
            require(heightD >= 1) { "heightD must be >= 1 (was $heightD)" }
            require(horizOffD >= 1) { "horizOffD must be >= 1 (was $horizOffD)" }
            require(vertOffD >= 1) { "vertOffD must be >= 1 (was $vertOffD)" }
            require(widthN >= 1) { "widthN must be >= 1 (was $widthN)" }
            require(heightN >= 1) { "heightN must be >= 1 (was $heightN)" }
        }

        companion object {
            /**
             * Build a clean-aperture box describing an integer-pixel,
             * top-left-anchored crop within the coded image.
             *
             * `cropX, cropY` are the top-left corner of the desired
             * visible rectangle (relative to the coded image's
             * origin); `cropW, cropH` are its size.
             *
             * Per ISO/IEC 14496-12 the offsets are expressed as the
             * delta from the image's centerpoint. We rephrase the
             * top-left form to the spec's center form here so calling
             * code can talk in pixel-rectangle terms.
             */
            fun centeredCropOf(
                codedWidth: Int,
                codedHeight: Int,
                cropX: Int,
                cropY: Int,
                cropW: Int,
                cropH: Int,
            ): ClapPayload {
                require(codedWidth >= 1 && codedHeight >= 1) {
                    "codedWidth ($codedWidth) and codedHeight ($codedHeight) must be >= 1"
                }
                require(cropW >= 1 && cropH >= 1) { "cropW ($cropW) and cropH ($cropH) must be >= 1" }
                require(cropX >= 0 && cropY >= 0) { "cropX ($cropX) and cropY ($cropY) must be >= 0" }
                require(cropX + cropW <= codedWidth) {
                    "crop ($cropX + $cropW) overflows codedWidth ($codedWidth)"
                }
                require(cropY + cropH <= codedHeight) {
                    "crop ($cropY + $cropH) overflows codedHeight ($codedHeight)"
                }
                // Center of the cropped rectangle in coded-image pixel coordinates,
                // expressed as a numerator/denominator pair (denominator 2 to retain
                // half-pixel precision).
                val cropCenterX2 = (2 * cropX + cropW - 1)
                val cropCenterY2 = (2 * cropY + cropH - 1)
                val codedCenterX2 = (codedWidth - 1)
                val codedCenterY2 = (codedHeight - 1)
                val horizOffN = cropCenterX2 - codedCenterX2
                val vertOffN = cropCenterY2 - codedCenterY2
                return ClapPayload(
                    widthN = cropW,
                    widthD = 1,
                    heightN = cropH,
                    heightD = 1,
                    horizOffN = horizOffN,
                    horizOffD = 2,
                    vertOffN = vertOffN,
                    vertOffD = 2,
                )
            }
        }
    }

    /** Encode a `pasp` box payload (8 bytes, big-endian). */
    fun encodePasp(pasp: PaspPayload): ByteArray {
        val out = ByteArray(PASP_PAYLOAD_LENGTH)
        writeUInt32Be(out, 0, pasp.hSpacing.toLong() and 0xFFFFFFFFL)
        writeUInt32Be(out, 4, pasp.vSpacing.toLong() and 0xFFFFFFFFL)
        return out
    }

    /** Decode a `pasp` box payload. Throws on wrong length. */
    fun decodePasp(bytes: ByteArray): PaspPayload {
        require(bytes.size == PASP_PAYLOAD_LENGTH) {
            "pasp payload must be exactly $PASP_PAYLOAD_LENGTH bytes (was ${bytes.size})"
        }
        return PaspPayload(
            hSpacing = readUInt32BeAsInt(bytes, 0),
            vSpacing = readUInt32BeAsInt(bytes, 4),
        )
    }

    /** Encode a `clap` box payload (32 bytes, big-endian). */
    fun encodeClap(clap: ClapPayload): ByteArray {
        val out = ByteArray(CLAP_PAYLOAD_LENGTH)
        writeUInt32Be(out, 0, clap.widthN.toLong() and 0xFFFFFFFFL)
        writeUInt32Be(out, 4, clap.widthD.toLong() and 0xFFFFFFFFL)
        writeUInt32Be(out, 8, clap.heightN.toLong() and 0xFFFFFFFFL)
        writeUInt32Be(out, 12, clap.heightD.toLong() and 0xFFFFFFFFL)
        writeInt32Be(out, 16, clap.horizOffN)
        writeUInt32Be(out, 20, clap.horizOffD.toLong() and 0xFFFFFFFFL)
        writeInt32Be(out, 24, clap.vertOffN)
        writeUInt32Be(out, 28, clap.vertOffD.toLong() and 0xFFFFFFFFL)
        return out
    }

    /** Decode a `clap` box payload. Throws on wrong length. */
    fun decodeClap(bytes: ByteArray): ClapPayload {
        require(bytes.size == CLAP_PAYLOAD_LENGTH) {
            "clap payload must be exactly $CLAP_PAYLOAD_LENGTH bytes (was ${bytes.size})"
        }
        return ClapPayload(
            widthN = readUInt32BeAsInt(bytes, 0),
            widthD = readUInt32BeAsInt(bytes, 4),
            heightN = readUInt32BeAsInt(bytes, 8),
            heightD = readUInt32BeAsInt(bytes, 12),
            horizOffN = readInt32Be(bytes, 16),
            horizOffD = readUInt32BeAsInt(bytes, 20),
            vertOffN = readInt32Be(bytes, 24),
            vertOffD = readUInt32BeAsInt(bytes, 28),
        )
    }

    private fun writeUInt32Be(buf: ByteArray, off: Int, value: Long) {
        buf[off] = ((value ushr 24) and 0xFF).toByte()
        buf[off + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[off + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[off + 3] = (value and 0xFF).toByte()
    }

    private fun writeInt32Be(buf: ByteArray, off: Int, value: Int) {
        buf[off] = ((value ushr 24) and 0xFF).toByte()
        buf[off + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[off + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[off + 3] = (value and 0xFF).toByte()
    }

    private fun readUInt32BeAsInt(buf: ByteArray, off: Int): Int {
        val v = ((buf[off].toLong() and 0xFF) shl 24) or
            ((buf[off + 1].toLong() and 0xFF) shl 16) or
            ((buf[off + 2].toLong() and 0xFF) shl 8) or
            (buf[off + 3].toLong() and 0xFF)
        require(v <= Int.MAX_VALUE) { "uint32 overflow at offset $off (value=$v)" }
        return v.toInt()
    }

    private fun readInt32Be(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 24) or
            ((buf[off + 1].toInt() and 0xFF) shl 16) or
            ((buf[off + 2].toInt() and 0xFF) shl 8) or
            (buf[off + 3].toInt() and 0xFF)
}
