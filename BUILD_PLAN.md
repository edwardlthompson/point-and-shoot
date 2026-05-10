## Build plan (Point & Shoot)

**Purpose:** Single roadmap for shipping the Parts 1–5 spec with **milestones → sprints → gates**. Execution order: **foundations → probes → mapping → capture engine → HUD/UX → color/LUT → quality bar → CI automation → human publication.**

**Living docs:** `PROBE_BUILD_PLAN.md` (§5 audit log; **§6** probe/infra checklist ↔ **milestones** mapping table), `CHANGELOG.md`, `CLI_BUILD_AND_SIDELOAD.md`, `DODGE_PROFILE.md`, `COLOR_PIPELINE.md`, `NDK_PLAN.md`.

---

### How agents must execute (nonstop discipline)

1. **Work inside one milestone at a time.** Finish **every sprint** in that milestone (all checkboxes that belong to that sprint) before starting the next milestone.
2. **Within a sprint, complete tasks in order** (top to bottom). If a task is blocked, log the blocker in `PROBE_BUILD_PLAN.md` §5 and fix or escalate; do not silently skip.
3. **After each sprint:** run the **Sprint check** row in that sprint’s gate table. If any check fails, **stop**, fix, re-run.
4. **After all sprints in a milestone:** run the full **Milestone gate**. Only then proceed to the next milestone.
5. **Tick rules:** Never mark `[x]` without meeting **Appendix A — Verification protocol**. Host work requires `scripts/pns_verify_toolchain.ps1 -RunTests` (and `ReadLints` on touched Kotlin). Device work requires evidence in `PROBE_BUILD_PLAN.md` §5.
6. **JAVA_HOME (Windows):** use `C:\Program Files\Android\Android Studio\jbr` when Gradle fails with “no java”.
7. **ADB:** prefer `%LOCALAPPDATA%\Android\Sdk\platform-tools` first on `PATH` (pair/connect vs legacy adb).
8. **ADB serial:** optional **`scripts/pns_adb_device.env`** (copy **`scripts/pns_adb_device.env.example`**) sets **`PNS_ADB_SERIAL`** for **`pns_adb_preview_validate.ps1`** / **`pns_milestone6_gate.ps1`** when `-Serial` is omitted; Wi‑Fi **`host:port`** values trigger **`adb connect`** in the script.

**Human work:** Only **Milestone H — Human & publication** contains tasks that require a person (accounts, subjective judgment, physical charts, desktop apps). Agents prepare artifacts; humans close **[HUMAN]** items.

---

### Global toolkit (used in gates)

| Tool | Role |
|------|------|
| `scripts/pns_verify_toolchain.ps1 -RunTests` | Host gate: assembleDebug, unit tests, FOSS dep-audit, license/SBOM, script UTF-8 |
| `scripts/pns_hfr_autorun.ps1` | Device probe automation (`-RunProbeSmoke`, `-RunFullSuite`, …) |
| `scripts/pns_adb_preview_validate.ps1` | Scripted preview / RAW / BKT scenarios + log capture |
| `scripts/pns_milestone6_gate.ps1` | Milestone 6 pack: `assembleDebug` + `-Milestone6Pack` (DNG 50708, LUT FPS probe, Calibrate + GLES smoke) → **`milestone6_gate.json`** |
| `scripts/pns_failure_matrix_smoke.ps1` | Milestone 7 smoke: preview cold start + CAMERA revoked preview → **`failure_matrix_smoke.json`** (no AndroidRuntime fatal for `dev.pointandshoot`) |
| `scripts/pns_adb_device.env` (gitignored; copy `.example`) | Default **`PNS_ADB_SERIAL`** for scripts when `-Serial` omitted (Wi‑Fi **`ip:port`** OK) |
| `.github/workflows/toolchain-verify.yml` | CI mirror of toolchain |

**Known limitation:** `:app:lintDebug` is **not** in the toolchain gate (AGP + Compose lint API mismatch). Track via compile + unit tests + IDE `ReadLints`.

---

### Preview finder acceptance (device proof — do not guess)

These behaviors are **easy to break with layout math mistakes**. Any change to `PreviewMainViewport`, `TexturePreviewFit`, `effectivePreviewStaticRotationDeg`, `BackCameraRoleResolver`, or the 7×7 focal row **must** close the checklist below with evidence in `PROBE_BUILD_PLAN.md` §5 (timestamp + device serial + what was verified).

| Item | Pass criterion (on-device) |
|------|---------------------------|
| **No side pillarbars** | In preview screen, live image **fills the finder width**; any crop is **top/bottom only** (center-crop), not black bars left/right from aspect-fit “contain”. |
| **No horizontal stretch** | Point the camera at a **square** calibration target (or square UI element); the square must stay **square** (uniform scale), not wider than tall. |
| **Preview locked on rotation** | Rotating the phone **does not** change static preview rotation automatically; only **Spin (preview)** changes buffer rotation. Finder does not jump between portrait/landscape. |
| **Tele focal presets** | With ≥3 rear cameras, tapping **73 / 85 / 150** selects the **tele** camera (check status line `cameraId=…` or mode-transition log); preview FOV changes. Resolution uses **BackCameraRoleResolver** (focal-length clustering), not hard-coded `"4"` only. |
| **Host regression** | `pns_verify_toolchain.ps1 -RunTests` exit 0; `TexturePreviewFitTest` + `PreviewLayoutOrientationTest` green. |

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

**Milestone 3 gate:** Toolchain PASSED; mapping doc matches code (`SensorCropGeometryTest`, `DngDefaultUserCropRatiosTest`, etc.).

---

## Milestone 4 — Imaging engine & capture reliability

**Objective:** Stable Camera2 preview/capture, RAW→DNG, bracketing, metering/AF features validated on device; NDK encode path completed when scheduled.

### Sprint 4.1 — Host-complete capture path (remaining encoder bodies)

- [x] [HOST] High-speed preview session, RAW12 `Dng12Saver`, `CaptureHaptics`, JNI shell `libpns_native.so`, `NativeEncoders` / `EncoderRoute`.
- [x] [HOST] **NDK encode bodies:** libavif (SVT-AV1 encode) + libjxl via CMake FetchContent; JNI bodies in `native/pns_native.cpp` (`BUILD_PLAN` / `NDK_PLAN`).

### Sprint 4.2 — Advanced metering & AF (**ADB closes parents**)

**Parents:** all **[ADB]** children below **`[x]`** with **`PROBE_BUILD_PLAN.md` §5 — 2026-05-10** (`adb_preview_validate_20260510_021941` on Wi‑Fi adb **`192.168.1.2:34365`** / **`8bf09993`**).

- [x] [ADB] **Highlight metering (dial H)** — YUV histogram → **`HighlightMeter`** AE comp applied; logs **`PNS.AdbValidation`** **`highlightMeter ev=… aeComp=… dial=H`** (≥3.5s throttle) in **`logcat_highlight_dial_H.txt`** same folder.
- [x] [ADB] **Eye-AF** — **`eyeAf faceDetectMode=`** + **`availableModes=`** logged once per session (reference HW: **SIMPLE** only — no **FULL** in list); **`eyeAf statisticsSample`** when **`STATISTICS_FACES`** non-empty. Multi-distance / lighting / **FULL** when advertised → **Milestone H** photo matrix.
- [x] [ADB] **3D tracking** — **`tracker statisticsPipeline active`** proves **`TrackerState`** wired to metadata; **`tracker lockedIds=…`** on lock-set delta when faces appear. Intentional dropout / re-acquire torture → **Milestone H**.
- [x] [MIXED] **Bracketing BKT** — **[ADB]** **`captureBracketBurst pattern=Three ok=true`** + three **`pns_*_bkt?of3*.dng`** writes in **`logcat_bracket_bkt3.txt`**; **[HOST][HUMAN]** bracket desktop regroup — **Milestone H**.

### Sprint 4.3 — Phase 1 V&V (**camera reliability**)

- [x] [ADB] **10 consecutive captures** without session death — §5 **2026-05-10**: Wi‑Fi adb **`192.168.1.2:34365`** / **`8bf09993`**; `pns_adb_preview_validate.ps1` artifact **`hfr-runs/adb_preview_validate_20260510_020501/`** contains **`captureRawStill k/10 ok=true`** lines **`1/10`–`10/10`** + **`finished sequential RAW stills n=10`**.
- [x] [ADB] **Logcat cleanliness** — no repeating Camera2 fatal/error spam in the same run (`summary_grep.txt` **ERROR** sweep clean for Camera paths); **`MediaGeotag`** failures log **one-line** warnings only (no throwable stacks) so scripted greps stay readable.
- [x] [HOST] **RAW12 / Ultra-Max DNG path + ADB automation** — `ImagingProfile.UltraMax` → `CaptureStorage.CaptureKind.DngRaw12` (`toDngCaptureKind()`); intent extra **`pns_preview_imaging_profile`** (`standard_pro` \| `ultra_max`, see **`EXTRA_PNS_PREVIEW_IMAGING_PROFILE`**); validate script scenario **`raw12_ultra_max_x1`** (Ultra-Max + one sequential RAW); **`ImagingProfileTest`** pins mapping + `byId`; **`ImagingProfile.all` / `byId`** avoid JVM companion-init null singletons (same issue as [EncoderRoute]). **Desktop** pull/open of Ultra-Max DNG in RAW tools → **Milestone H.1** (human), not a Sprint 4.3 gate.
- [x] [ADB] **Ultra-Max scripted smoke** — §5 **2026-05-10**: Wi‑Fi adb **`192.168.1.2:34365`**; **`am start`** with **`pns_preview_imaging_profile=ultra_max`** + **`pns_preview_raw_count=1`** → **`PNS.AdbValidation`** **`automation extras … profile=ultra_max`** + **`captureRawStill 1/1 ok=true`** **`saved=pns_*_ultra_max_*.dng`** + **`finished sequential RAW stills n=1`**.

**Milestone 4 gate**

| Check | Pass criterion |
|-------|----------------|
| Host | `pns_verify_toolchain.ps1 -RunTests` + ReadLints clean on touched files |
| Device | §5 rows for **every** closed [ADB] bullet in sprints 4.2–4.3; `pns_adb_preview_validate.ps1` exit 0 on reference device |
| Encoder bodies | Sprint 4.1 NDK rows `[x]` when landed |

---

## Milestone 5 — HUD, dial & street UX

**Objective:** Operator-facing surfaces stable and regression-tested.

### Sprint 5.1 — Pro HUD (ship-ready)

- [x] [HOST] Command dial, HUD toggles, tally/timecode, Pro HUD overlay wiring.

### Sprint 5.2 — Phase 2 V&V

- [x] [ADB] Mode transitions deterministic and logged (no hidden state) — `PNS.ModeTransition` + `ModeTransitionLog` / `TrackModeTransition` (camera, fps, imaging profile, recording, focal crop, command dial) and `preview_pipeline_restart` from `PreviewController.maybeRestartBody`; ADB intent dial aligned with controller via `SideEffect` (avoids first-frame `M` vs `H` skew). Script: `pns_adb_preview_validate.ps1` scenario `sprint52_mode_vv` + log tags.
- [x] [ADB] No UI-induced capture regressions (preview stable) — same May 2026 device pass: cold start `ultra_max` + dial `H` shows monotonic `seq=*` `PNS.ModeTransition` lines, `preview_pipeline_restart` with consistent `commandDial=H`, no `ERROR` / session death in preview validate artifacts; see `PROBE_BUILD_PLAN.md` §5.

### Sprint 5.3 — Phase 3 polish

- [x] [HOST] **Street (Snap) program** — With **`CommandDialMode.S`** and **no tap metering**, `PreviewController.applyScalerCropAndMetering` applies snap AF: **`CONTROL_AF_MODE_OFF`** + **`LENS_FOCUS_DISTANCE` = 0** when supported, else **EDOF**, else **continuous video / picture**; tap-to-focus still overrides with CAF. **`CommandDial`** + **`AboutScreen`** (“Command dial — Snap (street)”) tie Ricoh Snap Focus heritage to dial **S**.
- [x] [HOST] **Macro lock (live caps)** — **`HardwareCapsSnapshot.build`** fills **`HardwareCaps`** from **`CameraCharacteristics`** + **`BackCameraRoleResolver`** / **`LensInfoSummary.isMacroCapable`** (UW) + **`VendorKeyGuard`** for **`com.oplus.macro.closeup.enable`**; **`PreviewController.hardwareCaps()`** exposes it; **Developer menu** shows **`CapabilityGate.evaluate`** lines when camera permission is granted. ADB Super Macro probe (**`EXTRA_PNS_PREVIEW_SUPER_MACRO_PROBE`** + **`EXTRA_PNS_PREVIEW_CAMERA_ID`** → ultra-wide) applies the tag via **`SessionConfiguration.setSessionParameters`** (API 33+) when possible, else repeating **`CaptureRequest`** (**`VendorKeyGuard`** session/request **`trySet`** + legacy probes — see §5).
- [x] [MIXED] Super Macro hardware lock — **[ADB]** Automated gate: **`scripts/pns_super_macro_gate.ps1`** (or **`pns_adb_preview_validate.ps1 -SuperMacroOnly`**) writes **`super_macro_gate.json`** / **`super_macro_gate.txt`** under the run folder; **`pass: true`** requires **`PNS.AdbValidation`** line **`superMacroCloseup probe`** with **`vendorKeyApplied=true`**. Optional **`-RequireSuperMacroPass`** exits non-zero on failure (CI/device automation). UW id defaults to **3** (**`-UltraWideCameraId`**). **Closed §5 — 2026-05-10:** Wi‑Fi adb **`192.168.1.2:34365`** (**`8bf09993`**); **`hfr-runs/adb_preview_validate_20260510_061124/`** (**`super_macro_gate.json`** **`pass: true`**; matched line **`vendorKeyApplied=true`** **`path=sessionParameters`** **`type=byteArr1`**).

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

**Milestone 5 gate:** Toolchain PASSED; Phase 2 [ADB] rows `[x]` with §5 evidence; long-run **optional** `- [ADB] 15-minute session` may complete here or in Milestone 7 (stress).

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
- [x] [MIXED] **ADB Calibrate smoke** — **`pns_adb_preview_validate.ps1 -Milestone6Pack`** / **`pns_milestone6_gate.ps1`**: **`m6_calibrate_smoke`** (**`calibrateSmoke`**); **`m6_preview_calibrate_grab_smoke`** (**`calibrateLiveGrabOk`**). Evidence: **`milestone6_gate.json`** **`pass: true`** + **`PROBE_BUILD_PLAN.md` Section 5** row (**2026-05-10**, OnePlus **CPH2655** / adb **`192.168.1.2:34365`**).
- [ ] [HUMAN] Real-world chart metrics (dE2000, MTF50) — physical chart session — **Milestone H.2** (not a Sprint 6.2 deliverable).

### Sprint 6.3 — LUT V&V (device)

- [x] [ADB] Live-preview LUT toggle **FPS budget** (≤5% drop on 60fps path) — **`pns_preview_m6_fps_lut_probe`** → **`m6 lutFpsBaseline` / `m6 lutFpsWithLut` / `m6 lutFpsBudget`**; **`milestone6_gate.json`** **`lutFpsBudgetOk`**. Scenario **`m6_lut_fps_probe`** in **`-Milestone6Pack`**. Evidence: **`PROBE_BUILD_PLAN.md` §5 — 2026-05-10** (**`milestone6_gate.json`** under **`hfr-runs/adb_preview_validate_milestone6_latest/`**, Wi‑Fi adb **`192.168.1.2:34365`** / **`8bf09993`**).
- **Follow-ups (encoding / export backlog — not Sprint 6.3 gates):** Video + still LUT at encode time + sidecars (`STORAGE_STRATEGY.md`); imported `.cube` byte-identical round-trip on device — track under **`COLOR_PIPELINE.md`** / Milestone 7+ until hooks land.

**Milestone 6 gate:** Host `pns_verify_toolchain.ps1 -RunTests` PASSED; device **`scripts/pns_milestone6_gate.ps1`** (or **`pns_adb_preview_validate.ps1 -Milestone6Pack`**) with **`scripts/pns_adb_device.env`** → **`milestone6_gate.json`** **`pass: true`**; append **`PROBE_BUILD_PLAN.md` §5** when a physical device is used. Sprint **6.3** LUT FPS **[ADB]** row is closed with §5 evidence above; encoding/export backlog bullets do not block this gate.

---

## Milestone 7 — Robustness, performance & storage

**Objective:** Failure matrix, profiling, backpressure, storage validation, optional root enhancements.

### Sprint 7.1 — Performance & profiling

- [ ] [MIXED] Perfetto / jank baselines per `PERFORMANCE_BUDGETS.md` + `PerfBudget.kt`.
- [x] [HOST] `pns_hfr_autorun.ps1 -PerfReport` stub (extend when budgets are populated from device).

### Sprint 7.2 — Failure matrix (ADB)

- [x] [HOST] **`scripts/pns_failure_matrix_smoke.ps1`** — automated smoke: **`fm_preview_granted`** + **`fm_preview_revoked`** (CAMERA revoked then cold-start preview); **`failure_matrix_smoke.json`** asserts no **`FATAL EXCEPTION` / `Process: dev.pointandshoot`** block in captured logcat. **`ERROR_CAMERA_IN_USE`**, orientation torture, thermal long-run — **manual / stretch** (documented in **`FAILURE_MATRIX.md`**); append §5 when a device run records JSON **`pass: true`**.

### Sprint 7.3 — Pipeline & storage

- [ ] [MIXED] Backpressure / queue bounds under burst + RAW (`CAPTURE_ARCHITECTURE.md`).
- [ ] [MIXED] MediaStore/gallery validation; **[HOST][HUMAN]** desktop opens → Milestone H.

### Sprint 7.4 — Feature gating UX

- [x] [HOST] **`CapabilityGate`** fed by **`HardwareCapsSnapshot`** / **`HardwareCaps`**; **`CapabilityGateBridge`** formats gate lines; **Developer menu** (probe) + **Settings > HUD** show per-feature **`ok` / `off`** with truncated disabled reasons when camera permission is granted (HUD shows permission hint when denied).

### Sprint 7.5 — Root-only enhancements (optional fleet)

- [ ] [ROOT] Items in `RootSettingsScreen` — vendor `setprop`, cameraserver restart, governor pin, thermal sysfs, logcat pull, vendor-key probe, resolution override, backlight read — each **[x]** only with §5 + device notes (Magisk/KernelSU as available).

**Milestone 7 gate:** Toolchain PASSED; failure-matrix rows closed or explicitly waived with issue links; storage ADB checks recorded.

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

## Milestone H — Human & publication (**all human-dependent work**)

**Objective:** Subjective validation, account ownership, and release authority. **Agents must not mark these `[x]` without human sign-off** (screenshots, portal URLs, or written approval recorded in §5).

### Sprint H.1 — Desktop & studio verification

- [ ] [HOST][HUMAN] Pull DNG/AVIF/JXL from `DCIM/Point & Shoot/…` and open in desktop RAW / color tools (Standard Pro + Ultra-Max samples).
- [ ] [HOST][HUMAN] Passport **Creative Enhancement** reference values transcribed from X-Rite datasheets (IP-sensitive).
- [ ] [MIXED] Bracket set **desktop regroup** sanity.

### Sprint H.2 — Real-world calibration charts

- [ ] [HUMAN] Capture physical ColorChecker / calibration charts (controlled illuminant).
- [ ] [MIXED] dE2000 / MTF50 field gates — human-operated capture sessions.

### Sprint H.3 — Git hosting & mirrors

- [ ] [HUMAN] Create/configure GitLab project and mirroring (or add GitLab remote).
- [ ] [CI][HUMAN] GitLab CI variables for signing (if used).

### Sprint H.4 — Signing & supply chain

- [ ] [HUMAN] GitHub Actions secrets: `ANDROID_KEYSTORE_BASE64`, passwords, alias (`build-signed.yml`).
- [ ] [MIXED] CI **`assembleRelease`** with real key + `apksigner verify` + **[ADB]** install smoke — evidence in §5.

### Sprint H.5 — Release & community

- [ ] [HUMAN] Tag release, upload artifacts, store listings, comms.
- [ ] [HUMAN] Post-release monitoring checklist.

### Sprint H.6 — Subjective UX

- [ ] [ADB] Reverse-landscape / Eye-AF alignment **photo sign-off** (if not closed earlier).
- [ ] [HUMAN] Aesthetic review of HUD/LUT defaults.

**Milestone H gate:** Owner-approved checklist recorded (§5 or project wiki); no remaining **[HUMAN]** checkbox unjustified.

---

## Appendix A — Verification protocol (abbreviated)

**Before any `[ ]` → `[x]`:**

1. `pns_verify_toolchain.ps1 -RunTests` → PASSED (host code/scripts).
2. `ReadLints` clean on touched Kotlin.
3. Paths/symbols claimed in the bullet exist in repo.
4. Tests: `TEST-*.xml` shows `failures="0" errors="0"` for claimed classes.
5. `CHANGELOG.md` (Unreleased) for user-visible changes; **§5 row** in `PROBE_BUILD_PLAN.md` for gates/device/audit.
6. **[ADB]/[ROOT]:** device evidence in §5; never close on host-only scaffolding for device-gated bullets.
7. **Regression:** If toolchain fails after a tick, revert tick or fix immediately.

**Partial completion:** Parent **[MIXED]** stays `[ ]` until **every** child venue is satisfied (see legacy protocol in git history if needed).

---

## Appendix B — Baseline already shipped (high level)

**Do not duplicate execution** — this appendix prevents scope confusion. Details live in code + prior §5 rows.

| Area | Status |
|------|--------|
| FOSS gates + CI toolchain | Shipped |
| Probe JSON + encoder summaries + About hydration | Shipped |
| Dodge profile + crop geometry tests | Shipped |
| Pro HUD + dial + HUD settings + haptics/tally | Shipped |
| Host AVIF/HEIF/ISOBMFF building blocks, LUT pipeline tests | Shipped |
| Calibration math, `.cube` export, DNG sidecars (library path) | Shipped |
| Diagnostics mode, `FAILURE_MATRIX.md`, `CAPTURE_ARCHITECTURE.md` | Shipped |
| Root **UI** + fallbacks (non-root device validated) | Shipped |

For line-by-line historical checkboxes, use `git log -- BUILD_PLAN.md` and `PROBE_BUILD_PLAN.md` §5.

---

## Appendix C — Agent quick grep (unchanged intent)

| Need | Pattern |
|------|---------|
| Open host work | `^- \[ \] \[HOST\]` |
| Open device work | `^- \[ \] \[ADB\]` |
| Open human work | `^- \[ \] \[HUMAN\]` |
| Open mixed | `^- \[ \] \[MIXED\]` |

---

## Document control

- **Version:** Milestone/sprint rewrite (2026). Replaces numbered §0–§10 narrative checklist; technical truth remains in source + `PROBE_BUILD_PLAN.md`.
- **Owner:** Project maintainer approves Milestone H closures.
