package dev.pointandshoot

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sprint **IP.2** — WebDAV upload for recent captures. FTP/SMB are not embedded (FOSS policy);
 * use [LanMediaTransferServer] or [CloudCaptureBackup] SAF trees instead.
 */
object NetworkStorageClient {
    const val TAG = "PNS.NetworkStorage"

    data class UploadResult(val ok: Boolean, val httpCode: Int, val message: String)

    fun uploadViaWebDav(
        context: Context,
        uri: Uri,
        displayName: String,
        baseUrl: String,
        user: String?,
        pass: String?,
    ): UploadResult {
        val base = baseUrl.trimEnd('/')
        val target = "$base/Point-and-Shoot/$displayName"
        return runCatching {
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return UploadResult(false, 0, "open failed")
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Content-Type", context.contentResolver.getType(uri) ?: "application/octet-stream")
                if (!user.isNullOrEmpty()) {
                    val cred = Base64.encodeToString("$user:${pass.orEmpty()}".toByteArray(), Base64.NO_WRAP)
                    setRequestProperty("Authorization", "Basic $cred")
                }
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            conn.disconnect()
            val ok = code in 200..299
            UploadResult(ok, code, if (ok) "uploaded" else "http $code")
        }.getOrElse { e ->
            UploadResult(false, 0, e.message ?: "error")
        }.also { r ->
            Log.i(TAG, "webdav put ok=${r.ok} code=${r.httpCode} ${r.message}")
            PnsAdbLog.i(context, "connectivity webdavUpload ok=${r.ok} code=${r.httpCode}")
        }
    }

    fun probeWebDavConfigured(context: Context): Boolean {
        val url = PnsConnectivity.webDavBaseUrl(context)
        val configured = !url.isNullOrEmpty()
        PnsAdbLog.i(context, "connectivity webdavConfigured=$configured")
        return configured
    }
}
