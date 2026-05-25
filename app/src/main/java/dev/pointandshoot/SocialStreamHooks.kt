package dev.pointandshoot

import android.content.Context
import android.net.Uri
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sprint **IP.2** — optional HTTPS webhook when the user configures a URL (no bundled social SDK).
 */
object SocialStreamHooks {
    const val TAG = "PNS.SocialStream"

    fun postCaptureEvent(
        context: Context,
        uri: Uri,
        mime: String?,
        displayName: String,
    ): Boolean {
        val hook = PnsConnectivity.socialWebhookUrl(context) ?: run {
            PnsAdbLog.i(context, "connectivity socialStream configured=false skipped=true")
            return true
        }
        val body =
            """{"event":"capture","name":"${displayName.replace("\"", "")}","mime":"${mime ?: ""}","uri":"$uri"}"""
        return runCatching {
            val conn = (URL(hook).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            val ok = code in 200..299
            PnsAdbLog.i(context, "connectivity socialStream posted=$ok code=$code")
            Log.i(TAG, "webhook code=$code")
            ok
        }.getOrElse { e ->
            PnsAdbLog.i(context, "connectivity socialStream posted=false err=${e.message}")
            false
        }
    }
}
