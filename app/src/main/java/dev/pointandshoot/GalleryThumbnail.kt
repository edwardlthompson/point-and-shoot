package dev.pointandshoot

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a small bitmap for gallery thumbnails. Prefer [android.content.ContentResolver.loadThumbnail]
 * on Q+; fall back to [ImageDecoder] when DNG / exotic MIME types fail.
 */
suspend fun loadGalleryThumbnail(context: Context, uri: Uri, sizePx: Int = 160): Bitmap? =
    withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    cr.loadThumbnail(uri, Size(sizePx, sizePx), null)
                } catch (_: Throwable) {
                    decodeScaledBitmap(cr, uri, sizePx)
                }
            } else {
                decodeScaledBitmap(cr, uri, sizePx)
            }
        }.getOrNull()
    }

private fun decodeScaledBitmap(cr: android.content.ContentResolver, uri: Uri, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (maxOf(w, h) / sample > maxPx) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

/**
 * Opens media with an implicit [Intent.ACTION_VIEW] so the system resolver runs and the user can
 * pick a viewer with **Just once** / **Always** (standard Android default-app flow). Avoid
 * [Intent.createChooser], which forces a chooser every time and hides **Always**.
 */
fun openMediaWithSystemResolver(context: Context, uri: Uri) {
    val mime = context.contentResolver.getType(uri) ?: "*/*"
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}
