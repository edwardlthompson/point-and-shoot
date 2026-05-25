package dev.pointandshoot

import android.content.Context
import android.net.ConnectivityManager as AndroidConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Sprint **IP.2** — connectivity prefs and capability probes (LAN transfer, WebDAV, social webhook).
 *
 * Cloud capture sync remains in [CloudCaptureBackup] (UX.3 / SAF). FTP/SMB are not bundled;
 * use LAN HTTP pull, WebDAV PUT, or a user-synced SAF folder.
 */
object PnsConnectivity {
    const val TAG = "PNS.Connectivity"
    const val PREFS_NAME = "pns_connectivity"

    private const val KEY_LAN_TRANSFER = "lan_transfer_enabled"
    private const val KEY_WEBDAV_URL = "webdav_base_url"
    private const val KEY_WEBDAV_USER = "webdav_user"
    private const val KEY_WEBDAV_PASS = "webdav_pass"
    private const val KEY_SOCIAL_WEBHOOK = "social_webhook_url"

    fun isLanTransferEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAN_TRANSFER, false)

    fun setLanTransferEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LAN_TRANSFER, enabled).commit()
        PnsAdbLog.i(context, "connectivity lanTransfer=$enabled")
        Log.i(TAG, "lanTransfer=$enabled")
    }

    fun webDavBaseUrl(context: Context): String? =
        prefs(context).getString(KEY_WEBDAV_URL, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun saveWebDav(context: Context, baseUrl: String, user: String?, pass: String?) {
        prefs(context).edit()
            .putString(KEY_WEBDAV_URL, baseUrl.trim())
            .putString(KEY_WEBDAV_USER, user?.trim().orEmpty())
            .putString(KEY_WEBDAV_PASS, pass.orEmpty())
            .commit()
    }

    fun socialWebhookUrl(context: Context): String? =
        prefs(context).getString(KEY_SOCIAL_WEBHOOK, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun saveSocialWebhook(context: Context, url: String?) {
        prefs(context).edit().putString(KEY_SOCIAL_WEBHOOK, url?.trim().orEmpty()).commit()
    }

    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? AndroidConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun logCapabilitySummary(context: Context) {
        PnsAdbLog.i(
            context,
            "connectivity summary lan=${isLanTransferEnabled(context)} " +
                "webdav=${webDavBaseUrl(context) != null} " +
                "social=${socialWebhookUrl(context) != null} " +
                "cloudBackup=${CloudCaptureBackup.isEnabled(context)} " +
                "ftpSupported=false smbSupported=false",
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
