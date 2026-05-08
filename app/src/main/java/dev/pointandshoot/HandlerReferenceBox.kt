package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the AVIF / HEIF Handler Reference Box
 * (`hdlr`) FullBox per ISO/IEC 14496-12 §8.4.3.
 *
 * `hdlr` declares the *purpose* of the box that contains it. For
 * the `meta` box of a HEIF / AVIF file, the handler type is
 * `"pict"` (still-picture container), which tells a HEIF / AVIF
 * decoder that it should look for image items in this `meta`. For
 * an MP4 `mdia` box the handler types are `"vide"` (video), `"soun"`
 * (sound), `"meta"` (timed metadata), etc.
 *
 * Wire format (per ISO/IEC 14496-12 §8.4.3.2 *Handler Reference Box*):
 *
 * ```
 * aligned(8) class HandlerBox extends FullBox('hdlr', version = 0, 0) {
 *     unsigned int(32) pre_defined = 0;
 *     unsigned int(32) handler_type;        // 4-char ASCII fourCC
 *     const unsigned int(32)[3] reserved = 0;
 *     string name;                          // null-terminated UTF-8
 * }
 * ```
 *
 * The fixed prefix is 4 + 4 + 12 = 20 bytes. The `name` is a
 * null-terminated UTF-8 string per spec (also accepts an empty
 * name, which encodes as a single trailing zero byte).
 *
 * This module emits ONLY the FullBox *payload* (the bytes after the
 * 4-byte version+flags slot). The caller wraps with
 * `IsobmffBox.encodeFullBox("hdlr", 0, 0, payload)`, or uses the
 * [encodeBox] convenience that does the wrapping in one call.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object HandlerReferenceBox {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "hdlr"

    /** Handler type for HEIF / AVIF still-image `meta` containers. */
    const val HANDLER_TYPE_PICT: String = "pict"

    /** Handler type for video tracks in an MP4 `mdia`. */
    const val HANDLER_TYPE_VIDE: String = "vide"

    /** Handler type for sound tracks in an MP4 `mdia`. */
    const val HANDLER_TYPE_SOUN: String = "soun"

    /** Handler type for timed-metadata tracks. */
    const val HANDLER_TYPE_META: String = "meta"

    /** Handler type for auxiliary video (e.g. AVIF auxiliary alpha layer). */
    const val HANDLER_TYPE_AUXV: String = "auxv"

    /**
     * Fixed payload prefix size (in bytes):
     *
     *  * `pre_defined` (uint32 BE): 4 bytes
     *  * `handler_type` (4 ASCII bytes)
     *  * `reserved[3]` (3 × uint32 BE): 12 bytes
     *
     * Total: 20 bytes. The name (NUL-terminated UTF-8) follows.
     */
    const val FIXED_PAYLOAD_PREFIX: Int = 20

    /**
     * Encode the `hdlr` FullBox payload (the bytes after the 4-byte
     * version+flags slot).
     *
     * @param handlerType the 4-character ASCII handler type (e.g.
     *     `"pict"` for HEIF / AVIF stills, `"vide"` / `"soun"` for
     *     MP4 tracks).
     * @param name the human-readable handler name (defaults to the
     *     empty string, which encodes as a single trailing 0 byte
     *     per spec). Cannot contain NUL since NUL is the wire
     *     terminator.
     *
     * Caller wraps with `IsobmffBox.encodeFullBox("hdlr", 0, 0,
     * payload)`.
     */
    fun encodePayload(handlerType: String, name: String = ""): ByteArray {
        require(handlerType.length == 4) {
            "handlerType must be exactly 4 ASCII characters; got '$handlerType' (length ${handlerType.length})"
        }
        for (c in handlerType) {
            require(c.code in 0x20..0x7E) {
                "handlerType must be printable ASCII; got '$handlerType' (codepoint ${c.code})"
            }
        }
        require(name.indexOf('\u0000') == -1) { "name cannot contain NUL" }

        val out = ByteArrayOutputStream()
        // pre_defined = 0
        writeUint32Be(out, 0)
        // handler_type (4 ASCII bytes)
        for (c in handlerType) {
            out.write(c.code and 0xFF)
        }
        // reserved[3] = 0
        writeUint32Be(out, 0)
        writeUint32Be(out, 0)
        writeUint32Be(out, 0)
        // name (NUL-terminated UTF-8)
        if (name.isNotEmpty()) {
            out.writeBytes(name.toByteArray(Charsets.UTF_8))
        }
        out.write(0)
        return out.toByteArray()
    }

    /**
     * Convenience: encode the FullBox payload and wrap with
     * `IsobmffBox.encodeFullBox("hdlr", 0, 0, payload)` so the
     * caller gets a complete, mux-ready `hdlr` box (header +
     * payload) in one call. Fixed at version=0 / flags=0 per spec.
     */
    fun encodeBox(handlerType: String, name: String = ""): ByteArray {
        val payload = encodePayload(handlerType, name)
        return IsobmffBox.encodeFullBox(BOX_TYPE, version = 0, flags = 0, payload = payload)
    }

    /**
     * Convenience for the canonical AVIF / HEIF still-image case:
     * `hdlr` with handler_type = "pict" and an empty name. Returns
     * the full mux-ready box.
     */
    fun encodePictBox(name: String = ""): ByteArray = encodeBox(HANDLER_TYPE_PICT, name)

    private fun writeUint32Be(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
