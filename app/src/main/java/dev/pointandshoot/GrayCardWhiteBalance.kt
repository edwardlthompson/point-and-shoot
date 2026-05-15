package dev.pointandshoot

import android.graphics.ImageFormat
import android.media.Image
import kotlin.math.max
import kotlin.math.min

/**
 * Estimates neutral RGB preview gains from a **YUV_420_888** frame by averaging chroma
 * in a center ROI (fill the center of the finder with an ~18% gray card, then tap
 * **Gray card WB** in the WB menu). Returns `{r,g,b}` with **g = 1f** for the GLES preview path.
 */
object GrayCardWhiteBalance {

    private const val NEUTRAL = 128f
    private const val GAIN_MAX = 1.55f
    private const val GAIN_MIN = 0.55f

    fun estimateRgbGainsFromYuv420888OrNull(image: Image): FloatArray? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val w = image.width
        val h = image.height
        if (w < 32 || h < 32) return null
        val planes = image.planes
        if (planes.size < 3) return null
        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuf = uPlane.buffer.duplicate()
        val vBuf = vPlane.buffer.duplicate()
        val uRow = uPlane.rowStride
        val vRow = vPlane.rowStride
        val uPs = uPlane.pixelStride.coerceAtLeast(1)
        val vPs = vPlane.pixelStride.coerceAtLeast(1)

        val x0 = w / 4
        val y0 = h / 4
        val x1 = w * 3 / 4
        val y1 = h * 3 / 4

        var uSum = 0.0
        var vSum = 0.0
        var n = 0
        var y = y0
        while (y < y1) {
            var x = x0
            val cy = y / 2
            while (x < x1) {
                val cx = x / 2
                val uPos = cy * uRow + cx * uPs
                val vPos = cy * vRow + cx * vPs
                if (uPos >= 0 && uPos < uBuf.limit() && vPos >= 0 && vPos < vBuf.limit()) {
                    uSum += (uBuf.get(uPos).toInt() and 0xff)
                    vSum += (vBuf.get(vPos).toInt() and 0xff)
                    n++
                }
                x += 4
            }
            y += 4
        }
        if (n < 8) return null
        val uAvg = (uSum / n).toFloat()
        val vAvg = (vSum / n).toFloat()
        val du = uAvg - NEUTRAL
        val dv = vAvg - NEUTRAL
        val r = clampGain(1f + 0.0068f * du - 0.0049f * dv)
        val b = clampGain(1f - 0.0077f * du + 0.0062f * dv)
        return floatArrayOf(r, 1f, b)
    }

    private fun clampGain(v: Float): Float = max(GAIN_MIN, min(GAIN_MAX, v))
}
