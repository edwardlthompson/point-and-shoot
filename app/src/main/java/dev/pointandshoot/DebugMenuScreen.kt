package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class DebugEntry(
    val title: String,
    val subtitle: String,
    val requiresCamera: Boolean,
    val onClick: () -> Unit,
)

private data class DebugSection(
    val title: String,
    val description: String,
    val entries: List<DebugEntry>,
)

/**
 * Developer-facing diagnostics hub. Copy is written for humans; technical JSON /
 * matrix tools stay one tap away but are framed with plain-language context.
 */
@Composable
fun DebugMenuScreen(
    padding: PaddingValues,
    hasCameraPermission: Boolean,
    reportMdReady: Boolean,
    cameraSummaries: List<String>,
    /** Live [CapabilityGate] lines for the wide camera (same roster as preview baseline). */
    capabilityGateLines: List<String> = emptyList(),
    onBackToCamera: () -> Unit,
    onShowMapping: () -> Unit,
    onShowPreviewEngine: () -> Unit,
    onShowEncoderProbe: () -> Unit,
    onShowLegacyCamera1: () -> Unit,
    onShowDeepCaps: () -> Unit,
    onShowSessionMatrix: () -> Unit,
    onShowHdrDcgRuntime: () -> Unit,
    onShowCaptureLatency: () -> Unit,
    onShowRawHdrExcl: () -> Unit,
    onShowBurstProbe: () -> Unit,
    onShowLogicalPhysical: () -> Unit,
    onShowExhaustive: () -> Unit,
    onShowAbout: () -> Unit,
    onShowProHud: () -> Unit,
    onShowHudSettings: () -> Unit,
    onShowCalibrate: () -> Unit,
    onShowLutImport: () -> Unit,
    onShowGlPreview: () -> Unit,
    onShowNativeDiagnostics: () -> Unit,
    onShowRootSettings: () -> Unit,
    onDumpDiagnostics: () -> Unit,
    onRequestPermission: () -> Unit,
    /** Clears welcome onboarding and shows the step-by-step permission flow again. */
    onResetPermissionWelcome: () -> Unit,
    onExport: () -> Unit,
) {
    val sections =
        listOf(
            DebugSection(
                title = "Live camera & preview",
                description = "Interactive previews and mapping helpers tied to the active sensor.",
                entries =
                    listOf(
                        DebugEntry(
                            "Live preview (engine)",
                            "Camera2 preview path with HUD overlays — same view as the main app, without chrome hiding.",
                            true,
                            onShowPreviewEngine,
                        ),
                        DebugEntry(
                            "Dodge lens mapping",
                            "Logical / physical camera routing for multi-lens devices.",
                            true,
                            onShowMapping,
                        ),
                        DebugEntry(
                            "Legacy Camera1 probe",
                            "Compare deprecated API behavior when debugging OEM quirks.",
                            true,
                            onShowLegacyCamera1,
                        ),
                    ),
            ),
            DebugSection(
                title = "Capability matrices",
                description = "Structured dumps you can share with maintainers. Outputs are JSON or logs.",
                entries =
                    listOf(
                        DebugEntry(
                            "Deep capabilities",
                            "Stream maps, formats, and static metadata in one JSON blob.",
                            true,
                            onShowDeepCaps,
                        ),
                        DebugEntry(
                            "Session configuration matrix",
                            "What Camera2 sessions can be built for each template.",
                            true,
                            onShowSessionMatrix,
                        ),
                        DebugEntry(
                            "HDR / dynamic range runtime",
                            "HDR and DR-related session probes.",
                            true,
                            onShowHdrDcgRuntime,
                        ),
                        DebugEntry(
                            "Logical vs physical",
                            "How logical cameras fan out to physical IDs.",
                            true,
                            onShowLogicalPhysical,
                        ),
                        DebugEntry(
                            "Exhaustive encoder / media matrix",
                            "Long-running sweep across codecs and HFR combinations.",
                            true,
                            onShowExhaustive,
                        ),
                    ),
            ),
            DebugSection(
                title = "Performance & reliability",
                description = "Timing, burst, and encoder-focused probes.",
                entries =
                    listOf(
                        DebugEntry(
                            "HFR encoder probe",
                            "Tests high-frame-rate encoding paths per MIME type.",
                            true,
                            onShowEncoderProbe,
                        ),
                        DebugEntry(
                            "Capture latency",
                            "End-to-end still capture timing.",
                            true,
                            onShowCaptureLatency,
                        ),
                        DebugEntry(
                            "RAW vs HDR exclusivity",
                            "Conflicting stream combinations the HAL rejects.",
                            true,
                            onShowRawHdrExcl,
                        ),
                        DebugEntry(
                            "Burst probe",
                            "Rapid-fire capture stress path.",
                            true,
                            onShowBurstProbe,
                        ),
                    ),
            ),
            DebugSection(
                title = "Color, LUT, and calibration",
                description = "Creative pipeline tooling — safe to ignore for capture debugging.",
                entries =
                    listOf(
                        DebugEntry(
                            "Calibrate",
                            "Reference target workflows for color.",
                            false,
                            onShowCalibrate,
                        ),
                        DebugEntry(
                            "Import LUT",
                            "Bring your own creative transform.",
                            false,
                            onShowLutImport,
                        ),
                        DebugEntry(
                            "Live GL LUT preview",
                            "GPU preview path for LUT grading.",
                            false,
                            onShowGlPreview,
                        ),
                    ),
            ),
            DebugSection(
                title = "Interface previews",
                description = "Mock hosts for HUD layouts without the live sensor.",
                entries =
                    listOf(
                        DebugEntry(
                            "Pro HUD (mock)",
                            "Static composition of dials, tally, and chips.",
                            false,
                            onShowProHud,
                        ),
                        DebugEntry(
                            "HUD settings",
                            "Toggle overlays and readouts for the main preview.",
                            false,
                            onShowHudSettings,
                        ),
                        DebugEntry(
                            "About / heritage",
                            "Device notes and encoder recipe snapshots.",
                            false,
                            onShowAbout,
                        ),
                    ),
            ),
            DebugSection(
                title = "Platform & root",
                description = "Native code health and optional root-assisted features.",
                entries =
                    listOf(
                        DebugEntry(
                            "Native diagnostics",
                            "JNI / .so load status without opening the camera.",
                            false,
                            onShowNativeDiagnostics,
                        ),
                        DebugEntry(
                            "Root-only enhancements",
                            "What Magisk / KernelSU unlock — each item lists a non-root fallback.",
                            false,
                            onShowRootSettings,
                        ),
                        DebugEntry(
                            "Diagnostics dump (quick)",
                            "Same action as the disk export below; repeated here for convenience.",
                            false,
                            onDumpDiagnostics,
                        ),
                    ),
            ),
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PnsColors.Charcoal).padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackToCamera) { Text("Back to camera") }
                Text("Diagnostics & tools", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    text =
                        "These screens are for engineering and support. " +
                            "Everyday shooting stays on the main camera view.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
                PermissionCallout(
                    hasCameraPermission = hasCameraPermission,
                    onRequestPermission = onRequestPermission,
                )
                val orientationProbe by OrientationProbeBridge.snapshotState
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.06f),
                        ),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Orientation / preview probe",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                        )
                        Text(
                            text =
                                "Live gravity + buffer/view/chrome readouts while the preview is open. " +
                                    "Opens an idle snapshot when the camera is not running.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        OrientationProbeOverlay(
                            bufferSize = orientationProbe.bufferSize,
                            centerViewSize = orientationProbe.centerViewSize,
                            sensorOrientationDeg = orientationProbe.sensorOrientationDeg,
                            chromeRotationDegSnapped = orientationProbe.chromeRotationDegSnapped,
                            chromeRotationDegSmooth = orientationProbe.chromeRotationDegSmooth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.06f),
                        ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Permission welcome (QA)", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text(
                            text =
                                "Clears the first-run flag and shows the onboarding flow again " +
                                    "(intro, each runtime permission, vibration note, notification policy).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        OutlinedButton(onClick = onResetPermissionWelcome, modifier = Modifier.fillMaxWidth()) {
                            Text("Reset permission welcome")
                        }
                    }
                }
            }
        }

        items(sections) { section ->
            SectionCard(section = section, hasCameraPermission = hasCameraPermission)
        }

        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.06f),
                    ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Probe snapshot", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        text = "Short summaries from the last on-device capability scan. Export the full report below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    for (line in cameraSummaries) {
                        Text(
                            text = line,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }

        if (capabilityGateLines.isNotEmpty()) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.06f),
                        ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Capability gates (live)",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Text(
                            text =
                                "Evaluated from CameraCharacteristics for the primary wide camera " +
                                    "(see CapabilityGate / HardwareCapsSnapshot).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                        for (line in capabilityGateLines) {
                            Text(
                                text = line,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
        }

        item {
            RowActions(
                reportMdReady = reportMdReady,
                onExport = onExport,
                onDumpDiagnostics = onDumpDiagnostics,
            )
        }
    }
}

@Composable
private fun PermissionCallout(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    if (hasCameraPermission) return
    Card(
        colors = CardDefaults.cardColors(containerColor = PnsColors.WarnAmber.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Camera access needed", style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(
                text = "Grant the camera permission to run live probes and previews.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Button(onClick = onRequestPermission) { Text("Grant permission") }
        }
    }
}

@Composable
private fun SectionCard(
    section: DebugSection,
    hasCameraPermission: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, color = PnsColors.PhotoOrange)
            Text(
                section.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
            for (entry in section.entries) {
                val enabled = !entry.requiresCamera || hasCameraPermission
                OutlinedButton(
                    onClick = entry.onClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(entry.title)
                        Text(
                            entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowActions(
    reportMdReady: Boolean,
    onExport: () -> Unit,
    onDumpDiagnostics: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Outputs", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Button(onClick = onExport, enabled = reportMdReady, modifier = Modifier.fillMaxWidth()) {
            Text("Export probe report (Markdown)")
        }
        OutlinedButton(onClick = onDumpDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("Write diagnostics package to disk")
        }
    }
}
