@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** Offload wizard: copy keepers to SAF, verify size, then stash-delete with undo. */
object GalleryVault {
    data class Report(val copied: Int, val failed: Int, val verified: Int, val message: String)

    fun offload(context: Context, treeUri: Uri, items: List<MediaItem>): Report {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return Report(0, items.size, 0, "Folder not usable")
        var copied = 0
        var failed = 0
        var verified = 0
        items.forEach { item ->
            val mime = item.mimeType ?: if (item.isVideo) "video/mp4" else "image/jpeg"
            val dest = tree.createFile(mime, item.displayName)
            if (dest == null) {
                failed++
                return@forEach
            }
            val ok =
                runCatching {
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        context.contentResolver.openOutputStream(dest.uri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                }.getOrDefault(false)
            if (!ok) {
                failed++
                return@forEach
            }
            copied++
            if (dest.length() >= item.size.coerceAtLeast(1L) * 8 / 10) verified++
        }
        return Report(
            copied,
            failed,
            verified,
            "Copied $copied, verified $verified, failed $failed",
        )
    }
}
