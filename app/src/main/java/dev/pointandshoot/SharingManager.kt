package dev.pointandshoot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider

/**
 * Sprint **IP.1** — [FileProvider]-backed share grants for gallery / tray exports.
 */
object SharingManager {
    const val TAG = "PNS.Share"
    const val FILE_PROVIDER_AUTHORITY = "dev.pointandshoot.fileprovider"

    fun shareUris(
        context: Context,
        uris: List<Uri>,
        chooserTitle: String = "Share media",
    ): Boolean {
        if (uris.isEmpty()) return false
        val granted = uris.map { grantForShare(context, it) }
        val intent =
            if (granted.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeFor(context, granted.first())
                    putExtra(Intent.EXTRA_STREAM, granted.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(granted))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        val chooser =
            Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return runCatching {
            context.startActivity(chooser)
            Log.i(TAG, "share count=${granted.size}")
            PnsAdbLog.i(context, "platform shareStarted count=${granted.size} fileProvider=true")
            true
        }.getOrDefault(false)
    }

    fun shareSingle(context: Context, uri: Uri, title: String = "Share media"): Boolean =
        shareUris(context, listOf(uri), title)

    /**
     * MediaStore `content://` URIs pass through with read grant; `file://` copies into cache for provider.
     */
    fun grantForShare(context: Context, uri: Uri): Uri {
        if (uri.scheme == "content") return uri
        if (uri.scheme != "file") return uri
        val src = java.io.File(uri.path ?: return uri)
        if (!src.exists()) return uri
        val destDir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
        val dest = java.io.File(destDir, src.name)
        if (!dest.exists() || dest.length() != src.length()) {
            src.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, dest)
    }

    fun probeFileProvider(context: Context): Boolean {
        val ok =
            runCatching {
                val probe = java.io.File(context.cacheDir, "share/probe.txt")
                probe.parentFile?.mkdirs()
                probe.writeText("pns")
                val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, probe)
                uri.authority == FILE_PROVIDER_AUTHORITY
            }.getOrDefault(false)
        PnsAdbLog.i(context, "platform fileProviderOk=$ok authority=$FILE_PROVIDER_AUTHORITY")
        Log.i(TAG, "fileProviderOk=$ok")
        return ok
    }

    private fun mimeFor(context: Context, uri: Uri): String =
        context.contentResolver.getType(uri) ?: "*/*"
}
