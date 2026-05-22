package dev.pointandshoot

import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

/**
 * Maps TIFF/EXIF orientation (tag **274**, values 1–8) to gallery display rotation.
 * [android.content.ContentResolver.loadThumbnail] applies EXIF for JPEG but usually not DNG TIFF
 * tag 274. JPEG companions are saved with upright pixels + ORIENTATION_NORMAL — do not rotate again
 * in the bespoke gallery.
 */
object DngGalleryOrientation {
    /** Reads EXIF orientation from a DNG/TIFF stream (IFD0 tag 274 + EXIF SubIFD). */
    fun readExifOrientation(inputStream: java.io.InputStream): Int {
        val meta = DngTiffReader().readMetadata(inputStream)
        return meta.exifOrientation
    }

    /** TIFF orientation value (1–8) → [ExifInterface] orientation constant. */
    fun tiffOrientationToExif(tiffValue: Int): Int =
        when (tiffValue) {
            1 -> ExifInterface.ORIENTATION_NORMAL
            2 -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
            3 -> ExifInterface.ORIENTATION_ROTATE_180
            4 -> ExifInterface.ORIENTATION_FLIP_VERTICAL
            5 -> ExifInterface.ORIENTATION_TRANSPOSE
            6 -> ExifInterface.ORIENTATION_ROTATE_90
            7 -> ExifInterface.ORIENTATION_TRANSVERSE
            8 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }

    fun rotationDegreesForExif(orientation: Int): Float =
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            ExifInterface.ORIENTATION_TRANSPOSE -> 90f
            ExifInterface.ORIENTATION_TRANSVERSE -> 270f
            else -> 0f
        }

    fun applyToMatrix(matrix: Matrix, orientation: Int) {
        val deg = rotationDegreesForExif(orientation)
        if (deg != 0f) {
            matrix.postRotate(deg)
        }
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> Unit
        }
    }

    /**
     * When TIFF tag 274 is missing, portrait selfie DNG previews are often landscape buffers
     * (w > h) that need [ORIENTATION_ROTATE_270] to match the JPEG companion.
     */
    fun fallbackOrientationForDng(
        exifOrientation: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): Int {
        if (exifOrientation != ExifInterface.ORIENTATION_NORMAL) return exifOrientation
        if (bitmapWidth > bitmapHeight) return ExifInterface.ORIENTATION_ROTATE_270
        return exifOrientation
    }

    /** Manual matrix rotation only for DNG; JPEG/HEIC thumbs from [loadGalleryThumbnail] are done. */
    fun applyGalleryDisplayRotation(
        source: android.graphics.Bitmap,
        isDng: Boolean,
        exifOrientation: Int,
    ): android.graphics.Bitmap {
        if (!isDng) return source
        val orient = fallbackOrientationForDng(exifOrientation, source.width, source.height)
        if (orient == ExifInterface.ORIENTATION_NORMAL) return source
        val matrix = Matrix()
        applyToMatrix(matrix, orient)
        return android.graphics.Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true,
        )
    }

    fun needsSwapWidthHeight(orientation: Int): Boolean =
        orientation in
            listOf(
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_TRANSVERSE,
            )
}
