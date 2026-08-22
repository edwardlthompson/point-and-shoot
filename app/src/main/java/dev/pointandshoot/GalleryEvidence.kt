@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

/** SHA-256 chain beside a capture (journalists / inspectors). Never rewrites DNG. */
object GalleryEvidence {
    data class Receipt(
        val displayName: String,
        val sha256: String,
        val bytes: Long,
        val utcMs: Long,
        val geotag: String,
    ) {
        fun text(): String =
            buildString {
                appendLine("Point & Shoot evidence receipt")
                appendLine("file=$displayName")
                appendLine("sha256=$sha256")
                appendLine("bytes=$bytes")
                appendLine("utcMs=$utcMs")
                appendLine("geotag=$geotag")
            }
    }

    fun hashUri(context: Context, uri: Uri): String? {
        val md = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        } ?: return null
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun receipt(context: Context, item: MediaItem): Receipt? {
        val sha = hashUri(context, item.uri) ?: return null
        val geotag =
            when (PnsProductPrefs.geotagMode(context)) {
                PnsGeotagMode.Off -> "off"
                PnsGeotagMode.Coarse -> if (item.hasLocation) "coarse" else "none"
                PnsGeotagMode.Precise -> if (item.hasLocation) "precise" else "none"
            }
        return Receipt(item.displayName, sha, item.size, item.date * 1000L, geotag)
    }
}
