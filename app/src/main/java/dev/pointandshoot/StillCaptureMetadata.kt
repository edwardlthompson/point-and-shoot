package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.net.Uri
import android.os.Build
import android.system.Os
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Writes camera-oriented EXIF/TIFF tags after MediaStore still capture so gallery apps and desktop
 * tools show Make / Model / exposure / focal length / ISO. Supplements platform [DngCreator] output
 * (which does not always surface tags in every viewer).
 *
 * **DNG:** Uses [android.os.ParcelFileDescriptor] in `"rw"` mode with an explicit [Os.fsync] after
 * [ExifInterface.saveAttributes] so scoped-storage viewers see committed tags.
 */
object StillCaptureMetadata {
    private const val TAG = "PNS.StillExif"

    /** Standard EXIF orientation tag value after JPEG pixels are physically rotated upright (see preview). */
    private const val ORIENTATION_NORMAL = ExifInterface.ORIENTATION_NORMAL.toString()

    private val exifDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun applyToDngUri(
        context: Context,
        uri: Uri,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
    ) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val bytes = ins.readBytes()
                val patched =
                    TiffIfd0Software305.patchSoftwarePreservingLength(bytes, "Point & Shoot")
                context.contentResolver.openOutputStream(uri, "wt")?.use { outs ->
                    outs.write(patched)
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "DNG IFD0 Software patch failed uri=$uri err=${e.message}")
        }
        applyCommon(context, uri, characteristics, result, setOrientation = false, stampSoftwareTag = false)
    }

    fun applyToJpegUri(
        context: Context,
        uri: Uri,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
    ) {
        applyCommon(context, uri, characteristics, result, setOrientation = true, stampSoftwareTag = true)
    }

    private fun applyCommon(
        context: Context,
        uri: Uri,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        setOrientation: Boolean,
        stampSoftwareTag: Boolean,
    ) {
        runCatching {
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: fallbackIso(characteristics)
            val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val focalMm = result.get(CaptureResult.LENS_FOCAL_LENGTH) ?: fallbackFocalMm(characteristics)
            val aperture = result.get(CaptureResult.LENS_APERTURE) ?: fallbackAperture(characteristics)

            val make = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: "Unknown"
            val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Device"
            val dateStr = LocalDateTime.now().format(exifDateTimeFormatter)

            val pfd =
                context.contentResolver.openFileDescriptor(uri, "rw")
                    ?: run {
                        Log.w(TAG, "openFileDescriptor(rw) null uri=$uri")
                        return@runCatching
                    }
            pfd.use {
                val exif = ExifInterface(it.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_MAKE, make)
                exif.setAttribute(ExifInterface.TAG_MODEL, model)
                exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
                if (stampSoftwareTag) {
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Point & Shoot")
                }

                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val lensDesc =
                    when (lensFacing) {
                        CameraCharacteristics.LENS_FACING_FRONT -> "Front Camera"
                        CameraCharacteristics.LENS_FACING_BACK -> "Back Camera"
                        else -> null
                    }
                lensDesc?.let { v -> exif.setAttribute(ExifInterface.TAG_LENS_MODEL, v) }

                iso?.let { v -> exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, v.toString()) }

                exposureNs?.let { ns ->
                    exposureTimeExifString(ns)?.let { s -> exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, s) }
                }

                focalMm?.let { mm ->
                    val scaled = (mm * 1000f).roundToInt().coerceAtLeast(1)
                    exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "$scaled/1000")
                }

                aperture?.let { av ->
                    val scaled = (av * 100f).roundToInt().coerceAtLeast(1)
                    exif.setAttribute(ExifInterface.TAG_F_NUMBER, "$scaled/100")
                }

                val flashBits =
                    when (result.get(CaptureResult.FLASH_STATE)) {
                        CaptureResult.FLASH_STATE_FIRED,
                        CaptureResult.FLASH_STATE_PARTIAL,
                        -> 1
                        else -> 0
                    }
                exif.setAttribute(ExifInterface.TAG_FLASH, flashBits.toString())

                val summary =
                    buildString {
                        append("ISO ")
                        append(iso ?: "?")
                        append(", ")
                        append(exposureNs?.let { exposureTimeExifString(it) } ?: "?")
                        append("s, f/")
                        append(aperture?.let { v -> "%.2f".format(Locale.US, v) } ?: "?")
                        append(", ")
                        append(focalMm?.let { v -> "%.2f".format(Locale.US, v) } ?: "?")
                        append("mm")
                    }
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, summary)

                if (setOrientation) {
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, ORIENTATION_NORMAL)
                }

                exif.saveAttributes()
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Os.fsync(it.fileDescriptor)
                    }
                }.onFailure { e -> Log.d(TAG, "fsync after exif uri=$uri err=${e.message}") }
            }
            Log.i(
                TAG,
                "apply metadata ok uri=$uri make=$make model=$model iso=${iso ?: "?"} ssNs=${exposureNs ?: "?"}",
            )
        }.onFailure { e ->
            Log.w(TAG, "apply metadata failed uri=$uri setOrientation=$setOrientation err=${e.message}")
        }
    }

    private fun fallbackFocalMm(chars: CameraCharacteristics): Float? {
        val logical = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: return null
        if (logical.isEmpty()) return null
        return logical.minOrNull() ?: logical[0]
    }

    private fun fallbackAperture(chars: CameraCharacteristics): Float? {
        val a = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: return null
        if (a.isEmpty()) return null
        return a.minOrNull() ?: a[0]
    }

    /**
     * Rough fallback when [CaptureResult.SENSOR_SENSITIVITY] is missing on some RAW pipelines.
     */
    private fun fallbackIso(chars: CameraCharacteristics): Int? {
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return null
        val maxAnalog = chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY)
        val guess =
            when {
                maxAnalog != null && maxAnalog in isoRange.lower..isoRange.upper -> maxAnalog
                else -> isoRange.upper / 2
            }
        return guess.coerceIn(isoRange.lower, isoRange.upper)
    }

    /**
     * Exposure time EXIF string (seconds as decimal or `1/N`).
     */
    internal fun exposureTimeExifString(ns: Long): String? {
        if (ns <= 0L) return null
        val sec = ns / 1_000_000_000.0
        if (sec >= 1.0) {
            return "%.4f".format(Locale.US, sec).trimEnd('0').trimEnd('.').ifEmpty { "0" }
        }
        val denom = (1.0 / sec).roundToInt().coerceAtLeast(1)
        return "1/$denom"
    }
}
