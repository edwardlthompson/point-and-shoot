package dev.pointandshoot

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight decoder for the app's own uncompressed little-endian RGB16 TIFF output.
 * Used by the built-in gallery when platform thumbnail decode cannot open TIFF.
 */
object Tiff16PreviewDecoder {
    private const val TIFF_MAGIC = 42
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4

    private const val TAG_IMAGE_WIDTH = 256
    private const val TAG_IMAGE_LENGTH = 257
    private const val TAG_BITS_PER_SAMPLE = 258
    private const val TAG_STRIP_OFFSETS = 273
    private const val TAG_SAMPLES_PER_PIXEL = 277
    private const val TAG_STRIP_BYTE_COUNTS = 279
    private const val TAG_PLANAR_CONFIGURATION = 284

    private data class Entry(
        val type: Int,
        val count: Int,
        val valueOrOffset: Int,
    )

    fun decodeThumbnail(bytes: ByteArray, maxPx: Int): Bitmap? {
        if (bytes.size < 16) return null
        if (bytes[0].toInt() != 0x49 || bytes[1].toInt() != 0x49) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
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

        val width = readIntValue(bb, map[TAG_IMAGE_WIDTH]) ?: return null
        val height = readIntValue(bb, map[TAG_IMAGE_LENGTH]) ?: return null
        if (width <= 0 || height <= 0) return null
        val spp = readIntValue(bb, map[TAG_SAMPLES_PER_PIXEL]) ?: 3
        if (spp != 3) return null
        val planar = readIntValue(bb, map[TAG_PLANAR_CONFIGURATION]) ?: 1
        if (planar != 1) return null
        val bitsOk = readBitsPerSample16(bb, bytes, map[TAG_BITS_PER_SAMPLE])
        if (!bitsOk) return null

        val stripOffset = readIntValue(bb, map[TAG_STRIP_OFFSETS]) ?: return null
        val stripBytes = readIntValue(bb, map[TAG_STRIP_BYTE_COUNTS]) ?: return null
        if (stripOffset <= 0 || stripBytes <= 0) return null
        if (stripOffset + stripBytes > bytes.size) return null

        val sample = computeSample(width, height, maxPx)
        val outW = (width / sample).coerceAtLeast(1)
        val outH = (height / sample).coerceAtLeast(1)
        val out = IntArray(outW * outH)
        var oi = 0
        for (y in 0 until outH) {
            val sy = y * sample
            for (x in 0 until outW) {
                val sx = x * sample
                val src = stripOffset + ((sy * width + sx) * 6)
                if (src + 5 >= bytes.size) return null
                val r16 = (bytes[src].toInt() and 0xFF) or ((bytes[src + 1].toInt() and 0xFF) shl 8)
                val g16 = (bytes[src + 2].toInt() and 0xFF) or ((bytes[src + 3].toInt() and 0xFF) shl 8)
                val b16 = (bytes[src + 4].toInt() and 0xFF) or ((bytes[src + 5].toInt() and 0xFF) shl 8)
                val r8 = (r16 ushr 8) and 0xFF
                val g8 = (g16 ushr 8) and 0xFF
                val b8 = (b16 ushr 8) and 0xFF
                out[oi++] = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
            }
        }
        return Bitmap.createBitmap(out, outW, outH, Bitmap.Config.ARGB_8888)
    }

    private fun readIntValue(bb: ByteBuffer, e: Entry?): Int? {
        e ?: return null
        return when (e.type) {
            TYPE_SHORT -> e.valueOrOffset and 0xFFFF
            TYPE_LONG -> e.valueOrOffset
            else -> null
        }
    }

    private fun readBitsPerSample16(
        bb: ByteBuffer,
        bytes: ByteArray,
        e: Entry?,
    ): Boolean {
        e ?: return false
        if (e.type != TYPE_SHORT || e.count != 3) return false
        val off = e.valueOrOffset
        if (off < 0 || off + 6 > bytes.size) return false
        val b0 = bb.getShort(off).toInt() and 0xFFFF
        val b1 = bb.getShort(off + 2).toInt() and 0xFFFF
        val b2 = bb.getShort(off + 4).toInt() and 0xFFFF
        return b0 == 16 && b1 == 16 && b2 == 16
    }

    private fun computeSample(width: Int, height: Int, maxPx: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxPx) {
            sample *= 2
        }
        return sample
    }
}
