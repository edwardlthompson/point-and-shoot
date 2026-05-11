package dev.pointandshoot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
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
import androidx.compose.runtime.mutableIntStateOf
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
/**
 * Optional extras when `--es pns_screen preview` — drive dial / burst validation from ADB
 * (see `scripts/pns_adb_preview_validate.ps1`).
 */
const val EXTRA_PNS_PREVIEW_DIAL = "pns_preview_dial"
const val EXTRA_PNS_PREVIEW_RAW_COUNT = "pns_preview_raw_count"
const val EXTRA_PNS_PREVIEW_BRACKET = "pns_preview_bracket"
/** `standard_pro` or `ultra_max` — seeds [ImagingProfile] for scripted preview capture (Sprint 4.3 RAW12). */
const val EXTRA_PNS_PREVIEW_IMAGING_PROFILE = "pns_preview_imaging_profile"
/** Optional physical/logical id (e.g. `3` = ultra-wide on dodge) for scripted preview validation. */
const val EXTRA_PNS_PREVIEW_CAMERA_ID = "pns_preview_camera_id"
/**
 * When true with [EXTRA_PNS_PREVIEW_CAMERA_ID] on ultra-wide: attempt `com.oplus.macro.closeup.enable` on the
 * repeating preview request (Sprint 5.3 Super Macro ADB evidence).
 */
const val EXTRA_PNS_PREVIEW_SUPER_MACRO_PROBE = "pns_preview_super_macro_probe"

/** Seeds [HudSettings.selectedLutForStills] by [LutCatalog] enum name (e.g. `PnsCinematic`). */
const val EXTRA_PNS_PREVIEW_STILLS_LUT = "pns_preview_stills_lut"

/** BUILD_PLAN Milestone 6.3 — logs baseline vs LUT preview FPS (`PNS.AdbValidation` `m6 lutFps*`). */
const val EXTRA_PNS_PREVIEW_M6_FPS_LUT_PROBE = "pns_preview_m6_fps_lut_probe"

/** When true with [PNS_SCREEN_PREVIEW]: after the stream is up, grab one `TextureView` frame for calibration smoke (logs `calibrate preview frame grab ok`). */
const val EXTRA_PNS_PREVIEW_CALIBRATE_GRAB_SMOKE = "pns_preview_calibrate_grab_smoke"

/**
 * Optional `--ei pns_preview_self_timer_sec N` with [PNS_SCREEN_PREVIEW]: seeds [PreviewChromePreferences.selfTimerDelaySec]
 * (**0 / 3 / 5 / 10**; invalid values normalize to **0**). Logged as **`PNS.ChromeUx`** **`selfTimerSec=`** after apply.
 */
const val EXTRA_PNS_PREVIEW_SELF_TIMER_SEC = "pns_preview_self_timer_sec"

/** Value for [EXTRA_PNS_SCREEN] and system camera intents — opens [PreviewEngineScreen]. */
const val PNS_SCREEN_PREVIEW = "preview"
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
private const val SCREEN_GLPREVIEW = "glpreview"
private const val SCREEN_NATIVE = "native"
private const val SCREEN_ROOT_SETTINGS = "rootsettings"

const val SWEEP_SIGNAL_TAG = "PNS.SWEEP_SIGNAL"

@Composable
fun CameraCapabilitiesProbe(
    launchScreen: String? = null,
    imageCaptureReturn: ImageCaptureReturnContract? = null,
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
    var capabilityGateLines by remember { mutableStateOf(listOf<String>()) }
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
    var aboutLiveSummary by remember { mutableStateOf<EncoderSummary?>(null) }
    var showProHud by remember { mutableStateOf(false) }
    var showHudSettings by remember { mutableStateOf(false) }
    var hudSettingsFocus by remember { mutableStateOf(HudSettingsFocus.None) }
    var showCalibrate by remember { mutableStateOf(false) }
    var showLutImport by remember { mutableStateOf(false) }
    var showGlPreview by remember { mutableStateOf(false) }
    var showNativeDiagnostics by remember { mutableStateOf(false) }
    var showRootSettings by remember { mutableStateOf(false) }
    var showDebugMenu by remember { mutableStateOf(false) }
    var previewLaunchedFromDebug by remember { mutableStateOf(false) }

    val activity = context as? ComponentActivity
    val intentIncludeLogical = activity?.intent?.getBooleanExtra(EXTRA_PNS_INCLUDE_LOGICAL, false) ?: false
    val intentExhaustiveHfrOnly = activity?.intent?.getBooleanExtra(EXTRA_PNS_EXHAUSTIVE_HFR_ONLY, false) ?: false
    val effectiveIncludeLogical = exhaustiveIncludeLogical || intentIncludeLogical
    val effectiveExhaustiveHfrOnly = exhaustiveHfrOnly || intentExhaustiveHfrOnly

    var permissionEpoch by remember { mutableIntStateOf(0) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        permissionEpoch++
        hasCameraPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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

    var showPermissionWelcome by remember(launchScreen) {
        mutableStateOf(
            launchScreen == null &&
                !WelcomePrefs.hasCompletedPermissionOnboarding(context.applicationContext),
        )
    }

    if (showPermissionWelcome) {
        val hasRuntimePermission =
            remember(permissionEpoch) {
                { perm: String ->
                    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                }
            }
        WelcomePermissionsScreen(
            hasRuntimePermission = hasRuntimePermission,
            onRequestRuntimePermission = { perm -> requestPermission.launch(perm) },
            onOpenNotificationPolicySettings = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            },
            onFinished = {
                WelcomePrefs.markPermissionOnboardingComplete(context.applicationContext)
                showPermissionWelcome = false
            },
        )
        return
    }

    // Native diagnostics is the one launch screen that does not require
    // CAMERA permission (it inspects the .so / Kotlin facade only). Run a
    // separate effect for it so `--es pns_screen native` works on a fresh
    // install before the runtime permission dialog has been answered.
    LaunchedEffect(launchScreen) {
        if (launchScreen == SCREEN_NATIVE) {
            showNativeDiagnostics = true
        } else if (launchScreen == SCREEN_ROOT_SETTINGS) {
            showRootSettings = true
        }
    }

    LaunchedEffect(showAbout) {
        if (!showAbout) return@LaunchedEffect
        aboutLiveSummary =
            EncoderAttemptJsonAdapter.loadLatest(context)?.let { result ->
                EncoderResultAggregator.summarize(result.attempts)
            }
    }

    LaunchedEffect(hasCameraPermission, launchScreen) {
        if (!hasCameraPermission) return@LaunchedEffect
        if (launchScreen == PNS_SCREEN_PREVIEW) {
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
        } else if (launchScreen == SCREEN_GLPREVIEW) {
            showGlPreview = true
        } else if (launchScreen == SCREEN_NATIVE) {
            showNativeDiagnostics = true
        } else if (launchScreen == SCREEN_ROOT_SETTINGS) {
            showRootSettings = true
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            capabilityGateLines = emptyList()
            return@LaunchedEffect
        }
        val report = buildProbeReport(context)
        reportMd = report
        Log.i(TAG, "Probe built (${report.length} chars), ready to export.")
        cameraSummaries = report
            .lineSequence()
            .filter { it.startsWith("- Camera ") }
            .toList()

        capabilityGateLines = CapabilityGateBridge.uiLines(context)

        val inz = (context as? ComponentActivity)?.intent
        val suppressHugeMarkdownDump =
            inz != null &&
                inz.getStringExtra(EXTRA_PNS_SCREEN) == PNS_SCREEN_PREVIEW &&
                (
                    (inz.getIntExtra(EXTRA_PNS_PREVIEW_RAW_COUNT, 0) ?: 0) > 0 ||
                        inz.previewBracketExtra() != null ||
                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_DIAL).isNullOrBlank() ||
                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_IMAGING_PROFILE).isNullOrBlank() ||
                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_STILLS_LUT).isNullOrBlank() ||
                        (inz.getBooleanExtra(EXTRA_PNS_PREVIEW_M6_FPS_LUT_PROBE, false)) ||
                        (inz.getBooleanExtra(EXTRA_PNS_PREVIEW_CALIBRATE_GRAB_SMOKE, false)) ||
                        inz.hasExtra(EXTRA_PNS_PREVIEW_SELF_TIMER_SEC) ||
                        inz.action == MediaStore.ACTION_IMAGE_CAPTURE
                    )
        if (!suppressHugeMarkdownDump) {
            Log.i(TAG, "\n$report")
        }
    }

    if (showMapping) {
        DodgeMappingScreen(onBackToProbe = { showMapping = false })
        return
    }

    if (showPreviewEngine) {
        // Read intent extras every frame — `remember` cached stale 0 when preview opened without extras earlier in-process.
        val adbDial = activity?.intent.previewDialModeExtra()
        val adbRawCount = activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_RAW_COUNT, 0) ?: 0
        val adbBracket = activity?.intent.previewBracketExtra()
        val adbImagingProfile = activity?.intent.previewImagingProfileExtra()
        val adbCameraId = activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_CAMERA_ID)?.trim()?.takeIf { it.isNotBlank() }
        val adbSuperMacroProbe = activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SUPER_MACRO_PROBE, false) ?: false
        val adbPreviewStillsLutName = activity?.intent.previewStillsLutNameExtra()
        val adbM6FpsLutProbe = activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_M6_FPS_LUT_PROBE, false) ?: false
        val adbCalibrateGrabSmoke =
            activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_CALIBRATE_GRAB_SMOKE, false) ?: false
        val adbSelfTimerSec = activity?.intent.previewSelfTimerSecExtra()
        PreviewEngineScreen(
            onBack = {
                val ic = imageCaptureReturn
                if (ic != null) {
                    ic.host.setResult(android.app.Activity.RESULT_CANCELED)
                    ic.host.finish()
                } else {
                    showPreviewEngine = false
                    if (previewLaunchedFromDebug) {
                        showDebugMenu = true
                    }
                    previewLaunchedFromDebug = false
                }
            },
            onOpenDeveloperMenu = {
                showPreviewEngine = false
                showDebugMenu = true
            },
            onOpenHudSettings = { focus ->
                hudSettingsFocus = focus
                showHudSettings = true
                showPreviewEngine = false
            },
            startAutoSweep = autoSweep,
            adbInitialDial = adbDial,
            adbSequentialRawStills = adbRawCount,
            adbBracketPattern = adbBracket,
            adbInitialImagingProfile = adbImagingProfile,
            adbSeedCameraId = adbCameraId,
            adbSuperMacroProbe = adbSuperMacroProbe,
            adbPreviewStillsLutName = adbPreviewStillsLutName,
            adbM6FpsLutProbe = adbM6FpsLutProbe,
            adbCalibrateGrabSmoke = adbCalibrateGrabSmoke,
            adbInitialSelfTimerSec = adbSelfTimerSec,
            imageCaptureReturn = imageCaptureReturn,
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
        // BUILD_PLAN §2 Phase 0 V&V + §6: reload latest exhaustive_probe JSON each time About opens.
        AboutScreen(
            onBack = { showAbout = false },
            liveSummary = aboutLiveSummary,
        )
        return
    }

    if (showProHud) {
        ProHudScreen(onBack = { showProHud = false })
        return
    }

    if (showHudSettings) {
        HudSettingsScreen(
            onBack = {
                showHudSettings = false
                hudSettingsFocus = HudSettingsFocus.None
            },
            initialFocus = hudSettingsFocus,
        )
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

    if (showGlPreview) {
        GLPreviewScreen(onBack = { showGlPreview = false })
        return
    }

    if (showNativeDiagnostics) {
        NativeDiagnosticsScreen(onBack = { showNativeDiagnostics = false })
        return
    }

    if (showRootSettings) {
        RootSettingsScreen(onBack = { showRootSettings = false })
        return
    }

    if (launchScreen == null && showDebugMenu) {
        val insets = rememberSystemInsetsDp()
        DebugMenuScreen(
            padding = insets.asPaddingValues(extra = 16.dp),
            hasCameraPermission = hasCameraPermission,
            reportMdReady = reportMd.isNotBlank(),
            cameraSummaries = cameraSummaries,
            capabilityGateLines = capabilityGateLines,
            onBackToCamera = { showDebugMenu = false },
            onShowMapping = {
                showDebugMenu = false
                showMapping = true
            },
            onShowPreviewEngine = {
                showDebugMenu = false
                previewLaunchedFromDebug = true
                showPreviewEngine = true
            },
            onShowEncoderProbe = {
                showDebugMenu = false
                showEncoderProbe = true
            },
            onShowLegacyCamera1 = {
                showDebugMenu = false
                showLegacyCamera1 = true
            },
            onShowDeepCaps = {
                showDebugMenu = false
                showDeepCaps = true
            },
            onShowSessionMatrix = {
                showDebugMenu = false
                showSessionMatrix = true
            },
            onShowHdrDcgRuntime = {
                showDebugMenu = false
                showHdrDcgRuntime = true
            },
            onShowCaptureLatency = {
                showDebugMenu = false
                showCaptureLatency = true
            },
            onShowRawHdrExcl = {
                showDebugMenu = false
                showRawHdrExcl = true
            },
            onShowBurstProbe = {
                showDebugMenu = false
                showBurstProbe = true
            },
            onShowLogicalPhysical = {
                showDebugMenu = false
                showLogicalPhysical = true
            },
            onShowExhaustive = {
                showDebugMenu = false
                showExhaustive = true
            },
            onShowAbout = {
                showDebugMenu = false
                showAbout = true
            },
            onShowProHud = {
                showDebugMenu = false
                showProHud = true
            },
            onShowHudSettings = {
                showDebugMenu = false
                hudSettingsFocus = HudSettingsFocus.None
                showHudSettings = true
            },
            onShowCalibrate = {
                showDebugMenu = false
                showCalibrate = true
            },
            onShowLutImport = {
                showDebugMenu = false
                showLutImport = true
            },
            onShowGlPreview = {
                showDebugMenu = false
                showGlPreview = true
            },
            onShowNativeDiagnostics = {
                showDebugMenu = false
                showNativeDiagnostics = true
            },
            onShowRootSettings = {
                showDebugMenu = false
                showRootSettings = true
            },
            onDumpDiagnostics = {
                DiagnosticsMode.setEnabled(context, true)
                val path = DiagnosticsMode.dump(context)
                val msg = if (path != null) "Diagnostics written to $path" else "Diagnostics dump skipped (no external storage)"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            },
            onRequestPermission = { requestPermission.launch(Manifest.permission.CAMERA) },
            onResetPermissionWelcome = {
                WelcomePrefs.resetPermissionOnboardingForDebug(context.applicationContext)
                showDebugMenu = false
                showPermissionWelcome = true
            },
            onExport = {
                val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now())
                exportLauncher.launch("PROBE_RESULTS_$ts.md")
            },
        )
        return
    }

    if (launchScreen == null) {
        PreviewEngineScreen(
            onBack = { activity?.finish() },
            onOpenDeveloperMenu = { showDebugMenu = true },
            onOpenHudSettings = { focus ->
                hudSettingsFocus = focus
                showHudSettings = true
            },
            startAutoSweep = autoSweep,
        )
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
        onShowHudSettings = {
            hudSettingsFocus = HudSettingsFocus.None
            showHudSettings = true
        },
        onShowCalibrate = { showCalibrate = true },
        onShowLutImport = { showLutImport = true },
        onShowGlPreview = { showGlPreview = true },
        onShowNativeDiagnostics = { showNativeDiagnostics = true },
        onShowRootSettings = { showRootSettings = true },
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
    onShowGlPreview: () -> Unit,
    onShowNativeDiagnostics: () -> Unit,
    onShowRootSettings: () -> Unit,
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
            OutlinedButton(onClick = onShowGlPreview) { Text("Live preview LUT") }
            OutlinedButton(onClick = onShowNativeDiagnostics) { Text("Native diagnostics") }
            OutlinedButton(onClick = onShowRootSettings) { Text("Root Only") }
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

private fun Intent?.previewDialModeExtra(): CommandDialMode? {
    val s = this?.getStringExtra(EXTRA_PNS_PREVIEW_DIAL) ?: return null
    return when (s.trim().uppercase()) {
        "A", "AUTO" -> CommandDialMode.Auto
        "M" -> CommandDialMode.M
        "H" -> CommandDialMode.H
        "S" -> CommandDialMode.S
        "BKT" -> CommandDialMode.BKT
        else -> null
    }
}

private fun Intent?.previewBracketExtra(): BracketPattern? {
    val s = this?.getStringExtra(EXTRA_PNS_PREVIEW_BRACKET) ?: return null
    return when (s.trim()) {
        "3" -> BracketPattern.Three
        "5" -> BracketPattern.Five
        "7" -> BracketPattern.Seven
        else -> null
    }
}

private fun Intent?.previewImagingProfileExtra(): ImagingProfile? {
    val s = this?.getStringExtra(EXTRA_PNS_PREVIEW_IMAGING_PROFILE)?.trim()?.lowercase() ?: return null
    return when (s) {
        ImagingProfile.StandardPro.id -> ImagingProfile.StandardPro
        ImagingProfile.UltraMax.id -> ImagingProfile.UltraMax
        else -> null
    }
}

private fun Intent?.previewStillsLutNameExtra(): String? =
    this?.getStringExtra(EXTRA_PNS_PREVIEW_STILLS_LUT)?.trim()?.takeIf { it.isNotBlank() }

private fun Intent?.previewSelfTimerSecExtra(): Int? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_SELF_TIMER_SEC)) {
        getIntExtra(EXTRA_PNS_PREVIEW_SELF_TIMER_SEC, 0)
    } else {
        null
    }

