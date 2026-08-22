@file:Suppress("MagicNumber", "LoopWithTooManyJumpStatements")

package dev.pointandshoot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Trim between chapter marks, jump list, and JPEG frame extract. */
object GalleryVideoFinish {
    data class Result(val ok: Boolean, val message: String, val uri: Uri? = null)

    fun chapterStartsMs(): List<Long> = VideoChapterMarks.snapshot().map { it.offsetMs }

    fun extractFrame(context: Context, video: Uri, atMs: Long): Result {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, video)
            val frame = retriever.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: return Result(false, "No frame at ${atMs}ms")
            val saved = saveJpeg(context, frame, "pns_frame_${atMs}ms.jpg")
            frame.recycle()
            if (saved == null) Result(false, "Could not save frame") else Result(true, "Saved frame", saved)
        } catch (e: Exception) {
            Result(false, e.message ?: "extract failed")
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun trimToFile(context: Context, video: Uri, startMs: Long, endMs: Long): Result {
        if (endMs <= startMs) return Result(false, "End must be after start")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, video, null)
            var videoTrack = -1
            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && videoTrack < 0) videoTrack = i
                if (mime.startsWith("audio/") && audioTrack < 0) audioTrack = i
            }
            if (videoTrack < 0) return Result(false, "No video track")
            val outFile = File(context.cacheDir, "pns_trim_${startMs}_${endMs}.mp4")
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            extractor.selectTrack(videoTrack)
            val vIdx = muxer.addTrack(extractor.getTrackFormat(videoTrack))
            val aIdx =
                if (audioTrack >= 0) {
                    extractor.selectTrack(audioTrack)
                    muxer.addTrack(extractor.getTrackFormat(audioTrack))
                } else {
                    -1
                }
            muxer.start()
            val buf = java.nio.ByteBuffer.allocateDirect(1_048_576)
            val info = MediaCodec.BufferInfo()
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val pts = extractor.sampleTime
                if (pts > endUs) break
                info.offset = 0
                info.size = extractor.readSampleData(buf, 0)
                if (info.size < 0) break
                info.presentationTimeUs = (pts - startUs).coerceAtLeast(0L)
                info.flags = muxerFlagsFromExtractor(extractor.sampleFlags)
                val dest = if (track == videoTrack) vIdx else aIdx
                if (dest >= 0 && pts >= startUs) {
                    muxer.writeSampleData(dest, buf, info)
                }
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            muxer = null
            val uri = copyCacheToDcim(context, outFile, "pns_trim_${startMs}_${endMs}.mp4", "video/mp4")
            outFile.delete()
            if (uri == null) Result(false, "Trim muxed but DCIM copy failed") else Result(true, "Trim saved", uri)
        } catch (e: Exception) {
            Result(false, e.message ?: "trim failed")
        } finally {
            runCatching { extractor.release() }
            runCatching { muxer?.release() }
        }
    }

    /** Map extractor sample bits onto MediaCodec buffer flags lint accepts. */
    private fun muxerFlagsFromExtractor(sampleFlags: Int): Int {
        var flags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }

    private fun saveJpeg(context: Context, bitmap: Bitmap, name: String): Uri? {
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/PointAndShoot")
                }
            }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            uri
        }.getOrNull()
    }

    private fun copyCacheToDcim(context: Context, file: File, name: String, mime: String): Uri? {
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/PointAndShoot")
                }
            }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            uri
        }.getOrNull()
    }
}
