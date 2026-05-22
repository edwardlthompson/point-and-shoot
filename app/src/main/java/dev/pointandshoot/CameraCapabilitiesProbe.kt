package dev.pointandshoot

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.os.SystemClock
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject
import android.hardware.camera2.CameraMetadata
import dev.pointandshoot.fleet.FleetCameraProfileBuilder
import dev.pointandshoot.fleet.FleetCameraProfileStore
import dev.pointandshoot.fleet.FleetCameraProfiles

private const val TAG = "PNS.Probe"
/** Single-line shallow metadata summary for ADB gates (`scripts/pns_shallow_scan_hub_validate.ps1`). */
private const val TAG_PROBE_HUB = "PNS.ProbeHub"

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
/** With [PNS_SCREEN_FACE_METER]: write `face_meter_probe_*.{md,json}` then finish (headless automation). */
const val EXTRA_PNS_AUTOFACEMETER = "pns_autofacemeter"
/**
 * Optional extras when `--es pns_screen preview` — drive dial / burst validation from ADB
 * (see `scripts/pns_adb_preview_validate.ps1`).
 */
const val EXTRA_PNS_PREVIEW_DIAL = "pns_preview_dial"
const val EXTRA_PNS_PREVIEW_RAW_COUNT = "pns_preview_raw_count"
/** When true with [EXTRA_PNS_PREVIEW_RAW_COUNT] > 0: shorter ADB settle/poll (dev smoke only). */
const val EXTRA_PNS_PREVIEW_RAW_STILL_FAST = "pns_preview_raw_still_fast"
const val EXTRA_PNS_PREVIEW_BRACKET = "pns_preview_bracket"
/** `standard_pro` or `ultra_max` — seeds [ImagingProfile] for scripted preview capture (Sprint 4.3 RAW12). */
const val EXTRA_PNS_PREVIEW_IMAGING_PROFILE = "pns_preview_imaging_profile"

/** When true, cold start persists **HDR / 10-bit live preview** on before preview session create (M10 automation). */
const val EXTRA_PNS_PREVIEW_HDR10_LIVE_PREVIEW = "pns_preview_hdr10_live_preview"
/**
 * Optional RAW stream pick for preview session + still path (`default`, `raw_sensor_first`,
 * `raw12_only`, `raw_sensor_only`, `raw10_only`). See `RawStreamPreference` in `RawCaptureSupport.kt`.
 */
const val EXTRA_PNS_PREVIEW_RAW_STREAM = "pns_preview_raw_stream"
/**
 * When present (`--ez`), seeds [PreviewChromePreferences.stillCaptureJpegCompanion] for this process
 * only (session-only; does not write prefs disk). Matrix testing for RAW+ vs RAW-only.
 */
const val EXTRA_PNS_PREVIEW_JPEG_COMPANION = "pns_preview_jpeg_companion"

/** Matrix bisect: skip [StillCaptureMetadata.applyToDngUri] after DNG save. */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_STILL_METADATA = "pns_preview_dng_skip_still_metadata"

/**
 * Override still DNG backend on OP13: `framework_proshot` | `motioncam_inspired`.
 * See [DngSaveBisectState] and `docs/DNG_PIPELINE_TRIANGULATION_MATRIX.md`.
 */
const val EXTRA_PNS_PREVIEW_STILL_DNG_BACKEND = "pns_preview_still_dng_backend"

/** Sprint 13.8: `standard` | `zsl` | `hdr` — seeds [StillCaptureMode] for automation. */
const val EXTRA_PNS_PREVIEW_STILL_MODE = "pns_preview_still_mode"

/** Skip DNG tag 50708 ([Dng12Saver] unique camera model buffer rewrite). */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_UNIQUE_CAMERA_MODEL = "pns_preview_dng_skip_unique_camera_model"

/** Skip [PreviewJpegProcessingHints] on RAW still capture request. */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_JPEG_HINTS_STILL = "pns_preview_dng_skip_jpeg_hints_still"

/** Force [LeafDngHalReconcile] ASN patch on leaf rear ids (true/false). */
const val EXTRA_PNS_PREVIEW_DNG_FORCE_LEAF_RECONCILE = "pns_preview_dng_force_leaf_reconcile"

/** Leaf reconcile: use Bayer ASN (legacy) instead of OP13 gains-first. */
const val EXTRA_PNS_PREVIEW_DNG_FORCE_BAYER_ASN = "pns_preview_dng_force_bayer_asn"

/** Skip [DngCreator.setDescription] / LUT software line on DNG save. */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_SOFTWARE_DESC = "pns_preview_dng_skip_software_desc"
/** When true: one [PreviewController.captureComposedStill] after preview ready (IMG matrix path). */
const val EXTRA_PNS_PREVIEW_COMPOSED_STILL = "pns_preview_composed_still"
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

/**
 * Optional **`--es pns_preview_focal_mm_slot N`** with [PNS_SCREEN_PREVIEW]: after default **M23** seed,
 * applies the focal-length chip matching [FocalMmSlot.labelMm] (`14`…`150`) once and logs
 * **`PNS.ChromeUx`** **`focalSlotTap=…`** (Milestone **9.13** tele-preset ADB proof).
 */
const val EXTRA_PNS_PREVIEW_FOCAL_MM_SLOT = "pns_preview_focal_mm_slot"

/**
 * Optional **`--ei pns_preview_readout_iso N`** with [PNS_SCREEN_PREVIEW]: after preview session is ready,
 * locks readout ISO (Sprint **14.7** gate) and logs **`PNS.ChromeUx readoutAeApplied`**.
 */
const val EXTRA_PNS_PREVIEW_READOUT_ISO = "pns_preview_readout_iso"

/**
 * Optional **`--ez pns_preview_primary_photo false`** with [PNS_SCREEN_PREVIEW]: cold-start **video-primary**
 * tray (vs photo-primary default).
 */
const val EXTRA_PNS_PREVIEW_PRIMARY_PHOTO = "pns_preview_primary_photo"

/**
 * Debug APK + [PNS_SCREEN_PREVIEW]: after preview settles, record **N** seconds via in-app
 * [android.media.MediaRecorder] automation (**`scripts/pns_in_app_video_verify.ps1`**). Values clamp to **[0, 120]**.
 */
const val EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC = "pns_preview_automation_in_app_video_sec"

/** Sprint **14.11** gate: open in-preview [AboutScreen] overlay on cold preview (`pns_about_links_verify.ps1`). */
const val EXTRA_PNS_PREVIEW_SHOW_ABOUT = "pns_preview_show_about"

/**
 * Debug APK + [PNS_SCREEN_PREVIEW]: target FPS for in-app video automation.
 * When > 60 the [MediaCodecVideoRecorder] path is used (bypasses `ro.media.recorder-max-base-layer-fps=60`).
 * Defaults to 120 when [EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC] > 0 and this extra is absent.
 *
 * Typical ADB: `--ei pns_preview_video_fps 120`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_FPS = "pns_preview_video_fps"

/**
 * Debug APK + [PNS_SCREEN_PREVIEW]: chrome in-app video encode width for automation (**13V.16**).
 * Typical ADB: `--ei pns_preview_video_encode_w 3840`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_ENCODE_W = "pns_preview_video_encode_w"

/**
 * Debug APK + [PNS_SCREEN_PREVIEW]: chrome in-app video encode height for automation (**13V.16**).
 * Typical ADB: `--ei pns_preview_video_encode_h 2160`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_ENCODE_H = "pns_preview_video_encode_h"

/**
 * Debug APK + [PNS_SCREEN_PREVIEW]: when `true`, in-app video automation uses HEVC Main10
 * (10-bit) profile via [MediaCodecVideoRecorder] (`c2.qti.hevc.encoder`).
 *
 * Typical ADB: `--ez pns_preview_video_10bit true`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_TENBIT = "pns_preview_video_10bit"

/**
 * Sprint 13.5: Debug APK + [PNS_SCREEN_PREVIEW]: when `true`, in-app video automation records
 * DCG (HEVC Main10HDR10 + `isHdr10=true` + `KEY_HDR_STATIC_INFO` SEI metadata) via
 * [MediaCodecVideoRecorder]. Used by `pns_video_hdr10_metadata_verify.ps1`.
 *
 * Typical ADB: `--ez pns_preview_video_dcg true`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_DCG = "pns_preview_video_dcg"

/**
 * Sprint **13.6**: scripted RAW video record (**`scripts/pns_raw_video_verify.ps1`**).
 * Clamped to **[0, 120]** seconds; uses [RawVideoRecordingController] (no MediaRecorder).
 */
const val EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC = "pns_preview_video_raw_sec"

/**
 * Sprint **13V.10**: seed HUD focus-peaking color for ADB gates (`Red`, `Green`, …).
 * Typical ADB: `--es pns_preview_focus_peaking Red`
 */
const val EXTRA_PNS_PREVIEW_FOCUS_PEAKING = "pns_preview_focus_peaking"

/**
 * Sprint **14.8**: seed preview focus program (`auto`, `manual`, `caf_video`, …).
 * Typical ADB: `--es pns_preview_focus_mode manual` with dial **M** for peaking gates.
 */
const val EXTRA_PNS_PREVIEW_FOCUS_MODE = "pns_preview_focus_mode"

/**
 * Sprint **13V.11**: seed [HudSettings.selectedLutForVideo] for GLES preview during video
 * (`PnsCinematic`, `BwBt709`, …). Typical ADB: `--es pns_preview_video_lut PnsCinematic`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_LUT = "pns_preview_video_lut"

/**
 * Sprint **13V.12**: force power + thermal HUD on preview (`--ez pns_preview_force_power_thermal true`).
 */
const val EXTRA_PNS_PREVIEW_FORCE_POWER_THERMAL = "pns_preview_force_power_thermal"

/**
 * Sprint **PO.2** gate: override battery % for [PreviewAdaptiveFpsPolicy] (`--ei pns_preview_adaptive_battery_pct 15`).
 */
const val EXTRA_PNS_PREVIEW_ADAPTIVE_BATTERY_PCT = "pns_preview_adaptive_battery_pct"

/**
 * Sprint **PO.2** gate: override thermal status for [PreviewAdaptiveFpsPolicy]
 * (`--ei pns_preview_adaptive_thermal_status 3` = [android.os.PowerManager.THERMAL_STATUS_SEVERE]).
 */
const val EXTRA_PNS_PREVIEW_ADAPTIVE_THERMAL_STATUS = "pns_preview_adaptive_thermal_status"

/**
 * Sprint **13V.13**: override free bytes for storage-remaining math (`--ei pns_preview_storage_available_bytes N`).
 */
const val EXTRA_PNS_PREVIEW_STORAGE_AVAILABLE_BYTES = "pns_preview_storage_available_bytes"

/**
 * Sprint **13V.17**: enable smile-triggered still (`--ez pns_preview_smile_still true`).
 */
const val EXTRA_PNS_PREVIEW_SMILE_STILL = "pns_preview_smile_still"

/**
 * Sprint **13V.17** gate only: fire one tray still after preview session settle (no ML Kit face required).
 * Requires [EXTRA_PNS_PREVIEW_SMILE_STILL]. Typical: `--ez pns_preview_smile_still_synthetic true`.
 */
const val EXTRA_PNS_PREVIEW_SMILE_STILL_SYNTHETIC = "pns_preview_smile_still_synthetic"

/**
 * Sprint **13V.17**: seed HUD video bitrate scale **50–150** (`--ei pns_preview_video_bitrate_scale 125`).
 */
const val EXTRA_PNS_PREVIEW_VIDEO_BITRATE_SCALE = "pns_preview_video_bitrate_scale"

/**
 * Sprint **13V.17**: enable scene vendor hint logging toggle (`--ez pns_preview_scene_vendor_hints true`).
 */
const val EXTRA_PNS_PREVIEW_SCENE_VENDOR_HINTS = "pns_preview_scene_vendor_hints"

/**
 * When true, after the full markdown probe is built (requires `CAMERA` grant), writes
 * [PROBE_EXPORT_LATEST_FILE] under the app's **files** dir. Host pull (debuggable):
 * `adb exec-out run-as dev.pointandshoot cat files/PROBE_EXPORT_LATEST.md`.
 * Logged as **`PNS.ProbeExport`** with the absolute path.
 *
 * Typical ADB: `--es pns_screen probehub --ez pns_auto_export_probe true` (see `scripts/pns_ae_highlight_probe_adb.ps1`).
 */
const val EXTRA_PNS_AUTO_EXPORT_PROBE = "pns_auto_export_probe"

/** Stable filename written when [EXTRA_PNS_AUTO_EXPORT_PROBE] is true. */
const val PROBE_EXPORT_LATEST_FILE = "PROBE_EXPORT_LATEST.md"

/** Value for [EXTRA_PNS_SCREEN] and system camera intents — opens [PreviewEngineScreen]. */
const val PNS_SCREEN_PREVIEW = "preview"
/** Value for [EXTRA_PNS_SCREEN] — opens [FaceMeterProbeScreen] (face / eye / AE-AF static probe). */
const val PNS_SCREEN_FACE_METER = "facemeter"

/** Value for [EXTRA_PNS_SCREEN] — headless **`CameraDevice.createExtensionSession`** smoke (Milestone 4). */
const val PNS_SCREEN_CAMERA_EXT_SMOKE = "cameraextsmoke"

/**
 * With **`pns_screen=rootsettings`**: after persisted **Grant Su** ([`RootCapability.RootState.Granted`]),
 * run read-only [`RootPrivilegedDiagnostics.runScan`] once and log **`PNS.AdbValidation`** **`rootPrivScan …`**
 * (Milestone **7.5** automation).
 */
const val EXTRA_PNS_AUTO_ROOT_DIAGNOSTICS = "pns_auto_root_diagnostics"

/** Value for [EXTRA_PNS_SCREEN] — opens the engineering hub ([DebugMenuScreen]) without a sub-probe route. */
const val PNS_SCREEN_PROBE_HUB = "probehub"

/** Value for [EXTRA_PNS_SCREEN] — CameraX preview + YUV [ImageAnalysis] QR / barcode scan (ZXing). */
const val PNS_SCREEN_QR_SCAN = "qrscan"

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
    videoCaptureReturn: VideoCaptureReturnContract? = null,
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
    autoFaceMeterProbe: Boolean = false,
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var reportMd by remember { mutableStateOf("") }
    var cameraSummaries by remember { mutableStateOf(listOf<String>()) }
    var shallowScanHubLine by remember { mutableStateOf<String?>(null) }
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
    var showCameraExtSmoke by remember { mutableStateOf(false) }
    var showLogicalPhysical by remember { mutableStateOf(false) }
    var showExhaustive by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var aboutOpenedFromPreview by remember { mutableStateOf(false) }
    var aboutLiveSummary by remember { mutableStateOf<EncoderSummary?>(null) }
    var aboutHalHfrMaxByCameraId by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) }
    var showProHud by remember { mutableStateOf(false) }
    var showHudSettings by remember { mutableStateOf(false) }
    var hudSettingsFocus by remember { mutableStateOf(HudSettingsFocus.None) }
    var showCalibrate by remember { mutableStateOf(false) }
    var showLutImport by remember { mutableStateOf(false) }
    var showGlPreview by remember { mutableStateOf(false) }
    var showNativeDiagnostics by remember { mutableStateOf(false) }
    var showRootSettings by remember { mutableStateOf(false) }
    var showFaceMeterProbe by remember { mutableStateOf(false) }
    var showQrScan by remember { mutableStateOf(false) }
    var showDebugMenu by remember { mutableStateOf(false) }
    var previewLaunchedFromDebug by remember { mutableStateOf(false) }
    var probeHubNavEpoch by remember { mutableIntStateOf(0) }

    var shallowRescanSeq by remember {
        mutableLongStateOf(ShallowCapabilityCacheStore.readRescanSeq(context.applicationContext))
    }
    val appCtx = context.applicationContext
    DisposableEffect(appCtx) {
        val prefs = ShallowCapabilityCacheStore.prefs(appCtx)
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == ShallowCapabilityCacheStore.KEY_RESCAN_SEQ) {
                    shallowRescanSeq = prefs.getLong(ShallowCapabilityCacheStore.KEY_RESCAN_SEQ, 0L)
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val activity = context as? ComponentActivity
    val intentIncludeLogical = activity?.intent?.getBooleanExtra(EXTRA_PNS_INCLUDE_LOGICAL, false) ?: false
    val intentExhaustiveHfrOnly = activity?.intent?.getBooleanExtra(EXTRA_PNS_EXHAUSTIVE_HFR_ONLY, false) ?: false
    val effectiveIncludeLogical = exhaustiveIncludeLogical || intentIncludeLogical
    val effectiveExhaustiveHfrOnly = exhaustiveHfrOnly || intentExhaustiveHfrOnly

    fun navigateProbeFromTitle(title: String, dismissDebugMenu: Boolean) {
        if (dismissDebugMenu) showDebugMenu = false
        when (title) {
            "Dodge lens mapping" -> showMapping = true
            "Live preview (engine)" -> {
                previewLaunchedFromDebug = true
                activity?.intent?.removeExtra(EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC)
                activity?.intent?.removeExtra(EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC)
                showPreviewEngine = true
            }
            "QR / barcode scan" -> showQrScan = true
            "Legacy Camera1 probe" -> showLegacyCamera1 = true
            "Deep capabilities" -> showDeepCaps = true
            "Face / eye / metering probe" -> showFaceMeterProbe = true
            "Session configuration matrix" -> showSessionMatrix = true
            "HDR / dynamic range runtime" -> showHdrDcgRuntime = true
            "Logical vs physical" -> showLogicalPhysical = true
            "Exhaustive encoder / media matrix" -> showExhaustive = true
            "HFR encoder probe" -> showEncoderProbe = true
            "Capture latency" -> showCaptureLatency = true
            "RAW vs HDR exclusivity" -> showRawHdrExcl = true
            "Burst probe" -> showBurstProbe = true
            "Calibrate" -> showCalibrate = true
            "Import LUT" -> showLutImport = true
            "Live GL LUT preview" -> showGlPreview = true
            "Pro HUD (mock)" -> showProHud = true
            "HUD settings" -> {
                hudSettingsFocus = HudSettingsFocus.None
                showHudSettings = true
            }
            "About / heritage" -> showAbout = true
            "Native diagnostics" -> showNativeDiagnostics = true
            "Root-only enhancements" -> showRootSettings = true
            "Diagnostics dump (quick)" -> {
                DiagnosticsMode.setEnabled(context, true)
                val path = DiagnosticsMode.dump(context)
                val msg =
                    if (path != null) {
                        "Diagnostics written to $path"
                    } else {
                        "Diagnostics dump skipped (no external storage)"
                    }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            else -> Log.w(TAG_PROBE_HUB, "Unknown probe hub entry title=$title")
        }
    }

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
            val os =
                context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("Could not open export destination")
            os.use { it.write(reportMd.toByteArray(Charsets.UTF_8)) }
        }.onFailure { e ->
            Log.e(TAG, "Export failed", e)
            Toast.makeText(
                context,
                "Could not save probe export — try another folder or free space.",
                Toast.LENGTH_LONG,
            ).show()
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

    LaunchedEffect(showAbout, hasCameraPermission) {
        if (!showAbout) return@LaunchedEffect
        aboutLiveSummary =
            EncoderAttemptJsonAdapter.loadLatest(context)?.let { result ->
                EncoderResultAggregator.summarize(result.attempts)
            }
        aboutHalHfrMaxByCameraId =
            if (hasCameraPermission) {
                DeviceCameraCapabilityCache.halHighSpeedMaxFpsByCameraId(context.applicationContext)
            } else {
                emptyMap()
            }
    }

    LaunchedEffect(hasCameraPermission, launchScreen) {
        if (launchScreen == PNS_SCREEN_FACE_METER) {
            showFaceMeterProbe = true
            return@LaunchedEffect
        }
        if (launchScreen == PNS_SCREEN_QR_SCAN) {
            showQrScan = true
            return@LaunchedEffect
        }
        if (!hasCameraPermission) return@LaunchedEffect
        if (launchScreen == PNS_SCREEN_PREVIEW) {
            // Cold ADB `pns_screen=preview` — intent automation extras are intentional for this entry.
            previewLaunchedFromDebug = false
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
        } else if (launchScreen == PNS_SCREEN_CAMERA_EXT_SMOKE) {
            showCameraExtSmoke = true
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

    LaunchedEffect(hasCameraPermission, launchScreen, shallowRescanSeq) {
        if (launchScreen == PNS_SCREEN_FACE_METER || launchScreen == PNS_SCREEN_QR_SCAN) {
            return@LaunchedEffect
        }
        if (!hasCameraPermission) {
            capabilityGateLines = emptyList()
            shallowScanHubLine = null
            return@LaunchedEffect
        }
        val t0 = SystemClock.elapsedRealtime()
        val report =
            withContext(Dispatchers.Default) {
                buildProbeReport(context, scanBudgetMs = 4000L)
            }
        val elapsedMs = SystemClock.elapsedRealtime() - t0
        reportMd = report
        Log.i(TAG, "Probe built (${report.length} chars), ready to export.")
        cameraSummaries = report
            .lineSequence()
            .filter { it.startsWith("- Camera ") }
            .toList()
        val camCount =
            Regex("^## Cameras \\((\\d+)\\)", RegexOption.MULTILINE)
                .find(report)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: cameraSummaries.size
        val degraded = report.contains("degraded=true")
        val disk = if (ShallowCapabilityCacheStore.loadValidCachedRoot(context) != null) " diskCache=ok" else ""
        shallowScanHubLine = "Shallow scan: ${elapsedMs}ms cameras=$camCount degraded=$degraded$disk"
        Log.i(TAG_PROBE_HUB, shallowScanHubLine ?: "")

        capabilityGateLines = CapabilityGateBridge.uiLines(context)

        val inz = (context as? ComponentActivity)?.intent
        if (inz?.getBooleanExtra(EXTRA_PNS_AUTO_EXPORT_PROBE, false) == true) {
            runCatching {
                val f = File(context.filesDir, PROBE_EXPORT_LATEST_FILE)
                f.writeText(report)
                Log.i("PNS.ProbeExport", "path=${f.absolutePath} bytes=${f.length()}")
            }.onFailure { e ->
                Log.e(TAG, "auto export probe failed", e)
            }
        }
        val suppressHugeMarkdownDump =
            inz != null &&
                (
                    inz.getStringExtra(EXTRA_PNS_SCREEN) == PNS_SCREEN_FACE_METER ||
                        inz.getStringExtra(EXTRA_PNS_SCREEN) == PNS_SCREEN_QR_SCAN ||
                        inz.getStringExtra(EXTRA_PNS_SCREEN) == PNS_SCREEN_CAMERA_EXT_SMOKE ||
                        (
                            inz.getStringExtra(EXTRA_PNS_SCREEN) == PNS_SCREEN_PREVIEW &&
                                (
                                    (inz.getIntExtra(EXTRA_PNS_PREVIEW_RAW_COUNT, 0) ?: 0) > 0 ||
                                        inz.previewBracketExtra() != null ||
                                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_DIAL).isNullOrBlank() ||
                                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_IMAGING_PROFILE).isNullOrBlank() ||
                                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_RAW_STREAM).isNullOrBlank() ||
                                        inz.hasExtra(EXTRA_PNS_PREVIEW_JPEG_COMPANION) ||
                                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_STILLS_LUT).isNullOrBlank() ||
                                        (inz.getBooleanExtra(EXTRA_PNS_PREVIEW_M6_FPS_LUT_PROBE, false)) ||
                                        (inz.getBooleanExtra(EXTRA_PNS_PREVIEW_CALIBRATE_GRAB_SMOKE, false)) ||
                                        inz.hasExtra(EXTRA_PNS_PREVIEW_SELF_TIMER_SEC) ||
                                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_FOCAL_MM_SLOT).isNullOrBlank() ||
                                        inz.hasExtra(EXTRA_PNS_PREVIEW_READOUT_ISO) ||
                                        inz.hasExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO) ||
                                        ((inz.getIntExtra(EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC, 0) ?: 0) > 0) ||
                                        inz.hasExtra(EXTRA_PNS_PREVIEW_HDR10_LIVE_PREVIEW) ||
                                        inz.action == MediaStore.ACTION_IMAGE_CAPTURE ||
                                        inz.action == MediaStore.ACTION_VIDEO_CAPTURE
                                )
                            )
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
        val adbDialRaw = activity?.intent.previewDialModeExtra()
        val adbRawCountRaw = activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_RAW_COUNT, 0) ?: 0
        val adbRawStillFastRaw = activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_RAW_STILL_FAST, false) ?: false
        val adbBracketRaw = activity?.intent.previewBracketExtra()
        val adbImagingProfileRaw = activity?.intent.previewImagingProfileExtra()
        val adbRawStreamFromIntent = activity?.intent.previewRawStreamPreferenceExtra()
        val adbJpegCompanionFromIntent = activity?.intent.previewJpegCompanionSeedExtra()
        val adbCameraIdRaw = activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_CAMERA_ID)?.trim()?.takeIf { it.isNotBlank() }
        val adbPreviewStillsLutName = activity?.intent.previewStillsLutNameExtra()
        val adbM6FpsLutProbe = activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_M6_FPS_LUT_PROBE, false) ?: false
        val adbCalibrateGrabSmoke =
            activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_CALIBRATE_GRAB_SMOKE, false) ?: false
        val adbSelfTimerSec = activity?.intent.previewSelfTimerSecExtra()
        val adbFocalMmSlotProbe = activity?.intent.previewFocalMmSlotExtra()
        val adbReadoutIsoProbe = activity?.intent.previewReadoutIsoProbeExtra()
        val intentPrimaryPhotoSeed =
            activity?.intent?.takeIf { it.hasExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO) }
                ?.getBooleanExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO, true)
        val previewSeedPrimaryPhoto =
            intentPrimaryPhotoSeed ?: (
                activity?.intent?.action != MediaStore.INTENT_ACTION_VIDEO_CAMERA &&
                    activity?.intent?.action != MediaStore.ACTION_VIDEO_CAPTURE
            )
        /**
         * Sticky `Intent` problem: ADB / Studio may leave `pns_preview_raw_count`, `pns_preview_raw_stream`,
         * `pns_preview_jpeg_companion`, etc. on [ComponentActivity.getIntent]. Re-reading those every
         * composition is correct for dedicated `pns_screen=preview` runs, but opening the live preview
         * from the engineering hub must not replay automation or force RAW stream overrides from an
         * earlier shell session.
         *
         * After visiting the hub, [previewLaunchedFromDebug] is true. If [launchScreen] is still
         * [PNS_SCREEN_PREVIEW], the user is continuing the same cold ADB preview route — keep honoring
         * intent. If [launchScreen] is anything else (or null), ignore sticky preview automation extras.
         */
        val trustIntentForPreviewPipeline =
            !previewLaunchedFromDebug || launchScreen == PNS_SCREEN_PREVIEW
        val adbComposedStillSmoke =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_COMPOSED_STILL, false) ?: false
            } else {
                false
            }
        val intentInAppVideoAutomationSec =
            activity?.intent?.takeIf { it.hasExtra(EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC) }
                ?.getIntExtra(EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC, 0)
                ?.coerceIn(0, 120) ?: 0
        val intentRawVideoAutomationSec =
            activity?.intent?.takeIf { it.hasExtra(EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC) }
                ?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC, 0)
                ?.coerceIn(0, 120) ?: 0
        val automationWantsIntentPipeline =
            adbRawCountRaw > 0 ||
                adbBracketRaw != null ||
                launchScreen == PNS_SCREEN_PREVIEW ||
                !(activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_DIAL).isNullOrBlank()) ||
                (launchScreen == PNS_SCREEN_PREVIEW && intentInAppVideoAutomationSec > 0) ||
                (launchScreen == PNS_SCREEN_PREVIEW && intentRawVideoAutomationSec > 0) ||
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SMILE_STILL, false) == true ||
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_BITRATE_SCALE, 0) ?: 0) > 0
        val useIntentAutomationPipeline = trustIntentForPreviewPipeline && automationWantsIntentPipeline
        val adbAutomationVideoRawSec =
            if (trustIntentForPreviewPipeline && launchScreen == PNS_SCREEN_PREVIEW) {
                intentRawVideoAutomationSec
            } else {
                0
            }
        val adbAutomationInAppVideoSec =
            if (trustIntentForPreviewPipeline && launchScreen == PNS_SCREEN_PREVIEW) {
                intentInAppVideoAutomationSec
            } else {
                0
            }
        val adbAutomationVideoFps =
            if (trustIntentForPreviewPipeline) {
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_FPS, 0) ?: 0).let { if (it > 0) it else null }
            } else {
                null
            }
        val adbAutomationVideoEncodeW =
            if (trustIntentForPreviewPipeline) {
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_ENCODE_W, 0) ?: 0).let { if (it > 0) it else null }
            } else {
                null
            }
        val adbAutomationVideoEncodeH =
            if (trustIntentForPreviewPipeline) {
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_ENCODE_H, 0) ?: 0).let { if (it > 0) it else null }
            } else {
                null
            }
        val adbAutomationVideoTenBit =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_VIDEO_TENBIT, false) ?: false
            } else {
                false
            }
        val adbAutomationVideoDcg =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_VIDEO_DCG, false) ?: false
            } else {
                false
            }
        val adbDngBisectActive =
            if (trustIntentForPreviewPipeline) {
                DngSaveBisectState.applyFromPreviewIntent(activity?.intent)
            } else {
                DngSaveBisectState.reset()
                false
            }
        val adbStillCaptureMode =
            if (trustIntentForPreviewPipeline) {
                StillCaptureModePolicy.parseAdbExtra(
                    activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_STILL_MODE),
                )
            } else {
                null
            }
        val adbSeedFocusPeakingColor =
            if (trustIntentForPreviewPipeline) {
                activity?.intent.previewFocusPeakingColorExtra()
            } else {
                null
            }
        val adbPreviewFocusMode =
            if (trustIntentForPreviewPipeline) {
                activity?.intent.previewFocusModeExtra()
            } else {
                null
            }
        val adbSeedVideoLutName =
            if (trustIntentForPreviewPipeline) {
                activity?.intent.previewVideoLutNameExtra()
            } else {
                null
            }
        val adbForcePowerThermalOverlay =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_FORCE_POWER_THERMAL, false) ?: false
            } else {
                false
            }
        val adbAdaptiveBatteryPctOverride =
            if (trustIntentForPreviewPipeline) {
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_ADAPTIVE_BATTERY_PCT, -1) ?: -1)
                    .takeIf { it in 0..100 }
            } else {
                null
            }
        val adbAdaptiveThermalStatusOverride =
            if (trustIntentForPreviewPipeline) {
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_ADAPTIVE_THERMAL_STATUS, -1) ?: -1)
                    .takeIf { it >= 0 }
            } else {
                null
            }
        val adbStorageAvailableBytes =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getLongExtra(EXTRA_PNS_PREVIEW_STORAGE_AVAILABLE_BYTES, -1L)?.let { v ->
                    if (v >= 0L) v else null
                }
            } else {
                null
            }
        val adbEnableSmileStill =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SMILE_STILL, false) ?: false
            } else {
                false
            }
        val adbSmileStillSynthetic =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SMILE_STILL_SYNTHETIC, false) ?: false
            } else {
                false
            }
        val adbVideoBitrateScalePercent =
            if (trustIntentForPreviewPipeline) {
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_BITRATE_SCALE, 0) ?: 0)
                    .takeIf { it in HudSettings.VIDEO_BITRATE_SCALE_MIN..HudSettings.VIDEO_BITRATE_SCALE_MAX }
            } else {
                null
            }
        val adbSceneVendorHints =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SCENE_VENDOR_HINTS, false) ?: false
            } else {
                false
            }
        val adbShowAboutOverlay =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SHOW_ABOUT, false) ?: false
            } else {
                false
            }
        val adbSequentialRawStills = if (trustIntentForPreviewPipeline) adbRawCountRaw else 0
        val adbBracketPattern = if (trustIntentForPreviewPipeline) adbBracketRaw else null
        val adbRawStreamPreference = if (useIntentAutomationPipeline) adbRawStreamFromIntent else null
        val adbJpegCompanionSeed = if (useIntentAutomationPipeline) adbJpegCompanionFromIntent else null
        val adbRawStillFastAutomation = if (trustIntentForPreviewPipeline) adbRawStillFastRaw else false
        val adbInitialDial = if (trustIntentForPreviewPipeline) adbDialRaw else null
        val adbInitialImagingProfile = if (trustIntentForPreviewPipeline) adbImagingProfileRaw else null
        val adbSeedCameraId = if (trustIntentForPreviewPipeline) adbCameraIdRaw else null
        val adbSuperMacroProbe =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SUPER_MACRO_PROBE, false) ?: false
            } else {
                false
            }
        PreviewEngineScreen(
            onBack = {
                when {
                    imageCaptureReturn != null -> {
                        imageCaptureReturn.host.setResult(android.app.Activity.RESULT_CANCELED)
                        imageCaptureReturn.host.finish()
                    }
                    videoCaptureReturn != null -> {
                        videoCaptureReturn.host.setResult(android.app.Activity.RESULT_CANCELED)
                        videoCaptureReturn.host.finish()
                    }
                    else -> {
                        showPreviewEngine = false
                        if (previewLaunchedFromDebug) {
                            showDebugMenu = true
                        }
                        previewLaunchedFromDebug = false
                    }
                }
            },
            onOpenDeveloperMenu = {
                showPreviewEngine = false
                showDebugMenu = true
            },
            startAutoSweep = autoSweep,
            adbInitialDial = adbInitialDial,
            adbSequentialRawStills = adbSequentialRawStills,
            adbRawStillFastAutomation = adbRawStillFastAutomation,
            adbBracketPattern = adbBracketPattern,
            adbInitialImagingProfile = adbInitialImagingProfile,
            adbRawStreamPreference = adbRawStreamPreference,
            adbJpegCompanionSeed = adbJpegCompanionSeed,
            adbComposedStillSmoke = adbComposedStillSmoke,
            adbSeedCameraId = adbSeedCameraId,
            adbSuperMacroProbe = adbSuperMacroProbe,
            adbPreviewStillsLutName = adbPreviewStillsLutName,
            adbM6FpsLutProbe = adbM6FpsLutProbe,
            adbCalibrateGrabSmoke = adbCalibrateGrabSmoke,
            adbInitialSelfTimerSec = adbSelfTimerSec,
            adbFocalMmSlotProbe = adbFocalMmSlotProbe,
            imageCaptureReturn = imageCaptureReturn,
            videoCaptureReturn = videoCaptureReturn,
            initialPrimaryPhoto = previewSeedPrimaryPhoto,
            adbAutomationInAppVideoSec = adbAutomationInAppVideoSec,
            adbAutomationVideoFps = adbAutomationVideoFps,
            adbAutomationVideoEncodeW = adbAutomationVideoEncodeW,
            adbAutomationVideoEncodeH = adbAutomationVideoEncodeH,
            adbAutomationVideoTenBit = adbAutomationVideoTenBit,
            adbAutomationVideoDcg = adbAutomationVideoDcg,
            adbAutomationVideoRawSec = adbAutomationVideoRawSec,
            adbDngBisectActive = adbDngBisectActive,
            adbStillCaptureMode = adbStillCaptureMode,
            adbSeedFocusPeakingColor = adbSeedFocusPeakingColor,
            adbPreviewFocusMode = adbPreviewFocusMode,
            adbSeedVideoLutName = adbSeedVideoLutName,
            adbForcePowerThermalOverlay = adbForcePowerThermalOverlay,
            adbAdaptiveBatteryPctOverride = adbAdaptiveBatteryPctOverride,
            adbAdaptiveThermalStatusOverride = adbAdaptiveThermalStatusOverride,
            adbStorageAvailableBytes = adbStorageAvailableBytes,
            adbEnableSmileStill = adbEnableSmileStill,
            adbSmileStillSynthetic = adbSmileStillSynthetic,
            adbVideoBitrateScalePercent = adbVideoBitrateScalePercent,
            adbSceneVendorHints = adbSceneVendorHints,
            adbShowAboutOverlay = adbShowAboutOverlay,
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

    if (showCameraExtSmoke) {
        val adbCameraId =
            activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_CAMERA_ID)?.trim()?.takeIf { it.isNotBlank() }
        CameraExtensionSmokeScreen(
            onBack = { showCameraExtSmoke = false },
            preferredCameraId = adbCameraId,
            finishActivityWhenDone = launchScreen == PNS_SCREEN_CAMERA_EXT_SMOKE,
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
            onBack = {
                showAbout = false
                if (aboutOpenedFromPreview) {
                    showPreviewEngine = true
                    aboutOpenedFromPreview = false
                }
            },
            liveSummary = aboutLiveSummary,
            liveHalHfrMaxByCameraId = aboutHalHfrMaxByCameraId,
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
            onReplayWelcomeTips = {
                WelcomePrefs.resetPermissionOnboardingForDebug(context.applicationContext)
                showHudSettings = false
                hudSettingsFocus = HudSettingsFocus.None
                showPermissionWelcome = true
            },
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
        val autoRootDiag = activity?.intent?.getBooleanExtra(EXTRA_PNS_AUTO_ROOT_DIAGNOSTICS, false) == true
        RootSettingsScreen(
            onBack = { showRootSettings = false },
            autoRunDiagnostics = autoRootDiag,
        )
        return
    }

    if (showFaceMeterProbe) {
        FaceMeterProbeScreen(
            onBack = {
                showFaceMeterProbe = false
                if (launchScreen == PNS_SCREEN_FACE_METER) {
                    activity?.finish()
                }
            },
            startAuto = autoFaceMeterProbe,
        )
        return
    }

    if (showQrScan) {
        QrScanScreen(
            hasCameraPermission = hasCameraPermission,
            onRequestCameraPermission = { requestPermission.launch(Manifest.permission.CAMERA) },
            onBack = {
                showQrScan = false
                if (launchScreen == PNS_SCREEN_QR_SCAN) {
                    activity?.finish()
                }
            },
        )
        return
    }

    if (launchScreen == null && showDebugMenu) {
        val insets = rememberSystemInsetsDp()
        DebugMenuScreen(
            padding = insets.asPaddingValues(extra = 16.dp),
            hasCameraPermission = hasCameraPermission,
            probeSnapshot =
                DebugMenuProbeSnapshot(
                    reportMdReady = reportMd.isNotBlank(),
                    cameraSummaries = cameraSummaries,
                    shallowScanHubLine = shallowScanHubLine,
                    capabilityGateLines = capabilityGateLines,
                    logicalMultiCameraActivePhysicalId = PreviewLogicalPhysicalDebugBridge.snapshot(),
                ),
            onBackToCamera = {
                showDebugMenu = false
                // Restore live preview; opening the dev menu clears this flag — without setting it
                // again we fall through to the engineering hub when launchScreen is non-null (e.g. ADB preview).
                previewLaunchedFromDebug = true
                showPreviewEngine = true
            },
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
            onShowFaceMeterProbe = {
                showDebugMenu = false
                showFaceMeterProbe = true
            },
            onShowQrScan = {
                showDebugMenu = false
                showQrScan = true
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
            probeHubNavEpoch = probeHubNavEpoch,
            onRecordProbeHubEntry = { t ->
                ProbeHubRecentsStore.recordOpen(appCtx, t)
                probeHubNavEpoch++
            },
            onLaunchProbeHubTitle = { navigateProbeFromTitle(it, dismissDebugMenu = true) },
            onToggleProbeHubFavorite = { t ->
                ProbeHubRecentsStore.toggleFavoriteTitle(appCtx, t)
                probeHubNavEpoch++
            },
        )
        return
    }

    if (launchScreen == null) {
        val intentPrimaryPhotoDefault =
            activity?.intent?.takeIf { it.hasExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO) }
                ?.getBooleanExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO, true)
        val previewSeedPrimaryPhoto =
            intentPrimaryPhotoDefault ?: (
                activity?.intent?.action != MediaStore.INTENT_ACTION_VIDEO_CAMERA &&
                    activity?.intent?.action != MediaStore.ACTION_VIDEO_CAPTURE
            )
        PreviewEngineScreen(
            onBack = { activity?.finish() },
            onOpenDeveloperMenu = { showDebugMenu = true },
            startAutoSweep = autoSweep,
            initialPrimaryPhoto = previewSeedPrimaryPhoto,
            adbAutomationInAppVideoSec = 0,
        )
        return
    }

    val insets = rememberSystemInsetsDp()
    BackHandler {
        previewLaunchedFromDebug = true
        showPreviewEngine = true
    }
    DebugMenuScreen(
        padding = insets.asPaddingValues(extra = 16.dp),
        hasCameraPermission = hasCameraPermission,
        probeSnapshot =
            DebugMenuProbeSnapshot(
                reportMdReady = reportMd.isNotBlank(),
                cameraSummaries = cameraSummaries,
                shallowScanHubLine = shallowScanHubLine,
                capabilityGateLines = capabilityGateLines,
                logicalMultiCameraActivePhysicalId = PreviewLogicalPhysicalDebugBridge.snapshot(),
            ),
        onBackToCamera = null,
        onShowMapping = { showMapping = true },
        onShowPreviewEngine = {
            previewLaunchedFromDebug = true
            showPreviewEngine = true
        },
        onShowEncoderProbe = { showEncoderProbe = true },
        onShowLegacyCamera1 = { showLegacyCamera1 = true },
        onShowDeepCaps = { showDeepCaps = true },
        onShowFaceMeterProbe = { showFaceMeterProbe = true },
        onShowQrScan = { showQrScan = true },
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
        onResetPermissionWelcome = {
            WelcomePrefs.resetPermissionOnboardingForDebug(context.applicationContext)
            showPermissionWelcome = true
        },
        onExport = {
            val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now())
            exportLauncher.launch("PROBE_RESULTS_$ts.md")
        },
        probeHubNavEpoch = probeHubNavEpoch,
        onRecordProbeHubEntry = { t ->
            ProbeHubRecentsStore.recordOpen(appCtx, t)
            probeHubNavEpoch++
        },
        onLaunchProbeHubTitle = { navigateProbeFromTitle(it, dismissDebugMenu = false) },
        onToggleProbeHubFavorite = { t ->
            ProbeHubRecentsStore.toggleFavoriteTitle(appCtx, t)
            probeHubNavEpoch++
        },
    )
}

internal fun buildProbeReport(context: Context, scanBudgetMs: Long = 4000L): String {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraIds = runCatching { cameraManager.cameraIdList.toList() }.getOrDefault(emptyList())

    val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    val sb = StringBuilder()
    val scanStart = SystemClock.elapsedRealtime()
    val camerasJson = JSONArray()
    var scanDegraded = false

    sb.appendLine("# Point & Shoot — PROBE RESULTS")
    sb.appendLine()
    sb.appendLine("- Generated: $now")
    sb.appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
    sb.appendLine("- Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    sb.appendLine()
    AeHighlightProbe.appendDeviceWideSection(sb, context.applicationContext)
    sb.appendLine("## Cameras (${cameraIds.size})")
    sb.appendLine()

    for (id in cameraIds) {
        if (SystemClock.elapsedRealtime() - scanStart > scanBudgetMs) {
            scanDegraded = true
            sb.appendLine("## Scan truncated (Sprint 10.1)")
            sb.appendLine("- degraded=true reason=wall_clock_budget_ms budgetMs=$scanBudgetMs")
            break
        }
        val cc = runCatching { cameraManager.getCameraCharacteristics(id) }.getOrNull()
        if (cc == null) {
            sb.appendLine("- Camera $id: FAILED to read characteristics")
        } else {
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
        sb.appendLine("### Camera extensions (API 31+)")
        sb.appendLine()
        sb.append(CameraExtensionSupport.markdownLinesForCamera(cameraManager, id))
        sb.appendLine("### Reprocess / ZSL-related (characteristics)")
        sb.appendLine()
        sb.append(PreviewReprocessStillHints.markdownLines(cc))
        sb.appendLine()

        sb.appendLine()
        AeHighlightProbe.appendPerCameraSections(sb, id, cc, reqKeys, sessionKeys, resKeys)

        sb.appendLine("### StreamConfigurationMap (derived FPS candidates)")
        sb.appendLine()
        val map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (map == null) {
            sb.appendLine("- android.scaler.streamConfigurationMap: null")
        } else {
            appendStreamConfigSummary(sb, map)
        }
        sb.appendLine()
        val rawPick = RawCaptureSupport.pickRawOutput(cc)
        sb.appendLine(
            "- rawPickEffective=${RawCaptureSupport.rawPickEffectiveLabel(rawPick?.first)} " +
                "size=${rawPick?.second?.let { sz -> "${sz.width}x${sz.height}" } ?: "null"}",
        )
        sb.appendLine()
        runCatching {
            camerasJson.put(DeviceCameraCapabilityCache.cameraJson(id, cc, map))
        }.onFailure { e ->
            Log.w(TAG, "shallow cache json cameraId=$id failed: ${e.message}")
        }

        sb.appendLine("### Vendor-key highlights (name-based)")
        sb.appendLine()
        val vendorPool = (reqKeys + sessionKeys + resKeys).distinct()
        appendVendorHighlights(sb, vendorPool)
        sb.appendLine()

        val charKeyNames = cc.keys.map { it.name }
        appendNamedVendorFaceEyeTrackingKeysSection(sb, charKeyNames, reqKeys, resKeys, sessionKeys)

        val faceModes = cc.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
            ?.joinToString(prefix = "[", postfix = "]") { it.toString() }
            ?: "null"
        sb.appendLine("### Face detect modes")
        sb.appendLine()
        sb.appendLine("- STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES: $faceModes")
        sb.appendLine()

        appendFaceEyeMeteringProbe(sb, cc)
        appendFaceEyeRequestResultKeyLists(sb, reqKeys, resKeys)
        }
    }

    val shallowRoot = DeviceCameraCapabilityCache.buildRoot(context.applicationContext, camerasJson, scanDegraded)
    DeviceCameraCapabilityCache.appendMarkdownJsonBlock(sb, shallowRoot)
    ShallowCapabilityCacheStore.saveAfterProbe(context.applicationContext, shallowRoot)

    val fleetSnap = FleetCameraProfileBuilder.buildSnapshot(context.applicationContext)
    FleetCameraProfileStore.save(context.applicationContext, fleetSnap)
    FleetCameraProfileStore.appendProbeMarkdown(sb, fleetSnap)
    FleetCameraProfileStore.appendCatalogMarkdown(sb, context.applicationContext, probeHiddenIds = false)
    FleetCameraProfiles.invalidateMemoryCache()

    return sb.toString()
}

internal fun buildProbeReportMarkdown(context: Context): String = buildProbeReport(context)

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
        label = "RAW12 (ImageFormat.RAW12)",
        sizes = runCatching { map.getOutputSizes(ImageFormat.RAW12) }.getOrNull(),
    ) { s ->
        runCatching { map.getOutputMinFrameDuration(ImageFormat.RAW12, s) }.getOrNull()
    }

    appendOutputSection(
        label = "RAW10 (ImageFormat.RAW10)",
        sizes = runCatching { map.getOutputSizes(ImageFormat.RAW10) }.getOrNull(),
    ) { s ->
        runCatching { map.getOutputMinFrameDuration(ImageFormat.RAW10, s) }.getOrNull()
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

    sb.appendLine("#### High-speed FPS rollup (constrained high speed)")
    sb.appendLine(DeviceCameraCapabilityCache.hfrRollupMarkdownLine(map))
    sb.appendLine()

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

private fun filterFaceAeAfMetadataKeyNames(keys: List<String>): List<String> {
    val needles =
        listOf(
            "face",
            "Face",
            "eye",
            "Eye",
            "statistics.",
            "control.aeRegion",
            "control.afRegion",
            "control.awbRegion",
            "AeRegion",
            "AfRegion",
            "AwbRegion",
            "focusDistance",
            "focusRange",
            "Metering",
        )
    return keys.filter { k -> needles.any { n -> k.contains(n) } }.distinct().sorted()
}

private fun appendVendorHighlights(sb: StringBuilder, keys: List<String>) {
    fun hits(vararg terms: String): List<String> =
        keys.filter { k -> terms.any { t -> k.contains(t, ignoreCase = true) } }.distinct().sorted()

    val lbmf = hits("lbmf", "mfhdr", "EnableMFHDR", "EnableIdealRAW")
    val dcgHdr = hits("dcg", "EnableHDRDCGMode", "hdr")
    val hybridAe = hits("hybrid", "ae", "dynamicFPSConfig", "HDRMode", "SnapshotHDRMode")
    val highlightWeighted =
        hits(
            "highlight",
            "Highlight",
            "HIGHLIGHT",
            "weighted",
            "Weighted",
            "spot",
            "Spot",
            "metering",
            "Metering",
        )
    val bkt = hits("bracket", "EnableAFBracketing", "BKT", "Grouping")
    val faceEyeVendor = VendorFaceEyeKeyNames.namedFaceEyeTrackingVendorKeys(keys)

    sb.appendLine("- LBMF / MFHDR candidates: ${if (lbmf.isEmpty()) "(none found by name)" else lbmf.joinToString()}")
    sb.appendLine("- DCG-HDR / HDR candidates: ${if (dcgHdr.isEmpty()) "(none found by name)" else dcgHdr.joinToString()}")
    sb.appendLine("- Hybrid AE candidates: ${if (hybridAe.isEmpty()) "(none found by name)" else hybridAe.joinToString()}")
    sb.appendLine(
        "- Highlight / weighted / metering-ish candidates: " +
            "${if (highlightWeighted.isEmpty()) "(none found by name)" else highlightWeighted.joinToString()}",
    )
    sb.appendLine("- Bracketing candidates: ${if (bkt.isEmpty()) "(none found by name)" else bkt.joinToString()}")
    sb.appendLine(
        "- Vendor-named face / eye / tracking keys (`com.` / `org.` / `vendor` substring + face/eye/tracking name match): " +
            "${if (faceEyeVendor.isEmpty()) "(none found by name)" else faceEyeVendor.joinToString()}",
    )
}

private fun appendNamedVendorFaceEyeTrackingKeysSection(
    sb: StringBuilder,
    characteristicKeyNames: List<String>,
    reqKeys: List<String>,
    resKeys: List<String>,
    sessionKeys: List<String>,
) {
    sb.appendLine("### Named vendor keys — face / eye / tracking (by scope)")
    sb.appendLine()
    fun bullets(title: String, list: List<String>) {
        sb.appendLine("#### $title")
        sb.appendLine()
        if (list.isEmpty()) {
            sb.appendLine("- (none)")
        } else {
            list.forEach { sb.appendLine("- `$it`") }
        }
        sb.appendLine()
    }
    bullets(
        "Characteristics",
        VendorFaceEyeKeyNames.namedFaceEyeTrackingVendorKeys(characteristicKeyNames),
    )
    bullets("CaptureRequest", VendorFaceEyeKeyNames.namedFaceEyeTrackingVendorKeys(reqKeys))
    bullets("CaptureResult", VendorFaceEyeKeyNames.namedFaceEyeTrackingVendorKeys(resKeys))
    bullets("SessionConfiguration", VendorFaceEyeKeyNames.namedFaceEyeTrackingVendorKeys(sessionKeys))
}

private fun formatMaxRegions(v: Any?): String =
    when (v) {
        null -> "null"
        is IntArray -> v.contentToString()
        is Array<*> -> v.contentToString()
        else -> v.toString()
    }

/** Typed characteristics useful for face ROI, AE/AF regions, and Eye-AF expectations. */
private fun appendFaceEyeMeteringProbe(sb: StringBuilder, cc: CameraCharacteristics) {
    sb.appendLine("### Face / eye / metering (Camera2 typed)")
    sb.appendLine()
    sb.appendLine("- CONTROL_MAX_REGIONS_AE: ${formatMaxRegions(cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE))}")
    sb.appendLine("- CONTROL_MAX_REGIONS_AF: ${formatMaxRegions(cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF))}")
    sb.appendLine("- CONTROL_MAX_REGIONS_AWB: ${formatMaxRegions(cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB))}")
    val syncLat = cc.get(CameraCharacteristics.SYNC_MAX_LATENCY)
    sb.appendLine("- SYNC_MAX_LATENCY: ${syncLat?.toString() ?: "null"}")
    sb.appendLine()
}

private fun appendFaceEyeRequestResultKeyLists(
    sb: StringBuilder,
    reqKeys: List<String>,
    resKeys: List<String>,
) {
    val fr = filterFaceAeAfMetadataKeyNames(reqKeys)
    val fs = filterFaceAeAfMetadataKeyNames(resKeys)
    sb.appendLine("### Face / AE-AF related keys (name filter on Request / Result)")
    sb.appendLine()
    sb.appendLine("- CaptureRequest keys (${fr.size}): ${if (fr.isEmpty()) "(none)" else fr.joinToString()}")
    sb.appendLine("- CaptureResult keys (${fs.size}): ${if (fs.isEmpty()) "(none)" else fs.joinToString()}")
    sb.appendLine()
}

/**
 * Machine-readable face / eye / metering summary for adb pulls (`face_meter_probe_*.json`).
 * Markdown with full detail: [buildProbeReportMarkdown].
 */
internal fun buildFaceMeterProbeSummaryJson(context: Context): String {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraIds = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
    val root = JSONObject()
    root.put("kind", "face_meter_probe")
    root.put("schemaVersion", 2)
    root.put("generatedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
    root.put(
        "device",
        JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("release", Build.VERSION.RELEASE)
        },
    )
    val cams = JSONArray()
    for (id in cameraIds) {
        val o = JSONObject()
        o.put("cameraId", id)
        val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull()
        if (cc == null) {
            o.put("error", "getCameraCharacteristics_failed")
            cams.put(o)
            continue
        }
        val facing =
            when (cc.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }
        o.put("lensFacing", facing)
        val modes = cc.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) ?: intArrayOf()
        val modeJa = JSONArray()
        for (m in modes) modeJa.put(m)
        o.put("statisticsInfoAvailableFaceDetectModes", modeJa)
        o.put("hasFaceDetectFull", modes.contains(CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL))
        o.put("maxRegionsAe", formatMaxRegions(cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)))
        o.put("maxRegionsAf", formatMaxRegions(cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)))
        o.put("maxRegionsAwb", formatMaxRegions(cc.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB)))
        val syncLat = cc.get(CameraCharacteristics.SYNC_MAX_LATENCY)
        if (syncLat == null) {
            o.put("syncMaxLatency", JSONObject.NULL)
        } else {
            o.put("syncMaxLatency", syncLat)
        }
        val reqKeys = runCatching { cc.availableCaptureRequestKeys }.getOrNull()?.map { it.name } ?: emptyList()
        val resKeys = runCatching { cc.availableCaptureResultKeys }.getOrNull()?.map { it.name } ?: emptyList()
        val sessionKeys = runCatching { cc.availableSessionKeys }.getOrNull()?.map { it.name } ?: emptyList()
        val charNames = cc.keys.map { it.name }
        fun putNamedScope(jsonName: String, keyList: List<String>) {
            val ja = JSONArray()
            for (s in VendorFaceEyeKeyNames.namedFaceEyeTrackingVendorKeys(keyList)) ja.put(s)
            o.put(jsonName, ja)
        }
        putNamedScope("vendorNamedFaceEyeTracking_characteristics", charNames)
        putNamedScope("vendorNamedFaceEyeTracking_request", reqKeys)
        putNamedScope("vendorNamedFaceEyeTracking_result", resKeys)
        putNamedScope("vendorNamedFaceEyeTracking_session", sessionKeys)
        val jaAll = JSONArray()
        for (s in VendorFaceEyeKeyNames.namedFaceEyeTrackingVendorKeys(charNames + reqKeys + resKeys + sessionKeys)) {
            jaAll.put(s)
        }
        o.put("vendorNamedFaceEyeTracking_all", jaAll)
        val jaReq = JSONArray()
        for (s in filterFaceAeAfMetadataKeyNames(reqKeys)) jaReq.put(s)
        o.put("captureRequestKeysFaceAeAfFilter", jaReq)
        val jaRes = JSONArray()
        for (s in filterFaceAeAfMetadataKeyNames(resKeys)) jaRes.put(s)
        o.put("captureResultKeysFaceAeAfFilter", jaRes)
        o.put(
            "oplusMacroCloseupRequestAdvertised",
            VendorKeyGuard.isRequestKeyAvailable(cc, HardwareCapsSnapshot.VENDOR_MACRO_CLOSEUP_REQUEST) ||
                VendorKeyGuard.isSessionKeyAvailable(cc, HardwareCapsSnapshot.VENDOR_MACRO_CLOSEUP_REQUEST),
        )
        cams.put(o)
    }
    root.put("cameras", cams)
    return root.toString(2)
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
        "MACRO" -> CommandDialMode.Macro
        "QR" -> CommandDialMode.Qr
        "DUAL" -> CommandDialMode.Dual
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
        ImagingProfile.JpegOnly.id -> ImagingProfile.JpegOnly
        else -> null
    }
}

internal fun Intent?.previewRawStreamPreferenceExtra(): RawStreamPreference? {
    val s = this?.getStringExtra(EXTRA_PNS_PREVIEW_RAW_STREAM)?.trim()?.lowercase() ?: return null
    return when (s) {
        "", "default" -> RawStreamPreference.Default
        "raw_sensor_first", "sensor_first", "sensorfirst" -> RawStreamPreference.RawSensorFirst
        "raw12_only", "raw12only", "12_only" -> RawStreamPreference.Raw12Only
        "raw_sensor_only", "sensor_only", "rawsensor_only" -> RawStreamPreference.RawSensorOnly
        "raw10_only", "raw10only", "10_only" -> RawStreamPreference.Raw10Only
        else -> {
            Log.w(TAG, "unknown pns_preview_raw_stream=$s")
            null
        }
    }
}

internal fun Intent?.previewJpegCompanionSeedExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_JPEG_COMPANION)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_JPEG_COMPANION, true)
    } else {
        null
    }

private fun Intent?.previewStillsLutNameExtra(): String? =
    this?.getStringExtra(EXTRA_PNS_PREVIEW_STILLS_LUT)?.trim()?.takeIf { it.isNotBlank() }

internal fun Intent?.previewFocusPeakingColorExtra(): FocusPeakingColor? {
    val raw = this?.getStringExtra(EXTRA_PNS_PREVIEW_FOCUS_PEAKING)?.trim() ?: return null
    return FocusPeakingColor.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: run {
            Log.w(TAG, "unknown pns_preview_focus_peaking=$raw")
            null
        }
}

internal fun Intent?.previewFocusModeExtra(): PreviewFocusSelection? {
    val raw = this?.getStringExtra(EXTRA_PNS_PREVIEW_FOCUS_MODE) ?: return null
    return PreviewFocusMode.parseAdbExtra(raw)
        ?: run {
            Log.w(TAG, "unknown pns_preview_focus_mode=$raw")
            null
        }
}

internal fun Intent?.previewVideoLutNameExtra(): String? =
    this?.getStringExtra(EXTRA_PNS_PREVIEW_VIDEO_LUT)?.trim()?.takeIf { it.isNotBlank() }

private fun Intent?.previewSelfTimerSecExtra(): Int? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_SELF_TIMER_SEC)) {
        getIntExtra(EXTRA_PNS_PREVIEW_SELF_TIMER_SEC, 0)
    } else {
        null
    }

internal fun Intent?.previewFocalMmSlotExtra(): FocalMmSlot? {
    val raw = this?.getStringExtra(EXTRA_PNS_PREVIEW_FOCAL_MM_SLOT)?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return FocalMmSlot.entries.find { it.labelMm == raw }
}

internal fun Intent?.previewReadoutIsoProbeExtra(): Int? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_READOUT_ISO)) {
        getIntExtra(EXTRA_PNS_PREVIEW_READOUT_ISO, 0).takeIf { it > 0 }
    } else {
        null
    }

