package dev.pointandshoot

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
 * Shallow probe export summaries for [DebugMenuScreen] (keeps the screen composable under Detekt parameter limits).
 */
data class DebugMenuProbeSnapshot(
    val reportMdReady: Boolean,
    val cameraSummaries: List<String>,
    val shallowScanHubLine: String? = null,
    val capabilityGateLines: List<String> = emptyList(),
    /** Last [android.hardware.camera2.CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID] from preview (engineering). */
    val logicalMultiCameraActivePhysicalId: String? = null,
)

/**
 * Unified **engineering hub**: probe tools, diagnostics, and in-app developer shortcuts.
 * Open from the preview grid via **long-press Settings**, or when launching with **`pns_screen=probehub`**
 * ([PNS_SCREEN_PROBE_HUB]) for ADB automation (`scripts/pns_ae_highlight_probe_adb.ps1`).
 *
 * @param onBackToCamera When non-null, shows a back affordance to return to the live preview. When null (probe
 * activity root), the host should install [androidx.activity.compose.BackHandler] for navigation.
 */
@Suppress("LongParameterList", "FunctionNaming", "LongMethod")
@Composable
fun DebugMenuScreen(
    padding: PaddingValues,
    hasCameraPermission: Boolean,
    probeSnapshot: DebugMenuProbeSnapshot,
    onBackToCamera: (() -> Unit)?,
    onShowMapping: () -> Unit,
    onShowPreviewEngine: () -> Unit,
    onShowEncoderProbe: () -> Unit,
    onShowLegacyCamera1: () -> Unit,
    onShowDeepCaps: () -> Unit,
    onShowFleetMatrix: () -> Unit,
    onShowFaceMeterProbe: () -> Unit,
    onShowQrScan: () -> Unit,
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
    onShowEyeOverlayCalibrator: () -> Unit,
    onShowLutImport: () -> Unit,
    onShowGlPreview: () -> Unit,
    onShowNativeDiagnostics: () -> Unit,
    onShowRootSettings: () -> Unit,
    onDumpDiagnostics: () -> Unit,
    onRequestPermission: () -> Unit,
    onResetPermissionWelcome: () -> Unit,
    onExport: () -> Unit,
    probeHubNavEpoch: Int = 0,
    onRecordProbeHubEntry: (String) -> Unit = {},
    onLaunchProbeHubTitle: (String) -> Unit = {},
    onToggleProbeHubFavorite: (String) -> Unit = {},
    onProbeHubSearchPick: (ProbeHubSearchPick) -> Unit = {},
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val recentTitles = remember(probeHubNavEpoch) { ProbeHubRecentsStore.recentTitles(appCtx) }
    val favoriteTitles = remember(probeHubNavEpoch) { ProbeHubRecentsStore.favoriteTitles(appCtx) }

    val sections =
        listOf(
            DebugSection(
                title = "Live camera & preview",
                description = "Interactive previews and mapping helpers tied to the active sensor.",
                entries =
                    listOf(
                        DebugEntry(
                            "Dodge lens mapping",
                            "Logical / physical camera routing on multi-lens devices.",
                            true,
                            onShowMapping,
                        ),
                        DebugEntry(
                            "Live preview (engine)",
                            "Camera2 preview path with HUD — same as the main camera experience.",
                            true,
                            onShowPreviewEngine,
                        ),
                        DebugEntry(
                            "Eye overlay calibration",
                            "D-pad on live preview to align green eye marks; saved to device prefs.",
                            true,
                            onShowEyeOverlayCalibrator,
                        ),
                        DebugEntry(
                            "QR / barcode scan",
                            "CameraX ImageAnalysis (YUV) + ZXing; ADB `pns_screen=qrscan`.",
                            true,
                            onShowQrScan,
                        ),
                        DebugEntry(
                            "Legacy Camera1 probe",
                            "Deprecated API — useful when debugging OEM-specific quirks.",
                            true,
                            onShowLegacyCamera1,
                        ),
                    ),
            ),
            DebugSection(
                title = "Capability matrices",
                description = "Structured dumps to share with maintainers (JSON / logs).",
                entries =
                    listOf(
                        DebugEntry(
                            "Device capability matrix",
                            "Unified matrix + catalog — Summary, By camera, Features (search), Raw JSON; export + ADB paths (17.3).",
                            true,
                            onShowFleetMatrix,
                        ),
                        DebugEntry(
                            "Deep capabilities",
                            "Stream maps, formats, and static metadata in one JSON blob.",
                            true,
                            onShowDeepCaps,
                        ),
                        DebugEntry(
                            "Face / eye / metering probe",
                            "Full PROBE_RESULTS markdown + compact JSON; ADB `facemeter` + `pns_autofacemeter`.",
                            false,
                            onShowFaceMeterProbe,
                        ),
                        DebugEntry(
                            "Session configuration matrix",
                            "Which Camera2 sessions can be built for each template.",
                            true,
                            onShowSessionMatrix,
                        ),
                        DebugEntry(
                            "HDR / dynamic range runtime",
                            "HDR and dynamic-range session probes.",
                            true,
                            onShowHdrDcgRuntime,
                        ),
                        DebugEntry(
                            "Logical vs physical",
                            "How logical cameras map to physical camera IDs.",
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
                            "High frame-rate encoding paths per MIME type.",
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
                            "Stream combinations the HAL rejects.",
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
                description = "Creative pipeline tooling — optional for capture debugging.",
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
                description = "HUD and layout without tying up the live sensor.",
                entries =
                    listOf(
                        DebugEntry(
                            "Pro HUD (mock)",
                            "Static composition: dials, tally, and chips.",
                            false,
                            onShowProHud,
                        ),
                        DebugEntry(
                            "HUD settings",
                            "Toggle overlays and readouts used on the main preview.",
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
                description = "Native code health and optional privileged features.",
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
                            "What Magisk / KernelSU unlock (with non-root fallbacks).",
                            false,
                            onShowRootSettings,
                        ),
                        DebugEntry(
                            "Diagnostics dump (quick)",
                            "Same as the disk action under Outputs below.",
                            false,
                            onDumpDiagnostics,
                        ),
                    ),
            ),
        )

    val hubMenuEntries =
        remember(sections) {
            sections.flatMap { section ->
                section.entries.map { entry -> ProbeHubMenuEntry(entry.title, section.title) }
            }
        }
    val probeHubSearchIndex = remember(hubMenuEntries) { ProbeHubSearch.buildIndex(appCtx, hubMenuEntries) }
    var probeHubSearchQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(padding),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            EngineeringHubHeader(onBackToCamera = onBackToCamera)
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text =
                        if (onBackToCamera != null) {
                            "Engineering and support tools. Everyday shooting stays on the main camera view."
                        } else {
                            "Same hub as long-press Settings on the preview grid. " +
                                "Use system Back or Live preview (engine) below to return to the camera."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
                HubPermissionBanner(
                    hasCameraPermission = hasCameraPermission,
                    onRequestPermission = onRequestPermission,
                )
                HubProbeQuickReturnStrip(
                    recentTitles = recentTitles,
                    favoriteTitles = favoriteTitles,
                    onOpenTitle = { t ->
                        onRecordProbeHubEntry(t)
                        onLaunchProbeHubTitle(t)
                    },
                    onToggleFavorite = onToggleProbeHubFavorite,
                )
                ChromeSettingsSearchField(
                    query = probeHubSearchQuery,
                    onQueryChange = { probeHubSearchQuery = it },
                )
                if (probeHubSearchQuery.isNotBlank()) {
                    ProbeHubSearchResults(
                        query = probeHubSearchQuery,
                        index = probeHubSearchIndex,
                        onPick = { hit ->
                            probeHubSearchQuery = ""
                            onProbeHubSearchPick(hit.pick)
                        },
                    )
                }
                val orientationProbe by OrientationProbeBridge.snapshotState
                DebugAuxiliaryCard(title = "Logical multi-camera (preview)") {
                    var activePhys by remember {
                        mutableStateOf(
                            probeSnapshot.logicalMultiCameraActivePhysicalId
                                ?: PreviewLogicalPhysicalDebugBridge.snapshot(),
                        )
                    }
                    LaunchedEffect(probeHubNavEpoch) {
                        while (true) {
                            activePhys = PreviewLogicalPhysicalDebugBridge.snapshot()
                            delay(400)
                        }
                    }
                    Text(
                        text =
                            "Active physical camera id from the last preview session " +
                                "(LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID). Empty until preview has run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                    Text(
                        text = activePhys?.ifBlank { "—" } ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFAAEECC),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                DebugAuxiliaryCard(title = "Orientation / preview probe") {
                    Text(
                        text =
                            "Live gravity + buffer / view / chrome angles while preview runs. " +
                                "Shows an idle snapshot when the camera is off.",
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
                DebugAuxiliaryCard(title = "Permission welcome (QA)") {
                    Text(
                        text =
                            "Clears the first-run flag and shows the onboarding flow again " +
                                "(permissions, vibration note, notification policy).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                    OutlinedButton(onClick = onResetPermissionWelcome, modifier = Modifier.fillMaxWidth()) {
                        Text("Reset permission welcome")
                    }
                }
            }
        }

        items(sections) { section ->
            SectionBlock(
                section = section,
                hasCameraPermission = hasCameraPermission,
                favoriteTitles = favoriteTitles,
                onRecordProbeHubEntry = onRecordProbeHubEntry,
                onToggleProbeHubFavorite = onToggleProbeHubFavorite,
            )
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(top = 8.dp),
            ) {
                DebugAuxiliaryCard(title = "Probe snapshot") {
                    ProbeSnapshotCardInner(
                        cameraSummaries = probeSnapshot.cameraSummaries,
                        shallowScanHubLine = probeSnapshot.shallowScanHubLine,
                    )
                }
            }
        }

        if (probeSnapshot.capabilityGateLines.isNotEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    DebugAuxiliaryCard(title = "Capability gates (live)") {
                        Text(
                            text =
                                "Evaluated from CameraCharacteristics for the primary wide camera " +
                                    "(CapabilityGate / HardwareCapsSnapshot).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = Color.White.copy(alpha = 0.12f),
                        )
                        for (line in probeSnapshot.capabilityGateLines) {
                            Text(
                                text = line,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f),
                            )
                        }
                    }
                }
            }
        }

        item {
            OutputsCard(
                reportMdReady = probeSnapshot.reportMdReady,
                onExport = onExport,
                onDumpDiagnostics = onDumpDiagnostics,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .padding(top = 8.dp),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ProbeSnapshotCardInner(
    cameraSummaries: List<String>,
    shallowScanHubLine: String?,
) {
    Text(
        text = "Short summaries from the last on-device capability scan. Export the full report under Outputs.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.65f),
    )
    if (!shallowScanHubLine.isNullOrBlank()) {
        Text(
            text = shallowScanHubLine,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFAAEECC),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = Color.White.copy(alpha = 0.12f),
    )
    for (line in cameraSummaries) {
        Text(
            text = line,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun EngineeringHubHeader(onBackToCamera: (() -> Unit)?) {
    Surface(color = Color.Black.copy(alpha = 0.55f)) {
        if (onBackToCamera != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackToCamera) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to camera",
                        tint = Color.White.copy(alpha = 0.92f),
                    )
                }
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Engineering hub",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    Text(
                        text = "Probe · diagnostics · developer",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
                Text(
                    text = "Engineering hub",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    text = "Point & Shoot · CameraCapabilitiesProbe",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
                Text(
                    text = "Long-press Settings on the preview grid opens this same menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.52f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
    }
}

@Suppress("FunctionNaming")
@Composable
private fun HubProbeQuickReturnStrip(
    recentTitles: List<String>,
    favoriteTitles: Set<String>,
    onOpenTitle: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    if (recentTitles.isEmpty() && favoriteTitles.isEmpty()) {
        Text(
            text = "Open a probe below to build recents and starred favorites here.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.52f),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (favoriteTitles.isNotEmpty()) {
            Text(
                text = "Favorites",
                style = MaterialTheme.typography.labelLarge,
                color = PnsColors.PhotoOrange,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (t in favoriteTitles) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onOpenTitle(t) }, modifier = Modifier.widthIn(max = 200.dp)) {
                            Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onToggleFavorite(t) }) {
                            Icon(
                                Icons.Outlined.Star,
                                contentDescription = "Remove favorite",
                                tint = PnsColors.PhotoOrange,
                            )
                        }
                    }
                }
            }
        }
        if (recentTitles.isNotEmpty()) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (t in recentTitles) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onOpenTitle(t) }, modifier = Modifier.widthIn(max = 200.dp)) {
                            Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onToggleFavorite(t) }) {
                            Icon(
                                imageVector =
                                    if (favoriteTitles.contains(t)) {
                                        Icons.Outlined.Star
                                    } else {
                                        Icons.Outlined.StarBorder
                                    },
                                contentDescription = if (favoriteTitles.contains(t)) "Unstar" else "Star as favorite",
                                tint = Color.White.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubPermissionBanner(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    if (hasCameraPermission) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A2F)),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Camera permission granted — live probes and preview are available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAAEECC),
                )
            }
        }
        return
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PnsColors.WarnAmber.copy(alpha = 0.18f)),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Camera permission required",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                text = "Grant access to enable mapping, preview, matrices, and timing probes.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Text("Grant camera permission")
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open app settings")
            }
        }
    }
}

@Composable
private fun DebugAuxiliaryCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.95f),
            )
            content()
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SectionBlock(
    section: DebugSection,
    hasCameraPermission: Boolean,
    favoriteTitles: Set<String>,
    onRecordProbeHubEntry: (String) -> Unit,
    onToggleProbeHubFavorite: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = PnsColors.PhotoOrange,
            )
            Text(
                text = section.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (entry in section.entries) {
                DebugEntryRow(
                    entry = entry,
                    enabled = !entry.requiresCamera || hasCameraPermission,
                    isFavorite = favoriteTitles.contains(entry.title),
                    onRecordProbeHubEntry = onRecordProbeHubEntry,
                    onToggleFavorite = { onToggleProbeHubFavorite(entry.title) },
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun DebugEntryRow(
    entry: DebugEntry,
    enabled: Boolean,
    isFavorite: Boolean,
    onRecordProbeHubEntry: (String) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val rowSemantics =
        buildString {
            append(entry.title)
            append(". ")
            append(entry.subtitle)
            if (enabled) {
                append(". Opens this tool.")
            } else {
                append(". Disabled, needs camera permission.")
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = rowSemantics },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Card(
            onClick = {
                if (enabled) onRecordProbeHubEntry(entry.title)
                entry.onClick()
            },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    disabledContainerColor = Color.White.copy(alpha = 0.04f),
                ),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
                    )
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = if (enabled) 0.68f else 0.38f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!enabled) {
                    Text(
                        text = "Needs camera",
                        style = MaterialTheme.typography.labelSmall,
                        color = PnsColors.WarnAmber,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.38f),
                    )
                }
            }
        }
        if (enabled) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                    tint = if (isFavorite) PnsColors.PhotoOrange else Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun OutputsCard(
    reportMdReady: Boolean,
    onExport: () -> Unit,
    onDumpDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DebugAuxiliaryCard(title = "Outputs", modifier = modifier) {
        Text(
            text = "Share probe results or write a diagnostics bundle to storage.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
        )
        Button(onClick = onExport, enabled = reportMdReady, modifier = Modifier.fillMaxWidth()) {
            Text("Export probe report (Markdown)")
        }
        OutlinedButton(onClick = onDumpDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("Write diagnostics package to disk")
        }
    }
}

