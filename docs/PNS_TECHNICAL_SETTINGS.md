# Point & Shoot — technical settings (source of truth)

**Purpose:** Single reference for **numeric defaults**, **mode behavior**, **pipeline locks**, and **where they live in code**. Use this when implementing features, bisecting regressions, or building a similar Camera2 app.

**Maintenance contract (mandatory):** When you **add**, **change**, or **remove** any setting, constant, default, mode behavior, or fleet lock described here, **update this file in the same change** (same PR / commit). Do not defer doc updates.

| Also update when relevant | File |
|---------------------------|------|
| Product milestones / sprints | `BUILD_PLAN.md`, `BUILD_PLAN_COMPLETED.md` |
| Probe ↔ product map | `PROBE_BUILD_PLAN.md` §6 |
| Shipped / user-visible deltas | `CHANGELOG.md`, `scripts/changelog_coverage.v1.json` |
| Agent automation | `AGENTS.md` |
| Locked invariants (short) | `.cursor/rules/*.mdc` where applicable |

**Last synced with tree:** 2026-06-05 (EXODUS 4K fleet honesty — `video.4k_regular` gate, parity session proof, API 28 legacy record policy).

**Related deep dives (not duplicated here):**

| Topic | Doc |
|-------|-----|
| Preview chrome layout (frozen UI) | `docs/preview-chrome-layout-style-guide.md` |
| Capture bisect / §4a / §2 RAW tier | `docs/REVERTED_FEATURES_RESTORE_LIST.md` |
| legacy device RAW/DNG fleet | `docs/FLEET_ONEPLUS13_RAW_POLICY.md`, `DODGE_PROFILE.md` |
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
14. [Fleet UI visibility (M17)](#14-fleet-ui-visibility-m17)
15. [Accessibility (a11y)](#15-accessibility-a11y)

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
| **Monochrome** | MONO | Dedicated hardware **monochrome sensor** mode (not LUT emulation). Dial option appears only when a dedicated monochrome camera is detected (`MonochromeCameraSupport`). Selecting MONO routes to that camera id and enables tiered still fallback: hardware still (`RAW/JPEG` when healthy) → safe JPEG retry → preview-frame fallback snapshot (`MONO_FALLBACK_SNAPSHOT_SAVED`). |
| **BKT** | BKT | AE bracket burst (3/5/7); RAW12 + `GroupingID` when enabled. |
| **Macro** | MACRO | Close-up focus (&lt;10 cm class); session/macro probes. |
| **Night** | NIGHT | **NightScape** — burst **4/6/8** hardware JPEGs at max ISO + **≤1 s** exposure → block-align → average → **AVIF/JXL** per IMG tier (`NightScapeCapture.kt`, `HudSettings.nightScapeFrameCount`). Progress: `PNS.NightScape frame=N/M`. Requires preview **≤119 fps** + JPEG in IMG. |
| **Bokeh** | BOKEH | CameraX **BOKEH** extension when available. |
| **Qr** | QR | Live ZXing on YUV (photo programs); **not** on rotary dial UI — separate entry. |

**Dial visibility:** `CommandDial` composable hides **Qr**; **Night** / **Bokeh** hidden when extension probe fails (typical LineageOS / AOSP); **MONO** appears only when a dedicated monochrome camera is detected.

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

- **AE lock** on still when `commandDialMode == H` and not full manual sensor / not ReferenceCam pure leaf / not readout chase — `RawStillProcessingHints.applyAeLockIfAvailable`
- **Readout chase active:** ReferenceCam still metering and extra H AE lock are **skipped** (`wantsReadoutExposureChase()` gate)

---

## 3. Readout strip — AE coupling & ISO auto range

**Code:** `ReadoutIsoBand.kt`, `ReadoutAeCoupling.kt`, `ReadoutExposureCatalog.kt`, `PreviewReadoutStrip.kt`, `PreviewController` overrides.

### 3.1 AE coupling (derived from chip locks)

| Coupling | ISO chip | Shutter chip | `CONTROL_AE_MODE` on preview/still |
|----------|----------|--------------|-------------------------------------|
| **AUTO** | Auto | Auto | HAL AE **ON** (default program) |
| **LOCKED_ISO_AUTO_SS** | Locked | Auto | **OFF** + `CONTROL_MODE_OFF` + manual `SENSOR_*` |
| **LOCKED_SS_AUTO_ISO** | Auto | Locked | **OFF** + manual `SENSOR_*` |
| **MANUAL_BOTH** | Locked | Locked | **OFF** + both axes from picks / metadata |

**Important:** Dial **M** is **focus only**; “manual exposure” means **both readout chips locked**, not dial M alone.

### 3.2 ISO auto range checklist

- ISO menu now has a **range checklist** section.
- **Auto (sensor range)** is the top row (no custom clamp).
- Tapping one ISO stop sets a single-stop range; tapping a second stop fills the full span (`100` then `800` => `100…800`).
- All ISO picks / chase updates are clamped to the selected range plus HAL `SENSOR_INFO_SENSITIVITY_RANGE`.
- Persistence token for tray restore uses `auto` or `range:min-max` (legacy enum tokens still parse for backward compatibility).

### 3.3 Applying exposure to HAL

**Function:** `PreviewController.applyReadoutManualExposureAndWb`  
**Repeating preview + JPEG still + RAW still** (when coupling ≠ AUTO) use the same **AE OFF + chase/manual** path.

**ReferenceCam leaf still metering** (`applyReferenceAppPreviewExposureFromResult`) is **not** applied when `wantsReadoutExposureChase()` — avoids HAL re-metering over locked ISO.

### 3.5 Preview AE lock (Sprint 15.26)

**Separate from §3.1 chip coupling:** long-press **ISO** or **Ss** chip toggles `PreviewController.aeLocked` → `CaptureRequest.CONTROL_AE_LOCK` on repeating preview (AF unchanged). Amber **12 dp** padlock beside ISO when active. Clears on camera id change, command dial change, or `closeCamera`. Log: `PNS.ChromeUx aeLock=true|false`. ADB: `--ez pns_preview_ae_lock true` → `pns_ae_lock_verify.ps1`.

**Incompatible with readout chase:** AE lock is ignored (and cleared when arming locked-shutter / auto-ISO) while `wantsReadoutExposureChase()` — preview AE lock would freeze the auto axis.

### 3.6 Aperture readout (F chip)

**Code:** `PreviewApertureSupport.kt`, `PreviewController.cycleReadoutAperture`, `applyReadoutAperture` (via `applyReadoutManualExposureAndWb`).

| HAL `LENS_INFO_AVAILABLE_APERTURES` | F chip |
|-------------------------------------|--------|
| Empty | Hidden (`lens.aperture` fleet gate) |
| One value | Shows `f/x.x` (read-only) |
| Two or more + `LENS_APERTURE` request key | Tap cycles sorted f-stops |

- Readout order: **ISO · SS · F · WB · AF** (F sits beside SS).
- Selection is stored **per `cameraId`** (`apertureByCameraId`) so focal-row lens switches (e.g. Sony Xperia PRO-I main **f/2.0** / **f/4.0** vs fixed UW/tele) do not reset the main lens choice.
- Default on first use: **smallest f-number** (widest open).
- Logs: `PNS.ChromeUx apertureInit`, `apertureCycle`, `apertureAutomation`.
- ADB: `--ei pns_preview_aperture_cycles N` with `--es pns_preview_camera_id` (optional); gate **`scripts/pns_aperture_readout_verify.ps1`**.
- Catalog: `lens.aperture`, `lens.variable_aperture`.

### 3.7 Focus mode picker (Sprint 14.8)

**Code:** `PreviewFocusMode.kt`, `PreviewFocusModePickerDialog.kt`, readout **AF** chip, `PreviewController.setPreviewFocusSelection`.

| Selection | HAL | Notes |
|-----------|-----|--------|
| **Auto** | `CONTINUOUS_PICTURE` (preferred) | Restores CAF; clears manual diopters |
| **Manual distance** | `AF_MODE_OFF` + `LENS_FOCUS_DISTANCE` | Slider + finder vertical drag (same gain as dial **M**) |
| **Hal AF** | e.g. `CONTINUOUS_VIDEO`, `MACRO`, `EDOF` | Only modes in `CONTROL_AF_AVAILABLE_MODES` |

**Precedence:** Tap / face metering → **macro program** (dial **MACRO** or picker **Macro AF**) → dial **S** / **M** → picker selection.

**Manual distance drag:** Horizontal on the finder (not vertical — avoids front/rear camera swipes). No slider in the picker dialog (preview is obscured).

**Macro program:** Auto-selects the best close-focus back camera for the current device (`macroMode autoSwitch cameraId=...`) — prefers macro-capable cameras by minimum-focus distance (dedicated macro when present), then falls back to close-focus UW/wide. Macro mode keeps selection on that macro camera instead of hard-locking `14 mm`; HAL `CONTROL_AF_MODE_MACRO` when advertised; OPLUS `com.oplus.macro.closeup.enable` when available. Logs: `PNS.ChromeUx focusMode=`, `macroMode afMode=MACRO`, `macroMode autoSwitch`.

**Macro video (Sprint 15.31):** Video tray + dial **MACRO** — same macro-camera auto-selection, preview/record fps capped at **60**, EIS + OIS forced via HUD override, readout badge **MACRO VIDEO** (amber). Workflow preset **`macro_video`**. Log: `PNS.ChromeUx macroVideo=true`. Gate: `pns_macro_video_verify.ps1`.

ADB: `--es pns_preview_focus_mode manual|auto|macro|…`. Gate: `pns_macro_focus_verify.ps1` (dial **MACRO** photo); macro video: `--ez pns_preview_primary_photo false --es pns_preview_dial MACRO`.

---

## 4. YUV exposure chase (locked-axis Auto)

**Code:** `ReadoutExposureChase.kt`, `PreviewController.maybeAdjustReadoutChaseFromHistogram` (implemented in `PreviewEngineScreen.kt` controller)

### 4.1 Constants (edit here → update this doc)

| Constant | Value | Meaning |
|----------|-------|---------|
| `TARGET_MEDIAN_BIN` | **34** | **Single** luminance target for preview, DNG, and tonal still (May 2026 USB parity; was 40 then 56). |
| `MEDIAN_DEADBAND_BINS` | **6** | No chase adjust if ‖medianEma − target‖ &lt; 6 |
| `MEDIAN_EMA_ALPHA` | **0.32** | Histogram median smoothing |
| `LUMINANCE_BLEND_ALPHA` | **0.28** | Per-sample blend toward equilibrium |
| `MIN_EV_STEP` | **0.04** | Minimum EV step (H-EV chase path) |
| `MAX_EV_STEP` | **0.10** | Max EV step per YUV frame (H-EV chase) |
| `MIN_SIGNIFICANT_EXPOSURE_RATIO` | **1.012** | ~1/30 stop — min ratio to push HAL refresh |
| `MIN_SIGNIFICANT_ISO_RATIO` | **1.012** | Same for ISO axis |

### 4.2 Controller timing

| Setting | Value |
|---------|-------|
| YUV histogram min interval | **33 ms** (~30 Hz) — `readoutChaseHistMinIntervalMs` |
| HAL repeating refresh min gap | **66 ms** (~15 Hz) — `readoutChaseRefreshMinGapMs` |
| Readout chip poll (Compose) | **100 ms** when one axis locked + auto chase; else **350 ms** |
| YUV during in-app video record | **Attached** when `wantsReadoutExposureChase()` (or H/face/hist); RAW/JPEG still surfaces stay off while recorder is present |
| Chase state | `readoutChaseExposureNs` (locked ISO), `readoutChaseIso` (locked SS) |

### 4.3 One exposure knob (DNG + tonal still)

**Do not** apply a separate RAW-only EV offset. `captureRawStill` and `captureIndependentTonalStill` both call `applyReadoutManualExposureAndWb` with the same `readoutChaseExposureNs` / `readoutChaseIso` (tonal path: `forStillCapture = true`, no extra darken).

**Architecture:** IMG matrix uses **independent** captures when RAW and JPEG tiers **differ** (DNG request, then tonal hardware JPEG). When tiers **match** (both Standard or both Ultra), one still request emits **DNG + JPEG sidecar** on the same exposure. Parity requires the **same chase state** on both paths, not `RAW_STILL_EXTRA_DARKEN_STOPS`.

**USB parity script:** `scripts/pns_readout_jpeg_dng_parity.ps1` + `scripts/readout_jpeg_dng_luminance_compare.py`.

---

## 5. RAW / DNG still capture

**Code:** `RawCaptureSupport.kt`, `PreviewEngineScreen.captureRawStill`, `DngMetadataResolver.kt`, `Dng12Saver.kt`, `RawStillProcessingHints.kt`, `fleet/LegacyDeviceFleetPolicy.kt`

### 5.1 Default RAW stream order (fleet)

| Preference | Pick order (in-tree) | Notes |
|------------|----------------------|-------|
| `RawStreamPreference.Default` | **RAW12 → RAW_SENSOR → RAW10** | **§2 bisect** — RAW10-first breaks `DngCreator` on legacy SKU |
| `RawSensorFirst` | RAW_SENSOR → RAW12 → RAW10 | ADB / matrix |
| `Raw12Only` / `RawSensorOnly` / `Raw10Only` | Single format | Testing |

**Do not** restore Milestone 10.1 **RAW10-before-RAW_SENSOR** on `Default` without USB proof — `docs/REVERTED_FEATURES_RESTORE_LIST.md` §2.

### 5.2 DNG metadata pairing (locked)

| Setting | In-tree value | Call sites |
|---------|---------------|------------|
| `allowPhysicalTotalResultPairing` | **`false`** (`DngSavePairingPolicy.ALLOW_PHYSICAL_TOTAL_RESULT_PAIRING`) | All `resolveForDngSave` / `ReferenceAppDngCreatorPair.forSave` in `PreviewEngineScreen.kt` |
| `usePhysicalChildRawStreamMapForLogicalSession` | **`false`** | `pickRawOutputForPreviewSession` |
| Pairing rule | **logical `CameraCharacteristics` + logical `TotalCaptureResult`** unless RAW outputs are **physically pinned** and USB proof opts in | `DngMetadataResolver` |

**legacy SKU:** Cameras **2/3/4** are independent logical IDs (empty `physicalCameraIds`); pairing is always logical+logical.

**Diagnostics:** `Log.i(PNS.CaptureStill, "dng save diag …")` with `DngMetadataResolution.toDiagSummary()`.

**Post-save / creator metadata (Sprint 15.14+):** `Dng12Saver` uses `setLocation` when geotag + fix available; capture time from `DngCreator` ctor (`SENSOR_TIMESTAMP`). `StillCaptureMetadata.applyToDngUri` → `TiffExifSubIfdCapturePatch` **in-place only** — never `ExifInterface.saveAttributes()` on DNG (loadability lock). On legacy device rear leaf (**2/3/4**), `skipStillMetadataApplyOnLeafDng` skips post-save EXIF patches (ReferenceCam parity). Leaf DNGs also skip P&S software auxiliary strings (`skipDngSoftwareDescriptionOnLeaf`).

**legacy device leaf DNG (shipped May 2026):** `ReferenceAppLeafStillCaptureRequest` + `ReferenceAppDngCreatorPair` mirror ReferenceCam decompile: crop + still IQ (lens shading map, edge/NR/tonemap/aberration/distortion) + **HAL AE** on still — **no** readout manual ISO, no `applyReferenceAppPreviewExposureFromResult` AE latch, no post-save TIFF reconcile. Code: `useExactReferenceAppLeafStillCaptureRequest()`, `useLegacyLeafAuxColorReconcile=false`, `useReferenceAppReferenceCalibration=false`.

### 5.3 Advanced capture modes (Sprint CC.1)

| Setting | Storage | Behavior |
|---------|---------|----------|
| Burst mode | `HudSettings.burstModeEnabled` + `burstShotCount` + `burstIntervalMs` + `burstPhotoQualityProfile` | **Tap:** runs [PreviewController.captureComposedStillBurst] for configured shot count. **Photo long-press:** starts/stops continuous burst on press/release with separated engines: **JPEG burst** uses a low-latency hardware request train (`captureIndependentTonalStill(... burstRequestCount > 1 ...)` -> `CameraCaptureSession.captureBurst`) and asynchronous save workers so capture request cadence is less blocked by encode/write cost; aggressive JPEG dispatch scales burst train depth by backlog (`2/4/6/8`), applies adaptive backpressure tuning (`paceFloorMs` + `queueCap` adjustments from live drop ratio), uses bounded save concurrency (`Semaphore(3)`), and now supports **live strategy auto-switch** (`aggressive` <-> `paced`) during a hold when drop/saved conditions cross thresholds. JPEG burst save path also enables lightweight metadata mode (skip heavy post-save metadata/description patching for burst writes). **RAW burst** stays on composed still capture with serialized dispatch to reduce `No RAW buffer` stalls. Top status band shows live effective burst fps (`Burst <effective> fps (target <fps>) q=<pending>`). Timer QS has distinct **Single**, **Timer**, and **Burst** sections. Burst speed is fixed to one fleet preset (**Fleet Max**, `17 ms` target) with no user slow/medium/fast tiers. Burst section keeps file-type picker (**RAW only** or **JPEG only**). Burst intent forces `photoResolutionMode=Binned`; JPEG long-press burst keeps low-latency still request tuning (skip stop/restart preview repeating and heavy per-shot IQ request tuning). Finish telemetry logs `profile`, `strategy`, `captured`, `saved`, `savePending`, `drops`, and capture latency buckets (`le100/le250/le500/gt500`) for benchmark parsing. ADB benchmark seeds: `pns_preview_burst_file` (`raw|jpeg`) + `pns_preview_burst_strategy` (`aggressive|paced`). |
| Intervalometer | `intervalometerIntervalSec` + `intervalometerRunning` | Timed stills while preview is open (photo mode, not recording). |
| Time-lapse output | `timeLapseMode` (`Off` / `Photo` / `Video`) | **Video:** hardware JPEG frames → H.264 MP4 @ 30 fps (`TimeLapseVideoEncoder`, PTS = frame × 1/30 s). Blocks RAW DNG + normal video rec while active. Requires JPEG tier in IMG menu. Log: `PNS.TimeLapse`. |
| Pre-capture buffer | `preCaptureBufferEnabled` | Enables [ZslStillFrameRing] on preview RAW; Standard stills use ZSL ring when on. |

**ADB:** `--ei pns_preview_burst_count N` + `--ei pns_preview_burst_interval_ms MS` + optional `--es pns_preview_burst_file raw|jpeg` + `--es pns_preview_burst_strategy aggressive|paced`. Gate: `scripts/pns_longpress_burst_verify.ps1`.

### 5.3.1 Pro capture (Sprint CC.3)

| Setting | Storage | Behavior |
|---------|---------|----------|
| Picture profiles | `HudSettings.selectedPictureProfileId` + `ProPictureProfiles` | Presets apply stills/video LUT, JPEG ISP bias, optional `ImagingProfile` (e.g. Ultra RAW). |
| Tethered capture | `tetheredCaptureEnabled` | Loopback HTTP on **127.0.0.1:28765** — `GET /status`, `POST /capture`, `POST /flash?mode=auto\|torch\|off`. |
| Wi‑Fi Direct tether | `wifiDirectTetherEnabled` | Dual bind **0.0.0.0:28765** + loopback; mDNS **`_pns-tether._tcp`**; see **`docs/TETHER_API.md`**. Log: `wifiDirectBound=true` |
| Flash strength | `previewFlashStrengthPercent` (**25–100**) | Maps to `CaptureRequest.FLASH_STRENGTH_LEVEL` when HAL advertises (API 35+). |
| Calibration I/O | `ColorCalibrationTools` | Export newest `CalibrationProfileStorage` profile to `files/color_calibration/`; SAF import JSON. Chart capture remains **Calibrate** screen (not a RAW editor). |

**ADB:** `--ez pns_preview_tether true`, `--es pns_preview_picture_profile cinematic`, `--ei pns_preview_flash_strength 50`, `--ez pns_preview_cal_export true`. Host: `adb reverse tcp:28765 tcp:28765` (do **not** use 18765 — reverse binds that port on-device). Gate: `scripts/pns_pro_features_test.ps1`.

### 5.4 Still capture timing

| Constant | Value |
|----------|-------|
| `RAW_STILL_POST_COMPLETE_WAIT_MS_DEFAULT` | **6500** ms |
| `RAW_STILL_POST_COMPLETE_WAIT_MS_RAW12` | **6000** ms |
| Scripted post-`stopRepeating` delay | **≥ 420 ms** (§4e revert doc) |

### 5.5 Post-RAW sensitivity boost

| Rule | Behavior |
|------|----------|
| `HudSettings.enablePostRawSensitivityBoost` | Default **false** |
| Applied on RAW still | Only when pref **on** and **not** manual ISO/exp override |
| Readout chase active | **Skipped** on RAW still (`!wantsReadoutExposureChase()` guard, May 2026) |

### 5.5 legacy device ReferenceCam leaf (reference)

See `docs/FLEET_ONEPLUS13_RAW_POLICY.md`. Summary:

- Shipped still backend: **`FRAMEWORK_REFERENCEAPP`** on legacy SKU/2653
- Leaf RAW format order: **32 → 37 → 38 → 36** on opened map
- `useReferenceAppPureDngSave()` — `DngCreator(leaf, stillResult)` without wide-cal reconcile on leaf

**ReferenceCam + readout chase:** HAL metering / AE lock from ReferenceCam **disabled** when `wantsReadoutExposureChase()`.

### 5.7 Stabilization on still

`PreviewStabilization.applyToRequest(..., isStillCapture = true)` — **restored** on RAW/bracket still (§1 revert doc). OIS-for-still can be disabled via `HudSettings.disableOisForStillCapture`.

---

## 6. REGULAR preview session locks

**Code:** `PreviewEngineScreen.kt` session create (`createCaptureSession` / `tryOnce`)

| Setting | In-tree | Rationale |
|---------|---------|-----------|
| `streamHints` | **`false`** | **§4a** — `true` caused RAW still timeout / `ERROR_CAMERA_DEVICE` on legacy SKU |
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
| First-launch scan | `FleetCameraStartupScan` → `files/fleet_focal_map.json` (legacy); **M16** consolidates into `files/fleet_device_matrix.json` **`product.focalSlots`** |
| Fleet matrix (M16) | `FleetDeviceMatrixBuilder` quick tier on hub probe → `fleet_device_matrix.json`; invalidates on fingerprint + `appVersionCode` — `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` |
| Prime focal row (dynamic) | Candidate targets fixed to **14,16,20,24,28,35,40,50,85,100,135,200** (35mm eq). Each target maps to exactly one rear camera using highest effective MP; hidden if crop output would fall below **12 MP** |
| Prime crop gating | Crop-only mapping (`target >= native`), active only below **120 fps**; at/above 120 fps row remains visible but digital crop is not applied |

**Verification:** `pns_chrome_ux_gate.ps1 -FocalMmSlot 150` — do **not** run parallel with `pns_photo_capture_verify` on one device. **Primary fleet USB device:** CPH2583 (see `AGENTS.md`).

---

## 8. Preview chrome (behavioral constants)

**Frozen layout:** `docs/preview-chrome-layout-style-guide.md` — **do not change** spacing/tiles without explicit user request.

| Constant | Value | File |
|----------|-------|------|
| `PreviewChromeFinderFlexWeight` | **2.9f** | `PreviewEngineScreen.kt` |
| `PreviewChromeRailFlexWeight` | **1f** | `PreviewEngineScreen.kt` |
| Finder aspect | **3:4** width:height, full width | Style guide |
| Grid | **7×3** + horizontal-scroll focal row + 2 sticky shortcut rows | `previewChromeGridSlots` |

**Fleet UI visibility (M17):** Consumer chrome reads **`FleetUiVisibilityGate`** + **`FleetChromeVisibility`** (catalog id → surface). Unavailable features → **hidden** (empty grid cell / no toggle row). Root-only → **`PnsColors.RootAccentBlue`** + **`FleetUiVisibilityGate.showRootOnlyToast`**. Evaluation order: per-camera `featureGates` → matrix `capabilityCatalog.deviceSupported` → live **`CapabilityGate`**. Log tag **`PNS.FleetVisibility`**. Engineering hub keeps full inventory in **Device capability matrix** + **`ProbeHubSearch`**. Rule: `.cursor/rules/fleet-ui-visibility.mdc`.

| Surface | Gate helper | Catalog ids (examples) |
|---------|-------------|------------------------|
| QS quick actions | `quickActionFeatureId()` | `face.eye_af`, `hud.histogram`, `hud.zebra`, `lens.ois`, `lens.eis` |
| Mode dial menu | `FleetChromeVisibility.filterCommandDialModes` | `hud.highlight_meter`, `still.bracket`, `preview.qr`, `video.dual`, … |
| Focal row | dynamic prime mapping from `resolvePrimeLensAssignments`; hides unsupported targets (<12 MP effective) | `lens.multi` |
| Tray format FABs | Left tray slot (next to gallery thumb) is mode-dependent: **Photo** always shows the still-format FAB and opens still format flow in this order: **MAX Photo preset**, then **Color space**, **Sensor resolution**, **RAW format**, **Compressed format**. Sensor resolution offers **Binned** (default stream map) and **Max resolution** (maximum-resolution stream map + `SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION` when available, with fallback to binned). RAW/compressed lists hide incompatible entries for the selected color space, keep `Off` in both lists, and sort by bit depth high→low. MAX picks highest-CQI still color space plus the highest-bit-depth RAW/compressed pair for that space (**Rec.2020 MAX now prefers TIFF 16-bit over JXL 12-bit**) and selects **Max resolution**. Tonal-only Max-resolution captures now attempt adaptive progressive tiling (`SCALER_CROP_REGION`) with overlap seam blending (`maxphoto_stitch`): grid auto-selects **1×1 / 2×1 / 2×2** from active-array vs baseline still size and blends overlapping seams while stitching. **Video** keeps Max presets first and forces highest-CQI video color space when selected. | `PreviewBottomCaptureTray`, `StillFormatPickerSheet`, `ComposedStillIntent.StillPhotoPickerMatrix`, `ComposedStillIntent.coerceForStillColorSpace`, `PreviewController.setComposedCapturePlan`, `RawCaptureSupport.pickRawOutputForPreviewSession`, `MaxPhotoTileStitch`, `VideoFormatPickerSheet` |
| Settings rails | per-toggle `FleetUiVisibilityGate.visible` | overlays, video QS, capture tools, FPS rail |
| Readout STAB | `showReadoutStabChip` | `lens.ois`, `lens.eis` |
| Video format picker | `FleetChromeVisibility.filterVideoFormats` | `video.h264`, `video.hevc`, `video.hfr`, `video.regular.1080p30` |
| Hub search pick | `ProbeHubSearch` → `PNS.ProbeHub settingsSearchPick` | scroll + 3× orange pulse via `rememberSettingHighlightFlash` |

---

## 9. HudSettings defaults

**Code:** `HudSettings.kt` — persisted via `SharedPreferences` + backup rules `res/xml/pns_backup_rules.xml`

| Setting | Default | Notes |
|---------|---------|-------|
| `showHistogram` | **false** | Enables YUV analysis stream when on (photo-primary) |
| `showHistogramDuringVideo` | **false** | Video-primary: arms YUV + shows luma histogram overlay **while recording** only |
| `showRgbHistogram` | **false** | Requires histogram pipeline |
| `showHighlightClipZebra` | **false** | Near-clip zebra (legacy toggle; syncs with `falseColorMode`) |
| `falseColorMode` | **off** | `off` / `zebra` / `false_color` — zebra + false-color bands on YUV analysis |
| `zebraIreThreshold` | **95** | IRE % (75–100) → luma threshold for zebra grid |
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
| `videoShutterAngle` | **FREE** | Video-only: `FREE`, `360°`, `180°`, `90°`, `45°` → locks SS with `LOCKED_SS_AUTO_ISO`; label on SS chip |
| Settings rail groups | Capture · Video · Focus · Display · About · Developer | `RailSettingsHomeContent`; **Developer settings** shortcut is pinned at the bottom of main Settings rail and opens research/diagnostic controls (`enableResearch*`). |
| QS grid interactions (M22) | **7×2 shortcut rows** under focal row | **Tap** opens tile menu (no direct toggle/cycle on tap). **Long-press + drag** reorders tiles within the 7×2 area, with persisted order (`PreviewChromePreferences`) plus hover target/incoming-tile preview animation. |
| `showFaceAlignmentDebugCrosshair` | **false** | Center crosshair on preview tile (HUD) |
| Face overlay calibration | engineering only | `FaceOverlayCalibrationStore` (`pns_face_overlay_calibration`); D-pad in **Probe hub → Eye overlay calibration**; Camera2 eyes: `PreviewBufferCoordMap.activeArrayToPreviewBuffer` (same linear crop as tap); view: `FaceHudOverlayMapping.mapBufferPointToTile` on measured content host (`onPreviewContentSized`); no sensor quarter-turn / ST / nominal footprint offset (`PNS.FaceAlign` **`faceHudMap`**) |

**OIS vs EIS (user-facing):** Settings → **Video & stabilization** — **Optical stabilization (OIS)** maps to `enableLensOpticalStabilization` (preview + still policy in `PreviewStabilization`); **Electronic stabilization (EIS)** maps to `enableVideoStabilizationPreview` (skipped at preview **≥120 fps**).

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
| Post-apply diag | `CalibrationWorkflow.logPostApplyParity` → logcat **`PNS.ColorCal`** `postApplyParity` (WB gains vs `asShotNeutral` / preview shader WB) |
| Auto-detect | `ChartQuadDetector` on preview grab; debounced **1.8 s** while overlay on and corners &lt; 4 |

**Storage:** `getExternalFilesDir(null)/calibration/<illuminant>_<utc>.json` — newest wins.

Command dial default on fresh install: **`CommandDialMode.M`** in probe/preview automation paths; user preference via `HudSettings.saveCommandDialMode`.

---

## 10. Video encode (in-app)

**Code:** `MediaCodecVideoRecorder.kt`, `VideoRecordingController.kt`, Sprint **14.6**

| Setting | Value |
|---------|-------|
| 8-bit HEVC VUI | **BT.709 limited** on **MediaCodec** path (all 8-bit HEVC fps, incl. ≤60); log `PNS.MCVideoRec colorVui=bt709`. H.264 ≤60 stays **MediaRecorder**. |
| Video color profile | **M15.16** — [VideoColorProfile] in `HudSettings` (`sdr` / `hlg` / `flat_cine`). **HLG** → MediaCodec **Main10** + `colorVui=bt2020-hlg`; preview applies HLG→linear→sRGB in `lut_preview_external.frag.glsl`. **Flat/cine** → preview tone curve + BT.709 limited encode. Settings → Video rail. |
| Still ICC (JPEG) | **M15.17** — [IccProfileBuilder] + [JpegIccEmbedder] APP2 `ICC_PROFILE` after EXIF on JPEG stills (`StillCaptureMetadata.applyToJpegUri`, uses [ImagingProfile.colorSpace]). **Not** on DNG. AVIF: `AvifStillMuxer` `colr` `prof` when `iccProfileBytes` set (Kotlin mux); native AVIF remains CICP `nclx`. |
| H.264 @ 60 (reference) | Typically bt470bg / smpte170m (device-dependent) |
| In-app preview cap FPS | `VideoRecordingController.IN_APP_VIDEO_PREVIEW_CAP_FPS` |
| Gate scripts | `scripts/pns_video_codec_color_compare.ps1` (H.264 @60 MR + 8-bit HEVC @60/@30 MC; `colorVui=bt709` + ffprobe) → `scripts/pns_hfr_color_compare_frames.ps1` (wraps gate + `scripts/video_codec_yuv_compare.py`; mean Cb/Cr Δ &lt; 8) |
| 8K picker banner | When `ALL_TIERS` lists 8K but `MediaCodecCapabilityProbe.supports8k` is false, `VideoFormatPickerSheet` shows an orange diagnostic line (probe logs `maxFps8k` / `supports8k` on `PNS.MCVideoRec` prepare) |
| 8K record path | `VideoRecordingController` routes **7680×4320** through **MediaCodec** (not MediaRecorder) for reliable mux finalize |
| 8K session negotiate | While recording (non-HFR), clamp encode size to HAL **MediaRecorder** outputs; align preview **SurfaceTexture** buffer to record size when listed (`InAppVideoRecordingSupport.pickPreviewSizeAlignedToRecord`); log `PNS.VideoEncode eightKNegotiation` |
| 8K picker gate | `InAppVideoFormatSelection` requires HAL MR/ST **and** encoder `supports8k` for 8K rows; orange banner when probe or HAL missing |
| Preview stream fit | Always **contain** (shrink-to-fit) in the finder — no user toggle. GLES: [lut_preview_external.vert.glsl] `viewToBufferUv` + raw [SurfaceTexture] matrix; layout footprint uses [previewBufferDimensionsForDisplay] (portrait 3:4). Tap/HUD: [mapViewToBufferWithExternalOesPreview] / [mapBufferToViewWithExternalOesPreview] on the GL content box. USB gate: `scripts/pns_preview_jpeg_framing_gate.ps1` |
| PPM audio meters | `PpmAudioMeter` in `PreviewTopStatusBar` when `audioMeters=true` during video record; when **pillar-bar HUD** is active (`showVideoPillarHud`, letterbox ≥24dp), meters move to the right pillar and top-bar `audioMeters=false` |
| Pillar-bar video HUD | **M15.23** — `showVideoPillarHud`; left pillar = timecode + thermal rotated **+90° CW** (landscape record); right pillar = **horizontal** PPM (quiet→loud left→right); `LivePpmAudioMeter` polls amplitude ~50 ms |
| Video audio source | **M15.24** — `videoAudioSource` in Settings → Video; `PNS.MCVideoRec audioSource=` log |
| Focus breathing comp | **M15.28** — `enableFocusBreathingComp` + `focusBreathingCompK` (default **0.005**); M dial + tele slot + manual focus widens `SCALER_CROP_REGION` when diopters rack (EMA). Log: `PNS.FocusBreathing` |
| Rack focus waypoints | **M15.36** — `rackFocusWaypointNear` / `rackFocusWaypointFar` + `rackFocusDurationMs` (500–3000 ms); long-press **AF** readout chip; **▶ Rack** on M dial. Log: `PNS.RackFocus rackFocus from=` |
| Dual ISO video (experimental) | **M15.38** — `dualIsoVideoEnabled`; probes `SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP` on session create (API 31+). Toggle greyed when probe fails. [DualIsoVideoMerger] pass-through stub only. Log: `PNS.DualIso multiResSupported=` |
| False color / zebra QS | **M15.21 / M22** — grid tile opens mode menu on tap (`Off`, `Zebra`, `Exposure bands`); long-press participates in drag/drop reorder |
| Dual video automation | `pns_preview_dial=DUAL` + `pns_preview_video_fps=30` + `pns_preview_automation_in_app_video_sec`; app waits for `dualGlRecordArmed` before record duration (`pns_dual_video_verify.ps1`) |

Chrome **video format chip** maps to `InAppVideoFormatSelection` (codec / fps / resolution prefs).

### 10.1 Audio capture & shutter (Sprint AS)

**Code:** `PnsAudioCaptureSupport.kt`, `ShutterSoundManager.kt`, `ShutterSoundLibrary.kt`, `AudioEffects.kt`, `SpatialAudio.kt` — prefs in `PreviewChromePreferences`.

| Pref / behavior | Default | Notes |
|-----------------|---------|-------|
| `audioHiFiCapture` | **false** | When true: prefer **96 kHz** then 48 kHz; AAC **256 kbps**; PCM **16-bit** stereo |
| `videoAudioSource` | **camcorder** | Mic path for in-app record — Settings → Video (`HudSettings`) |
| `audioGainDb` | **0** | In-app MediaCodec mic gain (−12…+12 dB, 0.5 step); `PNS.MCVideoRec audioGainDb=` log |
| `windNoiseFilterEnabled` | **false** | When true **and** source is Camcorder: `NoiseSuppressor` + `AcousticEchoCanceler` after `AudioRecord.startRecording()` (`PNS.Audio` `windFilter=on`) |
| `audioWindNoiseReduction` | **true** | Legacy FAB toggle — `PreviewChromePreferences`; in-app MediaCodec path uses `windNoiseFilterEnabled` instead |
| `audioPreferExternalInput` | **true** | `AudioRecord.setPreferredDevice` for USB / wired / BT SCO |
| `shutterSoundPackKey` | **mechanical** | `mechanical`, `digital`, `vintage`, `silent` — CC0 samples via `SoundPool` (`res/raw/shutter_*.ogg`; see `assets/sounds/shutter_cc0/SOURCE.txt`) |

**HFR + Hi-Fi mux (MediaCodec):** Stereo PCM timestamps use frame count (`shortsRead / channelCount`), not raw short count — fixes ~2× audio duration vs video. At **≥120 fps** video mux PTS is **uniform** (`frameIndex × 1e6 / targetFps`) so MP4 `avg_frame_rate` / system gallery match the capture target; encoder surface PTS often stays on a 60 Hz grid on legacy SKU-class HS. ≤119 fps still uses encoder PTS for A/V alignment.

**HFR honesty (≥120 fps):** On legacy SKU-class devices, constrained HS + Qualcomm HEVC delivers about **half** the target unique frame rate (e.g. ~60 unique/s at 120 fps). **`VideoRecordingController.lacksTrueHfrUniqueFrames`** hides **HEVC-family** and **AV1** picker rows at **≥120 fps** until hardware + unique-frame proof exists. **H.264 @ 120/240/480** remains when the camera HS table supports it. **AV1 ≤60** when `MediaCodecCapabilityProbe` lists an encoder. No mux frame duplication. USB AV1@120 artifact: `hfr-runs/av1_hfr_verify_*`. See **`docs/VIDEO_MODE_MATRIX.md`**.

**Strict 4K120 truth lane (M24):**

- Preview controller tracks strict-start telemetry: `hfrWarmupAttempt`, `hfrRoute` (`interleaved_primary` vs **`interleaved_sub4k`** when HS capture is below encode pref), `hsCaptureWxH`, `encodePrefWxH`, `hfrHealthWindowMs`, `hfrBlockReason`, `mcPrepared`, `strictHfrWarmupHealthy` (logs in `PNS.Cam` + `PNS.AdbValidation`).
- Format picker / readout: 4K@120 rows with sub-4K HS capture carry **`hfrDeliveryTier=HS_SUB4K_CAPTURE`** and show capture WxH in the video format chip (`VideoDeliveryHonesty.kt`). Parity row **`video.delivery_honesty`** passes when catalog tiers are honestly labeled.
- ADB automation defers `isRecording=true` until `adbStrictHfrWarmupReady()` (strict warmup healthy or fps below HFR threshold).
- HS startup hardening: constrained-HS sessions now start MediaCodec before repeating burst for in-app record, route burst failures into camera fault recovery, skip first `stopRepeating()` on fresh HS start, and defer fault-reopen retries while open/configure is still pending.
- Strict 120 start path uses a bounded retry budget before falling back to recovery-cap block behavior. If strict warmup cannot become healthy, start is rejected (`inAppVideo120StrictBlocked ... reason=warmup_unhealthy`).
- `scripts/pns_mediacodec_hfr_verify.ps1` emits per-test `TruthClass`:
  - `true_4k120` (container reports exact 3840x2160 + valid HFR gates),
  - `hs120_sub4k`,
  - `blocked_unstable`.
- `scripts/pns_mediacodec_hfr_verify.ps1` also logs route-aware 4K120 diagnostics (`HFR route interleaved`, `HFR route encoderOnly`, `route policy ok`) and stricter frame thresholds scaled by record duration.
- `scripts/pns_4k120_verify.ps1` is strict and only passes on `true_4k120`; retry policy is truth-aware (retries only `blocked_unstable`, hard-stop on `hs120_sub4k`) and writes `strict_4k120_summary.json` with attempt telemetry (`truthClass`, `hfrRoute`, `hfrWarmupAttempt`, `hfrBlockReason`).
- `scripts/pns_video_capability_probe.ps1` now reports capability classes for strict 4K120 gating: `S0` (no 4K120 encoder path), `S1` (sub-4K-only class), `S2` (true 4K120-capable path discovered).
- `scripts/pns_m24_gate.ps1` stores class-aware step metadata in `m24_gate.json` and exports strict truth handoff path for downstream parity merge.
- `scripts/pns_fleet_parity_sweep.ps1` imports 4K120 truth via priority sources (`PNS_4K120_TRUTH_SUMMARY` handoff, M24 strict summary, endurance run summaries, then standalone verify) and surfaces `video4k120TruthClass`, `video4k120TruthSource`, `video4k120TruthSerial`.
- Endurance script: `scripts/pns_4k120_endurance.ps1` writes `hfr-runs/4k120_endurance_*/endurance_report.{json,md}` with `bestPassSec` and terminal reason.

**Format picker matrix (device-truth):** [`VideoFormatPresets.catalogTierSizes`] = HAL HS ∪ exact HEVC/H.264 perf sizes ∪ `MediaRecorder` outputs, ∩ canonical [`ALL_TIERS`]. [`fpsOptionsForResolution`] uses **exact** encoder points per size **plus baseline 30 fps** when HAL lists [`MediaRecorder`] output for that size (M17). [`InAppVideoFormatSelection.isFormatAvailableOnDevice`] keeps a row only when **labeled** WxH+fps+codec are all real: HFR = exact [`hasExactHighSpeedFps`] **and** exact H.264 encoder perf; **H.264 ≤60** = [`supportsMediaRecorderOutputSize`] on active camera (exact H.264 perf not required at ≤60); HEVC/10-bit/DCG ≤60 = exact HEVC perf. **`MediaCodecCapabilityProbe`** probes **1080p@30** tier; **`invalidateAndReprobe()`** on fleet matrix rescan (`FleetDeviceMatrixBuilder`); preview reloads catalog on matrix `scanMeta.generatedAtEpochMs` change (ON_RESUME). Stale prefs migrate via `videoFormatCatalogMigrate`. **legacy SKU:** 480 only UW @ 1080p/720p; wide/tele max 240 HAL; **no 4K @ ≥120** in picker.
| `shutterSoundVolume` | **0.85** | App shutter loudness (0…1), not system media volume |
| `shutterHapticSync` | **false** | When true, haptic with shutter sound; else haptic at readout complete |

**Focus confirm (hardware S1):** [`ShutterSoundManager.playFocusConfirm`] — short chirp (`res/raw/focus_confirm.ogg`) when half-press AF converges (`PASSIVE_FOCUSED` / `FOCUSED_LOCKED`). Uses **shutter volume**; **Silent** pack or volume **0** = no chirp. No haptic. Log: `PNS.ShutterSound focusConfirm` · `PNS.AdbValidation focusConfirm ok=true` · `PNS.HardwareKey focusConfirmBeep fired=true`.
| `audioLightCompression` | **false** | Soft-knee PCM compression in MediaCodec audio thread |
| `audioVoiceoverDucking` | **false** | `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` while recording |

**Still shutter:** `PreviewController.onStillShutterFired` → `ShutterSoundManager.playShutter` at capture start (`captureComposedStill` / `captureRawStill`).

**Settings UI:** **Settings → Capture & stills → Shutter sound** — pack (Mechanical / Digital / Vintage / Silent; tap a row to preview), volume slider, haptic-with-shutter toggle. Persisted in `PreviewChromePreferences` (`shutter_sound_pack`, `shutter_sound_volume`, `shutter_haptic_sync`).

**Spatial / multi-track:** `SpatialAudio` logs surround capability; in-app record stays stereo — `AudioEffects.multiTrackPolicy()` logs **unsupported**.

**Gallery:** `VideoCaptureMetadata` embeds **measured** fps (frame count ÷ duration or retriever capture-framerate) in MediaStore **DESCRIPTION** after finalize; `mergeFrameRateForDisplay` prefers measured rate over the capture target so 4K@60 picks that mux ~30 are not labeled 60. MediaCodec mux finalize writes **effective** mux fps into description. **Hi-Fi FAB:** `PnsAacEncoderSupport.maxHiFiMuxSampleRateHz` probes AAC encoders once per process — menu shows **48 kHz** / **96 kHz** etc. for this device, not a static “96 kHz when supported”.

**4K gates (fleet — two tiers, do not conflate):**

| Catalog id | Matrix gate | Consumer rule |
|------------|-------------|---------------|
| `video.4k_regular` | `featureGates.fourKRegular` — `advertised` = MR map lists 3840×2160; `sessionOk` = `regular_3840x2160` session probe; `appEnabled = advertised && sessionOk` | Picker rows ≥3840×2160 under 60 fps map here via `FleetChromeVisibility.videoFormatFeatureId`. Hidden when `sessionOk=false`. |
| `video.uhd60` | `featureGates.uhd60` — `sessionOk = uhd60Advertised && hfr.sessionOk`; **`appEnabled = advertised && sessionOk`** (same as HFR) | Picker rows ≥3840×2160 at 60 fps (below 120). Requires proven HS session + `UltraHd60SessionParameters` (API **33+** for template). |

**Parity:** `FleetParitySweepRunner.SESSION_GATED_CATALOG_IDS` includes both ids — `provenOk=false` when `sessionOk=false`, or when `sessionOk=null` and matrix `scanTier≠full`. Host scripts refresh matrix with `-ScanTier full` before parity (`pns_fleet_parity_sweep.ps1`, `pns_fleet_regression_pack.ps1`).

**API 28 legacy record:** Grant `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` (`maxSdkVersion=28` in manifest; welcome flow + `pns_in_app_video_verify.ps1` grants). `DeviceAdaptedPrefs` migrates stale **4K HEVC** → **H.264** when `fourKRegularSessionOk=false` or `SDK_INT≤28`. `InAppVideoFormatSelection` blocks **4K non-H.264** on API ≤28. USB gate: `scripts/pns_4k_regular_verify.ps1` (skips when `fourKRegular.sessionOk=false`).

**4K @ 60 (UHD60, fleet):** When **3840×2160 SurfaceTexture** preview caps ~30 fps but the HAL lists **MediaRecorder @ 4K** and H.264 encoder perf @ 60 (`UltraHd60RecordSupport.isCatalogTierSupported`), the picker offers **H.264 3840×2160@60**. Record path: **interleaved** REGULAR session — max-60-fps ST preview (typically **1920×1080**) + **MediaRecorder** @ 4K on the same camera — plus session template **AE 60–60** (`UltraHd60SessionParameters`). Do **not** align preview to 4K for this tier. Log: `PNS.UltraHd60 uhd60Interleaved`. USB **CPH2583:** measured **~59 fps**, ~63 MB / 8 s. Encoder-only MR/MC REGULAR sessions receive no frames on this HAL class. **Not offered** when `uhd60.sessionOk=false` (e.g. EXODUS: no 4K HS sizes).

**Video format FAB:** `VideoFormatPickerSheet` step **A — Audio** (Hi-Fi, wind NS, external mic, compression, ducking) — persists via `PreviewChromePreferences`.

**HFR audio:** `MediaCodecVideoRecorder` primes AAC before muxer start and waits for the audio track — avoids video-only muxer race at 120+ fps. Mux PTS use [audioEncoderSampleRateHz] from encoder output (not capture rate alone) so hi-fi does not sound half-speed when the HAL resamples.

**96 kHz AAC:** `PnsAacEncoderSupport.openBestAacEncoder` probes ranked AAC encoders (QTI → Android → others) at **96 → 48 → 44.1 kHz** when `audioHiFiCapture` is on; keeps only picks whose **output** `KEY_SAMPLE_RATE` matches the target. PCM / [AudioRecord] rate is aligned to the winning mux rate. Gallery DESCRIPTION uses the mux rate after save.

**Gates:** `scripts/pns_audio_quality_test.ps1`, `scripts/pns_shutter_sound_test.ps1`, `scripts/pns_audio_sprint_gate.ps1`.

---

## 11. Automation & ADB intent extras

**Rule:** `automationSuppressFacePipeline = true` **only** when `adbBracketPattern != null` — **never** for sequential RAW-only (`pns_preview_raw_count`) alone (breaks YUV / RAW session on legacy SKU).

| Extra | Effect |
|-------|--------|
| `pns_preview_dial=H` | Initial command dial |
| `pns_preview_dial=MONO` | Initial command dial forced to MONO; scripted mono verify accepts either hardware still success or fallback snapshot log |
| `pns_preview_raw_count=N` | Sequential RAW stills (keep face pipeline) |
| `pns_preview_focal_mm_slot=` | Focal mm for chrome gate |
| `pns_preview_imaging_profile` | Imaging profile override |
| `pns_preview_still_format` | Sprint 22.3 still-export override for composed smoke (`heic`, `motion_photo`, `tiff16`, `jxl`) |
| `pns_preview_raw_stream` | `RawStreamPreference` name |
| `pns_screen=preview` | Cold-start preview route |
| `pns_preview_adaptive_battery_pct` | PO.2 gate: override battery % for [PreviewAdaptiveFpsPolicy] |
| `pns_preview_adaptive_thermal_status` | PO.2 gate: override [PowerManager] thermal status int |
| `pns_preview_audio_hifi` | AS.1 — session seed `audioHiFiCapture` |
| `pns_preview_audio_wind` | AS.1 — session seed `audioWindNoiseReduction` (chrome) + maps to `windNoiseFilterEnabled` when `pns_preview_wind_noise_filter` absent |
| `pns_preview_wind_noise_filter` | 15.25 — session seed `HudSettings.windNoiseFilterEnabled` |
| `pns_preview_experimental_master` | M22.9 — seed `HudSettings.enableExperimentalAppBreakingFeatures` for max-res unlock proof lane |
| `pns_preview_experimental_max_res_unlock` | M22.9 — seed `HudSettings.enableExperimentalMaxResolutionUnlock` (CPH2583 root lane) |
| `pns_preview_experimental_vendor_session` | M22.9 — seed `HudSettings.enableExperimentalVendorSessionKeys` (independent vendor key lane) |
| `pns_preview_force_safe_mode` | M22.9 — force `ExperimentalSafeModeStore.safe_mode_active=true`, disable experimental flags, and verify fail-closed recovery |
| `pns_preview_max_res_sweep_session_keys` | M22.9b — CSV vendor **session** keys to sweep on camera `2` during still session-template build (`maxResSweep scope=session ...`) |
| `pns_preview_max_res_sweep_request_keys` | M22.9b — CSV vendor **request** keys to sweep on camera `2` during still request build (`maxResSweep scope=request ...`) |
| `pns_preview_timelapse_mode` | 15.27 — `off` / `photo` / `video` → `HudSettings.timeLapseMode` |
| `pns_preview_timelapse_running` | 15.27 — seed `intervalometerRunning` |
| `pns_preview_timelapse_interval_sec` | 15.27 — seed `intervalometerIntervalSec` (normalized to CC.1 options) |
| `pns_preview_focus_breathing_comp` | 15.28 — seed `HudSettings.enableFocusBreathingComp` |
| `pns_preview_automation_focus_rack_sec` | 15.28 — sweep manual focus diopters for gate (`PNS.FocusBreathing`) |
| `pns_preview_shutter_sound_pack` | AS.2 — session seed shutter pack key |
| `pns_preview_theme_mode` | **UX.1** — `System` / `Light` / `Dark` → `UxSettings` + `PnsTheme` |
| `pns_preview_workflow_preset` | **UX.3** — built-in `street` / `portrait` / `video_log` (dial + imaging + photo/video tray + optional FPS) |
| `pns_preview_open_gallery` | **UX.2/UX.3** — open bespoke gallery overlay on cold preview |
| `pns_preview_gallery_batch_share` | **UX.3** — int ≥ 2: auto-select N indexed items and `ACTION_SEND_MULTIPLE` |
| `pns_preview_readout_shutter_ns` | **M15.10** — lock shutter speed (ns) so ISO chase emits `PNS.Cam readoutChase` logs |
| `pns_preview_open_settings` | **M15.8** — open chrome Settings rail (`settingsRail=open` in `PNS.ChromeUx`) |
| `pns_preview_video_shutter_angle` | **M15.11** — video-primary: apply [VideoShutterAngle] preset (`Angle180`, …); logs `readoutManual videoShutterAngle=` |
| `pns_preview_platform_share_probe` | **IP.1** — [SharingManager] share probe on first indexed capture |
| `pns_preview_platform_file_provider_probe` | **IP.1** — FileProvider authority probe |
| `pns_preview_platform_widget_probe` | **IP.1** — log widget + installed external viewers |
| `pns_preview_lan_transfer` | **IP.2** — enable LAN HTTP server (`PnsConnectivity`) |
| `pns_preview_lan_transfer_probe` | **IP.2** — start LAN server + capability summary log |
| `pns_preview_webdav_probe` | **IP.2** — log WebDAV URL configured |
| `pns_preview_social_stream_probe` | **IP.2** — [SocialStreamHooks] skip/post probe |
| `pns_preview_collaborative_probe` | **IP.2** — [CollaborativeCapture] client counter log |
| `pns_screen=hardwarekeyprobe` | Engineering hardware-key probe screen; writes `files/HARDWARE_KEY_PROBE_LATEST.json` |
| `pns_preview_automation_hardware_key=true` | After ~8 s on preview, synthesize `KEYCODE_CAMERA` UP → `PNS.HardwareKey shutterFired` (`pns_hardware_shutter_verify.ps1`) |

**Hardware camera key (in-app):** When preview is foreground and **Settings → Extra shutters → Hardware camera key shutter** is on (`PreviewChromePreferences.hardwareCameraKeyCapture`, default **true**), [`PnsHardwareShutterRouter`] handles `KEYCODE_FOCUS` DOWN (center AF + poll until focused → **focus confirm chirp** via [`ShutterSoundManager.playFocusConfirm`], shutter volume / Silent rules) and `KEYCODE_FOCUS` UP (cancel poll). Full press `KEYCODE_CAMERA` UP fires tray composed still (same path as BT remote). Fleet matrix `product.hardwareButtons.interactiveProbe.distinctKeyCodes` adds programmable extra shutter keyCodes. Log tags **`PNS.HardwareKey`**, **`PNS.ShutterSound`**. Settings toggle hidden when [`FleetUiVisibilityGate`] reports no matrix evidence (`product.hardware_camera_key`). Cold launch from dedicated camera keys is OEM-dependent — see **Hardware launch (cold start)** help in preview Settings.

**Gates:** `scripts/pns_hardware_key_probe.ps1`, `scripts/pns_hardware_shutter_verify.ps1`.

**Platform integration (IP.1):** Deep links `pointandshoot://preview|camera|video|gallery|share` → [PlatformIntegration.applyDeepLinkToIntent]. Share ingress: [ShareReceiveActivity] (`ACTION_SEND` / `SEND_MULTIPLE`). Home widget: [PnsCameraWidgetProvider]. Sharing: [SharingManager] + `dev.pointandshoot.fileprovider`. Quick Settings tiles unchanged (`quicksettings/*TileService`).

**Connectivity (IP.2):** [LanMediaTransferServer] HTTP on Wi‑Fi (`0.0.0.0`, preferred **28766**, ephemeral fallback) — `GET /status`, `/files`, `/file?id=`. HUD: **LAN media transfer** toggle (`pns_connectivity.xml`). **Leaderboard contribute:** HUD toggle `PnsConnectivity.isLeaderboardContributeEnabled` — after Engineering Hub Full parity sweep, posts redacted bundle via [FleetLeaderboardSubmit] to `BuildConfig.LEADERBOARD_INGEST_URL` (empty = disabled). WebDAV PUT: [NetworkStorageClient] (user URL in prefs; no bundled FTP/SMB). Social: optional HTTPS webhook ([SocialStreamHooks]). Cloud: [CloudCaptureBackup] (UX.3). Collaborative: [CollaborativeCapture] + tether POST `/capture` (CC.3).

**IP gates:** `scripts/pns_platform_integration_test.ps1`, `scripts/pns_connectivity_test.ps1` (LAN status via `adb reverse` + host `curl` fallback on device).

**UX appearance prefs:** `UxSettings` (`pns_ux_settings`, key `theme_mode`). In-preview **Settings → HUD** rail: **Appearance** + **Workflow presets**. Photo chrome stays dark charcoal in all theme modes (layout lock).

**UX navigation (UX.2):** `NavigationUx.detectNavigationMode` reads `config_navBarInteractionMode` (0 = 3-button, 2 = gesture). `rememberNavigationUxSnapshot` logs `PNS.NavUx` + `PNS.AdbValidation` `navUx …`. Bottom capture tray wrapped in `PnsGestureExclusionBottomBand` (~24% height, API 29+). Gallery `BackHandler` clears batch selection before exiting.

**UX gallery batch share:** Grid **Select** → multi-tap → **Share** uses `ACTION_SEND_MULTIPLE`; log `PNS.Gallery` / `PNS.AdbValidation` `gallery batchShare count=N`.

**Cloud backup (UX.3):** `CloudCaptureBackup` (`pns_cloud_backup.xml`) — user SAF folder + optional Wi‑Fi-only; copies DCIM captures + `pns_backup_manifest.json`. Hooks: still save (`applyStillResultToGalleryThumb`), in-app video finalize. HUD: **Settings → HUD → Cloud backup**. ADB: `pns_preview_cloud_backup`, `pns_preview_cloud_backup_sync`, `pns_preview_cloud_backup_probe` (debug probe dir under app external files). Gate: `scripts/pns_cloud_backup_test.ps1`.

**Gates:** `scripts/pns_ui_modernization_test.ps1`, `scripts/pns_navigation_compatibility_test.ps1`, `scripts/pns_workflow_test.ps1` (`-AllPresets`), `scripts/pns_ux_gallery_batch_test.ps1`, `scripts/pns_cloud_backup_test.ps1`, combined `scripts/pns_ux_sprint_adb_gate.ps1`. Nav back smoke: `adb shell input keyevent KEYCODE_BACK` expects `navBack galleryExit` / `navBack previewGalleryClosed` in `PNS.AdbValidation`.

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

### M21 — Fleet parity sweep perf SLA (Quick / Full)

**Code:** `FleetParitySweepRunner` · `scripts/pns_fleet_parity_sweep.ps1` · `scripts/pns_m21_gate.ps1`

| Gate | SLA / threshold |
|------|-----------------|
| Quick cell count | **≥ 50** scripted catalog ids (`quickCellIds`) |
| Quick sweep wall time | **≤ 120 s** host wait (in-app `durationMs` typically **< 2 s**) |
| Full sweep wall time | **≤ 240 s** host wait |
| Ship blockers | **0** blocking gaps (`shipBlockerGapCount`) for gate pass |
| Delivery verify (`-IncludeRecord`) | **1920×1080 @ 30** default; fps **±3** (≤60) or **≥ 75%** target (HFR) — same as `pns_mediacodec_hfr_verify.ps1` / `FleetDeliveryProbe` |
| Thermal cost | `parity_thermal_cost.md` from `dumpsys thermalservice` before/after record |

**Parity perf catalog cells:** `perf.capture_latency`, `perf.cold_preview_ms`, `perf.first_frame_ms`, `perf.battery_adaptive_fps`, `perf.thermal_adaptive` — compared via matrix `performanceProbes` when full tier present; quick tier uses HAL-advertised only (`sessionOk=null`).

### M22 — Proof-pack merge closure

**Code:** `scripts/pns_fleet_parity_sweep.ps1` (`-IncludeProofPack`, `-IncludeRecord`) · `scripts/pns_parity_proof_pack.ps1` · `scripts/pns_m22_gate.ps1`

| Gate | Requirement |
|------|-------------|
| Full parity with proof merge | `pns_fleet_parity_sweep.ps1 -Mode Full -IncludeRecord -IncludeProofPack` |
| Proof manifest schema | `parity_proof_manifest.v1` (`scripts/parity_proof_manifest.json`) |
| Proof results schema | `parity_proof_results.v1` (`proof_pack/parity_proof_results.json`) |
| Matrix-gated rows | `skippedReason=matrix_gate:*` counts as proven in merge (`provenOk=true`, `gap=OK`) |
| M22 closure floor | merged `OK >= 163`, `GAP_UNAUTOMATED=0`, `GAP_ADVERTISED_NOT_PROVEN=0`, `GAP_PLANNED=0` on CPH2583 |
| Host merge fixture | `pns_fleet_parity_sweep.ps1 -HostProofPackMergeFixture` validates merge semantics in CI without USB |

---

## 12. GLES preview geometry

**Code:** `LutCameraPreviewRenderer.setGeometry`, `TexturePreviewFit`, `PreviewMainViewport` (`AndroidView` `update` + `OnLayoutChangeListener` only — no coroutine/ST listeners).

| Mode | `coverCrop` | Behavior |
|------|-------------|----------|
| Photo / cover (default) | **true** | Center-**crop** — fills 3:4 finder tile |
| Video / contain | **false** | Center-**contain** — letterbox/pillarbox inside tile (`LaunchedEffect(primaryPhoto)` syncs pref) |

`TexturePreviewFit.computeFitRect(..., coverCrop)` returns overlay clip rect (full view when crop; inset rect when contain). Face HUD uses same flag via `FaceHudOverlayMapping.mapBufferPointToTile`.

**Gallery viewer (15.7):** `BespokeGalleryScreen` pager uses fixed **3:4** outer tile; media `ContentScale.Fit` inside.

**Invariant (May 2026):** `LutCameraPreviewRenderer.setGeometry` only from **`PreviewMainViewport`** — `AndroidView` `update` + `OnLayoutChangeListener`.

**Do not reintroduce without redesign + USB proof:**

- `LaunchedEffect` → `setGeometry` on buffer/generation churn
- Controller `setPreviewBufferGeometryListener` → `queueEvent { setGeometry }`
- `setPreviewDisplayLayoutSyncListener` / `previewLayoutSyncNonce` cold-start overrides
- `GLSurfaceView.setPreserveEGLContextOnPause(true)` on legacy SKU (abandoned surface)

**Gallery return:** `restartMainActivityCold` when tray opens external viewer successfully; else `kickPreviewPipelineRestart()` + optional `GLSurfaceView.post { requestLayout() }`.

**Tray surface restore:** `PreviewLastSurfacePrefs` (`pns_preview_last_surface.xml`) stores the last **Photo** / **Video** / in-app **Gallery** tray surface. Cold start restores it unless ADB `pns_preview_primary_photo`, `MediaStore` video capture intents, or still/video return contracts override. Saved on tray changes and `ON_STOP` via `LaunchedEffect` in `PreviewEngineScreen`.

**Tray mode settings (photo vs video):** `PreviewTrayModeStore` (`pns_tray_mode_settings.xml`) snapshots per-tray readout overrides (ISO / shutter / AWB / AE lock / ISO auto range), target FPS, OIS, EIS, video shutter-angle preset, and last prime focal target. On Photo ↔ Video FAB toggle, the outgoing mode is saved and the incoming mode is restored (`PNS.ChromeUx trayModeRestore=…`), including focal-target restore for each tray mode. Video locked SS from shutter-angle presets no longer carries into photo mode. **STAB chip** reflects live HUD toggles synced to [PreviewController] (`setLiveHudSettings`); EIS is hidden when manual sensor / locked shutter blocks preview EIS (same rule as [PreviewStabilization.applyToRequest]). Labels: **OIS** / **EIS** / **OIS+EIS** when active; **Off** when advertised but disabled. Readout strip auto-scales chip typography to fit AF + STAB + LUT chips (incl. angle-prefixed SS); bottom tray height **76 dp** (was 92). Still shutter taps now queue while previous captures process; top status line reports queue progress (`Still queue X/Y - pending N`).

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
| `PNS.ReferenceAppStill` | ReferenceCam leaf metering (when not chase-gated) |
| `PNS.PostRawBoost` | Post-RAW sensitivity |
| `PNS.MCVideoRec` | Video encoder color VUI |
| `PNS.AdbValidation` | Scripted capture verify needles |
| `PNS.NavUx` | Navigation mode + inset snapshot; gesture exclusion debug |
| `PNS.Workflow` | Workflow preset apply |
| `PNS.Gallery` | In-app gallery batch share |
| `PNS.CloudBackup` | SAF folder backup copies + sync |
| `PNS.FleetMatrix` | Fleet matrix quick/full scan tier, camera count; **`hardwareLaunch`** / **`hardwareButtons`** on rescan |
| `PNS.HardwareKey` | Dedicated camera key half-press AF + full-press shutter (`shutterFired source=…`) |
| `PNS.HardwareKeyProbe` | Engineering probe key events; **`HARDWARE_KEY_PROBE_DONE`** in `PNS.SWEEP_SIGNAL` |
| `PNS.FleetVisibility` | Consumer chrome hide / root-only tap (`hidden`, `rootOnlyTap`) |
| `PNS.ProbeHub` | Engineering hub search picks (`settingsSearchPick`) |
| `PNS.VideoCapProbe` | MediaCodec perf matrix; `capProbeInvalidate` on rescan |
| `PNS.HardwareKey` | Secure lockscreen launch policy marker (`secureLaunchPolicy showWhenLocked=true turnScreenOn=true …`) |

---

## 14. Fleet UI visibility (M17)

**Code:** `fleet/FleetUiVisibilityGate.kt`, `fleet/FleetChromeVisibility.kt`, `fleet/CameraCapabilityCatalog.kt`

| Policy | Behavior |
|--------|----------|
| `HideWhenUnavailable` | Do not compose consumer control (empty QS cell) |
| `RootOnly` | Show blue chip/toggle; tap → standardized toast until SU granted |
| `ShowDisabledEngineering` | Visible in engineering contexts only (hub catalog) |

**Matrix artifacts:** `files/fleet_device_matrix.json` (`capabilityCatalog` slice) + `files/fleet_device_capability_summary.md` (ADB pull). Hub: **Device capability matrix** (`FleetMatrixHubScreen`) — Summary · By camera · Features · Raw JSON.

**Hub search:** `ProbeHubSearch.kt` indexes hub menu + catalog + chrome settings; pick navigates to matrix Features tab, HUD/settings, or preview settings rail with **`rememberSettingHighlightFlash`** (3× pulse).

**Do not:** Remove locked 7×3 slot definitions when hiding features; use empty `Box`. Do not add legacy SKU-only visibility branches without `FleetDevicePolicy` plugin.

---

## 15. Accessibility (a11y)

**Code:** `AboutScreenA11y.kt`, `ChromeSettingsA11y.kt` — stable `contentDescription` strings pinned by JVM tests.

| Surface | Object | Strings | Consumers |
|---------|--------|---------|-----------|
| About / heritage | `AboutScreenA11y` | `BACK`, `RELEASE_NOTES`, `CHANGELOG`, `PRIVACY`, `VENMO` | `AboutScreen.kt` external link chips |
| Settings search | `ChromeSettingsA11y` | `SEARCH_FIELD`, `SEARCH_ICON` | `ChromeSettingsSearch.kt` |

**Tests:** `AboutScreenA11yTest.kt`, `ChromeSettingsSearchA11yTest.kt` — assert non-blank descriptions; `EXTERNAL_LINK_DESCRIPTIONS` list completeness.

**Policy:** New preview/settings chrome that exposes tappable icons or external links must add constants here (not inline literals) and extend the matching JVM test in the same commit.

---

## Appendix A — Change log (doc metadata)

| Date | Change |
|------|--------|
| 2026-05-21 | Initial source-of-truth doc: H mode, readout chase, session locks, fleet pointers. |
| 2026-05-21 | Parity: removed **RAW-only** darken; **TARGET_MEDIAN_BIN = 40** (single knob for DNG + tonal). |

| 2026-05-29 | M17: fleet UI visibility gate, hub search/highlight, 1080p@30 video format + probe invalidation on matrix rescan. |

*Append rows here when this file is updated for traceability.*
