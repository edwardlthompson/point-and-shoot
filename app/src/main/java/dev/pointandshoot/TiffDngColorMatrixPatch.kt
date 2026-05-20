package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.util.Rational
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Post-processes a little-endian DNG byte array written by [android.hardware.camera2.DngCreator]
 * to **replace** the ColorMatrix1/2 (tags 50721/50722) and ForwardMatrix1/2 (50964/50965) SRATIONAL
 * arrays with values from a [CameraCharacteristics] override.
 *
 * **Why this exists:** On CPH2655-class Qualcomm stacks the HAL reports a copy-pasted
 * ForwardMatrix (fm1==fm2, identical across UW/wide/tele) and miscalibrated ColorMatrix2
 * for aux cameras. [android.hardware.camera2.DngCreator] has no public setter for these tags.
 * This patcher walks the primary IFD → raw sub-IFD (tag 50740) and overwrites the 9-element
 * SRATIONAL payloads in-place. Because the payload size is identical (9 × 8 bytes = 72 bytes),
 * no structural rewrite is required — we just overwrite the existing SRATIONAL data at its
 * current file offset.
 *
 * If any tag or sub-IFD is missing the file is returned unchanged (safe no-op).
 */
object TiffDngColorMatrixPatch {

    // DNG tag constants
    private const val TAG_DNG_PRIVATE     = 50740  // SubIFD pointer to raw IFD
    internal const val TAG_COLOR_MATRIX1 = 50721
    internal const val TAG_COLOR_MATRIX2 = 50722
    internal const val TAG_FORWARD_MATRIX1 = 50964
    internal const val TAG_FORWARD_MATRIX2 = 50965
    private const val TAG_AS_SHOT_NEUTRAL = 50728 // RATIONAL[3], unsigned

    /** Reads SRATIONAL matrix tag element [0,0] from IFD0 (for USB hal-cal diag). */
    fun readMatrixElement00(original: ByteArray, tag: Int): Float? =
        runCatching { readMatrixElement00Internal(original, tag) }.getOrNull()

    private fun readMatrixElement00Internal(original: ByteArray, tag: Int): Float {
        require(original.size >= 8)
        val bb = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(0)
        require(bb.short == 0x4949.toShort())
        require(bb.short == 42.toShort())
        val ifd0Offset = bb.int.toLong() and 0xFFFF_FFFFL
        if (ifd0Offset < 0 || ifd0Offset + 2 > original.size) error("bad ifd0")
        bb.position(ifd0Offset.toInt())
        val count = bb.short.toInt() and 0xFFFF
        val entryStart = ifd0Offset.toInt() + 2
        for (i in 0 until count) {
            val base = entryStart + i * 12
            if (base + 12 > original.size) break
            bb.position(base)
            val t = bb.short.toInt() and 0xFFFF
            val type = bb.short.toInt() and 0xFFFF
            val cnt = bb.int
            if (t != tag || type != TIFF_TYPE_SRATIONAL || cnt != 9) continue
            val dataOffset =
                ByteBuffer.wrap(original, base + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val needed = 9 * SRATIONAL_SIZE
            if (dataOffset < 0 || dataOffset + needed > original.size) break
            val n0 = ByteBuffer.wrap(original, dataOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val d0 =
                ByteBuffer.wrap(original, dataOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            return n0.toFloat() / d0.coerceAtLeast(1).toFloat()
        }
        error("tag $tag not found")
    }

    private const val TIFF_TYPE_SRATIONAL = 10  // signed rational: 8 bytes per entry
    private const val TIFF_TYPE_RATIONAL  = 5   // unsigned rational: 8 bytes per entry
    private const val RATIONAL_SIZE       = 8   // 4-byte numerator + 4-byte denominator
    private const val SRATIONAL_SIZE      = 8

    /**
     * Patches AsShotNeutral (tag 50728) in IFD0 using WB gains derived from
     * [android.hardware.camera2.CaptureResult.COLOR_CORRECTION_GAINS].
     *
     * On CPH2655 the HAL copies the wide camera's [SENSOR_NEUTRAL_COLOR_POINT] into aux camera
     * TotalCaptureResults, causing [DngCreator] to embed a wrong AsShotNeutral — the primary
     * driver of the green/dark cast seen in every RAW converter. COLOR_CORRECTION_GAINS comes
     * from the per-sensor ISP AWB loop and is correct for each physical camera.
     *
     * [gains] order: [R, G_even, G_odd, B]. We average the two G channels and write
     * AsShotNeutral = [1/R, 1/G_avg, 1/B] normalized so the maximum value == 1.0.
     * Returns [original] unchanged on any error.
     */
    fun patchAsShotNeutral(
        original: ByteArray,
        gains: android.hardware.camera2.params.RggbChannelVector,
    ): ByteArray = runCatching { patchAsnInternal(original, gains) }.getOrElse { original }

    /** Normalized AsShotNeutral `[R, G, B]` (max == 1) from capture gains. */
    fun asShotNeutralFromGains(gains: android.hardware.camera2.params.RggbChannelVector): FloatArray {
        val gAvg = (gains.greenEven + gains.greenOdd) / 2f
        val invR = 1f / gains.red.coerceAtLeast(1e-6f)
        val invG = 1f / gAvg.coerceAtLeast(1e-6f)
        val invB = 1f / gains.blue.coerceAtLeast(1e-6f)
        val maxInv = maxOf(invR, invG, invB)
        return floatArrayOf(invR / maxInv, invG / maxInv, invB / maxInv)
    }

    /**
     * Overload that accepts a pre-normalized [FloatArray] `[R, G, B]` where max == 1.0.
     * Used by [DngBayerAsShotNeutral] which computes ASN from raw pixel channel means.
     */
    fun patchAsShotNeutralFromFloats(
        original: ByteArray,
        asn: FloatArray,
    ): ByteArray = runCatching {
        require(asn.size == 3) { "asn must be length 3" }
        patchAsnBytes(original, asn[0], asn[1], asn[2])
    }.getOrElse { original }

    private fun patchAsnInternal(
        original: ByteArray,
        gains: android.hardware.camera2.params.RggbChannelVector,
    ): ByteArray {
        val gAvg = (gains.greenEven + gains.greenOdd) / 2f
        val invR = 1f / gains.red
        val invG = 1f / gAvg
        val invB = 1f / gains.blue
        val maxInv = maxOf(invR, invG, invB)
        return patchAsnBytes(original, invR / maxInv, invG / maxInv, invB / maxInv)
    }

    private fun patchAsnBytes(original: ByteArray, asnR: Float, asnG: Float, asnB: Float): ByteArray {
        require(original.size >= 8) { "buffer too small" }
        // Encode as RATIONAL with denominator 1_000_000 for ~6 decimal places of precision
        val denom = 1_000_000
        val numR = (asnR * denom + 0.5f).toLong().coerceIn(0, denom.toLong())
        val numG = (asnG * denom + 0.5f).toLong().coerceIn(0, denom.toLong())
        val numB = (asnB * denom + 0.5f).toLong().coerceIn(0, denom.toLong())

        val buf = original.copyOf()
        val bb  = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(0)
        require(bb.short == 0x4949.toShort()) { "not little-endian TIFF" }
        require(bb.short == 42.toShort()) { "not TIFF 42" }
        val ifd0Offset = bb.int.toLong() and 0xFFFF_FFFFL
        overwriteRationalTag3(
            bb, buf, ifd0Offset,
            TAG_AS_SHOT_NEUTRAL,
            longArrayOf(numR, denom.toLong(), numG, denom.toLong(), numB, denom.toLong()),
        )
        return buf
    }

    /**
     * Overwrites a RATIONAL[3] tag (type 5) in-place. [values] is 6 longs: num0,den0, num1,den1, num2,den2.
     */
    private fun overwriteRationalTag3(
        bb: ByteBuffer,
        buf: ByteArray,
        ifdOffset: Long,
        targetTag: Int,
        values: LongArray,
    ) {
        if (ifdOffset < 0 || ifdOffset + 2 > buf.size) return
        bb.position(ifdOffset.toInt())
        val count = bb.short.toInt() and 0xFFFF
        val entryStart = ifdOffset.toInt() + 2
        for (i in 0 until count) {
            val base = entryStart + i * 12
            if (base + 12 > buf.size) break
            bb.position(base)
            val tag  = bb.short.toInt() and 0xFFFF
            val type = bb.short.toInt() and 0xFFFF
            val cnt  = bb.int
            val raw  = ByteArray(4).also { bb.get(it) }
            if (tag != targetTag || type != TIFF_TYPE_RATIONAL || cnt != 3) continue
            val dataOffset = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).int
            val needed = 3 * RATIONAL_SIZE
            if (dataOffset < 0 || dataOffset + needed > buf.size) break
            val patch = ByteBuffer.wrap(buf, dataOffset, needed).order(ByteOrder.LITTLE_ENDIAN)
            for (j in 0 until 3) {
                patch.putInt(values[j * 2].toInt())
                patch.putInt(values[j * 2 + 1].toInt())
            }
            return
        }
    }

    /**
     * Patches ForwardMatrix1/2 only (tags 50964/50965) from a [DngForwardMatrixFix.FmOverride].
     * Use this for devices where the HAL copy-pastes the wide camera's FM to all cameras.
     * Returns [original] unchanged on any error (safe no-op).
     */
    fun patchForwardMatrix(
        original: ByteArray,
        override: DngForwardMatrixFix.FmOverride,
    ): ByteArray = runCatching { patchFmInternal(original, override) }.getOrElse { original }

    private fun patchFmInternal(original: ByteArray, override: DngForwardMatrixFix.FmOverride): ByteArray {
        require(original.size >= 8) { "buffer too small" }
        val buf = original.copyOf()
        val bb  = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(0)
        require(bb.short == 0x4949.toShort()) { "not little-endian TIFF" }
        require(bb.short == 42.toShort()) { "not TIFF 42" }
        val ifd0Offset = bb.int.toLong() and 0xFFFF_FFFFL
        // Android DngCreator puts ForwardMatrix tags directly in IFD0 (no sub-IFD needed).
        overwriteSRationalTagsFromRationals(
            bb, buf, ifd0Offset,
            mapOf(
                TAG_FORWARD_MATRIX1 to override.fm1,
                TAG_FORWARD_MATRIX2 to override.fm2,
            ),
        )
        return buf
    }

    /**
     * Reconcile CM1/CM2/FM1/FM2 in **IFD0** from [CameraCharacteristics] (Android [DngCreator]
     * places ForwardMatrix in IFD0; sub-IFD path in [patch] may be a no-op).
     */
    fun patchCalibrationTagsIfd0(
        original: ByteArray,
        characteristics: CameraCharacteristics,
    ): ByteArray = runCatching { patchCalibrationIfd0Internal(original, characteristics) }
        .getOrElse { original }

    private fun patchCalibrationIfd0Internal(
        original: ByteArray,
        characteristics: CameraCharacteristics,
    ): ByteArray {
        require(original.size >= 8) { "buffer too small" }
        val buf = original.copyOf()
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(0)
        require(bb.short == 0x4949.toShort()) { "not little-endian TIFF" }
        require(bb.short == 42.toShort()) { "not TIFF 42" }
        val ifd0Offset = bb.int.toLong() and 0xFFFF_FFFFL
        val tagsToReplace =
            mapOf(
                TAG_COLOR_MATRIX1 to characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1),
                TAG_COLOR_MATRIX2 to characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2),
                TAG_FORWARD_MATRIX1 to characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1),
                TAG_FORWARD_MATRIX2 to characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2),
            )
        overwriteSRationalTagsInIfd(bb, buf, ifd0Offset, tagsToReplace)
        return buf
    }

    /**
     * Full-matrix patch from [CameraCharacteristics] — replaces CM1/CM2/FM1/FM2 in raw sub-IFD.
     * Returns [original] unchanged on any error.
     */
    fun patch(original: ByteArray, overrides: CameraCharacteristics): ByteArray {
        return runCatching { patchInternal(original, overrides) }
            .getOrElse { original }
    }

    private fun patchInternal(original: ByteArray, overrides: CameraCharacteristics): ByteArray {
        require(original.size >= 8) { "buffer too small" }
        val buf = original.copyOf()
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(0)
        val magic = bb.short
        require(magic == 0x4949.toShort()) { "not little-endian TIFF" }
        require(bb.short == 42.toShort()) { "not TIFF 42" }
        val ifd0Offset = bb.int.toLong() and 0xFFFF_FFFFL
        val rawIfdOffset = findTagInIfd(bb, buf, ifd0Offset, TAG_DNG_PRIVATE)
            ?: return original

        val tagsToReplace = mapOf(
            TAG_COLOR_MATRIX1   to overrides.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1),
            TAG_COLOR_MATRIX2   to overrides.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2),
            TAG_FORWARD_MATRIX1 to overrides.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1),
            TAG_FORWARD_MATRIX2 to overrides.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2),
        )
        overwriteSRationalTagsInIfd(bb, buf, rawIfdOffset.toLong() and 0xFFFF_FFFFL, tagsToReplace)
        return buf
    }

    /**
     * Finds a tag in an IFD and, for LONG/SHORT pointer types, returns the pointed-to value.
     * For SRATIONAL arrays returns the data offset.
     * Returns null if the tag is not found.
     */
    private fun findTagInIfd(bb: ByteBuffer, buf: ByteArray, ifdOffset: Long, targetTag: Int): Int? {
        if (ifdOffset < 0 || ifdOffset + 2 > buf.size) return null
        bb.position(ifdOffset.toInt())
        val count = bb.short.toInt() and 0xFFFF
        repeat(count) {
            if (bb.position() + 12 > buf.size) return null
            val tag   = bb.short.toInt() and 0xFFFF
            val type  = bb.short.toInt() and 0xFFFF
            val cnt   = bb.int
            val raw   = ByteArray(4).also { bb.get(it) }
            if (tag == targetTag) {
                // Tag 50740 is a LONG pointer to the raw sub-IFD offset
                val valBuf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                return when (type) {
                    4 -> valBuf.int   // LONG
                    3 -> valBuf.short.toInt() and 0xFFFF  // SHORT
                    else -> valBuf.int  // treat as offset
                }
            }
        }
        return null
    }

    /**
     * Variant of [overwriteSRationalTagsInIfd] using [Rational] arrays (9 values per matrix).
     */
    private fun overwriteSRationalTagsFromRationals(
        bb: ByteBuffer,
        buf: ByteArray,
        ifdOffset: Long,
        replacements: Map<Int, Array<Rational>>,
    ) {
        if (ifdOffset < 0 || ifdOffset + 2 > buf.size) return
        bb.position(ifdOffset.toInt())
        val count = bb.short.toInt() and 0xFFFF
        val entryStart = ifdOffset.toInt() + 2
        for (i in 0 until count) {
            val base = entryStart + i * 12
            if (base + 12 > buf.size) break
            bb.position(base)
            val tag  = bb.short.toInt() and 0xFFFF
            val type = bb.short.toInt() and 0xFFFF
            val cnt  = bb.int
            val raw  = ByteArray(4).also { bb.get(it) }
            if (type != TIFF_TYPE_SRATIONAL) continue
            val rationals = replacements[tag] ?: continue
            if (cnt != rationals.size) continue
            val dataOffset = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).int
            val needed = cnt * SRATIONAL_SIZE
            if (dataOffset < 0 || dataOffset + needed > buf.size) continue
            val patch = ByteBuffer.wrap(buf, dataOffset, needed).order(ByteOrder.LITTLE_ENDIAN)
            for (r in rationals) {
                patch.putInt(r.numerator)
                patch.putInt(r.denominator)
            }
        }
    }

    /**
     * For each tag in [replacements], finds the SRATIONAL entry in the IFD at [ifdOffset]
     * and overwrites its payload bytes in [buf] in-place.
     */
    private fun overwriteSRationalTagsInIfd(
        bb: ByteBuffer,
        buf: ByteArray,
        ifdOffset: Long,
        replacements: Map<Int, android.hardware.camera2.params.ColorSpaceTransform?>,
    ) {
        if (ifdOffset < 0 || ifdOffset + 2 > buf.size) return
        bb.position(ifdOffset.toInt())
        val count = bb.short.toInt() and 0xFFFF
        val entryStart = ifdOffset.toInt() + 2

        for (i in 0 until count) {
            val base = entryStart + i * 12
            if (base + 12 > buf.size) break
            bb.position(base)
            val tag   = bb.short.toInt() and 0xFFFF
            val type  = bb.short.toInt() and 0xFFFF
            val cnt   = bb.int   // should be 9 for 3×3 matrix
            val raw   = ByteArray(4).also { bb.get(it) }
            if (type != TIFF_TYPE_SRATIONAL) continue

            val transform = replacements[tag] ?: continue
            if (cnt != 9) continue

            // The 4-byte field is an offset into the file where the SRATIONAL data lives
            val dataOffset = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).int
            val needed = cnt * SRATIONAL_SIZE
            if (dataOffset < 0 || dataOffset + needed > buf.size) continue

            // Overwrite each of the 9 rational values
            val patch = ByteBuffer.wrap(buf, dataOffset, needed).order(ByteOrder.LITTLE_ENDIAN)
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    val r: Rational = transform.getElement(row, col)
                    patch.putInt(r.numerator)
                    patch.putInt(r.denominator)
                }
            }
        }
    }
}
