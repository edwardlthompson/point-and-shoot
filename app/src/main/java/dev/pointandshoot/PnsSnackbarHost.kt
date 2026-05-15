package dev.pointandshoot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App-wide snackbar anchor (see [MainActivity] [androidx.compose.material3.SnackbarHost]).
 * Screens use [pnsShowSnackbar] so messaging stays consistent when the host is present.
 */
val LocalPnsSnackbarHostState = compositionLocalOf<SnackbarHostState?> { null }

fun CoroutineScope.pnsShowSnackbar(
    host: SnackbarHostState?,
    message: String,
    longDuration: Boolean = true,
    /** When non-null with [clipboardAppContext], adds a **Copy** action (Milestone 10.15). */
    clipboardDetail: String? = null,
    clipboardAppContext: Context? = null,
    /**
     * When non-null, snackbar shows **Retry** instead of Copy. Material3 allows one action —
     * transient pipeline failures prefer Retry; use Copy for errors where retry is unlikely to help.
     */
    onRetry: (() -> Unit)? = null,
) {
    if (host == null) return
    val wantRetry = onRetry != null
    val wantCopy = !wantRetry && !clipboardDetail.isNullOrBlank() && clipboardAppContext != null
    launch {
        val result =
            host.showSnackbar(
                message = message,
                actionLabel =
                    when {
                        wantRetry -> "Retry"
                        wantCopy -> "Copy"
                        else -> null
                    },
                duration = if (longDuration) SnackbarDuration.Long else SnackbarDuration.Short,
                withDismissAction = wantCopy || wantRetry,
            )
        when {
            wantRetry && result == SnackbarResult.ActionPerformed -> onRetry?.invoke()
            wantCopy && result == SnackbarResult.ActionPerformed -> {
                val detail = clipboardDetail ?: return@launch
                val app = clipboardAppContext ?: return@launch
                val mgr = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                mgr.setPrimaryClip(ClipData.newPlainText("Point & Shoot error", detail))
            }
        }
    }
}
