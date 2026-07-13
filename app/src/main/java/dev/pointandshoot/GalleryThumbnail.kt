@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Size
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
 * on Q+ for JPEG/HEIC; **DNG** tries ImageDecoder → MediaStore thumb → embedded JPEG → BitmapFactory
 * so tray / bespoke gallery are not blank when platform BitmapFactory cannot open Bayer DNGs.
 */
suspend fun loadGalleryThumbnail(context: Context, uri: Uri, sizePx: Int = 160): Bitmap? =
    withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        runCatching {
            if (isDngMediaUri(context, uri)) {
                decodeDngThumbnail(cr, uri, sizePx)
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

/**
 * DNG tray/gallery preview: platform [BitmapFactory] often returns null for Bayer DNG.
 * Prefer ImageDecoder / MediaStore thumbnail / embedded JPEG preview strip.
 */
private fun decodeDngThumbnail(
    cr: android.content.ContentResolver,
    uri: Uri,
    maxPx: Int,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching {
            val src = ImageDecoder.createSource(cr, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.isMutableRequired = false
                val w = info.size.width.coerceAtLeast(1)
                val h = info.size.height.coerceAtLeast(1)
                val sample = computeSample(w, h, maxPx)
                if (sample > 1) {
                    decoder.setTargetSampleSize(sample)
                }
            }
        }.getOrNull()?.let { return it }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { cr.loadThumbnail(uri, Size(maxPx, maxPx), null) }.getOrNull()?.let { return it }
    }
    runCatching {
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        decodeEmbeddedJpegFromDng(bytes, maxPx)
            ?: DngBayerPreviewDecoder.decodeThumbnail(bytes, maxPx)
    }.getOrNull()?.let { return it }
    return decodeScaledBitmap(cr, uri, maxPx)
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

/** TIFF tags for JPEG interchange (IFD0 or SubIFD preview). */
private const val TAG_JPEG_INTERCHANGE_FORMAT = 513
private const val TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 514
private const val TAG_SUB_IFD = 330

/**
 * Extract an embedded JPEG preview from a DNG (common on `DngCreator` / OEM outputs).
 * Walks IFD0 then SubIFD pointers looking for tags 513/514.
 */
private fun decodeEmbeddedJpegFromDng(bytes: ByteArray, maxPx: Int): Bitmap? {
    if (bytes.size < 16) return null
    val little = bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte()
    val big = bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte()
    if (!little && !big) return null
    val order = if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
    val bb = ByteBuffer.wrap(bytes).order(order)
    if (bb.getShort(2).toInt() != 42) return null
    val firstIfd = bb.getInt(4)
    val jpeg =
        findJpegInIfd(bb, bytes, firstIfd)
            ?: findJpegViaSubIfd(bb, bytes, firstIfd)
            ?: return null
    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
    val w = opts.outWidth
    val h = opts.outHeight
    if (w <= 0 || h <= 0) return null
    val sample = computeSample(w, h, maxPx)
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, decodeOpts)
}

private fun findJpegViaSubIfd(bb: ByteBuffer, bytes: ByteArray, ifdOffset: Int): ByteArray? {
    val entry = readIfdEntry(bb, bytes, ifdOffset, TAG_SUB_IFD) ?: return null
    val offsets = readLongOrShortArray(bb, bytes, entry)
    for (off in offsets) {
        if (off <= 0 || off >= bytes.size) continue
        findJpegInIfd(bb, bytes, off)?.let { return it }
    }
    return null
}

private data class IfdEntry(val type: Int, val count: Int, val valueOrOffset: Int)

private fun findJpegInIfd(bb: ByteBuffer, bytes: ByteArray, ifdOffset: Int): ByteArray? {
    val offEntry = readIfdEntry(bb, bytes, ifdOffset, TAG_JPEG_INTERCHANGE_FORMAT) ?: return null
    val lenEntry = readIfdEntry(bb, bytes, ifdOffset, TAG_JPEG_INTERCHANGE_FORMAT_LENGTH) ?: return null
    val jpegOffset = readIntValue(bb, bytes, offEntry) ?: return null
    val jpegLen = readIntValue(bb, bytes, lenEntry) ?: return null
    if (jpegOffset <= 0 || jpegLen <= 0) return null
    if (jpegOffset + jpegLen > bytes.size) return null
    return bytes.copyOfRange(jpegOffset, jpegOffset + jpegLen)
}

private fun readIfdEntry(bb: ByteBuffer, bytes: ByteArray, ifdOffset: Int, wantTag: Int): IfdEntry? {
    if (ifdOffset <= 0 || ifdOffset >= bytes.size - 2) return null
    val entryCount = bb.getShort(ifdOffset).toInt() and 0xFFFF
    var p = ifdOffset + 2
    repeat(entryCount) {
        if (p + 12 > bytes.size) return null
        val tag = bb.getShort(p).toInt() and 0xFFFF
        val type = bb.getShort(p + 2).toInt() and 0xFFFF
        val count = bb.getInt(p + 4)
        val value = bb.getInt(p + 8)
        if (tag == wantTag) return IfdEntry(type, count, value)
        p += 12
    }
    return null
}

@Suppress("UnusedParameter")
private fun readIntValue(bb: ByteBuffer, bytes: ByteArray, entry: IfdEntry): Int? {
    // SHORT / LONG inline or pointed
    return when (entry.type) {
        3 -> { // SHORT
            if (entry.count == 1) entry.valueOrOffset and 0xFFFF else null
        }
        4 -> { // LONG
            if (entry.count == 1) entry.valueOrOffset else null
        }
        else -> null
    }
}

private fun readLongOrShortArray(bb: ByteBuffer, bytes: ByteArray, entry: IfdEntry): IntArray {
    val count = entry.count.coerceAtMost(32)
    if (count <= 0) return intArrayOf()
    val typeSize = if (entry.type == 3) 2 else 4
    val nbytes = typeSize * count
    val base =
        if (nbytes <= 4) {
            // value packed in valueOrOffset little-endian style — re-read from entry field
            null
        } else {
            entry.valueOrOffset
        }
    val out = IntArray(count)
    if (base == null) {
        // Single LONG/SHORT in valueOrOffset
        out[0] = if (entry.type == 3) entry.valueOrOffset and 0xFFFF else entry.valueOrOffset
        return out.copyOf(1)
    }
    if (base < 0 || base + nbytes > bytes.size) return intArrayOf()
    for (i in 0 until count) {
        out[i] =
            if (entry.type == 3) {
                bb.getShort(base + i * 2).toInt() and 0xFFFF
            } else {
                bb.getInt(base + i * 4)
            }
    }
    return out
}

private fun computeSample(width: Int, height: Int, maxPx: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > maxPx) {
        sample *= 2
    }
    return sample
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
