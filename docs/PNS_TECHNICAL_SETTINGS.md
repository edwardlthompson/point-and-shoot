# Point & Shoot — technical settings (source of truth)

**Purpose:** Single reference for **numeric defaults**, **mode behavior**, **pipeline locks**, and **where they live in code**. Use this when implementing features, bisecting regressions, or building a similar Camera2 app.

**Maintenance contract (mandatory):** When you **add**, **change**, or **remove** any setting, constant, default, mode behavior, or fleet lock described here, **update this file in the same change** (same PR / commit). Do not defer doc updates.

| Also update when relevant | File |
|---------------------------|------|
| Product milestones / sprints | `BUILD_PLAN.md`, `BUILD_PLAN_COMPLETED.md` |
| Probe ↔ product map | `PROBE_BUILD_PLAN.md` §6 |
| Shipped / user-visible deltas | `CHANGELOG.md` |
| Agent automation | `AGENTS.md` |
| Locked invariants (short) | `.cursor/rules/*.mdc` where applicable |

**Last synced with tree:** 2026-05-23 (Sprint **AS** — 96 kHz AAC encoder pick + HFR mux PTS).

**Related deep dives (not duplicated here):**

| Topic | Doc |
|-------|-----|
| Preview chrome layout (frozen UI) | `docs/preview-chrome-layout-style-guide.md` |
| Capture bisect / §4a / §2 RAW tier | `docs/REVERTED_FEATURES_RESTORE_LIST.md` |
| OnePlus 13 RAW/DNG fleet | `docs/FLEET_ONEPLUS13_RAW_POLICY.md`, `DODGE_PROFILE.md` |
| DNG metadata pairing | `AGENTS.md` (CRITICAL — DNG metadata pairing) |
| Dodge tele 73/85/150 | `DODGE_PROFILE.md`, `.cursor/rules/dodge-tele-focal-routing.mdc` |
| Color / LUT pipeline | `COLOR_PIPELINE.md` |
| HUD prefs persistence | `docs/SETTINGS_PERSISTENCE_AUDIT.md` |

---

## Table of contents

1. [Command dial modes](#1-command-dial-modes)
2. [H mode (Highlight)](#2-h-mode-highlight)
3. [Readout strip — AE coupling & ISO bands](#3-readout-strip--ae-coupling--iso-bands)
4. [YUV exposure chase (locked-axis Auto)](#4-yuv-exposure-chase-locked-axis-auto)
5. [RAW / DNG still capture](#5-raw--dng-still-capture)
6. [REGULAR preview session locks](#6-regular-preview-session-locks)
7. [Focal routing & digital crop](#7-focal-routing--digital-crop)
8. [Preview chrome (behavioral constants)](#8-preview-chrome-behavioral-constants)
9. [HudSettings defaults](#9-hudsettings-defaults)
10. [Video encode (in-app)](#10-video-encode-in-app)
10.1. [Audio capture & shutter (Sprint AS)](#101-audio-capture--shutter-sprint-as)
11. [Automation & ADB intent extras](#11-automation--adb-intent-extras)
12. [GLES preview geometry](#12-gles-preview-geometry)
13. [Diagnostics log tags](#13-diagnostics-log-tags)

---

## 1. Command dial modes

**Code:** `CommandDial.kt` — `enum class CommandDialMode`  
**Engine:** `PreviewController` / `PreviewEngineScreen.kt` — `setCommandDialMode`, `applyCommandDialToCaptureRequest` (and still builders).

| Mode | Label | Summary behavior |
|------|-------|------------------|
| **Auto** | A | HAL **AE/AF** program; readout ISO/shutter chips show **Auto** unless user locks an axis. |
| **M** | M | **Manual focus distance** on preview (drag); ISO/shutter from **readout chips**, not “full manual” on the dial alone. |
| **H** | H | **Highlight protection** — see [§2](#2-h-mode-highlight). |
| **S** | S | Street: AF at **infinity**; tap preview to refocus. |
| **BKT** | BKT | AE bracket burst (3/5/7); RAW12 + `GroupingID` when enabled. |
| **Macro** | MACRO | Close-up focus (&lt;10 cm class); session/macro probes. |
| **Night** | NIGHT | CameraX **NIGHT** extension when `CameraXExtensionProbe` reports available (hidden on dial otherwise). |
| **Bokeh** | BOKEH | CameraX **BOKEH** extension when available. |
| **Qr** | QR | Live ZXing on YUV (photo programs); **not** on rotary dial UI — separate entry. |

**Dial visibility:** `CommandDial` composable hides **Qr**; **Night** / **Bokeh** hidden when extension probe fails (typical LineageOS / AOSP).

**Interaction with FPS:** Digital focal crops (**85** / **150** mm) apply only when `desiredFps < 120` (`PreviewController` / crop gates). At **120 fps**, tele digital equivalence is not applied the same way — see `DODGE_PROFILE.md`.

---

## 2. H mode (Highlight)

**Goal:** Protect **bright peaks** (sky, sun disk); **never brighten** the scene via positive EV in H.

### 2.1 When H metering is active

| Gate | Value / rule | Code |
|------|----------------|------|
| Dial | `commandDialMode == CommandDialMode.H` | `wantsHighlightMetering()` |
| FPS | `desiredFps < 120` | YUV analysis surface attached; at **120 fps** default, H **does not** get YUV highlight path |
| YUV reader | Non-null `yuvImageReader` | Session must include analysis stream |

**Hardware H-AE:** If `HighlightAeModeSupport.supportsHardwareHighlightForHMode` and vendor prefs allow, HAL highlight AE may be used instead of YUV EV comp (`usesHardwareHighlightAe`).

### 2.2 YUV highlight pipeline (software path)

| Setting | Value | Location |
|---------|-------|----------|
| Histogram / EV sample interval | **380 ms** min | `highlightMeterMinIntervalMs` |
| AE comp refresh min gap | **900 ms** | `highlightAeRefreshMinGapMs` |
| Darken only | **true** | `highlightMeterDarkenOnly` — no positive EV pump |
| EV stability zone (snap to 0) | **0.24** | `highlightEvStabilityZone` |
| Darken bypass snap | **≤ −0.11 EV** | `highlightMeterStabilityDarkenBypassEv` |
| Deadband darken | **0.095** | `highlightMeterEvDeadbandDarken` |
| Deadband brighten | **0.155** (unused when darken-only) | `highlightMeterEvDeadbandBrighten` |
| Darken engagement EMA | `HighlightMeter.suggestEvCorrectionBreakdown` → `smoothHighlightDarkenEngagement` | Reduces breathing |

**CaptureRequest:** While AE stays **ON**, sets `CONTROL_AE_EXPOSURE_COMPENSATION` from smoothed EV (`HighlightMeterSupport.evToCompensationIndex`). Log tag: **`PNS.ChromeUx`** / adb **`HighlightMeter`** lines (~3.5 s throttle).

### 2.3 H + locked readout axis (Sprint 14.7)

When **locked ISO** or **locked shutter** is active **and** dial is **H** with `desiredFps < 120`, the **free axis** chases via **highlight EV** (not median target):

- Locked ISO → `ReadoutExposureChase.adjustExposureNsFromEv` on `readoutChaseExposureNs`
- Locked SS → `ReadoutExposureChase.adjustIsoFromEv` on `readoutChaseIso`

Uses shared `highlightEvForReadoutChase()` (same engagement / EMA as §2.2).

### 2.4 H on RAW still

- **AE lock** on still when `commandDialMode == H` and not full manual sensor / not ProShot pure leaf / not readout chase — `RawStillProcessingHints.applyAeLockIfAvailable`
- **Readout chase active:** ProShot still metering and extra H AE lock are **skipped** (`wantsReadoutExposureChase()` gate)

---

## 3. Readout strip — AE coupling & ISO bands

**Code:** `ReadoutIsoBand.kt`, `ReadoutAeCoupling.kt`, `ReadoutExposureCatalog.kt`, `PreviewReadoutStrip.kt`, `PreviewController` overrides.

### 3.1 AE coupling (derived from chip locks)

| Coupling | ISO chip | Shutter chip | `CONTROL_AE_MODE` on preview/still |
|----------|----------|--------------|-------------------------------------|
| **AUTO** | Auto | Auto | HAL AE **ON** (default program) |
| **LOCKED_ISO_AUTO_SS** | Locked | Auto | **OFF** + `CONTROL_MODE_OFF` + manual `SENSOR_*` |
| **LOCKED_SS_AUTO_ISO** | Auto | Locked | **OFF** + manual `SENSOR_*` |
| **MANUAL_BOTH** | Locked | Locked | **OFF** + both axes from picks / metadata |

**Important:** Dial **M** is **focus only**; “manual exposure” means **both readout chips locked**, not dial M alone.

### 3.2 ISO bands

| Preset | Range | Enum |
|--------|-------|------|
| Full range | Sensor / table | `ReadoutIsoBand.FULL` |
| 100–400 | 100…400 | `BAND_100_400` |
| 100–800 | 100…800 | `BAND_100_800` |
| 100–3200 | 100…3200 | `BAND_100_3200` |

Band filters menu stops and clamps picks. **Band ceiling lock** (`isoLockFromBandCeilingOnly`) stays until user selects **Auto** or **Full range** (prevents ISO “breathing” at band edge).

### 3.3 Applying exposure to HAL

**Function:** `PreviewController.applyReadoutManualExposureAndWb`  
**Repeating preview + JPEG still + RAW still** (when coupling ≠ AUTO) use the same **AE OFF + chase/manual** path.

**ProShot leaf still metering** (`applyProShotPreviewExposureFromResult`) is **not** applied when `wantsReadoutExposureChase()` — avoids HAL re-metering over locked ISO.

### 3.4 Focus mode picker (Sprint 14.8)

**Code:** `PreviewFocusMode.kt`, `PreviewFocusModePickerDialog.kt`, readout **AF** chip, `PreviewController.setPreviewFocusSelection`.

| Selection | HAL | Notes |
|-----------|-----|--------|
| **Auto** | `CONTINUOUS_PICTURE` (preferred) | Restores CAF; clears manual diopters |
| **Manual distance** | `AF_MODE_OFF` + `LENS_FOCUS_DISTANCE` | Slider + finder vertical drag (same gain as dial **M**) |
| **Hal AF** | e.g. `CONTINUOUS_VIDEO`, `MACRO`, `EDOF` | Only modes in `CONTROL_AF_AVAILABLE_MODES` |

**Precedence:** Tap / face metering → **macro program** (dial **MACRO** or picker **Macro AF**) → dial **S** / **M** → picker selection.

**Manual distance drag:** Horizontal on the finder (not vertical — avoids front/rear camera swipes). No slider in the picker dialog (preview is obscured).

**Macro program:** Forces **ultra-wide** (`macroMode autoSwitchUW`); focal slots other than **14 mm** disabled; HAL `CONTROL_AF_MODE_MACRO` when advertised; OPLUS `com.oplus.macro.closeup.enable` on UW when available. Logs: `PNS.ChromeUx focusMode=`, `macroMode afMode=MACRO`.

ADB: `--es pns_preview_focus_mode manual|auto|macro|…`. Gate: `pns_macro_focus_verify.ps1` (dial **MACRO**).

---

## 4. YUV exposure chase (locked-axis Auto)

**Code:** `ReadoutExposureChase.kt`, `PreviewController.maybeAdjustReadoutChaseFromHistogram`

### 4.1 Constants (edit here → update this doc)

| Constant | Value | Meaning |
|----------|-------|---------|
| `TARGET_MEDIAN_BIN` | **34** | **Single** luminance target for preview, DNG, and tonal still (May 2026 USB parity; was 40 then 56). |
| `MEDIAN_DEADBAND_BINS` | **10** | No chase adjust if ‖medianEma − target‖ &lt; 10 |
| `MEDIAN_EMA_ALPHA` | **0.22** | Histogram median smoothing |
| `LUMINANCE_BLEND_ALPHA` | **0.14** | Per-sample blend toward equilibrium |
| `MIN_EV_STEP` | **0.04** | Minimum EV step (H-EV chase path) |
| `MAX_EV_STEP` | **0.10** | Max EV step per YUV frame (H-EV chase) |
| `MIN_SIGNIFICANT_EXPOSURE_RATIO` | **1.023** | ~1/15 stop — min ratio to push HAL refresh |
| `MIN_SIGNIFICANT_ISO_RATIO` | **1.023** | Same for ISO axis |

### 4.2 Controller timing

| Setting | Value |
|---------|-------|
| YUV histogram min interval | **50 ms** (~20 Hz) — `readoutChaseHistMinIntervalMs` |
| HAL repeating refresh min gap | **150 ms** — `readoutChaseRefreshMinGapMs` |
| Chase state | `readoutChaseExposureNs` (locked ISO), `readoutChaseIso` (locked SS) |

### 4.3 One exposure knob (DNG + tonal still)

**Do not** apply a separate RAW-only EV offset. `captureRawStill` and `captureIndependentTonalStill` both call `applyReadoutManualExposureAndWb` with the same `readoutChaseExposureNs` / `readoutChaseIso` (tonal path: `forStillCapture = true`, no extra darken).

**Architecture:** IMG matrix uses **independent** captures (DNG request, then tonal hardware JPEG) — parity requires the **same chase state** on both requests, not `RAW_STILL_EXTRA_DARKEN_STOPS`.

**USB parity script:** `scripts/pns_readout_jpeg_dng_parity.ps1` + `scripts/readout_jpeg_dng_luminance_compare.py`.

---

## 5. RAW / DNG still capture

**Code:** `RawCaptureSupport.kt`, `PreviewEngineScreen.captureRawStill`, `DngMetadataResolver.kt`, `Dng12Saver.kt`, `RawStillProcessingHints.kt`, `fleet/OnePlus13FleetPolicy.kt`

### 5.1 Default RAW stream order (fleet)

| Preference | Pick order (in-tree) | Notes |
|------------|----------------------|-------|
| `RawStreamPreference.Default` | **RAW12 → RAW_SENSOR → RAW10** | **§2 bisect** — RAW10-first breaks `DngCreator` on CPH2655 |
| `RawSensorFirst` | RAW_SENSOR → RAW12 → RAW10 | ADB / matrix |
| `Raw12Only` / `RawSensorOnly` / `Raw10Only` | Single format | Testing |

**Do not** restore Milestone 10.1 **RAW10-before-RAW_SENSOR** on `Default` without USB proof — `docs/REVERTED_FEATURES_RESTORE_LIST.md` §2.

### 5.2 DNG metadata pairing (locked)

| Setting | In-tree value | Call sites |
|---------|---------------|------------|
| `allowPhysicalTotalResultPairing` | **`false`** | All `resolveForDngSave` in `PreviewEngineScreen.kt` |
| `usePhysicalChildRawStreamMapForLogicalSession` | **`false`** | `pickRawOutputForPreviewSession` |
| Pairing rule | **logical `CameraCharacteristics` + logical `TotalCaptureResult`** unless RAW outputs are **physically pinned** and USB proof opts in | `DngMetadataResolver` |

**CPH2655:** Cameras **2/3/4** are independent logical IDs (empty `physicalCameraIds`); pairing is always logical+logical.

**Diagnostics:** `Log.i(PNS.CaptureStill, "dng save diag …")` with `DngMetadataResolution.toDiagSummary()`.

### 5.3 Still capture timing

| Constant | Value |
|----------|-------|
| `RAW_STILL_POST_COMPLETE_WAIT_MS_DEFAULT` | **6500** ms |
| `RAW_STILL_POST_COMPLETE_WAIT_MS_RAW12` | **6000** ms |
| Scripted post-`stopRepeating` delay | **≥ 420 ms** (§4e revert doc) |

### 5.4 Post-RAW sensitivity boost

| Rule | Behavior |
|------|----------|
| `HudSettings.enablePostRawSensitivityBoost` | Default **false** |
| Applied on RAW still | Only when pref **on** and **not** manual ISO/exp override |
| Readout chase active | **Skipped** on RAW still (`!wantsReadoutExposureChase()` guard, May 2026) |

### 5.5 OnePlus 13 ProShot leaf (reference)

See `docs/FLEET_ONEPLUS13_RAW_POLICY.md`. Summary:

- Shipped still backend: **`FRAMEWORK_PROSHOT`** on CPH2655/2653
- Leaf RAW format order: **32 → 37 → 38 → 36** on opened map
- `useProShotPureDngSave()` — `DngCreator(leaf, stillResult)` without wide-cal reconcile on leaf

**ProShot + readout chase:** HAL metering / AE lock from ProShot **disabled** when `wantsReadoutExposureChase()`.

### 5.6 Stabilization on still

`PreviewStabilization.applyToRequest(..., isStillCapture = true)` — **restored** on RAW/bracket still (§1 revert doc). OIS-for-still can be disabled via `HudSettings.disableOisForStillCapture`.

---

## 6. REGULAR preview session locks

**Code:** `PreviewEngineScreen.kt` session create (`createCaptureSession` / `tryOnce`)

| Setting | In-tree | Rationale |
|---------|---------|-----------|
| `streamHints` | **`false`** | **§4a** — `true` caused RAW still timeout / `ERROR_CAMERA_DEVICE` on CPH2655 |
| Preview physical pin | **Output 0 only** on logical parent | RAW/JPEG unpinned — metadata/pixel parity |
| RAW map pick | Logical `SCALER_STREAM_CONFIGURATION_MAP` | `usePhysicalChildRawStreamMapForLogicalSession = false` |
| Aux UW/tele pin | Prefer **RAW_SENSOR** on logical map when `shouldPreferRawSensorForAuxPhysicalPreviewPin` | Avoid **Default** RAW12 trap (dark/green DNG) |

Full regression table: `docs/REVERTED_FEATURES_RESTORE_LIST.md` §8.

---

## 7. Focal routing & digital crop

**Code:** `BackCameraRoleResolver.kt`, `SensorCropGeometry.kt`, `FocalLensStripSupport.kt`, `DODGE_PROFILE.md`

| Invariant | Rule |
|-----------|------|
| Tele slots **73 / 85 / 150** | Same physical **tele** (`Roles.tele`, e.g. id **4** on dodge) |
| **150 mm** | `LongTele150` digital crop on **mid-tele** sensor — **not** `longTeleId` |
| Open camera | Prefer **physical tele id** when in `cameraIdList` (`tid to mode`) over logical **0** first |
| Fleet policy enum | **No** second tele routing policy / persisted prefs (dodge path only) |

**Verification:** `pns_chrome_ux_gate.ps1 -FocalMmSlot 150` — do **not** run parallel with `pns_photo_capture_verify` on one device.

---

## 8. Preview chrome (behavioral constants)

**Frozen layout:** `docs/preview-chrome-layout-style-guide.md` — **do not change** spacing/tiles without explicit user request.

| Constant | Value | File |
|----------|-------|------|
| `PreviewChromeFinderFlexWeight` | **2.9f** | `PreviewEngineScreen.kt` |
| `PreviewChromeRailFlexWeight` | **1f** | `PreviewEngineScreen.kt` |
| Finder aspect | **3:4** width:height, full width | Style guide |
| Grid | **7×3** + focal row + 2 sticky shortcut rows | `previewChromeGridSlots` |

---

## 9. HudSettings defaults

**Code:** `HudSettings.kt` — persisted via `SharedPreferences` + backup rules `res/xml/pns_backup_rules.xml`

| Setting | Default | Notes |
|---------|---------|-------|
| `showHistogram` | **false** | Enables YUV analysis stream when on |
| `showRgbHistogram` | **false** | Requires histogram |
| `showHighlightClipZebra` | **false** | ~0.95 luma zebra |
| `showHighlightWeightedMeter` | **true** | H-mode-related UI |
| `enablePostRawSensitivityBoost` | **false** | See §5.4 |
| `enableLensOpticalStabilization` | **true** | Preview + still policy in `PreviewStabilization` |
| `disableOisForStillCapture` | **false** | Force OIS off on still only |
| `enableVideoStabilizationPreview` | **false** | Skipped at HFR ≥ 120 |
| `enableHdr10LivePreview` | **false** | Dynamic range profile probe |
| `enableResearchAfBracketing` | **false** | Qualcomm session vendor key |
| `enableResearchHfrAICameraHSR` | **false** | Qualcomm HFR session key |
| `enableSmileTriggeredStill` | **false** | ML Kit smile → still |
| `focusPeakingColor` | **Off** | GL shader path |
| `selectedLutForStills` / `Video` | **None** | Per-mode LUT memory |
| `hardwareJpegIspBias` | **0** (chart apply sets **-2**) | [PreviewJpegProcessingHints] — edge/NR/tonemap/CC modes |
| `videoBitrateScalePercent` | **100** | 50–150% of probe table |

### 9.1 Chart calibration (natural JPEG + DNG sidecar)

**Code:** `CalibrationWorkflow.kt`, `CalibrateScreen.kt`, `StillCaptureColorApply.kt`, `COLOR_PIPELINE.md`

| Step | In-app |
|------|--------|
| Align chart | **Corner test chart overlay** → **Auto-detect** or manual **TL → TR → BR → BL** |
| Exit mode | Finder **Exit** (top/bottom), **system Back**, Settings **Exit chart calibration mode**, or overlay toggle **off** |
| One-shot apply | **Apply** on finder; or Settings → **Chart calibration** |
| Reference target | **ColorChecker Classic 24** layout (`BundledReferenceTargets.ColorCheckerClassic24`) |
| Natural ISP | `hardwareJpegIspBias = **-2**` (minimal sharpening; no creative LUT) |
| Preview WB | `previewShaderWbRgb` from profile WB gains + `CONTROL_AWB_MODE_OFF` |
| Exposure nudge | ±1.25 EV max from **Neutral 5** patch vs `NEUTRAL5_REC709_LUMA` (**0.396**) |
| JPEG color | When `selectedLutForStills == None`, newest saved profile applied via `StillRgbLut.applyCalibrationProfileInPlace` on hardware JPEG decode path |
| DNG | WB/CCM in `.pns-calibration.json` sidecar (`DngColorTags`); RAW Bayer not LUT-baked |

**Storage:** `getExternalFilesDir(null)/calibration/<illuminant>_<utc>.json` — newest wins.

Command dial default on fresh install: **`CommandDialMode.M`** in probe/preview automation paths; user preference via `HudSettings.saveCommandDialMode`.

---

## 10. Video encode (in-app)

**Code:** `MediaCodecVideoRecorder.kt`, `VideoRecordingController.kt`, Sprint **14.6**

| Setting | Value |
|---------|-------|
| 8-bit HEVC VUI | **BT.709 limited** — log `PNS.MCVideoRec colorVui=bt709` |
| H.264 @ 60 (reference) | Typically bt470bg / smpte170m (device-dependent) |
| In-app preview cap FPS | `VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS` |
| Gate script | `scripts/pns_video_codec_color_compare.ps1` |

Chrome **video format chip** maps to `InAppVideoFormatSelection` (codec / fps / resolution prefs).

### 10.1 Audio capture & shutter (Sprint AS)

**Code:** `PnsAudioCaptureSupport.kt`, `ShutterSoundManager.kt`, `ShutterSoundLibrary.kt`, `AudioEffects.kt`, `SpatialAudio.kt` — prefs in `PreviewChromePreferences`.

| Pref / behavior | Default | Notes |
|-----------------|---------|-------|
| `audioHiFiCapture` | **false** | When true: prefer **96 kHz** then 48 kHz; AAC **256 kbps**; PCM **16-bit** stereo |
| `audioWindNoiseReduction` | **true** | `NoiseSuppressor` on `AudioRecord` session when available |
| `audioPreferExternalInput` | **true** | `AudioRecord.setPreferredDevice` for USB / wired / BT SCO |
| `shutterSoundPackKey` | **mechanical** | `mechanical`, `digital`, `vintage`, `silent` — CC0 samples via `SoundPool` (`res/raw/shutter_*.ogg`; see `assets/sounds/shutter_cc0/SOURCE.txt`) |

**HFR + Hi-Fi mux (MediaCodec):** Stereo PCM timestamps use frame count (`shortsRead / channelCount`), not raw short count — fixes ~2× audio duration vs video. At **≥120 fps** video mux PTS is **uniform** (`frameIndex × 1e6 / targetFps`) so MP4 `avg_frame_rate` / system gallery match the capture target; encoder surface PTS often stays on a 60 Hz grid on CPH2655-class HS. ≤119 fps still uses encoder PTS for A/V alignment.

**HFR honesty (≥120 fps):** On CPH2655-class devices, constrained HS + Qualcomm HEVC delivers about **half** the target unique frame rate (e.g. ~60 unique/s at 120 fps). **`VideoRecordingController.lacksTrueHfrUniqueFrames`** hides **HEVC-family** and **AV1** picker rows at **≥120 fps** until hardware + unique-frame proof exists. **H.264 @ 120/240/480** remains when the camera HS table supports it. **AV1 ≤60** when `MediaCodecCapabilityProbe` lists an encoder. No mux frame duplication. USB AV1@120 artifact: `hfr-runs/av1_hfr_verify_*`. See **`docs/VIDEO_MODE_MATRIX.md`**.

**Format picker matrix (device-truth):** [`VideoFormatPresets.catalogTierSizes`] = HAL HS ∪ exact HEVC/H.264 perf sizes ∪ `MediaRecorder` outputs, ∩ canonical [`ALL_TIERS`]. [`fpsOptionsForResolution`] uses **exact** encoder points per size (no inherited 480 on 8K). [`InAppVideoFormatSelection.isFormatAvailableOnDevice`] keeps a row only when **labeled** WxH+fps+codec are all real: HFR = exact [`hasExactHighSpeedFps`] **and** exact H.264 encoder perf (no `pickHighSpeedVideoTarget` fallback); H.264 ≤60 = exact H.264 perf **and** [`supportsMediaRecorderOutputSize`]; HEVC/10-bit/DCG ≤60 = exact HEVC perf. Stale prefs migrate via `videoFormatCatalogMigrate`. **Video truth** banner in [`VideoFormatPickerSheet`] from [`buildVideoTruth`] (per active `cameraId` HS map). **CPH2655:** 480 only UW @ 1080p/720p; wide/tele max 240 HAL; **no 4K @ ≥120** in picker.
| `shutterSoundVolume` | **0.85** | App shutter loudness (0…1), not system media volume |
| `shutterHapticSync` | **false** | When true, haptic with shutter sound; else haptic at readout complete |
| `audioLightCompression` | **false** | Soft-knee PCM compression in MediaCodec audio thread |
| `audioVoiceoverDucking` | **false** | `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` while recording |

**Still shutter:** `PreviewController.onStillShutterFired` → `ShutterSoundManager.playShutter` at capture start (`captureComposedStill` / `captureRawStill`).

**Settings UI:** **Settings → Capture & stills → Shutter sound** — pack (Mechanical / Digital / Vintage / Silent; tap a row to preview), volume slider, haptic-with-shutter toggle. Persisted in `PreviewChromePreferences` (`shutter_sound_pack`, `shutter_sound_volume`, `shutter_haptic_sync`).

**Spatial / multi-track:** `SpatialAudio` logs surround capability; in-app record stays stereo — `AudioEffects.multiTrackPolicy()` logs **unsupported**.

**Gallery:** `VideoCaptureMetadata` embeds capture **fps** + audio in MediaStore **DESCRIPTION**; `readFromUri` prefers embedded **120fps** over retriever **60** when the container under-reports HFR. **Hi-Fi FAB:** `PnsAacEncoderSupport.maxHiFiMuxSampleRateHz` probes AAC encoders once per process — menu shows **48 kHz** / **96 kHz** etc. for this device, not a static “96 kHz when supported”.

**Video format FAB:** `VideoFormatPickerSheet` step **A — Audio** (Hi-Fi, wind NS, external mic, compression, ducking) — persists via `PreviewChromePreferences`.

**HFR audio:** `MediaCodecVideoRecorder` primes AAC before muxer start and waits for the audio track — avoids video-only muxer race at 120+ fps. Mux PTS use [audioEncoderSampleRateHz] from encoder output (not capture rate alone) so hi-fi does not sound half-speed when the HAL resamples.

**96 kHz AAC:** `PnsAacEncoderSupport.openBestAacEncoder` probes ranked AAC encoders (QTI → Android → others) at **96 → 48 → 44.1 kHz** when `audioHiFiCapture` is on; keeps only picks whose **output** `KEY_SAMPLE_RATE` matches the target. PCM / [AudioRecord] rate is aligned to the winning mux rate. Gallery DESCRIPTION uses the mux rate after save.

**Gates:** `scripts/pns_audio_quality_test.ps1`, `scripts/pns_shutter_sound_test.ps1`, `scripts/pns_audio_sprint_gate.ps1`.

---

## 11. Automation & ADB intent extras

**Rule:** `automationSuppressFacePipeline = true` **only** when `adbBracketPattern != null` — **never** for sequential RAW-only (`pns_preview_raw_count`) alone (breaks YUV / RAW session on CPH2655).

| Extra | Effect |
|-------|--------|
| `pns_preview_dial=H` | Initial command dial |
| `pns_preview_raw_count=N` | Sequential RAW stills (keep face pipeline) |
| `pns_preview_focal_mm_slot=` | Focal mm for chrome gate |
| `pns_preview_imaging_profile` | Imaging profile override |
| `pns_preview_raw_stream` | `RawStreamPreference` name |
| `pns_screen=preview` | Cold-start preview route |
| `pns_preview_adaptive_battery_pct` | PO.2 gate: override battery % for [PreviewAdaptiveFpsPolicy] |
| `pns_preview_adaptive_thermal_status` | PO.2 gate: override [PowerManager] thermal status int |
| `pns_preview_audio_hifi` | AS.1 — session seed `audioHiFiCapture` |
| `pns_preview_audio_wind` | AS.1 — session seed `audioWindNoiseReduction` |
| `pns_preview_shutter_sound_pack` | AS.2 — session seed shutter pack key |

Full list: `CameraCapabilitiesProbe` / `MainActivity` extras; automation hub **`AGENTS.md`**.

**Default preview FPS before UI sync:** `DESIRED_FPS_DEFAULT_BEFORE_UI_SYNC` — typically **120**; H YUV requires **`desiredFps < 120`**.

### PO.2 — adaptive preview FPS (battery + thermal)

**Code:** `PreviewAdaptiveFpsPolicy.kt`, polled every **3 s** in `PreviewEngineScreen` (`userSelectedFps` vs effective `selectedFps`).

| Condition | Max preview FPS |
|-----------|-----------------|
| Battery ≤ **10%** | **30** |
| Battery ≤ **20%** | **60** |
| Battery ≤ **30%** | **90** |
| Thermal ≥ **MODERATE** | **90** |
| Thermal ≥ **SEVERE** | **60** |
| Thermal ≥ **CRITICAL** | **30** |

Log: `PNS.PowerThermal adaptiveFpsCap userFps=… effective=…`. Skipped while FPS **sweep** job is active.

### PO.2 — background pause (long-running preview work)

**Code:** `PreviewLongRunningPause`, `PreviewController.lifecycleBackgroundPaused`.

- **`ON_PAUSE`:** pause optional YUV analysis; FPS sweep **waits** (does not advance) until resume.
- **`ON_RESUME`:** clear pause, `kickPreviewPipelineRestart()` to reattach analysis surfaces.

Log: `PNS.PowerThermal longRunningPaused=true/false`.

---

## 12. GLES preview geometry

**Invariant (May 2026):** `LutCameraPreviewRenderer.setGeometry` only from **`PreviewMainViewport`** — `AndroidView` `update` + `OnLayoutChangeListener`.

**Do not reintroduce without redesign + USB proof:**

- `LaunchedEffect` → `setGeometry` on buffer/generation churn
- Controller `setPreviewBufferGeometryListener` → `queueEvent { setGeometry }`
- `setPreviewDisplayLayoutSyncListener` / `previewLayoutSyncNonce` cold-start overrides
- `GLSurfaceView.setPreserveEGLContextOnPause(true)` on CPH2655 (abandoned surface)

**Gallery return:** `restartMainActivityCold` when tray opens external viewer successfully; else `kickPreviewPipelineRestart()` + optional `GLSurfaceView.post { requestLayout() }`.

**Tray surface restore:** `PreviewLastSurfacePrefs` (`pns_preview_last_surface.xml`) stores the last **Photo** / **Video** / in-app **Gallery** tray surface. Cold start restores it unless ADB `pns_preview_primary_photo`, `MediaStore` video capture intents, or still/video return contracts override. Saved on tray changes and `ON_STOP` via `LaunchedEffect` in `PreviewEngineScreen`.

**PO.1 memory (Sprint PO.1):** `PnsBitmapGuard` (`PNS.Bitmap`) tracks gallery/tray bitmap recycle; `PnsMediaStoreGallery` (`PNS.GalleryIndex`) indexes `DCIM/Point & Shoot` via `RELATIVE_PATH` + `QUERY_ARG_LIMIT` (lazy EXIF on selection); preview session logs `PNS.MemoryProfiler` (10 s interval, CSV under app external `memory_profiles/`). Gate: `scripts/pns_memory_profiler.ps1`.

**PO.2 battery (Sprint PO.2):** `PreviewAdaptiveFpsPolicy` + `PreviewLongRunningPause`; logs `PNS.PowerThermal adaptiveFpsCap` / `longRunningPaused`. Gate: `scripts/pns_battery_life_test.ps1`. See also `docs/M13V_12_POWER_THERMAL.md`.

Details: **`AGENTS.md`** — CRITICAL GLES preview aspect.

---

## 13. Diagnostics log tags

| Tag | Use |
|-----|-----|
| `PNS.ChromeUx` | Readout AE, coupling, `readoutAeApplied`, focal slot |
| `PNS.CaptureStill` | RAW still save, **`dng save diag`** |
| `PNS.Dng` / `PNS.DngMeta` | Color matrices, resolver pairing |
| `PNS.ProShotStill` | ProShot leaf metering (when not chase-gated) |
| `PNS.PostRawBoost` | Post-RAW sensitivity |
| `PNS.MCVideoRec` | Video encoder color VUI |
| `PNS.AdbValidation` | Scripted capture verify needles |

---

## Appendix A — Change log (doc metadata)

| Date | Change |
|------|--------|
| 2026-05-21 | Initial source-of-truth doc: H mode, readout chase, session locks, fleet pointers. |
| 2026-05-21 | Parity: removed **RAW-only** darken; **TARGET_MEDIAN_BIN = 40** (single knob for DNG + tonal). |

*Append rows here when this file is updated for traceability.*
