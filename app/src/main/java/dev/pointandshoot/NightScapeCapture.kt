package dev.pointandshoot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import kotlin.math.abs

/**
 * Sprint **15.29** — Night dial multi-frame stack: burst JPEG @ HAL max sensitivity → align → blend → AVIF.
 *
 * Design: [docs/PNS_TECHNICAL_SETTINGS.md] Night dial · [BUILD_PLAN.md] 15.29.
 */
object NightScapeCapture {
    const val TAG = "PNS.NightScape"

    /** Downsample factor for block-matcher luma (full-res shift = estimate × [DOWNSAMPLE]). */
    private const val DOWNSAMPLE = 8

    /** Decode long edge for stack math (avoids full-sensor RGB888 OOM on fleet devices). */
    private const val STACK_DECODE_MAX_LONG_EDGE = 2048

    /** Search radius on downsampled grid (± pixels). */
    private const val SEARCH_RADIUS_DS = 12

    /** Gap between burst frames so the HAL can settle (ms). */
    const val FRAME_GAP_MS = 400L

    /** Per-frame exposure cap — HAL [SENSOR_INFO_EXPOSURE_TIME_RANGE] upper can be tens of seconds. */
    private const val MAX_BURST_EXPOSURE_NS = 1_000_000_000L

    data class FramePayload(
        val jpegBytes: ByteArray,
        val captureResult: TotalCaptureResult,
    )

    data class StackedRgb(
        val rgb888: ByteArray,
        val width: Int,
        val height: Int,
    )

    fun normalizeFrameCount(raw: Int): Int =
        AdvancedCaptureSettings.normalizeNightScapeFrameCount(raw)

    /** Manual AE at sensor max ISO + max exposure (night burst frames). */
    fun applyMaxHalSensitivity(
        builder: CaptureRequest.Builder,
        chars: CameraCharacteristics,
    ) {
        val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        if (!aeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF) ||
            isoRange == null ||
            expRange == null
        ) {
            Log.w(TAG, "maxHalSensitivity skipped: AE off or ranges unavailable")
            return
        }
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, isoRange.upper)
        val expNs = minOf(expRange.upper, MAX_BURST_EXPOSURE_NS)
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expNs)
        val iso = isoRange.upper
        val expUs = expNs / 1000L
        Log.i(TAG, "maxHalSensitivity iso=$iso expUs=$expUs")
    }

    /** Decode JPEG bytes → oriented RGB888 (no LUT; applied at save). */
    fun decodeJpegRgb888(
        jpegBytes: ByteArray,
        maxLongEdge: Int = STACK_DECODE_MAX_LONG_EDGE,
    ): StackedRgb? {
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxLongEdge) {
            sample *= 2
        }
        val decoded =
            BitmapFactory.decodeByteArray(
                jpegBytes,
                0,
                jpegBytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return null
        val w = decoded.width
        val h = decoded.height
        if (w <= 0 || h <= 0) return null
        val rgb = bitmapToRgb888(decoded)
        decoded.recycle()
        return StackedRgb(rgb, w, h)
    }

    /**
     * Align each frame to the first with a coarse block matcher, then average-blend RGB888.
     */
    fun stackJpegFrames(jpegFrames: List<ByteArray>): StackedRgb? {
        if (jpegFrames.isEmpty()) return null
        val decoded = ArrayList<StackedRgb>(jpegFrames.size)
        for (bytes in jpegFrames) {
            val frame = decodeJpegRgb888(bytes) ?: continue
            if (decoded.isEmpty()) {
                decoded += frame
            } else {
                val ref = decoded.first()
                if (frame.width != ref.width || frame.height != ref.height) {
                    continue
                }
                decoded += frame
            }
        }
        if (decoded.isEmpty()) return null
        val w = decoded.first().width
        val h = decoded.first().height
        val refLuma = downsampleLuma(decoded.first().rgb888, w, h, DOWNSAMPLE)
        val dsW = (w + DOWNSAMPLE - 1) / DOWNSAMPLE
        val dsH = (h + DOWNSAMPLE - 1) / DOWNSAMPLE
        val shifts = ArrayList<Pair<Int, Int>>(decoded.size)
        shifts += 0 to 0
        for (i in 1 until decoded.size) {
            val candLuma = downsampleLuma(decoded[i].rgb888, w, h, DOWNSAMPLE)
            val (dxDs, dyDs) = estimateShiftDs(refLuma, dsW, dsH, candLuma, dsW, dsH)
            shifts += (dxDs * DOWNSAMPLE) to (dyDs * DOWNSAMPLE)
        }
        val out = averageBlend(decoded.map { it.rgb888 }, w, h, shifts)
        return StackedRgb(out, w, h)
    }

    internal fun downsampleLuma(
        rgb888: ByteArray,
        width: Int,
        height: Int,
        factor: Int,
    ): IntArray {
        val dsW = (width + factor - 1) / factor
        val dsH = (height + factor - 1) / factor
        val out = IntArray(dsW * dsH)
        for (dy in 0 until dsH) {
            for (dx in 0 until dsW) {
                var sum = 0
                var count = 0
                val y0 = dy * factor
                val x0 = dx * factor
                for (y in y0 until minOf(y0 + factor, height)) {
                    for (x in x0 until minOf(x0 + factor, width)) {
                        val i = (y * width + x) * 3
                        val r = rgb888[i].toInt() and 0xFF
                        val g = rgb888[i + 1].toInt() and 0xFF
                        val b = rgb888[i + 2].toInt() and 0xFF
                        sum += (r * 30 + g * 59 + b * 11) / 100
                        count++
                    }
                }
                out[dy * dsW + dx] = if (count > 0) sum / count else 0
            }
        }
        return out
    }

    internal fun estimateShiftDs(
        ref: IntArray,
        refW: Int,
        refH: Int,
        cand: IntArray,
        candW: Int,
        candH: Int,
    ): Pair<Int, Int> {
        var bestDx = 0
        var bestDy = 0
        var bestScore = Long.MAX_VALUE
        for (dy in -SEARCH_RADIUS_DS..SEARCH_RADIUS_DS) {
            for (dx in -SEARCH_RADIUS_DS..SEARCH_RADIUS_DS) {
                var sad = 0L
                var count = 0
                for (y in 0 until refH) {
                    val cyBase = y + dy
                    if (cyBase !in 0 until candH) continue
                    for (x in 0 until refW) {
                        val cx = x + dx
                        if (cx !in 0 until candW) continue
                        sad += abs(ref[y * refW + x] - cand[cyBase * candW + cx])
                        count++
                    }
                }
                if (count == 0) continue
                val score = sad / count
                if (score < bestScore) {
                    bestScore = score
                    bestDx = dx
                    bestDy = dy
                }
            }
        }
        return bestDx to bestDy
    }

    internal fun averageBlend(
        frames: List<ByteArray>,
        width: Int,
        height: Int,
        shifts: List<Pair<Int, Int>>,
    ): ByteArray {
        require(frames.size == shifts.size)
        val pixelCount = width * height * 3
        val acc = LongArray(pixelCount)
        val contrib = IntArray(width * height)
        frames.forEachIndexed { index, rgb ->
            val (dx, dy) = shifts[index]
            for (y in 0 until height) {
                val sy = y - dy
                if (sy !in 0 until height) continue
                for (x in 0 until width) {
                    val sx = x - dx
                    if (sx !in 0 until width) continue
                    val si = (sy * width + sx) * 3
                    val oi = (y * width + x) * 3
                    acc[oi] += rgb[si].toLong() and 0xFF
                    acc[oi + 1] += rgb[si + 1].toLong() and 0xFF
                    acc[oi + 2] += rgb[si + 2].toLong() and 0xFF
                    contrib[y * width + x]++
                }
            }
        }
        val out = ByteArray(pixelCount)
        for (i in 0 until width * height) {
            val c = contrib[i].coerceAtLeast(1)
            val oi = i * 3
            out[oi] = (acc[oi] / c).toInt().coerceIn(0, 255).toByte()
            out[oi + 1] = (acc[oi + 1] / c).toInt().coerceIn(0, 255).toByte()
            out[oi + 2] = (acc[oi + 2] / c).toInt().coerceIn(0, 255).toByte()
        }
        return out
    }

    private fun bitmapToRgb888(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val rgb = ByteArray(w * h * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            val o = i * 3
            rgb[o] = ((p shr 16) and 0xFF).toByte()
            rgb[o + 1] = ((p shr 8) and 0xFF).toByte()
            rgb[o + 2] = (p and 0xFF).toByte()
        }
        return rgb
    }

    fun logFrameProgress(index: Int, total: Int) {
        Log.i(TAG, "frame=$index/$total")
    }

    fun isoFromResult(result: CaptureResult): Int? =
        result.get(CaptureResult.SENSOR_SENSITIVITY)
}
