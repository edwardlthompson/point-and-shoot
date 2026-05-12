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
9. **ADB serial:** optional **`scripts/pns_adb_device.env`** (copy **`scripts/pns_adb_device.env.example`**) sets **`PNS_ADB_SERIAL`** to the USB serial from **`adb devices`** for **`pns_sideload_and_launch.ps1`**, **`pns_adb_preview_validate.ps1`**, **`pns_milestone6_gate.ps1`**, **`pns_device_screencap.ps1`** when **`-Serial`** is omitted. Use **`-Serial`** when more than one device is connected or you need to override the env file.
10. **Git after each milestone (agents):** When a **numbered milestone** (0–9, excluding H) is complete — all sprint checkboxes for that milestone are `[x]` and the **Milestone gate** for that milestone passes per items 3–5 above — **`git commit`** the closing changes with a message that names the milestone (for example `Milestone 7: storage and failure-matrix gates`) and **`git push`** to the branch’s upstream **before** starting work on the next milestone. Do not accumulate finished milestone work across long-lived local branches without pushing; humans and CI rely on the remote for review and bisect.

**Human work:** Only **Milestone H — Human & publication** contains tasks that require a person (accounts, subjective judgment, physical charts, desktop apps). Agents prepare artifacts; humans close **[HUMAN]** items.

---

### Global toolkit (used in gates)

| Tool | Role |
|------|------|
| `scripts/pns_verify_toolchain.ps1 -RunTests` | Host gate: assembleDebug, unit tests, FOSS dep-audit, license/SBOM, script UTF-8 |
| `scripts/pns_hfr_autorun.ps1` | Device probe automation (`-RunProbeSmoke`, `-RunFullSuite`, …); **`-PerfReport`** → `perf-runs/perf_*.md` (cold start `am start -W`, `dumpsys meminfo`, `PNS.Reader` drop tail; optional **`-Serial`** / `pns_adb_device.env`); Perfetto / `gfxinfo` protocol → **`PERFORMANCE_BUDGETS.md`** § *Perfetto & frame jank* |
| `scripts/pns_adb_preview_validate.ps1` | Scripted preview / RAW / BKT + log capture; **`-ChromeUxPack`** → **`chrome_ux_smoke.json`**; else **7 s** settle → **`mediastore_probe.json`** + DCIM **`ls`** + MediaStore tail |
| `scripts/pns_milestone6_gate.ps1` | Milestone 6 pack: `assembleDebug` + `-Milestone6Pack` (DNG 50708, LUT FPS probe, Calibrate + GLES smoke) → **`milestone6_gate.json`**; optional **`-Serial`** selects the device when **`pns_adb_device.env`** is unset or you need to override **`PNS_ADB_SERIAL`** |
| `scripts/pns_failure_matrix_smoke.ps1` | Milestone 7 smoke: preview cold start + CAMERA revoked preview → **`failure_matrix_smoke.json`** (no AndroidRuntime fatal for `dev.pointandshoot`); optional **`-AppendSection5`** / **`-ProbePlan`** after **`pass: true`** |
| `scripts/pns_root_capability_adb.ps1` | **ADB transport root** probe: optional **`adb root`**, **`adb shell id`** / best-effort **`su -c id`**, **`root_capability_adb.json`** (**`pns.root_capability_adb.v1`**) for **`pns_probe_append_section5.ps1`** |
| `scripts/pns_chrome_ux_gate.ps1` | Milestone 9 pack: toolchain + optional device **`PNS.ChromeUx`** checks (**`seedOk`** … **`grid7=`**, **`modeDialPopout=`**, **`readoutCapture=`**, **`selfTimerSec=`**) → **`chrome_ux_gate.json`** |
| `scripts/pns_automation_smoke.ps1` | Fleet: verify toolchain → chrome UX gate → failure-matrix smoke → optional **`-ChromeUxPack`**; optional **`-RunFullAdbPreviewValidate`** + **`-RequireMediaStoreDcim`**; **`-AppendSection5`** (+ **`-ProbePlan`**) appends **§5** when the smoke passes (**`chrome_ux_gate`** only if adb was authorized); **`mediastore_probe`** §5 uses **`-PassOnly`** only when **`dcimHasPnsCapture`** is true so empty DCIM still logs; **`-TryAdbRoot`** → **`automation_smoke.json`** |
| `scripts/pns_device_screencap.ps1` | **`adb exec-out screencap -p`** to PNG via **`Process` stdout stream** (avoids broken PS pipelines); use for **`BUILD_PLAN`** UI verification artifacts |
| `scripts/pns_pull_dcim_captures.ps1` | **`adb pull`** **`/sdcard/DCIM/Point & Shoot`** to **`hfr-runs/pull_dcim_*`** (or **`-OutDir`**); supports **`pns_adb_device.env`** / **`-Serial`** — desktop half of Sprint **7.3** / **Milestone H.1** |
| `scripts/pns_sideload_and_launch.ps1` | **`assembleDebug`** + **`adb install -r -t`** + runtime grants + **`am start`** preview (`--es pns_screen preview`); primary fast path for **UI work gate** (“How agents must execute”, item 6) |
| `scripts/pns_adb_device.env` (gitignored; copy `.example`) | Default **`PNS_ADB_SERIAL`** (USB serial) for scripts when **`-Serial`** omitted |
| `.github/workflows/toolchain-verify.yml` | CI mirror of toolchain |

**Lint / static analysis:** `pns_verify_toolchain.ps1 -RunTests` runs `:app:detekt`, `:app:lintDebug`, and `:app:testDebugUnitTest`. Detekt uses `config/detekt/detekt.yml` plus `config/detekt/baseline.xml` (regenerate with `:app:detektBaseline` when intentionally bulk-fixing legacy debt). Android Lint uses `app/lint-baseline.xml` (regenerate with `:app:updateLintBaseline` after fixing or accepting new findings). AGP is **8.8.2** on **Gradle 8.10.2**; release builds use **R8** (`isMinifyEnabled` + `isShrinkResources`) with `app/proguard-rules.pro` (keep **UTF-8** without BOM).

### Performance & responsiveness backlog (2026 audit)

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

### Follow-on recommendations (performance, evidence, and maintenance)

Ongoing practices beyond the closed rows above; align evidence with **`PERFORMANCE_BUDGETS.md`** and **`PROBE_BUILD_PLAN.md`** section 5 when device-backed.

| Area | Recommendation |
|------|----------------|
| **Cold-start evidence** | Pair **`adb shell am start -W -n dev.pointandshoot/.MainActivity`** (**`TotalTime`**) with **PSS** via **`adb shell dumpsys meminfo dev.pointandshoot`**. Use **`scripts/pns_cold_start_capture.ps1`** (wraps **`pns_hfr_autorun.ps1 -PerfReport`**) or **`-PerfReport`** directly; writes **`perf-runs/perf_<stamp>.md`**. |
| **Release prep** | At least once per **release prep**, repeat **`am start -W`** on an **installed release** APK that includes merged **baseline / startup** profiles and **R8** (`assembleRelease` / Play-equivalent), not debug-only, and record **TotalTime** (+ optional PSS) in **`perf-runs/`** or **PROBE_BUILD_PLAN.md** section 5. |
| **ADB stability** | Use **one** **`adb`** on **`PATH`**, same build as **Android Studio** / **`%LOCALAPPDATA%\Android\Sdk\platform-tools`** (see *How agents must execute* item **8**), to avoid **client/server version mismatch** daemon restarts that flake **`mergeReleaseBaselineProfile`**, Macrobenchmark, and other **connected** Gradle tasks. |
| **Baseline profile workflow** | Treat **`:app:generateBaselineProfile`** as an **explicit** step (**`scripts/pns_baseline_profile_generate.ps1`**, stable USB). Do **not** assume random CI jobs run profile generation without a **device lab** or **scheduled** hardware job. |
| **First frame “ready to shoot”** | **Shipped:** `PnsStartupTrace` + `PnsApplication` + preview **`updatePreviewMetadata`** gate (FPS, sensor readout, AE/AF stable). **`pns_hfr_autorun.ps1 -PerfReport`** parses **`pns.firstFrameReady`** from logcat tail (<= **1200 ms** budget). |
| **Compose jank** | If preview/chrome still stutters after structural fixes (e.g. stable **`cameraIds`**), capture a **Compose layout / system trace** on the preview route before adding speculative **`derivedStateOf`** or broad refactors. |
| **Baseline profile diffs** | When **`baseline-prof.txt`** / **`startup-prof.txt`** churn heavily, regenerate on a **fixed reference device class** (manufacturer + **Android / API level**) and note **device class + API** in the PR so reviewers can interpret large diffs. |
| **Merge hygiene** | **CI:** `.github/workflows/toolchain-verify.yml` runs **`scripts/pns_verify_toolchain.ps1 -RunTests`**. Locally, keep the same before merges (*How agents must execute* item **5**); do not skip Detekt, **lintDebug**, or unit tests unless explicitly waived with rationale. |

### UX backlog — feedback, onboarding, accessibility (**main preview chrome locked**)

**Policy:** Portrait **preview engine chrome** (finder vs rail flex, 7×7 grid geometry, readout strip layout, section dividers, tile positions, spacing, colors, and locked chip styling) stays **unchanged** unless the maintainer explicitly unlocks it. Canonical references: **`docs/preview-chrome-layout-style-guide.md`**, **`.cursor/rules/preview-chrome-ui-lock.mdc`**. All items below target **messaging, feedback channels, copy, accessibility, engineering surfaces, and non-layout behaviors** — not relayout of the approved preview column.

**Automation note (Compose / system trace):** Use **`scripts/pns_compose_layout_trace_capture.ps1`** (warm preview + Perfetto **`gfx view sched wm input`**) when investigating jank or layout invalidation; keep artifacts under **`perf-runs/`** and reference them in **`PROBE_BUILD_PLAN.md`** §5 when closing performance-adjacent UX tasks.

Implementation backlog (tick in PRs / §5; order is suggested, not strict):

- [x] **[HOST]** Prefer **Material 3 `SnackbarHost`** (optional **Retry** / **Open settings** actions) for **recurring capture and calibration errors** instead of **Toast-only** feedback; keep messages **non-blocking** and do **not** introduce new chrome **bands** or resize locked preview slots. **Shipped (partial):** root **`SnackbarHost`** + **`LocalPnsSnackbarHostState`** in **`MainActivity`**; **`PreviewEngineScreen`** routes geotag-clear, DND policy hint, DNG/bracket failures, volume-key / FPS / bottom-tray hints, and immersive gesture tip through snackbars. **Retry / Copy raw error** actions remain backlog.
- [x] **[HOST]** **Probe hub export** (`CreateDocument` path in `CameraCapabilitiesProbe`): on failure, show a **user-visible** message (snackbar or inline) — not **`Log.e` only** — so export taps never fail silently. **Shipped:** **Toast** on open/write failure (short copy); **`Log.e`** retained for diagnostics.
- [x] **[HOST]** **Humanize error strings**: short user-facing line (“Could not save burst — storage may be full”) plus optional **Details** / **Copy** for raw `Throwable` text; avoid naked OEM/HAL **`e.message`** in user-visible surfaces. **Shipped (partial):** **`PnsUserFacingErrors`** + **`PnsUserFacingErrorsTest`** for DNG/bracket-style failures; **`ACTION_IMAGE_CAPTURE`** return path uses the same classifier instead of raw **`e.message`** toast. **Details / Copy** UI still backlog.
- [x] **[MIXED]** **First-run (`WelcomePermissionsScreen`)**: optional **persistence for skipping optional steps**; if the user **denies camera**, show a **one-line recovery** with **link to app settings** (and/or `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`). **Shipped:** required-step **Open app settings** + camera-specific recovery copy; **Skip (optional)** on microphone and location steps (advances without grant). **Persisted skip** (remember across app restarts) not implemented.
- [x] **[HOST]** **Geotagging**: when **`saveLocationWithMedia` is cleared** because fine location was revoked, surface a **one-time snackbar or readout hint** (“Location off — new photos won’t be geotagged”) so the behavior is not silent. **Shipped:** **Snackbar** in **`PreviewEngineScreen`** when the auto-clear **`LaunchedEffect`** runs (same wording; ASCII apostrophe in code).
- [ ] **[MIXED]** **Long-running capture work** (DNG, bracket, calibration frame grab, etc.): add **explicit progress** (indeterminate or stepped) using **existing modal or readout text** patterns — **no** new persistent chrome regions beyond what the style guide already allows.
- [x] **[HOST]** **Flash quick action**: surface **short-press vs long-press** affordance via **tooltip**, **long-press label**, or **one-time coach mark** (backed by prefs) — **no** new grid slots or tile size changes. **Shipped (partial):** grid slot copy documents tap vs long-press; **TalkBack** **`contentDescription`** includes **current flash mode**; full **tooltip / coach-mark** prefs still backlog.
- [x] **[HOST]** **Debug / probe hub** (`CameraCapabilitiesProbe` routes): reduce **wall-of-equal entries** — **group** destinations (e.g. Diagnostics vs Camera2 matrices vs Media) and/or add **recents / favorites** without changing preview-route chrome. **Shipped:** **`DebugMenuScreen`** already uses titled **`DebugSection`** blocks; this pass adds **Open app settings** on the permission banner + **semantic labels** on hub rows. **Recents / favorites** remain backlog.
- [x] **[HOST]** **Navigation clarity**: document and align **ADB `startAuto` flows that `finish()` the activity** vs **in-app `onBack` stacks** so humans and automation share the same mental model (short doc section + code comments where intents are parsed).
- [x] **[MIXED]** **TalkBack / accessibility**: audit **`contentDescription`** (and state labels) on **7×7 quick actions** and icon rails so toggles announce **meaning + on/off** (histogram, flash mode, DND, etc.) — **no** geometry changes. **Shipped (partial):** quick tiles compute **on/off**, **timer seconds**, and **flash mode** in **`contentDescription`**; engineering hub rows expose a combined **contentDescription**. Further rail/icon audit remains open.
- [x] **[MIXED]** **Immersive system bars**: **one-time first-run tip** (“Swipe from screen edge for Back / Home”) after **`MainActivity`** immersive setup — store “seen” in prefs; do not alter window flags contract without device re-validation. **Shipped:** **`PnsUiHintsStore`** + snackbar on first non-automation preview session (**`pns_ui_hints`** in backup allow-list).
- [x] **[HOST]** **Calibrate screen** and **LUT importer**: avoid **duplicate Toast + inline status** for the same event; pick a **single primary** channel for success and reserve Toast/snackbar for **hard failures** only. **Shipped:** **inline strip / result card only** — Toasts removed from **`CalibrateScreen`** / **`LutImporterScreen`** success and error paths that already update inline text.
- [x] **[HOST]** **Gallery / “open file”**: when **no app** can open the asset, offer **Share** or **“Open with…”** (`ACTION_SEND` / resolver) instead of a dead-end Toast only. **Shipped:** **`openMediaWithSystemResolver`** retries **`ACTION_VIEW`** with **`*/*`**, then **`ACTION_SEND`** **`createChooser`**; Toast only if all paths fail.

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

**Host rebuild (2026-05-11):** `.\gradlew.bat :app:assembleDebug` + `:app:assembleRelease` + `:app:lintDebug` → **PASSED** (lint + detekt baselines committed).

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

**Objective:** Stable Camera2 preview/capture, RAW→DNG, bracketing, metering/AF features validated on device; NDK encode path completed when scheduled. **Sprint 4.4** tracks Camera2 contract/metadata upgrades aligned with **`docs/CAMERA2_KEYS_AND_APIS_REFERENCE.md`** (flash / ZSL / triggers / antibanding land there or in linked preview still paths).

### Sprint 4.1 — Host-complete capture path (remaining encoder bodies)

- [x] [HOST] High-speed preview session, RAW12 `Dng12Saver`, `CaptureHaptics`, JNI shell `libpns_native.so`, `NativeEncoders` / `EncoderRoute`.
- [x] [HOST] **NDK encode bodies:** libavif (SVT-AV1 encode) + libjxl via CMake FetchContent; JNI bodies in `native/pns_native.cpp` (`BUILD_PLAN` / `NDK_PLAN`).

### Sprint 4.2 — Advanced metering & AF (**ADB closes parents**)

**Parents:** all **[ADB]** children below **`[x]`** with **`PROBE_BUILD_PLAN.md` §5 — 2026-05-10** (`adb_preview_validate_20260510_021941`, device **`8bf09993`**).

- [x] [ADB] **Highlight metering (dial H)** — YUV histogram → **`HighlightMeter`** AE comp applied; logs **`PNS.AdbValidation`** **`highlightMeter ev=… aeComp=… dial=H`** (≥3.5s throttle) in **`logcat_highlight_dial_H.txt`** same folder.
- [x] [ADB] **Eye-AF** — **`eyeAf faceDetectMode=`** + **`availableModes=`** logged once per session (reference HW: **SIMPLE** only — no **FULL** in list); **`eyeAf statisticsSample`** when **`STATISTICS_FACES`** non-empty. Multi-distance / lighting / **FULL** when advertised → **Milestone H** photo matrix.
- [x] [ADB] **3D tracking** — **`tracker statisticsPipeline active`** proves **`TrackerState`** wired to metadata; **`tracker lockedIds=…`** on lock-set delta when faces appear. Intentional dropout / re-acquire torture → **Milestone H**.
- [x] [MIXED] **Bracketing BKT** — **[ADB]** **`captureBracketBurst pattern=Three ok=true`** + three **`pns_*_bkt?of3*.dng`** writes in **`logcat_bracket_bkt3.txt`**; **[HOST][HUMAN]** bracket desktop regroup — **Milestone H**.

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
- [ ] [MIXED] **Stabilization** — `CONTROL_VIDEO_STABILIZATION_MODE` and/or `LENS_OPTICAL_STABILIZATION_MODE` where characteristics allow; policy tied to focal / FPS / user pref without breaking frozen preview chrome layout.
- [x] [HOST] **JPEG request metadata** — **`PreviewStillCaptureHints`**: `JPEG_ORIENTATION` (degrees via **`RawCaptureSupport.orientationClockwiseDegForDng`**) + optional **`JPEG_GPS_LOCATION`** when embed-location pref is on and keys are advertised; single RAW still + bracket still.
- [ ] [MIXED] **Logical multi-camera readout** — surface `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID` (and optional `getPhysicalCameraResults`) in readout or diagnostics when the logical camera is in use.
- [ ] [HOST] **Session defaults** — extend `SessionConfiguration.setSessionParameters` usage beyond OEM macro for stable session-wide defaults (e.g. antibanding) where OEM-safe.
- [x] [HOST] **Richer capture metadata (JPEG USER_COMMENT)** — **`StillCaptureMetadata.fillExifFields`**: appends **`LENS_FOCUS_DISTANCE`** (FD), **`LENS_STATE`**, **`CONTROL_AF_STATE`**, **`SENSOR_ROLLING_SHUTTER_SKEW`** to **`TAG_USER_COMMENT`** when present on **`TotalCaptureResult`** (DNG **`ExifInterface`** pass + JPEG companion). TIFF IFD rational patches / full DNG sidecar dump remain backlog.
- [ ] [MIXED] **`CONTROL_POST_RAW_SENSITIVITY_BOOST`** — optional policy when advertised and compatible with highlight / manual readout modes.

**Larger features (schedule after improvement row is mostly closed or when product pulls forward):**

- [ ] [MIXED] **Camera extensions** — `CameraExtensionCharacteristics` / `createExtensionSession` for vendor night / HDR extension modes (`EXTENSION_*` results in reference doc).
- [ ] [MIXED] **HDR / 10-bit / color space on live preview** — promote probed `REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES` (and color space profiles) to **`OutputConfiguration.setDynamicRangeProfile`** on the main preview session when validated (`SessionConfigurationCompat`, `HdrDcgRuntimeProbeScreen` evidence patterns).
- [ ] [MIXED] **`CONTROL_AUTOFRAMING`** — when `CONTROL_AUTOFRAMING_AVAILABLE`; distinct from ML Kit face track.
- [ ] [MIXED] **Reprocessing / input surface** — `createReprocessCaptureRequest`, ZSL-style templates, `REPROCESS_EFFECTIVE_EXPOSURE_FACTOR` where pipeline supports it.
- [ ] [HOST] **Stream use cases** — `SCALER_AVAILABLE_STREAM_USE_CASES` + `OutputConfiguration.setStreamUseCase` for preview vs still optimizer hints.
- [ ] [MIXED] **Torch / flash strength** — `CameraManager.turnOnTorchWithStrengthLevel` / `FLASH_STRENGTH_LEVEL` / `FLASH_INFO_*_STRENGTH_*` characteristics when hardware supports variable levels.

**Milestone 4 gate**

| Check | Pass criterion |
|-------|----------------|
| Host | `pns_verify_toolchain.ps1 -RunTests` + ReadLints clean on touched files |
| Device | §5 rows for **every** closed [ADB] bullet in sprints 4.2–4.3; `pns_adb_preview_validate.ps1` exit 0 on reference device |
| Encoder bodies | Sprint 4.1 NDK rows `[x]` when landed |
| Sprint 4.4 | Optional: closing any **[MIXED]** / **[ADB]** row requires §5 + `PNS.AdbValidation` or scripted validate evidence (same standard as 4.2–4.3) |

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

### Sprint 9.8 — 7×7 grid layout + Settings **`[2,6]`** (row 2, col 6)

- [x] [HOST] **`previewChromeGridSlots`** — row **0** focal mm chips unchanged; **shortcut rows** (see Sprint **9.9** shipped layout): expand shortcuts + quick actions + **Settings** **`ExpandShortcut`** at **`(row=2,col=6)`** in current code (`settingsAt=r2c6` in **`PNS.ChromeUx`**).
- [x] [HOST] **`PNS.ChromeUx`** **`grid7=layout settingsAt=r2c6=true`** (+ **`quickActions=…`** list); **`PreviewReadoutStrip`** uses **`TransformOrigin(0.5f,0.5f)`** with **`uiRotationDeg`** (matches grid rotation pivot).
- [x] [HOST] **`pns_chrome_ux_gate.ps1`** — **`grid7Ok`**.

### Sprint 9.9 — Grid quick actions (shipped layout)

- [x] [HOST] **`ChromeGridSlotSpec`** — **`ExpandShortcut`** vs **`QuickAction`**; **row 1** (logical): **Guides**, **Preview & keys**, **Capture & tools** (expand), **Self timer**, **Histogram**, **Horizon level**, **Eye AF overlay**; **row 2**: **Video tally**, **Max brightness**, **DND in preview**, **Extra shutters** (merged tap + volume), **Flash mode**, **Save location in files**, **Settings** (expand). Source: **`previewChromeGridSlots`** in **`PreviewEngineScreen.kt`**.
- [x] [HOST] **`PNS.ChromeUx`** — **`quickActions=timer,histogram,horizon,eyeAf,tally,bright,dnd,extraShutter,flash,saveLoc`** (see **`LaunchedEffect`** log in **`PreviewEngineScreen`**).

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

### Sprint 9.12 — Flash mode + quick-settings slot (**chrome**)

**Objective:** Real still/preview **flash** policy (not a stub), a **single dedicated Flash quick-setting** tile on the 7×7 grid, and **one fewer** binary quick tile by merging two related toggles.

**Chrome / layout:** Obey **`.cursor/rules/preview-chrome-ui-lock.mdc`** — reuse existing tile geometry and chip styling; **no** finder flex / rail weight / grid spacing experiments unless the user explicitly unlocks the style guide.

- [x] [HOST] **Merge two quick settings into one** — **Extra shutters** tile + popup (**tap** + **volume** toggles); **`CHANGELOG.md`** / rail sheets aligned.
- [x] [HOST] **Flash quick-setting (QS) tile** — **`CycleFlash`** + **`PreviewFlashMode`** + **`PreviewFlashPolicy`**.
- [x] [MIXED] **Camera2 flash wiring** — Preview repeating + still **`FLASH_MODE`** / AE modes (bracket stills force flash off); front / no-flash handled in **`PreviewFlashPolicy`**.
- [x] [HOST] **`PNS.ChromeUx`** — **`extraShutter`**, **`flash`** tokens in `quickActions=` log.
- [x] [ADB] **`pns_adb_preview_validate.ps1 -ChromeUxPack`** (or gate script) — optional scenario line / JSON field proving **`flash`** QS appears and **`PNS.ChromeUx`** logs expected tail after cold start (device without flash still logs **degraded** / **skipped_no_flash** — document).

**Note:** `chrome_ux_smoke.json` asserts **`quickActions=…flash…`** on the **`grid7=`** line and **`flashPreviewHardware=true|false`** once per preview session (hardware absent → `false`). Self-timer **`selfTimerSec=N`** log uses the **normalized** ADB seed value immediately (not a stale prefs read). Evidence: **`PROBE_BUILD_PLAN.md` §5 — 2026-05-12** (`hfr-runs/adb_preview_validate_20260512_022021/chrome_ux_smoke.json`).

**Follow-on (not Sprint 9.12):** zebras GLSL; remaining Sprint **4.4** Camera2 items.

**Milestone 9 gate (current):** `pns_verify_toolchain.ps1 -RunTests` PASSED; with device: **`scripts/pns_chrome_ux_gate.ps1`** exit 0 and **`chrome_ux_gate.json`** **`pass: true`**; §5 row when a physical device is used. UI tweaks **also** require § **Preview finder acceptance → UI change verification (screenshots mandatory)** above. **Sprint 9.12** closes only after device proof for flash + merged QS (screenshots + §5).

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
- [ ] [MIXED] Work through **§ UX backlog (preview chrome locked)** above (messaging, snackbars, export errors, onboarding tweaks, geotag hint, progress, probe-hub IA, a11y labels, immersive tip, gallery open fallback) without changing locked preview chrome geometry or styling. **Host-automation slice (2026-05-12):** most rows in that backlog table are now **[x]** with “partial” notes where applicable; **long-running capture progress** and **Details/Copy** on errors remain open.

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
