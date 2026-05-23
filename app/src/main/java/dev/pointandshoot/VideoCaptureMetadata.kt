package dev.pointandshoot

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/**
 * Embeds capture FPS (and a short codec label) into saved in-app videos and exposes read helpers
 * for the bespoke gallery.
 *
 * **Do not** run [androidx.exifinterface.media.ExifInterface.saveAttributes] on MP4 — same class
 * of file corruption as DNG (see dng-save-pipeline-lock).
 */
object VideoCaptureMetadata {
    private const val TAG = "PNS.VideoMeta"
    private val DESCRIPTION_FPS = Regex("""(\d+)\s*fps""", RegexOption.IGNORE_CASE)

    data class CaptureInfo(
        val captureFps: Int,
        val codecLabel: String,
        val mimeType: String,
    )

    data class ReadInfo(
        val frameRate: String?,
        val durationMs: Long?,
        val bitRate: Long?,
        val codec: String?,
        /** Muxer orientation hint / retriever rotation (0, 90, 180, 270). */
        val rotationDegrees: Int = 0,
    )

    /** After mux finalize — MediaStore description carries codec + capture FPS for gallery/system UI. */
    fun applyAfterFinalize(context: Context, uri: Uri, info: CaptureInfo) {
        val app = context.applicationContext
        runCatching {
            val desc = buildDescription(info.codecLabel, info.captureFps)
            val values =
                ContentValues().apply {
                    put(MediaStore.Video.Media.DESCRIPTION, desc)
                }
            app.contentResolver.update(uri, values, null, null)
            Log.i(TAG, "video metadata desc=$desc uri=$uri")
        }.onFailure { e ->
            Log.w(TAG, "MediaStore description update failed uri=$uri: ${e.message}")
        }
    }

    fun readFromUri(context: Context, uri: Uri): ReadInfo {
        val fromStore = readDescriptionFields(context, uri)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val fpsRaw =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                        ?.let { frameCountStr ->
                            val frames = frameCountStr.toLongOrNull() ?: return@let null
                            val durationMs =
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    ?.toLongOrNull()
                                    ?: return@let null
                            if (durationMs <= 0L) return@let null
                            val fps = frames * 1000.0 / durationMs
                            if (fps > 0.0) "%.0f".format(fps) else null
                        }
            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val bitRate =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            val rotation =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull()
                    ?: 0
            val mergedFps =
                fpsRaw?.trim()?.takeIf { it.isNotEmpty() }
                    ?: fromStore.captureFps?.toString()
            ReadInfo(
                frameRate = mergedFps,
                durationMs = durationMs,
                bitRate = bitRate,
                codec = fromStore.codecLabel,
                rotationDegrees = rotation,
            )
        } catch (e: Exception) {
            Log.w(TAG, "readFromUri failed uri=$uri: ${e.message}")
            ReadInfo(
                frameRate = fromStore.captureFps?.toString(),
                durationMs = null,
                bitRate = null,
                codec = fromStore.codecLabel,
                rotationDegrees = 0,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun formatDuration(durationMs: Long?): String? {
        if (durationMs == null || durationMs <= 0) return null
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (min > 0) "%d:%02d".format(min, sec) else "%ds".format(sec)
    }

    fun formatFrameRate(fps: String?): String? {
        if (fps.isNullOrBlank()) return null
        val v = fps.toFloatOrNull() ?: return fps
        return if (v == v.toInt().toFloat()) "${v.toInt()} fps" else "%.1f fps".format(v)
    }

    internal data class DescriptionFields(
        val captureFps: Int?,
        val codecLabel: String?,
    )

    private fun readDescriptionFields(context: Context, uri: Uri): DescriptionFields {
        return runCatching {
            val projection = arrayOf(MediaStore.Video.Media.DESCRIPTION)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use DescriptionFields(null, null)
                val desc = cursor.getString(0) ?: return@use DescriptionFields(null, null)
                parseDescription(desc)
            } ?: DescriptionFields(null, null)
        }.getOrElse { DescriptionFields(null, null) }
    }

    internal fun parseDescription(desc: String): DescriptionFields {
        val fps = DESCRIPTION_FPS.find(desc)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val codec =
            desc.split(" · ")
                .getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        return DescriptionFields(fps, codec)
    }

    private fun buildDescription(codecLabel: String, captureFps: Int): String =
        "Point & Shoot · $codecLabel · ${captureFps}fps"
}
