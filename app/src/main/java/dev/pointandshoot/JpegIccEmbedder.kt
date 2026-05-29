package dev.pointandshoot

/**
 * Sprint **15.17** — insert an ICC profile into a JPEG via APP2 (`ICC_PROFILE`) segments.
 *
 * Runs after [androidx.exifinterface.media.ExifInterface.saveAttributes] so EXIF and ICC coexist.
 * Replaces any prior APP2 `ICC_PROFILE` chunks (ExifInterface may embed sRGB first).
 * Not used on DNG (row-strip TIFF).
 */
object JpegIccEmbedder {
    private val ICC_PROFILE_ID: ByteArray =
        byteArrayOf(
            'I'.code.toByte(),
            'C'.code.toByte(),
            'C'.code.toByte(),
            '_'.code.toByte(),
            'P'.code.toByte(),
            'R'.code.toByte(),
            'O'.code.toByte(),
            'F'.code.toByte(),
            'I'.code.toByte(),
            'L'.code.toByte(),
            'E'.code.toByte(),
            0,
        )

    /** Max ICC bytes per APP2 segment (JPEG length field limit). */
    private const val MAX_ICC_CHUNK_BYTES = 65_519

    fun embedAfterSoi(jpeg: ByteArray, iccProfile: ByteArray): ByteArray {
        if (iccProfile.isEmpty()) return jpeg
        if (jpeg.size < 2 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return jpeg

        val stripped = stripApp2IccSegments(jpeg)
        val chunks = iccProfile.toList().chunked(MAX_ICC_CHUNK_BYTES)
        val chunkCount = chunks.size
        val segments =
            chunks.mapIndexed { index, chunk ->
                buildApp2IccSegment(chunk.toByteArray(), chunkIndex = index + 1, chunkCount = chunkCount)
            }
        val segmentsSize = segments.sumOf { it.size }
        val out = ByteArray(2 + segmentsSize + (stripped.size - 2))
        out[0] = stripped[0]
        out[1] = stripped[1]
        var writeAt = 2
        for (segment in segments) {
            System.arraycopy(segment, 0, out, writeAt, segment.size)
            writeAt += segment.size
        }
        System.arraycopy(stripped, 2, out, writeAt, stripped.size - 2)
        return out
    }

    internal fun stripApp2IccSegments(jpeg: ByteArray): ByteArray {
        if (jpeg.size < 4) return jpeg
        val kept = ArrayList<Byte>()
        kept.add(jpeg[0])
        kept.add(jpeg[1])
        var i = 2
        while (i + 3 < jpeg.size) {
            if (jpeg[i] != 0xFF.toByte()) {
                kept.addAll(jpeg.sliceArray(i until jpeg.size).toList())
                break
            }
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xD9) {
                kept.add(jpeg[i])
                kept.add(jpeg[i + 1])
                break
            }
            if (marker == 0xD8 || i + 3 >= jpeg.size) {
                kept.addAll(jpeg.sliceArray(i until jpeg.size).toList())
                break
            }
            val segLen = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            if (segLen < 2 || i + 2 + segLen > jpeg.size) {
                kept.addAll(jpeg.sliceArray(i until jpeg.size).toList())
                break
            }
            val segmentEnd = i + 2 + segLen
            val isIccApp2 =
                marker == 0xE2 &&
                    i + 4 + ICC_PROFILE_ID.size <= segmentEnd &&
                    ICC_PROFILE_ID.indices.all { j -> jpeg[i + 4 + j] == ICC_PROFILE_ID[j] }
            if (!isIccApp2) {
                for (b in i until segmentEnd) {
                    kept.add(jpeg[b])
                }
            }
            i = segmentEnd
        }
        return kept.toByteArray()
    }

    private fun buildApp2IccSegment(
        iccChunk: ByteArray,
        chunkIndex: Int,
        chunkCount: Int,
    ): ByteArray {
        val payloadSize = ICC_PROFILE_ID.size + 2 + iccChunk.size
        val lengthField = 2 + payloadSize
        val seg = ByteArray(2 + lengthField)
        seg[0] = 0xFF.toByte()
        seg[1] = 0xE2.toByte()
        seg[2] = ((lengthField shr 8) and 0xFF).toByte()
        seg[3] = (lengthField and 0xFF).toByte()
        var off = 4
        System.arraycopy(ICC_PROFILE_ID, 0, seg, off, ICC_PROFILE_ID.size)
        off += ICC_PROFILE_ID.size
        seg[off++] = chunkIndex.toByte()
        seg[off++] = chunkCount.toByte()
        System.arraycopy(iccChunk, 0, seg, off, iccChunk.size)
        return seg
    }
}
