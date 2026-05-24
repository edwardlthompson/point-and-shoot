package dev.pointandshoot

import android.graphics.ImageFormat
import android.media.Image
import android.util.Log

/** Copies [ImageFormat.YUV_420_888] from an HS monitor [android.media.ImageReader]. */
object HfrYuvImageCopier {
    private const val TAG = "PNS.HfrYuvMonitor"

    fun copy(
        image: Image,
        textureRotationDeg: Int = 0,
        textureCrop: HfrMonitorTextureCrop = HfrMonitorTextureCrop.FULL,
    ): HfrYuvMonitorFrame? {
        if (image.format != ImageFormat.YUV_420_888) {
            Log.w(TAG, "skip image format=${image.format}")
            return null
        }
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return null
        return try {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val uvW = w / 2
            val uvH = h / 2
            val yOut = ByteArray(w * h)
            val uOut = ByteArray(uvW * uvH)
            val vOut = ByteArray(uvW * uvH)
            if (!copyPlane(yPlane, w, h, yOut, w)) return null
            if (!copyPlane(uPlane, uvW, uvH, uOut, uvW)) return null
            if (!copyPlane(vPlane, uvW, uvH, vOut, uvW)) return null
            HfrYuvMonitorFrame(w, h, textureRotationDeg, textureCrop, yOut, uOut, vOut)
        } catch (e: Exception) {
            Log.w(TAG, "copy failed ${w}x$h: ${e.message}")
            null
        }
    }

    private fun copyPlane(
        plane: Image.Plane,
        width: Int,
        height: Int,
        out: ByteArray,
        outRowStride: Int,
    ): Boolean {
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (width <= 0 || height <= 0 || pixelStride <= 0 || rowStride <= 0) return false
        val src = plane.buffer
        src.rewind()
        val lastRowBytes = (width - 1) * pixelStride + 1
        val minRemaining = rowStride * (height - 1) + lastRowBytes
        if (src.remaining() < minRemaining) {
            Log.w(
                TAG,
                "plane buffer short remaining=${src.remaining()} need=$minRemaining " +
                    "w=$width h=$height rowStride=$rowStride pixelStride=$pixelStride",
            )
            return false
        }
        if (pixelStride == 1 && rowStride == width) {
            val bytes = rowStride * height
            if (bytes > out.size) return false
            src.get(out, 0, bytes)
            return true
        }
        var dstOff = 0
        for (row in 0 until height) {
            var srcOff = row * rowStride
            for (col in 0 until width) {
                if (dstOff >= out.size) return false
                out[dstOff++] = src.get(srcOff)
                srcOff += pixelStride
            }
            dstOff += (outRowStride - width).coerceAtLeast(0)
        }
        return true
    }
}
