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

/** True when [uri] is an Adobe DNG (gallery must decode without [ContentResolver.loadThumbnail] EXIF bake-in). */
fun isDngMediaUri(context: Context, uri: Uri): Boolean {
    val path = uri.toString().lowercase()
    if (path.endsWith(".dng")) return true
    return context.contentResolver.getType(uri) == "image/x-adobe-dng"
}

private fun isTiffMediaUri(context: Context, uri: Uri): Boolean {
    val path = uri.toString().lowercase()
    if (path.endsWith(".tif") || path.endsWith(".tiff")) return true
    val mime = context.contentResolver.getType(uri)?.lowercase() ?: return false
    return mime == "image/tiff" || mime == "image/x-tiff"
}

/**
 * Loads a small bitmap for gallery thumbnails. Prefer [android.content.ContentResolver.loadThumbnail]
 * on Q+ for JPEG/HEIC; **DNG** always uses [decodeScaledBitmap] so orientation is applied once in
 * [DngGalleryOrientation.applyGalleryDisplayRotation].
 */
suspend fun loadGalleryThumbnail(context: Context, uri: Uri, sizePx: Int = 160): Bitmap? =
    withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        runCatching {
            if (isDngMediaUri(context, uri)) {
                decodeScaledBitmap(cr, uri, sizePx)
            } else if (isTiffMediaUri(context, uri)) {
                decodeTiff16Bitmap(cr, uri, sizePx)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    cr.loadThumbnail(uri, Size(sizePx, sizePx), null)
                } catch (_: Throwable) {
                    decodeScaledBitmap(cr, uri, sizePx)
                }
            } else {
                decodeScaledBitmap(cr, uri, sizePx)
            }
        }.getOrNull()?.also { bmp ->
            PnsBitmapGuard.onAllocated("GalleryThumbnail", bmp)
        }
    }

private fun decodeTiff16Bitmap(
    cr: android.content.ContentResolver,
    uri: Uri,
    maxPx: Int,
): Bitmap? =
    runCatching {
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
        Tiff16PreviewDecoder.decodeThumbnail(bytes, maxPx)
    }.getOrNull()

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
 * [Intent.createChooser] for the first hop, which forces a chooser every time and hides **Always**.
 *
 * If no handler exists for the resolved MIME type, retries [Intent.ACTION_VIEW] with a generic
 * wildcard MIME (any type), then offers [Intent.ACTION_SEND] via [Intent.createChooser] so
 * **Share** / **Open with** paths still work (`BUILD_PLAN` UX backlog — gallery thumb).
 *
 * @return true if an activity was started (viewer or chooser); false if nothing matched.
 */
fun openMediaWithSystemResolver(context: Context, uri: Uri): Boolean {
    val cr = context.contentResolver
    val mime = cr.getType(uri) ?: "*/*"

    fun viewIntent(resolvedMime: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun tryStart(intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    if (tryStart(viewIntent(mime))) return true
    if (mime != "*/*" && tryStart(viewIntent("*/*"))) return true

    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser =
        Intent.createChooser(sendIntent, "Share or open capture").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    if (tryStart(chooser)) return true

    Toast.makeText(
        context,
        "No app can open this file — try Share from another app or install a viewer.",
        Toast.LENGTH_LONG,
    ).show()
    return false
}
