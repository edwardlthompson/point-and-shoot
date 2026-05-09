package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the AVIF / HEIF Auxiliary Type
 * Property (`auxC`) per ISO/IEC 23008-12 § 6.5.8 and AVIF
 * specification § 3.4.
 *
 * The `auxC` ItemProperty is associated to image items that
 * carry "auxiliary" image data — alpha mattes, depth maps, or
 * other ancillary single-channel image data tied to a primary
 * image item via an `iref` `auxl` reference. The property
 * declares what the auxiliary channel actually means.
 *
 * The most common case for camera apps shipping AVIF stills is
 * **alpha**: AVIF spec § 3.4 mandates the URN
 * `urn:mpeg:mpegB:cicp:systems:auxiliary:alpha` for an alpha
 * image item. A reader that finds this `auxC` `aux_type` on a
 * monochrome AV1 image item knows it is the alpha channel of
 * the primary image item that the `iref` `auxl` reference
 * points to.
 *
 * Wire format:
 *
 * ```
 * aligned(8) class AuxiliaryTypeProperty
 *     extends ItemFullProperty('auxC', version = 0, flags = 0)
 * {
 *     string  aux_type;          // NUL-terminated UTF-8
 *     unsigned int(8) aux_subtype[];   // optional, opaque
 * }
 * ```
 *
 * `aux_type` is a NUL-terminated URI / URN string. When
 * `aux_subtype` is empty (the spec-default for AVIF alpha),
 * the FullBox payload is exactly `aux_type` plus its trailing
 * NUL byte. When `aux_subtype` is non-empty, its raw bytes are
 * appended right after the NUL — the spec leaves the format of
 * `aux_subtype` up to the URN scheme.
 *
 * `auxC` is a FullBox per § 6.5.8. AVIF spec § 3.4 marks it as
 * **essential** when associated to an alpha image item, so the
 * `ipma` association MUST set `essential = true` for that
 * property index.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 *
 * @see AvifAuxiliaryBoxes for the unrelated `irot` / `imir` /
 *     `pixi` properties.
 * @see ItemReferenceBox for the `iref` `auxl` reference that
 *     ties the alpha image item to the primary image item.
 */
object AvifAuxiliaryTypeProperty {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "auxC"

    /** FullBox version pin (always `0` per spec). */
    const val VERSION: Int = 0

    /** FullBox flags pin (always `0` per spec). */
    const val FLAGS: Int = 0

    /**
     * AVIF / HEIF alpha auxiliary URN per AVIF specification § 3.4.
     * A reader that finds this `aux_type` on a monochrome AV1
     * auxiliary image item knows it is the alpha channel of
     * its primary image item.
     */
    const val AUX_TYPE_ALPHA: String = "urn:mpeg:mpegB:cicp:systems:auxiliary:alpha"

    /**
     * HEIF depth-map auxiliary URN per ISO/IEC 23008-12 § 6.5.8.
     * Provided for completeness; the camera app does not
     * currently emit depth-map auxiliary items.
     */
    const val AUX_TYPE_DEPTH: String = "urn:mpeg:mpegB:cicp:systems:auxiliary:depth"

    /**
     * Empty `aux_subtype` shorthand for the common case (every
     * shipped use site for the camera app).
     */
    val EMPTY_AUX_SUBTYPE: ByteArray = ByteArray(0)

    /**
     * One `auxC` payload's structured form.
     *
     * @param auxType the auxiliary-type URN; must be non-empty,
     *     UTF-8 encodable, and contain no embedded NUL byte
     *     (the spec terminates the string with a single NUL).
     * @param auxSubtype optional opaque trailing bytes;
     *     spec-default is empty (zero-length).
     */
    data class Payload(
        val auxType: String,
        val auxSubtype: ByteArray = EMPTY_AUX_SUBTYPE,
    ) {
        init {
            require(auxType.isNotEmpty()) { "auxType must not be empty" }
            require(auxType.indexOf('\u0000') == -1) {
                "auxType must not contain embedded NUL; got '$auxType'"
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Payload) return false
            if (auxType != other.auxType) return false
            if (!auxSubtype.contentEquals(other.auxSubtype)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = auxType.hashCode()
            result = 31 * result + auxSubtype.contentHashCode()
            return result
        }

        companion object {
            /** Pre-computed payload for the canonical AVIF alpha aux. */
            val ALPHA: Payload = Payload(auxType = AUX_TYPE_ALPHA)

            /** Pre-computed payload for the canonical HEIF depth aux. */
            val DEPTH: Payload = Payload(auxType = AUX_TYPE_DEPTH)
        }
    }

    /**
     * Encode the FullBox payload (the bytes after the 4-byte
     * version + flags slot). Caller wraps with
     * `IsobmffBox.encodeFullBox("auxC", 0, 0, payload)` or uses
     * [encodeBox] which does the wrap in one call.
     */
    fun encodePayload(payload: Payload): ByteArray {
        val typeBytes = payload.auxType.toByteArray(Charsets.UTF_8)
        // Defensive: a UTF-8 round-trip can introduce a NUL byte
        // when the input string contains a `\u0000`; we already
        // reject that in `Payload.init`, but checking the encoded
        // bytes directly is cheap insurance against a future code
        // path that bypasses init.
        require(typeBytes.indexOf(0) == -1) {
            "auxType UTF-8 encoding must not contain NUL"
        }
        val out = ByteArrayOutputStream(typeBytes.size + 1 + payload.auxSubtype.size)
        out.write(typeBytes)
        out.write(0) // NUL terminator
        if (payload.auxSubtype.isNotEmpty()) out.write(payload.auxSubtype)
        return out.toByteArray()
    }

    /**
     * Decode an `auxC` FullBox payload back into structured
     * form. Throws when the payload is missing the NUL
     * terminator or when the encoded `auxType` is empty.
     */
    fun decodePayload(bytes: ByteArray): Payload {
        require(bytes.isNotEmpty()) { "auxC payload must not be empty" }
        val nulIdx = bytes.indexOf(0)
        require(nulIdx != -1) {
            "auxC payload must end the auxType with a NUL byte"
        }
        require(nulIdx > 0) {
            "auxC payload must declare a non-empty auxType"
        }
        val auxType = String(bytes, 0, nulIdx, Charsets.UTF_8)
        val auxSubtype = if (nulIdx + 1 < bytes.size) {
            bytes.copyOfRange(nulIdx + 1, bytes.size)
        } else {
            EMPTY_AUX_SUBTYPE
        }
        return Payload(auxType = auxType, auxSubtype = auxSubtype)
    }

    /**
     * Convenience: encode a complete, mux-ready `auxC` FullBox
     * (header + version + flags + payload) in one call.
     */
    fun encodeBox(payload: Payload): ByteArray =
        IsobmffBox.encodeFullBox(BOX_TYPE, version = VERSION, flags = FLAGS, payload = encodePayload(payload))

    /** Convenience: encode the canonical AVIF alpha `auxC` box. */
    fun encodeAlphaBox(): ByteArray = encodeBox(Payload.ALPHA)
}
