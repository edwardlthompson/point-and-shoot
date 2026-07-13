@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Grayscale thumbnail from uncompressed Bayer DNG row strips (typical [android.hardware.camera2.DngCreator]
 * output). Used when the platform cannot [BitmapFactory]-decode DNG and no JPEG SubIFD exists.
 */
object DngBayerPreviewDecoder {
    private const val TIFF_MAGIC = 42
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4

    private const val TAG_IMAGE_WIDTH = 256
    private const val TAG_IMAGE_LENGTH = 257
    private const val TAG_BITS_PER_SAMPLE = 258
    private const val TAG_COMPRESSION = 259
    private const val TAG_STRIP_OFFSETS = 273
    private const val TAG_SAMPLES_PER_PIXEL = 277
    private const val TAG_ROWS_PER_STRIP = 278
    private const val TAG_STRIP_BYTE_COUNTS = 279
    private const val TAG_PHOTOMETRIC = 262

    private data class Entry(
        val type: Int,
        val count: Int,
        val valueOrOffset: Int,
    )

    /**
     * Decodes a small ARGB bitmap from Bayer green (rough preview). Returns null when the DNG is
     * not an uncompressed single-sample row-strip layout.
     */
    @Suppress("CyclomaticComplexMethod", "ComplexCondition", "ReturnCount")
    fun decodeThumbnail(bytes: ByteArray, maxPx: Int): Bitmap? {
        if (bytes.size < 16) return null
        val little = bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte()
        val big = bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte()
        if (!little && !big) return null
        val order = if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val bb = ByteBuffer.wrap(bytes).order(order)
        if (bb.getShort(2).toInt() != TIFF_MAGIC) return null
        val ifdOffset = bb.getInt(4)
        if (ifdOffset <= 0 || ifdOffset >= bytes.size - 2) return null
        val entryCount = bb.getShort(ifdOffset).toInt() and 0xFFFF
        val map = mutableMapOf<Int, Entry>()
        var p = ifdOffset + 2
        repeat(entryCount) {
            if (p + 12 > bytes.size) return@repeat
            val tag = bb.getShort(p).toInt() and 0xFFFF
            val type = bb.getShort(p + 2).toInt() and 0xFFFF
            val count = bb.getInt(p + 4)
            val value = bb.getInt(p + 8)
            map[tag] = Entry(type, count, value)
            p += 12
        }

        val width = readInt(bb, map[TAG_IMAGE_WIDTH]) ?: return null
        val height = readInt(bb, map[TAG_IMAGE_LENGTH]) ?: return null
        if (width <= 0 || height <= 0 || width > 16384 || height > 16384) return null
        val bits = readInt(bb, map[TAG_BITS_PER_SAMPLE]) ?: 16
        if (bits != 16 && bits != 10 && bits != 12) return null
        val compression = readInt(bb, map[TAG_COMPRESSION]) ?: 1
        if (compression != 1) return null // uncompressed only
        val spp = readInt(bb, map[TAG_SAMPLES_PER_PIXEL]) ?: 1
        if (spp != 1) return null
        val photometric = readInt(bb, map[TAG_PHOTOMETRIC])
        // 32803 = CFA, 34892 = LinearRaw — both OK for green subsample preview
        if (photometric != null && photometric != 32803 && photometric != 34892 && photometric != 1) {
            return null
        }

        val rowsPerStrip = (readInt(bb, map[TAG_ROWS_PER_STRIP]) ?: height).coerceAtLeast(1)
        val stripCount = (height + rowsPerStrip - 1) / rowsPerStrip
        val offsets = readArray(bb, bytes, map[TAG_STRIP_OFFSETS], stripCount) ?: return null
        val counts = readArray(bb, bytes, map[TAG_STRIP_BYTE_COUNTS], stripCount) ?: return null
        if (offsets.isEmpty() || counts.isEmpty()) return null

        val sample = computeSample(width, height, maxPx)
        val outW = (width / sample).coerceAtLeast(1)
        val outH = (height / sample).coerceAtLeast(1)
        val pixels = IntArray(outW * outH)
        val bytesPerPixel = 2
        val rowBytes = width * bytesPerPixel

        var oi = 0
        for (y in 0 until outH) {
            val sy = (y * sample).coerceAtMost(height - 1)
            val stripIndex = sy / rowsPerStrip
            if (stripIndex !in offsets.indices) return null
            val rowInStrip = sy % rowsPerStrip
            val stripOff = offsets[stripIndex]
            val stripLen = counts[stripIndex]
            if (stripOff <= 0 || stripLen <= 0) return null
            val rowOff = stripOff + rowInStrip * rowBytes
            if (rowOff + rowBytes > bytes.size) return null
            for (x in 0 until outW) {
                // Prefer a green site in RGGB/BGGR (odd,odd or even,even depending on pattern).
                // Sampling (sx|1, sy|1) lands on G for both common CFA layouts often enough for a thumb.
                val sx = ((x * sample) or 1).coerceAtMost(width - 1)
                val src = rowOff + sx * bytesPerPixel
                val v16 =
                    if (order == ByteOrder.LITTLE_ENDIAN) {
                        (bytes[src].toInt() and 0xFF) or ((bytes[src + 1].toInt() and 0xFF) shl 8)
                    } else {
                        ((bytes[src].toInt() and 0xFF) shl 8) or (bytes[src + 1].toInt() and 0xFF)
                    }
                // Black-level-ish floor ~64 on this fleet; stretch midtones for visible thumb.
                val g = (((v16 - 64).coerceAtLeast(0) * 255) / 1024).coerceIn(0, 255)
                pixels[oi++] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        return Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, outW, 0, 0, outW, outH)
        }
    }

    private fun computeSample(width: Int, height: Int, maxPx: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxPx) {
            sample *= 2
        }
        // Prefer even sample so Bayer phase stays roughly aligned.
        if (sample > 1 && sample % 2 != 0) sample++
        return sample
    }

    @Suppress("UnusedParameter")
    private fun readInt(bb: ByteBuffer, entry: Entry?): Int? {
        entry ?: return null
        return when (entry.type) {
            TYPE_SHORT -> entry.valueOrOffset and 0xFFFF
            TYPE_LONG -> entry.valueOrOffset
            else -> null
        }
    }

    private fun readArray(
        bb: ByteBuffer,
        bytes: ByteArray,
        entry: Entry?,
        expected: Int,
    ): IntArray? {
        entry ?: return null
        val count = entry.count.coerceAtMost(expected.coerceAtLeast(1))
        if (count <= 0) return null
        val typeSize = if (entry.type == TYPE_SHORT) 2 else 4
        val nbytes = typeSize * count
        val out = IntArray(count)
        if (nbytes <= 4) {
            out[0] = if (entry.type == TYPE_SHORT) entry.valueOrOffset and 0xFFFF else entry.valueOrOffset
            return if (count == 1) out else null
        }
        val base = entry.valueOrOffset
        if (base < 0 || base + nbytes > bytes.size) return null
        for (i in 0 until count) {
            out[i] =
                if (entry.type == TYPE_SHORT) {
                    bb.getShort(base + i * 2).toInt() and 0xFFFF
                } else {
                    bb.getInt(base + i * 4)
                }
        }
        return out
    }
}
