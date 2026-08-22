@file:Suppress("MagicNumber", "NestedBlockDepth")

package dev.pointandshoot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Apply a LUT to a JPEG still, or export a graded video proxy (sampled frames). */
object LogLutBakeExport {
    data class Outcome(val ok: Boolean, val message: String, val uri: Uri? = null)

    suspend fun bakeStill(context: Context, source: Uri, lut: Lut3D, name: String): Outcome {
        val src = loadGalleryThumbnail(context, source, 4096) ?: return Outcome(false, "Could not decode still")
        val baked = applyLut(src, lut)
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "baked_${name.substringBeforeLast('.')}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/PointAndShoot")
                }
            }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return Outcome(false, "MediaStore insert failed")
        context.contentResolver.openOutputStream(uri)?.use { baked.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (baked !== src) baked.recycle()
        src.recycle()
        return Outcome(true, "Baked JPEG saved", uri)
    }

    fun bakeVideoProxy(context: Context, video: Uri, lut: Lut3D, maxFrames: Int = 24): Outcome {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, video)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (dur <= 0L) return Outcome(false, "Video has no duration")
            val zip = File(context.cacheDir, "pns_baked_proxy.zip")
            ZipOutputStream(zip.outputStream()).use { zos ->
                val n = maxFrames.coerceIn(4, 48)
                for (i in 0 until n) {
                    val tMs = dur * i / n
                    val frame =
                        retriever.getFrameAtTime(tMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                    val baked = applyLut(frame, lut)
                    zos.putNextEntry(ZipEntry("frame_${i.toString().padStart(3, '0')}.jpg"))
                    baked.compress(Bitmap.CompressFormat.JPEG, 85, zos)
                    zos.closeEntry()
                    if (baked !== frame) baked.recycle()
                    frame.recycle()
                }
            }
            val destFile = File(File(context.cacheDir, "share").apply { mkdirs() }, "pns_baked_proxy.zip")
            zip.copyTo(destFile, overwrite = true)
            zip.delete()
            val uri =
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    SharingManager.FILE_PROVIDER_AUTHORITY,
                    destFile,
                )
            Outcome(true, "Baked frame proxy ready to share", uri)
        } catch (e: Exception) {
            Outcome(false, e.message ?: "proxy bake failed")
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun applyLut(src: Bitmap, lut: Lut3D): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        val rgb = FloatArray(3)
        val mapped = FloatArray(3)
        for (i in pixels.indices) {
            val c = pixels[i]
            rgb[0] = Color.red(c) / 255f
            rgb[1] = Color.green(c) / 255f
            rgb[2] = Color.blue(c) / 255f
            LutPipeline.applyTrilinearInto(rgb[0], rgb[1], rgb[2], lut, mapped, 0)
            pixels[i] =
                Color.argb(
                    Color.alpha(c),
                    (mapped[0] * 255f).toInt().coerceIn(0, 255),
                    (mapped[1] * 255f).toInt().coerceIn(0, 255),
                    (mapped[2] * 255f).toInt().coerceIn(0, 255),
                )
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
