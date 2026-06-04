package dev.pointandshoot

import android.media.Image
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * P&S documented MCRAW-class container (AltReferenceApp `.mcraw` is proprietary — inspiration only).
 *
 * Layout:
 * - Header: magic `PNMRAWV1`, version, width, height, [ImageFormat], reserved
 * - Frames: `frameIndex` (int), `timestampNs` (long), `payloadLen` (int), raw bytes
 * - Footer: magic `PNMRAWEND`, `frameCount` (int), `totalPayloadBytes` (long)
 */
class RawVideoWriter(
    private val out: OutputStream,
    val width: Int,
    val height: Int,
    val imageFormat: Int,
    private val dualIsoMerge: Boolean = false,
) {
    private var frameCount: Int = 0
    private var totalPayloadBytes: Long = 0
    private var closed: Boolean = false

    init {
        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.put(MAGIC_HEADER.toByteArray(Charsets.US_ASCII))
        header.putInt(VERSION)
        header.putInt(width)
        header.putInt(height)
        header.putInt(imageFormat)
        header.putInt(0) // reserved
        out.write(header.array(), 0, header.position())
    }

    @Synchronized
    fun appendFrame(image: Image, timestampNs: Long) {
        check(!closed) { "writer closed" }
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate().order(ByteOrder.nativeOrder())
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val rowBytes = width * pixelStride
        val payload = ByteArray(rowBytes * height)
        var dst = 0
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            if (pixelStride == 1) {
                buffer.get(payload, dst, rowBytes)
                dst += rowBytes
            } else {
                for (col in 0 until width) {
                    payload[dst++] = buffer.get(row * rowStride + col * pixelStride)
                }
            }
        }
        val frameHeader = ByteBuffer.allocate(FRAME_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        frameHeader.putInt(frameCount)
        frameHeader.putLong(timestampNs)
        val writePayload = if (dualIsoMerge) DualIsoVideoMerger.merge(payload) else payload
        frameHeader.putInt(writePayload.size)
        out.write(frameHeader.array(), 0, frameHeader.position())
        out.write(writePayload)
        frameCount++
        totalPayloadBytes += writePayload.size
    }

    @Synchronized
    fun finish(): Int {
        if (closed) return frameCount
        closed = true
        val footer = ByteBuffer.allocate(FOOTER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        footer.put(MAGIC_FOOTER.toByteArray(Charsets.US_ASCII))
        footer.putInt(frameCount)
        footer.putLong(totalPayloadBytes)
        out.write(footer.array(), 0, footer.position())
        out.flush()
        return frameCount
    }

    companion object {
        const val MAGIC_HEADER: String = "PNMRAWV1"
        const val MAGIC_FOOTER: String = "PNMRAWEND"
        const val VERSION: Int = 1
        private const val HEADER_BYTES: Int = 28
        private const val FRAME_HEADER_BYTES: Int = 16
        private const val FOOTER_BYTES: Int = 24

        fun isValidFooter(magic: String, frameCount: Int): Boolean =
            magic == MAGIC_FOOTER && frameCount > 0
    }
}
