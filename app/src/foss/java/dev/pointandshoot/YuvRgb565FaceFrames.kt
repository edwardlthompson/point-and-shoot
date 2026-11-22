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
 *
 * Plane reads follow [HfrYuvImageCopier]: valid bytes are buffer `position()`..`limit()`,
 * not `capacity()`. Absolute [ByteBuffer.get] throws at `limit`.
 */
internal object YuvRgb565FaceFrames {

    const val MAX_LONG_SIDE = 320
    private const val NEUTRAL_CHROMA = 128
    private const val LUMA_OFFSET = 16
    private const val YUV_Y_SCALE = 298
    private const val YUV_R_V = 409
    private const val YUV_G_U = 100
    private const val YUV_G_V = 208
    private const val YUV_B_U = 516
    private const val YUV_ROUND = 128
    private const val YUV_SHIFT = 8
    private const val BYTE_MASK = 0xFF
    private const val RGB565_R_SHIFT = 3
    private const val RGB565_G_SHIFT = 2
    private const val RGB565_PACK_R = 11
    private const val RGB565_PACK_G = 5
    private const val LUMA_R = 299
    private const val LUMA_G = 587
    private const val LUMA_B = 114
    private const val LUMA_DEN = 1000
    private const val MIN_MOUTH_W = 6
    private const val MIN_MOUTH_H = 3
    private const val MIN_DIM = 2
    private const val DEGREES_CIRCLE = 360

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
        val uvW = (yuvW + 1) / 2
        val uvH = (yuvH + 1) / 2
        val yBytes = copyPlaneTight(yPlane, yuvW, yuvH) ?: return null
        val uBytes = copyPlaneTight(uPlane, uvW, uvH) ?: return null
        val vBytes = copyPlaneTight(vPlane, uvW, uvH) ?: return null
        val longSide = max(yuvW, yuvH)
        val scale = if (longSide <= MAX_LONG_SIDE) 1f else longSide / MAX_LONG_SIDE.toFloat()
        val outW = FaceDetectorGeometry.evenFloor((yuvW / scale).toInt())
        val outH = max(MIN_DIM, (yuvH / scale).toInt())
        val rgb565 = Bitmap.createBitmap(outW, outH, Bitmap.Config.RGB_565)
        fillRgb565(rgb565, yBytes, uBytes, vBytes, yuvW, yuvH, uvW)
        val rot = ((rotationDegrees % DEGREES_CIRCLE) + DEGREES_CIRCLE) % DEGREES_CIRCLE
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
        if (w < MIN_MOUTH_W || h < MIN_MOUTH_H) return null
        val out = ByteArray(w * h)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, hint.mouthLeft, hint.mouthTop, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and BYTE_MASK
            val g = (c shr 8) and BYTE_MASK
            val b = c and BYTE_MASK
            out[i] = ((r * LUMA_R + g * LUMA_G + b * LUMA_B) / LUMA_DEN).toByte()
        }
        return out
    }

    /**
     * Tight packed copy of one plane. Uses [ByteBuffer.position] as the sample origin
     * and [ByteBuffer.limit] as the exclusive end — never [ByteBuffer.capacity] or [ByteBuffer.clear].
     */
    private fun copyPlaneTight(
        plane: Image.Plane,
        width: Int,
        height: Int,
    ): ByteArray? {
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val buf = plane.buffer.duplicate()
        val start = buf.position()
        val end = buf.limit()
        val neededEnd = YuvPlaneLayout.neededEnd(start, width, height, rowStride, pixelStride)
            ?: return null
        if (neededEnd > end) return null
        val out = ByteArray(width * height)
        val rowScratch = ByteArray(rowStride)
        for (y in 0 until height) {
            val pos = start + y * rowStride
            if (!YuvPlaneLayout.inRange(pos, start, end)) return null
            buf.position(pos)
            val toRead = min(rowStride, end - pos)
            buf.get(rowScratch, 0, toRead)
            if (pixelStride == 1) {
                System.arraycopy(rowScratch, 0, out, y * width, min(width, toRead))
            } else {
                var d = y * width
                var s = 0
                var x = 0
                while (x < width && s < toRead) {
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
        uBytes: ByteArray,
        vBytes: ByteArray,
        yuvW: Int,
        yuvH: Int,
        uvW: Int,
    ) {
        val outW = dest.width
        val outH = dest.height
        val packed = ShortArray(outW * outH)
        val uvLast = uBytes.size
        for (oy in 0 until outH) {
            val iy = (oy * yuvH) / outH
            val yRow = iy * yuvW
            val cy = iy / 2
            for (ox in 0 until outW) {
                val ix = (ox * yuvW) / outW
                val y = yBytes[yRow + ix].toInt() and BYTE_MASK
                val uvIndex = cy * uvW + ix / 2
                val u =
                    if (uvIndex in 0 until uvLast) {
                        uBytes[uvIndex].toInt() and BYTE_MASK
                    } else {
                        NEUTRAL_CHROMA
                    }
                val v =
                    if (uvIndex in 0 until vBytes.size) {
                        vBytes[uvIndex].toInt() and BYTE_MASK
                    } else {
                        NEUTRAL_CHROMA
                    }
                packed[oy * outW + ox] = yuvToRgb565(y, u, v)
            }
        }
        val bb = ByteBuffer.allocate(packed.size * 2).order(ByteOrder.nativeOrder())
        bb.asShortBuffer().put(packed)
        bb.rewind()
        dest.copyPixelsFromBuffer(bb)
    }

    private fun yuvToRgb565(y: Int, u: Int, v: Int): Short {
        val c = y - LUMA_OFFSET
        val d = u - NEUTRAL_CHROMA
        val e = v - NEUTRAL_CHROMA
        val r = ((YUV_Y_SCALE * c + YUV_R_V * e + YUV_ROUND) shr YUV_SHIFT).coerceIn(0, BYTE_MASK)
        val g =
            ((YUV_Y_SCALE * c - YUV_G_U * d - YUV_G_V * e + YUV_ROUND) shr YUV_SHIFT)
                .coerceIn(0, BYTE_MASK)
        val b = ((YUV_Y_SCALE * c + YUV_B_U * d + YUV_ROUND) shr YUV_SHIFT).coerceIn(0, BYTE_MASK)
        val packed =
            ((r shr RGB565_R_SHIFT) shl RGB565_PACK_R) or
                ((g shr RGB565_G_SHIFT) shl RGB565_PACK_G) or
                (b shr RGB565_R_SHIFT)
        return packed.toShort()
    }
}
