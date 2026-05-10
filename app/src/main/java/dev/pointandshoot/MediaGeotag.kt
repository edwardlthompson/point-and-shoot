package dev.pointandshoot

import android.content.ContentValues
import android.content.Context
import android.location.Location
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/**
 * Writes GPS metadata for gallery indexing and desktop EXIF tools.
 * Stills: EXIF lat/long + MediaStore columns. Video: MediaStore columns; in-file GPS for MP4
 * should be set at encode time via [MediaRecorder.applyCaptureGeotag].
 */
object MediaGeotag {
    private const val TAG = "PNS.Geotag"

    fun applyToImageUri(context: Context, uri: Uri, location: Location) {
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setLatLong(location.latitude, location.longitude)
                exif.saveAttributes()
            }
        }.onFailure { e -> Log.w(TAG, "EXIF geotag failed uri=$uri err=${e.message}") }

        runCatching {
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.LATITUDE, location.latitude)
                    put(MediaStore.Images.Media.LONGITUDE, location.longitude)
                }
            context.contentResolver.update(uri, values, null, null)
        }.onFailure { e -> Log.w(TAG, "MediaStore image lat/lon failed uri=$uri err=${e.message}") }
    }

    /**
     * MediaStore indexing columns only — use after in-file EXIF GPS is written elsewhere
     * (e.g. [StillCaptureMetadata]) so galleries still index lat/lon.
     */
    fun applyMediaStoreImageLocationColumns(context: Context, uri: Uri, location: Location) {
        runCatching {
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.LATITUDE, location.latitude)
                    put(MediaStore.Images.Media.LONGITUDE, location.longitude)
                }
            context.contentResolver.update(uri, values, null, null)
        }.onFailure { e -> Log.w(TAG, "MediaStore image lat/lon failed uri=$uri err=${e.message}") }
    }

    fun applyToVideoUri(context: Context, uri: Uri, location: Location) {
        runCatching {
            val values =
                ContentValues().apply {
                    put(MediaStore.Video.Media.LATITUDE, location.latitude)
                    put(MediaStore.Video.Media.LONGITUDE, location.longitude)
                }
            context.contentResolver.update(uri, values, null, null)
        }.onFailure { e -> Log.w(TAG, "MediaStore video lat/lon failed uri=$uri err=${e.message}") }
    }
}
