@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** Copy today's P&S roll into a user-picked folder tree. */
object GalleryFolderExport {
    fun copyToday(context: Context, treeUri: Uri, items: List<MediaItem>, nowSec: Long): Int {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        val dayStart = nowSec - (nowSec % 86_400L)
        val today = items.filter { it.date >= dayStart }
        var copied = 0
        today.forEach { item ->
            val mime = item.mimeType ?: if (item.isVideo) "video/mp4" else "image/jpeg"
            val dest = tree.createFile(mime, item.displayName) ?: return@forEach
            val ok =
                runCatching {
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        context.contentResolver.openOutputStream(dest.uri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                }.getOrDefault(false)
            if (ok) copied++
        }
        return copied
    }
}
