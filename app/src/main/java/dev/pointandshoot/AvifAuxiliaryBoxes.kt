package dev.pointandshoot

/**
 * Pure-data byte-layout formatters for the small AVIF auxiliary
 * metadata boxes:
 *
 *   * `irot` - Image Rotation (ISO/IEC 23008-12 §6.5.10). Tells the
 *     renderer to rotate the decoded image clockwise by 0, 90, 180,
 *     or 270 degrees. Used to express sensor orientation without
 *     re-encoding pixels (e.g. for a portrait capture from a
 *     landscape-natural sensor).
 *
 *   * `imir` - Image Mirror (ISO/IEC 23008-12 §6.5.12). Tells the
 *     renderer to flip the decoded image about a vertical or
 *     horizontal axis. Used for selfie-mode captures from
 *     front-facing sensors.
 *
 *   * `pixi` - Pixel Information (ISO/IEC 23008-12 §6.5.6). Lists
 *     the per-channel bit depth of the decoded image. Mandatory in
 *     the AVIF spec for monochrome (1 channel), RGB (3 channels),
 *     and RGBA (4 channels) outputs.
 *
 * The byte layouts here are the **container-neutral** payload (no
 * BMFF size + box-type prefix); the muxer wraps these blobs at mux
 * time. All multi-byte fields are big-endian.
 *
 * No Android imports - safe for unit testing on the JVM.
 */
object AvifAuxiliaryBoxes {

    /** Bumped only when the byte-layout schema changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Total byte length of the `irot` payload (1 byte: angle / 90). */
    const val IROT_PAYLOAD_LENGTH: Int = 1

    /** Total byte length of the `imir` payload (1 byte: axis flag). */
    const val IMIR_PAYLOAD_LENGTH: Int = 1

    /**
     * Image rotation angle in degrees, clockwise. Stored as
     * `angle / 90` in the wire format (so 0..3).
     */
    enum class Rotation(val degrees: Int, val wireValue: Int) {
        Rot0(0, 0),
        Rot90(90, 1),
        Rot180(180, 2),
        Rot270(270, 3);

        companion object {
            fun fromDegrees(degrees: Int): Rotation = when (((degrees % 360) + 360) % 360) {
                0 -> Rot0
                90 -> Rot90
                180 -> Rot180
                270 -> Rot270
                else -> error("unsupported rotation $degrees (must be a multiple of 90)")
            }

            fun fromWireValue(v: Int): Rotation = when (v) {
                0 -> Rot0
                1 -> Rot90
                2 -> Rot180
                3 -> Rot270
                else -> error("invalid irot wire value $v (must be 0..3)")
            }
        }
    }

    /**
     * Image mirror axis. `Vertical` flips around the vertical axis
     * (left-right mirror; what a selfie cam emits). `Horizontal`
     * flips around the horizontal axis (top-bottom mirror).
     *
     * The wire format is a single byte: bit 0 selects axis (0 =
     * top-bottom flip aka horizontal mirror; 1 = left-right flip aka
     * vertical mirror). Bits 7..1 are reserved and must be zero per
     * spec.
     *
     * NB the spec's terminology (`axis_horizontal` = top-bottom flip)
     * is the opposite of how a layperson would label it; we keep
     * spec-aligned names on the wire-value enum to avoid confusion
     * for callers reading the spec directly.
     */
    enum class MirrorAxis(val wireValue: Int) {
        Horizontal(0), // top-bottom flip per ISO/IEC 23008-12
        Vertical(1);   // left-right flip per ISO/IEC 23008-12

        companion object {
            fun fromWireValue(v: Int): MirrorAxis = when (v) {
                0 -> Horizontal
                1 -> Vertical
                else -> error("invalid imir axis $v (must be 0 or 1)")
            }
        }
    }

    /** Encode an `irot` box payload (1 byte). */
    fun encodeIrot(rotation: Rotation): ByteArray = byteArrayOf(rotation.wireValue.toByte())

    /** Decode an `irot` box payload. Throws on wrong length or invalid wire value. */
    fun decodeIrot(bytes: ByteArray): Rotation {
        require(bytes.size == IROT_PAYLOAD_LENGTH) {
            "irot payload must be exactly $IROT_PAYLOAD_LENGTH byte (was ${bytes.size})"
        }
        // Spec defines bits 7..2 as reserved zero; we silently mask them off for tolerance with
        // pre-spec encoders that emitted non-zero reserved bits.
        return Rotation.fromWireValue(bytes[0].toInt() and 0x03)
    }

    /** Encode an `imir` box payload (1 byte). */
    fun encodeImir(axis: MirrorAxis): ByteArray = byteArrayOf(axis.wireValue.toByte())

    /** Decode an `imir` box payload. Throws on wrong length or invalid axis. */
    fun decodeImir(bytes: ByteArray): MirrorAxis {
        require(bytes.size == IMIR_PAYLOAD_LENGTH) {
            "imir payload must be exactly $IMIR_PAYLOAD_LENGTH byte (was ${bytes.size})"
        }
        // Spec defines bits 7..1 as reserved zero; mask off for tolerance.
        return MirrorAxis.fromWireValue(bytes[0].toInt() and 0x01)
    }

    /**
     * Pixel-information payload. `bitDepths[c]` is the bit depth of
     * channel `c`; the `pixi` box lists one byte per channel
     * preceded by an 8-bit channel count.
     *
     * The "FullBox" version + flags header (4 bytes of zeros) is
     * part of the BMFF box header (FullBox), NOT the payload, so
     * we don't include it here - the muxer prefixes it when
     * building the box.
     */
    data class PixiPayload(val bitDepths: IntArray) {
        init {
            require(bitDepths.isNotEmpty()) { "pixi must have at least one channel" }
            require(bitDepths.size in 1..255) {
                "pixi channel count must be in [1, 255] (was ${bitDepths.size})"
            }
            for ((i, depth) in bitDepths.withIndex()) {
                require(depth in 1..255) { "channel $i bit depth must be in [1, 255] (was $depth)" }
            }
        }

        override fun equals(other: Any?): Boolean =
            other is PixiPayload && bitDepths.contentEquals(other.bitDepths)

        override fun hashCode(): Int = bitDepths.contentHashCode()

        companion object {
            /** 8-bit-per-channel monochrome (Y8). */
            val MONO_8: PixiPayload = PixiPayload(intArrayOf(8))

            /** 8-bit-per-channel RGB (24 bpp). */
            val RGB_8: PixiPayload = PixiPayload(intArrayOf(8, 8, 8))

            /** 8-bit-per-channel RGBA (32 bpp). */
            val RGBA_8: PixiPayload = PixiPayload(intArrayOf(8, 8, 8, 8))

            /** 10-bit-per-channel RGB (HDR10 / Rec.2020 PQ default). */
            val RGB_10: PixiPayload = PixiPayload(intArrayOf(10, 10, 10))

            /** 12-bit-per-channel RGB (libjxl default for high-bit RAW-derived stills). */
            val RGB_12: PixiPayload = PixiPayload(intArrayOf(12, 12, 12))
        }
    }

    /**
     * Encode a `pixi` payload. Layout:
     *
     *     +--------+--------+--------+...+--------+
     *     |numChan | bitD[0]| bitD[1]| ...| bitD[N-1] |
     *     +--------+--------+--------+...+--------+
     *
     * where `numChan = bitDepths.size` and each `bitD[i]` is one byte.
     */
    fun encodePixi(pixi: PixiPayload): ByteArray {
        val out = ByteArray(1 + pixi.bitDepths.size)
        out[0] = pixi.bitDepths.size.toByte()
        for (i in pixi.bitDepths.indices) {
            out[1 + i] = pixi.bitDepths[i].toByte()
        }
        return out
    }

    /** Decode a `pixi` payload. Throws on inconsistent length / channel count / depth. */
    fun decodePixi(bytes: ByteArray): PixiPayload {
        require(bytes.isNotEmpty()) { "pixi payload must be at least 1 byte (was ${bytes.size})" }
        val numChan = bytes[0].toInt() and 0xFF
        require(numChan >= 1) { "pixi channel count must be >= 1 (was $numChan)" }
        require(bytes.size == 1 + numChan) {
            "pixi payload length ${bytes.size} does not match declared channel count $numChan"
        }
        val depths = IntArray(numChan) { i -> bytes[1 + i].toInt() and 0xFF }
        return PixiPayload(depths)
    }
}
