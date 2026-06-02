package dev.pointandshoot

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sprint **PO.1** — indexed MediaStore reads for in-app gallery (P&S DCIM tree only, capped row count).
 */
object PnsMediaStoreGallery {
    private const val TAG = "PNS.GalleryIndex"
    private const val PNS_DCIM_RELATIVE_PATH = "DCIM/Point & Shoot"
    const val DEFAULT_MAX_ITEMS = 500

    /**
     * Fast gallery index: no per-row EXIF/TIFF IO (metadata loads lazily on selection in UI).
     */
    suspend fun loadIndex(
        context: Context,
        maxItems: Int = DEFAULT_MAX_ITEMS,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val merged = mutableListOf<MediaItem>()
            queryCollection(
                context = context,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                maxItems = maxItems,
                isVideo = false,
                into = merged,
            )
            val imageCount = merged.size
            queryCollection(
                context = context,
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                maxItems = maxItems,
                isVideo = true,
                into = merged,
            )
            val videoCount = (merged.size - imageCount).coerceAtLeast(0)
            val sorted =
                merged
                    .sortedByDescending { it.date }
                    .take(maxItems)
            Log.i(
                TAG,
                "loadIndex count=${sorted.size} ms=${System.currentTimeMillis() - start} " +
                    "images=$imageCount videos=$videoCount pathFilter=$PNS_DCIM_RELATIVE_PATH",
            )
            sorted
        }

    internal fun dcimRelativePathLikeArg(): String = "$PNS_DCIM_RELATIVE_PATH%"

    internal fun pnsRelativePathSelection(): Pair<String, Array<String>>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf(dcimRelativePathLikeArg())
    }

    private fun queryCollection(
        context: Context,
        collection: Uri,
        maxItems: Int,
        isVideo: Boolean,
        into: MutableList<MediaItem>,
    ) {
        if (maxItems <= 0) return
        val projection =
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.Images.Media.DESCRIPTION,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
            )
        val mimeSelection =
            if (isVideo) {
                "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'video/%'"
            } else {
                "(${MediaStore.MediaColumns.MIME_TYPE} LIKE 'image/%' OR " +
                    "${MediaStore.MediaColumns.MIME_TYPE}='image/x-adobe-dng' OR " +
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.dng')"
            }
        val pathFilter = pnsRelativePathSelection()
        val selection =
            buildString {
                append(mimeSelection)
                append(" AND ${MediaStore.MediaColumns.SIZE} > 0")
                if (pathFilter != null) {
                    append(" AND ${pathFilter.first}")
                }
            }
        val selectionArgs =
            if (pathFilter != null) {
                pathFilter.second
            } else {
                null
            }

        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val cursor =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val args =
                    Bundle().apply {
                        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                        if (selectionArgs != null) {
                            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                        }
                        putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                        putInt(ContentResolver.QUERY_ARG_LIMIT, maxItems)
                    }
                context.contentResolver.query(collection, projection, args, null)
            } else {
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    "$sortOrder LIMIT $maxItems",
                )
            }
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val descCol = c.getColumnIndex(MediaStore.Images.Media.DESCRIPTION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            while (c.moveToNext() && into.size < maxItems) {
                val id = c.getLong(idCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                val displayName = c.getString(nameCol) ?: continue
                val mimeType = c.getString(mimeCol)
                val description = if (descCol >= 0) c.getString(descCol) else null
                val size = c.getLong(sizeCol)
                val date = c.getLong(dateCol)
                val width = c.getInt(widthCol).takeIf { it > 0 } ?: 1920
                val height = c.getInt(heightCol).takeIf { it > 0 } ?: 1080
                val isRaw =
                    displayName.lowercase().let { n ->
                        n.endsWith(".dng") || n.endsWith(".raw") || n.endsWith(".cr2") || n.endsWith(".nef")
                    }
                val colorSpace =
                    parseColorFromDescription(description)
                        ?: inferColorFromMime(displayName, mimeType, isRaw)
                into.add(
                    MediaItem(
                        uri = uri,
                        displayName = displayName,
                        mimeType = mimeType,
                        size = size,
                        date = date,
                        width = width,
                        height = height,
                        isVideo = isVideo,
                        isRaw = isRaw,
                        isHdr = false,
                        hasLocation = false,
                        cameraId = null,
                        lens = null,
                        focalLength = null,
                        aperture = null,
                        iso = null,
                        shutterSpeed = null,
                        whiteBalance = null,
                        frameRate = null,
                        bitRate = null,
                        duration = null,
                        codec = null,
                        colorSpace = colorSpace,
                    ),
                )
            }
        }
    }

    private fun parseColorFromDescription(description: String?): String? {
        val d = description ?: return null
        val m = Regex("""\bcolor=([^\n\r]+)$""").find(d) ?: return null
        return m.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun inferColorFromMime(displayName: String, mimeType: String?, isRaw: Boolean): String =
        when {
            isRaw -> "RAW"
            mimeType.equals("image/tiff", ignoreCase = true) ||
                displayName.lowercase().endsWith(".tif") ||
                displayName.lowercase().endsWith(".tiff") -> "Rec. 2020"
            mimeType.equals("image/jxl", ignoreCase = true) ||
                displayName.lowercase().endsWith(".jxl") -> "Rec. 2020"
            mimeType.equals("image/heic", ignoreCase = true) ||
                displayName.lowercase().endsWith(".heic") -> "Rec. 2020"
            mimeType.equals("image/avif", ignoreCase = true) ||
                displayName.lowercase().endsWith(".avif") -> "Display P3"
            else -> "Unknown"
        }
}
