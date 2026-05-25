package dev.pointandshoot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat

/**
 * Sprint **IP.1** — Android Share ingress: receive images/videos from other apps and open P&S gallery.
 */
class ShareReceiveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action
        val uris = extractSharedUris(intent)
        Log.i(PlatformIntegration.TAG, "shareReceive action=$action count=${uris.size}")
        PnsAdbLog.i(applicationContext, "platform shareTarget received=${uris.size}")
        val launch =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_PNS_SCREEN, PNS_SCREEN_PREVIEW)
                putExtra(EXTRA_PNS_PREVIEW_OPEN_GALLERY, true)
                putExtra(EXTRA_PNS_SHARE_INGRESS_COUNT, uris.size)
            }
        startActivity(launch)
        finish()
    }

    private fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val one =
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                return if (one != null) listOf(one) else emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val many =
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                return many.orEmpty()
            }
        }
        return emptyList()
    }
}

/** Logged on preview cold start after share ingress. */
const val EXTRA_PNS_SHARE_INGRESS_COUNT = "pns_share_ingress_count"
