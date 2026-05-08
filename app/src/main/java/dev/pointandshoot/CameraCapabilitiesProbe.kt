package dev.pointandshoot

import android.Manifest
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "PNS.Probe"

const val EXTRA_PNS_SCREEN = "pns_screen"
const val EXTRA_PNS_AUTOSWEEP = "pns_autosweep"
const val EXTRA_PNS_AUTOENC = "pns_autoenc"
const val EXTRA_PNS_AUTODEEPCAPS = "pns_autodeepcaps"
const val EXTRA_PNS_AUTOSESSIONMATRIX = "pns_autosessionmatrix"
const val EXTRA_PNS_AUTOHDRDCG = "pns_autohdrdcg"
const val EXTRA_PNS_AUTOCAPTURELATENCY = "pns_autocapturelatency"
const val EXTRA_PNS_AUTORAWHDREXCL = "pns_autorawhdrexcl"
const val EXTRA_PNS_AUTOBURST = "pns_autoburst"
const val EXTRA_PNS_AUTOLOGICALPHYSICAL = "pns_autologicalphysical"
const val EXTRA_PNS_AUTOEXHAUSTIVE = "pns_autoexhaustive"
const val EXTRA_PNS_INCLUDE_LOGICAL = "pns_include_logical"
/** When true with exhaustive screen: run constrained high-speed (HFR) encoder matrix only; skip regular (≤120fps) attempts. */
const val EXTRA_PNS_EXHAUSTIVE_HFR_ONLY = "pns_exhaustive_hfr_only"
const val EXTRA_PNS_AUTOLEGACY = "pns_autolegacy"
private const val SCREEN_PREVIEW = "preview"
private const val SCREEN_ENC = "enc"
private const val SCREEN_DEEPCAPS = "deepcaps"
private const val SCREEN_SESSION_MATRIX = "sessionmatrix"
private const val SCREEN_HDR_DCG = "hdrdcg"
private const val SCREEN_CAPTURE_LATENCY = "capturelatency"
private const val SCREEN_RAW_HDR_EXCL = "rawhdrexcl"
private const val SCREEN_BURST = "burst"
private const val SCREEN_LOGICAL_PHYSICAL = "logicalphysical"
private const val SCREEN_EXHAUSTIVE = "exhaustive"
private const val SCREEN_CAMERA1 = "camera1"
private const val SCREEN_ABOUT = "about"
private const val SCREEN_PROHUD = "prohud"
private const val SCREEN_HUDSETTINGS = "hudsettings"
private const val SCREEN_CALIBRATE = "calibrate"
private const val SCREEN_LUTIMPORT = "lutimport"

const val SWEEP_SIGNAL_TAG = "PNS.SWEEP_SIGNAL"

@Composable
fun CameraCapabilitiesProbe(
    launchScreen: String? = null,
    autoSweep: Boolean = false,
    autoEncProbe: Boolean = false,
    autoDeepCaps: Boolean = false,
    autoSessionMatrix: Boolean = false,
    autoHdrDcgRuntime: Boolean = false,
    autoCaptureLatency: Boolean = false,
    autoRawHdrExclusivity: Boolean = false,
    autoBurstProbe: Boolean = false,
    autoLogicalPhysical: Boolean = false,
    autoExhaustive: Boolean = false,
    exhaustiveIncludeLogical: Boolean = false,
    exhaustiveHfrOnly: Boolean = false,
    autoLegacyCamera1: Boolean = false,
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var reportMd by remember { mutableStateOf("") }
    var cameraSummaries by remember { mutableStateOf(listOf<String>()) }
    var showMapping by remember { mutableStateOf(false) }
    var showPreviewEngine by remember { mutableStateOf(false) }
    var showEncoderProbe by remember { mutableStateOf(false) }
    var showLegacyCamera1 by remember { mutableStateOf(false) }
    var showDeepCaps by remember { mutableStateOf(false) }
    var showSessionMatrix by remember { mutableStateOf(false) }
    var showHdrDcgRuntime by remember { mutableStateOf(false) }
    var showCaptureLatency by remember { mutableStateOf(false) }
    var showRawHdrExcl by remember { mutableStateOf(false) }
    var showBurstProbe by remember { mutableStateOf(false) }
    var showLogicalPhysical by remember { mutableStateOf(false) }
    var showExhaustive by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showProHud by remember { mutableStateOf(false) }
    var showHudSettings by remember { mutableStateOf(false) }
    var showCalibrate by remember { mutableStateOf(false) }
    var showLutImport by remember { mutableStateOf(false) }

    val activity = context as? ComponentActivity
    val intentIncludeLogical = activity?.intent?.getBooleanExtra(EXTRA_PNS_INCLUDE_LOGICAL, false) ?: false
    val intentExhaustiveHfrOnly = activity?.intent?.getBooleanExtra(EXTRA_PNS_EXHAUSTIVE_HFR_ONLY, false) ?: false
    val effectiveIncludeLogical = exhaustiveIncludeLogical || intentIncludeLogical
    val effectiveExhaustiveHfrOnly = exhaustiveHfrOnly || intentExhaustiveHfrOnly

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                os.write(reportMd.toByteArray(Charsets.UTF_8))
            }
        }.onFailure { e ->
            Log.e(TAG, "Export failed", e)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission, launchScreen) {
        if (!hasCameraPermission) return@LaunchedEffect
        if (launchScreen == SCREEN_PREVIEW) {
            showPreviewEngine = true
        } else if (launchScreen == SCREEN_ENC) {
            showEncoderProbe = true
        } else if (launchScreen == SCREEN_DEEPCAPS) {
            showDeepCaps = true
        } else if (launchScreen == SCREEN_SESSION_MATRIX) {
            showSessionMatrix = true
        } else if (launchScreen == SCREEN_HDR_DCG) {
            showHdrDcgRuntime = true
        } else if (launchScreen == SCREEN_CAPTURE_LATENCY) {
            showCaptureLatency = true
        } else if (launchScreen == SCREEN_RAW_HDR_EXCL) {
            showRawHdrExcl = true
        } else if (launchScreen == SCREEN_BURST) {
            showBurstProbe = true
        } else if (launchScreen == SCREEN_LOGICAL_PHYSICAL) {
            showLogicalPhysical = true
        } else if (launchScreen == SCREEN_EXHAUSTIVE) {
            showExhaustive = true
        } else if (launchScreen == SCREEN_CAMERA1) {
            showLegacyCamera1 = true
        } else if (launchScreen == SCREEN_ABOUT) {
            showAbout = true
        } else if (launchScreen == SCREEN_PROHUD) {
            showProHud = true
        } else if (launchScreen == SCREEN_HUDSETTINGS) {
            showHudSettings = true
        } else if (launchScreen == SCREEN_CALIBRATE) {
            showCalibrate = true
        } else if (launchScreen == SCREEN_LUTIMPORT) {
            showLutImport = true
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val report = buildProbeReport(context)
        reportMd = report
        Log.i(TAG, "Probe built (${report.length} chars), ready to export.")
        cameraSummaries = report
            .lineSequence()
            .filter { it.startsWith("- Camera ") }
            .toList()

        Log.i(TAG, "\n$report")
    }

    if (showMapping) {
        DodgeMappingScreen(onBackToProbe = { showMapping = false })
        return
    }

    if (showPreviewEngine) {
        PreviewEngineScreen(
            onBack = { showPreviewEngine = false },
            startAutoSweep = autoSweep,
        )
        return
    }

    if (showEncoderProbe) {
        HfrEncoderProbeScreen(
            onBack = { showEncoderProbe = false },
            startAutoProbe = autoEncProbe,
        )
        return
    }

    if (showLegacyCamera1) {
        LegacyCamera1ProbeScreen(
            onBack = { showLegacyCamera1 = false },
            startAuto = autoLegacyCamera1,
        )
        return
    }

    if (showDeepCaps) {
        DeepCapsProbeScreen(
            onBack = { showDeepCaps = false },
            startAuto = autoDeepCaps,
        )
        return
    }

    if (showSessionMatrix) {
        SessionMatrixProbeScreen(
            onBack = { showSessionMatrix = false },
            startAuto = autoSessionMatrix,
        )
        return
    }

    if (showHdrDcgRuntime) {
        HdrDcgRuntimeProbeScreen(
            onBack = { showHdrDcgRuntime = false },
            startAuto = autoHdrDcgRuntime,
        )
        return
    }

    if (showCaptureLatency) {
        CaptureLatencyProbeScreen(
            onBack = { showCaptureLatency = false },
            startAuto = autoCaptureLatency,
        )
        return
    }

    if (showRawHdrExcl) {
        RawHdrExclusivityProbeScreen(
            onBack = { showRawHdrExcl = false },
            startAuto = autoRawHdrExclusivity,
        )
        return
    }

    if (showBurstProbe) {
        BurstProbeScreen(
            onBack = { showBurstProbe = false },
            startAuto = autoBurstProbe,
        )
        return
    }

    if (showLogicalPhysical) {
        LogicalPhysicalProbeScreen(
            onBack = { showLogicalPhysical = false },
            startAuto = autoLogicalPhysical,
        )
        return
    }

    if (showExhaustive) {
        ExhaustiveMediaProbeScreen(
            onBack = { showExhaustive = false },
            startAuto = autoExhaustive,
            includeLogicalCamera = effectiveIncludeLogical,
            hfrOnly = effectiveExhaustiveHfrOnly,
        )
        return
    }

    if (showAbout) {
        // BUILD_PLAN \u00a72 Phase 0 V&V "Engine consumption (drive HUD chips /
        // About-page recipe list off `EncoderSummary`)": when the user opens
        // About / Heritage we hydrate the live "From the latest probe (live)"
        // section from whatever exhaustive_probe_*.json is most recent under
        // getExternalFilesDir(null). On the first device run (no probe artifact
        // yet) liveSummary is null and the section is hidden entirely.
        val live = remember {
            EncoderAttemptJsonAdapter.loadLatest(context)?.let { result ->
                EncoderResultAggregator.summarize(result.attempts)
            }
        }
        AboutScreen(
            onBack = { showAbout = false },
            liveSummary = live,
        )
        return
    }

    if (showProHud) {
        ProHudScreen(onBack = { showProHud = false })
        return
    }

    if (showHudSettings) {
        HudSettingsScreen(onBack = { showHudSettings = false })
        return
    }

    if (showCalibrate) {
        CalibrateScreen(onBack = { showCalibrate = false })
        return
    }

    if (showLutImport) {
        LutImporterScreen(onBack = { showLutImport = false })
        return
    }

    val insets = rememberSystemInsetsDp()
    ProbeHomeContent(
            padding = insets.asPaddingValues(extra = 16.dp),
            hasCameraPermission = hasCameraPermission,
            reportMdReady = reportMd.isNotBlank(),
            cameraSummaries = cameraSummaries,
            onShowMapping = { showMapping = true },
            onShowPreviewEngine = { showPreviewEngine = true },
            onShowEncoderProbe = { showEncoderProbe = true },
            onShowLegacyCamera1 = { showLegacyCamera1 = true },
            onShowDeepCaps = { showDeepCaps = true },
            onShowSessionMatrix = { showSessionMatrix = true },
            onShowHdrDcgRuntime = { showHdrDcgRuntime = true },
            onShowCaptureLatency = { showCaptureLatency = true },
            onShowRawHdrExcl = { showRawHdrExcl = true },
            onShowBurstProbe = { showBurstProbe = true },
            onShowLogicalPhysical = { showLogicalPhysical = true },
            onShowExhaustive = { showExhaustive = true },
            onShowAbout = { showAbout = true },
            onShowProHud = { showProHud = true },
            onShowHudSettings = { showHudSettings = true },
            onShowCalibrate = { showCalibrate = true },
            onShowLutImport = { showLutImport = true },
            onDumpDiagnostics = {
                DiagnosticsMode.setEnabled(context, true)
                val path = DiagnosticsMode.dump(context)
                val msg = if (path != null) "Diagnostics written to $path" else "Diagnostics dump skipped (no external storage)"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            },
            onRequestPermission = { requestPermission.launch(Manifest.permission.CAMERA) },
            onExport = {
                val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now())
                exportLauncher.launch("PROBE_RESULTS_$ts.md")
            },
        )
}

@Composable
private fun ProbeHomeContent(
    padding: PaddingValues,
    hasCameraPermission: Boolean,
    reportMdReady: Boolean,
    cameraSummaries: List<String>,
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
    onDumpDiagnostics: () -> Unit,
    onRequestPermission: () -> Unit,
    onExport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Point & Shoot — Probe")
        Text("Phase 0: CameraCapabilitiesProbe")
        Text(if (hasCameraPermission) "Camera permission granted." else "Camera permission required to probe.")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onShowMapping, enabled = hasCameraPermission) { Text("Dodge mapping") }
            OutlinedButton(onClick = onShowPreviewEngine, enabled = hasCameraPermission) { Text("Preview engine") }
            OutlinedButton(onClick = onShowEncoderProbe, enabled = hasCameraPermission) { Text("HFR encoder probe") }
            OutlinedButton(onClick = onShowLegacyCamera1, enabled = hasCameraPermission) { Text("Camera1 probe") }
            OutlinedButton(onClick = onShowDeepCaps, enabled = hasCameraPermission) { Text("Deep caps (JSON)") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onShowSessionMatrix, enabled = hasCameraPermission) {
                Text("Session matrix (JSON)")
            }
            OutlinedButton(onClick = onShowHdrDcgRuntime, enabled = hasCameraPermission) {
                Text("HDR / DR session (JSON)")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onShowCaptureLatency, enabled = hasCameraPermission) {
                Text("Capture latency")
            }
            OutlinedButton(onClick = onShowRawHdrExcl, enabled = hasCameraPermission) {
                Text("RAW / HDR exclusivity")
            }
            OutlinedButton(onClick = onShowBurstProbe, enabled = hasCameraPermission) {
                Text("Burst probe")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onShowLogicalPhysical, enabled = hasCameraPermission) {
                Text("Logical / physical (JSON)")
            }
            OutlinedButton(onClick = onShowExhaustive, enabled = hasCameraPermission) {
                Text("Exhaustive matrix (JSON)")
            }
            OutlinedButton(onClick = onShowAbout) { Text("About / Heritage") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onShowProHud) { Text("Pro HUD (preview)") }
            OutlinedButton(onClick = onShowHudSettings) { Text("Settings > HUD") }
            OutlinedButton(onClick = onDumpDiagnostics) { Text("Diagnostics dump") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onShowCalibrate) { Text("Calibrate") }
            OutlinedButton(onClick = onShowLutImport) { Text("Import LUT") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onRequestPermission) { Text("Request permission") }
            Button(onClick = onExport, enabled = reportMdReady) { Text("Export Markdown") }
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(cameraSummaries) { s ->
                Text(text = s, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun buildProbeReport(context: Context): String {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraIds = runCatching { cameraManager.cameraIdList.toList() }.getOrDefault(emptyList())

    val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    val sb = StringBuilder()

    sb.appendLine("# Point & Shoot — PROBE RESULTS")
    sb.appendLine()
    sb.appendLine("- Generated: $now")
    sb.appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
    sb.appendLine("- Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    sb.appendLine()
    sb.appendLine("## Cameras (${cameraIds.size})")
    sb.appendLine()

    for (id in cameraIds) {
        val cc = runCatching { cameraManager.getCameraCharacteristics(id) }.getOrNull()
        if (cc == null) {
            sb.appendLine("- Camera $id: FAILED to read characteristics")
            continue
        }

        val facing = when (cc.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "BACK"
            CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }

        val focalLengths = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.joinToString(prefix = "[", postfix = "]") { it.toString() }
            ?: "null"

        val activeArray = cc.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val pixelArray = cc.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)

        sb.appendLine("- Camera $id: facing=$facing focalLengths=$focalLengths activeArray=$activeArray pixelArray=$pixelArray")
        sb.appendLine()

        appendKeysSection(sb, "Vendor/standard characteristics keys", cc.keys.map { it.name })

        val reqKeys = runCatching { cc.availableCaptureRequestKeys }
            .getOrNull()
            ?.map { it.name }
            ?: emptyList()
        appendKeysSection(sb, "Available CaptureRequest keys", reqKeys)

        val resKeys = runCatching { cc.availableCaptureResultKeys }
            .getOrNull()
            ?.map { it.name }
            ?: emptyList()
        appendKeysSection(sb, "Available CaptureResult keys", resKeys)

        val sessionKeys = runCatching { cc.availableSessionKeys }
            .getOrNull()
            ?.map { it.name }
            ?: emptyList()
        appendKeysSection(sb, "Available SessionConfiguration keys", sessionKeys)

        sb.appendLine("### Typed values (selected)")
        sb.appendLine()

        val capabilities = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.joinToString(prefix = "[", postfix = "]") { it.toString() }
            ?: "null"
        sb.appendLine("- android.request.availableCapabilities: $capabilities")

        val dynRangeProfiles = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)
            ?.supportedProfiles
            ?.joinToString(prefix = "[", postfix = "]") { it.toString() }
            ?: "null"
        sb.appendLine("- android.request.availableDynamicRangeProfiles: $dynRangeProfiles")

        val recommendedTenBit = cc.get(CameraCharacteristics.REQUEST_RECOMMENDED_TEN_BIT_DYNAMIC_RANGE_PROFILE)
        sb.appendLine("- android.request.recommendedTenBitDynamicRangeProfile: ${recommendedTenBit?.toString() ?: "null"}")

        val aeFpsRanges = cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        sb.appendLine("- android.control.aeAvailableTargetFpsRanges: ${aeFpsRanges?.joinToString(prefix = "[", postfix = "]") ?: "null"}")

        val maxRawOutputs = cc.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW)
        sb.appendLine("- android.request.maxNumOutputRaw: ${maxRawOutputs?.toString() ?: "null"}")

        val physicalIds = runCatching { cc.physicalCameraIds.toList() }.getOrDefault(emptyList())
        sb.appendLine("- physicalCameraIds: ${physicalIds.joinToString(prefix = "[", postfix = "]")}")

        sb.appendLine()

        sb.appendLine("### StreamConfigurationMap (derived FPS candidates)")
        sb.appendLine()
        val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (map == null) {
            sb.appendLine("- android.scaler.streamConfigurationMap: null")
        } else {
            appendStreamConfigSummary(sb, map)
        }
        sb.appendLine()

        sb.appendLine("### Vendor-key highlights (name-based)")
        sb.appendLine()
        val vendorPool = (reqKeys + sessionKeys + resKeys).distinct()
        appendVendorHighlights(sb, vendorPool)
        sb.appendLine()

        val faceModes = cc.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
            ?.joinToString(prefix = "[", postfix = "]") { it.toString() }
            ?: "null"
        sb.appendLine("### Face detect modes")
        sb.appendLine()
        sb.appendLine("- STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES: $faceModes")
        sb.appendLine()

        sb.appendLine("### High-speed video configurations")
        sb.appendLine()
        sb.appendLine("- (see StreamConfigurationMap-derived FPS candidates above)")
        sb.appendLine()
    }

    return sb.toString()
}

private fun appendStreamConfigSummary(sb: StringBuilder, map: StreamConfigurationMap) {
    fun maxFpsNs(minFrameDurationNs: Long?): Double? {
        if (minFrameDurationNs == null || minFrameDurationNs <= 0L) return null
        return 1_000_000_000.0 / minFrameDurationNs.toDouble()
    }

    fun appendOutputSection(label: String, sizes: Array<Size>?, durationNs: (Size) -> Long?) {
        sb.appendLine("#### $label")
        if (sizes.isNullOrEmpty()) {
            sb.appendLine("- (none)")
            sb.appendLine()
            return
        }
        val sorted = sizes.sortedWith(compareBy({ it.width * it.height }, { it.width }))
        for (s in sorted) {
            val fps = maxFpsNs(durationNs(s))
            val fpsStr = if (fps == null) "unknown" else String.format("%.1f", fps)
            val is120 = fps != null && fps >= 120.0
            sb.appendLine("- ${s.width}x${s.height}  maxFps≈$fpsStr${if (is120) "  (>=120fps candidate)" else ""}")
        }
        sb.appendLine()
    }

    appendOutputSection(
        label = "Preview (SurfaceTexture)",
        sizes = runCatching { map.getOutputSizes(SurfaceTexture::class.java) }.getOrNull(),
    ) { s ->
        runCatching { map.getOutputMinFrameDuration(SurfaceTexture::class.java, s) }.getOrNull()
    }

    appendOutputSection(
        label = "Preview (SurfaceHolder)",
        sizes = runCatching { map.getOutputSizes(SurfaceHolder::class.java) }.getOrNull(),
    ) { s ->
        runCatching { map.getOutputMinFrameDuration(SurfaceHolder::class.java, s) }.getOrNull()
    }

    appendOutputSection(
        label = "RAW_SENSOR (ImageFormat.RAW_SENSOR)",
        sizes = runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR) }.getOrNull(),
    ) { s ->
        runCatching { map.getOutputMinFrameDuration(ImageFormat.RAW_SENSOR, s) }.getOrNull()
    }

    appendOutputSection(
        label = "YUV_420_888 (ImageFormat.YUV_420_888)",
        sizes = runCatching { map.getOutputSizes(ImageFormat.YUV_420_888) }.getOrNull(),
    ) { s ->
        runCatching { map.getOutputMinFrameDuration(ImageFormat.YUV_420_888, s) }.getOrNull()
    }

    sb.appendLine("#### High-speed video (Camera2 constrained high speed)")
    val hsSizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
    if (hsSizes.isEmpty()) {
        sb.appendLine("- (none)")
        return
    }

    for (s in hsSizes.sortedWith(compareBy({ it.width * it.height }, { it.width }))) {
        val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull()
        val rStr = ranges
            ?.distinct()
            ?.sortedBy { it.upper }
            ?.joinToString(prefix = "[", postfix = "]") { "[${it.lower}, ${it.upper}]" }
            ?: "null"
        sb.appendLine("- ${s.width}x${s.height} fpsRanges=$rStr")
    }
}

private fun appendVendorHighlights(sb: StringBuilder, keys: List<String>) {
    fun hits(vararg terms: String): List<String> =
        keys.filter { k -> terms.any { t -> k.contains(t, ignoreCase = true) } }.distinct().sorted()

    val lbmf = hits("lbmf", "mfhdr", "EnableMFHDR", "EnableIdealRAW")
    val dcgHdr = hits("dcg", "EnableHDRDCGMode", "hdr")
    val hybridAe = hits("hybrid", "ae", "dynamicFPSConfig", "HDRMode", "SnapshotHDRMode")
    val bkt = hits("bracket", "EnableAFBracketing", "BKT", "Grouping")

    sb.appendLine("- LBMF / MFHDR candidates: ${if (lbmf.isEmpty()) "(none found by name)" else lbmf.joinToString()}")
    sb.appendLine("- DCG-HDR / HDR candidates: ${if (dcgHdr.isEmpty()) "(none found by name)" else dcgHdr.joinToString()}")
    sb.appendLine("- Hybrid AE candidates: ${if (hybridAe.isEmpty()) "(none found by name)" else hybridAe.joinToString()}")
    sb.appendLine("- Bracketing candidates: ${if (bkt.isEmpty()) "(none found by name)" else bkt.joinToString()}")
}

private fun appendKeysSection(sb: StringBuilder, title: String, keys: List<String>) {
    val (vendor, standard) = keys
        .distinct()
        .sorted()
        .partition { it.contains("com.", ignoreCase = true) || it.contains("org.", ignoreCase = true) || it.contains("vendor", ignoreCase = true) }

    sb.appendLine("### $title")
    sb.appendLine()
    sb.appendLine("- Total: ${keys.distinct().size}")
    sb.appendLine("- Vendor-ish: ${vendor.size}")
    sb.appendLine()

    if (vendor.isNotEmpty()) {
        sb.appendLine("#### Vendor-ish keys")
        sb.appendLine()
        vendor.forEach { sb.appendLine("- `$it`") }
        sb.appendLine()
    }

    if (standard.isNotEmpty()) {
        sb.appendLine("#### Standard keys")
        sb.appendLine()
        standard.forEach { sb.appendLine("- `$it`") }
        sb.appendLine()
    }
}

