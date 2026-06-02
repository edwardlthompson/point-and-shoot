package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Root Only settings drawer per **BUILD_PLAN** Milestone 7 Sprint 7.5 (root-only enhancements).
 *
 * Renders every shipped [RootCapability.Feature] with its purpose +
 * fallback. Rows are visibly disabled (greyed) until the active state
 * is [RootCapability.RootState.Granted]; the drawer always renders so
 * the user can see what they would gain by rooting.
 *
 * The "Grant Su" button performs an explicit
 * [RootCapabilityProbe.requestGrant] call ONLY when the user taps it -
 * Point & Shoot never silently shells out to `su` on launch (silent SU
 * prompts are the #1 complaint of root-aware apps).
 *
 * Reachable from the probe home or via `--es pns_screen rootsettings`.
 * Does NOT require `CAMERA` permission.
 */
@Composable
fun RootSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Cold-start: **`--ez pns_auto_root_diagnostics true`** with **`pns_screen=rootsettings`**.
     * After root state is known (not [RootCapability.RootState.Unknown]), runs [RootPrivilegedDiagnostics.runScan]
     * once (logs **`skipped`** when not [RootCapability.RootState.Granted], else read-only SU probes).
     */
    autoRunDiagnostics: Boolean = false,
) {
    val appCtx = LocalContext.current.applicationContext
    var state by remember { mutableStateOf(RootCapability.RootState.Unknown) }
    var pending by remember { mutableStateOf(false) }
    var tryVendorHighlightAe by remember(appCtx) {
        mutableStateOf(VendorHighlightAePrefs.isTryExtraModesEnabled(appCtx))
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val disk = RootCapabilityStore.loadOrUnknown(appCtx)
        val static = withContext(Dispatchers.IO) { RootCapabilityProbe.probeStatic() }
        state = when (disk) {
            RootCapability.RootState.Granted,
            RootCapability.RootState.Denied,
            -> disk
            else -> static
        }
    }

    LaunchedEffect(state) {
        if (state != RootCapability.RootState.Unknown) {
            RootCapabilityStore.save(appCtx, state)
        }
    }

    var autoDiagDone by remember { mutableStateOf(false) }
    LaunchedEffect(autoRunDiagnostics, state, autoDiagDone) {
        if (!autoRunDiagnostics || autoDiagDone) return@LaunchedEffect
        if (state == RootCapability.RootState.Unknown) return@LaunchedEffect
        autoDiagDone = true
        withContext(Dispatchers.IO) {
            RootPrivilegedDiagnostics.runScan(appCtx, state)
        }
    }

    val results = RootGate.evaluate(state)
    val safeModeActive = remember { mutableStateOf(ExperimentalSafeModeStore.isSafeModeActive(appCtx)) }

    val insets = rememberSystemInsetsDp()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(insets.asPaddingValues(extra = 16.dp))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text(
                text = "Root Only",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Text(
            text = "Point & Shoot is designed to work fully without root. " +
                "These options unlock additional performance, quality, and diagnostics " +
                "ONLY on devices that already have root (LineageOS user-debug, Magisk, " +
                "KernelSU). Every option below has a non-root fallback.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Status",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        Text(
            text = state.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = when (state) {
                RootCapability.RootState.Granted -> PnsColors.PhotoOrange
                RootCapability.RootState.NotAvailable, RootCapability.RootState.Denied -> PnsColors.RecordRed
                else -> Color.White.copy(alpha = 0.85f)
            },
        )
        if (safeModeActive.value) {
            Text(
                text = "Safe mode active: experimental unlock lanes are disabled.",
                style = MaterialTheme.typography.bodySmall,
                color = PnsColors.RecordRed,
            )
            OutlinedButton(
                onClick = {
                    ExperimentalSafeModeStore.clearSafeMode(appCtx)
                    safeModeActive.value = false
                },
            ) {
                Text("Clear safe mode")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = !pending && state.canRequestGrant,
                onClick = {
                    pending = true
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            RootCapabilityProbe.requestGrant()
                        }
                        state = outcome
                        pending = false
                    }
                },
            ) {
                Text(if (pending) "Requesting..." else "Grant Su")
            }
            OutlinedButton(
                enabled = !pending,
                onClick = {
                    scope.launch {
                        state = withContext(Dispatchers.IO) { RootCapabilityProbe.probeStatic() }
                    }
                },
            ) {
                Text("Re-probe")
            }
            OutlinedButton(
                enabled = !pending && state.grantsPrivileged,
                onClick = {
                    pending = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            RootPrivilegedDiagnostics.runScan(appCtx, state)
                        }
                        pending = false
                    }
                },
            ) {
                Text("Read-only SU checks")
            }
        }

        Text(
            text = "Tip: \"Grant Su\" runs `su -c id` exactly once and reads the result. " +
                "If your SU manager (Magisk / KernelSU / Superuser) shows a consent dialog, " +
                "approve it ONCE; you can revoke later.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Root-only enhancements",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )

        Text(
            text = "Blue callouts in quick settings mark modes that usually need root or vendor unlock; this list explains each feature.",
            style = MaterialTheme.typography.bodySmall,
            color = PnsColors.RootAccentBlue.copy(alpha = 0.85f),
        )

        for (result in results) {
            RootFeatureCard(result)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Experimental",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Vendor highlight AE modes",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (state.grantsPrivileged) Color.White else Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        text = "If the SDK omits CONTROL_AE_MODE_ON_HIGHLIGHT_WEIGHTED, try non-standard integers " +
                            "from CONTROL_AE_AVAILABLE_MODES on the H dial (multiple modes: pick highest). " +
                            if (state.grantsPrivileged) {
                                "Requires this drawer’s SU grant so we never fork su silently from preview."
                            } else {
                                "Requires Grant Su below."
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                Switch(
                    checked = tryVendorHighlightAe,
                    enabled = state.grantsPrivileged,
                    onCheckedChange = { v ->
                        VendorHighlightAePrefs.setTryExtraModesEnabled(appCtx, v)
                        tryVendorHighlightAe = v
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Technical: paths probed for su — ${RootCapabilityProbe.CANONICAL_SU_PATHS.joinToString()}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun RootFeatureCard(result: RootGate.GateResult) {
    val labelColor = if (result.enabled) Color.White else Color.White.copy(alpha = 0.55f)
    val accentColor = if (result.enabled) PnsColors.PhotoOrange else PnsColors.RootAccentBlue.copy(alpha = 0.75f)
    val statusText = if (result.enabled) "On" else "Off"
    val statusColor = if (result.enabled) PnsColors.PhotoOrange else PnsColors.RecordRed
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.descriptor.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = labelColor,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                )
            }
            Text(
                text = result.descriptor.purpose,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor.copy(alpha = 0.88f),
            )
            Text(
                text = "Without root: ${result.descriptor.fallback}",
                style = MaterialTheme.typography.bodySmall,
                color = accentColor,
            )
        }
    }
}
