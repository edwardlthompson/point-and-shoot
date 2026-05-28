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

    private val DESCRIPTION_AUDIO =

        Regex(

            """AAC\s+(\d+)kHz\s+(\d+)k(?:bps)?\s+(\d+)ch""",

            RegexOption.IGNORE_CASE,

        )



    data class CaptureInfo(

        val captureFps: Int,

        val codecLabel: String,

        val mimeType: String,

        val audioSampleRateHz: Int? = null,

        val audioAacBitrateBps: Int? = null,

        val audioChannelCount: Int? = null,

        val audioHiFi: Boolean = false,

        val audioWindNoiseReduction: Boolean = false,

    ) {

        val hasAudio: Boolean

            get() = audioSampleRateHz != null && audioSampleRateHz > 0

    }



    data class ReadInfo(

        val frameRate: String?,

        val durationMs: Long?,

        val bitRate: Long?,

        val codec: String?,

        /** Muxer orientation hint / retriever rotation (0, 90, 180, 270). */

        val rotationDegrees: Int = 0,

        val audioSampleRateHz: Int? = null,

        val audioAacBitrateBps: Int? = null,

        val audioChannelCount: Int? = null,

        val audioHiFi: Boolean = false,

        val audioWindNoiseReduction: Boolean = false,

    ) {

        fun audioSummaryLabel(): String? {

            val rate = audioSampleRateHz ?: return null

            val kbps = (audioAacBitrateBps ?: 0) / 1000

            val ch = audioChannelCount ?: 2

            val extras = buildList {

                if (audioHiFi) add("Hi-Fi")

                if (audioWindNoiseReduction) add("wind NS")

            }

            val tail = if (extras.isEmpty()) "" else " · ${extras.joinToString(", ")}"

            return "AAC ${rate / 1000}kHz ${kbps}k ${ch}ch$tail"

        }

    }



    /** After mux finalize — MediaStore description carries codec + capture FPS for gallery/system UI. */

    fun applyAfterFinalize(context: Context, uri: Uri, info: CaptureInfo) {

        val app = context.applicationContext

        runCatching {

            val desc = buildDescription(info)

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
                mergeFrameRateForDisplay(
                    embeddedCaptureFps = fromStore.captureFps,
                    retrieverFpsRaw = fpsRaw,
                )

            ReadInfo(

                frameRate = mergedFps,

                durationMs = durationMs,

                bitRate = bitRate,

                codec = fromStore.codecLabel,

                rotationDegrees = rotation,

                audioSampleRateHz = fromStore.audioSampleRateHz,

                audioAacBitrateBps = fromStore.audioAacBitrateBps,

                audioChannelCount = fromStore.audioChannelCount,

                audioHiFi = fromStore.audioHiFi,

                audioWindNoiseReduction = fromStore.audioWindNoiseReduction,

            )

        } catch (e: Exception) {

            Log.w(TAG, "readFromUri failed uri=$uri: ${e.message}")

            ReadInfo(

                frameRate = fromStore.captureFps?.toString(),

                durationMs = null,

                bitRate = null,

                codec = fromStore.codecLabel,

                rotationDegrees = 0,

                audioSampleRateHz = fromStore.audioSampleRateHz,

                audioAacBitrateBps = fromStore.audioAacBitrateBps,

                audioChannelCount = fromStore.audioChannelCount,

                audioHiFi = fromStore.audioHiFi,

                audioWindNoiseReduction = fromStore.audioWindNoiseReduction,

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

        val audioSampleRateHz: Int? = null,

        val audioAacBitrateBps: Int? = null,

        val audioChannelCount: Int? = null,

        val audioHiFi: Boolean = false,

        val audioWindNoiseReduction: Boolean = false,

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



    /**
     * Gallery / readout FPS. [MediaMetadataRetriever] often reports 60 for HFR MP4 while the
     * capture-time MediaStore description still has the true rate (e.g. 120fps).
     */
    internal fun mergeFrameRateForDisplay(
        embeddedCaptureFps: Int?,
        retrieverFpsRaw: String?,
    ): String? {
        val embedded = embeddedCaptureFps?.toFloat()
        val retrieved = retrieverFpsRaw?.trim()?.toFloatOrNull()
        val chosen =
            when {
                // HFR MP4: frame-count÷duration often ~30–60 while MediaStore description has true capture tier.
                embedded != null && embedded > 0f && retrieved != null && retrieved > 0f &&
                    retrieved < embedded * 0.75f -> embedded
                embedded != null && embedded > 0f &&
                    (retrieved == null || embedded >= retrieved - 0.5f) -> embedded
                retrieved != null && retrieved > 0f -> retrieved
                embedded != null && embedded > 0f -> embedded
                else -> null
            }
        return chosen?.let { v ->
            if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)
        }
    }

    internal fun parseDescription(desc: String): DescriptionFields {

        val fps = DESCRIPTION_FPS.find(desc)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val parts = desc.split(" · ").map { it.trim() }

        val codec =

            parts.getOrNull(1)

                ?.takeIf { it.isNotEmpty() && !it.contains("fps", ignoreCase = true) }

        val audioPart = parts.firstOrNull { it.startsWith("AAC", ignoreCase = true) }

        val audioMatch = audioPart?.let { DESCRIPTION_AUDIO.find(it) }

        val sampleKhz = audioMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

        val aacKbps = audioMatch?.groupValues?.getOrNull(2)?.toIntOrNull()

        val channels = audioMatch?.groupValues?.getOrNull(3)?.toIntOrNull()

        val hiFi = desc.contains("Hi-Fi", ignoreCase = true)

        val windNs = desc.contains("wind NS", ignoreCase = true)

        return DescriptionFields(

            captureFps = fps,

            codecLabel = codec,

            audioSampleRateHz = sampleKhz?.let { it * 1000 },

            audioAacBitrateBps = aacKbps?.let { it * 1000 },

            audioChannelCount = channels,

            audioHiFi = hiFi,

            audioWindNoiseReduction = windNs,

        )

    }



    private fun buildDescription(info: CaptureInfo): String =

        buildString {

            append("Point & Shoot · ${info.codecLabel} · ${info.captureFps}fps")

            if (info.hasAudio) {

                val rateKhz = (info.audioSampleRateHz ?: 48_000) / 1000

                val aacK = (info.audioAacBitrateBps ?: 128_000) / 1000

                val ch = info.audioChannelCount ?: 2

                append(" · AAC ${rateKhz}kHz ${aacK}k ${ch}ch")

                if (info.audioHiFi) append(" Hi-Fi")

                if (info.audioWindNoiseReduction) append(" wind NS")

            } else {

                append(" · no audio")

            }

        }

}

