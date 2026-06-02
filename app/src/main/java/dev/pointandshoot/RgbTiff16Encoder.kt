package dev.pointandshoot

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tiny little-endian baseline TIFF encoder for 16-bit RGB strips.
 * Used by still-export automation for `still.tiff16`.
 */
object RgbTiff16Encoder {
    private const val TIFF_MAGIC = 42
    private const val IFD_ENTRY_SIZE = 12
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4
    private const val TYPE_RATIONAL = 5

    private const val TAG_IMAGE_WIDTH = 256
    private const val TAG_IMAGE_LENGTH = 257
    private const val TAG_BITS_PER_SAMPLE = 258
    private const val TAG_COMPRESSION = 259
    private const val TAG_PHOTOMETRIC = 262
    private const val TAG_STRIP_OFFSETS = 273
    private const val TAG_SAMPLES_PER_PIXEL = 277
    private const val TAG_ROWS_PER_STRIP = 278
    private const val TAG_STRIP_BYTE_COUNTS = 279
    private const val TAG_PLANAR_CONFIGURATION = 284
    private const val TAG_EXIF_IFD_POINTER = 34665
    private const val TAG_EXPOSURE_TIME = 33434
    private const val TAG_F_NUMBER = 33437
    private const val TAG_PHOTOGRAPHIC_SENSITIVITY = 34855
    private const val TAG_FOCAL_LENGTH = 37386

    data class CaptureExif(
        val exposureTimeNs: Long? = null,
        val aperture: Float? = null,
        val iso: Int? = null,
        val focalMm: Float? = null,
    )

    fun encodeRgb888AsTiff16(
        rgb888: ByteArray,
        width: Int,
        height: Int,
        exif: CaptureExif? = null,
    ): ByteArray {
        require(width > 0 && height > 0) { "invalid TIFF size ${width}x$height" }
        require(rgb888.size == width * height * 3) { "rgb888 size mismatch" }

        val includeExif =
            exif != null &&
                ((exif.exposureTimeNs ?: 0L) > 0L ||
                    (exif.aperture ?: 0f) > 0f ||
                    (exif.iso ?: 0) > 0 ||
                    (exif.focalMm ?: 0f) > 0f)

        val ifdEntryCount = if (includeExif) 11 else 10
        val ifdStart = 8
        val ifdSize = 2 + ifdEntryCount * IFD_ENTRY_SIZE + 4
        val bitsOffset = ifdStart + ifdSize
        val exifIfdOffset = bitsOffset + 6
        val exifEntryCount = 4
        val exifIfdSize = if (includeExif) (2 + exifEntryCount * IFD_ENTRY_SIZE + 4) else 0
        val exifRationalOffset = exifIfdOffset + exifIfdSize
        val exifRationalBytes = if (includeExif) 3 * 8 else 0
        val pixelOffset = exifIfdOffset + exifIfdSize + exifRationalBytes
        val pixelBytes = width * height * 6
        val out = ByteBuffer.allocate(pixelOffset + pixelBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Header
        out.put(0x49.toByte())
        out.put(0x49.toByte())
        out.putShort(TIFF_MAGIC.toShort())
        out.putInt(ifdStart)

        // IFD
        out.putShort(ifdEntryCount.toShort())
        putLongEntry(out, TAG_IMAGE_WIDTH, width)
        putLongEntry(out, TAG_IMAGE_LENGTH, height)
        putShort3Entry(out, TAG_BITS_PER_SAMPLE, bitsOffset)
        putShortEntry(out, TAG_COMPRESSION, 1) // no compression
        putShortEntry(out, TAG_PHOTOMETRIC, 2) // RGB
        putLongEntry(out, TAG_STRIP_OFFSETS, pixelOffset)
        putShortEntry(out, TAG_SAMPLES_PER_PIXEL, 3)
        putLongEntry(out, TAG_ROWS_PER_STRIP, height)
        putLongEntry(out, TAG_STRIP_BYTE_COUNTS, pixelBytes)
        putShortEntry(out, TAG_PLANAR_CONFIGURATION, 1)
        if (includeExif) {
            putLongEntry(out, TAG_EXIF_IFD_POINTER, exifIfdOffset)
        }
        out.putInt(0) // next IFD

        // BitsPerSample payload
        out.putShort(16)
        out.putShort(16)
        out.putShort(16)

        if (includeExif) {
            val ex = exif ?: CaptureExif()
            out.putShort(exifEntryCount.toShort())
            putRationalEntry(out, TAG_EXPOSURE_TIME, exifRationalOffset)
            putRationalEntry(out, TAG_F_NUMBER, exifRationalOffset + 8)
            putShortEntry(out, TAG_PHOTOGRAPHIC_SENSITIVITY, (ex.iso ?: 100).coerceIn(1, 65535))
            putRationalEntry(out, TAG_FOCAL_LENGTH, exifRationalOffset + 16)
            out.putInt(0) // next Exif IFD

            val (expN, expD) = exposureRational(ex.exposureTimeNs)
            val (fNumN, fNumD) = decimalRational(ex.aperture?.toDouble() ?: 2.0)
            val (focalN, focalD) = decimalRational(ex.focalMm?.toDouble() ?: 1.0)
            out.putInt(expN)
            out.putInt(expD)
            out.putInt(fNumN)
            out.putInt(fNumD)
            out.putInt(focalN)
            out.putInt(focalD)
        }

        // Pixel payload, 16-bit upscale from RGB888.
        var i = 0
        while (i < rgb888.size) {
            val r = rgb888[i++].toInt() and 0xFF
            val g = rgb888[i++].toInt() and 0xFF
            val b = rgb888[i++].toInt() and 0xFF
            out.putShort(((r shl 8) or r).toShort())
            out.putShort(((g shl 8) or g).toShort())
            out.putShort(((b shl 8) or b).toShort())
        }
        return out.array()
    }

    private fun putShortEntry(bb: ByteBuffer, tag: Int, value: Int) {
        bb.putShort(tag.toShort())
        bb.putShort(TYPE_SHORT.toShort())
        bb.putInt(1)
        bb.putShort(value.toShort())
        bb.putShort(0)
    }

    private fun putLongEntry(bb: ByteBuffer, tag: Int, value: Int) {
        bb.putShort(tag.toShort())
        bb.putShort(TYPE_LONG.toShort())
        bb.putInt(1)
        bb.putInt(value)
    }

    private fun putShort3Entry(bb: ByteBuffer, tag: Int, dataOffset: Int) {
        bb.putShort(tag.toShort())
        bb.putShort(TYPE_SHORT.toShort())
        bb.putInt(3)
        bb.putInt(dataOffset)
    }

    private fun putRationalEntry(bb: ByteBuffer, tag: Int, dataOffset: Int) {
        bb.putShort(tag.toShort())
        bb.putShort(TYPE_RATIONAL.toShort())
        bb.putInt(1)
        bb.putInt(dataOffset)
    }

    private fun exposureRational(exposureNs: Long?): Pair<Int, Int> {
        val ns = exposureNs ?: 0L
        if (ns <= 0L) return 1 to 100
        return if (ns >= 1_000_000_000L) {
            val s = (ns / 1_000_000_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            s to 1
        } else {
            1 to (1_000_000_000L / ns).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun decimalRational(value: Double): Pair<Int, Int> {
        val scaled = (value * 1000.0).toInt().coerceAtLeast(1)
        return scaled to 1000
    }
}
