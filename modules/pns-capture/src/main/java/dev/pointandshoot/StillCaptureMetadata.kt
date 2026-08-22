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
 * existing EXIF IFD entries in-place ([TiffExifSubIfdCapturePatch]).
 *
 * **DNG loadability (locked May 2026):** Do **not** call [ExifInterface.saveAttributes] on DNG — it
 * corrupts legacy-device row-strip TIFFs (Lightroom/ACR cannot open). See [applyToDngUri] and
 * `.cursor/rules/dng-save-pipeline-lock.mdc`.
 */
object StillCaptureMetadata {
    private const val TAG = "PNS.StillExif"

    private const val ORIENTATION_NORMAL = ExifInterface.ORIENTATION_NORMAL.toString()
    private const val CREDIT_MAX_CHARS = 128

    private val exifDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun applyToDngUri(
        context: Context,
        uri: Uri,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        location: Location? = null,
        stripPrivacyExif: Boolean = false,
    ) {
        if (stripPrivacyExif) {
            Log.i(TAG, "apply DNG metadata skipped stripPrivacyExif=true uri=$uri")
            PnsAdbLog.i(context, "exifStrip dngMetadataSkipped ok=true")
            return
        }
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

            // Do NOT run [ExifInterface.saveAttributes] on DNG: it rewrites the TIFF for JPEG-style
            // EXIF and destroys legacy-device row-strip payloads (Lightroom/ACR "cannot load"; rawpy may
            // still decode). In-place IFD patches above preserve strip offsets from [DngCreator].
            if (!writeStagedBytesToUri(context, uri, patchedBytes, "dng")) {
                Log.w(TAG, "DNG write failed uri=$uri")
                return@runCatching
            }
            location?.let { MediaGeotag.applyMediaStoreImageLocationColumns(context, uri, it) }
            updateImageDescription(
                context = context,
                uri = uri,
                result = result,
                colorSpaceTarget = null,
            )
            Log.i(
                TAG,
                "apply DNG metadata ok uri=$uri iso=${result.get(CaptureResult.SENSOR_SENSITIVITY) ?: "?"} geo=${location != null}",
            )
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
        colorSpaceTarget: ColorSpaceTarget = ColorSpaceTarget.DisplayP3,
        stripPrivacyExif: Boolean = false,
        artist: String? = null,
        copyright: String? = null,
    ) {
        applyCommonFd(
            context,
            uri,
            characteristics,
            result,
            location,
            setOrientation = true,
            stampSoftwareTag = true,
            embedIccProfile = true,
            colorSpaceTarget = colorSpaceTarget,
            stripPrivacyExif = stripPrivacyExif,
            artist = artist,
            copyright = copyright,
        )
    }

    /**
     * Sprint **15.17** — AVIF stills on API 34+ may carry ICC via `colr` `prof` when muxed in Kotlin
     * ([AvifStillMuxer]). Native `.so` AVIF uses CICP `nclx` until remux lands.
     */
    fun applyToAvifUri(
        context: Context,
        uri: Uri,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        location: Location? = null,
        colorSpaceTarget: ColorSpaceTarget = ColorSpaceTarget.DisplayP3,
        stripPrivacyExif: Boolean = false,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        applyCommonFd(
            context,
            uri,
            characteristics,
            result,
            location,
            setOrientation = false,
            stampSoftwareTag = false,
            embedIccProfile = false,
            colorSpaceTarget = colorSpaceTarget,
            stripPrivacyExif = stripPrivacyExif,
        )
        Log.i(TAG, "apply AVIF metadata ok uri=$uri colorSpace=$colorSpaceTarget (ICC via muxer when used)")
    }

    /**
     * TIFF metadata path for in-app 16-bit still exports.
     *
     * Uses [ExifInterface.saveAttributes] on TIFF (safe), unlike DNG where rewrite is forbidden.
     * This stamps ISO / shutter / focal / aperture so gallery metadata rows are populated.
     */
    fun applyToTiffUri(
        context: Context,
        uri: Uri,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        location: Location? = null,
        stripPrivacyExif: Boolean = false,
    ) {
        // TIFF EXIF capture fields are embedded directly in [RgbTiff16Encoder].
        // Here we only mirror geotag + compact MediaStore summary.
        if (!stripPrivacyExif) {
            location?.let { MediaGeotag.applyMediaStoreImageLocationColumns(context, uri, it) }
        }
        updateImageDescription(
            context = context,
            uri = uri,
            result = result,
            colorSpaceTarget = ColorSpaceTarget.Rec2020,
        )
        Log.i(TAG, "apply TIFF metadata ok uri=$uri geo=${location != null && !stripPrivacyExif}")
    }

    private fun applyCommonFd(
        context: Context,
        uri: Uri,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        location: Location?,
        setOrientation: Boolean,
        stampSoftwareTag: Boolean,
        embedIccProfile: Boolean,
        colorSpaceTarget: ColorSpaceTarget,
        stripPrivacyExif: Boolean = false,
        artist: String? = null,
        copyright: String? = null,
    ) {
        val effectiveLocation = if (stripPrivacyExif) null else location
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                fillExifFields(
                    exif,
                    characteristics,
                    result,
                    effectiveLocation,
                    setOrientation,
                    stampSoftwareTag,
                    stripPrivacyExif,
                    artist,
                    copyright,
                )
                exif.saveAttributes()
                if (stripPrivacyExif) {
                    val removed = JpegExifPrivacyStrip.stripInPlace(exif)
                    exif.saveAttributes()
                    PnsAdbLog.i(context, "exifStrip ok=true removed=$removed")
                    Log.i(TAG, "exif privacy strip ok uri=$uri removed=$removed")
                }
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Os.fsync(pfd.fileDescriptor)
                    }
                }.onFailure { e -> Log.d(TAG, "fsync after jpeg exif uri=$uri err=${e.message}") }
            }
                ?: Log.w(TAG, "openFileDescriptor(rw) null uri=$uri")
            if (embedIccProfile) {
                embedIccProfileInJpegFd(context, uri, colorSpaceTarget)
            }
            if (!stripPrivacyExif) {
                effectiveLocation?.let { MediaGeotag.applyMediaStoreImageLocationColumns(context, uri, it) }
            }
            updateImageDescription(
                context = context,
                uri = uri,
                result = result,
                colorSpaceTarget = colorSpaceTarget,
            )
            Log.i(
                TAG,
                "apply JPEG metadata ok uri=$uri geo=${effectiveLocation != null} icc=$embedIccProfile strip=$stripPrivacyExif",
            )
        }.onFailure { e ->
            Log.w(TAG, "apply JPEG metadata failed uri=$uri err=${e.message}")
        }
    }

    private fun updateImageDescription(
        context: Context,
        uri: Uri,
        result: TotalCaptureResult,
        colorSpaceTarget: ColorSpaceTarget?,
    ) {
        runCatching {
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            val expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val focal = result.get(CaptureResult.LENS_FOCAL_LENGTH)
            val aperture = result.get(CaptureResult.LENS_APERTURE)
            val wb = result.get(CaptureResult.CONTROL_AWB_MODE)
            val desc =
                buildString {
                    append("pnsStillMeta")
                    iso?.let { append(" iso=").append(it) }
                    expNs?.let { append(" expNs=").append(it) }
                    focal?.let { append(" focalMm=").append(String.format(Locale.US, "%.2f", it)) }
                    aperture?.let { append(" aperture=").append(String.format(Locale.US, "%.2f", it)) }
                    wb?.let { append(" awb=").append(it) }
                    colorSpaceTarget?.let { append(" color=").append(it.displayName) }
                }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DESCRIPTION, desc)
            }
            context.contentResolver.update(uri, values, null, null)
        }.onFailure { e ->
            Log.w(TAG, "MediaStore description update failed uri=$uri: ${e.message}")
        }
    }

    private fun embedIccProfileInJpegFd(
        context: Context,
        uri: Uri,
        colorSpaceTarget: ColorSpaceTarget,
    ) {
        val icc = IccProfileBuilder.forColorSpaceTarget(colorSpaceTarget)
        val jpeg =
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return
        val patched = JpegIccEmbedder.embedAfterSoi(jpeg, icc)
        if (patched.contentEquals(jpeg)) return
        if (!writeStagedBytesToUri(context, uri, patched, "jpeg-icc")) {
            Log.w(TAG, "ICC embed write failed uri=$uri")
        }
    }

    private fun writeStagedBytesToUri(
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        stageLabel: String,
    ): Boolean {
        val staged =
            runCatching {
                val stagedFile = File.createTempFile("pns_$stageLabel", ".bin", context.cacheDir)
                stagedFile.writeBytes(bytes)
                stagedFile
            }.getOrNull() ?: return false
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { outs ->
                staged.inputStream().use { input -> input.copyTo(outs) }
                outs.flush()
            } != null
        } finally {
            runCatching { staged.delete() }
        }
    }

    private fun fillExifFields(
        exif: ExifInterface,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        location: Location?,
        setOrientation: Boolean,
        stampSoftwareTag: Boolean,
        stripPrivacyExif: Boolean = false,
        artist: String? = null,
        copyright: String? = null,
    ) {
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: fallbackIso(characteristics)
        val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val focalMm = result.get(CaptureResult.LENS_FOCAL_LENGTH) ?: fallbackFocalMm(characteristics)
        val aperture = result.get(CaptureResult.LENS_APERTURE) ?: fallbackAperture(characteristics)

        if (!stripPrivacyExif) {
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
            sanitizeCredit(artist)?.let { exif.setAttribute(ExifInterface.TAG_ARTIST, it) }
            sanitizeCredit(copyright)?.let { exif.setAttribute(ExifInterface.TAG_COPYRIGHT, it) }

            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val lensDesc =
                when (lensFacing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front Camera"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back Camera"
                    else -> null
                }
            lensDesc?.let { exif.setAttribute(ExifInterface.TAG_LENS_MODEL, it) }

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
        }

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

    fun sanitizeCredit(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return trimmed.take(CREDIT_MAX_CHARS)
    }

    fun exposureTimeExifString(ns: Long): String? {
        if (ns <= 0L) return null
        val sec = ns / 1_000_000_000.0
        if (sec >= 1.0) {
            return "%.4f".format(Locale.US, sec).trimEnd('0').trimEnd('.').ifEmpty { "0" }
        }
        val denom = (1.0 / sec).roundToInt().coerceAtLeast(1)
        return "1/$denom"
    }
}
