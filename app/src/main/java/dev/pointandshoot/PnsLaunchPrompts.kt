package dev.pointandshoot

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val KIND_UNEVALUATED = ""
private const val KIND_NONE = "none"
private const val KIND_DONATE = "donate"
private const val KIND_UPDATE = "update"

/**
 * Optional donate-after-update note and GitHub update prompt. Never mixed.
 * Skipped for ADB / capture-intent automation so gates stay unblocked.
 */
@Suppress("FunctionNaming")
@Composable
fun PnsLaunchPromptsHost(enabled: Boolean) {
    if (!enabled) return
    val context = LocalContext.current
    val hideDialogs =
        PnsForegroundCapture.isRecording ||
            PnsForegroundCapture.settingsOpen ||
            PnsForegroundCapture.aboutOpen ||
            PnsForegroundCapture.intervalometerRunning ||
            PnsForegroundCapture.selfTimerRunning ||
            PnsForegroundCapture.holdBurstActive ||
            PnsForegroundCapture.stillQueueBusy ||
            PnsForegroundCapture.bracketBusy ||
            PnsForegroundCapture.nightScapeBusy ||
            PnsForegroundCapture.installDialogOpen ||
            PnsProductPrefs.airplaneSafe(context)
    var kind by rememberSaveable { mutableStateOf(KIND_UNEVALUATED) }
    var promptVersion by rememberSaveable { mutableStateOf("") }
    var promptUrl by rememberSaveable { mutableStateOf("") }
    var promptSha by rememberSaveable { mutableStateOf("") }
    var promptSize by rememberSaveable { mutableStateOf(0L) }
    var installRequest by remember { mutableStateOf<PnsApkInstaller.Request?>(null) }

    LaunchedEffect(Unit) {
        if (kind != KIND_UNEVALUATED) return@LaunchedEffect
        val prompt =
            withContext(Dispatchers.IO) {
                runCatching { PnsAppUpdates.evaluateOnLaunch(context.applicationContext) }
                    .getOrDefault(PnsProductUpdate.LaunchPrompt.None)
            }
        when (prompt) {
            is PnsProductUpdate.LaunchPrompt.Donate -> {
                kind = KIND_DONATE
                promptVersion = prompt.currentVersion
            }
            is PnsProductUpdate.LaunchPrompt.Update -> {
                kind = KIND_UPDATE
                promptVersion = prompt.version
                promptUrl = prompt.url
                promptSha = prompt.sha256Url.orEmpty()
                promptSize = prompt.sizeBytes
            }
            PnsProductUpdate.LaunchPrompt.None -> kind = KIND_NONE
        }
    }

    if (!hideDialogs) when (kind) {
        KIND_DONATE ->
            DonateNudgeDialog(
                onDonate = {
                    PnsAppUpdates.markDonateSeen(context.applicationContext, promptVersion)
                    val ok = openExternalUrl(context, PNS_VENMO_DONATION_URL)
                    if (!ok) {
                        Toast.makeText(context, "No browser found to open Venmo.", Toast.LENGTH_SHORT).show()
                    }
                    kind = KIND_NONE
                },
                onNotNow = {
                    PnsAppUpdates.markDonateSeen(context.applicationContext, promptVersion)
                    kind = KIND_NONE
                },
            )
        KIND_UPDATE ->
            UpdateAvailableDialog(
                version = promptVersion,
                notes = PnsProductUpdate.sanitizeReleaseNotes(PnsUpdatePrefs(context).lastReleaseNotes()),
                sha256Url = promptSha.takeIf { it.isNotBlank() },
                onInstall = {
                    installRequest =
                        PnsApkInstaller.Request(
                            url = promptUrl.ifBlank { PnsProductUpdate.RELEASES_PAGE },
                            sha256Url = promptSha.takeIf { it.isNotBlank() },
                            expectedVersion = promptVersion.takeIf { it.isNotBlank() },
                            sizeBytes = promptSize,
                        )
                    kind = KIND_NONE
                },
                onLater = {
                    PnsAppUpdates.markUpdateDismissed(context.applicationContext, promptVersion)
                    kind = KIND_NONE
                },
                onObtainium = {
                    val ok = openExternalUrl(context, PNS_OBTAINIUM_ADD_URL)
                    if (!ok) {
                        Toast.makeText(context, "Obtainium is not installed.", Toast.LENGTH_SHORT).show()
                    }
                },
            )
    }

    PnsUpdateInstallHost(request = installRequest, onFinished = { installRequest = null })
}

@Suppress("FunctionNaming")
@Composable
private fun DonateNudgeDialog(
    onDonate: () -> Unit,
    onNotNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = {
            Text("Development is still going", color = Color.White)
        },
        text = {
            Text(
                "You just got a new build. If this app helps you, you can support ongoing work " +
                    "on Venmo. This is optional and will not appear again until the next update.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
            )
        },
        confirmButton = {
            TextButton(onClick = onDonate) {
                Text("Donate via Venmo", color = PnsColors.PhotoOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text("Not now", color = Color.White.copy(alpha = 0.75f))
            }
        },
        containerColor = PnsColors.Charcoal,
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun UpdateAvailableDialog(
    version: String,
    notes: String = "",
    sha256Url: String? = null,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onObtainium: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Text("Update available", color = Color.White)
        },
        text = {
            val context = LocalContext.current
            var shaShort by remember(sha256Url) { mutableStateOf<String?>(null) }
            LaunchedEffect(sha256Url) {
                shaShort =
                    withContext(Dispatchers.IO) {
                        PnsApkInstaller.peekSha256Short(context.applicationContext, sha256Url)
                    }
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Point & Shoot $version is on GitHub. Install it when you are ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                )
                shaShort?.let { short ->
                    Text(
                        "SHA-256 $short…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
                if (notes.isNotBlank()) {
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
                if (onObtainium != null) {
                    TextButton(onClick = onObtainium) {
                        Text("Add in Obtainium", color = PnsColors.PhotoOrange)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall) {
                Text("Install", color = PnsColors.PhotoOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Later", color = Color.White.copy(alpha = 0.75f))
            }
        },
        containerColor = PnsColors.Charcoal,
    )
}
