package dev.pointandshoot

import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Sprint **IP.1** — deep links and launch-intent normalization for external apps / automation.
 *
 * Scheme **`pointandshoot://`** hosts: `preview`, `camera`, `video`, `gallery`, `share`.
 */
object PlatformIntegration {
    const val TAG = "PNS.Platform"

    const val DEEP_LINK_SCHEME = "pointandshoot"

    data class DeepLinkRoute(
        val screen: String,
        val primaryPhoto: Boolean? = null,
        val openGallery: Boolean = false,
        val shareProbe: Boolean = false,
        val composedStill: Boolean = false,
    )

    fun parseDeepLink(uri: Uri?): DeepLinkRoute? =
        uri?.toString()?.let { parseDeepLinkString(it) }

    /** JVM-testable parser (no framework [Uri] required). */
    fun parseDeepLinkString(uriString: String): DeepLinkRoute? {
        val uri =
            runCatching { java.net.URI(uriString.trim()) }.getOrNull() ?: return null
        if (uri.scheme != DEEP_LINK_SCHEME) return null
        val host = uri.host?.lowercase() ?: return null
        return when (host) {
            "preview", "camera" ->
                DeepLinkRoute(
                    screen = PNS_SCREEN_PREVIEW,
                    primaryPhoto = true,
                    composedStill = uri.query?.contains("shoot=1") == true,
                )
            "video" ->
                DeepLinkRoute(screen = PNS_SCREEN_PREVIEW, primaryPhoto = false)
            "gallery" ->
                DeepLinkRoute(screen = PNS_SCREEN_PREVIEW, openGallery = true)
            "share" ->
                DeepLinkRoute(
                    screen = PNS_SCREEN_PREVIEW,
                    shareProbe = true,
                )
            else -> null
        }
    }

    /**
     * Maps [Intent.data] deep links into [EXTRA_PNS_*] extras (idempotent for cold start).
     *
     * @return true when a deep link was applied.
     */
    fun applyDeepLinkToIntent(intent: Intent): Boolean {
        val route = parseDeepLink(intent.data) ?: return false
        intent.putExtra(EXTRA_PNS_SCREEN, route.screen)
        route.primaryPhoto?.let { intent.putExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO, it) }
        if (route.openGallery) intent.putExtra(EXTRA_PNS_PREVIEW_OPEN_GALLERY, true)
        if (route.shareProbe) intent.putExtra(EXTRA_PNS_PREVIEW_PLATFORM_SHARE_PROBE, true)
        if (route.composedStill) intent.putExtra(EXTRA_PNS_PREVIEW_COMPOSED_STILL, true)
        intent.action = Intent.ACTION_VIEW
        Log.i(TAG, "deepLink host=${intent.data?.host} screen=${route.screen}")
        Log.i(TAG, "deepLink adb host=${intent.data?.host}")
        return true
    }

    fun logWidgetProbe(context: android.content.Context) {
        val ok = PnsCameraWidgetProvider.isRegistered(context)
        PnsAdbLog.i(context, "platform widgetRegistered=$ok")
        Log.i(TAG, "widgetRegistered=$ok")
    }
}
