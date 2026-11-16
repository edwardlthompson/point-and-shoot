package dev.pointandshoot

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Downscaled RGB_565 frames for [android.media.FaceDetector] from Camera2 YUV_420_888.
 */
internal object YuvRgb565FaceFrames {

    const val MAX_LONG_SIDE = 320

    data class PreparedFrame(
        val bitmap: Bitmap,
        val preRotateW: Int,
        val preRotateH: Int,
        val yuvW: Int,
        val yuvH: Int,
        val rotationDegrees: Int,
    )

    fun prepare(
        image: Image,
        rotationDegrees: Int,
    ): PreparedFrame? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val yuvW = image.width
        val yuvH = image.height
        if (yuvW <= 1 || yuvH <= 1) return null
        val yPlane = image.planes.getOrNull(0) ?: return null
        val uPlane = image.planes.getOrNull(1) ?: return null
        val vPlane = image.planes.getOrNull(2) ?: return null
        val yBytes = copyPlaneTight(yPlane, yuvW, yuvH, luma = true) ?: return null
        val longSide = max(yuvW, yuvH)
        val scale = if (longSide <= MAX_LONG_SIDE) 1f else longSide / MAX_LONG_SIDE.toFloat()
        var outW = FaceDetectorGeometry.evenFloor((yuvW / scale).toInt())
        var outH = max(2, (yuvH / scale).toInt())
        val rgb565 = Bitmap.createBitmap(outW, outH, Bitmap.Config.RGB_565)
        fillRgb565(rgb565, yBytes, yuvW, yuvH, uPlane, vPlane)
        val rot = ((rotationDegrees % 360) + 360) % 360
        val upright =
            if (rot == 0) {
                rgb565
            } else {
                val matrix = Matrix().apply { postRotate(rot.toFloat()) }
                val rotated = Bitmap.createBitmap(rgb565, 0, 0, outW, outH, matrix, true)
                if (rotated !== rgb565) rgb565.recycle()
                rotated
            }
        val evenW = FaceDetectorGeometry.evenFloor(upright.width)
        val sized =
            if (evenW == upright.width) {
                upright
            } else {
                val cropped = Bitmap.createBitmap(upright, 0, 0, evenW, upright.height)
                if (cropped !== upright) upright.recycle()
                cropped
            }
        // FaceDetector requires a mutable RGB_565 bitmap.
        val finalBmp =
            if (sized.isMutable && sized.config == Bitmap.Config.RGB_565) {
                sized
            } else {
                val converted = sized.copy(Bitmap.Config.RGB_565, true)
                if (converted !== sized) sized.recycle()
                converted
            }
        return PreparedFrame(finalBmp, outW, outH, yuvW, yuvH, rot)
    }

    fun mouthLuma(bitmap: Bitmap, hint: FaceDetectorGeometry.FaceHint): ByteArray? {
        val w = hint.mouthRight - hint.mouthLeft
        val h = hint.mouthBottom - hint.mouthTop
        if (w < 6 || h < 3) return null
        val out = ByteArray(w * h)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, hint.mouthLeft, hint.mouthTop, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            out[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
        }
        return out
    }

    private fun copyPlaneTight(
        plane: Image.Plane,
        width: Int,
        height: Int,
        luma: Boolean,
    ): ByteArray? {
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride < 1 || rowStride < width) return null
        val buf = plane.buffer.duplicate()
        buf.clear()
        val sampleW = if (luma) width else (width + 1) / 2
        val sampleH = if (luma) height else (height + 1) / 2
        val out = ByteArray(sampleW * sampleH)
        val rowScratch = ByteArray(rowStride)
        for (y in 0 until sampleH) {
            val pos = y * rowStride
            if (pos >= buf.capacity()) return null
            buf.position(pos)
            val toRead = min(rowStride, buf.remaining())
            buf.get(rowScratch, 0, toRead)
            if (pixelStride == 1) {
                System.arraycopy(rowScratch, 0, out, y * sampleW, sampleW)
            } else {
                var d = y * sampleW
                var s = 0
                var x = 0
                while (x < sampleW && s < toRead) {
                    out[d] = rowScratch[s]
                    d++
                    s += pixelStride
                    x++
                }
            }
        }
        return out
    }

    private fun fillRgb565(
        dest: Bitmap,
        yBytes: ByteArray,
        yuvW: Int,
        yuvH: Int,
        uPlane: Image.Plane,
        vPlane: Image.Plane,
    ) {
        val outW = dest.width
        val outH = dest.height
        val packed = ShortArray(outW * outH)
        val uBuf = uPlane.buffer.duplicate()
        val vBuf = vPlane.buffer.duplicate()
        val uRow = uPlane.rowStride
        val vRow = vPlane.rowStride
        val uPix = uPlane.pixelStride
        val vPix = vPlane.pixelStride
        val uCap = uBuf.capacity()
        val vCap = vBuf.capacity()
        for (oy in 0 until outH) {
            val iy = (oy * yuvH) / outH
            val yRow = iy * yuvW
            val cy = iy / 2
            for (ox in 0 until outW) {
                val ix = (ox * yuvW) / outW
                val y = yBytes[yRow + ix].toInt() and 0xFF
                val cx = ix / 2
                val uIndex = cy * uRow + cx * uPix
                val vIndex = cy * vRow + cx * vPix
                val u = if (uIndex in 0 until uCap) uBuf.get(uIndex).toInt() and 0xFF else 128
                val v = if (vIndex in 0 until vCap) vBuf.get(vIndex).toInt() and 0xFF else 128
                packed[oy * outW + ox] = yuvToRgb565(y, u, v)
            }
        }
        val bb = ByteBuffer.allocate(packed.size * 2).order(ByteOrder.nativeOrder())
        bb.asShortBuffer().put(packed)
        bb.rewind()
        dest.copyPixelsFromBuffer(bb)
    }

    private fun yuvToRgb565(y: Int, u: Int, v: Int): Short {
        val c = y - 16
        val d = u - 128
        val e = v - 128
        val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
        val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
        val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
        val packed = ((r shr 3) shl 11) or ((g shr 2) shl 5) or (b shr 3)
        return packed.toShort()
    }
}
