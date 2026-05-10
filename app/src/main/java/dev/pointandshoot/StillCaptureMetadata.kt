package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Writes camera-oriented EXIF/TIFF tags after MediaStore still capture so gallery apps and desktop
 * tools show Make / Model / exposure / focal length / ISO. Supplements platform [DngCreator] output
 * (which does not always surface tags in every viewer).
 */
object StillCaptureMetadata {
    private const val TAG = "PNS.StillExif"

    /** Standard EXIF orientation tag value after JPEG pixels are physically rotated upright (see preview). */
    private const val ORIENTATION_NORMAL = ExifInterface.ORIENTATION_NORMAL.toString()

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
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                val make = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: "Unknown"
                val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Device"
                exif.setAttribute(ExifInterface.TAG_MAKE, make)
                exif.setAttribute(ExifInterface.TAG_MODEL, model)
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
                lensDesc?.let { exif.setAttribute(ExifInterface.TAG_LENS_MODEL, it) }

                result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { iso ->
                    exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, iso.toString())
                }

                result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { ns ->
                    exposureTimeExifString(ns)?.let { exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, it) }
                }

                result.get(CaptureResult.LENS_FOCAL_LENGTH)?.let { mm ->
                    val scaled = (mm * 1000f).roundToInt().coerceAtLeast(1)
                    exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "$scaled/1000")
                }

                result.get(CaptureResult.LENS_APERTURE)?.let { av ->
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
                        append(result.get(CaptureResult.SENSOR_SENSITIVITY) ?: "?")
                        append(", ")
                        append(result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { exposureTimeExifString(it) } ?: "?")
                        append("s, f/")
                        append(
                            result.get(CaptureResult.LENS_APERTURE)?.let { "%.2f".format(Locale.US, it) }
                                ?: "?",
                        )
                        append(", ")
                        append(result.get(CaptureResult.LENS_FOCAL_LENGTH)?.let { "%.2f".format(Locale.US, it) }
                            ?: "?")
                        append("mm")
                    }
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, summary)

                if (setOrientation) {
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, ORIENTATION_NORMAL)
                }

                exif.saveAttributes()
            }
        }.onFailure { e ->
            Log.w(TAG, "apply metadata failed uri=$uri setOrientation=$setOrientation err=${e.message}")
        }
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
