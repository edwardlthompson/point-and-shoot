package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.location.Location
import android.net.Uri
import android.os.Build
import android.system.Os
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Writes camera-oriented EXIF/TIFF tags after MediaStore still capture so gallery apps and desktop
 * tools show Make / Model / exposure / focal length / ISO / GPS.
 *
 * **DNG (Adobe Digital Negative):** The DNG spec allows TIFF-EP–style tags in IFD0 **or** classic
 * EXIF tags in the **EXIF SubIFD**; **the EXIF SubIFD location is preferred** for interoperability.
 * Gallery apps (Google Photos, OEM galleries) typically surface ISO / shutter / aperture from that
 * EXIF Photo block. We therefore patch IFD0 ASCII (Make/Model/DateTime/Software) **and** overwrite
 * existing EXIF IFD entries in-place ([TiffExifSubIfdCapturePatch]), then run [ExifInterface] for any
 * remaining tags.
 */
object StillCaptureMetadata {
    private const val TAG = "PNS.StillExif"

    private const val ORIENTATION_NORMAL = ExifInterface.ORIENTATION_NORMAL.toString()

    private val exifDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun applyToDngUri(
        context: Context,
        uri: Uri,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        location: Location? = null,
    ) {
        runCatching {
            val rawBytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: run {
                        Log.w(TAG, "DNG read failed uri=$uri")
                        return@runCatching
                    }

            val make = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: "Unknown"
            val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Device"
            val dateStr = LocalDateTime.now().format(exifDateTimeFormatter)

            var patchedBytes = TiffIfd0Software305.patchSoftwarePreservingLength(rawBytes, "Point & Shoot")
            patchedBytes =
                TiffIfd0Software305.patchPrimaryIfdAsciiTagPreservingLength(
                    patchedBytes,
                    TiffIfd0Software305.TAG_MAKE,
                    make,
                )
            patchedBytes =
                TiffIfd0Software305.patchPrimaryIfdAsciiTagPreservingLength(
                    patchedBytes,
                    TiffIfd0Software305.TAG_MODEL,
                    model,
                )
            patchedBytes =
                TiffIfd0Software305.patchPrimaryIfdAsciiTagPreservingLength(
                    patchedBytes,
                    TiffIfd0Software305.TAG_DATETIME,
                    dateStr,
                )

            patchedBytes =
                TiffExifSubIfdCapturePatch.patchFromCapture(
                    patchedBytes,
                    characteristics,
                    result,
                    dateStr,
                )

            val tmp = File.createTempFile("pns_dng_exif", ".dng", context.cacheDir)
            try {
                tmp.writeBytes(patchedBytes)
                runCatching {
                    val exif = ExifInterface(tmp.absolutePath)
                    fillExifFields(
                        exif,
                        characteristics,
                        result,
                        location,
                        setOrientation = false,
                        stampSoftwareTag = false,
                    )
                    exif.saveAttributes()
                    runCatching {
                        val isoRead = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                        val expRead = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                        Log.d(TAG, "DNG post-save read-back iso=$isoRead exposure=$expRead")
                    }
                }.onFailure { e ->
                    Log.w(TAG, "DNG ExifInterface pass skipped/err uri=$uri err=${e.message}")
                    tmp.writeBytes(patchedBytes)
                }
                context.contentResolver.openOutputStream(uri, "wt")?.use { outs ->
                    tmp.inputStream().use { ins -> ins.copyTo(outs) }
                }
                location?.let { MediaGeotag.applyMediaStoreImageLocationColumns(context, uri, it) }
                Log.i(
                    TAG,
                    "apply DNG metadata ok uri=$uri iso=${result.get(CaptureResult.SENSOR_SENSITIVITY) ?: "?"} geo=${location != null}",
                )
            } finally {
                tmp.delete()
            }
        }.onFailure { e ->
            Log.w(TAG, "apply DNG metadata failed uri=$uri err=${e.message}")
        }
    }

    fun applyToJpegUri(
        context: Context,
        uri: Uri,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        location: Location? = null,
    ) {
        applyCommonFd(
            context,
            uri,
            characteristics,
            result,
            location,
            setOrientation = true,
            stampSoftwareTag = true,
        )
    }

    private fun applyCommonFd(
        context: Context,
        uri: Uri,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        location: Location?,
        setOrientation: Boolean,
        stampSoftwareTag: Boolean,
    ) {
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                fillExifFields(exif, characteristics, result, location, setOrientation, stampSoftwareTag)
                exif.saveAttributes()
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Os.fsync(pfd.fileDescriptor)
                    }
                }.onFailure { e -> Log.d(TAG, "fsync after jpeg exif uri=$uri err=${e.message}") }
            }
                ?: Log.w(TAG, "openFileDescriptor(rw) null uri=$uri")
            location?.let { MediaGeotag.applyMediaStoreImageLocationColumns(context, uri, it) }
            Log.i(TAG, "apply JPEG metadata ok uri=$uri geo=${location != null}")
        }.onFailure { e ->
            Log.w(TAG, "apply JPEG metadata failed uri=$uri err=${e.message}")
        }
    }

    private fun fillExifFields(
        exif: ExifInterface,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        location: Location?,
        setOrientation: Boolean,
        stampSoftwareTag: Boolean,
    ) {
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: fallbackIso(characteristics)
        val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val focalMm = result.get(CaptureResult.LENS_FOCAL_LENGTH) ?: fallbackFocalMm(characteristics)
        val aperture = result.get(CaptureResult.LENS_APERTURE) ?: fallbackAperture(characteristics)

        val make = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: "Unknown"
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Device"
        val dateStr = LocalDateTime.now().format(exifDateTimeFormatter)

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
        lensDesc?.let { exif.setAttribute(ExifInterface.TAG_LENS_MODEL, it) }

        iso?.let { exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it.toString()) }

        exposureNs?.let { ns ->
            exposureTimeExifString(ns)?.let { exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, it) }
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
                append(aperture?.let { "%.2f".format(Locale.US, it) } ?: "?")
                append(", ")
                append(focalMm?.let { "%.2f".format(Locale.US, it) } ?: "?")
                append("mm")
                append(captureDebugSuffixForUserComment(result))
            }
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, summary)

        location?.let { loc ->
            runCatching { exif.setLatLong(loc.latitude, loc.longitude) }
                .onFailure { e -> Log.w(TAG, "setLatLong failed err=${e.message}") }
        }

        if (setOrientation) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ORIENTATION_NORMAL)
        }
    }

    private fun captureDebugSuffixForUserComment(result: TotalCaptureResult): String {
        val parts = mutableListOf<String>()
        result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.takeIf { it.isFinite() && it > 0f }?.let { m ->
            parts += "FD=%.2fm".format(Locale.US, m)
        }
        result.get(CaptureResult.LENS_STATE)?.let { parts += "LS=${lensStateShort(it)}" }
        result.get(CaptureResult.CONTROL_AF_STATE)?.let { parts += "AF=${afStateShort(it)}" }
        result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)?.let { skew ->
            parts += "RSS=${skew}ns"
        }
        return if (parts.isEmpty()) "" else ", " + parts.joinToString(", ")
    }

    private fun lensStateShort(v: Int): String =
        when (v) {
            CaptureResult.LENS_STATE_STATIONARY -> "STATIONARY"
            CaptureResult.LENS_STATE_MOVING -> "MOVING"
            else -> "v$v"
        }

    private fun afStateShort(v: Int): String =
        when (v) {
            CaptureResult.CONTROL_AF_STATE_INACTIVE -> "INACTIVE"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> "PASSIVE_SCAN"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> "PASSIVE_FOCUSED"
            CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> "ACTIVE_SCAN"
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> "FOCUSED_LOCKED"
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> "NOT_FOCUSED_LOCKED"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> "PASSIVE_UNFOCUSED"
            else -> "v$v"
        }

    internal fun fallbackFocalMm(chars: CameraCharacteristics): Float? {
        val logical = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: return null
        if (logical.isEmpty()) return null
        return logical.minOrNull() ?: logical[0]
    }

    internal fun fallbackAperture(chars: CameraCharacteristics): Float? {
        val a = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: return null
        if (a.isEmpty()) return null
        return a.minOrNull() ?: a[0]
    }

    internal fun fallbackIso(chars: CameraCharacteristics): Int? {
        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return null
        val maxAnalog = chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY)
        val guess =
            when {
                maxAnalog != null && maxAnalog in isoRange.lower..isoRange.upper -> maxAnalog
                else -> isoRange.upper / 2
            }
        return guess.coerceIn(isoRange.lower, isoRange.upper)
    }

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
