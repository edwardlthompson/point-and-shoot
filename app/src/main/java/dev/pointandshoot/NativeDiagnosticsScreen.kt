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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Diagnostic screen surfacing the runtime state of [NativeEncoders] and the
 * resulting [EncoderRoute] decisions per [ImagingProfile].
 *
 * Reachable from the probe home ("Native diagnostics" button) or via
 * `--es pns_screen native` for ADB-driven validation runs. The screen is
 * pure-data: it does not call into the camera / GLES stack and is safe to
 * launch without `CAMERA` permission.
 *
 * What you should see in Phase 0 (no NDK pipeline yet):
 *
 * - "Native library: NOT LOADED" with the underlying loadLibrary error
 *   (typically "Couldn't find 'libpns_native.so'" or similar).
 * - Per-profile rows that show the AVIF / JXL container as "DOWNGRADED to
 *   JPEG" alongside the canonical user-facing message.
 *
 * Once the NDK pipeline lands behind `pns.nativeEncoders=true` and
 * `libpns_native.so` ships in the APK, the same screen reports the loaded
 * version and stops downgrading.
 */
@Composable
fun NativeDiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = NativeEncoders.isAvailable
    val loadError = NativeEncoders.lastLoadError
    val version = NativeEncoders.version()
    val profiles = listOf<ImagingProfile>(ImagingProfile.StandardPro, ImagingProfile.UltraMax)

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
                text = "Native diagnostics",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Text(
            text = "BUILD_PLAN \u00a74 / NDK_PLAN.md status. Phase 0 ships the Kotlin facade " +
                "(NativeEncoders + EncoderRoute) so the capture engine can degrade gracefully " +
                "to JPEG until libavif / libjxl land via the externalNativeBuild switch.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Native library",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        Text(
            text = if (available) "Status: LOADED" else "Status: NOT LOADED",
            style = MaterialTheme.typography.bodyLarge,
            color = if (available) PnsColors.PhotoOrange else PnsColors.RecordRed,
        )
        Text(
            text = "Version: ${if (available) version else "${NativeEncoders.VERSION_UNAVAILABLE} (unavailable)"}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        if (loadError != null) {
            Text(
                text = "loadLibrary error: $loadError",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Per-profile encoder route",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )

        for (profile in profiles) {
            val decision = EncoderRoute.decide(profile, nativeAvailable = available)
            ProfileRouteRow(decision)
        }

        if (!available) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "How this clears: install the NDK + CMake (Android Studio SDK Manager " +
                    "OR scripts/pns_install_ndk.ps1), turn on the externalNativeBuild block in " +
                    "app/build.gradle.kts (Phase 1 PR), and the .so ships in the APK. The " +
                    "facade auto-detects the load and the downgrade banner clears.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }

        Text(
            text = "Allowed downgrade message: \"${EncoderRoute.DOWNGRADE_MESSAGE}\"",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun ProfileRouteRow(decision: EncoderRoute.Decision) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = decision.profile.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        Text(
            text = "RAW: ${decision.rawWritten.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        if (decision.tonalWritten != null) {
            Text(
                text = "Tonal: ${decision.tonalWritten.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = PnsColors.PhotoOrange,
            )
        } else {
            Text(
                text = "Tonal: DOWNGRADED to JPEG",
                style = MaterialTheme.typography.bodyMedium,
                color = PnsColors.RecordRed,
            )
            decision.downgradeReason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
    }
}
