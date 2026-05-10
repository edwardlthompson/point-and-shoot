package dev.pointandshoot

import android.content.ContentValues
import android.content.Context
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/**
 * Writes capture outputs to MediaStore per [STORAGE_STRATEGY.md](../../../../STORAGE_STRATEGY.md).
 *
 *   * Standard Pro -> `DCIM/Point & Shoot/`
 *   * Ultra-Max    -> `DCIM/Point & Shoot/Ultra-Max/`
 *   * Video        -> `DCIM/Point & Shoot/` (same DCIM tree as stills)
 *   * Diagnostics  -> app-private external files (no MediaStore insert)
 *
 * The writer uses the `IS_PENDING` flag (API 29+) so partial files are never
 * exposed to gallery apps; it clears the flag on flush.
 *
 * The class itself does not encode pixels - callers obtain an [OutputStream]
 * via [openOutput] and pipe their `Dng12Saver`, AVIF / JXL encoder, or any
 * other byte source into it.
 *
 * When [openOutput] / [openVideoOutput] is called with a null [location], the
 * pipeline uses [CaptureLocationBridge.snapshot] if the user enabled geotagging.
 *
 * No proprietary blobs - everything here is platform AOSP MediaStore.
 */
object CaptureStorage {

    private const val TAG = "PNS.Storage"
    private const val ROOT_RELATIVE_PATH = "DCIM/Point & Shoot"
    private const val VIDEO_ROOT_RELATIVE_PATH = "DCIM/Point & Shoot"

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
        /** Camera2 hardware JPEG / post-LUT re-encode (sRGB 8-bit). */
        JpegSdr("image/jpeg", "jpg"),
        Avif10BitHdr("image/avif", "avif"),
        JpegXl12Bit("image/jxl", "jxl"),
        Mp4("video/mp4", "mp4"),
    }

    /**
     * Reserve a MediaStore entry and return an [OutputStream] you can write
     * the encoded bytes to. The returned [Handle] tracks the URI so you can
     * [Handle.close] on success (clears IS_PENDING) or [Handle.discard] on
     * failure (deletes the partial entry).
     *
     * @param location optional GPS fix; defaults to [CaptureLocationBridge.snapshot].
     */
    fun openOutput(
        context: Context,
        profile: ImagingProfile,
        kind: CaptureKind,
        sequence: Long? = null,
        location: Location? = null,
        /**
         * When **true** (default), a null [location] is replaced with [CaptureLocationBridge.snapshot]
         * for [Handle.close] geotagging. When **false**, null stays null — callers that post-process
         * EXIF/GPS via [StillCaptureMetadata] after close must use **false** to avoid duplicate or
         * overwritten tags.
         */
        useLocationBridge: Boolean = true,
        /** Appended before the extension, e.g. bracket stop id + grouping (`bkt3of5-bkt-abc123`). */
        filenameSuffix: String? = null,
    ): Handle {
        require(!kind.mimeType.startsWith("video/")) { "Use openVideoOutput for video" }
        val resolver = context.applicationContext.contentResolver
        val seq = sequence ?: seqCounter.incrementAndGet()
        val displayName = filename(profile, kind, seq, filenameSuffix)
        val relativePath = relativePath(profile)
        val fix =
            when {
                location != null -> location
                useLocationBridge -> CaptureLocationBridge.snapshot()
                else -> null
            }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, kind.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        return Handle(
            context = context.applicationContext,
            uri = uri,
            output = out,
            kind = kind,
            location = fix,
            displayName = displayName,
        )
    }

    /**
     * Reserve a JSON sidecar next to still captures (same [relativePath] as DNG).
     * Requires API 29+ (scoped RELATIVE_PATH). Caller writes UTF-8 then [JsonSidecarHandle.close].
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun openCalibrationSidecarOutput(
        context: Context,
        profile: ImagingProfile,
        dngDisplayName: String,
    ): JsonSidecarHandle {
        val sidecarName = DngCalibrationSidecar.displayNameForSiblingDng(dngDisplayName)
        val resolver = context.applicationContext.contentResolver
        val relativePath = relativePath(profile)

        val values =
            ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, sidecarName)
                put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }

        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri =
            resolver.insert(collection, values)
                ?: error("MediaStore.Files.insert returned null for $sidecarName ($relativePath)")
        val out =
            resolver.openOutputStream(uri, "w")
                ?: error("MediaStore.openOutputStream returned null for $uri")

        Log.d(TAG, "openCalibrationSidecarOutput uri=$uri displayName=$sidecarName")
        return JsonSidecarHandle(
            context = context.applicationContext,
            uri = uri,
            output = out,
        )
    }

    /**
     * Reserve a UTF-8 LUT sidecar (`*.lutref.txt` or `*.cube.txt`) next to still captures.
     * Caller writes [LutSidecar.encode] output then [JsonSidecarHandle.close].
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun openLutSidecarOutput(
        context: Context,
        profile: ImagingProfile,
        siblingCaptureDisplayName: String,
        isBundled: Boolean,
    ): JsonSidecarHandle {
        val sidecarName = LutSidecar.siblingFilenameFor(siblingCaptureDisplayName, isBundled)
        val resolver = context.applicationContext.contentResolver
        val relativePath = relativePath(profile)

        val values =
            ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, sidecarName)
                put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }

        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri =
            resolver.insert(collection, values)
                ?: error("MediaStore.Files.insert returned null for $sidecarName ($relativePath)")
        val out =
            resolver.openOutputStream(uri, "w")
                ?: error("MediaStore.openOutputStream returned null for $uri")

        Log.d(TAG, "openLutSidecarOutput uri=$uri displayName=$sidecarName")
        return JsonSidecarHandle(
            context = context.applicationContext,
            uri = uri,
            output = out,
        )
    }

    /**
     * Same as [openOutput] for video files under `DCIM/Point & Shoot/`.
     * Pair with [MediaRecorder.applyCaptureGeotag] before [MediaRecorder.prepare] for in-file GPS.
     */
    fun openVideoOutput(
        context: Context,
        profile: ImagingProfile,
        sequence: Long? = null,
        location: Location? = null,
    ): Handle {
        val kind = CaptureKind.Mp4
        val resolver = context.applicationContext.contentResolver
        val seq = sequence ?: seqCounter.incrementAndGet()
        val displayName = filename(profile, kind, seq)
        val relativePath = videoRelativePath(profile)
        val fix = location ?: CaptureLocationBridge.snapshot()

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, kind.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

        val uri = resolver.insert(collection, values)
            ?: error("MediaStore video insert returned null for $displayName ($relativePath)")
        val out = resolver.openOutputStream(uri, "w")
            ?: error("MediaStore.openOutputStream returned null for $uri")

        Log.d(TAG, "openVideoOutput uri=$uri displayName=$displayName")
        return Handle(
            context = context.applicationContext,
            uri = uri,
            output = out,
            kind = kind,
            location = fix,
            displayName = displayName,
        )
    }

    /** Build the deterministic filename for this capture. */
    fun filename(
        profile: ImagingProfile,
        kind: CaptureKind,
        sequence: Long,
        suffix: String? = null,
    ): String {
        val ts = timestampFormatter.format(Instant.now())
        val seqStr = sequence.toString().padStart(4, '0')
        val safe =
            suffix
                ?.trim()
                ?.replace(Regex("[\\\\/:*?\"<>|]"), "-")
                ?.take(120)
        val extra = if (safe.isNullOrEmpty()) "" else "_$safe"
        return "pns_${ts}_${profile.id}_${seqStr}$extra.${kind.extension}"
    }

    private fun relativePath(profile: ImagingProfile): String {
        return when (profile) {
            ImagingProfile.UltraMax -> "$ROOT_RELATIVE_PATH/Ultra-Max"
            else -> ROOT_RELATIVE_PATH
        }
    }

    private fun videoRelativePath(profile: ImagingProfile): String {
        return when (profile) {
            ImagingProfile.UltraMax -> "$VIDEO_ROOT_RELATIVE_PATH/Ultra-Max"
            else -> VIDEO_ROOT_RELATIVE_PATH
        }
    }

    /**
     * Lifecycle wrapper for one in-flight MediaStore entry. Call [close] on
     * success (clears the IS_PENDING bit so gallery apps see the file) or
     * [discard] on failure (deletes the row).
     */
    /**
     * Pending MediaStore JSON row under the same folder convention as image stills.
     * Does not apply geotagging (sidecar is metadata only).
     */
    class JsonSidecarHandle internal constructor(
        private val context: Context,
        val uri: Uri,
        val output: OutputStream,
    ) : AutoCloseable {
        private var finalized = false

        override fun close() {
            if (finalized) return
            finalized = true
            runCatching { output.flush() }
            runCatching { output.close() }
            val values =
                ContentValues().apply {
                    put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                }
            runCatching { context.contentResolver.update(uri, values, null, null) }
                .onFailure { Log.w(TAG, "clear IS_PENDING failed for sidecar $uri", it) }
            Log.d(TAG, "close sidecar uri=$uri")
        }

        fun discard() {
            if (finalized) return
            finalized = true
            runCatching { output.close() }
            runCatching { context.contentResolver.delete(uri, null, null) }
                .onFailure { Log.w(TAG, "discard delete failed for sidecar $uri", it) }
            Log.w(TAG, "discard sidecar uri=$uri")
        }
    }

    class Handle internal constructor(
        private val context: Context,
        val uri: Uri,
        val output: OutputStream,
        val kind: CaptureKind,
        private val location: Location?,
        /** MediaStore [MediaStore.MediaColumns.DISPLAY_NAME] — used for sibling sidecars. */
        val displayName: String,
    ) : AutoCloseable {
        private var finalized = false

        override fun close() {
            if (finalized) return
            finalized = true
            runCatching { output.flush() }
            runCatching { output.close() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(
                        if (kind.mimeType.startsWith("video/")) MediaStore.Video.Media.IS_PENDING
                        else MediaStore.Images.Media.IS_PENDING,
                        0,
                    )
                }
                runCatching { context.contentResolver.update(uri, values, null, null) }
                    .onFailure { Log.w(TAG, "clear IS_PENDING failed for $uri", it) }
            }
            if (location != null) {
                if (kind.mimeType.startsWith("video/")) {
                    MediaGeotag.applyToVideoUri(context, uri, location)
                } else {
                    MediaGeotag.applyToImageUri(context, uri, location)
                }
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
