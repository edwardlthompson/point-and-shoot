package dev.pointandshoot

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses row-strip TIFF tables (tags 273 / 279) for DNG loadability regression tests.
 */
internal object DngTiffStripTable {
    data class Snapshot(
        val width: Int,
        val height: Int,
        val stripOffsets: IntArray,
        val stripByteCounts: IntArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Snapshot) return false
            return width == other.width &&
                height == other.height &&
                stripOffsets.contentEquals(other.stripOffsets) &&
                stripByteCounts.contentEquals(other.stripByteCounts)
        }

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + stripOffsets.contentHashCode()
            result = 31 * result + stripByteCounts.contentHashCode()
            return result
        }
    }

    fun snapshot(bytes: ByteArray): Snapshot? {
        val ifd0 = ifd0Offset(bytes) ?: return null
        val entryCount = ByteBuffer.wrap(bytes, ifd0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
        var width: Int? = null
        var height: Int? = null
        var stripOffsetsOff: Int? = null
        var stripByteCountsOff: Int? = null
        var pos = ifd0 + 2
        repeat(entryCount) {
            if (pos + 12 > bytes.size) return null
            when (readU16(bytes, pos)) {
                256 -> width = readU32(bytes, pos + 8)
                257 -> height = readU32(bytes, pos + 8)
                273 -> stripOffsetsOff = resolveDataOffset(pos, 4, readU32(bytes, pos + 4), readU32(bytes, pos + 8))
                279 -> stripByteCountsOff = resolveDataOffset(pos, 4, readU32(bytes, pos + 4), readU32(bytes, pos + 8))
            }
            pos += 12
        }
        val w = width ?: return null
        val h = height ?: return null
        val so = stripOffsetsOff ?: return null
        val sc = stripByteCountsOff ?: return null
        if (so + h * 4 > bytes.size || sc + h * 4 > bytes.size) return null
        val offsets = IntArray(h) { readU32(bytes, so + it * 4) }
        val counts = IntArray(h) { readU32(bytes, sc + it * 4) }
        return Snapshot(w, h, offsets, counts)
    }

    private fun ifd0Offset(bytes: ByteArray): Int? {
        if (bytes.size < 8 || bytes[0] != 'I'.code.toByte() || bytes[1] != 'I'.code.toByte()) return null
        val hdr = ByteBuffer.wrap(bytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        if (hdr.getShort(2) != 42.toShort()) return null
        val ifd0 = hdr.getInt(4)
        return ifd0.takeIf { it >= 0 && ifd0 + 2 <= bytes.size }
    }

    private fun readU16(bytes: ByteArray, off: Int): Int =
        ByteBuffer.wrap(bytes, off, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff

    private fun readU32(bytes: ByteArray, off: Int): Int =
        ByteBuffer.wrap(bytes, off, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun resolveDataOffset(
        entryPos: Int,
        typeSize: Int,
        count: Int,
        valueOrOff: Int,
    ): Int? {
        val total = typeSize * count
        return if (total <= 4) entryPos + 8 else valueOrOff
    }
}
