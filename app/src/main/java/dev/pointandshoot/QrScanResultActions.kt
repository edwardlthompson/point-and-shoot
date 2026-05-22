package dev.pointandshoot

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope

/**
 * What to offer after a QR / barcode decode.
 *
 * **Industry norm (iOS Camera, Google Lens, most banking apps):** show the payload and require an
 * explicit user action before opening external apps — especially for `http`/`https` — to reduce
 * quishing and drive-by navigation. This app does **not** auto-launch URLs on decode.
 */
sealed class QrScanAction {
    abstract val snackbarMessage: String

    /** User can tap **Open** (snackbar or finder buttons) → [Intent.ACTION_VIEW]. */
    data class ViewUri(
        val uri: String,
        val actionLabel: String,
        override val snackbarMessage: String,
    ) : QrScanAction()

    /** Non-actionable or sensitive payload — **Copy** only. */
    data class CopyOnly(
        override val snackbarMessage: String,
    ) : QrScanAction()
}

object QrScanResultActions {
    private const val TAG = QrCodeAnalyzer.TAG

    fun resolve(
        rawText: String,
        barcodeFormat: String? = null,
    ): QrScanAction {
        val text = rawText.trim()
        val formatLabel = barcodeFormat?.replace('_', ' ')?.let { "$it: " } ?: ""
        if (text.isEmpty()) {
            return QrScanAction.CopyOnly(snackbarMessage = "Empty scan")
        }

        val asUri = runCatching { Uri.parse(text) }.getOrNull()
        val scheme = asUri?.scheme?.lowercase()

        return when {
            text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) ||
                scheme in listOf("http", "https") ->
                QrScanAction.ViewUri(
                    uri = text,
                    actionLabel = "Open link",
                    snackbarMessage = "Link detected — tap Open",
                )
            text.startsWith("tel:", ignoreCase = true) || scheme == "tel" ->
                QrScanAction.ViewUri(
                    uri = text,
                    actionLabel = "Call",
                    snackbarMessage = "Phone number",
                )
            text.startsWith("mailto:", ignoreCase = true) || scheme == "mailto" ->
                QrScanAction.ViewUri(
                    uri = text,
                    actionLabel = "Email",
                    snackbarMessage = "Email address",
                )
            text.startsWith("sms:", ignoreCase = true) ||
                text.startsWith("smsto:", ignoreCase = true) ||
                text.startsWith("mmsto:", ignoreCase = true) ||
                scheme in listOf("sms", "smsto", "mmsto") ->
                QrScanAction.ViewUri(
                    uri =
                        if (scheme == "sms" && asUri?.schemeSpecificPart != null) {
                            "smsto:${asUri.schemeSpecificPart}"
                        } else {
                            text
                        },
                    actionLabel = "Message",
                    snackbarMessage = "SMS",
                )
            text.startsWith("geo:", ignoreCase = true) || scheme == "geo" ->
                QrScanAction.ViewUri(
                    uri = text,
                    actionLabel = "Open map",
                    snackbarMessage = "Location",
                )
            text.startsWith("WIFI:", ignoreCase = true) ->
                QrScanAction.CopyOnly(
                    snackbarMessage = "Wi‑Fi config — copy, then join in Settings",
                )
            text.startsWith("BEGIN:VCARD", ignoreCase = true) ->
                QrScanAction.CopyOnly(
                    snackbarMessage = "Contact card — use Copy",
                )
            else ->
                QrScanAction.CopyOnly(
                    snackbarMessage = "${formatLabel}${text.take(120)}",
                )
        }
    }

    fun copyToClipboard(context: Context, rawText: String) {
        val mgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        mgr.setPrimaryClip(ClipData.newPlainText("QR scan", rawText))
    }

    /**
     * Snackbar + logging after decode. Pair with finder **Open** / **Copy** buttons when possible.
     */
    fun present(
        scope: CoroutineScope,
        snackbarHost: SnackbarHostState?,
        appContext: Context,
        rawText: String,
        barcodeFormat: String? = null,
    ) {
        val action = resolve(rawText, barcodeFormat)
        Log.i(
            TAG,
            "decode presented type=${action::class.simpleName} len=${rawText.length} autoOpen=false",
        )
        when (action) {
            is QrScanAction.ViewUri ->
                scope.pnsShowSnackbar(
                    snackbarHost,
                    "${action.snackbarMessage} — tap ${action.actionLabel} (not the message text)",
                    longDuration = true,
                    primaryActionLabel = action.actionLabel,
                    onPrimaryAction = {
                        val ok = launchViewUri(appContext, action.uri)
                        Log.i(TAG, "open userInitiated=true uri=${action.uri.take(120)} ok=$ok")
                        if (!ok) {
                            scope.pnsShowSnackbar(
                                snackbarHost,
                                "No app to open this",
                                clipboardDetail = rawText,
                                clipboardAppContext = appContext,
                            )
                        }
                    },
                )
            is QrScanAction.CopyOnly ->
                scope.pnsShowSnackbar(
                    snackbarHost,
                    action.snackbarMessage,
                    longDuration = true,
                    clipboardDetail = rawText,
                    clipboardAppContext = appContext,
                )
        }
    }

    fun launchViewUri(context: Context, uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri.trim()))
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
