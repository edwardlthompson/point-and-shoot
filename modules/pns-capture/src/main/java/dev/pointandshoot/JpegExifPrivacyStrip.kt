package dev.pointandshoot

import androidx.exifinterface.media.ExifInterface

/**
 * Removes identifying EXIF tags from JPEG stills (Sprint **29.1** / GrapheneOS `REMOVE_EXIF` parity).
 * Technical exposure tags (ISO, shutter, focal, aperture, flash, orientation) stay for library sorting.
 */
object JpegExifPrivacyStrip {

    internal val privacyTagNames: List<String> =
        listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
        )

    /** Clears privacy tags in-place; returns count of tags cleared. */
    fun stripInPlace(exif: ExifInterface): Int {
        var removed = 0
        for (tag in privacyTagNames) {
            val before = exif.getAttribute(tag)
            if (before != null) {
                exif.setAttribute(tag, null)
                removed++
            }
        }
        return removed
    }
}
