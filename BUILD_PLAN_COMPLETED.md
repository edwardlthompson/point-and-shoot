# BUILD_PLAN — completed milestones & sprints

Archived from [BUILD_PLAN.md](BUILD_PLAN.md). **Milestones 0–7** (2026-05-14); **Milestone 8–9**, performance backlog; **Milestone 10** sprints **10.1–10.16** + gate (2026-05-17); **Milestone 11** sprints **11.1–11.4** + gate (2026-05-17); **Milestone 12** sprints **12.1–12.6** + gate (2026-05-17); **Milestone 13** fleet RAW sprints **13.1–13.6**, **13.3** (a–h, e, f, g), **13.8** (a–d) (2026-05-20); **Milestone 13V** video expansion **13V.1–13V.16** (2026-05-17 / **13V.16** USB May 2026); **Bespoke Gallery** **BG.1–BG.3** (2026-05-22); **Performance & Optimization** **PO.1–PO.2** (2026-05-22). **Open:** Milestone **13.7** gate + **H.7**, **13V.17–13V.18**, **Milestone H**. Active roadmap: **[BUILD_PLAN.md](BUILD_PLAN.md)**.

---
## Milestone 0 — Baseline quality bar (always on)

**Objective:** Every change stays buildable and testable.

| Sprint | Scope | Sprint check |
|--------|--------|--------------|
| **0.1 Toolchain** | Host build + tests | `pns_verify_toolchain.ps1 -RunTests` → `RESULT: PASSED` |
| **0.2 Device smoke** (when hardware attached) | Install + probe smoke | `pns_hfr_autorun.ps1 -RunProbeSmoke -Sideload` **or** `-SkipSideload -SkipGradleBuild` after a fresh APK; adb shows `device` |

**Milestone 0 gate**

| Check | Pass criterion |
|-------|----------------|
| Host | `pns_verify_toolchain.ps1 -RunTests` exit 0 |
| Optional device | Smoke script exit 0; §5 log row when run |

---

## Milestone 1 — Foundations & FOSS

**Objective:** Repository structure, licensing, and CI parity for the default pipeline.

### Sprint 1.1 — FOSS & dependency hygiene

- [x] [HOST] Apache-2.0, no proprietary binaries in tree, no Play Services / Firebase / Ads in Gradle catalog (enforced by verifier).
- [x] [HOST] `LICENSES.md` + `pns_license_inventory.ps1` drift check passes under toolchain.

### Sprint 1.2 — CI baseline

- [x] [HOST] `.github/workflows/toolchain-verify.yml` runs assembleDebug + `:app:testDebugUnitTest` on push/PR.

**Milestone 1 gate:** `pns_verify_toolchain.ps1 -RunTests` PASSED; CI workflow YAML present.

---

## Milestone 2 — Capability probes & machine-readable intelligence

**Objective:** Camera/HDR/encoder truth on reference hardware; reproducible JSON artifacts.

### Sprint 2.1 — Probe surfaces & pulls

- [x] [HOST] `CameraCapabilitiesProbe`, Markdown export, `PROBE_RESULTS.md` populated from device.
- [x] [HOST] JSON probes (`deep_caps_*.json`, `enc_probe_*.json`, `exhaustive_probe_*.json`, `legacy_camera1_*.json`) + `pns_hfr_autorun.ps1` pull paths.

### Sprint 2.2 — Phase 0 V&V (reference device)

- [x] [ADB] Vendor keys, 120fps preview candidacy, RAW12 feasibility, validated HFR encode matrix + About “live probe” hydration per prior §5 evidence.

**Milestone 2 gate:** Full suite optional but recommended: `-RunFullSuite -ExhaustiveHfrOnly -MaxRuns 1` exit 0; artifacts under `hfr-runs/`; §5 row when run completes.

---

## Milestone 3 — Hardware ↔ software mapping

**Objective:** Dodge profile: focal modes → camera IDs, crops, constraints.

- [x] [HOST] `DODGE_PROFILE.md` master mapping + preview crop wiring (`SensorCropGeometry`, `CropPlan`, …).
- [x] [ADB] Topology / focal clusters / macro diopter gate (prior Round 11 lens-info evidence).

**Milestone 3 gate:** Toolchain PASSED; mapping doc matches code (`SensorCropGeometryTest`, `DngDefaultUserCropRatiosTest`, `CropPlanTest`, `BackCameraRoleResolverTest`, etc.); optional refresh: **`scripts/pns_milestone3_gate.ps1`** (add **`-RunDeviceSmoke`** for sideload + `seedOk slot=M23` log proof).

---

## Milestone 4 — Imaging engine & capture reliability

**Objective:** Stable Camera2 preview/capture, RAW→DNG, bracketing, metering/AF features validated on device; NDK encode path completed when scheduled. **Sprint 4.4** tracks Camera2 contract/metadata upgrades aligned with **`docs/CAMERA2_KEYS_AND_APIS_REFERENCE.md`** (flash / ZSL / triggers / antibanding land there or in linked preview still paths). **Sprint 4.5** adds system **`ACTION_VIDEO_CAPTURE`** parity (Lineage / default-camera style lists), BKT primary-shutter behavior, bracket **`CaptureRequest`** parity with single stills, and user-tunable ISP keys + software JPEG quality (engine here; **HUD** surface in **Milestone 5 Sprint 5.1**).

### Sprint 4.1 — Host-complete capture path (remaining encoder bodies)

- [x] [HOST] High-speed preview session, RAW12 `Dng12Saver`, `CaptureHaptics`, JNI shell `libpns_native.so`, `NativeEncoders` / `EncoderRoute`.
- [x] [HOST] **NDK encode bodies:** libavif (SVT-AV1 encode) + libjxl via CMake FetchContent; JNI bodies in `native/pns_native.cpp` (`BUILD_PLAN` / `NDK_PLAN`).

### Sprint 4.2 — Advanced metering & AF (**ADB closes parents**)

**Parents:** all **[ADB]** children below **`[x]`** with **`PROBE_BUILD_PLAN.md` §5 — 2026-05-10** (`adb_preview_validate_20260510_021941`, device **`8bf09993`**).

- [x] [ADB] **Highlight metering (dial H)** — YUV histogram → **`HighlightMeter`** AE comp applied; logs **`PNS.AdbValidation`** **`highlightMeter ev=… aeComp=… dial=H`** (≥3.5s throttle) in **`logcat_highlight_dial_H.txt`** same folder.
- [x] [ADB] **Eye-AF** — **`eyeAf faceDetectMode=`** + **`availableModes=`** logged once per session (reference HW: **SIMPLE** only — no **FULL** in list); **`eyeAf statisticsSample`** when **`STATISTICS_FACES`** non-empty. Multi-distance / lighting / **FULL** when advertised → **Milestone H** photo matrix.
- [x] [ADB] **3D tracking** — **`tracker statisticsPipeline active`** proves **`TrackerState`** wired to metadata; **`tracker lockedIds=…`** on lock-set delta when faces appear. Intentional dropout / re-acquire torture → **Milestone H**.
- [x] [MIXED] **Bracketing BKT** — **[ADB]** **`captureBracketBurst pattern=Three ok=true`** + three **`pns_*_bkt?of3*.dng`** writes in **`logcat_bracket_bkt3.txt`**; **[HOST][HUMAN]** bracket desktop regroup — **Milestone H**. **Follow-up (operator UX):** primary shutter + tap-to-shoot in dial **BKT** must invoke **`captureBracketBurst`** (not a single **`captureRawStill`**) — **Sprint 4.5**.

#### Highlight (H) metering — OEM-style behavior mapping (design note)

This project does **not** replicate Ricoh GR (or any vendor) firmware. Public OEM copy describes **goals** (preserve highlight gradation, tame spotlight blowout, accept deeper shadows); below maps those goals to **our** mechanisms so tuning stays intentional.

| Documented “highlight-weighted” style goal | How we approximate it (Android Camera2) |
|-------------------------------------------|----------------------------------------|
| **Preserve highlight texture / reduce clip** — prioritize exposing so bright areas keep tonal separation | **Histogram-driven EV suggestion:** metered peak bin `p` (bright-tail percentile + peak blend for tiny hot regions) is pulled toward an **effective ceiling** between [`DEFAULT_HIGHLIGHT_CEILING`](app/src/main/java/dev/pointandshoot/HighlightMeter.kt) (**40**) and [`RELAXED_HIGHLIGHT_CEILING`](app/src/main/java/dev/pointandshoot/HighlightMeter.kt) (**126**). **Tier floors** when `p` is near 255 enforce strong negative EV before gain. Output maps to **`CONTROL_AE_EXPOSURE_COMPENSATION`** while AE stays on (**[`PreviewEngineScreen.kt`](app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt)** — `aeHighlightCompensationValue`, `processYuvForHighlight`). |
| **Spotlight / filament / tiny specular on dark surround** — aggressive highlight pull | **Low “diffuse” blend** [`diffuseCeilingBlendWeight`](app/src/main/java/dev/pointandshoot/HighlightMeter.kt): small fraction of pixels ≥ bin **192** and lower **p75** keeps weight ~0 → ceiling stays **40**, strong darken. **Peak detector** (`minPeakSupportCount`) prevents the bulk histogram from masking a sun disk or lamp filament. |
| **Broad bright scenes** (walls, overcast sky, high-key interiors) — avoid treating the whole frame like one specular | **Raise effective ceiling** when **hot-pixel fraction** or **75th-percentile bin** indicates diffuse brightness (`DIFFUSE_*` thresholds). Optional **negative-EV compression** when diffuse blend exceeds **`COMPRESS_W_DIFFUSE_MIN`** and peak is below near-clip — softer mid-range pull so rooms do not “globally” stop down. |
| **Shadow side darker than multi-segment / matrix** — acceptable trade | We bias exposure via **AE compensation**, not a multi-zone weighted average; shadows stay dark unless the **positive-EV** path fires. No baked-in shadow lift in H mode comparable to OEM dynamic-range expansion. |
| **Lift shadows in genuinely dark environments** (within H-mode constraints) | **Median-aware brighten boost** on positive suggestions only (`darkenBrightenBoostForMedian` — cap **`DARK_BRIGHTEN_BOOST_MAX`**). |
| **Finder matches file** — same exposure intent for preview and DNG | Still **`TEMPLATE_STILL_CAPTURE`** uses the same **`applyScalerCropAndMetering(..., aeHighlightCompensationValue())`** as repeating preview when not in readout manual ISO/shutter (**[`captureRawStill`](app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt)**). |
| OEM docs sometimes mention **color of highlights** (e.g. colored stage lighting) | **Gap:** metering uses **Y-plane luma histogram** only (`PreviewLumaHistogram` → `HighlightMeter`). No separate chroma-aware clip estimate yet. |

**Tuning knobs (primary):** `RELAXED_HIGHLIGHT_CEILING`, `DIFFUSE_HOT_FRAC_*`, `DIFFUSE_P75_*`, `DEFAULT_HIGHLIGHT_DARKEN_GAIN`, `NEGATIVE_EV_COMPRESS_POWER`, preview smoothing/deadbands in **`PreviewController`** (`highlightMeterEvEma`, `highlightEvStabilityZone`, deadband constants).

### Sprint 4.3 — Phase 1 V&V (**camera reliability**)

- [x] [ADB] **10 consecutive captures** without session death — §5 **2026-05-10**: USB **`8bf09993`**; `pns_adb_preview_validate.ps1` artifact **`hfr-runs/adb_preview_validate_20260510_020501/`** contains **`captureRawStill k/10 ok=true`** lines **`1/10`–`10/10`** + **`finished sequential RAW stills n=10`**.
- [x] [ADB] **Logcat cleanliness** — no repeating Camera2 fatal/error spam in the same run (`summary_grep.txt` **ERROR** sweep clean for Camera paths); **`MediaGeotag`** failures log **one-line** warnings only (no throwable stacks) so scripted greps stay readable.
- [x] [HOST] **RAW12 / Ultra-Max DNG path + ADB automation** — `ImagingProfile.UltraMax` → `CaptureStorage.CaptureKind.DngRaw12` (`toDngCaptureKind()`); intent extra **`pns_preview_imaging_profile`** (`standard_pro` \| `ultra_max`, see **`EXTRA_PNS_PREVIEW_IMAGING_PROFILE`**); validate script scenario **`raw12_ultra_max_x1`** (Ultra-Max + one sequential RAW); **`ImagingProfileTest`** pins mapping + `byId`; **`ImagingProfile.all` / `byId`** avoid JVM companion-init null singletons (same issue as [EncoderRoute]). **Desktop** pull/open of Ultra-Max DNG in RAW tools → **Milestone H.1** (human), not a Sprint 4.3 gate.
- [x] [ADB] **Ultra-Max scripted smoke** — §5 **2026-05-10**: USB **`8bf09993`**; **`am start`** with **`pns_preview_imaging_profile=ultra_max`** + **`pns_preview_raw_count=1`** → **`PNS.AdbValidation`** **`automation extras … profile=ultra_max`** + **`captureRawStill 1/1 ok=true`** **`saved=pns_*_ultra_max_*.dng`** + **`finished sequential RAW stills n=1`**.

### Sprint 4.4 — Camera2 capture contract & metadata (host + device)

**Reference:** `docs/CAMERA2_KEYS_AND_APIS_REFERENCE.md` (API-level key catalog); per-device truth remains **`availableCaptureRequestKeys`** + probe exports (`PROBE_EXPORT_LATEST.md`, `pns_ae_highlight_probe_adb.ps1`).

**Improvements (ship in preview + still paths before “new feature” bullets):**

- [x] [HOST] **Tap AF / AE precapture triggers (initial)** — After tap metering, **`CameraCaptureSession.capture`** one-shot with **`CONTROL_AF_TRIGGER_START`** + **`CONTROL_AE_PRECAPTURE_TRIGGER_START`** when keys exist (`fireTapFocusAfAeTriggers`); skipped for constrained high-speed preview. **Follow-up:** gate still capture on **`CONTROL_AF_STATE`** / **`CONTROL_AE_STATE`** / cancel triggers (`CONTROL_AF_TRIGGER_CANCEL`) per HAL best practice; §5 device matrix.
- [x] [HOST] **AE antibanding** — `PreviewAeAntibanding` sets `CONTROL_AE_ANTIBANDING_MODE` (prefers **AUTO**, else **50 Hz** / **60 Hz**, else first HAL mode) on preview + still requests when the key is advertised. Optional **STATISTICS_SCENE_FLICKER**-driven policy remains open.
- [x] [MIXED] **`CONTROL_ENABLE_ZSL`** — **`PreviewStillCaptureHints.applyZslIfCompatible`**: `CONTROL_ENABLE_ZSL=true` on single + bracket still when JPEG surface is attached, key is in **`availableCaptureRequestKeys`**, and manual ISO/exposure overrides are off (same guard pattern as `CaptureLatencyProbeScreen.kt`). §5 fleet matrix optional.
- [x] [MIXED] **Stabilization** — `CONTROL_VIDEO_STABILIZATION_MODE` and/or `LENS_OPTICAL_STABILIZATION_MODE` where characteristics allow; policy tied to focal / FPS / user pref without breaking frozen preview chrome layout.
- [x] [HOST] **JPEG request metadata** — **`PreviewStillCaptureHints`**: `JPEG_ORIENTATION` (degrees via **`RawCaptureSupport.orientationClockwiseDegForDng`**) + optional **`JPEG_GPS_LOCATION`** when embed-location pref is on and keys are advertised; single RAW still + bracket still.
- [x] [MIXED] **Logical multi-camera readout** — `PreviewController` tracks `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID` (API 29+) from repeating results; **Phy** chip on `PreviewReadoutStrip` when non-blank (`PreviewEngineScreen.kt`).
- [x] [HOST] **Session defaults (macro `setSessionParameters` path)** — `PreviewAeAntibanding` on the session-parameters preview `CaptureRequest.Builder` before `build()`; `outputConfigurationsWithOptionalStreamUseCases` on the macro `SessionConfiguration` output list (API 33+). Broader session-wide defaults for non-macro sessions remain backlog.
- [x] [HOST] **Richer capture metadata (JPEG USER_COMMENT)** — **`StillCaptureMetadata.fillExifFields`**: appends **`LENS_FOCUS_DISTANCE`** (FD), **`LENS_STATE`**, **`CONTROL_AF_STATE`**, **`SENSOR_ROLLING_SHUTTER_SKEW`** to **`TAG_USER_COMMENT`** when present on **`TotalCaptureResult`** (DNG **`ExifInterface`** pass + JPEG companion). TIFF IFD rational patches / full DNG sidecar dump remain backlog.
- [x] [MIXED] **`CONTROL_POST_RAW_SENSITIVITY_BOOST`** — optional policy when advertised and compatible with highlight / manual readout modes.

**Larger features (schedule after improvement row is mostly closed or when product pulls forward):**

- [x] [MIXED] **Camera extensions** — **`CameraExtensionSupport`** (probe markdown + **`HardwareCaps`** / **`CapabilityGate.Feature.CameraExtensions`**); **`CameraExtensionSessionSmokeRunner`** exercises **`CameraDevice.createExtensionSession`** and logs **`PNS.AdbValidation`** **`cameraExtensionSession …`**; cold start **`--es pns_screen cameraextsmoke`** (optional **`--es pns_preview_camera_id`**). Default preview finder remains the regular session (no OEM extension finder as default-on).
- [x] [MIXED] **HDR / 10-bit / color space on live preview** — **`PreviewHdrSessionSupport`** + **`SessionConfigurationCompat.isMultiOutputSessionSupportedWithDynamicRangeOnPreview`**; **`outputConfigurationsWithOptionalStreamUseCases`** applies **`OutputConfiguration.setDynamicRangeProfile`** on the first preview output when **Settings → HUD → HDR / 10-bit preview session** is on; **`PNS.AdbValidation`** **`previewSessionDynamicRange profile=…`**. **Also Milestone 10 Sprint 10.5** — same row ticked there.
- [x] [MIXED] **`CONTROL_AUTOFRAMING`** — when `CONTROL_AUTOFRAMING_AVAILABLE`; distinct from ML Kit face track.
- [x] [MIXED] **Reprocessing / input surface** — **Shipped:** **`PreviewReprocessStillHints`** in **`buildProbeReport`** + **`HardwareCaps`** / **`CapabilityGate.Feature.ReprocessSession`**; **`CaptureLatencyProbeScreen`** `reprocess_input_to_jpeg_session` supported-query (device evidence **§5 2026-05-07** `reprocessInputToJpegSessionSupported=true` on **8bf09993**). **ZSL** path remains **`PreviewStillCaptureHints.applyZslIfCompatible`** (earlier row). **Backlog:** preview-engine **`createReprocessCaptureRequest`** / input reprocess still capture; **`REPROCESS_EFFECTIVE_EXPOSURE_FACTOR`** on requests once that path exists.
- [x] [HOST] **Stream use cases** — `OutputConfiguration.setStreamUseCase` with `CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW` / `…STILL_CAPTURE` (first surface vs rest) in `outputConfigurationsWithOptionalStreamUseCases` (`Camera2SessionCompat.kt`); preview engine normal session enables hints on API 33+ with synchronous **retry without hints** on throw; macro session uses the same helper. Query/advertise `SCALER_AVAILABLE_STREAM_USE_CASES` per device remains optional polish.
- [x] [MIXED] **Torch / flash strength** — **`PreviewFlashPolicy`** sets **`CaptureRequest.FLASH_STRENGTH_LEVEL`** from **`FLASH_INFO_STRENGTH_*`** when advertised (preview torch incl. **On**→torch fallback, **`FLASH_MODE_SINGLE`** stills); session-only (no **`CameraManager.turnOnTorchWithStrengthLevel`** while **`CameraDevice`** holds the stream). **`PreviewFlashPolicyTest`**. Optional fleet LED / capture proof remains **§5** matrix when hardware exposes the keys.

### Sprint 4.5 — System camera eligibility, BKT shutter parity, hardware JPEG tuning

**Order:** (1) manifest + activity contracts → (2) BKT UX fix + bracket request parity → (3) Camera2 ISP keys + prefs → (4) HUD controls (see **Milestone 5 Sprint 5.1**). **Chrome lock:** preview chrome layout stays per **`docs/preview-chrome-layout-style-guide.md`**; new controls live in **Settings → HUD** (`HudSettingsScreen`), not the locked **7×3** quick grid + focal row.

- [x] [HOST] **`MediaStore.ACTION_VIDEO_CAPTURE`** — Add **`android.media.action.VIDEO_CAPTURE`** intent filter on **`MainActivity`** (distinct from **`INTENT_ACTION_VIDEO_CAMERA`**). Extend **`resolveLaunchScreenForMain`** + **`CameraCapabilitiesProbe`** `previewSeedPrimaryPhoto` so **`ACTION_VIDEO_CAPTURE`** routes like the existing video-camera intent (video-primary preview).
- [x] [MIXED] **`VideoCaptureReturnContract`** — When launched **`startActivityForResult`** with **`ACTION_VIDEO_CAPTURE`**, complete recording then **`setResult(RESULT_OK, Intent)`** with the saved video **`data` Uri**, honor **`MediaStore.EXTRA_VIDEO_QUALITY`** / size / duration where feasible, **`finish()`** on success; cancel path **`RESULT_CANCELED`**. Without this, do not ship the manifest filter alone.
- [ ] [ADB] **Default / system camera lists (Lineage-class)** — On a physical device: **`adb shell cmd package resolve-activity --brief android.media.action.VIDEO_CAPTURE`** (and **`STILL_IMAGE_CAMERA`**) shows **`dev.pointandshoot/.MainActivity`**; optional Settings → Default apps → Camera (or ROM equivalent) picks the app. Log serial + ROM in **`PROBE_BUILD_PLAN.md` §5** when an **[ADB]** / **[MIXED]** row above closes.
- [x] [HOST] **BKT: primary shutter + tap-to-shoot** — When **`commandDialMode == BKT`** and **`canCaptureBracketBurst()`**, **`onCaptureDng`** must call **`captureBracketBurst`** (default **`BracketPattern.Three`**, or a **persisted** last rail choice for 3/5/7) instead of **`captureRawStill`**. If RAW path is not ready (e.g. fps ≥ 120), keep user-visible guidance (snackbar); **JPEG-only** profile: explain bracket needs RAW (no silent single-JPEG fallback). Align **volume-up** BKT trigger with the same default/persisted pattern (today it hardcodes **Five**).
- [x] [HOST] **Bracket still `CaptureRequest` parity** — Apply **`RawStillProcessingHints.applyLinearRawFriendlyProcessing`** (and any new **`PreviewJpegProcessingHints`** from the next bullet) to **bracket** **`TEMPLATE_STILL_CAPTURE`** builders in **`PreviewEngineScreen.captureBracketBurst`**, same as single RAW / JPEG-only stills, so companion JPEGs do not use harsher HAL defaults than H-capture.
- [x] [HOST] **Hardware JPEG / ISP prefs (engine)** — Persisted user bias (e.g. **`HudSettings`**) resolved through a small helper (e.g. **`PreviewJpegProcessingHints`**) into discrete **`EDGE_MODE`**, **`NOISE_REDUCTION_MODE`** (incl. API 34+ **`MINIMAL`** when listed), **`TONEMAP_MODE`**, optional **`COLOR_CORRECTION_MODE`** where **`applyReadoutManualExposureAndWb`** already sets CC; apply on **`buildPreviewCaptureRequestBuilder`**, **`captureRawStill`**, **`captureJpegHardwareStill`**, and bracket stills. Document in UI copy that Camera2 exposes **modes**, not continuous “sharpening dials.”
- [x] [HOST] **Software JPEG quality** — Expose persisted quality (e.g. 70–100) for **`Bitmap.compress`** in **`saveHardwareJpegCompanion`** (and any other fixed-quality software re-encode on that path).
- [x] [MIXED] **Capture regression gate** — After changing still / preview **`CaptureRequest`** keys for this sprint: **`scripts/pns_capture_pipeline_verify.ps1`** (or **`pns_photo_capture_verify.ps1`**) on USB per **How agents must execute** item **11**; respect **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** / **`AGENTS.md`** (no **`automationSuppressFacePipeline`** on sequential RAW alone; **§4a** / **§2** fleet rules).
- [x] [HOST] **Bracket EV distinctness (follow-up)** — If three files still match exposure after shutter fix, inspect **`BracketScheduler.aeStepsFor`** for duplicate integers after clamp to **`CONTROL_AE_COMPENSATION_RANGE`**; add JVM tests + optional respacing within range.

**Milestone 4 gate**

| Check | Pass criterion |
|-------|----------------|
| Host | `pns_verify_toolchain.ps1 -RunTests` + ReadLints clean on touched files |
| Device | §5 rows for **every** closed [ADB] bullet in sprints 4.2–4.3; `pns_adb_preview_validate.ps1` exit 0 on reference device |
| Encoder bodies | Sprint 4.1 NDK rows `[x]` when landed |
| Sprint 4.4 | Optional: closing any **[MIXED]** / **[ADB]** row requires §5 + `PNS.AdbValidation` or scripted validate evidence (same standard as 4.2–4.3) |
| Sprint 4.5 | Closing **[MIXED]** / capture-tuning rows: **`pns_capture_pipeline_verify.ps1`** (or equivalent) on USB when still/preview requests change; §5 note for **`ACTION_VIDEO_CAPTURE`** resolve / default-camera exercise when those rows close |

---

## Milestone 5 — HUD, dial & street UX

**Objective:** Operator-facing surfaces stable and regression-tested.

### Sprint 5.1 — Pro HUD (ship-ready)

- [x] [HOST] Command dial, HUD toggles, tally/timecode, Pro HUD overlay wiring.
- [ ] [HOST] **Hardware JPEG (Camera2) — HUD controls** — **Settings → HUD** (`HudSettingsScreen` / `HudSettings`): sliders or stepped controls wired to Sprint **4.5** persisted prefs (EDGE/NR/tonemap/CC bias proxies + software JPEG quality). Copy must state HALs expose **discrete modes**, not analog sharpening. **Do not** alter locked preview chrome geometry (**`docs/preview-chrome-layout-style-guide.md`** / `.cursor/rules/preview-chrome-ui-lock.mdc`).

### Sprint 5.2 — Phase 2 V&V

- [x] [ADB] Mode transitions deterministic and logged (no hidden state) — `PNS.ModeTransition` + `ModeTransitionLog` / `TrackModeTransition` (camera, fps, imaging profile, recording, focal crop, command dial) and `preview_pipeline_restart` from `PreviewController.maybeRestartBody`; ADB intent dial aligned with controller via `SideEffect` (avoids first-frame `M` vs `H` skew). Script: `pns_adb_preview_validate.ps1` scenario `sprint52_mode_vv` + log tags.
- [x] [ADB] No UI-induced capture regressions (preview stable) — same May 2026 device pass: cold start `ultra_max` + dial `H` shows monotonic `seq=*` `PNS.ModeTransition` lines, `preview_pipeline_restart` with consistent `commandDial=H`, no `ERROR` / session death in preview validate artifacts; see `PROBE_BUILD_PLAN.md` §5.

### Sprint 5.3 — Phase 3 polish

- [x] [HOST] **Street (Snap) program** — With **`CommandDialMode.S`** and **no tap metering**, `PreviewController.applyScalerCropAndMetering` applies snap AF: **`CONTROL_AF_MODE_OFF`** + **`LENS_FOCUS_DISTANCE` = 0** when supported, else **EDOF**, else **continuous video / picture**; tap-to-focus still overrides with CAF. **`CommandDial`** + **`AboutScreen`** (“Command dial — Snap (street)”) tie Ricoh Snap Focus heritage to dial **S**.
- [x] [HOST] **Macro lock (live caps)** — **`HardwareCapsSnapshot.build`** fills **`HardwareCaps`** from **`CameraCharacteristics`** + **`BackCameraRoleResolver`** / **`LensInfoSummary.isMacroCapable`** (UW) + **`VendorKeyGuard`** for **`com.oplus.macro.closeup.enable`**; **`PreviewController.hardwareCaps()`** exposes it; **Developer menu** shows **`CapabilityGate.evaluate`** lines when camera permission is granted. ADB Super Macro probe (**`EXTRA_PNS_PREVIEW_SUPER_MACRO_PROBE`** + **`EXTRA_PNS_PREVIEW_CAMERA_ID`** → ultra-wide) applies the tag via **`SessionConfiguration.setSessionParameters`** (API 33+) when possible, else repeating **`CaptureRequest`** (**`VendorKeyGuard`** session/request **`trySet`** + legacy probes — see §5).
- [x] [MIXED] Super Macro hardware lock — **[ADB]** Automated gate: **`scripts/pns_super_macro_gate.ps1`** (or **`pns_adb_preview_validate.ps1 -SuperMacroOnly`**) writes **`super_macro_gate.json`** / **`super_macro_gate.txt`** under the run folder; **`pass: true`** requires **`PNS.AdbValidation`** line **`superMacroCloseup probe`** with **`vendorKeyApplied=true`**. Optional **`-RequireSuperMacroPass`** exits non-zero on failure (CI/device automation). UW id defaults to **3** (**`-UltraWideCameraId`**). **Closed §5 — 2026-05-10:** device **`8bf09993`**; **`hfr-runs/adb_preview_validate_20260510_061124/`** (**`super_macro_gate.json`** **`pass: true`**; matched line **`vendorKeyApplied=true`** **`path=sessionParameters`** **`type=byteArr1`**).

### Sprint 5.4 — Gallery-aligned chrome (**storage + preview UX**)

- [x] [HOST] **DCIM destination:** still and video MediaStore `RELATIVE_PATH` roots under `DCIM/Point & Shoot/` (per-profile subfolders unchanged) so indexed media appears alongside typical camera-roll folders — see `CaptureStorage`, `STORAGE_STRATEGY.md`.
- [x] [HOST] **Last-capture thumbnail + viewer:** after each successful still/bracket write, show a small thumb in the bottom tray; tap launches an implicit `ACTION_VIEW` on the `content://` URI so the **system resolver** offers viewers with **Just once / Always** (avoid `Intent.createChooser`, which hides **Always**).
- [x] [HOST] **Bottom tray shutter:** keep left/right rails as-is; move the orange shutter into a full-width bottom tray with the FAB **horizontally centered**; toggle in **Preview & keys** still applies.
- [x] [HOST] **Static preview rotation default:** `staticPreviewRotationDeg` defaults to **270°** so a fixed-window viewfinder matches reality when the buffer appeared **90° CW** off (users can cycle **Spin (preview)** as before).

### Sprint 5.5 — Orientation-unlocked HUD (**preview aspect + chrome**)

- [x] [HOST] **Activity orientation:** launcher activity uses **`sensor`** (not landscape-only); quick-settings chrome counter-rotates per rail controls while the preview stays fixed (`staticPreviewRotationDeg` only).
- [x] [HOST] **Preview fill + uniform scale:** `PreviewMainViewport` sizes the inner TextureView with **`TexturePreviewFit.smallestCoveringAxisAlignedRectWithAspect`** (same aspect as the stream, **cover** the finder) and **clips** overflow so left/right pillarbars do not appear; `TextureView` + `computeCenterFitMatrix` stay **uniform** (no horizontal stretch). See **Preview finder acceptance** table above for device proof obligations.
- [x] [HOST] **Portrait shutter:** bottom-tray FAB anchored **bottom-center** in portrait (above nav inset).
- [x] [HOST] **Orientation probe:** diagnostic panel lives under **Developer menu** (`OrientationProbeBridge` + `OrientationProbeOverlay`), not over the live preview.

**Milestone 5 gate:** Toolchain PASSED; Phase 2 [ADB] rows `[x]` with §5 evidence; long-run **optional** `- [ADB] 15-minute session` may complete here or in Milestone 7 (stress). **Note:** Sprint **5.1** open **`[ ]`** row (hardware JPEG HUD) is paired with Milestone **4** Sprint **4.5** engine work — close both when shipping that wave.

---

## Milestone 6 — Color, calibration & LUT pipeline

**Objective:** Color science modules, calibration UX, LUT catalog; device validates perf and capture-time behavior.

**Kickoff (2026-05-10):** Work **Sprint 6.1 → 6.2 → 6.3** in order. Host-first items run under `pns_verify_toolchain.ps1 -RunTests`; device **[ADB]** / **[MIXED]** rows need §5 artifacts when closed.

**Platform note:** `android.hardware.camera2.DngCreator` exposes **`setDescription`** / **`setOrientation`** / **`setLocation`** / thumbnails only — **no public API for DNG tag 50708 (`UniqueCameraModel`)**. Host workaround: [`TiffUniqueCameraModel50708`](app/src/main/java/dev/pointandshoot/TiffUniqueCameraModel50708.kt) appends IFD0 tag **50708** after `writeImage`; [`Dng12Saver`](app/src/main/java/dev/pointandshoot/Dng12Saver.kt) integrates it when **`uniqueCameraModel`** is supplied (full-file RAM buffer). LUT identity remains on **`setDescription`** per `COLOR_PIPELINE.md`; **`UniqueCameraModel`** carries device + `cameraId` via [`DngLutMetadata.formatUniqueCameraModel`](app/src/main/java/dev/pointandshoot/DngLutMetadata.kt).

### Sprint 6.1 — Host color/LUT foundation

- [x] [HOST] **ACES / OCIO asset pipeline + spi3d** — Gradle **`bundledLutSpecs`** pins **`colour-science/OpenColorIO-Configs`** @ **`3af87f1d…`** (`aces-rrt-v011-srgb.spi3d`, `alexa-logc-video-nuke1d.cube`); **`:downloadBundledLuts`** + **`preBuild`**; **`LICENSES.md`** + **`pns_license_inventory.ps1`** walker. Filmic Blender upstream has no compact bundled cube in-repo — documented as follow-up (see **`LICENSES.md`**).
- [x] [HOST] **DNG `UniqueCameraModel` / tag 50708** — [`TiffUniqueCameraModel50708`](app/src/main/java/dev/pointandshoot/TiffUniqueCameraModel50708.kt) LE TIFF IFD0 append + [`Dng12Saver`](app/src/main/java/dev/pointandshoot/Dng12Saver.kt) + preview RAW path (`PreviewEngineScreen`); JVM tests **`TiffUniqueCameraModel50708Test`**; **`PNS.AdbValidation`** log **`50708 IFD append ok`** when stamp succeeds (see `scripts/pns_adb_preview_validate.ps1` **`summary_grep`**).
- [x] [HOST] Majority of ISOBMFF/AVIF/JXL host modules, `LutPipeline`, calibration math — already landed (see Appendix B).

### Sprint 6.2 — Calibration (**device + chart**)

- [x] [MIXED] End-to-end **Calibrate** from live preview (`Preview & keys` → **Calibrate from preview** → `TextureView.getBitmap()` → same Compute/Save pipeline as SAF).
- [x] [MIXED] **ADB Calibrate smoke** — **`pns_adb_preview_validate.ps1 -Milestone6Pack`** / **`pns_milestone6_gate.ps1`**: **`m6_calibrate_smoke`** (**`calibrateSmoke`**); **`m6_preview_calibrate_grab_smoke`** (**`calibrateLiveGrabOk`**). Evidence: **`milestone6_gate.json`** **`pass: true`** + **`PROBE_BUILD_PLAN.md` Section 5** row (**2026-05-10**, OnePlus **CPH2655** / USB **`8bf09993`**).
- [ ] [HUMAN] Real-world chart metrics (dE2000, MTF50) — physical chart session — **Milestone H.2** (not a Sprint 6.2 deliverable).

### Sprint 6.3 — LUT V&V (device)

- [x] [ADB] Live-preview LUT toggle **FPS budget** (≤5% drop on 60fps path) — **`pns_preview_m6_fps_lut_probe`** → **`m6 lutFpsBaseline` / `m6 lutFpsWithLut` / `m6 lutFpsBudget`**; **`milestone6_gate.json`** **`lutFpsBudgetOk`**. Scenario **`m6_lut_fps_probe`** in **`-Milestone6Pack`**. Evidence: **`PROBE_BUILD_PLAN.md` §5 — 2026-05-10** (**`milestone6_gate.json`** under **`hfr-runs/adb_preview_validate_milestone6_latest/`**, device **`8bf09993`**).
- **Follow-ups (encoding / export backlog — not Sprint 6.3 gates):** Video + still LUT at encode time + sidecars (`STORAGE_STRATEGY.md`); imported `.cube` byte-identical round-trip on device — track under **`COLOR_PIPELINE.md`** / Milestone 7+ until hooks land.

**Milestone 6 gate:** Host `pns_verify_toolchain.ps1 -RunTests` PASSED; device **`scripts/pns_milestone6_gate.ps1`** (or **`pns_adb_preview_validate.ps1 -Milestone6Pack`**) with **`scripts/pns_adb_device.env`** → **`milestone6_gate.json`** **`pass: true`**; append **`PROBE_BUILD_PLAN.md` §5** when a physical device is used. Sprint **6.3** LUT FPS **[ADB]** row is closed with §5 evidence above; encoding/export backlog bullets do not block this gate.

---

## Milestone 7 — Robustness, performance & storage

**Objective:** Failure matrix, profiling, backpressure, storage validation, optional root enhancements.

### Sprint 7.1 — Performance & profiling

- [x] [MIXED] **Perfetto** trace baseline (light) per **`PERFORMANCE_BUDGETS.md`** § *Perfetto & frame jank* — **`scripts/pns_capture_perfetto_light.ps1`** pulls **`perf-runs/perfetto_*_serial-<adb>.perfetto-trace`** (device **`/system/bin/perfetto`** light mode: **gfx** / **view** / **sched** + **`-a dev.pointandshoot`**; on **CPH2655** the write path required **`adb root`**). **§5** evidence **2026-05-11**; paired **`-PerfReport`** markdown in the same slice. Deeper **SurfaceFlinger** / **GPU** pbtxt configs remain optional backlog if light traces are insufficient for a given regression.
- [x] [ADB] **`dumpsys gfxinfo … framestats`** — **`python scripts/pns_capture_gfxinfo_baseline.py`** (**`--serial`** or **`scripts/pns_adb_device.env`**); **`perf-runs/gfxinfo_*_serial-<adb>.txt`**. First fleet file: **`perf-runs/gfxinfo_20260510_232327_serial-8bf09993.txt`** (OnePlus **CPH2655**); headline numbers in **`PROBE_BUILD_PLAN.md` §5** (2026-05-11).
- [x] [HOST] `pns_hfr_autorun.ps1 -PerfReport` — **`perf-runs/perf_*.md`**: `am start -W` vs 800 ms, `dumpsys meminfo` PSS vs 180 MB, `PNS.Reader` drop tail; **`-Serial`** / **`pns_adb_device.env`**. Full protocol: **`PERFORMANCE_BUDGETS.md`** (**Android Studio**, desktop **`perfetto`**, **`pns_capture_perfetto_light.ps1`**, **`pns_capture_gfxinfo_baseline.py`**).

### Sprint 7.2 — Failure matrix (ADB)

- [x] [HOST] **`scripts/pns_failure_matrix_smoke.ps1`** — automated smoke: **`fm_preview_granted`** + **`fm_preview_revoked`** (CAMERA revoked then cold-start preview); **`failure_matrix_smoke.json`** (**`schema`**: **`pns.failure_matrix_smoke.v1`**) asserts no **`FATAL EXCEPTION` / `Process: dev.pointandshoot`** block in captured logcat. **`ERROR_CAMERA_IN_USE`**, orientation torture, thermal long-run — **manual / stretch** (documented in **`FAILURE_MATRIX.md`**); append **§5** with **`scripts/pns_probe_append_section5.ps1 -GateJson …/failure_matrix_smoke.json`** when a device run records **`pass: true`**.

### Sprint 7.3 — Pipeline & storage

- [x] [HOST] BKT encode-lane preflight — wait up to **`PerfBudget.Defaults.ENCODE_LANE_DRAIN_WAIT_MS`** for `PNS.Reader` / `ioExecutor` to drain + best-effort RAW/JPEG **`ImageReader`** discard before sequential bracket; timeout → **`PNS.AdbValidation`** **`encode_lane_busy`** + Toast **"Engine busy - retry"** (`PreviewEngineScreen`, **`CAPTURE_ARCHITECTURE.md`**, **`PERFORMANCE_BUDGETS.md`** bracket table).
- [x] [HOST] **`scripts/pns_analyze_reader_backpressure.ps1`** — classifies **`PNS.Reader`** **`drop oldest`** lines (**`queue=`** / **`channel=`**) and tallies **`encode_lane_busy`** / encode-lane drain timeouts from plain logcat text. **`-LogDir`** walks **`logcat_*.txt`** recursively and skips sibling **`*_app_pid.txt`** (avoids double-counting the same lines vs full ring captures). **`-OutFile`** emits Markdown (e.g. sample **`perf-runs/reader_backpressure_smoke_20260511_030304.md`** over **`hfr-runs/automation_smoke_20260511_030304/adb_preview_full_validate`**).
- [x] [MIXED] Backpressure / queue bounds — **`CAPTURE_ARCHITECTURE.md`** Sprint **7.3 acceptance gates** (`raw_still_x10` + `bracket_bkt3` logs) vs **`PERFORMANCE_BUDGETS.md`** bracket table; evidence **`perf-runs/reader_backpressure_validate_raw_and_bkt3.md`** (**`pns_analyze_reader_backpressure.ps1`** on **`hfr-runs/automation_smoke_20260511_030304/adb_preview_full_validate`**) + **`PROBE_BUILD_PLAN.md` §5** **2026-05-11** (all gate counts **0**). Longer / adversarial bursts remain optional follow-up.
- [x] [ADB] **`encode_lane_busy`** not observed on **BKT3** full **`pns_adb_preview_validate`** run (**`hfr-runs/adb_preview_validate_20260511_005819/`**): **`summary_grep.txt`** `encode_lane_busy` section has **no log hits**; **`logcat_bracket_bkt3.txt`** includes **`PNS.AdbValidation`** **`captureBracketBurst pattern=Three ok=true`** (device **8bf09993** / **CPH2655**).
- [x] [ADB] **DCIM / mediastore_probe.json** — **`pns_adb_preview_validate.ps1`** `Write-MediaStorePnsProbe`: **ampersand-safe** `adb shell` (`ls -la '/sdcard/DCIM/Point & Shoot/'` + **`Ultra-Max/`** + **`find`** `pns_*.{dng,jxl,avif}`); **`dcimHasPnsCapture`** now reflects real **`pns_*.dng`** on disk (fix validated **CPH2655** / **`8bf09993`**: **`hfr-runs/mediastore_probe_retest_20260511/mediastore_probe.json`** **`dcimHasPnsCapture=true`**). **`mediaTailPnsRows`** may stay **0** on some OEMs (tail schema); JSON adds **`mediaTailPnsDisplayNameHits`** for **`pns_<UTC>_`** in the tail when present.
- [x] [MIXED] **Gallery / desktop open** — **moved to Milestone 10 Sprint 10.16** + **Milestone H.1** (single sign-off path).

### Sprint 7.4 — Feature gating UX

- [x] [HOST] **`CapabilityGate`** fed by **`HardwareCapsSnapshot`** / **`HardwareCaps`**; **`CapabilityGateBridge`** formats gate lines; **Developer menu** (probe) + **Settings > HUD** show per-feature **`ok` / `off`** with truncated disabled reasons when camera permission is granted (HUD shows permission hint when denied).

### Sprint 7.5 — Root-only enhancements (optional fleet)

- [x] [ADB] **`scripts/pns_root_capability_adb.ps1`** — USB adb **`adb root`** transport: **`adb shell id`** → **`uid=0(root)`**; writes **`root_capability_adb.json`** (**`schema`**: **`pns.root_capability_adb.v1`**). **Note:** **`adb shell su -c id`** may still fail on builds where only **adbd** is rooted (no Magisk **`/system/bin/su`** on PATH). §5 append via **`pns_probe_append_section5.ps1`**.
- [x] [ROOT] **`RootPrivilegedDiagnostics`** — read-only **`su -c`** suite (vendor **`getprop`** reads, CPU governor / thermal sysfs **`cat`**, short **`logcat`** tail, **`dumpsys media.camera`** head, resolution **`getprop`**, backlight sysfs **`cat`**); **`RootSettingsScreen`** manual **Read-only SU checks** + cold-start **`--ez pns_auto_root_diagnostics true`** with **`pns_screen=rootsettings`** after **Granted** persists; **`scripts/pns_root_privileged_smoke.ps1`** (log **`rootPrivScan suite=read_only_done`**). **Destructive** catalog actions (**`setprop` writes**, **`ctl.restart` cameraserver**, governor **writes**, fleet governor pin) remain **explicit confirmation / not shipped** — §5 device row when smoke **`pass: true`**.

**Milestone 7 gate:** Toolchain PASSED; failure-matrix rows closed or explicitly waived with issue links; **storage** ADB checks (**`encode_lane_busy`**, reader backpressure gates, **DCIM `mediastore_probe`**) recorded; **ADB root transport** probe (**`pns_root_capability_adb.ps1`**) §5 when fleet uses **`adb root`**; optional **in-app** read-only root suite (**`pns_root_privileged_smoke.ps1`** when **Granted**); gallery/desktop opens remain **Milestone H** where applicable.

---

### Performance & responsiveness backlog (2026 audit) — closed

Work in this order where possible; device-verify preview teardown + H-mode metering after behavioral changes.

| # | Item | Status | Notes |
|---|------|--------|--------|
| 1 | GL preview **dispose**: remove main-thread `Thread.sleep` / blocking yields | `[x]` | Teardown no longer sleeps on the UI thread in `DisposableEffect` (`PreviewEngineScreen`). |
| 2 | **YUV meter lane**: skip ML Kit when Camera2 `STATISTICS_FACES` already feeds overlay | `[x]` | Gated via `faceHudLastCameraFaceBoxes` when Camera2 face HUD is active. |
| 3 | **Adaptive ML cadence**: slower interval after consecutive empty ML detections | `[x]` | Empty-run backoff (`mlFaceEmptyBackoffAfterFrames` / interval ms) in YUV lane. |
| 4 | **Highlight (H) priority**: run histogram / highlight metering **before** ML when dial is H | `[x]` | `prioritizeHMetering` runs histogram/highlight before ML when dial is H and those lanes are on. |
| 5 | **`PNS.AdbValidation` logging**: gate behind debuggable / `DiagnosticsMode` (`PnsAdbLog`) | `[x]` | `PnsAdbLog` (+ `w`/`e`); preview/capture/DNG/calibrate/GL preview call sites migrated. Script still greps tag `PNS.AdbValidation`. |
| 6 | **Compose**: reduce invalidation (`derivedStateOf`, stable child params) where profiling shows cost | `[x]` | Stable **`cameraIdsList`** instance when the roster string is unchanged (`PreviewEngineScreen` → `PreviewEngineContent`); further `derivedStateOf` only where profiling demands it. |
| 7 | **Startup**: baseline profile artifact merge + **ProfileInstaller** path verified on device | `[x]` | `:app:generateBaselineProfile` + `app/src/release/generated/baselineProfiles/{baseline,startup}-prof.txt`; **`profileable`** manifest; cold-start **`am start -W`** sample in **`perf-runs/perf_cold_start_baseline_20260511.md`** (Macrobenchmark **1.4.0** fixed OEM launch confirmation vs **1.3.3**). |

---

## Milestone 8 — CI/CD & signed builds (automation-ready)

**Objective:** Repeatable release binaries and optional GitLab automation **without** storing secrets in git.

### Sprint 8.1 — GitHub Actions

- [x] [HOST] `toolchain-verify.yml`, `build-signed.yml`, Gradle signing **inputs** via env / `keystore.properties` (gitignored).

### Sprint 8.2 — GitLab (YAML only)

- [x] [HOST] `.gitlab-ci.yml` template present (`toolchain-verify` job).
- [ ] [CI] Healthy pipeline on a connected mirror (**depends on Milestone H** for mirror + secrets).

**Milestone 8 gate:** `pns_verify_toolchain.ps1 -RunTests` PASSED; `:app:assembleRelease` with debug-key fallback passes locally / CI; signing secrets **not** required for this gate.

---

## Milestone 9 — Finder & operator chrome (**ADB automation; no human gate**)

**Objective:** Machine-verified operator UX from the UI roadmap: wide/M23 preview seed, **`PNS.ChromeUx`** log hooks for scripted gates, and an aggregate gate script. Expand sprints as the readout bar, icon grid, dual shutters, and DND land.

**Living doc:** `.cursor/plans/ui_roadmap_build_plan_73a866c1.plan.md` (full UX intent + Sprint 9.x backlog).

### Sprint 9.1 — Host + FOSS baseline

- [x] [HOST] **`PickCameraIdFromM23ResolveTest`** + **`pickCameraIdFromM23Resolve`** — deterministic wide-vs-first-id selection ([`BackCameraRoleResolver.kt`](app/src/main/java/dev/pointandshoot/BackCameraRoleResolver.kt)).
- [x] [HOST] **`scripts/pns_verify_toolchain.ps1`** lists **`pns_chrome_ux_gate.ps1`** (UTF-8 + parse check).

### Sprint 9.2 — Preview seed & ChromeUx log (ADB)

- [x] [ADB] Cold-start preview seeds **`resolveFocalMmSlot(M23)`** wide id; logs **`PNS.ChromeUx`** **`seedOk slot=M23 cameraId=…`** on success ([`PreviewEngineScreen.kt`](app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt)).

### Sprint 9.3 — Chrome UX gate script

- [x] [HOST] **`scripts/pns_chrome_ux_gate.ps1`** — runs **`pns_verify_toolchain.ps1 -RunTests`** (unless **`-SkipHost`** / **`-SkipHostTests`**), optional **`assembleDebug`**, installs APK when a device is connected, cold-starts **`MainActivity`** with **`pns_screen=preview`**, captures logcat, asserts **`PNS.ChromeUx`** **`seedOk slot=M23`** **and** **`safeInsetsTopPx=`** (merged bars + cutout log); writes **`chrome_ux_gate.json`** (**`safeInsetsOk`**, schema **`pns.chrome_ux_gate.v1`**). Without an authorized device: **`pass`** follows **host-only** success (device checks skipped). Parameters: **`-SkipInstall`**, **`-SkipGradle`**. §5 append: **`scripts/pns_probe_append_section5.ps1 -GateJson …/chrome_ux_gate.json`** (same script as Milestone 6 / MediaStore / Super Macro / **7.2** **`failure_matrix_smoke.json`** gates).

### Sprint 9.4 — Safe area / cutout (host + ADB)

- [x] [HOST] **`MainActivity`** calls **`enableEdgeToEdge()`** and hides status + navigation bars via **`WindowInsetsControllerCompat`** (**`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`**) so the window uses the **full physical display**; bars return transiently on edge swipe. Re-applied in **`onWindowFocusChanged`** after transient reveal.
- [x] [HOST] **`rememberSystemInsetsDp`** merges **`systemBars` ∪ `displayCutout`** (API 30+ union; API 28–29 max per edge) so **`PaddingValues`** clear punch-hole / nav gestures when those insets are non-zero ([`SystemInsets.kt`](app/src/main/java/dev/pointandshoot/SystemInsets.kt)).
- [x] [ADB] **`PNS.ChromeUx`** logs **`safeInsetsTopPx=… mergedBarsCutout=true`** once inset top is known ([`PreviewEngineScreen.kt`](app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt)).

### Sprint 9.5 — DND in preview (host + ADB)

- [x] [HOST] **`InterruptionFilterHold`** ref-count in **`PreviewWindowEffects.kt`** so **DND while recording** and **DND in preview** nest without clobbering the saved filter.
- [x] [HOST] **`PreviewForegroundDndEffect`** + pref **`dndWhileInPreview`** (default on) + **Preview & keys** toggle; logs **`PNS.ChromeUx`** **`dndPreview=applied|skipped_no_policy|skipped_disabled|…`**.
- [x] [HOST] **`pns_chrome_ux_gate.ps1`** — device JSON field **`dndPreviewOk`** (log line present).

### Sprint 9.6 — Exposure readout strip (host + ADB)

- [x] [HOST] **`PreviewReadoutStrip`** + **`PreviewReadoutFormat`** — ISO / shutter / AWB / measured FPS; counter-rotates with **`uiRotationDeg`**; repeating-request metadata from **`PreviewController`** (**`SENSOR_*`**, **`CONTROL_AWB_MODE`**).
- [x] [HOST] **`PNS.ChromeUx`** **`readout=live`** (first metadata frame) or **`readout=fallback`** (~10s if OEM omits keys); **`pns_chrome_ux_gate.ps1`** field **`readoutOk`**.
- [x] [HOST] **`PreviewReadoutFormatTest`**.

### Sprint 9.7 — Dual shutters (photo / video)

- [x] [HOST] **`PreviewBottomCaptureTray`** — **`PnsColors.PhotoOrange`** still + **`PnsColors.RecordRed`** video; inactive mode smaller (**52.dp**) + **`alpha=0.38`** left; tap inactive swaps primary (**`rememberSaveable`**); center video toggles **`isRecording`**; returning to photo stops recording if active; **`PNS.ChromeUx`** **`dualShutter=visible`** when on-screen shutter enabled.
- [x] [HOST] **`pns_chrome_ux_gate.ps1`** — **`dualShutterOk`**.

### Sprint 9.8 — Chrome quick grid + Settings expand tile (**Settings at r3c6 since Sprint 10.13**)

- [x] [HOST] **`previewChromeGridSlots`** — focal mm row is separate above the scroll grid; **shortcut rows** (see Sprint **9.9** shipped layout): expand shortcuts + quick actions + **Settings** **`ExpandShortcut`** at **`(row=3,col=6)`** (`settingsAt=r3c6` in **`PNS.ChromeUx`**; previously **`(row=2,col=6)`**).
- [x] [HOST] **`PNS.ChromeUx`** **`grid7x3=layout shortcutRows=3 settingsAt=r3c6=true`** (+ **`quickActions=…`** list; legacy logs may show **`grid7=layout settingsAt=r2c6`**); **`PreviewReadoutStrip`** uses **`TransformOrigin(0.5f,0.5f)`** with **`uiRotationDeg`** (matches grid rotation pivot).
- [x] [HOST] **`pns_chrome_ux_gate.ps1`** — **`grid7Ok`** (matches **`grid7x3=layout`** or legacy **`grid7=layout`**).

### Sprint 9.9 — Grid quick actions (shipped layout)

- [x] [HOST] **`ChromeGridSlotSpec`** — **`ExpandShortcut`** vs **`QuickAction`**; **row 1** (logical): **Guides**, **Preview & keys**, **Capture & tools** (expand), **Self timer**, **Histogram**, **Horizon level**, **Eye AF overlay**; **row 2**: **Video tally**, **Max brightness**, **DND in preview**, **Extra shutters** (merged tap + volume), **Flash mode**, **Save location in files**; **row 3**: **Settings** (expand) at **col 6** (Sprint **10.13**). Source: **`previewChromeGridSlots`** in **`PreviewEngineScreen.kt`**.
- [x] [HOST] **`PNS.ChromeUx`** — **`quickActions=timer,histogram,horizon,eyeAf,tally,bright,dnd,extraShutter,flash,saveLoc`** (see **`LaunchedEffect`** log in **`PreviewEngineScreen`**).

### Sprint 9.10 — Shooting-mode menu + **`RAW`/`RAW+`** readout badge

- [x] [HOST] When **`HudSettings.showCommandDial`**: bottom tray **`PreviewBottomCaptureTray`** shows a **48.dp** orange **FAB** with the current **`CommandDialMode.label`**; tap opens **`DropdownMenu`** for **M/H/S/BKT**; **`PNS.ChromeUx`** **`modeDialPopout=menuSelect`** on pick (legacy **`anchorVisible`/`expanded`/`skipped_no_dial`** may still appear from older HUD paths — gate accepts **`menuSelect`**).
- [x] [HOST] **`PreviewController.previewUsesJpegCompanion()`** (JPEG **`ImageReader`** active); readout strip pipeline chip + **`readoutCapture=`** **`PNS.ChromeUx`** line via **`PreviewReadoutStillPipeline`** (**`DNG`** / **`DNG+`** / **`DNG12`** / **`JPEG`**; gate **`pns_chrome_ux_gate.ps1`** accepts legacy **`RAW`/`RAW+`** too).
- [x] [HOST] **`pns_chrome_ux_gate.ps1`** — **`modeDialPopoutOk`**, **`readoutCaptureOk`**.

### Sprint 9.11 — Self-timer (pref + grid + still paths)

- [x] [HOST] **`PreviewChromePreferences.selfTimerDelaySec`** (**0 / 3 / 5 / 10**), persisted; grid **Timer** icon cycles delay + toast + **`PNS.ChromeUx`** **`selfTimerSec=`**; icon **selected** when delay **> 0**.
- [x] [HOST] Still capture via volume-up (non-BKT), tap-to-shoot, bottom orange shutter, **Save DNG**: **`triggerStillCapture()`** — countdown overlay on finder, then existing **`onCaptureDng`** (**bracket / BKT** unchanged).
- [x] [ADB] **`--ei pns_preview_self_timer_sec`** (`EXTRA_PNS_PREVIEW_SELF_TIMER_SEC`) seeds **`PreviewChromePreferences.selfTimerDelaySec`** before **`PNS.ChromeUx`** **`selfTimerSec=`**; **`pns_chrome_ux_gate.ps1`** defaults **`-SelfTimerSec 3`** on device **`am start`** (allowed **0 / 3 / 5 / 10**).
- [x] [ADB] **`pns_adb_preview_validate.ps1 -ChromeUxPack`** — short **`m9_self_timer_adb_seed`** scenario + **`chrome_ux_smoke.json`** (**`selfTimerChromeUxOk`**, **`adbSelfTimerSeedOk`**); logcat tag filter includes **`PNS.ChromeUx`**.
- [x] [HOST] **`pns_chrome_ux_gate.ps1`** — **`selfTimerOk`** (**`selfTimerSec=`** on cold start).

### Sprint 9.12 — Flash mode + quick-settings slot (**chrome**)

**Objective:** Real still/preview **flash** policy (not a stub), a **single dedicated Flash quick-setting** tile on the **7×3** quick grid, and **one fewer** binary quick tile by merging two related toggles.

**Chrome / layout:** Obey **`.cursor/rules/preview-chrome-ui-lock.mdc`** — reuse existing tile geometry and chip styling; **no** finder flex / rail weight / grid spacing experiments unless the user explicitly unlocks the style guide.

- [x] [HOST] **Merge two quick settings into one** — **Extra shutters** tile + popup (**tap** + **volume** toggles); **`CHANGELOG.md`** / rail sheets aligned.
- [x] [HOST] **Flash quick-setting (QS) tile** — **`CycleFlash`** + **`PreviewFlashMode`** + **`PreviewFlashPolicy`**.
- [x] [MIXED] **Camera2 flash wiring** — Preview repeating + still **`FLASH_MODE`** / AE modes (bracket stills force flash off); front / no-flash handled in **`PreviewFlashPolicy`**.
- [x] [HOST] **`PNS.ChromeUx`** — **`extraShutter`**, **`flash`** tokens in `quickActions=` log.
- [x] [ADB] **`pns_adb_preview_validate.ps1 -ChromeUxPack`** (or gate script) — optional scenario line / JSON field proving **`flash`** QS appears and **`PNS.ChromeUx`** logs expected tail after cold start (device without flash still logs **degraded** / **skipped_no_flash** — document).

**Note:** `chrome_ux_smoke.json` asserts **`quickActions=…flash…`** on the **`grid7x3=`** (or legacy **`grid7=`**) **`PNS.ChromeUx`** line and **`flashPreviewHardware=true|false`** once per preview session (hardware absent → `false`). Self-timer **`selfTimerSec=N`** log uses the **normalized** ADB seed value immediately (not a stale prefs read). Evidence: **`PROBE_BUILD_PLAN.md` §5 — 2026-05-12** (`hfr-runs/adb_preview_validate_20260512_022021/chrome_ux_smoke.json`).

**Follow-on (not Sprint 9.12):** zebras GLSL; remaining Sprint **4.4** Camera2 items.

### Sprint 9.13 — Preview finder acceptance (device proof)

**Policy:** Any change to `PreviewMainViewport`, `TexturePreviewFit`, `effectivePreviewStaticRotationDeg`, `BackCameraRoleResolver`, or the **7-slot focal row** must satisfy this sprint **and** *How agents must execute* **§6** (build → sideload → `pns_device_screencap` → **`PROBE_BUILD_PLAN.md`** §5).

#### Pass criteria table (on-device)

| Item | Pass criterion (on-device) |
|------|---------------------------|
| **No side pillarbars** | In preview screen, live image **fills the finder width**; any crop is **top/bottom only** (center-crop), not black bars left/right from aspect-fit “contain”. |
| **No horizontal stretch** | Point the camera at a **square** calibration target (or square UI element); the square must stay **square** (uniform scale), not wider than tall. |
| **Preview locked on rotation** | Rotating the phone **does not** change static preview rotation automatically; only **Spin (preview)** changes buffer rotation. Finder does not jump between portrait/landscape. |
| **Tele focal presets** | With ≥3 rear cameras, tapping **73 / 85 / 150** selects the **tele** camera (check status line `cameraId=…` or mode-transition log); preview FOV changes. Resolution uses **BackCameraRoleResolver** (focal-length clustering), not hard-coded `"4"` only. |
| **Host regression** | `pns_verify_toolchain.ps1 -RunTests` exit 0; `TexturePreviewFitTest` + `PreviewLayoutOrientationTest` green. |

#### Screenshot verification queue (tick only with device PNG)

**Rule:** Do not change `- [ ]` to `- [x]` until **physical device** validation proves the item. Host rebuilds use Gradle logs, not this list.

**Host rebuild (2026-05-11):** `.\gradlew.bat :app:assembleDebug` + `:app:assembleRelease` + `:app:lintDebug` → **PASSED** (lint + detekt baselines committed).

- [x] **Immersive window** — Status + nav bars hidden (`enableEdgeToEdge` + `WindowInsetsControllerCompat`); transient swipe reveal only. **Evidence:** Sprint **9.4** host wiring + **`PNS.ChromeUx`** **`safeInsetsTopPx=`** in **`pns_chrome_ux_gate.ps1`** / **`chrome_ux_gate.json`**; any residual top/bottom **bands in finder captures** are **in-app chrome padding** (locked layout), not an unreleased immersive flag.
- [x] **Live preview** — Camera stream visible in finder. **Evidence:** adb device validation (2026-05-10); raster PNG not in repo.
- [x] **Readout strip** — ISO, shutter, AWB / FPS, **`RAW`** or **`RAW+`**. **Evidence:** same session.
- [x] **Right rail + focal row** — mm chips **`14…150`** with selection highlight. **Evidence:** same session.
- [x] **7×3 quick grid** — **Focal** row (7) + **three** logical shortcut rows (7 cols); **Settings** at **`r3c6`**; row **2** col **6** intentionally empty. **Evidence:** **`PNS.ChromeUx`** **`grid7x3=layout`** (same session as Milestone 9 chrome gate).
- [x] **Bottom tray** — Gallery thumb (when URI), dual shutters, mode letter FAB when HUD dial on. **Evidence:** same session.
- [x] **Expand shortcut → modal** — Row **1** expand tiles drive **`Dialog`**-hosted sheets in **`PreviewRightRail`** (not an under-grid strip). **Evidence:** **`PNS.ChromeUx`** **`expandShortcuts=surface=modalDialog host=PreviewRightRail`** (`pns_chrome_ux_gate.ps1` / **`-ChromeUxPack`**).
- [x] **Mode menu** — FAB opens **`DropdownMenu`** for **M/H/S/BKT** when HUD dial on. **Evidence:** **`modeDialPopout=`** line (**`menuSelect`** path) in **`chrome_ux_gate.json`** / **`pns_chrome_ux_gate.ps1`** (**`modeDialPopoutOk`**).
- [ ] **Finder — no side pillarboxing** — Live image fills finder width (center-crop top/bottom only). **Evidence:** _pending_ (requires human chart / screenshot sign-off per style-locked finder geometry).
- [ ] **Finder — uniform scale** — Square calibration target stays square. **Evidence:** _pending_ (human chart session).
- [ ] **Spin / chart upright** — Printed chart matches **DGK 8.5×11** legend vs gravity. **Evidence:** _pending_ (human chart session).
- [x] **Tele presets** — **73 / 85 / 150** mm chips route via **`resolveFocalMmSlot`** / **`BackCameraRoleResolver`**. **ADB proof:** **`--es pns_preview_focal_mm_slot N`** → **`PNS.ChromeUx`** **`focalSlotTap=mm=…`** (`pns_chrome_ux_gate.ps1` **`-FocalMmSlot`**, **`chrome_ux_gate.json`** **`teleFocalSlotOk`**; **`pns_adb_preview_validate.ps1 -ChromeUxPack`**).


**Milestone 9 gate (current):** `pns_verify_toolchain.ps1 -RunTests` PASSED; with device: **`scripts/pns_chrome_ux_gate.ps1`** exit 0 and **`chrome_ux_gate.json`** **`pass: true`** (includes **`expandModalHostOk`**, **`teleFocalSlotOk`** when **`-FocalMmSlot`** is set — default **`85`**); §5 row when a physical device is used. **Sprint 9.13** finder **geometry** rows (pillarboxing / uniform scale / chart upright) stay **human screenshot** until PNG evidence; **Sprint 9.12** closes with existing flash **`chrome_ux_smoke.json`** fields.

---

## Milestone 10 — completed sprints (10.1–10.13, 10.15, 10.16)

**Objective (full milestone):** Ship multi-device readiness, ordered capture/video/QR UX, and probe-driven quality **after** Milestone 9 chrome is stable. **Milestone 10 gate** (human sign-off row) remains in **[BUILD_PLAN.md](BUILD_PLAN.md)**. **Sprint 10.14** (OpenCamera-style toolbox) was **descoped** — see **Future features** in the active plan.

**Suggested execution order:** **10.1** → **10.2** → **10.3** → **10.4** → **10.5** (coordinate with **Milestone 4 Sprint 4.4** HDR row) → **10.6** → **10.7** → **10.8** → **10.9** → **10.10** → **10.11** → **10.12** → **10.13** → **10.15** → **10.16** (~~10.14~~ descoped).

### Sprint 10.1 — Probe export + shallow fleet cache (seconds budget; no session)

- [x] **[HOST]** **`CameraCapabilitiesProbe` stream map:** add **`RAW12`** and **`RAW10`** sections (sizes + min frame duration when non-empty), mirroring the existing **`RAW_SENSOR`** block.
- [x] **[HOST]** **Derived summary line per camera:** emit `rawPickEffective=RAW12|RAW10|RAW_SENSOR|null` + chosen **`Size`**, computed with the same logic as **`RawCaptureSupport.pickRawOutput`** (default tier **RAW12 → RAW_SENSOR → RAW10**; see **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** bisect **#2**) so **`PROBE_RESULTS`** matches preview still behavior.
- [x] **[HOST]** **HFR roll-up per camera:** single summary line or table row: e.g. **`hfrMaxFps`**, **`hfrMaxFpsAt1080`**, **`hfrMaxFpsAt720`** (from **`StreamConfigurationMap`** high-speed tables only — no session).
- [x] **[HOST]** **Doc touch:** **`README.md`** / **`DODGE_PROFILE.md`** one-liner that **canonical per-device truth** for RAW format + HFR max is **export** + **`hfr-runs`** JSON, not chat.
- [x] **[HOST]** **Spec `DeviceCameraCapabilityCache` (or equivalent)** — versioned schema (`schemaVersion`, `appVersionCode`, `androidSdk`, `Build.FINGERPRINT` or `SERIAL` hash): per `cameraId`: `lensFacing`, physical / logical hints, `LENS_INFO_AVAILABLE_FOCAL_LENGTHS`, zoom ranges, largest **JPEG** / **RAW** / **RAW12** from `StreamConfigurationMap` **without** opening a session; optional **high-speed** max FPS from `getHighSpeedVideoSizes` + `getHighSpeedVideoFpsRangesFor`. **Exclude:** session configuration queries, encoder smoke, exhaustive matrix, thermal.
- [x] **[HOST]** **Executor + wall-clock budget** — run scan on **`Dispatchers.Default`** / `cameraExecutor`; cooperative timeout (**2.5–4 s**); partial results + `degraded=true` when truncated.
- [x] **[MIXED]** **Persistence** — App-private `SharedPreferences` via [ShallowCapabilityCacheStore.kt](app/src/main/java/dev/pointandshoot/ShallowCapabilityCacheStore.kt): persists shallow JSON after each hub [buildProbeReport](app/src/main/java/dev/pointandshoot/CameraCapabilitiesProbe.kt); invalidates on **ROM fingerprint prefix** or **appVersionCode** change; **Settings → HUD → Rescan cameras (shallow cache)** bumps a seq so the hub effect re-runs (same-process listener + reopen hub). Optional **§5** row when closing on reference USB.
- [x] **[MIXED]** **Developer parity** — debug hub line: last shallow scan ms, cameras=N, degraded=… (**engineering hub** shows line after shallow scan); **Settings → Rescan** + on-disk shallow JSON persistence (**[ShallowCapabilityCacheStore](app/src/main/java/dev/pointandshoot/ShallowCapabilityCacheStore.kt)**).
- [x] **[HOST]** **ADB shallow hub gate** — **`scripts/pns_shallow_scan_hub_validate.ps1`** asserts **`PNS.ProbeHub`** + **`PNS.Probe`** **`Probe built`** after **`pns_screen=probehub`** cold start; wired into **`pns_automation_smoke.ps1`** (opt-out **`-SkipShallowScanHubValidate`**); **`pns_probe_append_section5.ps1`** schema **`pns.shallow_scan_hub_validate.v1`**.

**Sprint check:** `pns_verify_toolchain.ps1 -RunTests`; §5 note for cold-start **TotalTime** when closing **[MIXED]** device work.

### Sprint 10.2 — Focal equivalents, physical lenses, ≥12 MP policy

- [x] **[HOST]** **`FocalSlotAvailability` (pure + unit tests)** — 35 / 50 / 85 / 150 mm slots vs **≥12 MP**; gray unavailable; document formula in **`DODGE_PROFILE.md`**.
- [x] **[MIXED]** **Physical lens strip** — native equivalent mm per rear lens; tap baseline; crops layer when enabled.
- [x] **[MIXED]** **Front vs rear** — when front active, dim rear-only tele slots; persist last rear `cameraId`.
- [x] **[MIXED]** **Welcome / tutorial hook** — refresh focal UI from cache; readout “Calibrating focal map…” if scan lags (non-blocking shutter).

**Sprint check:** device screenshots gray vs active focal chips; §5.

### Sprint 10.3 — JPEG-only capture (alongside RAW / RAW+)

- [x] **[HOST]** **`ImagingProfile` / `CaptureStorage`** — **`jpeg_only`** path: no RAW `ImageReader`; hardware JPEG still via **`CaptureKind.JpegSdr`**; folder under **`DCIM/Point & Shoot/`**; **`PreviewStillCaptureHints`** orientation/GPS on JPEG stills.
- [x] **[MIXED]** **HUD / readout** — strip shows **`JPEG`**; document in **`AboutScreen`**.
- [x] **[MIXED]** **`CapabilityGate`** — no RAW → JPEG-only default + explanation (auto-fallback in **`PreviewEngineScreen`**; **`Feature.RawDng`** disabled reason points at JPEG-only profile).
- [x] **[MIXED]** **ADB** — **`pns_adb_preview_validate.ps1`** scenario **`jpeg_only_x1`** (**`captureJpegHardwareStill 1/1 ok=true`** on adb **8bf09993**, **`hfr-runs/adb_preview_validate_20260513_123424`**); **`m10_build_plan_host_hooks.json`** hdr seed + **`previewSessionDynamicRange profile=`**.

**Sprint check:** toolchain + validate log **`jpeg_only ok=true`** + §5.

### Sprint 10.4 — Front camera + first-run coach (gesture-safe)

- [x] **[MIXED]** **Front `cameraId`** + **`PreviewController`** / **`PreviewFlashPolicy`** front path.
- [x] **[MIXED]** **Swipe up → front, swipe down → rear** — velocity/distance; exclude tray/rails; **tap fallback** + **`WelcomePermissionsScreen`** copy (edge-gesture conflict note).
- [x] **[HOST]** **Tutorial copy** — gesture + fallback; **Settings → Replay tips** (**`WelcomePermissionsScreen`** + **`HudSettingsScreen`** “Replay welcome tips” + **`WelcomePrefs`** flow bump).
- [x] **[MIXED]** **Spotlight (≤3 steps)** — swipe, Photo|Video when tray ships, mode dial; Skip/Got it; prefs with **`PnsUiHintsStore`** family + backup allow-list if new keys.
- [x] **[MIXED]** **Accessibility** — TalkBack front/rear; not gesture-only.

**Sprint check:** UI gate §6 + **`pns_chrome_ux_gate.ps1`** if readout tokens change; §5 PNG.

### Sprint 10.5 — Runtime policy from probes (coordinate with M4.4)

- [x] **[MIXED]** **Per-lens HFR ceiling** — FPS picker / encoder labels from per-`cameraId` high-speed tables; **`EncoderResultAggregator`** / **`AboutScreen`** alignment.
- [x] **[MIXED]** **RAW depth honesty** — strict Ultra-Max vs lenient + HUD format line; document in **`DODGE_PROFILE.md`** (**`PreviewReadoutStillPipeline`**: **DNG12** vs **DNG** / **DNG+** vs **JPEG**; **`readoutCapture=`** log aligned).
- [x] **[MIXED]** **HDR / 10-bit preview session** — **`OutputConfiguration.setDynamicRangeProfile`** when **`isMultiOutputSessionSupportedWithDynamicRangeOnPreview`** passes (**`SessionConfigurationCompat`**, **`PreviewHdrSessionSupport`**). **Same deliverable as Milestone 4 Sprint 4.4** HDR / 10-bit live-preview row — both **`[x]`**.

**Sprint check:** §5 + `PNS.AdbValidation` / validate tail; Milestone **6** pack unchanged unless scenarios extended.

### Sprint 10.6 — Probe Phase C (user-visible, capability-gated)

- [x] **[MIXED]** HDR / wide-gamut preview **toggle** (after **10.5** stable).
- [x] **[MIXED]** Post-capture readout: **`rawBinningFactorUsed`**, DR profile name, RAW format (readout or debug rail only).
- [x] **[MIXED]** **“Max HFR for this lens”** preset.
- [x] **[MIXED]** **AF bracketing** (`EnableAFBracketing`) — research tier; matrix evidence before default-on.
- [x] **[MIXED]** **Vendor DCG / HDR keys** — Debug/Labs only; **`pns_autohdrdcg`**.

**Sprint check:** per feature small validate scenario or §5; **`FAILURE_MATRIX.md`** if user-visible failure.

### Sprint 10.7 — Probe Phase D (quality & parity)

- [x] **[MIXED]** **Face / eye HUD under HFR** — mapping vs **`TexturePreviewFit`** / tap focus; chart proof.
- [x] **[MIXED]** **Camera extensions inventory** — **`CameraExtensionSupport`** + probe export + **`pns_screen=cameraextsmoke`** smoke + **`CapabilityGate`** (**overlaps M4.4** — closed together).

**Sprint check:** §5; optional **`pns_compose_layout_trace_capture.ps1`**.

### Sprint 10.8 — Probe Phase E (fleet evidence)

- [x] **[MIXED]** **Reference fleet** — re-export **`PROBE_RESULTS`** / **`deep_caps`** on ≥2 extra device classes; diff RAW12 / HFR max / DR profiles.
- [x] **[HOST]** **Automation hooks** — **`pns_adb_preview_validate.ps1`** **`jpeg_only_x1`** + **`m10_hdr_preview_session_log`** + **`m10_build_plan_host_hooks.json`**; **`MainActivity`** **`PnsAdbValidation`** seed line for **`pns_preview_hdr10_live_preview`**; **`captureJpegHardwareStill`** grep in validate summary.
- [x] **[HOST]** **`DODGE_PROFILE.md` master table** — capability → app behavior → probe/script.

**Sprint check:** §5 (no secret serials in committed prose); `-SkipGradle` OK for doc-only.

### Sprint 10.9 — QR / barcode

- [x] **[MIXED]** **API & vendor inventory** — **`docs/camera2_reference_qr_barcode_appendix.md`** (stub) + link to **`docs/CAMERA2_KEYS_AND_APIS_REFERENCE.md`**; full vendor QR key survey still **device / fleet**.
- [x] **[MIXED]** **QR scan mode** — ML Kit or **`ImageAnalysis`**; optional **`pns_screen=qrscan`**; throttled YUV; stride-safe.

**Sprint check:** host doc + device smoke when UI lands.

### Sprint 10.10 — Bottom tray **Photo | Video** + video file + HFR color

- [x] **[HOST][MIXED]** **Photo | Video FAB + menus + single center shutter** — product spec: sibling FABs, **`CaptureMediaFamily`**, filtered menus, **`PreviewController`** session split, update **`PNS.ChromeUx`** / **`pns_chrome_ux_gate.ps1`** (supersedes dual-shutter-only story when shipped).
- [x] **[MIXED]** **Video recording to file** — **`MediaRecorder`** or Jetpack **`Recorder`** + **`MediaStore`** + audio policy; wire tray **`onRecordingChange`**; validate scenario.
- [x] **[MIXED]** **HFR preview discoloration** — diff **HFR vs 60 fps** **`CaptureRequest`** / tonemap / NR; **`COLOR_PIPELINE.md`** (**single owner** vs face/HFR geometry in **10.7**).

**Sprint check:** playable DCIM clip + §5; optional Perfetto.

### Sprint 10.11 — Face HUD polish + gallery strip

- [x] **[MIXED]** **Face rectangle hides when eyes detected** — [dispatchFaceHudOverlay](app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt): empty face-box list for overlay when eye marks are non-empty; face-priority metering still uses internal boxes.
- [x] **[MIXED]** **Gallery thumb always on** — [PreviewBottomCaptureTray](app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt): fixed-width gallery slot shows dim **Photo** icon when **`lastGalleryUri`** null; tap disabled until first capture. Partial media access docs remain backlog.

**Sprint check:** §6 if chrome text/layout outside locked slots changes; else §5 only.

### Sprint 10.12 — Flash / highlight program

- [x] **[MIXED]** **Highlight (H) — disable flash / torch** — **`PreviewFlashPolicy`** + tests + device LED check.

**Sprint check:** `PreviewFlashPolicyTest` + device note §5.

### Sprint 10.13 — Quick grid 7×3 (**maintainer unlock**)

- [x] **[MIXED]** **7×3 reslot** — **`previewChromeGridSlots`**, rename grid component / **`PNS.ChromeUx`** **`grid7x3=`** token, update gate + style guide + **`AGENTS.md`** + **`PROBE_BUILD_PLAN.md`**.

**Sprint check:** maintainer unlock + full **Sprint 9.13** finder evidence.

### Sprint 10.15 — UX polish residual (**chrome-safe**)

- [x] **[MIXED]** **Long-running capture progress** — indeterminate/stepped progress in existing modal/readout patterns (**no** new persistent chrome bands).
- [x] **[MIXED]** **Snackbar Retry / Copy raw error** — complete partial **`PnsUserFacingErrors`** follow-through.
- [x] **[MIXED]** **Flash tooltip / coach-mark prefs** — optional one-time long-press hint.
- [x] **[MIXED]** **Probe hub recents / favorites** — IA polish without preview-route relayout.
- [x] **[MIXED]** **Persist optional welcome skips** across restarts (mic/location) — if product still wants it.

**Sprint check:** UI gate §6 when toasts/snackbars affect preview route messaging.

### Sprint 10.16 — Milestone H handoff queue (non-code)

- [x] **[MIXED]** **Gallery / desktop open** — coordinates with **Milestone H.1**. **Automation created 2026-05-16:** `pns_pull_dcim_for_review.ps1` pulls captures and generates `review_manifest.md` with desktop review checklist. Human sign-off still required per H.1.

**Sprint check:** Script exists; DCIM pull verified; human row documented or waived.

---

## Milestone 11 — completed sprints (11.1, 11.2, 11.4)

**Objective (full milestone):** Restore dodge-style **85 mm / 150 mm** tele digital crops on the clustered **mid-tele** sensor ([`DODGE_PROFILE.md`](DODGE_PROFILE.md)), fix **white balance** readout ordering and defaults, align **face / eye** HUD with the preview, and ship reliable **in-app video** recording plus a **video resolution** selector beside FPS. **Open:** Sprint **11.3** and **Milestone 11 gate** remain in **[BUILD_PLAN.md](BUILD_PLAN.md)**.

**Suggested execution order:** **11.1** → **11.2** → **11.3** → **11.4** → **Milestone 11 gate** (11.3 still active in main plan).

### Sprint 11.1 — White balance menu (coldest → warmest)

- [x] **[HOST]** **[`ReadoutExposureCatalog.awbChoices`](app/src/main/java/dev/pointandshoot/ReadoutExposureCatalog.kt)** — Order HAL-supported presets **coldest → warmest** using Kelvin anchors in **`AwbPresetReadout`**; lead with **AWB** (`CONTROL_AWB_MODE_AUTO`) when advertised; remove the **`null`** “program default” entry; append **OFF** last among HAL modes; keep unknown vendor modes after ordered presets.
- [x] **[HOST]** **[`PreviewReadoutStrip`](app/src/main/java/dev/pointandshoot/PreviewReadoutStrip.kt)** + **[`PreviewReadoutFormat`](app/src/main/java/dev/pointandshoot/PreviewReadoutStrip.kt)** — Drop **“Default (program)”**; **Gray card custom WB** stays **after** the preset loop (bottom action). **`setReadoutManualAwbMode`** / chip: explicit **AWB** vs live **`STATISTICS`** readout as designed in sprint.
- [x] **[HOST]** **Unit tests** — e.g. `ReadoutExposureCatalogAwbOrderTest` (JVM): order, no `null` choice, AWB first.
- [x] **[ADB]** Device: open WB menu — order **SHD → CLD → … → INC**, **OFF** near bottom, gray card last; screencap → **`PROBE_BUILD_PLAN.md`** §5.

**Sprint check:** `pns_verify_toolchain.ps1 -RunTests`; UI gate item **6** for menu proof.

### Sprint 11.2 — Dodge tele routing (73 / 85 / 150 mm)

- [x] **[HOST]** **Single routing policy** — **`FleetAuto` removed**. **`resolveFocalMmSlot`** / **`telePhysicalForPreviewPin`** always use **[`Roles.tele`](app/src/main/java/dev/pointandshoot/BackCameraRoleResolver.kt)** for **73 / 85 / 150** mm; **150 mm** stays **digital [`LongTele150`](app/src/main/java/dev/pointandshoot/CropPlan.kt)** on that sensor ([**`DODGE_PROFILE.md`**](DODGE_PROFILE.md)), never lens-switch to **`longTele`**. When the physical tele id is enumerated, **open it directly** (preferred over logical-parent **`0`** only) so **`SCALER_CROP_REGION`** math matches the LYT-600 active array.
- [x] **[HOST]** **`SensorCropGeometry`** — **`LongTele150`** gates on **`teleId`** only (no alternate long-native path).
- [x] **[HOST]** **Tests** — **`BackCameraRoleResolverTest`** / **`SensorCropGeometryTest`** aligned with dodge-only behavior.
- [x] **[ADB]** Tap **73 → 85 → 150**; confirm crop behavior + logs; optional **`pns_chrome_ux_gate.ps1`** focal slot taps; **`pns_capture_pipeline_verify.ps1`** if session wiring changes.

**Sprint check:** toolchain + capture verify if `PreviewEngineScreen.kt` / crop paths materially change.

### Sprint 11.3 — Face / eye overlay calibration

- [x] **[MIXED]** **Automation** — **`scripts/pns_face_meter_probe.ps1`** exists and operational; captures face detection metrics to JSON. **Completed 2026-05-16:** Script generates JSON/MD artifacts at `hfr-runs\face_meter_probe_*\`.
- [x] **[ADB]** Evidence in **`PROBE_BUILD_PLAN.md`** §5. **Completed 2026-05-16:** Face meter probe artifacts logged.
- [ ] **[MIXED]** **Bisect misalignment** — portrait + reverse-landscape at **23 / 73 / 85 / 150** mm with **Eye AF** on. **Deferred:** Core face detection operational; fine alignment moved to post-M11 optimization.
- [ ] **[HUMAN]** **Live subject sign-off** — eyes land within ~1 finder tile. **Open:** Pairs with **Milestone H.6** Eye-AF photo row.

**Sprint check:** probe script completes; human row documented (H.6 carries remaining human verification).

### Sprint 11.4 — In-app video repair + resolution selector

- [x] **[HOST]** **`scripts/pns_in_app_video_verify.ps1`** — cold video-primary, record stop, assert **`inAppVideoSaved`** / playable MP4 / size threshold; artifacts **`hfr-runs/in_app_video_verify_*`** (Global toolkit row shipped).
- [x] **[MIXED]** **Recorder hardening** — Session **diet** when recording (preview + recorder targets; skip unnecessary RAW/YUV); preview **FPS** capped with **`MediaRecorder`** frame rate; video-only MR path (AAC deferred for OEM stability); **`pickOutputSize`** / **`prepare`** / session rebuild preserves prepared recorder on same camera id.
- [x] **[MIXED]** **Video resolution UI** — In **video mode**, readout **RES** + **`StreamConfigurationMap.getOutputSizes(MediaRecorder::class.java)`**, persisted **`PreviewChromePreferences`**, wired to **`setVideoSize`**; chrome-safe (**readout strip**).
- [x] **[ADB]** **`pns_in_app_video_verify.ps1`** green + **`pns_capture_pipeline_verify.ps1`** after **`PreviewEngineScreen`** session-path edits (item **11**).
- [x] **[HOST]** **`CHANGELOG.md` (Unreleased)** — user-visible in-app video line.

**Sprint check:** verify script pass + DCIM proof in §5.

---

## Milestone 12 — completed sprints (12.1, 12.2, 12.3, 12.4, 12.5, 12.6)

**Objective (full milestone):** Address **P0–P2 findings** from **May 16, 2026 codebase audit** (branch `chore/preview-chrome-camera-intents-histogram`, commit `9c535b7`). Ship audio-enabled video recording, design HFR recording path, complete native encoder integration, refactor monolithic video controller, and establish deterministic audio verification. **All sprints completed; Milestone 12 gate passed 2026-05-17.**

**Suggested execution order:** **12.1** → **12.2** → **12.3** → **12.4** → **12.5** → **12.6** → **Milestone 12 gate** ✅

### Sprint 12.1 — Video audio recording (P0)

- [x] **[HOST]** Add `RECORD_AUDIO` permission check before `MediaRecorder` prepare in `applyInAppVideoRecordingShellLocked`. Create `hasRecordAudioPermission()` helper using `ContextCompat.checkSelfPermission()`.
- [x] **[HOST]** When permission granted, call `mr.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)` **before** `setVideoSource()` per `MediaRecorderGeotag.kt` doc contract.
- [x] **[HOST]** Add `mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)`, `setAudioEncodingBitRate(128_000)`, `setAudioSamplingRate(48_000)` when audio enabled.
- [x] **[HOST]** Update `InAppVideoRecordingUiEvent` sealed class to expose `audioEnabled: Boolean` for UI status logging.
- [x] **[HOST]** Add `PNS.Video` log tag: `inAppVideoPrepared audioEnabled=$audioEnabled size=${sz.width}x${sz.height} fps=$recordFps`.
- [x] **[ADB]** Run `pns_video_audio_verify.ps1 -RecordSec 5` with audio permission granted; verify no `prepare failed` crashes. **Completed 2026-05-16:** Pass on OnePlus 13 (8bf09993).
- [x] **[ADB]** Pull MP4 artifact and verify with `ffprobe` that audio track exists (AAC, 48kHz). **Completed:** `audioCodec=aac`, `audioSampleRate=48000`, `audioBitRate=128252`.
- [x] **[MIXED]** Reject video recording when `desiredFps >= 120` **and** audio enabled. **Verified:** Code blocks HFR video (returns StartFailed when `desiredFps >= 120`).

**Sprint check:** Host compile passes; device recording produces MP4 with AAC audio track; ffprobe evidence in §5.

### Sprint 12.2 — HFR video recording path (P1)

- [x] **[HOST]** Research `CameraConstrainedHighSpeedCaptureSession` availability on reference device (OnePlus 13 / CPH2655). **Completed 2026-05-16:** Device supports `CONSTRAINED_HIGH_SPEED_VIDEO` capability; `docs/HFR_VIDEO_RESEARCH.md` created.
- [x] **[HOST]** Extend `InAppVideoRecordingSupport.kt` with `pickHighSpeedOutputSize()`, `supportsHighSpeedVideoRecording()`, `availableHighSpeedFpsOptions()`. **Completed 2026-05-17:** HFR capability detection and size selection implemented.
- [x] **[HOST]** Extend `VideoRecordingController.applyShell()` with `wantHighSpeed` and `supportsHighSpeed` parameters. **Completed 2026-05-17:** HFR gate allows 120fps+ when both conditions met.
- [x] **[HOST]** Extend `PreviewEngineScreen` with HFR capability detection and UI toast. **Completed 2026-05-17:** Toast "HFR video not available on this device" shown when switching to video mode with FPS ≥ 120 on unsupported devices.
- [x] **[ADB]** Create `pns_hfr_video_verify.ps1` with `hfr_video_gate.v1` JSON schema. **Completed 2026-05-17:** Script tests HFR recording path; evidence logged.

**Sprint check:** HFR infrastructure complete; UI toast implemented; script exists; device capability confirmed; build compiles.

### Sprint 12.3 — Native encoder completion (P2)

- [x] **[HOST]** Verify `nativeEncodeJxl12Rec2020` and `nativeEncodeAvif10Hdr` signatures match `native/pns_native.cpp`. **Completed:** JNI signatures verified; implementations present.
- [x] **[HOST]** `CMakeLists.txt` with `FetchContent` for `libjxl` and `libavif`. **Completed:** NDK build produces `libpns_native.so`.
- [x] **[HOST]** Verify `NativeEncoders.isAvailable` flips `true` when `.so` loads. **Completed 2026-05-16:** APK includes `.so` for `arm64-v8a` and `x86_64`; `NativeDiagnosticsScreen` shows status.
- [x] **[ADB]** Create `pns_native_encoder_verify.ps1` for JXL/AVIF verification. **Completed 2026-05-17:** Script tests encode paths via `PNS.TonalStill` logs; graceful fallback to JPEG when `.so` unavailable.

**Sprint check:** NDK builds; APK packages `.so`; verification scripts exist; graceful fallback verified.

### Sprint 12.4 — Architecture refactoring (P1)

- [x] **[HOST]** Extract `VideoRecordingController` class from `PreviewEngineScreen.kt` monolith. **Completed 2026-05-17:** New 180-line class with `applyShell()`, `maybeStartRecorder()`, `tearDownForCloseCamera()` methods.
- [x] **[HOST]** `VideoRecordingController` owns `MediaRecorder` lifecycle with two-phase prepare/start flow. `PrepareResult` sealed class (`Ready`, `Rejected`, `NoAction`) signals session rebuild needed; `Event` sealed class (`StartFailed`, `Stopped`) for UI callbacks.
- [x] **[HOST]** `PreviewEngineScreen` adds `videoRecordingSessionRebuildPending` flag to coordinate: prepare → set flag → rebuild session → clear flag on `onConfigured` → start recorder.
- [x] **[HOST]** Detekt clean; ~180 lines removed from `PreviewEngineScreen.kt`.
- [x] **[ADB]** `pns_in_app_video_verify.ps1` passes. **Completed 2026-05-17:** OnePlus 13 — `inAppVideoPrepared audioEnabled=true` → `MediaRecorder started` → `inAppVideoSaved` (no "unconfigured surface" errors).
- [x] **[ADB]** `pns_capture_pipeline_verify.ps1` green. **Completed 2026-05-17:** `captureRawStill 1/1 ok=true saved=` — still capture not broken.

**Sprint check:** Host detekt clean; video verify green; capture pipeline green; two-phase session rebuild working rock solid.

### Sprint 12.5 — Audio verification guardrail (P1)

- [x] **[HOST]** Create **`scripts/pns_video_audio_verify.ps1`** extending `pns_in_app_video_verify.ps1`. **Completed 2026-05-16:** `-RequireAudioTrack`, `-MinAudioBitrate` parameters; ffprobe validation.
- [x] **[HOST]** Add `VideoAudio` pack to `pns_sprint_guardrail.ps1` regression dispatch. **Completed 2026-05-17:** Created orchestrator with VideoAudio + CapturePipeline packs; unified `sprint_guardrail.v1` JSON schema; PROBE_BUILD_PLAN.md §5 evidence.
- [x] **[HOST]** Update guardrail JSON schema: `video_audio_gate.v1` with `audioStreamPresent`, `audioCodec`, `audioSampleRate`, `audioBitRate`, `pass`.
- [x] **[ADB]** Run with RECORD_AUDIO denied; verify `audioEnabled=false` in logs. **Completed:** Fresh install shows `PNS.Video: inAppVideoPrepared audioEnabled=false`.
- [x] **[MIXED]** Document guardrail usage in BUILD_PLAN.md with PowerShell examples.

**Usage:**
```powershell
.\scripts\pns_sprint_guardrail.ps1 -Pack VideoAudio -Serial 8bf09993
.\scripts\pns_sprint_guardrail.ps1 -Pack All -Fast
```

**Sprint check:** Script exists; both grant/deny permission cases tested; gate JSON schema valid; documentation complete.

### Sprint 12.6 — Automation infrastructure (P2)

**Objective:** Move automatable "human" tasks to HOST/DEVICE/CI. Reduces Milestone H to truly subjective/account-ownership work.

- [x] **[HOST]** Create **`scripts/pns_desktop_file_validate.ps1`**: Validates pulled DNG/AVIF/JXL files using CLI tools. **Verified 2026-05-16:** Successfully validated DNG and JPG files from device; signature checks pass.
- [x] **[HOST]** Create **`scripts/pns_bracket_regroup_check.ps1`**: Analyzes capture sets by timestamp/filename. **Verified 2026-05-16:** Successfully parsed files; distinguishes single captures from bracket sets.
- [x] **[HOST]** Create **`scripts/pns_gitlab_setup.ps1`**: Uses GitLab REST API to create project, configure mirroring from GitHub, set CI/CD variables.
- [x] **[HOST]** Create **`scripts/pns_github_secrets_set.ps1`**: Uses `gh secret set` or GitHub REST API to configure `ANDROID_KEYSTORE_BASE64`, keystore passwords, alias.
- [x] **[CI]** Extend `.github/workflows/build-signed.yml`: Full `assembleRelease` with real signing key, `apksigner verify`, and **[ADB]** install smoke test on device.
- [x] **[HOST]** Create **`scripts/pns_release_automation.ps1`**: Uses GitHub Release API or `gh release create`. Uploads APK, AAB, SBOM, generates release notes from `CHANGELOG.md`.
- [x] **[ADB]** Create **`scripts/pns_eye_af_alignment_probe.ps1`** (structure pairs with `pns_face_meter_probe.ps1` from Sprint 11.3). **Created 2026-05-16:** CV-based eye-AF alignment check structure ready.
- [x] **[HOST]** Research `imagemagick` or `opencv-python` for dE2000 color accuracy and MTF50 slanted-edge sharpness measurement. **Decision:** Research complete, implementation deferred to post-M12 sprint.

**Sprint check:** All 8 scripts exist and documented; 6+ scripts tested with device evidence in `hfr-runs/`; Milestone H human checklist reduced to truly subjective items only.

---

## Milestone 10 — Post–Milestone 9 product expansion (gate passed 2026-05-17)

**Completed sprints:** **10.1–10.13**, **10.15**, **10.16**. Camera app integration verified on **8bf09993** (`STILL_IMAGE_CAMERA`, `VIDEO_CAMERA` intent filters). **Human sign-off** items → **Milestone H** in active plan.

**Milestone 10 gate — PASSED 2026-05-17**

---

## Milestone 11 — Capture UX fixes (gate passed 2026-05-17)

**Completed sprints:** **11.1** (WB menu), **11.2** (dodge tele routing), **11.3** (face meter probe; overlay sign-off → **H.6**), **11.4** (in-app video + RES).

**Milestone 11 gate — PASSED 2026-05-17**

---

## Milestone 12 — Post-audit capture completeness (gate criteria met 2026-05-17)

**Completed sprints:** **12.1–12.6** (audio, HFR research, native encoder, `VideoRecordingController` refactor, audio guardrail, automation scripts).

**Milestone 12 gate** — host/automation criteria met; device evidence in §5.

---

## Milestone 13 — Fleet RAW parity (OnePlus 13 anchor)

**Objective:** Aux DNG color on **CPH2655**; **Standard** ProShot still default; optional **ZSL** / **HDR still**; fleet profiles; **DCG** session + encoded HDR10; **RAW video** lane on OP13.

**Still modes:** **Standard** (ProShot `DngCreator`) default; **ZSL** / **HDR** optional — same DNG writer, no MotionCam native SDK.

**Key references:** `docs/FLEET_ONEPLUS13_RAW_POLICY.md`, `docs/DNG_OPENABILITY_REGRESSIONS.md`, `docs/MOTIONCAM_APK_FLEET_ANALYSIS.md`, `docs/M13_4_DCG_SESSION.md`, `docs/M13_6_RAW_VIDEO.md`, `docs/M13_8D_STILL_MODE_BENCHMARK.md`, `AGENTS.md` CRITICAL locks.

### Sprint 13.1 — Reference APK decompile (ProShot + MotionCam Pro)

- [x] **[MIXED]** `scripts/pns_motioncam_apk_decompile.ps1`; **`docs/MOTIONCAM_APK_FLEET_ANALYSIS.md`**; **`docs/RAW_REFERENCE_APP_MATRIX.md`**

**Sprint check (May 2026, `8bf09993`):** `libnative-camera-host.so` + `.mcraw`; no `EnableHDRDCGMode` in MotionCam APK — P&S DCG follows Qualcomm probe.

### Sprint 13.2 — Fleet model: `FleetCameraProfile` + OnePlus 13 policy

- [x] **[HOST]** `dev.pointandshoot.fleet` — profiles, `OnePlus13FleetPolicy`, probe export, focal wiring, **`docs/FLEET_ONEPLUS13_RAW_POLICY.md`**, JVM tests

### Sprint 13.3 — Still DNG parity (OnePlus 13)

**ProShot layer contract** on leaf ids **3/2/4** — see archived **`BUILD_PLAN.md`** git history for full table.

#### 13.3a–c — Leaf metadata, IQ, RAW order

- [x] Leaf `DngMetadataResolver`; `StillCaptureIqPolicy`; `LEAF_RAW_FORMAT_ORDER` **32→37→38→36**

### Sprint 13.3g — ProShot pipeline fidelity + DNG openability (P0)

- [x] **[HOST]** `docs/DNG_OPENABILITY_REGRESSIONS.md`; **`ProShotPipelineContract`**; pure `DngCreator` on OP13 leaf (wide-cal / reconcile off by default)
- [x] **[HOST]** `dng_desktop_open_gate.py`, `pns_m13_3g2_gate.ps1`, wired into `pns_aux_dng_capture_analyze.ps1`
- [x] **[ADB]** USB **`8bf09993`**: capture **3/3** + openability **PASS** (`hfr-runs/aux_dng_capture_analyze_20260519_235745/`)

**Human ACR sign-off** → **Milestone H** (Sprint **H.7**).

### Sprint 13.3h — OEM wide-cal bisect (optional)

- [x] H1–H3 **do not ship** `useWideLeafCalibrationForAuxDng`; evidence `hfr-runs/m13_3h_wide_cal_bisect_20260520_003542/`

### Sprint 13.3e — Lock bisect ladder

- [x] E1–E6; **no lock promoted**; `hfr-runs/m13_3e_lock_bisect_20260520_005414/report.md`

### Sprint 13.3f — Daylight USB gates

- [x] `pns_m13_3f_gate.ps1`, `pns_aux_dng_capture_analyze` **3/3**, ProShot parity rawpy **FAIL** on UW/tele (documented HAL issue)

**Human visual ACR** → **Milestone H** (Sprint **H.7**).

### Sprint 13.8 — Optional still modes (ZSL + HDR)

#### 13.8a–d

- [x] `StillCaptureMode`; HUD cycle; `ZslStillFrameRing`; HDR bracket **3× DNG**; `pns_still_mode_benchmark.ps1` v2; `pns_m13_8d_gate.ps1` USB **PASS** (`hfr-runs/m13_8d_gate_20260520_020059/`)

**Human `STILL_MODE_COMPARE.md`** → **Milestone H** (Sprint **H.7**).

### Sprint 13.4 — DCG session alignment (encoded HDR video)

- [x] `DcgSessionParameters` + `resolveInAppVideoFormat()`; USB `pns_video_hdr10_metadata_verify.ps1` **PASS** (`hfr-runs/hdr10_meta_verify_20260519_222210/`)

### Sprint 13.5 — Fleet catalog

- [x] `FleetCameraCatalog`, `FleetOemOverrides`, probe export, CI `pns_fixture_dng_gates.ps1`

### Sprint 13.6 — RAW video path (MCRAW-class)

- [x] `RawVideoWriter` (`PNMRAWV1`); `RawVideoRecordingController`; HUD RAW lane; ADB `pns_preview_video_raw_sec`; USB **PASS** 145 frames (`hfr-runs/raw_video_verify_20260519_225113/`)

**Scope:** OP13 leaf cameras only for RAW video MVP.

---

## Milestone 13V — Video product expansion (2026-05-17)

**Objective:** Power-button quick-launch, HFR / 10-bit encoded video, DCG + HDR10 metadata, unified format picker, macro mode, recording overlays, RGB histogram. **Sprint IDs** use **13V.** prefix to distinguish from **Milestone 13** fleet RAW (**13.1–13.8**).

### Sprint 13V.1 — Power button quick-launch (P1)

- [x] `clearTaskOnLaunch`, `STILL_IMAGE_CAMERA_SECURE`, Quick Settings tiles; `pns_power_button_gate.ps1`

### Sprint 13V.2 — YUV+10-bit "RAW-like" video (P1)

- [x] HEVC Main10 / YUVP010 via `MediaCodecVideoRecorder`; `pns_mediacodec_hfr_verify.ps1` TenBit cases PASS (`hfr-runs/mediacodec_verify_20260517_100346`)

### Sprint 13V.3 — HFR 1080p@120 / 240 (P1)

- [x] MediaCodec path; ADB `pns_preview_video_fps` / `pns_preview_video_10bit`; 120/240 PASS same verify run

### Sprint 13V.4 — Unified video format picker (P1)

- [x] `VideoFormatPickerSheet`, `VideoFormatPresets`, 4K tiers; 7/7 `pns_mediacodec_hfr_verify.ps1` (`hfr-runs/mediacodec_verify_20260517_114216`)

### Sprint 13V.5 — DCG + HDR10 SEI (P2)

- [x] `DcgModeSupport`, HDR10 static info on MediaCodec; `pns_video_hdr10_metadata_verify.ps1` PASS (`hfr-runs/hdr10_meta_verify_20260517_120333`)

### Sprint 13V.6 — Macro shooting mode (P2)

- [x] `CommandDialMode.Macro`, UW auto-switch, vendor keys; `pns_macro_focus_verify.ps1` PASS

### Sprint 13V.7 — Multi-camera pipeline research (P3)

- [x] Documented in `docs/RAW_CAPTURE_DEVICE_MATRIX.md`

### Sprint 13V.8 — Recording overlays (timer + audio meters) (P2)

- [x] `TimecodeOverlay`, `AudioLevelMeter`; `pns_recording_overlays_verify.ps1` PASS

### Sprint 13V.9 — RGB histogram for video (P3)

- [x] `PreviewLumaHistogram.reduceRgb()`, HUD toggle; `pns_rgb_histogram_verify.ps1` PASS

### Sprint 13V.10 — Focus peaking for video (P3)

- [x] GLES peaking + M dial manual focus; `pns_focus_peaking_verify.ps1`; **`docs/M13V_10_FOCUS_PEAKING.md`**

### Sprint 13V.11 — LUT preview for video (P3)

- [x] `PreviewLutSelection` + `PNS.LutPreview`; `pns_video_lut_preview_verify.ps1`; **`docs/M13V_11_VIDEO_LUT_PREVIEW.md`**

### Sprint 13V.12 — Battery / thermal monitoring (P3)

- [x] `PreviewPowerThermalOverlay`; `pns_power_thermal_verify.ps1`; **`docs/M13V_12_POWER_THERMAL.md`**

### Sprint 13V.13 — Storage remaining indicator (P3)

- [x] `PreviewStorageRemainingOverlay`; `pns_storage_remaining_verify.ps1`; **`docs/M13V_13_STORAGE_REMAINING.md`**

### Sprint 13V.14 — README update (P2)

- [x] README audit **M10–13V** shipped features, status table, verify-script index (May 2026)

### Sprint 13V.15 — MediaCodec capability probe (P1)

- [x] `MediaCodecCapabilityProbe` + `PNS.VideoCapProbe`; `pns_video_capability_probe.ps1` **PASS** on **`8bf09993`** (`has4k120=true`, `c2.qti.hevc.encoder` **3840x2160@120fps**); **`docs/M13V_15_VIDEO_CAP_PROBE.md`**

### Sprint 13V.16 — 4K@120 unlock (P1)

- [x] Chrome encode prefs → `setInAppVideoEncodeSize` + `pickHighSpeedVideoTarget` (4K pref before 1080p/720p HS fallbacks); HFR record size follows constrained session (OP13: **720p@120** capture, encoder advertises **4K@120**).
- [x] MediaCodec path at **120+ fps** (no 60 fps cap); `peekInAppVideoRecorderStarted` waits for muxer-ready; muxer lifecycle fix (no pre-muxer discard on stop).
- [x] ADB `--ei pns_preview_video_encode_w/h`; `pns_mediacodec_hfr_verify.ps1` **7/7 PASS** on **`8bf09993`** (`hfr-runs/mediacodec_verify_20260520_011851/`).
- [x] **`docs/M13V_16_4K120_UNLOCK.md`**

### Sprint 13V.17 — AI features backlog (P3)

- [x] **[HOST]** `SmileStillCapturePolicy` + ML Kit smile on YUV when HUD enabled; tray still capture ref; cooldown **4.5 s**.
- [x] **[HOST]** `SceneVendorHintProbe` at app start → **`PNS.SceneHint`**; HUD **Scene vendor hints (log)**.
- [x] **[HOST]** `videoBitrateScalePercent` (**50–150%**) in `VideoRecordingController.bitrateForSize()`; HUD slider.
- [x] **[HOST]** `pns_ai_features_verify.ps1 -HostOnly`; JVM **`SmileStillCapturePolicyTest`**, **`SceneVendorHintProbeTest`**.
- [x] **`docs/M13V_17_AI_FEATURES.md`**
- [x] **[ADB]** `pns_ai_features_verify.ps1` **USB_PASS** on **`8bf09993`** (`hfr-runs/ai_features_verify_20260520_075142/`) — scene probe, bitrate **24883200 → 31104000** (**100% < 125%**), smile synthetic + DNG save.
- [ ] **[DEVICE][optional]** Manual smile at camera (ML Kit path); synthetic hook covers automation gate.

### Sprint 13V.18 — CameraX OEM extension probe (P2)

- [x] **[HOST]** `CameraXExtensionProbe`; Night/Bokeh dial filtered when unavailable; **`CameraXExtensionProbeTest`**.
- [x] **[HOST]** `pns_camerax_extension_probe.ps1` (`-HostOnly` + USB path); **`force-stop`** after run.
- [x] **`androidx.camera:camera-camera2`** dependency (required for `ProcessCameraProvider`; was missing → probe logged `IllegalStateException` only).
- [x] **[ADB]** `probe.json` with `probeComplete=true` on USB **`8bf09993`**: **`PROBE_OK_NO_EXTENSIONS`** (`hfr-runs/camerax_ext_probe_20260520_072853/`).
- [x] **[DEVICE]** Night/Bokeh absent from preview a11y tree / shooting-mode menu (no `NIGHT`/`BOKEH` in `uiautomator` dump after cold preview launch).

**Milestone 13V gate:** **13V.1–13V.18** automated/USB-verified on reference fleet **`8bf09993`** (May 2026).

---

## Milestone 13 — Fleet RAW parity (gate archived 2026-05-21)

**Objective:** Aux DNG on **OnePlus 13** (`CPH2655` / `8bf09993`); **Standard** ProShot still default; optional **ZSL** / **HDR still**; fleet profiles; **DCG** session + **RAW video** on OP13 leaf cameras.

**Completed sprints:** **13.1**, **13.2**, **13.3** (a–h, e, f automated), **13.3g** (automated), **13.4**, **13.5**, **13.6**, **13.8** (a–d automated) — see *Milestone 13 — Fleet RAW parity* section above in this file.

**Human closure:** ACR 3/3, visual aux color, **STILL_MODE_COMPARE** → active **[BUILD_PLAN.md](BUILD_PLAN.md)** **Milestone H** sprint **H.7** only.

### Sprint 13.7 — Milestone 13 gate

| Check | Pass criterion | Status |
|-------|----------------|--------|
| Host | `pns_verify_toolchain.ps1 -RunTests` exit 0 | ✅ |
| Still regression | `pns_capture_pipeline_verify.ps1` green | ✅ |
| DNG openability | **13.3g** gate PASS on USB | ✅ |
| Aux DNG | `pns_aux_dng_capture_analyze.ps1 -PreviewDial A` **3/3** | ✅ |
| ProShot parity (rawpy) | Documented **FAIL** UW/tele (HAL CM2) | ⚠️ documented |
| Still modes | ZSL + HDR benchmark PASS; Standard default for pipeline verify | ✅ |
| Lock / wide-cal bisect | **13.3e** / **13.3h** — no lock shipped | ✅ |
| DCG | `pns_video_hdr10_metadata_verify.ps1` PASS | ✅ |
| RAW video | `pns_raw_video_verify.ps1` PASS | ✅ |
| Docs / §5 | Policy docs + probe rows | ✅ **`docs/M13_7_GATE.md`** |
| **Human ACR 3/3** | **`ACR_HUMAN_VERIFY.md`** + **`-RecordAcrPass`** | ❌ → **H.7** |
| **Visual aux color** | ACR vs ProShot (**Standard**) | ❌ → **H.7** |
| **STILL_MODE_COMPARE** | **13.8d** daylight ACR across modes | ❌ → **H.7** |
| Battery | `force-stop` after each script | ✅ (scripts) |

**Milestone 13 gate:** Automated/USB criteria **PASS** (May 2026). Closes for publication when **H.7** human rows complete.

---

## Milestone 14 — Preview polish, pro controls, dual video (archived 2026-05-21)

**Objective:** Preview chrome regressions (readout / video format chip, status-bar HUD), QR scan, mode dial UX, face/eye overlay tooling, HFR HEVC color, manual AE/focus, selfie indicator, DND restore, heritage + donation, **stacked dual video** (one MP4), GitHub release APK packaging — without breaking locked chrome, dodge tele routing, or capture/DNG locks.

**Target device:** OnePlus 13 (`CPH2655` / `8bf09993`) for USB gates.

**Product decisions (locked):**
- **Dual video:** one MP4, **stacked** rear (top) + front (bottom) composite @ **1920×1080** @ **30 fps** v1.
- **Donation:** Venmo — `https://venmo.com/code?user_id=1857304970395648420` in Settings → About heritage block.

### Sprint 14.1 — Readout + video format chip (P0)

- [x] Wire `PreviewReadoutStrip(primaryPhoto, videoFormatChipSlot)` in `PreviewEngineScreen.kt` with `VideoFormatChip` + `InAppVideoFormatSelection`.
- [x] Video mode: hide Still LUT + IMG; show Video LUT + format chip.
- [x] **Sprint check:** `pns_chrome_ux_gate.ps1` **PASS** (`readoutOk=true`).

### Sprint 14.2 — Status bar HUD (timer, audio, messages)

- [x] `PreviewTopStatusBar` in top inset band: `TimecodeOverlay` + `AudioLevelMeter`; pipeline hints via `previewStatusBarLine`.
- [x] **Sprint check:** `pns_video_status_bar_verify.ps1` **PASS** on **8bf09993**.

### Sprint 14.3 — Mode selector UX (sections + orange selection)

- [x] Shooting-mode dropdown: **Photo programs** / **Video programs** section headers; selected row `PnsColors.PhotoOrange`.
- [x] **Sprint check:** `pns_chrome_ux_gate.ps1` **PASS** on **8bf09993**.

### Sprint 14.4 — QR scan photo mode

- [x] `CommandDialMode.Qr` (photo-only); ZXing on preview YUV; confirm-then-open for links.
- [x] **Sprint check:** `pns_qr_scan_verify.ps1` **PASS** on **8bf09993**.

### Sprint 14.5 — Face / eye overlay alignment

- [x] Audit buffer → overlay paths; debug crosshair toggle + `pns_eye_af_alignment_probe.ps1` **HOST_PASS**.
- [ ] **Sprint check:** **[HUMAN] H.8.1** glass sign-off → **Milestone H** in active plan.

### Sprint 14.6 — HFR HEVC color (vs H.264)

- [x] 8-bit HEVC Main: BT.709 limited VUI in `MediaCodecVideoRecorder`; `colorVui=bt709` log.
- [x] **Sprint check:** `pns_video_codec_color_compare.ps1` **PASS** (HEVC @ 120); H.264 @ 60 clip optional.

### Sprint 14.7 — ISO band + AE coupling

- [x] ISO presets + locked ISO / locked SS readout chips; `docs/PNS_TECHNICAL_SETTINGS.md` §3–§4.
- [x] **Sprint check:** `ReadoutIsoBandTest` JVM; `pns_capture_pipeline_verify.ps1` **PASS** on **8bf09993**.

### Sprint 14.8 — Focus mode picker

- [x] AF modes + manual distance drag; macro program; readout **AF** chip.
- [x] **Sprint check:** `pns_focus_peaking_verify.ps1` **PASS** on **8bf09993**.

### Sprint 14.9 — Selfie ring + smile under Eye AF

- [x] Orange selfie ring in top inset when front camera active; Eye AF menu **Smile to capture**.
- [x] **Sprint check:** `pns_ai_features_verify.ps1` **USB_PASS** on **8bf09993**.

### Sprint 14.10 — DND restore on exit / toggle off

- [x] `PreviewWindowEffects` hold/release; lifecycle restore; `dndPreview=restored` logs.
- [x] **Sprint check:** `pns_dnd_restore_verify.ps1` **USB_PASS** on **8bf09993** (when policy access granted).

### Sprint 14.11 — Settings heritage, donation, LG nod

- [x] `AboutScreen`: heritage credits (orange brands), LG nod, Venmo **Support development**; About scroll fix (no nested `verticalScroll`).
- [x] **Sprint check:** `pns_about_links_verify.ps1` **USB_PASS** on **8bf09993**.

### Sprint 14.12 — Dual video (stacked, single MP4)

- [x] **Phase A:** `docs/M14_12_DUAL_VIDEO.md`.
- [x] **Phase B:** Stacked preview + front `CameraDevice` + GL → MediaCodec composite **1920×1080** @ **30 fps**.
- [x] **Sprint check:** `pns_dual_video_verify.ps1 -RecordSec 5` **USB_PASS** on **8bf09993** (`inAppVideoSaved ok=true`).

### Sprint 14.13 — Release APK packaging for GitHub

- [x] `pns_release_packaging.ps1`: `assembleRelease`, `Point-and-Shoot_<versionName>.apk`, `zipalign -c -v 4`.
- [x] README release section + `pns_release_automation.ps1` for GitHub upload.

### Milestone 14 gate (archived)

| Check | Pass criterion | Status |
|-------|----------------|--------|
| Host | `pns_verify_toolchain.ps1 -RunTests` | partial — detekt baseline drift (pre-existing) |
| RAW still | `pns_capture_pipeline_verify.ps1` | **USB_PASS** **8bf09993** |
| Chrome | `pns_chrome_ux_gate.ps1` | **USB_PASS** **8bf09993** |
| Video smoke | `pns_in_app_video_verify.ps1` | **USB_PASS** **8bf09993** |
| HEVC color | `pns_video_codec_color_compare.ps1` | partial — HEVC **120** bt709 OK |
| AI / smile | `pns_ai_features_verify.ps1` | partial — bitrate scale needle |
| DND restore | `pns_dnd_restore_verify.ps1` | blocked without notification policy access on some devices |
| About overlay | `pns_about_links_verify.ps1` | **USB_PASS** **8bf09993** |
| Dual video | `pns_dual_video_verify.ps1` | **USB_PASS** **8bf09993** (stacked + record) |
| Face alignment | `pns_eye_af_alignment_probe.ps1` + **H.8.1** | **HOST_PASS** / glass TBD |
| Release script | `pns_release_packaging.ps1` | **HOST_PASS** |
| Battery | `force-stop` after USB scripts | done |

**Shipped version:** **`0.14.0-beta.2`**, `versionCode` **14002**, asset **`Point-and-Shoot_0.14.0-beta.2.apk`**.

**Milestone 14 gate:** Sprints **14.1–14.13** archived; subjective **H.8** remains in active **[BUILD_PLAN.md](BUILD_PLAN.md)**. Chart calibration tuning deferred in active plan (*Pinned — Chart calibration*).

---

## Bespoke Gallery Integration

**Objective:** Replace system gallery resolver with custom `BespokeGalleryScreen` for in-app media browsing and management.

**Shipped:** 2026-05-21 (integration + device verify); **BG.3 closed** 2026-05-22 (maintainer UX/UI sign-off).

### Sprint BG.1 — Bespoke Gallery Implementation

**Code:** `BespokeGalleryScreen.kt`, `PreviewEngineScreen.kt`

- [x] **[AGENT]** Create `BespokeGalleryScreen.kt` with MediaStore loading and bitmap display (removed Coil dependency)
- [x] **[AGENT]** Add `showBespokeGallery` state variable to `PreviewEngineScreen.kt`
- [x] **[AGENT]** Modify `PreviewBottomCaptureTray` to accept `onBespokeGalleryChange` callback
- [x] **[AGENT]** Update gallery thumbnail click handler to show bespoke gallery
- [x] **[AGENT]** Add bespoke gallery overlay composable with proper state management
- [x] **[AGENT]** Fix Kotlin scope issues and compilation errors
- [x] **[AGENT]** Fix deprecated ArrowBack icon warning (low priority)

### Sprint BG.2 — Device Verification

**Verification scripts:** `pns_gallery_integration_verify.ps1`, `pns_gallery_integration_complete.ps1`

- [x] **[ADB][HUMAN]** Test gallery thumbnail click opens bespoke gallery instead of system resolver (2026-05-21)
- [x] **[ADB][HUMAN]** Verify back button functionality returns to preview (2026-05-21)
- [x] **[ADB][HUMAN]** Test external gallery button launches system resolver (2026-05-21)
- [x] **[ADB][HUMAN]** Verify media items load and display correctly (2026-05-21)
- [x] **[ADB][HUMAN]** Test navigation between different media items (2026-05-21)
- [x] **[HUMAN]** Create `pns_gallery_integration_verify.ps1` for automated testing (2026-05-21)
- [x] **[HUMAN]** Create `pns_gallery_integration_complete.ps1` for comprehensive automated testing (2026-05-21)

### Sprint BG.3 — UX Polish & Features

- [x] **[AGENT]** Add media metadata display (EXIF, capture settings) (2026-05-21)
- [x] **[AGENT]** Implement media deletion functionality (2026-05-21)
- [x] **[AGENT]** Implement media sharing options (2026-05-21)
- [x] **[AGENT]** Implement zoom and pan for detailed viewing (2026-05-21)
- [x] **[HUMAN]** UX review and accessibility improvements — **maintainer UX/UI sign-off 2026-05-22** (formal TalkBack / a11y audit remains **Milestone H.6**)

**BG.1 Implementation gate:** `gradlew :app:compileDebugKotlin` passes with zero errors.

**BG.2 Device gate:** `pns_gallery_integration_verify.ps1` + human device checks on **8bf09993** (2026-05-21); no capture regressions.

**BG.3 UX gate:** Metadata, delete, share, zoom/pan shipped in `BespokeGalleryScreen.kt`; **maintainer approved UX/UI 2026-05-22**; tray **Photo / Video / Gallery** restore via `PreviewLastSurfacePrefs` (same session).

**BG Integration gate:** **CLOSED** — bespoke gallery fully functional; preview chrome layout lock unchanged.

---

## Performance & Optimization

**Objective:** Enhance app performance, reduce resource usage, and improve battery/thermal behavior on preview.

**Shipped:** 2026-05-22 (USB gates on **CPH2655** `8bf09993`).

### Sprint PO.1 — Memory & Performance Optimization

**Code:** `PreviewEngineScreen.kt`, `LutCameraPreviewRenderer.kt`, `PnsBitmapGuard.kt`, `PnsMediaStoreGallery.kt`, `BespokeGalleryScreen.kt`, `MemoryProfiler.kt`

- [x] **[AGENT]** Optimize preview pipeline memory usage with buffer pooling (GLES renderer)
- [x] **[AGENT]** Add performance monitoring hooks for capture latency
- [x] **[AGENT]** Implement lazy loading for gallery thumbnails
- [x] **[AGENT]** Implement memory leak detection and cleanup for bitmap resources (`PnsBitmapGuard`)
- [x] **[AGENT]** Optimize MediaStore queries with proper indexing (`PnsMediaStoreGallery`)
- [x] **[ADB][HUMAN]** Profile memory usage during extended capture sessions (`pns_memory_profiler.ps1` + `PNS.MemoryProfiler`)

### Sprint PO.2 — Battery & Thermal Optimization

**Code:** `PreviewPowerThermalMonitor.kt`, `PreviewAdaptiveFpsPolicy.kt`, `PreviewLongRunningPause.kt`

- [x] **[AGENT]** Add thermal throttling detection and response (`PreviewPowerThermalMonitor`)
- [x] **[AGENT]** Optimize background processing to minimize battery drain
- [x] **[AGENT]** Implement adaptive preview FPS based on battery level (`PreviewAdaptiveFpsPolicy`, `PNS.PowerThermal adaptiveFpsCap`)
- [x] **[AGENT]** Implement smart pause/resume for long-running operations (`PreviewLongRunningPause`)
- [x] **[ADB][HUMAN]** Test battery life under various usage patterns (13V.12 verified)
- [x] **[ADB][HUMAN]** Verify thermal management under sustained load (13V.12 verified)

**PO.1 Memory gate:** `pns_memory_profiler.ps1` **PASS** — `preview_session_start/stop`, `PNS.Bitmap leakCheck … ok`, no critical `PNS.MemoryProfiler` pressure. Artifacts: `hfr-runs/memory_profiler_*`.

**PO.2 Battery gate:** `pns_battery_life_test.ps1` **PASS** — `adaptiveFpsCap`, `longRunningPaused=true/false`. Artifacts: `hfr-runs/battery_life_test_*`.

**PO Optimization gate:** `pns_po_optimization_gate.ps1` **PASS** — combined report `hfr-runs/po_optimization_gate_*/report.md`. (15% battery improvement / 60-minute thermal soak remain human fleet benchmarks.)

**Gallery note (post-PO):** Selfie DNG orientation — `DngGalleryOrientation` + DNG-only decode path in `GalleryThumbnail.kt` (ongoing UX fix separate from PO gate closure).

---
