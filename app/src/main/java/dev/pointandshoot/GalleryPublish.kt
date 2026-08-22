@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

/** Immich / Nextcloud / WebDAV publish — one destination, progress, retry. */
object GalleryPublish {
    enum class Kind(val id: String, val label: String) {
        WebDav("webdav", "WebDAV / Nextcloud"),
        Immich("immich", "Immich"),
        ;

        companion object {
            fun fromId(raw: String?): Kind = entries.firstOrNull { it.id == raw } ?: WebDav
        }
    }

    data class Outcome(val ok: Boolean, val uploaded: Int, val message: String)

    fun publish(context: Context, items: List<MediaItem>, password: String): Outcome {
        val base = PnsProductPrefs.publishUrl(context).trim().trimEnd('/')
        if (base.isEmpty() || !base.startsWith("https://")) {
            return Outcome(false, 0, "Set an https:// publish URL in Settings.")
        }
        val kind = Kind.fromId(PnsProductPrefs.publishKind(context))
        var ok = 0
        var lastErr = ""
        items.forEach { item ->
            val result =
                runCatching { putOne(base, kind, item, context, password) }
                    .getOrElse { err ->
                        lastErr = err.message ?: "upload failed"
                        false
                    }
            if (result) ok++ else if (lastErr.isEmpty()) lastErr = "upload failed"
        }
        return if (ok == items.size && items.isNotEmpty()) {
            Outcome(true, ok, "Uploaded $ok files")
        } else {
            Outcome(false, ok, "Uploaded $ok / ${items.size}. $lastErr")
        }
    }

    private fun putOne(
        base: String,
        kind: Kind,
        item: MediaItem,
        context: Context,
        password: String,
    ): Boolean {
        val url =
            when (kind) {
                Kind.WebDav -> "$base/${item.displayName}"
                Kind.Immich -> "$base/api/assets"
            }
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        if (kind == Kind.WebDav) {
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", item.mimeType ?: "application/octet-stream")
            if (password.isNotBlank()) {
                val token =
                    android.util.Base64.encodeToString(
                        password.toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP,
                    )
                conn.setRequestProperty("Authorization", "Basic $token")
            }
        } else {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Accept", "application/json")
            if (password.isNotBlank()) conn.setRequestProperty("x-api-key", password)
        }
        context.contentResolver.openInputStream(item.uri)?.use { input ->
            conn.outputStream.use { input.copyTo(it) }
        } ?: return false
        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299
    }

    fun uriOf(item: MediaItem): Uri = item.uri
}
