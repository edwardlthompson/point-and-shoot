package dev.pointandshoot

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Sprint **IP.1** — known third-party photo/video package hints for resolver testing (no hard dependency).
 */
object ExternalApps {
    val PHOTO_VIEWER_PACKAGES =
        listOf(
            "com.adobe.lrmobile",
            "com.google.android.apps.photos",
            "org.lineageos.glimpse",
            "com.simplemobiletools.gallery.pro",
        )

    val VIDEO_PLAYER_PACKAGES =
        listOf(
            "org.videolan.vlc",
            "com.google.android.apps.photos",
            "org.lineageos.glimpse",
        )

    fun anyInstalled(context: Context, packages: List<String>): List<String> =
        packages.filter { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }

    fun openInPreferredPhotoApp(context: Context, uri: Uri): Boolean {
        val installed = anyInstalled(context, PHOTO_VIEWER_PACKAGES)
        for (pkg in installed) {
            val view =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "image/*")
                    setPackage(pkg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            if (view.resolveActivity(context.packageManager) != null) {
                context.startActivity(view)
                return true
            }
        }
        return openMediaWithSystemResolver(context, uri)
    }

    fun logInstalledViewers(context: Context) {
        val photos = anyInstalled(context, PHOTO_VIEWER_PACKAGES)
        val videos = anyInstalled(context, VIDEO_PLAYER_PACKAGES)
        PnsAdbLog.i(context, "platform externalApps photo=$photos video=$videos")
    }
}
