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
        if (w <= 0 || h <= 0) return null
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uvW = w / 2
        val uvH = h / 2
        val yOut = ByteArray(w * h)
        val uOut = ByteArray(uvW * uvH)
        val vOut = ByteArray(uvW * uvH)
        copyPlane(yPlane, w, h, yPlane.pixelStride, yPlane.rowStride, yOut, w)
        copyPlane(uPlane, uvW, uvH, uPlane.pixelStride, uPlane.rowStride, uOut, uvW)
        copyPlane(vPlane, uvW, uvH, vPlane.pixelStride, vPlane.rowStride, vOut, uvW)
        return HfrYuvMonitorFrame(w, h, textureRotationDeg, textureCrop, yOut, uOut, vOut)
    }

    private fun copyPlane(
        plane: Image.Plane,
        width: Int,
        height: Int,
        pixelStride: Int,
        rowStride: Int,
        out: ByteArray,
        outRowStride: Int,
    ) {
        val src = plane.buffer
        src.rewind()
        if (pixelStride == 1 && rowStride == width) {
            src.get(out, 0, width * height.coerceAtMost(out.size))
            return
        }
        var dstOff = 0
        for (row in 0 until height) {
            var srcOff = row * rowStride
            for (col in 0 until width) {
                if (dstOff >= out.size) return
                out[dstOff++] = src.get(srcOff)
                srcOff += pixelStride
            }
            dstOff += (outRowStride - width).coerceAtLeast(0)
        }
    }
}
