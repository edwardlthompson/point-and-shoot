package dev.pointandshoot

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Patches IFD0 **Software** (TIFF tag **305**) in a little-endian Adobe DNG produced by
 * [android.hardware.camera2.DngCreator].
 *
 * `DngCreator` stamps IFD0 Software with the **Android build fingerprint** (same slot length as
 * `setDescription` auxiliary text). Gallery/desktop tools often display IFD0 Software — not the EXIF
 * SubIFD — so [androidx.exifinterface.media.ExifInterface] alone does not replace what users see.
 *
 * This routine **overwrites the existing ASCII payload in place** (same byte length); the new string
 * must fit including a terminating NUL with optional padding.
 */
object TiffIfd0Software305 {

    private const val TIFF_MAGIC_II: Short = 0x4949.toShort()
    private const val TIFF_VERSION: Short = 42
    private const val TAG_SOFTWARE = 305
    private const val TYPE_ASCII = 2

    /** IFD0 tags commonly shown by galleries / desktop tools (DNG/TIFF baseline). */
    const val TAG_MAKE: Int = 271
    const val TAG_MODEL: Int = 272
    const val TAG_DATETIME: Int = 306

    /**
     * Returns a copy of [tifBytes] with IFD0 tag 305 replaced by [softwareAscii] (NUL-terminated,
     * padded with NULs to the original field length). Returns the original buffer unchanged if the
     * tag is missing or the new text is too long.
     */
    fun patchSoftwarePreservingLength(tifBytes: ByteArray, softwareAscii: String): ByteArray {
        val trimmed = softwareAscii.trim()
        if (trimmed.isEmpty()) return tifBytes
        require(tifBytes.size >= 8) { "buffer too small for TIFF header" }

        val hdr = ByteBuffer.wrap(tifBytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        if (hdr.short != TIFF_MAGIC_II) return tifBytes
        if (hdr.short != TIFF_VERSION) return tifBytes
        val primaryIfdOffset = hdr.int.toLong() and 0xffff_ffffL
        if (primaryIfdOffset < 0 || primaryIfdOffset > tifBytes.size - 4) return tifBytes

        val payload = trimmed.encodeToByteArrayWithNull()
        val out = tifBytes.copyOf()
        val ok =
            patchAsciiTagInPrimaryIfd(
                out,
                primaryIfdOffset.toInt(),
                TAG_SOFTWARE,
                TYPE_ASCII,
                payload,
            )
        return if (ok) out else tifBytes
    }

    /**
     * Overwrites IFD0 ASCII [tagId] in place when present (same rules as Software).
     * [ascii] is truncated with NUL padding to fit the existing field length.
     */
    fun patchPrimaryIfdAsciiTagPreservingLength(tifBytes: ByteArray, tagId: Int, ascii: String): ByteArray {
        val trimmed = ascii.trim()
        if (trimmed.isEmpty()) return tifBytes
        require(tifBytes.size >= 8) { "buffer too small for TIFF header" }

        val hdr = ByteBuffer.wrap(tifBytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        if (hdr.short != TIFF_MAGIC_II) return tifBytes
        if (hdr.short != TIFF_VERSION) return tifBytes
        val primaryIfdOffset = hdr.int.toLong() and 0xffff_ffffL
        if (primaryIfdOffset < 0 || primaryIfdOffset > tifBytes.size - 4) return tifBytes

        val existingLen = asciiFieldByteCountInPrimaryIfd(tifBytes, primaryIfdOffset.toInt(), tagId)
            ?: return tifBytes
        val payload = trimmed.encodeToAsciiField(existingLen)
        val out = tifBytes.copyOf()
        val ok =
            patchAsciiTagInPrimaryIfd(
                out,
                primaryIfdOffset.toInt(),
                tagId,
                TYPE_ASCII,
                payload,
            )
        return if (ok) out else tifBytes
    }

    private fun asciiFieldByteCountInPrimaryIfd(tifBytes: ByteArray, ifdOffset: Int, tagId: Int): Int? {
        val bb = ByteBuffer.wrap(tifBytes).order(ByteOrder.LITTLE_ENDIAN)
        if (ifdOffset < 0 || ifdOffset > tifBytes.size - 2) return null
        bb.position(ifdOffset)
        val entryCount = bb.short.toInt() and 0xffff
        repeat(entryCount) {
            val tag = bb.short.toInt() and 0xffff
            val type = bb.short.toInt() and 0xffff
            val count = bb.int.toLong() and 0xffff_ffffL
            val valueBytes = ByteArray(4)
            bb.get(valueBytes)
            if (tag != tagId) return@repeat
            if (type != TYPE_ASCII) return null
            return count.toInt().coerceAtLeast(1)
        }
        return null
    }

    private fun String.encodeToAsciiField(totalByteCount: Int): ByteArray {
        val n = totalByteCount.coerceAtLeast(1)
        val out = ByteArray(n)
        val ascii = this.toByteArray(StandardCharsets.US_ASCII)
        val maxText = (n - 1).coerceAtLeast(0)
        val textLen = minOf(ascii.size, maxText)
        System.arraycopy(ascii, 0, out, 0, textLen)
        if (textLen < n) {
            out[textLen] = 0
            if (textLen + 1 < n) {
                Arrays.fill(out, textLen + 1, n, 0)
            }
        }
        return out
    }

    private fun patchAsciiTagInPrimaryIfd(
        out: ByteArray,
        ifdOffset: Int,
        tagId: Int,
        typeAscii: Int,
        payloadWithNull: ByteArray,
    ): Boolean {
        val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        if (ifdOffset < 0 || ifdOffset > out.size - 2) return false
        bb.position(ifdOffset)
        val entryCount = bb.short.toInt() and 0xffff
        repeat(entryCount) {
            val entryStart = bb.position()
            val tag = bb.short.toInt() and 0xffff
            val type = bb.short.toInt() and 0xffff
            val count = bb.int.toLong() and 0xffff_ffffL
            val valueBytes = ByteArray(4)
            bb.get(valueBytes)
            if (tag != tagId) return@repeat
            if (type != typeAscii) return false
            val byteCount = count.toInt()
            if (byteCount < 1 || payloadWithNull.size > byteCount) return false

            val vb = ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN)
            val valueOffset =
                if (byteCount <= 4) {
                    entryStart + 8
                } else {
                    vb.int
                }
            if (valueOffset < 0 || valueOffset + byteCount > out.size) return false

            Arrays.fill(out, valueOffset, valueOffset + byteCount, 0)
            System.arraycopy(payloadWithNull, 0, out, valueOffset, payloadWithNull.size)
            return true
        }
        return false
    }

    private fun String.encodeToByteArrayWithNull(): ByteArray {
        val ascii = this.toByteArray(StandardCharsets.US_ASCII)
        return ascii + byteArrayOf(0)
    }
}
