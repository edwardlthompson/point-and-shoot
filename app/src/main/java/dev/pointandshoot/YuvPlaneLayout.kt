package dev.pointandshoot

/**
 * Camera2 [android.media.Image.Plane] addressing: valid bytes are
 * [start, end) ([ByteBuffer.position] / [ByteBuffer.limit]), not [ByteBuffer.capacity].
 *
 * Absolute [java.nio.ByteBuffer.get] is indexed from 0 and throws at `limit`, so callers
 * must add [start] and reject indexes `>= end`.
 */
object YuvPlaneLayout {

    /**
     * Exclusive end index required for a packed plane of [width]×[height] samples.
     * Null if strides cannot hold that rectangle.
     */
    fun neededEnd(
        start: Int,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
    ): Int? {
        if (start < 0) return null
        if (width <= 0 || height <= 0) return null
        if (rowStride <= 0 || pixelStride <= 0) return null
        val lastRowBytes = (width - 1) * pixelStride + 1
        if (rowStride < lastRowBytes) return null
        return start + rowStride * (height - 1) + lastRowBytes
    }

    fun inRange(index: Int, start: Int, end: Int): Boolean = index in start until end
}
