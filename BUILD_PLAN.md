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
6. **UI work gate (mandatory — blocks completion of any UI task):** If the change touches **user-visible surfaces** (Compose screens/layouts, preview/chrome/readout, themes, `res/` drawables or dimensions, navigation, dialogs, `MainActivity` window flags), the task is **not done** until all of the following succeed on a **physical device** (emulator only if no USB device is available — say so in §5):
   1. **`.\gradlew.bat :app:assembleDebug`** (or `assembleDebug` as part of your workflow) — zero compile errors.
   2. **Install + launch** the debug APK on the device — use **`scripts/pns_sideload_and_launch.ps1`** (build + install + grant + `am start` preview) or equivalent **`adb install -r -t`** + **`am start -n dev.pointandshoot/.MainActivity --es pns_screen preview`**. Prefer **`scripts/pns_adb_device.env`** for `PNS_ADB_SERIAL` when `-Serial` is omitted.
   3. **Verify** on the glass that the UI matches the intent (layout, spacing, rotation behavior, no obvious regressions).
   4. **Capture proof:** **`scripts/pns_device_screencap.ps1 -OutPath docs\screenshots\ui_<area>_YYYYMMDD.png`** (raw PNG; do not corrupt via naive PowerShell piping — see script header). Record the path + device serial in **`PROBE_BUILD_PLAN.md` §5** or the PR body.
   Pure refactors with **no** visual or behavioral UI change (rename-only, dead-code removal) may skip the device step with a one-line justification. **Agents:** treat this gate as **non-optional** for normal UI work; do not declare UI changes “complete” from IDE previews or chat screenshots alone.
7. **JAVA_HOME (Windows):** use `C:\Program Files\Android\Android Studio\jbr` when Gradle fails with “no java”.
8. **ADB:** prefer `%LOCALAPPDATA%\Android\Sdk\platform-tools` first on `PATH` (pair/connect vs legacy adb).
9. **ADB serial:** optional **`scripts/pns_adb_device.env`** (copy **`scripts/pns_adb_device.env.example`**) sets **`PNS_ADB_SERIAL`** for **`pns_sideload_and_launch.ps1`**, **`pns_adb_preview_validate.ps1`**, **`pns_milestone6_gate.ps1`**, **`pns_device_screencap.ps1`** when `-Serial` is omitted; Wi‑Fi **`host:port`** values trigger **`adb connect`** where scripts support it. Use **`-Serial`** on **`pns_milestone6_gate.ps1`** / **`pns_adb_preview_validate.ps1`** to override env when that endpoint is offline.

**Human work:** Only **Milestone H — Human & publication** contains tasks that require a person (accounts, subjective judgment, physical charts, desktop apps). Agents prepare artifacts; humans close **[HUMAN]** items.

---

### Global toolkit (used in gates)

| Tool | Role |
|------|------|
| `scripts/pns_verify_toolchain.ps1 -RunTests` | Host gate: assembleDebug, unit tests, FOSS dep-audit, license/SBOM, script UTF-8 |
| `scripts/pns_hfr_autorun.ps1` | Device probe automation (`-RunProbeSmoke`, `-RunFullSuite`, …); **`-PerfReport`** → `perf-runs/perf_*.md` (cold start `am start -W`, `dumpsys meminfo`, `PNS.Reader` drop tail; optional **`-Serial`** / `pns_adb_device.env`); Perfetto / `gfxinfo` protocol → **`PERFORMANCE_BUDGETS.md`** § *Perfetto & frame jank* |
| `scripts/pns_adb_preview_validate.ps1` | Scripted preview / RAW / BKT + log capture; **`-ChromeUxPack`** → **`chrome_ux_smoke.json`**; else **7 s** settle → **`mediastore_probe.json`** + DCIM **`ls`** + MediaStore tail |
| `scripts/pns_milestone6_gate.ps1` | Milestone 6 pack: `assembleDebug` + `-Milestone6Pack` (DNG 50708, LUT FPS probe, Calibrate + GLES smoke) → **`milestone6_gate.json`**; optional **`-Serial`** overrides **`pns_adb_device.env`** when Wi‑Fi adb is offline |
| `scripts/pns_failure_matrix_smoke.ps1` | Milestone 7 smoke: preview cold start + CAMERA revoked preview → **`failure_matrix_smoke.json`** (no AndroidRuntime fatal for `dev.pointandshoot`); optional **`-AppendSection5`** / **`-ProbePlan`** after **`pass: true`** |
| `scripts/pns_root_capability_adb.ps1` | **ADB transport root** probe: optional **`adb root`**, **`adb shell id`** / best-effort **`su -c id`**, **`root_capability_adb.json`** (**`pns.root_capability_adb.v1`**) for **`pns_probe_append_section5.ps1`** |
| `scripts/pns_chrome_ux_gate.ps1` | Milestone 9 pack: toolchain + optional device **`PNS.ChromeUx`** checks (**`seedOk`** … **`grid7=`**, **`modeDialPopout=`**, **`readoutCapture=`**, **`selfTimerSec=`**) → **`chrome_ux_gate.json`** |
| `scripts/pns_automation_smoke.ps1` | Fleet: verify toolchain → chrome UX gate → failure-matrix smoke → optional **`-ChromeUxPack`**; optional **`-RunFullAdbPreviewValidate`** + **`-RequireMediaStoreDcim`**; **`-AppendSection5`** (+ **`-ProbePlan`**) appends **§5** when the smoke passes (**`chrome_ux_gate`** only if adb was authorized); **`mediastore_probe`** §5 uses **`-PassOnly`** only when **`dcimHasPnsCapture`** is true so empty DCIM still logs; **`-TryAdbRoot`** → **`automation_smoke.json`** |
| `scripts/pns_device_screencap.ps1` | **`adb exec-out screencap -p`** to PNG via **`Process` stdout stream** (avoids broken PS pipelines); use for **`BUILD_PLAN`** UI verification artifacts |
| `scripts/pns_pull_dcim_captures.ps1` | **`adb pull`** **`/sdcard/DCIM/Point & Shoot`** to **`hfr-runs/pull_dcim_*`** (or **`-OutDir`**); supports **`pns_adb_device.env`** / **`-Serial`** / Wi‑Fi **`adb connect`** — desktop half of Sprint **7.3** / **Milestone H.1** |
| `scripts/pns_sideload_and_launch.ps1` | **`assembleDebug`** + **`adb install -r -t`** + runtime grants + **`am start`** preview (`--es pns_screen preview`); primary fast path for **UI work gate** (“How agents must execute”, item 6) |
| `scripts/pns_adb_device.env` (gitignored; copy `.example`) | Default **`PNS_ADB_SERIAL`** for scripts when `-Serial` omitted (Wi‑Fi **`ip:port`** OK) |
| `.github/workflows/toolchain-verify.yml` | CI mirror of toolchain |

**Known limitation:** `:app:lintDebug` is **not** in the toolchain gate (AGP + Compose lint API mismatch). Track via compile + unit tests + IDE `ReadLints`.

---

### Preview finder acceptance (device proof — do not guess)

These behaviors are **easy to break with layout math mistakes**. Any change to `PreviewMainViewport`, `TexturePreviewFit`, `effectivePreviewStaticRotationDeg`, `BackCameraRoleResolver`, or the 7×7 focal row **must** close the checklist below with evidence in `PROBE_BUILD_PLAN.md` §5 (timestamp + device serial + what was verified).

#### UI change verification (screenshots mandatory — implements item 6 UI work gate)

This checklist is the **detailed acceptance criteria** for preview/chrome work; it **must** be satisfied together with **How agents must execute → item 6** (build → sideload → on-device verify → `pns_device_screencap` → §5 or PR note). Do not treat UI as shipped until both match.

Whenever **Compose layout**, **preview chrome** (rails, readout, bottom tray, shutters, mode dial, 7×7 grid), **preview rotation**, or **insets** change:

1. **Do not merge or declare complete from screenshots in chat alone** — verify on a **physical device** (this host cannot judge color, distortion, or gravity alignment).
2. Capture **before** and **after** PNGs on device using **`scripts/pns_device_screencap.ps1`** (streams raw bytes to disk on Windows) or USB **`adb exec-out screencap -p > docs/screenshots/ui_<area>_YYYYMMDD_before.png`** / **`…_after.png`** via **`cmd.exe` redirection** — do **not** pipe PNG bytes through PowerShell `Set-Content` without raw byte arrays (common corruption). Same lighting where possible; Android Studio capture also OK.
3. Store artifacts under **`docs/screenshots/`** (or `hfr-runs/` for scripted gates) and note paths in **`PROBE_BUILD_PLAN.md`** §5 or the PR description (serial + build / APK identity).
4. For **orientation / distortion / color**, use a **known chart** (e.g. DGK ColorChecker-style target): legend readable upright vs gravity; square targets stay square; compare to prior screenshot — **do not guess**.

| Item | Pass criterion (on-device) |
|------|---------------------------|
| **No side pillarbars** | In preview screen, live image **fills the finder width**; any crop is **top/bottom only** (center-crop), not black bars left/right from aspect-fit “contain”. |
| **No horizontal stretch** | Point the camera at a **square** calibration target (or square UI element); the square must stay **square** (uniform scale), not wider than tall. |
| **Preview locked on rotation** | Rotating the phone **does not** change static preview rotation automatically; only **Spin (preview)** changes buffer rotation. Finder does not jump between portrait/landscape. |
| **Tele focal presets** | With ≥3 rear cameras, tapping **73 / 85 / 150** selects the **tele** camera (check status line `cameraId=…` or mode-transition log); preview FOV changes. Resolution uses **BackCameraRoleResolver** (focal-length clustering), not hard-coded `"4"` only. |
| **Host regression** | `pns_verify_toolchain.ps1 -RunTests` exit 0; `TexturePreviewFitTest` + `PreviewLayoutOrientationTest` green. |

#### Screenshot verification queue (UI items — tick only with device PNG)

**Rule:** Do not change `- [ ]` to `- [x]` until **physical device** validation proves the item; optional PNG paths may be noted when captures exist locally (raster proof is **not** committed to this repo). Host rebuilds use Gradle logs, not this list.

**Host rebuild (2026-05-10):** `.\gradlew.bat :app:assembleDebug` → **PASSED**.

- [ ] **Immersive window** — Status + nav bars hidden (`enableEdgeToEdge` + `WindowInsetsControllerCompat`); transient swipe reveal only. **Evidence:** _pending_ (top/bottom bands still visible in latest capture — see note below).
- [x] **Live preview** — Camera stream visible in finder. **Evidence:** adb device validation (2026-05-10); raster PNG not in repo.
- [x] **Readout strip** — ISO, shutter, AWB / FPS, **`RAW`** or **`RAW+`**. **Evidence:** same session.
- [x] **Right rail + focal row** — mm chips **`14…150`** with selection highlight. **Evidence:** same session.
- [x] **7×7 grid** — Row **0** focal + rows **1–3** shortcuts + placeholders **4–6** + **Settings** at **`r6c6`**. **Evidence:** same session.
- [x] **Bottom tray** — Gallery thumb (when URI), dual shutters, mode letter FAB when HUD dial on. **Evidence:** same session.
- [ ] **Expand shortcut → modal** — Row **1** icon opens centered **`Dialog`**, not a strip under the grid. **Evidence:** _pending_
- [ ] **Mode menu** — FAB opens **`DropdownMenu`** listing **M/H/S/BKT**. **Evidence:** _pending_
- [ ] **Finder — no side pillarboxing** — Live image fills finder width (center-crop top/bottom only). **Evidence:** _pending_
- [ ] **Finder — uniform scale** — Square calibration target stays square. **Evidence:** _pending_
- [ ] **Spin / chart upright** — Printed chart matches **DGK 8.5×11** legend vs gravity. **Evidence:** _pending_
- [ ] **Tele presets** — **73 / 85 / 150** selects tele camera + visible FOV change. **Evidence:** _pending_

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
- [ ] [MIXED] **Gallery / desktop open** — still **[HOST][HUMAN]** (Milestone H): open indexed DNG/JPEG in OEM gallery + desktop tooling per **`STORAGE_STRATEGY.md`**; host staging: **`scripts/pns_pull_dcim_captures.ps1`** (**`adb pull`** **`/sdcard/DCIM/Point & Shoot`**). Close together with **Milestone H.1** desktop row when sign-off is recorded in §5.

### Sprint 7.4 — Feature gating UX

- [x] [HOST] **`CapabilityGate`** fed by **`HardwareCapsSnapshot`** / **`HardwareCaps`**; **`CapabilityGateBridge`** formats gate lines; **Developer menu** (probe) + **Settings > HUD** show per-feature **`ok` / `off`** with truncated disabled reasons when camera permission is granted (HUD shows permission hint when denied).

### Sprint 7.5 — Root-only enhancements (optional fleet)

- [x] [ADB] **`scripts/pns_root_capability_adb.ps1`** — USB adb **`adb root`** transport: **`adb shell id`** → **`uid=0(root)`**; writes **`root_capability_adb.json`** (**`schema`**: **`pns.root_capability_adb.v1`**). **Note:** **`adb shell su -c id`** may still fail on builds where only **adbd** is rooted (no Magisk **`/system/bin/su`** on PATH). §5 append via **`pns_probe_append_section5.ps1`**.
- [ ] [ROOT] Items in `RootSettingsScreen` — vendor `setprop`, cameraserver restart, governor pin, thermal sysfs, logcat pull, vendor-key probe, resolution override, backlight read — each **[x]** only with §5 + device notes when the **in-app privileged actions** exist beyond the catalog + **`su`** grant probe (**catalog ships today; destructive fleet ops not individually implemented**).

**Milestone 7 gate:** Toolchain PASSED; failure-matrix rows closed or explicitly waived with issue links; **storage** ADB checks (**`encode_lane_busy`**, reader backpressure gates, **DCIM `mediastore_probe`**) recorded; **ADB root transport** probe (**`pns_root_capability_adb.ps1`**) §5 when fleet uses **`adb root`**; gallery/desktop opens remain **Milestone H** where applicable.

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

### Sprint 9.8 — 7×7 grid layout + Settings `[6,6]`

- [x] [HOST] **`previewChromeGridSlots`** — row **0** focal mm chips unchanged; **row 1** cols **0–5**: expand shortcuts **Target FPS, Guides, Looks / LUT, Preview & keys, Spin (preview), Capture & tools**; **Settings** **`ExpandShortcut`** at **`(row=6,col=6)`**; additional quick-action rows **2–3** (see Sprint **9.9**).
- [x] [HOST] **`PNS.ChromeUx`** **`grid7=layout settingsAt=r6c6=true`** (+ **`quickActions=…`** list); **`PreviewReadoutStrip`** uses **`TransformOrigin(0.5f,0.5f)`** with **`uiRotationDeg`** (matches grid rotation pivot).
- [x] [HOST] **`pns_chrome_ux_gate.ps1`** — **`grid7Ok`**.

### Sprint 9.9 — Grid quick actions (LUT, flash, timer, histogram, …)

- [x] [HOST] **`ChromeGridSlotSpec`** — **`ExpandShortcut`** vs **`QuickAction`**; row **2**: **LUT** (cycle stills), **Flash** (stub), **Timer** (self-timer — Sprint **9.11**), **Histogram**, **Horizon level**, **Eye-AF overlay**, **Video tally**; row **3**: **Max brightness in preview**, **DND in preview**, **Tap preview to capture**, **Volume keys capture**.
- [x] [HOST] **`PNS.ChromeUx`** — **`grid7=… quickActions=lut,flash,timer,histogram,horizon,eyeAf,tally,bright,dnd,tap,volKeys`** (see log line in **`PreviewEngineScreen`**).

### Sprint 9.10 — Shooting-mode menu + **`RAW`/`RAW+`** readout badge

- [x] [HOST] When **`HudSettings.showCommandDial`**: bottom tray **`PreviewBottomCaptureTray`** shows a **48.dp** orange **FAB** with the current **`CommandDialMode.label`**; tap opens **`DropdownMenu`** for **M/H/S/BKT**; **`PNS.ChromeUx`** **`modeDialPopout=menuSelect`** on pick (legacy **`anchorVisible`/`expanded`/`skipped_no_dial`** may still appear from older HUD paths — gate accepts **`menuSelect`**).
- [x] [HOST] **`PreviewController.previewUsesJpegCompanion()`** (JPEG **`ImageReader`** active); readout strip suffix **`RAW`** / **`RAW+`** + **`readoutCapture=`** **`PNS.ChromeUx`** line.
- [x] [HOST] **`pns_chrome_ux_gate.ps1`** — **`modeDialPopoutOk`**, **`readoutCaptureOk`**.

### Sprint 9.11 — Self-timer (pref + grid + still paths)

- [x] [HOST] **`PreviewChromePreferences.selfTimerDelaySec`** (**0 / 3 / 5 / 10**), persisted; grid **Timer** icon cycles delay + toast + **`PNS.ChromeUx`** **`selfTimerSec=`**; icon **selected** when delay **> 0**.
- [x] [HOST] Still capture via volume-up (non-BKT), tap-to-shoot, bottom orange shutter, **Save DNG**: **`triggerStillCapture()`** — countdown overlay on finder, then existing **`onCaptureDng`** (**bracket / BKT** unchanged).
- [x] [ADB] **`--ei pns_preview_self_timer_sec`** (`EXTRA_PNS_PREVIEW_SELF_TIMER_SEC`) seeds **`PreviewChromePreferences.selfTimerDelaySec`** before **`PNS.ChromeUx`** **`selfTimerSec=`**; **`pns_chrome_ux_gate.ps1`** defaults **`-SelfTimerSec 3`** on device **`am start`** (allowed **0 / 3 / 5 / 10**).
- [x] [ADB] **`pns_adb_preview_validate.ps1 -ChromeUxPack`** — short **`m9_self_timer_adb_seed`** scenario + **`chrome_ux_smoke.json`** (**`selfTimerChromeUxOk`**, **`adbSelfTimerSeedOk`**); logcat tag filter includes **`PNS.ChromeUx`**.
- [x] [HOST] **`pns_chrome_ux_gate.ps1`** — **`selfTimerOk`** (**`selfTimerSec=`** on cold start).

**Follow-on sprints (open):** real flash AE wiring; zebras GLSL.

**Milestone 9 gate (current):** `pns_verify_toolchain.ps1 -RunTests` PASSED; with device: **`scripts/pns_chrome_ux_gate.ps1`** exit 0 and **`chrome_ux_gate.json`** **`pass: true`**; §5 row when a physical device is used. UI tweaks **also** require § **Preview finder acceptance → UI change verification (screenshots mandatory)** above.

---

## Milestone H — Human & publication (**all human-dependent work**)

**Objective:** Subjective validation, account ownership, and release authority. **Agents must not mark these `[x]` without human sign-off** (screenshots, portal URLs, or written approval recorded in §5).

### Sprint H.1 — Desktop & studio verification

- [ ] [HOST][HUMAN] Pull DNG/AVIF/JXL from `DCIM/Point & Shoot/…` and open in desktop RAW / color tools (Standard Pro + Ultra-Max samples). **Staging:** **`scripts/pns_pull_dcim_captures.ps1`** → then **`darktable` / `RawTherapee` / `djxl`** / AVIF viewer per **`STORAGE_STRATEGY.md`**.
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
