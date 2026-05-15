# Agent automation reference (Point & Shoot)

This document is for **AI coding agents** (Cursor and similar) working in this repository. It lists **what you can run yourself** instead of asking the human to copy commands into PowerShell.

**Operational rule:** If a task can be done via `adb`, Gradle, or a repo script from a terminal in this workspace, **run it**. Only ask the human when something is **missing from the machine** (no device, no JDK, MCP server down, auth not completed) or **unsafe** (destructive prod action).

**Device truth rule:** Do **not** tell the user a **fix or feature is delivered / done** until it is **verified on a real device over USB ADB** (install the build under test, exercise the path, and report script artifacts or log needles). If no device is online, say explicitly that **device verification was not run** and treat the change as **unverified**. Use repo scripts (`pns_photo_capture_verify`, `pns_in_app_video_verify`, `pns_chrome_ux_gate`, `pns_adb_preview_validate`, etc.) when they match the change; otherwise document the exact `adb` / `am start` steps you ran and what you observed.

---

## CRITICAL — sequential RAW / `pns_preview_raw_count` and preview session wiring

**Never** set **`automationSuppressFacePipeline = true`** for **`adbSequentialRawStills > 0`** alone (sequential RAW-only / `pns_preview_raw_count`). That path must keep the **same H-dial YUV / face-pipeline behavior** as manual H capture; suppressing it forced **`wantYuv=false`** and broke RAW still session create on **CPH2655-class** stacks (`CAMERA_DISCONNECTED`). **Only `adbBracketPattern != null`** should enable **`automationSuppressFacePipeline`**. See **`README.md`** STOP banner, **`BUILD_PLAN.md`** item **11** (hard rule), and **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** (top). After any capture-session change: **`scripts/pns_photo_capture_verify.ps1`** or **`scripts/pns_capture_pipeline_verify.ps1`** on USB; after bulk restore from the bisect doc: **`scripts/pns_capture_restore_verified.ps1`**.

**Incremental restore (May 2026, CPH2655 proof):** Do **not** re-apply every §1–§5 “shipping” hunk in one commit without **per-hunk** **`pns_photo_capture_verify`** (or pipeline verify). **§4a** (stream hints on) and **§2** (RAW10 before RAW_SENSOR on `Default`) each broke scripted capture on **`8bf09993`** while other rows stayed restored; the **max verified** combo for that device keeps **§4a off** and **§2 bisected**, and restores **§1** + **§5**. Table: **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8.

---

## CRITICAL — REGULAR session stream hints (§4a) and `Default` RAW tier (§2)

**Do not** flip these back to “Milestone shipping” on **`PreviewEngineScreen.kt`** / **`RawCaptureSupport.kt`** for the dodge / **CPH2655-class** fleet **without** a fresh USB **`pns_photo_capture_verify.ps1`** (or **`pns_capture_pipeline_verify.ps1`**) pass — they are **known regressions** on **`8bf09993`** (May 2026):

- **§4a — `streamHints = SDK_INT >= TIRAMISU` on the REGULAR session:** causes scripted RAW still **timeouts** and **`ERROR_CAMERA_DEVICE` (`onError` 4)** after capture starts (HAL never completes the still in time). **Keep** bisect **`streamHints = false`** (+ comments) unless you have **device proof** and a **narrow** OEM-specific gate.
- **§2 — `RawStreamPreference.Default` with RAW10 before RAW_SENSOR:** picks **RAW10 (format 37)**; capture can succeed but **`DngCreator.writeImage`** fails with **`Unsupported image format 37`**. **Keep** bisect order **RAW12 → RAW_SENSOR → RAW10** for **`Default`** here until the DNG pipeline explicitly supports RAW10 for this path **and** USB proof exists.

Full avoidance table + artifact paths: **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8 and **§8 “What agents must avoid”**.

---

## CRITICAL — GLES preview aspect (do not reapply reverted fixes)

**May 2026 (CPH2655 / user-verified):** Multiple attempts to fix **gallery-return** or **resume** preview stretch **broke default preview** (distorted / stretched) and were **reverted**. **Do not reintroduce** these patterns without maintainer sign-off, a **new** design, and USB proof on a real device:

1. **`LaunchedEffect`** (or similar coroutine) calling **`LutCameraPreviewRenderer.setGeometry`**, especially keyed on **`previewPipelineGeneration`**, **`previewBufferSize`**, **`centerViewSize`**, or other high-churn Compose state — races **layout** and **`RENDERMODE_WHEN_DIRTY`**.
2. **`Handler.post`**-deferred **`kickPreviewPipelineRestart()`** on **`ON_RESUME`** — ordering vs **`GLSurfaceView.onResume()`** / new **`SurfaceTexture`** was reverted as risky.
3. **`PreviewController.setPreviewBufferGeometryListener`** + coalesced **`mainHandler`** notifications + **`GLSurfaceView.queueEvent { setGeometry }`** on **`previewBufferSize()`** changes — **reverted**; user reported preview broken again.
4. **`PreviewController.setPreviewDisplayLayoutSyncListener`** + **`previewLayoutSyncNonce`** + extra **`ON_RESUME`** buffer / layout nudges — **reverted May 2026**; caused **cold-start** distortion and related regressions on **`8bf09993`**-class devices.

**Shipped invariant:** **`setGeometry`** is driven only from **`PreviewMainViewport`** — **`AndroidView` `update`** and **`OnLayoutChangeListener`**. Any future gallery/resume fix must **not** duplicate that contract with a second writer unless explicitly redesigned.

**May 2026 follow-up — what actually failed (do not repeat blindly):**

| Approach | Result |
|----------|--------|
| **`previewGeometryApplyToken`** + delayed second bump + **`AndroidView` `update`** | User: **gallery** stretch **not** fixed; extra complexity. |
| **`setPreviewDisplayLayoutSyncListener`** + pushing **`previewBufferSize()`** into Compose from **`reconcile…`** (even gated) | **Cold-start** finder / preview distortion — view-sized ST hints are **not** buffer WxH. |
| **`previewLayoutSyncNonce`** + **`ON_RESUME`** re-read **`previewBufferSize`** | Same **cold-start** class of breakage when combined with forced **`AndroidView` `update`**. |
| **`GLSurfaceView.setPreserveEGLContextOnPause(true)`** | **`Surface was abandoned`** / **`createCaptureSession`** **`IllegalArgumentException`** on **`8bf09993`** cold **`pns_photo_capture_verify`**. |
| **`Handler.post`**-**delayed** **`kickPreviewPipelineRestart()`** only | Listed as risky (ordering vs new **`SurfaceTexture`**). |
| **Hard task restart** after tray **`openMediaWithSystemResolver` → true** | **Shipped May 2026** — **`Intent` `CLEAR_TASK` + `NEW_TASK`**, **`finishAffinity()`**, relaunch same activity class copying **`intent` extras** — cold-start–equivalent when GLES preview stays wrong after external viewers. **Only** when a viewer actually started (**`AtomicBoolean`**); other resumes use **`kick`** + **`View.post`** layout. |

**Industry-aligned pattern that stays within the `setGeometry` contract:** After **`kickPreviewPipelineRestart()`** on **`ON_RESUME`**, call **`previewHostSlot.view?.post { requestLayout(); invalidate() }`** (and optionally a **second** nested **`post { }`** only if USB proof requires it). **`View.post`** defers until after the current UI traversal so **`GLSurfaceView.onResume()`** and window insets typically run first — same idea as “defer work to next message” in Camera / **`SurfaceView`** samples. **Do not** combine this with Compose-driven **`previewBufferSize`** overrides from **`reconcile…`** view hints.

Resume handling: when returning after **`openMediaWithSystemResolver`** succeeded from the tray thumb, **`restartMainActivityCold`** (task clear + relaunch). Otherwise **`kickPreviewPipelineRestart()`** + optional **`GLSurfaceView` `post` layout** above; Compose **`previewBufferSize`** stays on the existing controller poll. Still **no** coroutine-driven **`setGeometry`** and **no** controller **`setGeometry`** listener — **`setGeometry`** stays only in **`PreviewMainViewport`**’s **`AndroidView` `update`** + **`OnLayoutChangeListener`**.

---

## CRITICAL — Dodge tele focal slots (73 / 85 / 150 mm) — do not regress

This is **preview/correctness**, not Gradle compile failures. **May 2026 regression:** adding **`FocalRoutingPolicy` / `FleetAuto`**, **`effectivePolicy`**, and resolving tele chips through **logical `cameraId=0` alone** when **physical tele** (e.g. **`4`**, LYT-600 ~**13.9 mm**) was already in **`cameraIdList`** broke **digital equivalence**:

- **85 mm** / **150 mm** looked unchanged vs **73 mm** (`SCALER_CROP_REGION` / active-array basis mismatch).
- **Fleet** routing could send **150 mm** toward **`longTele`** instead of **digital `LongTele150`** on **`Roles.tele`** — violates **`DODGE_PROFILE.md`** for the reference stack.

**Shipped invariant (do not revert without maintainer sign-off + USB proof):**

1. **Single policy — dodge tele row:** **`resolveFocalMmSlot`** / **`telePhysicalForPreviewPin`** use **`Roles.tele`** for **all three** tele M-slots (**73** native, **85** `Portrait85`, **150** `LongTele150`). **No** second “fleet” policy enum or persisted prefs for tele routing.
2. **Physical-first when enumerated:** **`teleOpenablePair`** must prefer **`tid to mode`** when **`tid in ids`** so preview opens **physical tele** when the HAL lists it — keeps crop math on the **tele sensor’s** active array (see **`BackCameraRoleResolver.kt`**).
3. **`SensorCropGeometry`:** **`LongTele150`** **`allowsDigitalCrop`** gates on **`teleId`** only (mid-tele sensor), **not** **`longTeleId`**.
4. **FPS:** Digital crops apply only when **`desiredFps < 120`** in **`PreviewController`**; do not “fix” tele UX by forcing fleet lens-switch — clamp FPS or document readout behavior instead.

**Automation hygiene:** Do **not** run **`pns_capture_pipeline_verify`** / **`pns_photo_capture_verify`** **concurrently** with **`pns_chrome_ux_gate`** against the same device — overlapping cold starts produce **`ERROR_CAMERA_DEVICE` / capture failed** **false negatives**.

**Minimum verification** after touching **`BackCameraRoleResolver.kt`**, **`SensorCropGeometry.kt`**, **`FocalLensStripSupport.kt`**, or focal / crop wiring in **`PreviewEngineScreen.kt`**: JVM tests (`BackCameraRoleResolverTest`, `SensorCropGeometryTest`) + USB **`scripts/pns_chrome_ux_gate.ps1 -SkipGradle -SkipHost -FocalMmSlot 150`** (expect **`teleFocalSlotOk=true`**, log **`focalSlotTap=`** with **`cameraIdAfter=`** physical tele and **`focalCrop=LongTele150`** when applicable). Optionally **`pns_photo_capture_verify.ps1 -Fast`** — run **alone**, not parallel with chrome gate.

---

## Terminal and shell

- **OS:** Windows (PowerShell). Prefer **absolute paths** when passing paths to tools.
- **You have full shell access** in this environment: run builds, scripts, `adb`, and short Python invocations yourself.
- **Repo root** for commands: `c:\Users\edwar\AndroidStudioProjects\point-and-shoot` (adjust if the workspace path differs on another clone).

---

## Gradle and JDK (no manual `JAVA_HOME` setup required)

| Script | Role |
|--------|------|
| `scripts\pns_gradlew.ps1` | Runs `gradlew.bat` with JDK resolved in-process (e.g. `.\scripts\pns_gradlew.ps1 :app:assembleDebug`). |
| `scripts\pns_baseline_profile_generate.ps1` | USB device: optional animation-scale tweaks + `pns_gradlew.ps1 :app:generateBaselineProfile` → `app\src\release\generated\baselineProfiles\`. |
| `scripts\pns_java_home.ps1` | JDK discovery; use `-ListCandidates` if resolution fails. |
| `scripts\pns_verify_toolchain.ps1` | Toolchain sanity checks. Resolves a JDK via `pns_java_home.ps1 -EmitPath` before invoking Gradle so minimal PATH shells (no prior `JAVA_HOME`) still run `assembleDebug` / Detekt / lint / unit tests. |
| `scripts\pns_install_ndk.ps1` | NDK install helper when needed. |

`pns_milestone6_gate.ps1` may call `gradlew.bat` directly from repo root; either pattern is valid.

**`pns_gradlew.ps1` (PowerShell 5.1):** Gradle tasks starting with **`:`** are forwarded via **`$args`**; optional **`-JdkHome "…\jbr"`** must appear before task tokens if you use it.

**Settings across reinstall:** the app enables **Auto Backup** allow-listed **`SharedPreferences`** (`res/xml/pns_backup_rules.xml`). Restore after reinstall depends on **Google / OEM backup** (not automatic for all sideload-only installs).

### Quality gates (Detekt, Android Lint, R8, baseline profiles)

| Command | Role |
|--------|------|
| `.\gradlew.bat :app:detekt` | Static analysis (`config/detekt/detekt.yml`; baseline `config/detekt/baseline.xml`). |
| `.\gradlew.bat :app:detektBaseline` | Regenerate Detekt baseline after bulk suppressions (commit the updated XML intentionally). |
| `.\gradlew.bat :app:lintDebug` | Android Lint (baseline `app/lint-baseline.xml`). |
| `.\gradlew.bat :app:updateLintBaseline` | Refresh lint baseline when triaging existing findings. |
| `.\gradlew.bat :app:assembleRelease` | Release APK with **R8** shrink + obfuscation; `app/proguard-rules.pro` must stay **UTF-8** (no BOM). |
| `.\gradlew.bat :app:generateBaselineProfile` | Macrobenchmark baseline + startup profiles (USB device); outputs under `app\src\release\generated\baselineProfiles\`. Prefer **`scripts\pns_baseline_profile_generate.ps1`**. |

Some Gradle tasks in the baseline-profile graph (e.g. **`mergeReleaseBaselineProfile`**) can still invoke **connected** instrumentation; flaky USB or **adb** client/server version mismatches may surface as **`Connection reset`** / **`Connection refused`** from ddmlib — retry with a stable cable or aligned **adb** builds.

`pns_verify_toolchain.ps1 -RunTests` runs **Detekt**, **lintDebug**, and **unit tests** after `assembleDebug`. **SBOM:** `.github/workflows/sbom-monthly.yml` runs `pns_sbom.ps1 -Verify` on a schedule; pushes still verify SBOM via the toolchain script.

---

## ADB device configuration (gitignored local file)

| File | Purpose |
|------|---------|
| `scripts\pns_adb_device.env` | **Local only** (gitignored). Set `PNS_ADB_SERIAL` to the USB serial from `adb devices`. |
| `scripts\pns_adb_device.env.example` | Copy to `pns_adb_device.env` and edit. |

**Behavior (shared across many scripts):**

- If `-Serial` is omitted, scripts read `PNS_ADB_SERIAL` from `scripts\pns_adb_device.env`.
- If more than one device is online, set `PNS_ADB_SERIAL` or pass `-Serial` where the script supports it.

**Always-applied workspace rule:** `.cursor/rules/adb-device-env.mdc` — keep automation aligned with it (env file name, `PNS_ADB_SERIAL`, sideload/validate/milestone scripts).

---

## Obtainium (updates from source)

[Obtainium](https://github.com/ImranR98/Obtainium) pulls APKs from release pages you choose (GitHub and other supported sources).

### Import from URL list (bulk, simple)

Same idea as a plain subscription list (like OPML for feeds): **one URL per line**, UTF-8 text, no XML.

1. Create a file, e.g. `obtainium-sources.txt`, with one repo or source URL per line:

   ```text
   https://github.com/edwardlthompson/MultiAppShare-
   https://github.com/asksven/bbs_reloaded-releases
   ```

2. On the phone: **Obtainium → Import / Export → Import from URL list**, paste the whole file contents (or open the file in an editor on the device, select all, copy, paste).

3. Optional: push the file from this machine:  
   `adb push scripts\obtainium-sources.txt /sdcard/Download/obtainium-sources.txt`  
   then open **Downloads → obtainium-sources.txt** on the device, select all, copy, paste into Obtainium (Obtainium expects **plain text**, not XML “OPML” markup).

The repo keeps a starter list at **`scripts\obtainium-sources.txt`** (one GitHub repo URL per line).

Use normal `https://github.com/...` URLs so Obtainium can detect the source. Adjust per-app options afterward (for example **Include prereleases** if a project only ships pre-releases).

### One-off add link

To jump straight to the add screen for one GitHub repo:  
`obtainium://add/github.com/edwardlthompson/MultiAppShare-`  
Example over USB:  
`adb shell am start -a android.intent.action.VIEW -d "obtainium://add/github.com/edwardlthompson/MultiAppShare-"`

---

## PowerShell automation scripts (`scripts\`)

Use these from repo root unless a script documents otherwise.

| Script | Use when |
|--------|----------|
| `pns_sideload_and_launch.ps1` | Build (optional), `adb install -r`, grant camera, launch app (`-LaunchScreen preview` default). Prepends SDK **platform-tools** to PATH when **`pns_resolve_adb.ps1`** is present. |
| `pns_resolve_adb.ps1` | Prefer one **adb**: **`-EmitPath`** prints SDK **platform-tools\\adb.exe**; **`-CheckOnly`** exits **2** if PATH adb differs; dot-source **`-PrependToPath`** (**`-Quiet`**) at startup in device-facing **`scripts\\`** automation (preview validate, probes, gates, DCIM pull, screencap, Perfetto, sideload, HFR/cold-start, probe watch/append, automation smoke, etc.). |
| `pns_adb_preview_validate.ps1` | Device preview validation; **`-Milestone6Pack`** for milestone pack. |
| `pns_capture_still_forensics.ps1` | Cold **preview** + **`pns_preview_dial=H`** + **`pns_preview_raw_count`**: install (optional), pull pid + ring logcat into **`hfr-runs/capture_still_forensics_*`** (use after DNG save failures; see **`PNS.CaptureStill`**). **`-Fast`** passes **`pns_preview_raw_still_fast`** for shorter in-app ADB settle and a shorter default wait. |
| `pns_photo_capture_verify.ps1` | Loop **assembleDebug** (optional) → install → cold preview + one scripted RAW still; retries until **`PNS.AdbValidation`** shows **`captureRawStill 1/1 ok=true saved=`** or **`-MaxAttempts`**. Optional **`-SweepCameraIds`** tries **`pns_preview_camera_id`** **`(default),0,1,2,3`** in one artifact folder. Uses timeout-wrapped **adb**; artifacts **`hfr-runs/photo_capture_verify_*`** (logcat + **`run-as`** `files/PNS_CAPTURE_PIPELINE_DIAGNOSTICS.txt` when present). Logcat filter includes **`PNS.Cam:I`** for **`PNS.PreviewSessionCtx`**. Prefer **`pns_capture_pipeline_verify.ps1`** for **`docs/CAPTURE_PIPELINE_VERIFY_*.json`** (BUILD_PLAN item **11**). |
| `pns_in_app_video_verify.ps1` | Cold **preview** with **`pns_preview_primary_photo=false`** + **`pns_preview_automation_in_app_video_sec`**: install (optional), **`assembleDebug`** (optional), assert **`PNS.AdbValidation`** **`inAppVideoSaved ok=true`** and **`bytes ≥ MinBytes`**; artifacts **`hfr-runs/in_app_video_verify_*`**. Uses **`adb exec-out logcat -s …`** for OEM-stable tag dumps. Gate after in-app **`MediaRecorder`** / **`PreviewEngineScreen`** session changes alongside **`pns_capture_pipeline_verify.ps1`** when RAW session wiring moves. |
| `pns_capture_pipeline_verify.ps1` | Wraps **`pns_photo_capture_verify.ps1`** in a child process; writes **`hfr-runs/capture_pipeline_gate_*/gate.json`**, **`docs/CAPTURE_PIPELINE_VERIFY_LATEST.json`**, appends **`docs/CAPTURE_PIPELINE_VERIFY_HISTORY.jsonl`**. Optional **`-BisectStep`**, **`-Notes`**, **`-NoHistoryAppend`**. |
| `pns_capture_bisect_device.ps1` | **USB:** cumulative bisect steps **1..N** on **`PreviewEngineScreen.kt`** + **`RawCaptureSupport.kt`** (see **`docs/REVERTED_FEATURES_RESTORE_LIST.md`**), **`assembleDebug`**, **`pns_capture_pipeline_verify`** per step; **`hfr-runs/capture_bisect_device_*/report.md`**. **`-DryRun`**, **`-Fast`**, **`-FromStep`**, **`-NoRestore`**, **`-WriteDocHistory`**. |
| `pns_capture_restore_verified.ps1` | **`assembleDebug`** + USB **`pns_capture_pipeline_verify.ps1`** after capture restores — gate **`captureRawStill 1/1 ok=true saved=`** before merge. **Do not** treat as “ship full Milestone §1–§5”; **§4a** / **§2** are fleet-sensitive — see **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8. |
| `pns_raw_regression_bisect.ps1` | **USB automation:** snapshot `RawCaptureSupport.kt` + `PreviewEngineScreen.kt`, run **`pns_photo_capture_verify`** on baseline, then re-apply **one** suspect regression at a time (wrong default RAW tier order, `desiredFps` default 120, gated H-dial YUV), rebuild, re-verify; writes **`hfr-runs/raw_regression_bisect_*/results.json`** + **`report.md`**. Exit **1** if baseline fails (bisect inconclusive on that device). Dot-source **`pns_resolve_adb.ps1 -PrependToPath`** first on Windows if PATH adb differs from SDK. |
| `pns_raw_capture_matrix.ps1` | **20-cell** matrix (optional **`-Quick`** for 4 cells): **`pns_preview_imaging_profile`** × **`pns_preview_raw_stream`** (`default`, `raw_sensor_first`, `raw12_only`, `raw_sensor_only`, `raw10_only`) × **`pns_preview_jpeg_companion`**, plus optional **`-CameraId`**. Artifacts **`hfr-runs/raw_capture_matrix_*`** (`matrix.csv`, `matrix.md`, per-cell logcat). See **`docs/RAW_CAPTURE_DEVICE_MATRIX.md`**. |
| `pns_deep_caps_diff.ps1` | Host-side **Markdown** diff of two **`deep_caps_*.json`** pulls (**HFR max**, **HDR DR** summary, **`maxNumOutputRaw`**, **`rawCapabilityAdvertised`** per `cameraId`). See **`docs/FLEET_REFERENCE_M10_8.md`** (Milestone **10.8** fleet evidence). |
| `pns_gen_camera2_keys_reference.ps1` | Regenerate **`docs/CAMERA2_KEYS_AND_APIS_REFERENCE.md`** from **`local.properties` → sdk.dir** `platforms/android-<N>/android.jar`; **`<N>` = `compileSdk`** parsed from **`app/build.gradle.kts`** (override **`-ApiLevel`**). |
| `pns_ae_highlight_probe_adb.ps1` | Cold-start **`pns_screen=probehub`** + **`pns_auto_export_probe`**, pull **`PROBE_EXPORT_LATEST.md`**, write **`ae_highlight_probe_summary.txt`** + **`ae_highlight_probe.json`** (`summary` path); optional **`-AlsoRootCapabilityAdb`**. **Debuggable APK** required for `run-as`. |
| `pns_face_meter_probe.ps1` | Cold-start **`pns_screen=facemeter`** + **`pns_autofacemeter`**, wait for **`FACE_METER_PROBE_DONE`** in **`PNS.SWEEP_SIGNAL`**, pull **`face_meter_probe_*.{md,json}`** (face / eye / metering inventory). Artifacts under **`hfr-runs\face_meter_probe_*`**. |
| `pns_milestone6_gate.ps1` | One-shot: assembleDebug → validate pack → optional `PROBE_BUILD_PLAN.md` §5 append. Artifacts under `hfr-runs\`. |
| `pns_milestone3_gate.ps1` | **Milestone 3** mapping gate: JVM tests (`SensorCropGeometryTest`, `CropPlanTest`, `DngDefaultUserCropRatiosTest`, `BackCameraRoleResolverTest`) + optional **`-RunDeviceSmoke`** (sideload preview + `PNS.ChromeUx` **`seedOk slot=M23`** log grep). |
| `pns_automation_smoke.ps1` | Automation smoke; optional **`-RunAeHighlightProbe`** chains **`pns_ae_highlight_probe_adb.ps1`** (debug APK + `run-as` pull). |
| `pns_chrome_ux_gate.ps1` | Chrome UX gate; optional **`-FocalMmSlot`** (`14`…`150`, default **`85`**) appends **`pns_preview_focal_mm_slot`** for **`focalSlotTap=`** tele proof (**`teleFocalSlotOk`**). |
| `pns_failure_matrix_smoke.ps1` | Failure-matrix smoke. |
| `pns_hfr_autorun.ps1` | HFR autorun (`-PerfReport`, **`-PerfReportApkVariant Release`**, etc.). |
| `pns_cold_start_capture.ps1` | **`pns_hfr_autorun.ps1 -PerfReport`** → **`perf-runs/perf_*.md`** (or **`-Release`** → **`perf_release_*.md`** + assemble/install Release); optional **`-Serial`**, **`-SkipGradleBuild`**. |
| `pns_compose_layout_trace_capture.ps1` | Warm **preview** launch then **`pns_capture_perfetto_light.ps1`** with **`gfx view sched wm input`** (Compose / layout-oriented Perfetto slice). |
| `pns_capture_perfetto_light.ps1` | Light Perfetto capture. |
| `pns_pull_dcim_captures.ps1` | Pull captures from DCIM. |
| `pns_device_screencap.ps1` | Device screenshot helper. |
| `pns_root_capability_adb.ps1` | Probes `adb root` / `adb shell id` / `su`; writes `root_capability_adb.json` under `-OutDir`. |
| `pns_root_privileged_smoke.ps1` | Cold-start **`pns_screen=rootsettings`** + **`pns_auto_root_diagnostics`**; greps log for **`rootPrivScan`** (**`suite=read_only_done`** = full read-only SU suite, or **`skipped`** = wiring-only **`pass`**). **`-RequireGrantedSuite`** → **`pass`** only when suite completes. Artifacts under **`hfr-runs\root_privileged_smoke_*`**. |
| `pns_probe_append_section5.ps1` | Append probe/milestone rows (see script header). |
| `pns_probe_watch.ps1` | Probe watch loop. |
| `pns_super_macro_gate.ps1` | Super-macro gate. |
| `pns_sbom.ps1` | SBOM generation. |
| `pns_license_inventory.ps1` | License inventory. |
| `pns_analyze_reader_backpressure.ps1` | Reader backpressure analysis. |
| `pns_capture_gfxinfo_baseline.py` | Gfxinfo baseline (Python). |

If `pns_adb_device.env` is missing, scripts may warn and still run if a **single** default device is visible to `adb`.

### AE / highlight probe export (`pns_ae_highlight_probe_adb.ps1`) — debuggable build only

- The script pulls **`files/PROBE_EXPORT_LATEST.md`** with **`adb exec-out run-as dev.pointandshoot cat …`**. Android only allows **`run-as`** for **debuggable** application packages.
- Use the default **`app/build/outputs/apk/debug/app-debug.apk`** from **`assembleDebug`** (same as other repo ADB scripts). A **release / Play store build is not debuggable**; `run-as` will fail with errors such as **`run-as: package not debuggable`** — install the **debug** APK first (the script installs it by default). **`-AssembleDebug`** runs **`pns_gradlew.ps1 :app:assembleDebug`** before install even when an APK already exists; if the APK is missing, **`assembleDebug`** runs automatically unless **`-SkipInstall`**.
- Optional **`-SkipInstall`** still assumes a **debug** build is already installed (otherwise `run-as` cannot read app-private **`files/`**).
- **`host:port`** **`PNS_ADB_SERIAL`** values trigger **`adb connect`** before device checks (Wi‑Fi ADB).
- Pull retries (**`-PullAttempts`**, **`-PullRetrySec`**) reduce flakes; on failure the script writes **`logcat_probe_export_tail.txt`** under **`-OutDir`**.
- On success, writes **`ae_highlight_probe_summary.txt`** (short excerpt of the markdown: header, AE/highlight device context, first ~60 lines per camera AE block) and sets **`summary`** in **`ae_highlight_probe.json`**. Use the summary for quick review; use **`PROBE_EXPORT_LATEST.md`** for full key dumps.
- **Static SDK key catalog (host):** **`docs/CAMERA2_KEYS_AND_APIS_REFERENCE.md`** lists every `CameraCharacteristics` / `CaptureRequest` / `CaptureResult` **`Key`** field name for the app **`compileSdk`** (parsed from **`app/build.gradle.kts`** by the script) plus core `javap` method lists, a **face / eye filtered key subset**, and a merged appendix from **`docs/camera2_reference_face_eye_appendix.md`** — regenerate with **`.\scripts\pns_gen_camera2_keys_reference.ps1`** (optional **`-ApiLevel`** overrides **`compileSdk`**).

**Named vendor Camera2 keys (how to obtain):** The HAL advertises supported keys only on-device — there is no static SDK list for OEM strings. Read **`CameraCharacteristics.getAvailableCaptureRequestKeys()`** (and session/result/characteristics key sets); each key’s **`getName()`** is the string you gate on (see **`VendorKeyGuard`** and the probe markdown sections *Available CaptureRequest keys* / *Vendor-ish keys*). Optional: **`adb shell dumpsys media.camera`** on eng/userdebug builds for another view of advertised tags.

---

## Root / privileged ADB (optional, device-dependent)

- **`pns_root_capability_adb.ps1`** documents what was tried (`adb root`, shell uid, `su`). Output is JSON for probes/gates — **not** a guarantee the fleet is rooted.
- **`PNS_ADB_ROOT_AVAILABLE=1`** in `pns_adb_device.env` is a **human/agent note** (commented examples in `.example`); it is **not** parsed as auth by the scripts — use it to record that `adb shell su` works on a given fleet device.
- Do not assume root: userdebug/eng `adb root` and Magisk/KernelSU behavior differ per device.

---

## Cursor MCP (Composio, Render)

When this workspace is open in Cursor, **MCP servers** may be enabled (e.g. **Composio** `user-composio`, **Render** `user-render`). Capabilities depend on the user’s Cursor **Settings → MCP** and account auth.

**How agents should use MCP here:**

1. **Discover tools:** Tool JSON schemas are available under the Cursor project MCP cache, typically  
   `%USERPROFILE%\.cursor\projects\<project-folder>\mcps\<server>\tools\*.json`  
   (exact folder name matches the workspace path Cursor uses).
2. **Before any `call_mcp_tool`:** Read the relevant tool’s JSON schema (required parameters, names).
3. **If a server errors or asks for auth:** Tell the user briefly to check MCP status / Composio connections in Cursor; continue with non-MCP work where possible.

Composio-oriented tools (names vary by deployment) often include search, multi-execute, remote bash/workbench, connection management — **only use after reading schemas**.

---

## Cursor workspace rules (do not ignore)

| Rule | Summary |
|------|---------|
| `.cursor/rules/adb-device-env.mdc` | ADB env file, `PNS_ADB_SERIAL` (USB), script entry points. |
| `.cursor/rules/dodge-tele-focal-routing.mdc` | **Locked** dodge tele **73/85/150 mm** routing + crop gates — no fleet policy; physical tele preferred when enumerated; see **`AGENTS.md`** CRITICAL section. |
| `.cursor/rules/preview-chrome-ui-lock.mdc` | **Frozen** preview chrome layout — behavioral fixes only unless the user explicitly changes UI. |
| `docs/preview-chrome-layout-style-guide.md` | **Canonical** portrait stack: inset band, 3:4 finder flex, dividers, readout, **7×3** quick grid + focal row (matches the lock rule). |

---

## Project plans (human-authored scope)

- `BUILD_PLAN.md` (active milestones), `BUILD_PLAN_COMPLETED.md` (archived milestones 0–7), `PROBE_BUILD_PLAN.md` — milestones, gates, and probe expectations. Use them to choose the right script and artifacts paths (e.g. `hfr-runs\`, `milestone6_gate.json`).

### MainActivity / navigation (automation vs in-app)

- **`EXTRA_PNS_*` extras** (including **`pns_screen`**) are read once in **`MainActivity.onCreate`**. For deterministic logs and cold-start gates, use **`am force-stop dev.pointandshoot`** (scripts already do this before **`am start`**) so a new process picks up intent extras.
- **In-app navigation** stays inside **`CameraCapabilitiesProbe`** Compose state; Back typically does not **`finish()`** the activity unless a route explicitly does (e.g. **`MediaStore.ACTION_IMAGE_CAPTURE`** return path in **`deliverImageCaptureToCaller`**).

---

## Credentials and secrets (important)

- **`scripts\pns_adb_device.env`** may contain **device serials** — treat as sensitive; never commit (it is gitignored).
- **API keys / cloud tokens** for MCP (Render, Composio, etc.) live in **Cursor / OS secure storage**, not in this repo. You do not “read credentials from a file” for those — you invoke MCP tools and the runtime attaches auth. If auth is missing, **say so once** and ask the user to fix MCP/auth in Cursor.
- Do not invent serials or tokens; read from env/example files or ask.

---

## UI change policy (short)

Preview chrome is **locked** by rule (`preview-chrome-ui-lock.mdc`) and specified in **`docs/preview-chrome-layout-style-guide.md`** — do not “improve” spacing/tiles/colors without an explicit user request. Prefer minimal diffs for behavior bugs only.

**GLES external-OES preview (`LutCameraPreviewRenderer` / `setGeometry`):** treat aspect as **locked** to the **`PreviewMainViewport`** layout + **`AndroidView` `update`** path unless the user explicitly requests a pipeline change. See **`AGENTS.md`** **CRITICAL — GLES preview aspect (do not reapply reverted fixes)** for patterns that were tried and **reverted** after breaking preview.

---

## Checklist before asking the human to run something

1. Can `.\scripts\pns_gradlew.ps1` or `.\scripts\<relevant>.ps1` do it? → Run it.
2. Is `adb devices` empty or unauthorized? → Then ask them to connect USB, enable debugging, or fix `PNS_ADB_SERIAL`.
3. Is MCP required and failing? → Ask them to check Cursor MCP / Composio / Render auth once.
4. Otherwise → Proceed and report paths to artifacts (`hfr-runs\`, APK paths, JSON outputs).
