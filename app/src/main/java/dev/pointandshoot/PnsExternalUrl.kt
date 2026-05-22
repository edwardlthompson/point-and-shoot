package dev.pointandshoot

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Sprint **14.11** — locked donation link (BUILD_PLAN Milestone 14). */
const val PNS_VENMO_DONATION_URL: String =
    "https://venmo.com/code?user_id=1857304970395648420"

/**
 * Opens [url] in the user's default browser (external handler).
 *
 * @return true if an activity was started.
 */
fun openExternalUrl(context: Context, url: String): Boolean {
    val uri = Uri.parse(url.trim())
    val intent =
        Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    return try {
        context.startActivity(intent)
        Log.i("PNS.ChromeUx", "externalUrl=opened host=${uri.host}")
        true
    } catch (_: ActivityNotFoundException) {
        Log.w("PNS.ChromeUx", "externalUrl=no_handler url=$url")
        false
    }
}
