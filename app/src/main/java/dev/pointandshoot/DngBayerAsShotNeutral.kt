package dev.pointandshoot

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Estimates DNG AsShotNeutral from Bayer channel means (center crop).
 *
 * Prefer [estimate] on the live [Image] **before** [android.hardware.camera2.DngCreator.writeImage]
 * (HAL may invalidate the plane buffer after write). [estimateFromDngBytes] samples the
 * per-row strip table [DngCreator] embeds (legacy target uses 3072 row strips).
 */
object DngBayerAsShotNeutral {
    private const val TAG = "PNS.BayerAsn"
    /** Must be 2 (or 1) so the center crop hits all four Bayer phases; step 4 only samples one color. */
    private const val SAMPLE_STEP = 2
    private const val MIN_SAMPLES_PER_CHANNEL = 256

    private const val TAG_IMAGE_WIDTH = 256
    private const val TAG_IMAGE_LENGTH = 257
    private const val TAG_STRIP_OFFSETS = 273
    private const val TAG_STRIP_BYTE_COUNTS = 279
    private const val TAG_BLACK_LEVEL = 50714

    private val CFA_CANDIDATES =
        intArrayOf(
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB,
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG,
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG,
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR,
        )

    fun estimate(
        characteristics: CameraCharacteristics,
        image: Image,
        captureResult: TotalCaptureResult? = null,
    ): FloatArray? = estimateWithBayerRatios(characteristics, image, captureResult)?.asn

    fun estimateWithBayerRatios(
        characteristics: CameraCharacteristics,
        image: Image,
        captureResult: TotalCaptureResult? = null,
    ): BayerAsnEstimate? {
        if (image.format != ImageFormat.RAW_SENSOR) return null
        val plane = image.planes.getOrNull(0) ?: return null
        if (plane.pixelStride != 2) {
            Log.w(TAG, "estimate skip pixelStride=${plane.pixelStride}")
            return null
        }
        val w = image.width
        val h = image.height
        if (w < 64 || h < 64) return null
        return estimateWithStatsFromPlane(
            characteristics,
            w,
            h,
            plane.rowStride,
            plane.pixelStride,
            plane.buffer.duplicate().apply { clear() },
            captureResult,
            source = "image",
        )
    }

    /**
     * Samples uncompressed row strips in a [DngCreator] DNG (after [writeImage]).
     */
    fun estimateFromDngBytes(
        dng: ByteArray,
        characteristics: CameraCharacteristics,
    ): FloatArray? = estimateWithBayerRatiosFromDngBytes(dng, characteristics)?.asn

    /** ASN plus center-crop Bayer R/G and B/G (for ReferenceCam reference WB alignment). */
    fun estimateWithBayerRatiosFromDngBytes(
        dng: ByteArray,
        characteristics: CameraCharacteristics,
    ): BayerAsnEstimate? {
        val strip = parseRowStripLayout(dng) ?: return null
        return estimateWithStatsFromPlane(
            characteristics,
            strip.width,
            strip.height,
            rowStride = strip.width * 2,
            pixelStride = 2,
            buf = null,
            captureResult = null,
            source = "dng",
            dng = dng,
            stripLayout = strip,
        )
    }

    data class BayerAsnEstimate(
        val asn: FloatArray,
        val bayerRg: Float,
        val bayerBg: Float,
    )

    private fun estimateFromPlane(
        characteristics: CameraCharacteristics,
        w: Int,
        h: Int,
        rowStride: Int,
        pixelStride: Int,
        buf: ByteBuffer?,
        captureResult: TotalCaptureResult?,
        source: String,
        dng: ByteArray? = null,
        stripLayout: RowStripLayout? = null,
    ): FloatArray? = estimateWithStatsFromPlane(
        characteristics,
        w,
        h,
        rowStride,
        pixelStride,
        buf,
        captureResult,
        source,
        dng,
        stripLayout,
    )?.asn

    private fun estimateWithStatsFromPlane(
        characteristics: CameraCharacteristics,
        w: Int,
        h: Int,
        rowStride: Int,
        pixelStride: Int,
        buf: ByteBuffer?,
        captureResult: TotalCaptureResult?,
        source: String,
        dng: ByteArray? = null,
        stripLayout: RowStripLayout? = null,
    ): BayerAsnEstimate? {
        val black = blackLevel(characteristics, captureResult, stripLayout)
        val order = cfaTryOrder(characteristics)
        var best: BayerAsnEstimate? = null
        var bestScore = 0.0
        for (cfa in order) {
            val sample =
                when {
                    stripLayout != null && dng != null ->
                        sampleChannelsFromStrips(dng, stripLayout, black, cfa)
                    buf != null ->
                        sampleChannelsFromBuffer(buf, w, h, rowStride, pixelStride, black, cfa)
                    else -> null
                } ?: continue
            val meanR = (sample.sumR / sample.countR).coerceAtLeast(1.0)
            val meanG = (sample.sumG / sample.countG).coerceAtLeast(1.0)
            val meanB = (sample.sumB / sample.countB).coerceAtLeast(1.0)
            val score = cfaLayoutScore(meanR, meanG, meanB)
            if (score > bestScore) {
                bestScore = score
                best =
                    BayerAsnEstimate(
                        asn = sample.toAsn(),
                        bayerRg = (meanR / meanG).toFloat(),
                        bayerBg = (meanB / meanG).toFloat(),
                    )
            }
        }
        if (best == null) {
            Log.w(TAG, "estimate failed all CFA layouts $source ${w}x$h black=$black")
            return null
        }
        Log.i(
            TAG,
            "estimate ok $source asnWB R=%.3f B=%.3f rg=%.4f bg=%.4f score=$bestScore".format(
                1f / best.asn[0].coerceAtLeast(1e-6f),
                1f / best.asn[2].coerceAtLeast(1e-6f),
                best.bayerRg,
                best.bayerBg,
            ),
        )
        return best
    }

    /**
     * Nudge [asn] (max-normalized) so Bayer R/G and B/G align with ReferenceCam reference capture.
     */
    fun adjustAsnToTargetBayerRatios(
        asn: FloatArray,
        currentRg: Float,
        currentBg: Float,
        targetRg: Float,
        targetBg: Float,
    ): FloatArray {
        require(asn.size == 3)
        val rg = currentRg.coerceAtLeast(1e-6f)
        val bg = currentBg.coerceAtLeast(1e-6f)
        val asnRg = asn[0] / asn[1].coerceAtLeast(1e-6f)
        val asnBg = asn[2] / asn[1].coerceAtLeast(1e-6f)
        val rgScale = (targetRg / rg).coerceIn(0.82f, 1.22f)
        val bgScale = (targetBg / bg).coerceIn(0.82f, 1.22f)
        val newRg = asnRg * rgScale
        val newBg = asnBg * bgScale
        val max = maxOf(newRg, 1f, newBg)
        return floatArrayOf(newRg / max, 1f / max, newBg / max)
    }

    /** Gray-card / leaf rear: R/G and B/G usually sit in this band when CFA is correct. */
    fun bayerRatiosPlausibleForProShotAlign(rg: Float, bg: Float): Boolean =
        rg in 0.78f..1.12f && bg in 0.78f..1.45f

    /** @see [asnFromChannelMeans] */
    internal fun asnFromChannelMeans(meanR: Float, meanG: Float, meanB: Float): FloatArray =
        ChannelSample(1, 1, 1, meanR.toDouble(), meanG.toDouble(), meanB.toDouble()).toAsn()

    private fun cfaTryOrder(characteristics: CameraCharacteristics): List<Int> {
        val hinted =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
        return buildList {
            if (hinted != null) add(hinted)
            CFA_CANDIDATES.forEach { cfa -> if (cfa != hinted) add(cfa) }
        }
    }

    /** Gray-card / neutral scene: channel ratios land in this band when CFA phase is correct. */
    private fun cfaLayoutScore(meanR: Double, meanG: Double, meanB: Double): Double {
        val rg = meanR / meanG
        val bg = meanB / meanG
        return if (bayerRatiosPlausibleForProShotAlign(rg.toFloat(), bg.toFloat())) {
            1_000.0 - kotlin.math.abs(rg - 1.0) - kotlin.math.abs(bg - 1.0)
        } else {
            meanG / maxOf(meanR, meanB)
        }
    }

    private data class RowStripLayout(
        val width: Int,
        val height: Int,
        val stripOffsetsDataOff: Int,
        val stripByteCountsDataOff: Int,
        val blackLevel: Float,
    )

    private data class ChannelSample(
        val countR: Int,
        val countG: Int,
        val countB: Int,
        val sumR: Double,
        val sumG: Double,
        val sumB: Double,
    ) {
        val meanR: Float get() = (sumR / countR).toFloat().coerceAtLeast(1f)
        val meanG: Float get() = (sumG / countG).toFloat().coerceAtLeast(1f)
        val meanB: Float get() = (sumB / countB).toFloat().coerceAtLeast(1f)

        /**
         * DNG AsShotNeutral: channel means relative to green, normalized so max == 1.
         * Matches [TiffDngColorMatrixPatch.asShotNeutralFromGains] inversion semantics.
         */
        fun toAsn(): FloatArray {
            val asnR = meanR / meanG
            val asnG = 1f
            val asnB = meanB / meanG
            val max = maxOf(asnR, asnG, asnB)
            return floatArrayOf(asnR / max, asnG / max, asnB / max)
        }
    }

    private fun parseRowStripLayout(dng: ByteArray): RowStripLayout? {
        if (dng.size < 8) return null
        val bb = ByteBuffer.wrap(dng).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.short.toInt() and 0xFFFF != 0x4949) return null
        if (bb.short.toInt() and 0xFFFF != 42) return null
        val ifd0 = bb.int
        if (ifd0 < 0 || ifd0 + 2 > dng.size) return null
        var width: Int? = null
        var height: Int? = null
        var stripOffsetsOff: Int? = null
        var stripCountsOff: Int? = null
        var blackLevel = 64f
        val n = readU16(dng, ifd0)
        var pos = ifd0 + 2
        repeat(n) {
            if (pos + 12 > dng.size) return null
            val tag = readU16(dng, pos)
            val type = readU16(dng, pos + 2)
            val cnt = readU32(dng, pos + 4)
            val valueOff = readU32(dng, pos + 8)
            when (tag) {
                TAG_IMAGE_WIDTH -> width = ifdScalar(dng, type, cnt, valueOff)
                TAG_IMAGE_LENGTH -> height = ifdScalar(dng, type, cnt, valueOff)
                TAG_STRIP_OFFSETS ->
                    stripOffsetsOff = ifdDataOffset(dng, type, cnt, valueOff)
                TAG_STRIP_BYTE_COUNTS ->
                    stripCountsOff = ifdDataOffset(dng, type, cnt, valueOff)
                TAG_BLACK_LEVEL ->
                    blackLevel = readBlackLevelRational(dng, type, cnt, valueOff) ?: blackLevel
            }
            pos += 12
        }
        val w = width ?: return null
        val h = height ?: return null
        val so = stripOffsetsOff ?: return null
        val sc = stripCountsOff ?: return null
        if (h <= 0 || w < 64) return null
        return RowStripLayout(w, h, so, sc, blackLevel)
    }

    private fun sampleChannelsFromStrips(
        dng: ByteArray,
        layout: RowStripLayout,
        black: Float,
        cfa: Int,
    ): ChannelSample? {
        val w = layout.width
        val h = layout.height
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var countR = 0
        var countG = 0
        var countB = 0
        sampleBayerGrid(w, h, { x, y ->
            if (y >= h) {
                -1f
            } else {
                val rowOff = readU32(dng, layout.stripOffsetsDataOff + y * 4)
                val rowBytes = readU32(dng, layout.stripByteCountsDataOff + y * 4)
                val byteOff = rowOff + x * 2
                if (byteOff + 2 > dng.size || x * 2 + 2 > rowBytes) {
                    -1f
                } else {
                    readU16(dng, byteOff) - black
                }
            }
        }) { x, y, v ->
            when (cfaAt(cfa, x, y)) {
                CfaColor.R -> {
                    sumR += v
                    countR++
                }
                CfaColor.G -> {
                    sumG += v
                    countG++
                }
                CfaColor.B -> {
                    sumB += v
                    countB++
                }
            }
        }
        return finalizeSample(countR, countG, countB, sumR, sumG, sumB)
    }

    private fun sampleChannelsFromBuffer(
        buf: ByteBuffer,
        w: Int,
        h: Int,
        rowStride: Int,
        pixelStride: Int,
        black: Float,
        cfa: Int,
    ): ChannelSample? {
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var countR = 0
        var countG = 0
        var countB = 0
        val limit = buf.limit()
        sampleBayerGrid(w, h, { x, y ->
            readRaw16(buf, x, y, rowStride, pixelStride, limit) - black
        }) { x, y, v ->
            when (cfaAt(cfa, x, y)) {
                CfaColor.R -> {
                    sumR += v
                    countR++
                }
                CfaColor.G -> {
                    sumG += v
                    countG++
                }
                CfaColor.B -> {
                    sumB += v
                    countB++
                }
            }
        }
        return finalizeSample(countR, countG, countB, sumR, sumG, sumB)
    }

    private inline fun sampleBayerGrid(
        w: Int,
        h: Int,
        readValue: (x: Int, y: Int) -> Float,
        onSample: (x: Int, y: Int, v: Float) -> Unit,
    ) {
        val y0 = h / 5
        val y1 = (4 * h) / 5
        val x0 = w / 5
        val x1 = (4 * w) / 5
        for (yo in 0 until 2) {
            for (xo in 0 until 2) {
                var y = y0 + yo
                while (y < y1) {
                    var x = x0 + xo
                    while (x < x1) {
                        val v = readValue(x, y)
                        if (v > 32f) {
                            onSample(x, y, v)
                        }
                        x += SAMPLE_STEP
                    }
                    y += SAMPLE_STEP
                }
            }
        }
    }

    private fun finalizeSample(
        countR: Int,
        countG: Int,
        countB: Int,
        sumR: Double,
        sumG: Double,
        sumB: Double,
    ): ChannelSample? {
        if (countR < MIN_SAMPLES_PER_CHANNEL ||
            countG < MIN_SAMPLES_PER_CHANNEL ||
            countB < MIN_SAMPLES_PER_CHANNEL
        ) {
            return null
        }
        return ChannelSample(countR, countG, countB, sumR, sumG, sumB)
    }

    private fun blackLevel(
        @Suppress("UNUSED_PARAMETER") characteristics: CameraCharacteristics,
        @Suppress("UNUSED_PARAMETER") captureResult: TotalCaptureResult?,
        stripLayout: RowStripLayout?,
    ): Float = stripLayout?.blackLevel?.takeIf { it > 0f } ?: 64f

    private fun readRaw16(
        buf: ByteBuffer,
        x: Int,
        y: Int,
        rowStride: Int,
        pixelStride: Int,
        limit: Int,
    ): Float {
        val offset = y * rowStride + x * pixelStride
        if (offset + 1 >= limit) return 0f
        val lo = buf.get(offset).toInt() and 0xFF
        val hi = buf.get(offset + 1).toInt() and 0xFF
        return (lo or (hi shl 8)).toFloat()
    }

    private fun readU16(data: ByteArray, offset: Int): Int {
        if (offset + 2 > data.size) return 0
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    }

    private fun readU32(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return 0
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun ifdScalar(data: ByteArray, type: Int, count: Int, valueOff: Int): Int? {
        if (count != 1) return null
        return when (type) {
            3 -> valueOff and 0xFFFF
            4 -> valueOff
            else -> null
        }
    }

    private fun ifdDataOffset(data: ByteArray, type: Int, count: Int, valueOff: Int): Int? {
        if (count <= 0) return null
        return when {
            count == 1 && type == 4 -> valueOff
            else -> valueOff
        }
    }

    private fun readBlackLevelRational(
        data: ByteArray,
        type: Int,
        count: Int,
        valueOff: Int,
    ): Float? {
        if (count < 1) return null
        val dataOff =
            when {
                count == 1 && type == 5 -> valueOff
                count == 1 && type == 10 -> valueOff
                else -> valueOff
            }
        if (dataOff + 8 > data.size) return null
        val num = readS32(data, dataOff)
        val den = readS32(data, dataOff + 4).coerceAtLeast(1)
        return num.toFloat() / den
    }

    private fun readS32(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return 0
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private enum class CfaColor { R, G, B }

    private fun cfaAt(arrangement: Int, x: Int, y: Int): CfaColor {
        val evenX = x and 1 == 0
        val evenY = y and 1 == 0
        return when (arrangement) {
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB ->
                when {
                    evenY && evenX -> CfaColor.R
                    !evenY && !evenX -> CfaColor.B
                    else -> CfaColor.G
                }
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG ->
                when {
                    evenY && !evenX -> CfaColor.R
                    !evenY && evenX -> CfaColor.B
                    else -> CfaColor.G
                }
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG ->
                when {
                    !evenY && evenX -> CfaColor.R
                    evenY && !evenX -> CfaColor.B
                    else -> CfaColor.G
                }
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR ->
                when {
                    !evenY && !evenX -> CfaColor.R
                    evenY && evenX -> CfaColor.B
                    else -> CfaColor.G
                }
            else ->
                when {
                    evenY && evenX -> CfaColor.R
                    !evenY && !evenX -> CfaColor.B
                    else -> CfaColor.G
                }
        }
    }
}
