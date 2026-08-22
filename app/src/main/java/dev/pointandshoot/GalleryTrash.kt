package dev.pointandshoot

import android.content.Context
import android.net.Uri
import java.io.File

/** 30-second undo: stash bytes, delete MediaStore rows, restore on undo. */
object GalleryTrash {
    const val UNDO_MS: Long = 30_000L
    private const val DIR = "gallery_trash"

    data class Stash(
        val files: List<File>,
        val names: List<String>,
        val expiresAt: Long,
    )

    fun stashAndDelete(context: Context, items: List<MediaItem>): Stash? {
        if (items.isEmpty()) return null
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val files = mutableListOf<File>()
        val names = mutableListOf<String>()
        items.forEach { item ->
            val dest = File(dir, item.displayName.ifBlank { "item-${item.uri.hashCode()}" })
            val copied =
                runCatching {
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    dest.isFile && dest.length() > 0L
                }.getOrDefault(false)
            if (copied) {
                files += dest
                names += item.displayName
            }
            runCatching { context.contentResolver.delete(item.uri, null, null) }
        }
        if (files.isEmpty()) return null
        return Stash(files, names, System.currentTimeMillis() + UNDO_MS)
    }

    fun restore(context: Context, stash: Stash): Int {
        if (System.currentTimeMillis() > stash.expiresAt) {
            prune(stash)
            return 0
        }
        var restored = 0
        stash.files.forEachIndexed { index, file ->
            if (!file.isFile) return@forEachIndexed
            val name = stash.names.getOrElse(index) { file.name }
            val kind =
                if (name.lowercase().endsWith(".dng")) {
                    CaptureStorage.CaptureKind.DngLossless
                } else {
                    CaptureStorage.CaptureKind.JpegSdr
                }
            val handle =
                runCatching {
                    CaptureStorage.openOutput(
                        context,
                        ImagingProfile.StandardPro,
                        kind,
                        useLocationBridge = false,
                        filenameSuffix = "restore",
                    )
                }.getOrNull()
            if (handle != null) {
                runCatching {
                    handle.output.use { out -> file.inputStream().use { it.copyTo(out) } }
                    handle.close()
                    restored++
                }.onFailure { handle.discard() }
            }
            file.delete()
        }
        return restored
    }

    fun prune(stash: Stash?) {
        stash?.files?.forEach { it.delete() }
    }
}
