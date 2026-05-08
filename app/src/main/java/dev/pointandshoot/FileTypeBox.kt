package dev.pointandshoot

import java.io.ByteArrayOutputStream

/**
 * Pure-data formatter for the ISOBMFF / HEIF / AVIF File Type
 * Box (`ftyp`) per ISO/IEC 14496-12 §4.3.
 *
 * `ftyp` is the **first** box in every ISO base media file — it
 * declares the file's major brand (the format's primary
 * identifier) plus a list of compatible brands a decoder can use
 * to recognize the file. Without `ftyp`, no decoder can identify
 * the file as AVIF / HEIF / MP4 / etc.
 *
 * Wire format per §4.3.2:
 *
 * ```
 * aligned(8) class FileTypeBox extends Box('ftyp') {
 *     unsigned int(32) major_brand;          // 4-char ASCII fourCC
 *     unsigned int(32) minor_version;        // numeric value
 *     unsigned int(32) compatible_brands[];  // 4-char ASCII each
 * }
 * ```
 *
 * `ftyp` is a regular Box (NOT a FullBox) — there is no
 * version+flags slot.
 *
 * For an AVIF still file (per AVIF specification, AOM, January
 * 2019, §4 "AVIF File Format"):
 *
 *  * `major_brand = "avif"` (still image; `"avis"` for image
 *    sequences; not used here).
 *  * `minor_version = 0`.
 *  * `compatible_brands` MUST include `"avif"` and `"mif1"`
 *    (HEIF brand per ISO/IEC 23008-12 §10.2.1). MIAF
 *    (Multi-Image Application Format, ISO/IEC 23000-22) adds
 *    `"miaf"`. AVIF MIAF profile A1 adds `"MA1A"` /
 *    `"MA1B"` / `"MA1C"` (still / sequence / mixed). Point &
 *    Shoot ships the `"avif"` + `"mif1"` + `"miaf"` minimum.
 *
 * Pure-data Kotlin (no Android imports), JVM-testable.
 */
object FileTypeBox {

    /** Bumped only when the on-disk byte layout changes incompatibly. */
    const val SCHEMA_VERSION: Int = 1

    /** Canonical 4-byte ASCII box type. */
    const val BOX_TYPE: String = "ftyp"

    /** AVIF still-image major brand per AVIF spec §4. */
    const val BRAND_AVIF: String = "avif"

    /** AVIF image-sequence major brand. */
    const val BRAND_AVIS: String = "avis"

    /** HEIF brand per ISO/IEC 23008-12 §10.2.1. */
    const val BRAND_MIF1: String = "mif1"

    /** Multi-Image Application Format brand per ISO/IEC 23000-22. */
    const val BRAND_MIAF: String = "miaf"

    /** AVIF MIAF Profile A1 still brand. */
    const val BRAND_MA1A: String = "MA1A"

    /** AVIF MIAF Profile A1 still + image sequence mixed brand. */
    const val BRAND_MA1B: String = "MA1B"

    /** ISOBMFF generic base brand. */
    const val BRAND_ISOM: String = "isom"

    /** HEIF major brand for HEVC stills. */
    const val BRAND_HEIC: String = "heic"

    /** Each brand fourCC is 4 ASCII bytes. */
    const val BRAND_LENGTH: Int = 4

    /**
     * Encode the box payload (per §4.3.2). Caller wraps with
     * `IsobmffBox.encodeBox("ftyp", payload)`, or uses
     * [encodeBox] which does the wrap in one call.
     *
     * Validation:
     *
     *  * `majorBrand` and every `compatibleBrands` entry must be
     *    exactly 4 printable-ASCII characters (`0x20..0x7E`).
     *  * `minorVersion` must be in `[0, 0xFFFFFFFFL]` (uint32 range).
     */
    fun encodePayload(
        majorBrand: String,
        minorVersion: Long,
        compatibleBrands: List<String>,
    ): ByteArray {
        requireBrand(majorBrand, "majorBrand")
        require(minorVersion in 0..0xFFFFFFFFL) {
            "minorVersion must fit in uint32; got $minorVersion"
        }
        for ((i, brand) in compatibleBrands.withIndex()) {
            requireBrand(brand, "compatibleBrands[$i]")
        }
        val out = ByteArrayOutputStream()
        writeFourCc(out, majorBrand)
        writeUint32Be(out, minorVersion)
        for (brand in compatibleBrands) {
            writeFourCc(out, brand)
        }
        return out.toByteArray()
    }

    /**
     * Convenience: encode the payload and wrap with
     * `IsobmffBox.encodeBox("ftyp", payload)` so the caller gets
     * a complete, mux-ready `ftyp` box (header + payload) in
     * one call.
     */
    fun encodeBox(
        majorBrand: String,
        minorVersion: Long,
        compatibleBrands: List<String>,
    ): ByteArray {
        val payload = encodePayload(majorBrand, minorVersion, compatibleBrands)
        return IsobmffBox.encodeBox(BOX_TYPE, payload)
    }

    /**
     * Convenience for the canonical AVIF still: `major_brand = "avif"`,
     * `minor_version = 0`, `compatible_brands = ["avif", "mif1", "miaf"]`.
     * Returns the complete mux-ready `ftyp` box.
     */
    fun encodeAvifStillBox(): ByteArray = encodeBox(
        majorBrand = BRAND_AVIF,
        minorVersion = 0L,
        compatibleBrands = listOf(BRAND_AVIF, BRAND_MIF1, BRAND_MIAF),
    )

    private fun requireBrand(brand: String, label: String) {
        require(brand.length == BRAND_LENGTH) {
            "$label must be exactly $BRAND_LENGTH ASCII characters; got '$brand' (length ${brand.length})"
        }
        for (c in brand) {
            require(c.code in 0x20..0x7E) {
                "$label must be printable ASCII; got '$brand' (codepoint ${c.code})"
            }
        }
    }

    private fun writeFourCc(out: ByteArrayOutputStream, brand: String) {
        for (c in brand) {
            out.write(c.code and 0xFF)
        }
    }

    private fun writeUint32Be(out: ByteArrayOutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }
}
