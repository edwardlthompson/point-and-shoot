package dev.pointandshoot

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PROGRESS_MAX = 100

@Suppress("FunctionNaming")
@Composable
fun PnsUpdateInstallHost(
    request: PnsApkInstaller.Request?,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    var active by remember { mutableStateOf(request) }
    LaunchedEffect(request) {
        if (request != null) active = request
    }
    LaunchedEffect(active) {
        PnsForegroundCapture.installDialogOpen = active != null
    }
    DisposableEffect(Unit) {
        onDispose { PnsForegroundCapture.installDialogOpen = false }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        val prefs = PnsUpdatePrefs(app)
        if (prefs.clearPendingIfAlreadyInstalled(PnsAppInfo.versionName(app)) ||
            prefs.peekPendingInstall() == null
        ) {
            PnsApkInstaller.pruneCache(app)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && active == null) {
                    pruneCacheIfInstalled(app)
                    resumePendingIfAllowed(app) { pending -> active = pending }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val current = active ?: return
    InstallActiveDialogs(
        request = current,
        app = app,
        onClear = {
            active = null
            onFinished()
        },
    )
}

private fun resumePendingIfAllowed(
    app: Context,
    onPending: (PnsApkInstaller.Request) -> Unit,
) {
    if (!PnsApkInstaller.canRequestInstalls(app)) return
    val prefs = PnsUpdatePrefs(app)
    if (prefs.clearPendingIfAlreadyInstalled(PnsAppInfo.versionName(app))) {
        PnsApkInstaller.pruneCache(app)
        return
    }
    val pending = prefs.takePendingInstall() ?: return
    onPending(pending)
}

private fun pruneCacheIfInstalled(app: Context) {
    val prefs = PnsUpdatePrefs(app)
    val installed = PnsAppInfo.versionName(app)
    if (prefs.clearPendingIfAlreadyInstalled(installed) ||
        PnsProductUpdate.pendingVersionAlreadyInstalled(prefs.lastKnownGithubVersion(), installed)
    ) {
        PnsApkInstaller.pruneCache(app)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun InstallActiveDialogs(
    request: PnsApkInstaller.Request,
    app: Context,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var confirmMetered by remember(request) {
        mutableStateOf(PnsApkInstaller.isActiveNetworkMetered(app))
    }
    var progressPct by remember(request) { mutableIntStateOf(-1) }
    var progressLine by remember(request) { mutableStateOf("0%") }
    val cancelFlag = remember(request) { AtomicBoolean(false) }

    if (confirmMetered) {
        AlertDialog(
            onDismissRequest = onClear,
            title = { Text("Download on metered network?", color = Color.White) },
            text = {
                Text(
                    meteredDownloadMessage(request.sizeBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmMetered = false }) {
                    Text("Continue", color = PnsColors.PhotoOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = onClear) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.75f))
                }
            },
            containerColor = PnsColors.Charcoal,
        )
        return
    }

    LaunchedEffect(request) {
        progressPct = 0
        val outcome =
            withContext(Dispatchers.IO) {
                PnsApkInstaller.installFromUrl(
                    context = app,
                    url = request.url.ifBlank { PnsProductUpdate.RELEASES_PAGE },
                    sha256Url = request.sha256Url,
                    expectedVersion = request.expectedVersion,
                    expectedSizeBytes = request.sizeBytes,
                    shouldCancel = { cancelFlag.get() },
                    onProgress = { snap ->
                        app.mainExecutor.execute {
                            progressPct = snap.percent
                            progressLine = PnsApkInstaller.formatProgress(snap)
                        }
                    },
                )
            }
        handleInstallOutcome(context, app, request, outcome)
        onClear()
    }

    if (progressPct >= 0) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        progressLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                    LinearProgressIndicator(
                        progress = { progressPct.coerceIn(0, PROGRESS_MAX) / PROGRESS_MAX.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { cancelFlag.set(true) }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.75f))
                }
            },
            containerColor = PnsColors.Charcoal,
        )
    }
}

private fun handleInstallOutcome(
    context: Context,
    app: Context,
    request: PnsApkInstaller.Request,
    outcome: PnsApkInstaller.Outcome,
) {
    val prefs = PnsUpdatePrefs(app)
    when (outcome) {
        PnsApkInstaller.Outcome.Started -> {
            request.expectedVersion?.let { PnsAppUpdates.markUpdateDismissed(app, it) }
            prefs.clearPendingInstall()
        }
        PnsApkInstaller.Outcome.NeedPermission -> prefs.savePendingInstall(request)
        else -> prefs.clearPendingInstall()
    }
    val msg = toastFor(outcome)
    if (msg != null) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
}

internal fun meteredDownloadMessage(sizeBytes: Long): String {
    val size = PnsProductUpdate.formatMegabytes(sizeBytes)
    return if (size != null) {
        "This update is $size. You are on a metered connection."
    } else {
        "This update is a large APK (about 50 MB). You are on a metered connection."
    }
}

private fun toastFor(
    outcome: PnsApkInstaller.Outcome,
): String? {
    return when (outcome) {
        PnsApkInstaller.Outcome.Started -> null
        PnsApkInstaller.Outcome.NeedPermission ->
            "Allow installs from Point & Shoot. We'll continue when you return."
        PnsApkInstaller.Outcome.WrongPackage ->
            "The download was not a Point & Shoot APK. Install cancelled."
        PnsApkInstaller.Outcome.WrongVersion ->
            "The APK is not a newer Point & Shoot build. Install cancelled."
        PnsApkInstaller.Outcome.WrongSigner ->
            "That APK is signed differently and will not upgrade this install."
        PnsApkInstaller.Outcome.HashMismatch ->
            "The APK did not match the published SHA-256. Install cancelled."
        PnsApkInstaller.Outcome.Cancelled -> "Download cancelled."
        PnsApkInstaller.Outcome.NoSpace ->
            "Not enough storage to download the update."
        PnsApkInstaller.Outcome.Blocked ->
            "The download was blocked. Install cancelled."
        PnsApkInstaller.Outcome.Network ->
            "Couldn't download the update. Check the connection and try again."
        PnsApkInstaller.Outcome.Busy ->
            "An install is already running."
        PnsApkInstaller.Outcome.Failed ->
            "Couldn't start the installer."
    }
}
