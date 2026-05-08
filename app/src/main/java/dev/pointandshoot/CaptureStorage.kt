package dev.pointandshoot

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/**
 * Writes capture outputs to MediaStore per [STORAGE_STRATEGY.md](../../../../STORAGE_STRATEGY.md).
 *
 *   * Standard Pro -> `Pictures/Point & Shoot/`
 *   * Ultra-Max    -> `Pictures/Point & Shoot/Ultra-Max/`
 *   * Diagnostics  -> app-private external files (no MediaStore insert)
 *
 * The writer uses the `IS_PENDING` flag (API 29+) so partial files are never
 * exposed to gallery apps; it clears the flag on flush.
 *
 * The class itself does not encode pixels - callers obtain an [OutputStream]
 * via [openOutput] and pipe their `Dng12Saver`, AVIF / JXL encoder, or any
 * other byte source into it.
 *
 * No proprietary blobs - everything here is platform AOSP MediaStore.
 */
object CaptureStorage {

    private const val TAG = "PNS.Storage"
    private const val ROOT_RELATIVE_PATH = "Pictures/Point & Shoot"

    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** Monotonic per-process counter so back-to-back captures get unique sequences. */
    private val seqCounter = AtomicLong(0)

    enum class CaptureKind(
        val mimeType: String,
        val extension: String,
    ) {
        DngLossless("image/x-adobe-dng", "dng"),
        DngRaw12("image/x-adobe-dng", "dng"),
        Avif10BitHdr("image/avif", "avif"),
        JpegXl12Bit("image/jxl", "jxl"),
    }

    /**
     * Reserve a MediaStore entry and return an [OutputStream] you can write
     * the encoded bytes to. The returned [Handle] tracks the URI so you can
     * [Handle.close] on success (clears IS_PENDING) or [Handle.discard] on
     * failure (deletes the partial entry).
     *
     * @param context  any Context (uses applicationContext internally).
     * @param profile  imaging profile - drives subfolder + filename suffix.
     * @param kind     content kind - drives MIME type + extension.
     * @param sequence optional explicit sequence number; defaults to a monotonic per-process counter.
     */
    fun openOutput(
        context: Context,
        profile: ImagingProfile,
        kind: CaptureKind,
        sequence: Long? = null,
    ): Handle {
        val resolver = context.applicationContext.contentResolver
        val seq = sequence ?: seqCounter.incrementAndGet()
        val displayName = filename(profile, kind, seq)
        val relativePath = relativePath(profile)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, kind.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values)
            ?: error("MediaStore.insert returned null for $displayName ($relativePath)")
        val out = resolver.openOutputStream(uri, "w")
            ?: error("MediaStore.openOutputStream returned null for $uri")

        Log.d(TAG, "openOutput uri=$uri displayName=$displayName")
        return Handle(context = context.applicationContext, uri = uri, output = out, kind = kind)
    }

    /** Build the deterministic filename for this capture. */
    fun filename(profile: ImagingProfile, kind: CaptureKind, sequence: Long): String {
        val ts = timestampFormatter.format(Instant.now())
        val seqStr = sequence.toString().padStart(4, '0')
        return "pns_${ts}_${profile.id}_${seqStr}.${kind.extension}"
    }

    private fun relativePath(profile: ImagingProfile): String {
        return when (profile) {
            ImagingProfile.UltraMax -> "$ROOT_RELATIVE_PATH/Ultra-Max"
            else -> ROOT_RELATIVE_PATH
        }
    }

    /**
     * Lifecycle wrapper for one in-flight MediaStore entry. Call [close] on
     * success (clears the IS_PENDING bit so gallery apps see the file) or
     * [discard] on failure (deletes the row).
     */
    class Handle internal constructor(
        private val context: Context,
        val uri: Uri,
        val output: OutputStream,
        val kind: CaptureKind,
    ) : AutoCloseable {
        private var finalized = false

        override fun close() {
            if (finalized) return
            finalized = true
            runCatching { output.flush() }
            runCatching { output.close() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                runCatching { context.contentResolver.update(uri, values, null, null) }
                    .onFailure { Log.w(TAG, "clear IS_PENDING failed for $uri", it) }
            }
            Log.d(TAG, "close uri=$uri kind=$kind")
        }

        fun discard() {
            if (finalized) return
            finalized = true
            runCatching { output.close() }
            runCatching { context.contentResolver.delete(uri, null, null) }
                .onFailure { Log.w(TAG, "discard delete failed for $uri", it) }
            Log.w(TAG, "discard uri=$uri kind=$kind")
        }
    }
}
