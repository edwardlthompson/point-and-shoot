@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/** Blur/box faces-ish regions on a JPEG copy, then share. Never edits DNG. */
object GalleryRedact {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

    suspend fun redactCopy(context: Context, source: Uri, boxes: List<Box>, displayName: String): Uri? {
        val bmp = loadGalleryThumbnail(context, source, 2048) ?: return null
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(220, 20, 20, 20)
                style = Paint.Style.FILL
            }
        boxes.forEach { box ->
            canvas.drawRoundRect(
                RectF(
                    box.left * out.width,
                    box.top * out.height,
                    box.right * out.width,
                    box.bottom * out.height,
                ),
                12f,
                12f,
                paint,
            )
        }
        if (boxes.isEmpty()) {
            // Default privacy plate: hide the bottom-right eighth (typical face/plate zone).
            canvas.drawRoundRect(
                RectF(out.width * 0.62f, out.height * 0.08f, out.width * 0.95f, out.height * 0.38f),
                16f,
                16f,
                paint,
            )
        }
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "redact_${displayName.substringBeforeLast('.')}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/PointAndShoot")
                }
            }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                out.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            }
        }
        if (out !== bmp) out.recycle()
        bmp.recycle()
        return uri
    }
}
