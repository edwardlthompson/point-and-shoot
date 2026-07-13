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
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import dev.pointandshoot.fleet.FleetDeviceMatrix
import dev.pointandshoot.fleet.FleetDeviceMatrixBuilder
import dev.pointandshoot.fleet.FleetDeviceMatrixStore
import dev.pointandshoot.fleet.FleetProbeMatrixMarkdown
import dev.pointandshoot.fleet.FleetMatrixHubScreen
import dev.pointandshoot.fleet.FleetPolicyPreferences
import dev.pointandshoot.preview.PreviewAutomationExtrasRegistry
import dev.pointandshoot.preview.mock.MockPreviewScreens
import dev.pointandshoot.preview.mock.UnifiedMockPreviewScreen

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
const val EXTRA_PNS_PREVIEW_DIAL = PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_DIAL
const val EXTRA_PNS_PREVIEW_RAW_COUNT = PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_RAW_COUNT
/** When true with [EXTRA_PNS_PREVIEW_RAW_COUNT] > 0: shorter ADB settle/poll (dev smoke only). */
const val EXTRA_PNS_PREVIEW_RAW_STILL_FAST = PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_RAW_STILL_FAST
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

/** Sprint **29.1** — session seed for [PreviewChromePreferences.stripExifPrivacyTags]. */
const val EXTRA_PNS_PREVIEW_STRIP_EXIF = "pns_preview_strip_exif"

/** Matrix bisect: skip [StillCaptureMetadata.applyToDngUri] after DNG save. */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_STILL_METADATA = "pns_preview_dng_skip_still_metadata"

/**
 * Override still DNG backend on LegacyDevice: `framework_referenceapp` | `altreferenceapp_inspired`.
 * See [DngSaveBisectState] and `docs/DNG_PIPELINE_TRIANGULATION_MATRIX.md`.
 */
const val EXTRA_PNS_PREVIEW_STILL_DNG_BACKEND = "pns_preview_still_dng_backend"

/** Sprint 13.8: `standard` | `zsl` | `hdr` — seeds [StillCaptureMode] for automation. */
const val EXTRA_PNS_PREVIEW_STILL_MODE = "pns_preview_still_mode"
/** Sprint 22.3: still export override (`heic`|`motion_photo`|`tiff16`|`jxl`). */
const val EXTRA_PNS_PREVIEW_STILL_FORMAT = "pns_preview_still_format"
/** Sprint 22.x: still resolution mode seed (`binned`|`max_resolution`). */
const val EXTRA_PNS_PREVIEW_STILL_RESOLUTION_MODE = "pns_preview_still_resolution_mode"

/** Sprint **CC.1** — composed still burst count (`pns_preview_burst_count`, e.g. 3). */
const val EXTRA_PNS_PREVIEW_BURST_COUNT = "pns_preview_burst_count"

/** Sprint **CC.1** — ms between burst stills (`pns_preview_burst_interval_ms`, default 350). */
const val EXTRA_PNS_PREVIEW_BURST_INTERVAL_MS = "pns_preview_burst_interval_ms"
/** Sprint **CC.1** — scripted hold duration (ms) for long-press burst automation. */
const val EXTRA_PNS_PREVIEW_LONGPRESS_BURST_HOLD_MS = "pns_preview_longpress_burst_hold_ms"
/** Burst profile seed for automation (`pns_preview_burst_file`: `raw`|`jpeg`). */
const val EXTRA_PNS_PREVIEW_BURST_FILE = "pns_preview_burst_file"
/** Burst pipeline strategy seed for automation (`pns_preview_burst_strategy`: `aggressive`|`paced`). */
const val EXTRA_PNS_PREVIEW_BURST_STRATEGY = "pns_preview_burst_strategy"

/** Sprint **CC.3** — loopback tether (`pns_preview_tether`). */
const val EXTRA_PNS_PREVIEW_TETHER = "pns_preview_tether"
/** Sprint **15.37** — LAN tether + NSD (`pns_preview_wifi_direct_tether`). */
const val EXTRA_PNS_PREVIEW_WIFI_DIRECT_TETHER = "pns_preview_wifi_direct_tether"

/** Sprint **CC.3** — picture profile id (`pns_preview_picture_profile`, e.g. `cinematic`). */
const val EXTRA_PNS_PREVIEW_PICTURE_PROFILE = "pns_preview_picture_profile"

/** Sprint **CC.3** — flash strength percent 25–100 (`pns_preview_flash_strength`). */
const val EXTRA_PNS_PREVIEW_FLASH_STRENGTH = "pns_preview_flash_strength"

/** Sprint **CC.3** — export newest calibration profile once (`pns_preview_cal_export`). */
const val EXTRA_PNS_PREVIEW_CAL_EXPORT = "pns_preview_cal_export"

/** Sprint **29.2** — export settings JSON to app-private automation path (`pns_preview_settings_export`). */
const val EXTRA_PNS_PREVIEW_SETTINGS_EXPORT = "pns_preview_settings_export"

/** Sprint **29.2** — import settings JSON from automation path (`pns_preview_settings_import`). */
const val EXTRA_PNS_PREVIEW_SETTINGS_IMPORT = "pns_preview_settings_import"

/** Sprint **UX.1** — `system` | `light` | `dark` (`pns_preview_theme_mode`). */
const val EXTRA_PNS_PREVIEW_THEME_MODE = "pns_preview_theme_mode"

/** Sprint **UX.3** — apply [WorkflowPresets] id on cold preview (`pns_preview_workflow_preset`). */
const val EXTRA_PNS_PREVIEW_WORKFLOW_PRESET = "pns_preview_workflow_preset"

/** Sprint **UX.2/UX.3** — open in-app gallery on cold preview (`pns_preview_open_gallery`). */
const val EXTRA_PNS_PREVIEW_OPEN_GALLERY = "pns_preview_open_gallery"

/**
 * Sprint **UX.3** — after gallery index loads, auto-select N items and fire batch share (N ≥ 2).
 * (`pns_preview_gallery_batch_share`).
 */
const val EXTRA_PNS_PREVIEW_GALLERY_BATCH_SHARE = "pns_preview_gallery_batch_share"

/** Sprint **UX.3** — enable cloud backup prefs on cold preview (`pns_preview_cloud_backup`). */
const val EXTRA_PNS_PREVIEW_CLOUD_BACKUP = "pns_preview_cloud_backup"

/** Sprint **UX.3** — run [CloudCaptureBackup.syncRecentCaptures] once (`pns_preview_cloud_backup_sync`). */
const val EXTRA_PNS_PREVIEW_CLOUD_BACKUP_SYNC = "pns_preview_cloud_backup_sync"

/** Debug gate: backup to app external `ux_cloud_backup_probe/` when no SAF folder (`pns_preview_cloud_backup_probe`). */
const val EXTRA_PNS_PREVIEW_CLOUD_BACKUP_PROBE = "pns_preview_cloud_backup_probe"

/** Sprint **IP.1** — [SharingManager] probe on first gallery item (`pns_preview_platform_share_probe`). */
const val EXTRA_PNS_PREVIEW_PLATFORM_SHARE_PROBE = "pns_preview_platform_share_probe"

/** Sprint **IP.1** — FileProvider probe (`pns_preview_platform_file_provider_probe`). */
const val EXTRA_PNS_PREVIEW_PLATFORM_FILE_PROVIDER_PROBE = "pns_preview_platform_file_provider_probe"

/** Sprint **IP.1** — log widget provider (`pns_preview_platform_widget_probe`). */
const val EXTRA_PNS_PREVIEW_PLATFORM_WIDGET_PROBE = "pns_preview_platform_widget_probe"

/** Sprint **IP.2** — enable LAN HTTP transfer server (`pns_preview_lan_transfer`). */
const val EXTRA_PNS_PREVIEW_LAN_TRANSFER = "pns_preview_lan_transfer"

/** Sprint **IP.2** — log LAN server + hit /status via automation (`pns_preview_lan_transfer_probe`). */
const val EXTRA_PNS_PREVIEW_LAN_TRANSFER_PROBE = "pns_preview_lan_transfer_probe"

/** Sprint **IP.2** — log WebDAV configured (`pns_preview_webdav_probe`). */
const val EXTRA_PNS_PREVIEW_WEBDAV_PROBE = "pns_preview_webdav_probe"

/** Sprint **IP.2** — exercise [SocialStreamHooks] skip path (`pns_preview_social_stream_probe`). */
const val EXTRA_PNS_PREVIEW_SOCIAL_STREAM_PROBE = "pns_preview_social_stream_probe"

/** Sprint **IP.2** — log [CollaborativeCapture] (`pns_preview_collaborative_probe`). */
const val EXTRA_PNS_PREVIEW_COLLABORATIVE_PROBE = "pns_preview_collaborative_probe"

/** Skip DNG tag 50708 ([Dng12Saver] unique camera model buffer rewrite). */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_UNIQUE_CAMERA_MODEL = "pns_preview_dng_skip_unique_camera_model"

/** Skip [PreviewJpegProcessingHints] on RAW still capture request. */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_JPEG_HINTS_STILL = "pns_preview_dng_skip_jpeg_hints_still"

/** Force [LeafDngHalReconcile] ASN patch on leaf rear ids (true/false). */
const val EXTRA_PNS_PREVIEW_DNG_FORCE_LEAF_RECONCILE = "pns_preview_dng_force_leaf_reconcile"

/** Leaf reconcile: use Bayer ASN (legacy) instead of LegacyDevice gains-first. */
const val EXTRA_PNS_PREVIEW_DNG_FORCE_BAYER_ASN = "pns_preview_dng_force_bayer_asn"

/** Skip [DngCreator.setDescription] / LUT software line on DNG save. */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_SOFTWARE_DESC = "pns_preview_dng_skip_software_desc"

/** Fleet exposure E03 — skip pure-HAL AE_LOCK after precapture. */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_AE_LOCK = "pns_preview_dng_skip_ae_lock"

/** Fleet exposure E04 — after stopRepeating debounce ms. */
const val EXTRA_PNS_PREVIEW_DNG_AFTER_STOP_DEBOUNCE_MS = "pns_preview_dng_after_stop_debounce_ms"

/** Fleet exposure E05 — skip ProShot weight-0 AE regions. */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_AE_REGIONS = "pns_preview_dng_skip_ae_regions"

/** Fleet exposure E08 — AE compensation steps. */
const val EXTRA_PNS_PREVIEW_DNG_AE_COMP_STEPS = "pns_preview_dng_ae_comp_steps"

/** Fleet exposure E09 — precapture STILL template. */
const val EXTRA_PNS_PREVIEW_DNG_PRECAPTURE_STILL_TEMPLATE = "pns_preview_dng_precapture_still_template"

/** Fleet exposure E11 — skip still IQ pipeline. */
const val EXTRA_PNS_PREVIEW_DNG_SKIP_STILL_IQ = "pns_preview_dng_skip_still_iq"

/** ProShot L6/f6 process rebuild — ADB bisect only (`DngSaveBisectState.useProShotCapturePipeline`). */
const val EXTRA_PNS_PREVIEW_DNG_PROSHOT_PIPELINE = "pns_preview_dng_proshot_pipeline"
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
/** Sprint **15.10** — lock shutter speed (ns) so ISO chase can be proven over ADB. */
const val EXTRA_PNS_PREVIEW_READOUT_SHUTTER_NS = "pns_preview_readout_shutter_ns"
/**
 * Optional **`--ei pns_preview_aperture_cycles N`** with [PNS_SCREEN_PREVIEW]: after preview settles on a
 * variable-aperture camera, cycles [CaptureRequest.LENS_APERTURE] **N** times; logs **`apertureCycle`** +
 * **`PNS.AdbValidation apertureCycle ok=`** for **`scripts/pns_aperture_readout_verify.ps1`**.
 */
const val EXTRA_PNS_PREVIEW_APERTURE_CYCLES = "pns_preview_aperture_cycles"
/** Sprint **15.11** — video shutter-angle preset (`Angle180`, …) for `pns_shutter_angle_verify.ps1`. */
const val EXTRA_PNS_PREVIEW_VIDEO_SHUTTER_ANGLE = "pns_preview_video_shutter_angle"
/** Sprint **15.1** — ADB seed: enable eye AF HUD overlay on preview start. */
const val EXTRA_PNS_PREVIEW_EYE_AF_OVERLAY = "pns_preview_eye_af_overlay"

/** Sprint **15.19** — after preview settles, synthesize media play/pause → `shutterFired source=bt_media`. */
const val EXTRA_PNS_PREVIEW_AUTOMATION_BT_MEDIA_KEY = "pns_preview_automation_bt_media_key"

/** Hardware camera key automation — after preview settles, synthesize KEYCODE_CAMERA → `shutterFired source=camera_key`. */
const val EXTRA_PNS_PREVIEW_AUTOMATION_HARDWARE_KEY = "pns_preview_automation_hardware_key"

/**
 * Optional **`--ez pns_preview_primary_photo false`** with [PNS_SCREEN_PREVIEW]: cold-start **video-primary**
 * tray (vs photo-primary default).
 */
const val EXTRA_PNS_PREVIEW_PRIMARY_PHOTO = PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_PRIMARY_PHOTO

/** Sprint **AS.1** — hi-fi video audio (96 kHz AAC when supported). */
const val EXTRA_PNS_PREVIEW_AUDIO_HIFI = "pns_preview_audio_hifi"

/** Sprint **AS.1** — wind noise suppression on video record path. */
const val EXTRA_PNS_PREVIEW_AUDIO_WIND = "pns_preview_audio_wind"

/** Sprint **15.25** — HudSettings wind noise filter (Camcorder + NS/AEC). */
const val EXTRA_PNS_PREVIEW_WIND_NOISE_FILTER = "pns_preview_wind_noise_filter"

/** Experimental lane: master app-breaking toggle seed (session startup). */
const val EXTRA_PNS_PREVIEW_EXPERIMENTAL_MASTER = "pns_preview_experimental_master"
/** Experimental lane: max-res unlock toggle seed (root-only CPH2583 lane). */
const val EXTRA_PNS_PREVIEW_EXPERIMENTAL_MAX_RES_UNLOCK = "pns_preview_experimental_max_res_unlock"
/** Experimental lane: vendor session-key toggle seed (independent from property override). */
const val EXTRA_PNS_PREVIEW_EXPERIMENTAL_VENDOR_SESSION = "pns_preview_experimental_vendor_session"
/** Experimental lane: force safe mode active before preview starts. */
const val EXTRA_PNS_PREVIEW_FORCE_SAFE_MODE = "pns_preview_force_safe_mode"
/** Root-gated max-res sweep: CSV session keys (tested during still session template build). */
const val EXTRA_PNS_PREVIEW_MAX_RES_SWEEP_SESSION_KEYS = "pns_preview_max_res_sweep_session_keys"
/** Root-gated max-res sweep: CSV request keys (tested during still request build). */
const val EXTRA_PNS_PREVIEW_MAX_RES_SWEEP_REQUEST_KEYS = "pns_preview_max_res_sweep_request_keys"

/** Sprint **15.26** — seed preview AE lock (`pns_preview_ae_lock`). */
const val EXTRA_PNS_PREVIEW_AE_LOCK = "pns_preview_ae_lock"

/** Milestone **20.3** — concurrent rear+rear PiP preview (`pns_preview_pip`). */
const val EXTRA_PNS_PREVIEW_PIP = "pns_preview_pip"

/** Milestone **20.2** — Multicam Melt scaffold arm (`pns_preview_multicam_melt`). */
const val EXTRA_PNS_PREVIEW_MULTICAM_MELT = "pns_preview_multicam_melt"

/** Sprint **15.27** — intervalometer time-lapse automation seeds. */
const val EXTRA_PNS_PREVIEW_TIMELAPSE_MODE = "pns_preview_timelapse_mode"
const val EXTRA_PNS_PREVIEW_TIMELAPSE_RUNNING = "pns_preview_timelapse_running"
const val EXTRA_PNS_PREVIEW_TIMELAPSE_INTERVAL_SEC = "pns_preview_timelapse_interval_sec"
const val EXTRA_PNS_PREVIEW_TIMELAPSE_AUTO_STOP_SEC = "pns_preview_timelapse_auto_stop_sec"

/** Sprint **15.28** — focus breathing compensation toggle + automation rack. */
const val EXTRA_PNS_PREVIEW_FOCUS_BREATHING_COMP = "pns_preview_focus_breathing_comp"
const val EXTRA_PNS_PREVIEW_AUTOMATION_FOCUS_RACK_SEC = "pns_preview_automation_focus_rack_sec"
/** Sprint **15.36** — run waypoint rack pull once after preview settles (`pns_preview_rack_focus_pull`). */
const val EXTRA_PNS_PREVIEW_RACK_FOCUS_PULL = "pns_preview_rack_focus_pull"

/** Sprint **AS.2** — shutter pack: `mechanical`, `digital`, `vintage`, `silent`. */
const val EXTRA_PNS_PREVIEW_SHUTTER_SOUND_PACK = "pns_preview_shutter_sound_pack"

/**
 * Debug APK + [PNS_SCREEN_PREVIEW]: after preview settles, record **N** seconds via in-app
 * [android.media.MediaRecorder] automation (**`scripts/pns_in_app_video_verify.ps1`**). Values clamp to **[0, 120]**.
 */
const val EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC =
    PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC

/** Sprint **14.11** gate: open in-preview [AboutScreen] overlay on cold preview (`pns_about_links_verify.ps1`). */
const val EXTRA_PNS_PREVIEW_SHOW_ABOUT = "pns_preview_show_about"

/** Sprint **15.8** gate: open chrome Settings rail on cold preview (`pns_settings_rail_screencap.ps1`). */
const val EXTRA_PNS_PREVIEW_OPEN_SETTINGS = "pns_preview_open_settings"

/**
 * Debug APK + [PNS_SCREEN_PREVIEW]: target FPS for in-app video automation.
 * When > 60 the [MediaCodecVideoRecorder] path is used (bypasses `ro.media.recorder-max-base-layer-fps=60`).
 * Defaults to 120 when [EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC] > 0 and this extra is absent.
 *
 * Typical ADB: `--ei pns_preview_video_fps 120`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_FPS = PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_FPS

/**
 * Debug APK + [PNS_SCREEN_PREVIEW]: chrome in-app video encode width for automation (**13V.16**).
 * Typical ADB: `--ei pns_preview_video_encode_w 3840`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_ENCODE_W = PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_ENCODE_W

/**
 * Debug APK + [PNS_SCREEN_PREVIEW]: chrome in-app video encode height for automation (**13V.16**).
 * Typical ADB: `--ei pns_preview_video_encode_h 2160`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_ENCODE_H = PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_ENCODE_H

/**
 * Debug APK + [PNS_SCREEN_PREVIEW]: when `true`, in-app video automation uses HEVC Main10
 * (10-bit) profile via [MediaCodecVideoRecorder] (`c2.qti.hevc.encoder`).
 *
 * Typical ADB: `--ez pns_preview_video_10bit true`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_TENBIT = PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_TENBIT

/**
 * Sprint 13.5: Debug APK + [PNS_SCREEN_PREVIEW]: when `true`, in-app video automation records
 * DCG (HEVC Main10HDR10 + `isHdr10=true` + `KEY_HDR_STATIC_INFO` SEI metadata) via
 * [MediaCodecVideoRecorder]. Used by `pns_video_hdr10_metadata_verify.ps1`.
 *
 * Typical ADB: `--ez pns_preview_video_dcg true`
 */
const val EXTRA_PNS_PREVIEW_VIDEO_DCG = "pns_preview_video_dcg"

/**
 * Sprint **VF.1**: seed AV1 in-app encode (`VideoCodec.AV1` + MediaCodec path).
 * Typical ADB: `--ez pns_preview_video_av1 true` with `--ei pns_preview_video_codec_ordinal 4`.
 */
const val EXTRA_PNS_PREVIEW_VIDEO_AV1 = "pns_preview_video_av1"

/**
 * Sprint **VF.1**: override [PreviewChromePreferences.inAppVideoCodecOrdinal] (e.g. AV1 = 4).
 */
const val EXTRA_PNS_PREVIEW_VIDEO_CODEC_ORDINAL =
    PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_CODEC_ORDINAL

/**
 * Sprint **VF.2**: enable preview OIS + EIS for automation (`pns_video_stabilization_test.ps1`).
 */
const val EXTRA_PNS_PREVIEW_VIDEO_STABILIZATION = "pns_preview_video_stabilization"

/**
 * Sprint **13.6**: scripted RAW video record (**`scripts/pns_raw_video_verify.ps1`**).
 * Clamped to **[0, 120]** seconds; uses [RawVideoRecordingController] (no MediaRecorder).
 */
const val EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC = PreviewAutomationExtrasRegistry.EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC

/**
 * Removes one-shot in-app / RAW video automation extras from a sticky [android.content.Intent].
 * Call after consuming values so task restore or a later launcher start does not auto-record again.
 */
internal fun Intent.stripPreviewVideoAutomationExtras() {
    removeExtra(EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC)
    removeExtra(EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC)
}

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

/**
 * Fleet matrix scan tier for host automation (**16.3**): `quick` (default on probehub) or `full`.
 * Typical ADB: `--es pns_screen probehub --es pns_fleet_matrix_scan full`
 */
const val EXTRA_PNS_FLEET_MATRIX_SCAN = "pns_fleet_matrix_scan"

/** After focal MP override push: `--ez pns_fleet_matrix_rescan true` rebuilds matrix with override-aware focalSlots. */
const val EXTRA_PNS_FLEET_MATRIX_RESCAN = "pns_fleet_matrix_rescan"

private const val FLEET_MATRIX_RESCAN_SETTLE_MS = 800L

/** Fleet Parity Sweep (M18.6): `--ez pns_auto_parity_sweep true --es pns_parity_sweep_mode quick|full|delta` */
const val EXTRA_PNS_AUTO_PARITY_SWEEP = "pns_auto_parity_sweep"
const val EXTRA_PNS_PARITY_SWEEP_MODE = "pns_parity_sweep_mode"
const val EXTRA_PNS_PARITY_SWEEP_INCLUDE_RECORD = "pns_parity_sweep_include_record"

/** When true, enables [LegacyFleetPolicyPlugin] for this process (developer / regression lane). */
const val EXTRA_PNS_LEGACY_LegacyDevice_FLEET_POLICY = "pns_legacy_device_fleet_policy"

/** Stable filename written when [EXTRA_PNS_AUTO_EXPORT_PROBE] is true. */
const val PROBE_EXPORT_LATEST_FILE = "PROBE_EXPORT_LATEST.md"

/** Value for [EXTRA_PNS_SCREEN] and system camera intents — opens [PreviewEngineScreen]. */
const val PNS_SCREEN_PREVIEW = "preview"
/** Value for [EXTRA_PNS_SCREEN] — opens [FaceMeterProbeScreen] (face / eye / AE-AF static probe). */
const val PNS_SCREEN_FACE_METER = "facemeter"
/** Value for [EXTRA_PNS_SCREEN] — interactive hardware button keyCode probe (engineering). */
const val PNS_SCREEN_HARDWARE_KEY_PROBE = "hardwarekeyprobe"

/** With [PNS_SCREEN_HARDWARE_KEY_PROBE]: synthesize KEYCODE_CAMERA and auto-save probe JSON (ADB automation). */
const val EXTRA_PNS_AUTO_HARDWARE_KEY_PROBE = "pns_auto_hardware_key_probe"

/** Value for [EXTRA_PNS_SCREEN] — headless **`CameraDevice.createExtensionSession`** smoke (Milestone 4). */
const val PNS_SCREEN_CAMERA_EXT_SMOKE = "cameraextsmoke"

/** Value for [EXTRA_PNS_SCREEN] — Sprint **28.2** isolated HDR extension handoff spike. */
const val PNS_SCREEN_EXTENSION_HANDOFF = "extensionhandoff"

/** With [PNS_SCREEN_EXTENSION_HANDOFF]: cold handoff to [PNS_SCREEN_PREVIEW] after configure (default false). */
const val EXTRA_PNS_EXTENSION_HANDOFF_RETURN_PREVIEW = "pns_extension_handoff_return_preview"

/** Set when preview resumes after [PNS_SCREEN_EXTENSION_HANDOFF] — logs preview return proof once. */
const val EXTRA_PNS_AFTER_EXTENSION_HANDOFF = "pns_after_extension_handoff"

/**
 * With **`pns_screen=rootsettings`**: after persisted **Grant Su** ([`RootCapability.RootState.Granted`]),
 * run read-only [`RootPrivilegedDiagnostics.runScan`] once and log **`PNS.AdbValidation`** **`rootPrivScan …`**
 * (Milestone **7.5** automation).
 */
const val EXTRA_PNS_AUTO_ROOT_DIAGNOSTICS = "pns_auto_root_diagnostics"

/** Value for [EXTRA_PNS_SCREEN] — unified mock preview (GLES test pattern + Pro HUD); ADR-0008 / T.14. */
const val PNS_SCREEN_MOCK = "mock"

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
    themeMode: PnsThemeMode = PnsThemeMode.System,
    onThemeModeChange: (PnsThemeMode) -> Unit = {},
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
    previewLaunchExtras: PreviewLaunchExtras = PreviewLaunchExtras.EMPTY,
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
    // Preview routes must open synchronously — deferring via LaunchedEffect flashed DebugMenuScreen for one frame.
    var showPreviewEngine by remember(launchScreen) {
        mutableStateOf(launchScreen == PNS_SCREEN_PREVIEW)
    }
    var showEncoderProbe by remember { mutableStateOf(false) }
    var showLegacyCamera1 by remember { mutableStateOf(false) }
    var showDeepCaps by remember { mutableStateOf(false) }
    var showFleetMatrix by remember { mutableStateOf(false) }
    var showSessionMatrix by remember { mutableStateOf(false) }
    var showHdrDcgRuntime by remember { mutableStateOf(false) }
    var showCaptureLatency by remember { mutableStateOf(false) }
    var showRawHdrExcl by remember { mutableStateOf(false) }
    var showBurstProbe by remember { mutableStateOf(false) }
    var showCameraExtSmoke by remember { mutableStateOf(false) }
    var showExtensionHandoff by remember { mutableStateOf(false) }
    var showLogicalPhysical by remember { mutableStateOf(false) }
    var showExhaustive by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var aboutOpenedFromPreview by remember { mutableStateOf(false) }
    var aboutLiveSummary by remember { mutableStateOf<EncoderSummary?>(null) }
    var aboutHalHfrMaxByCameraId by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) }
    var showMockPreview by remember { mutableStateOf(false) }
    var mockPreviewLaunchRoute by remember { mutableStateOf(MockPreviewScreens.ROUTE_MOCK) }
    var showHudSettings by remember { mutableStateOf(false) }
    var hudSettingsFocus by remember { mutableStateOf(HudSettingsFocus.None) }
    var showCalibrate by remember { mutableStateOf(false) }
    var showLutImport by remember { mutableStateOf(false) }
    var showNativeDiagnostics by remember { mutableStateOf(false) }
    var showRootSettings by remember { mutableStateOf(false) }
    var showFaceMeterProbe by remember { mutableStateOf(false) }
    var showHardwareKeyProbe by remember { mutableStateOf(false) }
    var showQrScan by remember { mutableStateOf(false) }
    var showDebugMenu by remember { mutableStateOf(false) }
    var showEyeOverlayCalibrator by remember { mutableStateOf(false) }
    var previewLaunchedFromDebug by remember { mutableStateOf(false) }
    var fleetMatrixFeaturesQuery by remember { mutableStateOf<String?>(null) }
    var hudHighlightSettingKey by remember { mutableStateOf<String?>(null) }
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
    // Do not `remember` ADB intent extras — cold `am start --es pns_fleet_matrix_scan full` must be read fresh
    // (same stale-cache class as preview automation extras below).
    val fleetMatrixScanTier =
        activity?.intent?.getStringExtra(EXTRA_PNS_FLEET_MATRIX_SCAN)?.lowercase()?.trim()
    val fleetMatrixRescan =
        activity?.intent?.getBooleanExtra(EXTRA_PNS_FLEET_MATRIX_RESCAN, false) ?: false
    var fleetMatrixRescanDone by remember { mutableStateOf(false) }
    val legacyOp13FleetPolicy =
        activity?.intent?.getBooleanExtra(EXTRA_PNS_LEGACY_LegacyDevice_FLEET_POLICY, false) ?: false
    var fleetMatrixFullScanDone by remember { mutableStateOf(false) }
    val intentIncludeLogical = activity?.intent?.getBooleanExtra(EXTRA_PNS_INCLUDE_LOGICAL, false) ?: false
    val intentExhaustiveHfrOnly = activity?.intent?.getBooleanExtra(EXTRA_PNS_EXHAUSTIVE_HFR_ONLY, false) ?: false
    val effectiveIncludeLogical = exhaustiveIncludeLogical || intentIncludeLogical
    val effectiveExhaustiveHfrOnly = exhaustiveHfrOnly || intentExhaustiveHfrOnly

    fun openMockPreview(route: String = MockPreviewScreens.ROUTE_MOCK) {
        mockPreviewLaunchRoute = MockPreviewScreens.normalizeRoute(route)
        showMockPreview = true
    }

    fun navigateProbeFromTitle(title: String, dismissDebugMenu: Boolean) {
        if (dismissDebugMenu) showDebugMenu = false
        when (title) {
            "Dodge lens mapping" -> showMapping = true
            "Live preview (engine)" -> {
                previewLaunchedFromDebug = true
                activity?.intent?.stripPreviewVideoAutomationExtras()
                showPreviewEngine = true
            }
            "Eye overlay calibration" -> {
                previewLaunchedFromDebug = true
                showEyeOverlayCalibrator = true
                showPreviewEngine = true
            }
            "QR / barcode scan" -> showQrScan = true
            "Legacy Camera1 probe" -> showLegacyCamera1 = true
            "Deep capabilities" -> showDeepCaps = true
            "Device capability matrix" -> showFleetMatrix = true
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
            "Mock preview (HUD + GLES)" -> openMockPreview(MockPreviewScreens.ROUTE_MOCK)
            "Mock preview (HUD + GLES)" -> openMockPreview(MockPreviewScreens.ROUTE_MOCK)
            "Live GL LUT preview" -> openMockPreview(MockPreviewScreens.ROUTE_GLPREVIEW)
            "Pro HUD (mock)" -> openMockPreview(MockPreviewScreens.ROUTE_PROHUD)
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

    fun handleProbeHubSearchPick(pick: ProbeHubSearchPick, dismissDebugMenu: Boolean) {
        if (dismissDebugMenu) showDebugMenu = false
        when (pick) {
            is ProbeHubSearchPick.HubMenu -> navigateProbeFromTitle(pick.title, dismissDebugMenu = dismissDebugMenu)
            is ProbeHubSearchPick.CatalogFeature -> {
                fleetMatrixFeaturesQuery = pick.displayName
                showFleetMatrix = true
            }
            is ProbeHubSearchPick.ChromeSetting -> {
                val hit = pick.hit
                Log.i(
                    TAG_PROBE_HUB,
                    "settingsSearchPick title=${hit.title} subPage=${hit.subPage} key=${hit.settingKey}",
                )
                if (hit.subPage == "hud" && hit.settingKey != null) {
                    hudHighlightSettingKey = hit.settingKey
                    hudSettingsFocus = HudSettingsFocus.None
                    showHudSettings = true
                } else {
                    PendingChromeSettingsNavigation.stash(hit)
                    previewLaunchedFromDebug = true
                    showPreviewEngine = true
                }
            }
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
        if (launchScreen == PNS_SCREEN_HARDWARE_KEY_PROBE) {
            showHardwareKeyProbe = true
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
        } else if (launchScreen == PNS_SCREEN_EXTENSION_HANDOFF) {
            showExtensionHandoff = true
        } else if (launchScreen == SCREEN_LOGICAL_PHYSICAL) {
            showLogicalPhysical = true
        } else if (launchScreen == SCREEN_EXHAUSTIVE) {
            showExhaustive = true
        } else if (launchScreen == SCREEN_CAMERA1) {
            showLegacyCamera1 = true
        } else if (launchScreen == SCREEN_ABOUT) {
            showAbout = true
        } else if (launchScreen == SCREEN_PROHUD) {
            openMockPreview(MockPreviewScreens.ROUTE_PROHUD)
        } else if (launchScreen == SCREEN_HUDSETTINGS) {
            showHudSettings = true
        } else if (launchScreen == SCREEN_CALIBRATE) {
            showCalibrate = true
        } else if (launchScreen == SCREEN_LUTIMPORT) {
            showLutImport = true
        } else if (launchScreen == SCREEN_GLPREVIEW) {
            openMockPreview(MockPreviewScreens.ROUTE_GLPREVIEW)
        } else if (launchScreen == PNS_SCREEN_MOCK) {
            openMockPreview(MockPreviewScreens.ROUTE_MOCK)
        } else if (launchScreen == SCREEN_NATIVE) {
            showNativeDiagnostics = true
        } else if (launchScreen == SCREEN_ROOT_SETTINGS) {
            showRootSettings = true
        }
    }

    LaunchedEffect(hasCameraPermission, launchScreen, fleetMatrixScanTier) {
        val autoParity = activity?.intent?.getBooleanExtra(EXTRA_PNS_AUTO_PARITY_SWEEP, false) == true
        if (autoParity || (launchScreen == PNS_SCREEN_PROBE_HUB && !fleetMatrixScanTier.isNullOrBlank())) {
            showFleetMatrix = true
        }
    }

    LaunchedEffect(hasCameraPermission, launchScreen, shallowRescanSeq) {
        if (launchScreen == PNS_SCREEN_FACE_METER || launchScreen == PNS_SCREEN_QR_SCAN || launchScreen == PNS_SCREEN_HARDWARE_KEY_PROBE) {
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
                        inz.getStringExtra(EXTRA_PNS_SCREEN) == PNS_SCREEN_EXTENSION_HANDOFF ||
                        (
                            inz.getStringExtra(EXTRA_PNS_SCREEN) == PNS_SCREEN_PREVIEW &&
                                (
                                    (inz.getIntExtra(EXTRA_PNS_PREVIEW_RAW_COUNT, 0) ?: 0) > 0 ||
                                        inz.previewBracketExtra() != null ||
                                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_DIAL).isNullOrBlank() ||
                                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_IMAGING_PROFILE).isNullOrBlank() ||
                                        !inz.getStringExtra(EXTRA_PNS_PREVIEW_STILL_FORMAT).isNullOrBlank() ||
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

    LaunchedEffect(legacyOp13FleetPolicy) {
        if (legacyOp13FleetPolicy) {
            FleetPolicyPreferences.setLegacyOp13Enabled(appCtx, true)
            FleetCameraProfiles.invalidateMemoryCache()
        }
    }

    // Do not key on [fleetMatrixFullScanDone] — setting it true at start used to cancel this effect immediately.
    LaunchedEffect(hasCameraPermission, fleetMatrixScanTier) {
        if (!hasCameraPermission || fleetMatrixScanTier != "full" || fleetMatrixFullScanDone) return@LaunchedEffect
        fleetMatrixFullScanDone = true
        Log.i(FleetDeviceMatrixBuilder.TAG, "scanTier=full starting (adb pns_fleet_matrix_scan)")
        // API 28+ rejects openCamera until the hosting activity is RESUMED (not merely STARTED).
        val act = activity
        if (act != null) {
            var waited = 0
            while (act.lifecycle.currentState < Lifecycle.State.RESUMED && waited < 200) {
                delay(100)
                waited++
            }
        }
        delay(1500)
        withContext(Dispatchers.IO) {
            runCatching { FleetDeviceMatrixBuilder.buildFullAndSave(appCtx) }
                .onFailure { e -> Log.e(FleetDeviceMatrixBuilder.TAG, "scanTier=full failed", e) }
        }
    }

    LaunchedEffect(hasCameraPermission, fleetMatrixRescan) {
        if (!hasCameraPermission || !fleetMatrixRescan || fleetMatrixRescanDone) return@LaunchedEffect
        fleetMatrixRescanDone = true
        Log.i(FleetDeviceMatrixBuilder.TAG, "scanTier=quick rescan after focal MP override")
        val act = activity
        if (act != null) {
            var waited = 0
            while (act.lifecycle.currentState < Lifecycle.State.RESUMED && waited < 200) {
                delay(100)
                waited++
            }
        }
        delay(FLEET_MATRIX_RESCAN_SETTLE_MS)
        withContext(Dispatchers.IO) {
            runCatching { FleetDeviceMatrixBuilder.buildQuickAndSave(appCtx, forceRescan = true) }
                .onFailure { e -> Log.e(FleetDeviceMatrixBuilder.TAG, "focal MP rescan failed", e) }
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
        val adbStillFormatRaw = activity?.intent.previewStillFormatExtra()
        val adbStillResolutionModeRaw = activity?.intent.previewStillResolutionModeExtra()
        val adbRawStreamFromIntent = activity?.intent.previewRawStreamPreferenceExtra()
        val adbJpegCompanionFromIntent = activity?.intent.previewJpegCompanionSeedExtra()
        val adbStripExifFromIntent = activity?.intent.previewStripExifSeedExtra()
        val adbCameraIdRaw = activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_CAMERA_ID)?.trim()?.takeIf { it.isNotBlank() }
        val adbPreviewStillsLutName = activity?.intent.previewStillsLutNameExtra()
        val adbM6FpsLutProbe = activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_M6_FPS_LUT_PROBE, false) ?: false
        val adbCalibrateGrabSmoke =
            activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_CALIBRATE_GRAB_SMOKE, false) ?: false
        val adbSelfTimerSec = activity?.intent.previewSelfTimerSecExtra()
        val adbFocalMmSlotProbe = activity?.intent.previewFocalMmSlotExtra()
        val adbReadoutIsoProbe = activity?.intent.previewReadoutIsoProbeExtra()
        val adbReadoutShutterNsProbe = activity?.intent.previewReadoutShutterNsProbeExtra()
        val adbApertureCycleProbe = activity?.intent.previewApertureCycleProbeExtra()
        val adbVideoShutterAngleProbe = activity?.intent.previewVideoShutterAngleExtra()
        val intentPrimaryPhotoSeed =
            activity?.intent?.takeIf { it.hasExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO) }
                ?.getBooleanExtra(EXTRA_PNS_PREVIEW_PRIMARY_PHOTO, true)
        val previewSeedPrimaryPhoto =
            previewLaunchExtras.primaryPhoto
                ?: intentPrimaryPhotoSeed
                ?: (
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
        val adbEyeAfOverlaySeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewEyeAfOverlayExtra() else null
        val adbAudioHiFiSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewAudioHiFiExtra() else null
        val adbAudioWindSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewAudioWindExtra() else null
        val adbWindNoiseFilterSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewWindNoiseFilterExtra() else null
        val adbExperimentalMasterSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewExperimentalMasterExtra() else null
        val adbExperimentalMaxResUnlockSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewExperimentalMaxResUnlockExtra() else null
        val adbExperimentalVendorSessionSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewExperimentalVendorSessionExtra() else null
        val adbForceSafeModeSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewForceSafeModeExtra() else false
        val adbMaxResSweepSessionKeys =
            if (trustIntentForPreviewPipeline) activity?.intent.previewMaxResSweepSessionKeysExtra().orEmpty() else emptyList()
        val adbMaxResSweepRequestKeys =
            if (trustIntentForPreviewPipeline) activity?.intent.previewMaxResSweepRequestKeysExtra().orEmpty() else emptyList()
        val appCtx = context.applicationContext
        remember(
            trustIntentForPreviewPipeline,
            adbExperimentalMasterSeed,
            adbExperimentalMaxResUnlockSeed,
            adbExperimentalVendorSessionSeed,
            adbForceSafeModeSeed,
            adbMaxResSweepSessionKeys,
            adbMaxResSweepRequestKeys,
        ) {
            if (trustIntentForPreviewPipeline) {
                if (
                    adbExperimentalMasterSeed != null ||
                    adbExperimentalMaxResUnlockSeed != null ||
                    adbExperimentalVendorSessionSeed != null ||
                    adbForceSafeModeSeed ||
                    adbMaxResSweepSessionKeys.isNotEmpty() ||
                    adbMaxResSweepRequestKeys.isNotEmpty()
                ) {
                    PnsAdbLog.i(
                        appCtx,
                        "preview preseed experimental master=$adbExperimentalMasterSeed " +
                            "maxRes=$adbExperimentalMaxResUnlockSeed vendorSession=$adbExperimentalVendorSessionSeed " +
                            "forceSafe=$adbForceSafeModeSeed sweepSession=${adbMaxResSweepSessionKeys.size} " +
                            "sweepRequest=${adbMaxResSweepRequestKeys.size}",
                    )
                }
                if (adbForceSafeModeSeed) {
                    ExperimentalSafeModeStore.forceSafeMode(appCtx, "adb_seed")
                    ExperimentalSafeModeStore.disableExperimentalFlags(appCtx)
                } else {
                    if (
                        adbExperimentalMasterSeed != null ||
                        adbExperimentalMaxResUnlockSeed != null ||
                        adbExperimentalVendorSessionSeed != null
                    ) {
                        ExperimentalSafeModeStore.clearSafeMode(appCtx)
                    }
                    val current = HudSettings.load(appCtx)
                    var next = current
                    adbExperimentalMasterSeed?.let { want ->
                        if (next.enableExperimentalAppBreakingFeatures != want) {
                            next = next.copy(enableExperimentalAppBreakingFeatures = want)
                        }
                    }
                    adbExperimentalMaxResUnlockSeed?.let { want ->
                        if (next.enableExperimentalMaxResolutionUnlock != want) {
                            next = next.copy(enableExperimentalMaxResolutionUnlock = want)
                        }
                    }
                    adbExperimentalVendorSessionSeed?.let { want ->
                        if (next.enableExperimentalVendorSessionKeys != want) {
                            next = next.copy(enableExperimentalVendorSessionKeys = want)
                        }
                    }
                    if (next != current) {
                        HudSettings.save(appCtx, next)
                        PnsAdbLog.i(
                            appCtx,
                            "preview preseed experimental persisted " +
                                "master=${next.enableExperimentalAppBreakingFeatures} " +
                                "maxRes=${next.enableExperimentalMaxResolutionUnlock} " +
                                "vendorSession=${next.enableExperimentalVendorSessionKeys}",
                        )
                    }
                }
            }
            Unit
        }
        val adbAeLockSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewAeLockExtra() else null
        val adbPipPreviewSeed =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_PIP, false) ?: false
            } else {
                false
            }
        val adbMulticamMeltSeed =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_MULTICAM_MELT, false) ?: false
            } else {
                false
            }
        val adbTimelapseModeSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewTimelapseModeExtra() else null
        val adbTimelapseRunningSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewTimelapseRunningExtra() else null
        val adbTimelapseIntervalSecSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewTimelapseIntervalSecExtra() else null
        val adbTimelapseAutoStopSecSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewTimelapseAutoStopSecExtra() else null
        val adbFocusBreathingCompSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewFocusBreathingCompExtra() else null
        val adbAutomationFocusRackSec =
            if (trustIntentForPreviewPipeline) activity?.intent.previewAutomationFocusRackSecExtra() else null
        val adbRackFocusPull =
            if (trustIntentForPreviewPipeline) activity?.intent.previewRackFocusPullExtra() else false
        val adbShutterSoundPackSeed =
            if (trustIntentForPreviewPipeline) activity?.intent.previewShutterSoundPackExtra() else null
        val adbComposedStillSmoke =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_COMPOSED_STILL, false) ?: false
            } else {
                false
            }
        // Consume once per activity instance; strip extras so recomposition / task restore cannot re-arm recording.
        val intentInAppVideoAutomationSec =
            remember(activity) {
                activity?.intent?.let { intent ->
                    if (!intent.hasExtra(EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC)) return@let 0
                    intent.getIntExtra(EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC, 0)
                        .coerceIn(0, 120)
                        .also { intent.removeExtra(EXTRA_PNS_PREVIEW_AUTOMATION_IN_APP_VIDEO_SEC) }
                } ?: 0
            }
        val intentRawVideoAutomationSec =
            remember(activity) {
                activity?.intent?.let { intent ->
                    if (!intent.hasExtra(EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC)) return@let 0
                    intent.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC, 0)
                        .coerceIn(0, 120)
                        .also { intent.removeExtra(EXTRA_PNS_PREVIEW_VIDEO_RAW_SEC) }
                } ?: 0
            }
        val automationWantsIntentPipeline =
            adbRawCountRaw > 0 ||
                adbBracketRaw != null ||
                launchScreen == PNS_SCREEN_PREVIEW ||
                !(activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_DIAL).isNullOrBlank()) ||
                (launchScreen == PNS_SCREEN_PREVIEW && intentInAppVideoAutomationSec > 0) ||
                (launchScreen == PNS_SCREEN_PREVIEW && intentRawVideoAutomationSec > 0) ||
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SMILE_STILL, false) == true ||
                !(activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_MAX_RES_SWEEP_SESSION_KEYS).isNullOrBlank()) ||
                !(activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_MAX_RES_SWEEP_REQUEST_KEYS).isNullOrBlank()) ||
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_BITRATE_SCALE, 0) ?: 0) > 0
        val useIntentAutomationPipeline = trustIntentForPreviewPipeline && automationWantsIntentPipeline
        val adbAutomationVideoRawSec =
            when {
                previewLaunchExtras.rawVideoAutomationSec > 0 ->
                    previewLaunchExtras.rawVideoAutomationSec
                trustIntentForPreviewPipeline && launchScreen == PNS_SCREEN_PREVIEW ->
                    intentRawVideoAutomationSec
                else -> 0
            }
        val adbAutomationInAppVideoSec =
            when {
                previewLaunchExtras.inAppVideoAutomationSec > 0 ->
                    previewLaunchExtras.inAppVideoAutomationSec
                trustIntentForPreviewPipeline && launchScreen == PNS_SCREEN_PREVIEW ->
                    intentInAppVideoAutomationSec
                else -> 0
            }
        val adbAutomationVideoFps =
            previewLaunchExtras.videoFps
                ?: if (trustIntentForPreviewPipeline) {
                    (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_FPS, 0) ?: 0)
                        .let { if (it > 0) it else null }
                } else {
                    null
                }
        val adbAutomationVideoEncodeW =
            previewLaunchExtras.videoEncodeW
                ?: if (trustIntentForPreviewPipeline) {
                    (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_ENCODE_W, 0) ?: 0)
                        .let { if (it > 0) it else null }
                } else {
                    null
                }
        val adbAutomationVideoEncodeH =
            previewLaunchExtras.videoEncodeH
                ?: if (trustIntentForPreviewPipeline) {
                    (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_ENCODE_H, 0) ?: 0)
                        .let { if (it > 0) it else null }
                } else {
                    null
                }
        val adbAutomationVideoTenBit =
            if (previewLaunchExtras.wantsVideoAutomation) {
                previewLaunchExtras.videoTenBit
            } else if (trustIntentForPreviewPipeline) {
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
        val adbAutomationVideoAv1 =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_VIDEO_AV1, false) ?: false
            } else {
                false
            }
        val adbAutomationVideoCodecOrdinal =
            previewLaunchExtras.videoCodecOrdinal
                ?: if (trustIntentForPreviewPipeline) {
                    (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_VIDEO_CODEC_ORDINAL, -1) ?: -1)
                        .takeIf { it >= 0 }
                } else {
                    null
                }
        val adbAutomationVideoStabilization =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_VIDEO_STABILIZATION, false) ?: false
            } else {
                false
            }
        val adbAutomationBtMediaKey =
            if (trustIntentForPreviewPipeline && launchScreen == PNS_SCREEN_PREVIEW) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_AUTOMATION_BT_MEDIA_KEY, false) ?: false
            } else {
                false
            }
        val adbAutomationHardwareKey =
            if (trustIntentForPreviewPipeline && launchScreen == PNS_SCREEN_PREVIEW) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_AUTOMATION_HARDWARE_KEY, false) ?: false
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
        val adbBurstStillCount =
            if (trustIntentForPreviewPipeline) {
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_BURST_COUNT, 0) ?: 0).coerceAtLeast(0)
            } else {
                0
            }
        val adbBurstIntervalMs =
            if (trustIntentForPreviewPipeline) {
                val raw = activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_BURST_INTERVAL_MS, 0) ?: 0
                if (raw > 0) raw else 17
            } else {
                0
            }
        val adbLongPressBurstHoldMs =
            if (trustIntentForPreviewPipeline) {
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_LONGPRESS_BURST_HOLD_MS, 0) ?: 0)
                    .coerceAtLeast(0)
            } else {
                0
            }
        val adbBurstFileProfile =
            if (trustIntentForPreviewPipeline) {
                when (activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_BURST_FILE)?.trim()?.lowercase()) {
                    "raw" -> BurstPhotoQualityProfile.RawOnly
                    "jpeg" -> BurstPhotoQualityProfile.ProcessedOnly
                    else -> null
                }
            } else {
                null
            }
        val adbBurstPipelineStrategy =
            if (trustIntentForPreviewPipeline) {
                BurstPipelineStrategy.parse(
                    activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_BURST_STRATEGY),
                )
            } else {
                null
            }
        val adbTetherEnabled =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_TETHER, false) ?: false
            } else {
                false
            }
        val adbWifiDirectTether =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_WIFI_DIRECT_TETHER, false) ?: false
            } else {
                false
            }
        val adbPictureProfileId =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_PICTURE_PROFILE)?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        val adbFlashStrengthPercent =
            if (trustIntentForPreviewPipeline) {
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_FLASH_STRENGTH, 0) ?: 0)
                    .takeIf {
                        it in HudSettings.PREVIEW_FLASH_STRENGTH_MIN..HudSettings.PREVIEW_FLASH_STRENGTH_MAX
                    }
            } else {
                null
            }
        val adbCalExportSmoke =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_CAL_EXPORT, false) ?: false
            } else {
                false
            }
        val adbSettingsExportSmoke =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SETTINGS_EXPORT, false) ?: false
            } else {
                false
            }
        val adbSettingsImportSmoke =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SETTINGS_IMPORT, false) ?: false
            } else {
                false
            }
        val adbPreviewThemeMode =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_THEME_MODE)?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { PnsThemeMode.fromStorage(it) }
            } else {
                null
            }
        val adbWorkflowPresetId =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_WORKFLOW_PRESET)?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        val adbOpenGallery =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_OPEN_GALLERY, false) ?: false
            } else {
                false
            }
        val adbGalleryBatchShareCount =
            if (trustIntentForPreviewPipeline) {
                (activity?.intent?.getIntExtra(EXTRA_PNS_PREVIEW_GALLERY_BATCH_SHARE, 0) ?: 0)
                    .takeIf { it >= 2 }
            } else {
                null
            }
        val adbCloudBackupEnabled =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_CLOUD_BACKUP, false) ?: false
            } else {
                false
            }
        val adbCloudBackupSyncNow =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_CLOUD_BACKUP_SYNC, false) ?: false
            } else {
                false
            }
        val adbCloudBackupProbe =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_CLOUD_BACKUP_PROBE, false) ?: false
            } else {
                false
            }
        val adbPlatformShareProbe =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_PLATFORM_SHARE_PROBE, false) ?: false
            } else {
                false
            }
        val adbPlatformFileProviderProbe =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_PLATFORM_FILE_PROVIDER_PROBE, false) ?: false
            } else {
                false
            }
        val adbPlatformWidgetProbe =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_PLATFORM_WIDGET_PROBE, false) ?: false
            } else {
                false
            }
        val adbLanTransfer =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_LAN_TRANSFER, false) ?: false
            } else {
                false
            }
        val adbLanTransferProbe =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_LAN_TRANSFER_PROBE, false) ?: false
            } else {
                false
            }
        val adbWebDavProbe =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_WEBDAV_PROBE, false) ?: false
            } else {
                false
            }
        val adbSocialStreamProbe =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SOCIAL_STREAM_PROBE, false) ?: false
            } else {
                false
            }
        val adbCollaborativeProbe =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_COLLABORATIVE_PROBE, false) ?: false
            } else {
                false
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
        val adbOpenSettingsRail =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_OPEN_SETTINGS, false) ?: false
            } else {
                false
            }
        val adbSequentialRawStills =
            when {
                previewLaunchExtras.wantsVideoAutomation -> 0
                !trustIntentForPreviewPipeline -> 0
                adbAutomationInAppVideoSec > 0 || adbAutomationVideoRawSec > 0 -> 0
                else -> adbRawCountRaw
            }
        val adbBracketPattern = if (trustIntentForPreviewPipeline) adbBracketRaw else null
        val adbRawStreamPreference = if (useIntentAutomationPipeline) adbRawStreamFromIntent else null
        val adbJpegCompanionSeed = if (useIntentAutomationPipeline) adbJpegCompanionFromIntent else null
        val adbStripExifPrivacySeed = if (useIntentAutomationPipeline) adbStripExifFromIntent else null
        val adbRawStillFastAutomation = if (trustIntentForPreviewPipeline) adbRawStillFastRaw else false
        val adbInitialDial =
            when {
                previewLaunchExtras.inAppVideoAutomationSec > 0 -> CommandDialMode.Auto
                previewLaunchExtras.rawVideoAutomationSec > 0 ->
                    adbDialRaw?.takeUnless { it == CommandDialMode.H } ?: CommandDialMode.Auto
                !trustIntentForPreviewPipeline -> null
                adbAutomationInAppVideoSec > 0 || adbAutomationVideoRawSec > 0 ->
                    adbDialRaw?.takeUnless { it == CommandDialMode.H } ?: CommandDialMode.Auto
                else -> adbDialRaw
            }
        val secureLaunchSession =
            when (activity?.intent?.action) {
                MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE,
                MediaStore.ACTION_IMAGE_CAPTURE_SECURE,
                -> true
                else -> false
            }
        val adbInitialImagingProfile = if (trustIntentForPreviewPipeline) adbImagingProfileRaw else null
        val adbStillExportFormat = if (trustIntentForPreviewPipeline) adbStillFormatRaw else null
        val adbPhotoResolutionMode = if (trustIntentForPreviewPipeline) adbStillResolutionModeRaw else null
        val adbSeedCameraId = if (trustIntentForPreviewPipeline) adbCameraIdRaw else null
        val adbSuperMacroProbe =
            if (trustIntentForPreviewPipeline) {
                activity?.intent?.getBooleanExtra(EXTRA_PNS_PREVIEW_SUPER_MACRO_PROBE, false) ?: false
            } else {
                false
            }
        val adbAfterExtensionHandoff =
            remember(activity) {
                activity?.intent?.let { intent ->
                    if (!intent.hasExtra(EXTRA_PNS_AFTER_EXTENSION_HANDOFF)) return@let false
                    intent.getBooleanExtra(EXTRA_PNS_AFTER_EXTENSION_HANDOFF, false)
                        .also { intent.removeExtra(EXTRA_PNS_AFTER_EXTENSION_HANDOFF) }
                } ?: false
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
                        showEyeOverlayCalibrator = false
                        if (previewLaunchedFromDebug) {
                            showDebugMenu = true
                        }
                        previewLaunchedFromDebug = false
                    }
                }
            },
            onOpenDeveloperMenu = {
                showPreviewEngine = false
                showEyeOverlayCalibrator = false
                showDebugMenu = true
            },
            eyeOverlayCalibratorActive = showEyeOverlayCalibrator,
            onEyeOverlayCalibratorDone = {
                showEyeOverlayCalibrator = false
                if (previewLaunchedFromDebug) {
                    showDebugMenu = true
                }
            },
            startAutoSweep = autoSweep,
            adbInitialDial = adbInitialDial,
            secureLaunchSession = secureLaunchSession,
            adbSequentialRawStills = adbSequentialRawStills,
            adbRawStillFastAutomation = adbRawStillFastAutomation,
            adbBracketPattern = adbBracketPattern,
            adbInitialImagingProfile = adbInitialImagingProfile,
            adbStillExportFormat = adbStillExportFormat,
            adbPhotoResolutionMode = adbPhotoResolutionMode,
            adbRawStreamPreference = adbRawStreamPreference,
            adbJpegCompanionSeed = adbJpegCompanionSeed,
            adbStripExifPrivacySeed = adbStripExifPrivacySeed,
            adbAudioHiFiSeed = adbAudioHiFiSeed,
            adbAudioWindSeed = adbAudioWindSeed,
            adbWindNoiseFilterSeed = adbWindNoiseFilterSeed,
            adbExperimentalMasterSeed = adbExperimentalMasterSeed,
            adbExperimentalMaxResUnlockSeed = adbExperimentalMaxResUnlockSeed,
            adbExperimentalVendorSessionSeed = adbExperimentalVendorSessionSeed,
            adbForceSafeModeSeed = adbForceSafeModeSeed,
            adbMaxResSweepSessionKeys = adbMaxResSweepSessionKeys,
            adbMaxResSweepRequestKeys = adbMaxResSweepRequestKeys,
            adbAeLockSeed = adbAeLockSeed,
            adbPipPreviewSeed = adbPipPreviewSeed,
            adbMulticamMeltSeed = adbMulticamMeltSeed,
            adbTimelapseModeSeed = adbTimelapseModeSeed,
            adbTimelapseRunningSeed = adbTimelapseRunningSeed,
            adbTimelapseIntervalSecSeed = adbTimelapseIntervalSecSeed,
            adbTimelapseAutoStopSecSeed = adbTimelapseAutoStopSecSeed,
            adbFocusBreathingCompSeed = adbFocusBreathingCompSeed,
            adbAutomationFocusRackSec = adbAutomationFocusRackSec,
            adbRackFocusPull = adbRackFocusPull,
            adbShutterSoundPackSeed = adbShutterSoundPackSeed,
            adbComposedStillSmoke = adbComposedStillSmoke,
            adbSeedCameraId = adbSeedCameraId,
            adbSuperMacroProbe = adbSuperMacroProbe,
            adbPreviewStillsLutName = adbPreviewStillsLutName,
            adbM6FpsLutProbe = adbM6FpsLutProbe,
            adbCalibrateGrabSmoke = adbCalibrateGrabSmoke,
            adbInitialSelfTimerSec = adbSelfTimerSec,
            adbFocalMmSlotProbe = adbFocalMmSlotProbe,
            adbReadoutIsoProbe = adbReadoutIsoProbe,
            adbReadoutShutterNsProbe = adbReadoutShutterNsProbe,
            adbApertureCycleProbe = adbApertureCycleProbe,
            adbVideoShutterAngleProbe = adbVideoShutterAngleProbe,
            adbEyeAfOverlaySeed = adbEyeAfOverlaySeed,
            imageCaptureReturn = imageCaptureReturn,
            videoCaptureReturn = videoCaptureReturn,
            initialPrimaryPhoto = previewSeedPrimaryPhoto,
            adbAutomationInAppVideoSec = adbAutomationInAppVideoSec,
            adbAutomationVideoFps = adbAutomationVideoFps,
            adbAutomationVideoEncodeW = adbAutomationVideoEncodeW,
            adbAutomationVideoEncodeH = adbAutomationVideoEncodeH,
            adbAutomationVideoTenBit = adbAutomationVideoTenBit,
            adbAutomationVideoDcg = adbAutomationVideoDcg,
            adbAutomationVideoAv1 = adbAutomationVideoAv1,
            adbAutomationVideoCodecOrdinal = adbAutomationVideoCodecOrdinal,
            adbAutomationVideoStabilization = adbAutomationVideoStabilization,
            adbAutomationVideoRawSec = adbAutomationVideoRawSec,
            adbAutomationBtMediaKey = adbAutomationBtMediaKey,
            adbAutomationHardwareKey = adbAutomationHardwareKey,
            adbDngBisectActive = adbDngBisectActive,
            adbStillCaptureMode = adbStillCaptureMode,
            adbBurstStillCount = adbBurstStillCount,
            adbBurstIntervalMs = adbBurstIntervalMs,
            adbLongPressBurstHoldMs = adbLongPressBurstHoldMs,
            adbBurstFileProfile = adbBurstFileProfile,
            adbBurstPipelineStrategy = adbBurstPipelineStrategy,
            adbTetherEnabled = adbTetherEnabled,
            adbWifiDirectTether = adbWifiDirectTether,
            adbPictureProfileId = adbPictureProfileId,
            adbFlashStrengthPercent = adbFlashStrengthPercent,
            adbCalExportSmoke = adbCalExportSmoke,
            adbSettingsExportSmoke = adbSettingsExportSmoke,
            adbSettingsImportSmoke = adbSettingsImportSmoke,
            adbPreviewThemeMode = adbPreviewThemeMode,
            adbWorkflowPresetId = adbWorkflowPresetId,
            adbOpenGallery = adbOpenGallery,
            adbGalleryBatchShareCount = adbGalleryBatchShareCount,
            adbCloudBackupEnabled = adbCloudBackupEnabled,
            adbCloudBackupSyncNow = adbCloudBackupSyncNow,
            adbCloudBackupProbe = adbCloudBackupProbe,
            adbPlatformShareProbe = adbPlatformShareProbe,
            adbPlatformFileProviderProbe = adbPlatformFileProviderProbe,
            adbPlatformWidgetProbe = adbPlatformWidgetProbe,
            adbLanTransfer = adbLanTransfer,
            adbLanTransferProbe = adbLanTransferProbe,
            adbWebDavProbe = adbWebDavProbe,
            adbSocialStreamProbe = adbSocialStreamProbe,
            adbCollaborativeProbe = adbCollaborativeProbe,
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
            adbOpenSettingsRail = adbOpenSettingsRail,
            adbAfterExtensionHandoff = adbAfterExtensionHandoff,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )
        return
    }

    if (showExtensionHandoff) {
        val adbCameraId =
            activity?.intent?.getStringExtra(EXTRA_PNS_PREVIEW_CAMERA_ID)?.trim()?.takeIf { it.isNotBlank() }
        val returnToPreview =
            activity?.intent?.getBooleanExtra(EXTRA_PNS_EXTENSION_HANDOFF_RETURN_PREVIEW, false) ?: false
        ExtensionHandoffSpikeScreen(
            onBack = { showExtensionHandoff = false },
            preferredCameraId = adbCameraId,
            returnToPreview = returnToPreview,
            finishActivityWhenDone = launchScreen == PNS_SCREEN_EXTENSION_HANDOFF,
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

    if (showFleetMatrix) {
        FleetMatrixHubScreen(
            onBack = {
                showFleetMatrix = false
                fleetMatrixFeaturesQuery = null
            },
            initialFeaturesQuery = fleetMatrixFeaturesQuery,
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

    if (showMockPreview) {
        UnifiedMockPreviewScreen(
            onBack = { showMockPreview = false },
            launchRoute = mockPreviewLaunchRoute,
        )
        return
    }

    if (showHudSettings) {
        HudSettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onBack = {
                showHudSettings = false
                hudSettingsFocus = HudSettingsFocus.None
                hudHighlightSettingKey = null
            },
            initialFocus = hudSettingsFocus,
            highlightSettingKey = hudHighlightSettingKey,
            onHighlightConsumed = { hudHighlightSettingKey = null },
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

    if (showHardwareKeyProbe) {
        val autoHardwareKeyProbe =
            activity?.intent?.getBooleanExtra(EXTRA_PNS_AUTO_HARDWARE_KEY_PROBE, false) == true
        HardwareKeyProbeScreen(
            onBack = {
                showHardwareKeyProbe = false
                if (launchScreen == PNS_SCREEN_HARDWARE_KEY_PROBE) {
                    activity?.finish()
                }
            },
            autoProbe = autoHardwareKeyProbe,
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
            onShowFleetMatrix = {
                showDebugMenu = false
                showFleetMatrix = true
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
            onShowMockPreview = {
                showDebugMenu = false
                openMockPreview(MockPreviewScreens.ROUTE_MOCK)
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
            onShowEyeOverlayCalibrator = {
                showDebugMenu = false
                previewLaunchedFromDebug = true
                showEyeOverlayCalibrator = true
                showPreviewEngine = true
            },
            onShowLutImport = {
                showDebugMenu = false
                showLutImport = true
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
            onProbeHubSearchPick = { pick -> handleProbeHubSearchPick(pick, dismissDebugMenu = true) },
        )
        return
    }

    if (launchScreen == null) {
        val secureLaunchSession =
            when (activity?.intent?.action) {
                MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE,
                MediaStore.ACTION_IMAGE_CAPTURE_SECURE,
                -> true
                else -> false
            }
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
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            secureLaunchSession = secureLaunchSession,
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
        onShowFleetMatrix = { showFleetMatrix = true },
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
        onShowMockPreview = { openMockPreview(MockPreviewScreens.ROUTE_MOCK) },
        onShowHudSettings = {
            hudSettingsFocus = HudSettingsFocus.None
            showHudSettings = true
        },
        onShowCalibrate = { showCalibrate = true },
        onShowEyeOverlayCalibrator = {
            previewLaunchedFromDebug = true
            showEyeOverlayCalibrator = true
            showPreviewEngine = true
        },
        onShowLutImport = { showLutImport = true },
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
        onProbeHubSearchPick = { pick -> handleProbeHubSearchPick(pick, dismissDebugMenu = false) },
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

        val drp = cc.getAvailableDynamicRangeProfilesOrNull()
        if (drp != null) {
            val dynRangeProfiles =
                drp.supportedProfiles
                    ?.joinToString(prefix = "[", postfix = "]") { it.toString() }
                    ?: "null"
            sb.appendLine("- android.request.availableDynamicRangeProfiles: $dynRangeProfiles")
            val recommendedTenBit = cc.getRecommendedTenBitDynamicRangeProfileOrNull()
            sb.appendLine("- android.request.recommendedTenBitDynamicRangeProfile: ${recommendedTenBit?.toString() ?: "null"}")
        } else {
            sb.appendLine("- android.request.availableDynamicRangeProfiles: n/a (API < 33)")
            sb.appendLine("- android.request.recommendedTenBitDynamicRangeProfile: n/a (API < 33)")
        }

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

    // Host `pns_fleet_matrix_scan -ScanTier full` runs [buildFullAndSave] in parallel; persisting quick here
    // would race and downgrade scanMeta.scanTier on disk (REG-20260605-001).
    val adbFullMatrixScan =
        (context as? ComponentActivity)?.intent
            ?.getStringExtra(EXTRA_PNS_FLEET_MATRIX_SCAN)
            ?.lowercase()
            ?.trim() == "full"
    val existingFullTier =
        FleetDeviceMatrixStore.loadValid(context.applicationContext)
            ?.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)
            ?.optString("scanTier") == "full"
    if (!adbFullMatrixScan && !existingFullTier) {
        val matrixBuilt =
            FleetDeviceMatrixBuilder.buildQuick(
                context.applicationContext,
                prebuiltCameras = camerasJson,
                prebuiltShallowRoot = shallowRoot,
                prebuiltDegraded = scanDegraded,
            )
        FleetDeviceMatrixStore.save(context.applicationContext, matrixBuilt.root)
        Log.i(
            FleetDeviceMatrixBuilder.TAG,
            "scanTier=quick cameras=${matrixBuilt.cameraCount} degraded=${matrixBuilt.degraded} " +
                "ms=${matrixBuilt.scanDurationMs}",
        )
    }

    FleetProbeMatrixMarkdown.appendSummary(sb, context.applicationContext)

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
        "MONO", "MONOCHROME", "BW", "B&W" -> CommandDialMode.Monochrome
        "BKT" -> CommandDialMode.BKT
        "MACRO" -> CommandDialMode.Macro
        "NIGHT" -> CommandDialMode.Night
        "BOKEH" -> CommandDialMode.Bokeh
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

private fun Intent?.previewStillFormatExtra(): StillExportKind? =
    StillExportScaffolds.fromAdbValue(this?.getStringExtra(EXTRA_PNS_PREVIEW_STILL_FORMAT))

private fun Intent?.previewStillResolutionModeExtra(): PhotoResolutionMode? {
    val raw = this?.getStringExtra(EXTRA_PNS_PREVIEW_STILL_RESOLUTION_MODE)?.trim()?.takeIf { it.isNotBlank() }
        ?: return null
    return when (raw.lowercase()) {
        "binned" -> PhotoResolutionMode.Binned
        "max_resolution", "max", "maximum", "maxres" -> PhotoResolutionMode.MaxResolution
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

internal fun Intent?.previewStripExifSeedExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_STRIP_EXIF)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_STRIP_EXIF, true)
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

internal fun Intent?.previewReadoutShutterNsProbeExtra(): Long? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_READOUT_SHUTTER_NS)) {
        getLongExtra(EXTRA_PNS_PREVIEW_READOUT_SHUTTER_NS, 0L).takeIf { it > 0L }
    } else {
        null
    }

internal fun Intent?.previewApertureCycleProbeExtra(): Int? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_APERTURE_CYCLES)) {
        getIntExtra(EXTRA_PNS_PREVIEW_APERTURE_CYCLES, 0).takeIf { it > 0 }
    } else {
        null
    }

internal fun Intent?.previewVideoShutterAngleExtra(): String? =
    this?.getStringExtra(EXTRA_PNS_PREVIEW_VIDEO_SHUTTER_ANGLE)?.trim()?.takeIf { it.isNotBlank() }

internal fun Intent?.previewEyeAfOverlayExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_EYE_AF_OVERLAY)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_EYE_AF_OVERLAY, false)
    } else {
        null
    }

internal fun Intent?.previewAudioHiFiExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_AUDIO_HIFI)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_AUDIO_HIFI, false)
    } else {
        null
    }

internal fun Intent?.previewAudioWindExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_AUDIO_WIND)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_AUDIO_WIND, true)
    } else {
        null
    }

internal fun Intent?.previewWindNoiseFilterExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_WIND_NOISE_FILTER)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_WIND_NOISE_FILTER, false)
    } else {
        null
    }

internal fun Intent?.previewExperimentalMasterExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_EXPERIMENTAL_MASTER)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_EXPERIMENTAL_MASTER, false)
    } else {
        null
    }

internal fun Intent?.previewExperimentalMaxResUnlockExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_EXPERIMENTAL_MAX_RES_UNLOCK)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_EXPERIMENTAL_MAX_RES_UNLOCK, false)
    } else {
        null
    }

internal fun Intent?.previewExperimentalVendorSessionExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_EXPERIMENTAL_VENDOR_SESSION)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_EXPERIMENTAL_VENDOR_SESSION, false)
    } else {
        null
    }

internal fun Intent?.previewForceSafeModeExtra(): Boolean =
    this?.getBooleanExtra(EXTRA_PNS_PREVIEW_FORCE_SAFE_MODE, false) == true

private fun parseCsvKeys(raw: String?): List<String> =
    raw?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        .orEmpty()

internal fun Intent?.previewMaxResSweepSessionKeysExtra(): List<String> =
    parseCsvKeys(this?.getStringExtra(EXTRA_PNS_PREVIEW_MAX_RES_SWEEP_SESSION_KEYS))

internal fun Intent?.previewMaxResSweepRequestKeysExtra(): List<String> =
    parseCsvKeys(this?.getStringExtra(EXTRA_PNS_PREVIEW_MAX_RES_SWEEP_REQUEST_KEYS))

internal fun Intent?.previewAeLockExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_AE_LOCK)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_AE_LOCK, false)
    } else {
        null
    }

internal fun Intent?.previewTimelapseModeExtra(): String? =
    this?.getStringExtra(EXTRA_PNS_PREVIEW_TIMELAPSE_MODE)?.trim()?.takeIf { it.isNotBlank() }

internal fun Intent?.previewTimelapseRunningExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_TIMELAPSE_RUNNING)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_TIMELAPSE_RUNNING, false)
    } else {
        null
    }

internal fun Intent?.previewTimelapseIntervalSecExtra(): Int? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_TIMELAPSE_INTERVAL_SEC)) {
        getIntExtra(EXTRA_PNS_PREVIEW_TIMELAPSE_INTERVAL_SEC, 0)
    } else {
        null
    }

internal fun Intent?.previewTimelapseAutoStopSecExtra(): Int? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_TIMELAPSE_AUTO_STOP_SEC)) {
        getIntExtra(EXTRA_PNS_PREVIEW_TIMELAPSE_AUTO_STOP_SEC, 0).coerceIn(0, 120)
    } else {
        null
    }

internal fun Intent?.previewFocusBreathingCompExtra(): Boolean? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_FOCUS_BREATHING_COMP)) {
        getBooleanExtra(EXTRA_PNS_PREVIEW_FOCUS_BREATHING_COMP, false)
    } else {
        null
    }

internal fun Intent?.previewAutomationFocusRackSecExtra(): Int? =
    if (this != null && hasExtra(EXTRA_PNS_PREVIEW_AUTOMATION_FOCUS_RACK_SEC)) {
        getIntExtra(EXTRA_PNS_PREVIEW_AUTOMATION_FOCUS_RACK_SEC, 0).coerceIn(0, 60)
    } else {
        null
    }

internal fun Intent?.previewRackFocusPullExtra(): Boolean =
    this?.getBooleanExtra(EXTRA_PNS_PREVIEW_RACK_FOCUS_PULL, false) == true

internal fun Intent?.previewShutterSoundPackExtra(): String? =
    this?.getStringExtra(EXTRA_PNS_PREVIEW_SHUTTER_SOUND_PACK)?.trim()?.takeIf { it.isNotBlank() }

