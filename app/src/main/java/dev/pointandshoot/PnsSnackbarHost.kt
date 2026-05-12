package dev.pointandshoot

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
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
) {
    if (host == null) return
    launch {
        host.showSnackbar(
            message = message,
            duration = if (longDuration) SnackbarDuration.Long else SnackbarDuration.Short,
        )
    }
}
