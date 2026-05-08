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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Root Only settings drawer per BUILD_PLAN section 9 "Root-only enhancements".
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
) {
    var state by remember { mutableStateOf(RootCapability.RootState.Unknown) }
    var pending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val initial = withContext(Dispatchers.IO) { RootCapabilityProbe.probeStatic() }
        state = initial
    }

    val results = RootGate.evaluate(state)

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = !pending && state != RootCapability.RootState.Granted,
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

        for (result in results) {
            RootFeatureRow(result)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Detection paths checked: ${RootCapabilityProbe.CANONICAL_SU_PATHS.joinToString()}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun RootFeatureRow(result: RootGate.GateResult) {
    val labelColor = if (result.enabled) Color.White else Color.White.copy(alpha = 0.55f)
    val accentColor = if (result.enabled) PnsColors.PhotoOrange else Color.White.copy(alpha = 0.45f)
    val statusText = if (result.enabled) "ENABLED" else "DISABLED"
    val statusColor = if (result.enabled) PnsColors.PhotoOrange else PnsColors.RecordRed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = result.descriptor.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = labelColor,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
            )
        }
        Text(
            text = "Why: ${result.descriptor.purpose}",
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor.copy(alpha = 0.85f),
        )
        Text(
            text = "Fallback: ${result.descriptor.fallback}",
            style = MaterialTheme.typography.bodySmall,
            color = accentColor,
        )
        if (!result.enabled && result.disabledReason != null) {
            Text(
                text = result.disabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}
