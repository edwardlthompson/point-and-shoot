package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * In-place patches **EXIF IFD** capture tags inside a little-endian TIFF/DNG buffer.
 *
 * Adobe **DNG Specification** (§ “Additional TIFF Tags”, metadata overview): TIFF-EP may place some
 * tags in IFD0, while classic EXIF stores them in a **separate EXIF SubIFD**; **either is allowed,
 * but the EXIF SubIFD location is preferred** for interoperability with viewers (including
 * mobile galleries that surface ISO / shutter / aperture from standard EXIF Photo tags).
 *
 * [androidx.exifinterface.media.ExifInterface] should write these tags, but on some OEM DNGs the
 * rewrite step is unreliable; this pass **overwrites existing IFD entries** when their types and
 * sizes match, so bytes remain valid TIFF without reallocating IFDs.
 *
 * Tag IDs follow EXIF 2.3 / JEITA CP-3451 (numeric constants below).
 */
object TiffExifSubIfdCapturePatch {
    private const val TAG = "PNS.TiffExifPatch"

    private const val TIFF_MAGIC_II: Short = 0x4949.toShort()
    private const val TIFF_VERSION: Short = 42

    private const val TYPE_ASCII = 2
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4
    private const val TYPE_RATIONAL = 5

    /** IFD0 → pointer to Exif SubIFD (also known as Exif IFD offset). */
    private const val TAG_EXIF_IFD_POINTER = 0x8769

    private const val TAG_EXPOSURE_TIME = 0x829A // 33434
    private const val TAG_F_NUMBER = 0x829D // 33437
    /** Modern ISO tag (replaces legacy ISOSpeedRatings where applicable). */
    private const val TAG_PHOTOGRAPHIC_SENSITIVITY = 0x8827 // 34855
    private const val TAG_DATETIME_ORIGINAL = 0x9003 // 36867
    private const val TAG_FOCAL_LENGTH = 0x920A // 37386
    /** EXIF FocalLengthIn35mmFilm (0xA405). */
    private const val TAG_FOCAL_LENGTH_IN_35MM_FILM = 0xA405 // 41989

    fun patchFromCapture(
        tifBytes: ByteArray,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        dateTimeOriginalAscii19: String,
    ): ByteArray {
        if (tifBytes.size < 8) return tifBytes
        val hdr = ByteBuffer.wrap(tifBytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        if (hdr.short != TIFF_MAGIC_II) return tifBytes
        if (hdr.short != TIFF_VERSION) return tifBytes
        val ifd0 = hdr.int.toLong() and 0xffff_ffffL
        if (ifd0 < 0 || ifd0 > tifBytes.size - 4) return tifBytes

        val exifIfdOffset =
            readSingleLongTagTargetOffset(tifBytes, ifd0.toInt(), TAG_EXIF_IFD_POINTER)
                ?: run {
                    Log.d(TAG, "no Exif IFD pointer (34665) — gallery EXIF may be missing")
                    return tifBytes
                }

        val iso =
            result.get(CaptureResult.SENSOR_SENSITIVITY)
                ?: StillCaptureMetadata.fallbackIso(characteristics)
        val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val aperture =
            result.get(CaptureResult.LENS_APERTURE)
                ?: StillCaptureMetadata.fallbackAperture(characteristics)
        val focalMm =
            result.get(CaptureResult.LENS_FOCAL_LENGTH)
                ?: StillCaptureMetadata.fallbackFocalMm(characteristics)
        val focal35mm = focalLength35mmEquiv(focalMm, characteristics)

        var out = tifBytes.copyOf()
        var patched = 0

        iso?.let { i ->
            if (patchShortTagInIfd(out, exifIfdOffset.toInt(), TAG_PHOTOGRAPHIC_SENSITIVITY, i.coerceIn(1, 65535))) {
                patched++
            }
        }

        exposureNs?.let { ns ->
            val r = rationalExposureFromNs(ns)
            if (r != null &&
                patchRationalTagInIfd(
                    out,
                    exifIfdOffset.toInt(),
                    TAG_EXPOSURE_TIME,
                    r.first,
                    r.second,
                )
            ) {
                patched++
            }
        }

        aperture?.let { av ->
            val num = (av * 100f).roundToLong().coerceAtLeast(1)
            val den = 100L
            val (n, d) = reduceRational(num, den)
            if (patchRationalTagInIfd(out, exifIfdOffset.toInt(), TAG_F_NUMBER, n, d)) {
                patched++
            }
        }

        if (patchAsciiTagInIfdPreservingLength(out, exifIfdOffset.toInt(), TAG_DATETIME_ORIGINAL, dateTimeOriginalAscii19)) {
            patched++
        }

        focalMm?.let { mm ->
            val num = (mm * 1000f).roundToLong().coerceAtLeast(1)
            val (n, d) = reduceRational(num, 1000L)
            if (patchRationalTagInIfd(out, exifIfdOffset.toInt(), TAG_FOCAL_LENGTH, n, d)) {
                patched++
            }
        }

        focal35mm?.let { mm35 ->
            if (patchShortTagInIfd(out, exifIfdOffset.toInt(), TAG_FOCAL_LENGTH_IN_35MM_FILM, mm35.coerceIn(1, 65535))) {
                patched++
            }
        }

        if (patched > 0) {
            Log.i(TAG, "patched Exif IFD entries=$patched exifIfdOffset=$exifIfdOffset iso=$iso")
        }
        return out
    }

    private fun readSingleLongTagTargetOffset(buf: ByteArray, ifdOffset: Int, tagId: Int): Long? {
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        if (ifdOffset < 0 || ifdOffset > buf.size - 2) return null
        bb.position(ifdOffset)
        val entryCount = bb.short.toInt() and 0xffff
        repeat(entryCount) {
            val tag = bb.short.toInt() and 0xffff
            val type = bb.short.toInt() and 0xffff
            val count = bb.int.toLong() and 0xffff_ffffL
            val valueBytes = ByteArray(4)
            bb.get(valueBytes)
            if (tag != tagId) return@repeat
            if (type != TYPE_LONG || count != 1L) return null
            val vb = ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN)
            return vb.int.toLong() and 0xffff_ffffL
        }
        return null
    }

    private fun patchShortTagInIfd(out: ByteArray, ifdOffset: Int, tagId: Int, value: Int): Boolean {
        val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        if (ifdOffset < 0 || ifdOffset > out.size - 2) return false
        bb.position(ifdOffset)
        val entryCount = bb.short.toInt() and 0xffff
        repeat(entryCount) {
            val entryStart = bb.position()
            val tag = bb.short.toInt() and 0xffff
            val type = bb.short.toInt() and 0xffff
            val count = bb.int.toLong() and 0xffff_ffffL
            val valueFieldStart = bb.position()
            val valueBytes = ByteArray(4)
            bb.get(valueBytes)
            if (tag != tagId) return@repeat
            if (type != TYPE_SHORT || count != 1L) return false
            val v = value and 0xffff
            out[valueFieldStart] = (v and 0xff).toByte()
            out[valueFieldStart + 1] = ((v shr 8) and 0xff).toByte()
            return true
        }
        return false
    }

    private fun patchRationalTagInIfd(
        out: ByteArray,
        ifdOffset: Int,
        tagId: Int,
        numerator: Long,
        denominator: Long,
    ): Boolean {
        val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        if (ifdOffset < 0 || ifdOffset > out.size - 2) return false
        bb.position(ifdOffset)
        val entryCount = bb.short.toInt() and 0xffff
        repeat(entryCount) {
            val entryStart = bb.position()
            val tag = bb.short.toInt() and 0xffff
            val type = bb.short.toInt() and 0xffff
            val count = bb.int.toLong() and 0xffff_ffffL
            val valueBytes = ByteArray(4)
            bb.get(valueBytes)
            if (tag != tagId) return@repeat
            if (type != TYPE_RATIONAL || count != 1L) return false
            val vb = ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN)
            val rationalOffset = vb.int.toLong() and 0xffff_ffffL
            if (rationalOffset < 0 || rationalOffset + 8 > out.size) return false
            val dest = rationalOffset.toInt()
            val le = ByteBuffer.wrap(out, dest, 8).order(ByteOrder.LITTLE_ENDIAN)
            le.putInt((numerator and 0xffff_ffffL).toInt())
            le.putInt((denominator and 0xffff_ffffL).toInt())
            return true
        }
        return false
    }

    private fun patchAsciiTagInIfdPreservingLength(out: ByteArray, ifdOffset: Int, tagId: Int, ascii: String): Boolean {
        val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        if (ifdOffset < 0 || ifdOffset > out.size - 2) return false
        bb.position(ifdOffset)
        val entryCount = bb.short.toInt() and 0xffff
        repeat(entryCount) {
            val entryStart = bb.position()
            val tag = bb.short.toInt() and 0xffff
            val type = bb.short.toInt() and 0xffff
            val count = bb.int.toLong() and 0xffff_ffffL
            val valueBytes = ByteArray(4)
            bb.get(valueBytes)
            if (tag != tagId) return@repeat
            if (type != TYPE_ASCII) return false
            val byteCount = count.toInt()
            if (byteCount < 1) return false
            val vb = ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN)
            val valueOffset =
                if (byteCount <= 4) {
                    entryStart + 8
                } else {
                    vb.int
                }
            if (valueOffset < 0 || valueOffset + byteCount > out.size) return false
            val payload = ascii.encodeToByteArrayTiffAscii(byteCount)
            System.arraycopy(payload, 0, out, valueOffset, byteCount)
            return true
        }
        return false
    }

    private fun String.encodeToByteArrayTiffAscii(totalBytes: Int): ByteArray {
        val src = this.toByteArray(Charsets.US_ASCII)
        val out = ByteArray(totalBytes)
        val copyLen = minOf(src.size, totalBytes)
        System.arraycopy(src, 0, out, 0, copyLen)
        if (copyLen < totalBytes) {
            out[copyLen] = 0
            if (copyLen + 1 < totalBytes) {
                java.util.Arrays.fill(out, copyLen + 1, totalBytes, 0)
            }
        }
        return out
    }

    private fun rationalExposureFromNs(ns: Long): Pair<Long, Long>? {
        if (ns <= 0L) return null
        val sec = ns / 1_000_000_000.0
        return if (sec >= 1.0) {
            reduceRational((sec * 10_000).roundToLong().coerceAtLeast(1), 10_000L)
        } else {
            val denom = (1.0 / sec).roundToInt().coerceAtLeast(1).toLong()
            1L to denom
        }
    }

    private fun reduceRational(n: Long, d: Long): Pair<Long, Long> {
        var a = abs(n)
        var b = abs(d)
        if (b == 0L) return n to 1L
        while (b != 0L) {
            val t = a % b
            a = b
            b = t
        }
        val g = a.coerceAtLeast(1L)
        return (n / g) to (d / g)
    }

    private fun focalLength35mmEquiv(focalMm: Float?, characteristics: CameraCharacteristics): Int? {
        if (focalMm == null || focalMm <= 0f) return null
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        if (sensorSize.width <= 0f || sensorSize.height <= 0f) return null
        val diagSensor = hypot(sensorSize.width.toDouble(), sensorSize.height.toDouble())
        if (diagSensor <= 0.0) return null
        return (focalMm * (43.27 / diagSensor)).roundToInt().coerceIn(1, 65535)
    }
}
