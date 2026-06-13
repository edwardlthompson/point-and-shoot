# Agent automation reference (Point & Shoot)

This document is for **AI coding agents** (Cursor and similar) working in this repository. It lists **what you can run yourself** instead of asking the human to copy commands into PowerShell.

**Technical settings source of truth:** [`docs/PNS_TECHNICAL_SETTINGS.md`](docs/PNS_TECHNICAL_SETTINGS.md) — command dial modes, H-mode metering, readout/YUV chase constants, RAW/DNG locks, HUD defaults, and related code pointers. **Update that file whenever you add, change, or remove any of those settings** (same commit as the code change). Tie new `BUILD_PLAN.md` sprints to the relevant section.

**Changelog contract (mandatory):** User-visible milestones and release cuts must update **`CHANGELOG.md`** and **`scripts/changelog_coverage.v1.json`** in the **same commit**. Host gate: **`scripts/pns_changelog_gate.ps1`** (also runs inside **`pns_verify_toolchain.ps1`**). When bumping **`versionCode`**, add a dated release section and bump **`latestRelease`** in the coverage manifest.

**Knowledge base (index):** [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) — canonical doc → code → gate map for capture, fleet, chrome, video, release, and agent ops. **Before broad refactors:** grep the index, then read **`docs/AGENT_REGRESSION_MEMORY.md`** for regression locks. Do not duplicate linked doc prose in new files.

**Operational rule:** If a task can be done via `adb`, Gradle, or a repo script from a terminal in this workspace, **run it**. Only ask the human when something is **missing from the machine** (no device, no JDK, MCP server down, auth not completed) or **unsafe** (destructive prod action).

**Device truth rule:** Do **not** tell the user a **fix or feature is delivered / done** until it is **verified on a real device over USB ADB** (install the build under test, exercise the path, and report script artifacts or log needles). If no device is online, say explicitly that **device verification was not run** and treat the change as **unverified**. Use repo scripts (`pns_photo_capture_verify`, `pns_in_app_video_verify`, `pns_chrome_ux_gate`, `pns_adb_preview_validate`, etc.) when they match the change; otherwise document the exact `adb` / `am start` steps you ran and what you observed.

**Battery and heat rule (MANDATORY):** After **every** ADB testing session — success or failure — you **must close the app** to prevent battery drain and device overheating. The user may not be present to supervise. Use `adb shell am force-stop dev.pointandshoot` or equivalent cleanup in all scripts. **Never leave the camera app running after testing completes.** This applies to all automated scripts, verification runs, and manual ADB exercises.

---

## Template file map (Milestone T)

Maps the Cursor **Project Initialization Prompt** template to this repo. **Do not** maintain parallel copies of the same rules — link to the canonical path and update in place.

### Aliases (template name → repo)

| Template name | Repo path | Role |
|---------------|-----------|------|
| `.cursorrules` | [`.cursor/rules/*.mdc`](.cursor/rules/) | Fragmented subsystem locks (`alwaysApply` on critical paths). No root `.cursorrules` file. |
| `COMPLETED_TASKS.md` | [`BUILD_PLAN_COMPLETED.md`](BUILD_PLAN_COMPLETED.md) | Shipped work archive by feature + milestone (§29 = Milestone T) |
| `DECISION_LOG.md` | [`docs/adr/`](docs/adr/) + [`DECISION_LOG.md`](DECISION_LOG.md) | Append-only architecture decisions |
| MIT license | [`LICENSE`](LICENSE) | **Apache-2.0** intentionally (see Milestone T ADR-0005 when landed) |

### File map and update policy

| Template file | Repo path | When to update |
|---------------|-----------|----------------|
| `AGENTS.md` | [`AGENTS.md`](AGENTS.md) | New/changed automation scripts, CRITICAL locks, template map |
| `AGENT_MEMORY.md` | [`AGENT_MEMORY.md`](AGENT_MEMORY.md) | Session startup, milestone boundary, handoff to fresh chat only |
| `KNOWLEDGE_BASE.md` | [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) | New SoT doc, code entry point, or gate script; milestone boundary |
| `PROMPT_LIBRARY.md` | [`PROMPT_LIBRARY.md`](PROMPT_LIBRARY.md) | New high-value agent workflow prompt |
| `docs/adr/` | [`docs/adr/`](docs/adr/) | Major architectural trade-off — **append** ADR; never rewrite history |
| `DECISION_LOG.md` | [`DECISION_LOG.md`](DECISION_LOG.md) | Index when adding ADR-0008+ |
| `BUILD_PLAN.md` | [`BUILD_PLAN.md`](BUILD_PLAN.md) | Active sprint tasks, gates, promotion from parity intake |
| `PROBE_BUILD_PLAN.md` | [`PROBE_BUILD_PLAN.md`](PROBE_BUILD_PLAN.md) | Probe/automation lifecycle; §5 blockers |
| Regression ledger | [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md) | After USB-proven fix or reverted experiment (`REG-*` row, same commit) |
| `.cursor-session-state` | [`.cursor-session-state.example`](.cursor-session-state.example); gitignored live file | Milestone end / architectural pivot; delete after handoff |
| Technical settings | [`docs/PNS_TECHNICAL_SETTINGS.md`](docs/PNS_TECHNICAL_SETTINGS.md) | Any settings/constants/mode behavior change (same commit) |
| Changelog | [`CHANGELOG.md`](CHANGELOG.md) + [`scripts/changelog_coverage.v1.json`](scripts/changelog_coverage.v1.json) | User-visible ship / `versionCode` bump (same commit) |
| `CONTRIBUTING.md` | [`CONTRIBUTING.md`](CONTRIBUTING.md) | Trunk flow, pre-commit install, CI checklist |
| Pre-commit | [`.pre-commit-config.yaml`](.pre-commit-config.yaml) | New local hook or CI mirror change |
| Dev container | [`.devcontainer/`](.devcontainer/) | JDK/Python/SDK baseline change |
| `CODEOWNERS` | [`.github/CODEOWNERS`](.github/CODEOWNERS) | Ownership path changes |

### Update cadence (template §3)

Modify memory/plan files **only** at:

1. **Session startup** — refresh [`AGENT_MEMORY.md`](AGENT_MEMORY.md): active milestone, device, last gates, blockers.
2. **Milestone boundary** — tick [`BUILD_PLAN.md`](BUILD_PLAN.md); archive to [`BUILD_PLAN_COMPLETED.md`](BUILD_PLAN_COMPLETED.md); refresh [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) index if SoT paths changed.
3. **Architectural pivot** — new [`docs/adr/`](docs/adr/) entry; cross-link from [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md).

**Do not** update these on every commit — avoids documentation tax and drift. **Exception:** [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md) append on proven fixes; [`docs/PNS_TECHNICAL_SETTINGS.md`](docs/PNS_TECHNICAL_SETTINGS.md) and changelog on every settings/ship change (hard rules).

### `.cursor/rules/` inventory (replaces `.cursorrules`)

| Rule file | Locks |
|-----------|--------|
| `agent-automation-hub.mdc` | Points to this file + `scripts/` |
| `agent-regression-memory.mdc` | REG ledger read/append |
| `adb-device-env.mdc` | `pns_adb_device.env` |
| `changelog-coverage.mdc` | CHANGELOG + coverage manifest |
| `dng-save-pipeline-lock.mdc` | DNG loadability |
| `dng-logical-multicam-metadata-lock.mdc` | DngMetadataResolver pairing |
| `dodge-tele-focal-routing.mdc` | Tele 73/85/150 mm |
| `fleet-generic-policy.mdc` | Fleet matrix SoT |
| `fleet-ui-visibility.mdc` | Consumer chrome gates |
| `github-release.mdc` | Release workflow |
| `pns-technical-settings.mdc` | PNS_TECHNICAL_SETTINGS sync |
| `preview-chrome-ui-lock.mdc` | Preview chrome layout |
| `preview-readout-video-mode-lock.mdc` | Photo vs video readout |
| `session-checkpoint.mdc` | Session handoff / `.cursor-session-state` |

---

## CRITICAL — Fleet capability matrix (Milestone 16)

**Primary USB device:** OnePlus 12 **CPH2583** — not legacy SKU unless running the **optional legacy device regression** lane. See **`BUILD_PLAN.md`** pinned fleet note and **`docs/FLEET_DEVICE_VERIFY_MATRIX.md`**.

**Source of truth:** `files/fleet_device_matrix.json` (`FleetDeviceMatrix` schema **v1–v2**; v2 adds optional `product.focalRow`). Built by **`FleetDeviceMatrixBuilder`** (quick tier on Diagnostics hub shallow scan; full tier in **16.1**). Invalidates on **`fingerprintSha256Prefix`** + **`appVersionCode`** change (same policy as shallow cache).

| Artifact | Role |
|----------|------|
| `fleet/FleetDeviceMatrix.kt` | Schema constants, `scanMeta`, validation |
| `fleet/FleetDeviceMatrixStore.kt` | Persist + `fleet_device_matrix_history/` rotation |
| `fleet/FleetDeviceMatrixBuilder.kt` | Quick scan orchestration |
| Log tag **`PNS.FleetMatrix`** | `scanTier=quick|full` after hub probe / full rescan |

**Host scripts (16.3):** `scripts/pns_fleet_matrix_scan.ps1` · `scripts/pns_fleet_matrix_diff.ps1` · matrix checks in `pns_shallow_scan_hub_validate.ps1`

**Fleet policy (16.4):** Default **`GenericFleetPolicy`** — no `policyId` on new SKUs. **`LegacyDeviceFleetPolicyPlugin`** opt-in via `FleetPolicyPreferences` or ADB `--ez pns_legacy_legacy_fleet_policy true`.

**Agent rules:**

1. **No new legacy SKU-only** feature gates or policy without **`FleetDevicePolicy` plugin** + USB proof on an onboarded SKU row.
2. Fleet-affecting changes → hub matrix rescan or **`pns_fleet_matrix_scan.ps1`**; attach **`pns_fleet_matrix_diff.ps1`** output in PR notes.
3. **`docs/FLEET_ONEPLUS13_RAW_POLICY.md`** = legacy device plugin — not default fleet behavior.
4. DNG **loadability** and **metadata pairing** locks remain (`.cursor/rules/dng-*-lock.mdc`); matrix supplies **policy / advertised / sessionOk** flags.

**Docs:** `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` · `docs/FLEET_REFERENCE_M10_8.md` (streams → matrix). **Rule:** `.cursor/rules/fleet-generic-policy.mdc`.

**Execution order:** `BUILD_PLAN.md` **Milestone 18** onward for parity sweep + catalog max-out before treating legacy device-only DNG parity as a global ship blocker.

---

## CRITICAL — Fleet Parity Sweep (Milestone 18.6)

**Name:** Fleet Parity Sweep (FPS) · log tag **`PNS.FleetParity`** · script **`scripts/pns_fleet_parity_sweep.ps1`**

**`-Mode` is required:** `Full` | `Delta`. Script exits **2** without `-Mode` (except `-Help` or human `-Interactive`).

**Agent rule:** If the user asks to run parity sweep / FPS **without** naming a mode → **AskQuestion** with Full / Delta labels. **Do not** default silently.

| Mode | Use |
|------|-----|
| `Full` | Every catalog row; optional `-IncludeRecord`; delivery verify |
| `Delta` | Rows changed since last catalog/matrix version |

```powershell
.\scripts\pns_fleet_parity_sweep.ps1 -Mode Delta
.\scripts\pns_fleet_parity_sweep.ps1 -Mode Full -IncludeRecord
.\scripts\pns_fleet_regression_pack.ps1 -Tier all
```

**Artifacts:** `hfr-runs/parity_sweep_*/parity_report.json` · `docs/FLEET_PARITY_LATEST.json` · `docs/FLEET_PARITY_HISTORY.jsonl` · `docs/FLEET_PARITY_DEBT_LEDGER.json` · `docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json` · Full mode: `delivery_mismatch.md`

**Backlog:** Each sweep runs `pns_parity_debt_ledger_refresh.ps1` + `pns_parity_build_plan_intake.ps1`. Promote `PBI-*` rows from intake into **BUILD_PLAN.md** Milestone 26. USB gates must not overlap on one serial — use `scripts/pns_usb_gate_mutex.ps1` (wired in `pns_m24_gate.ps1`).

**Docs:** `docs/FLEET_PARITY_SWEEP.md` · `docs/CAMERA_CAPABILITY_TAXONOMY.md`

---

## CRITICAL — On-device AnTuTu benchmark (leaderboard)

**Script:** `scripts/pns_antutu_benchmark.ps1` · samples **`docs/leaderboard/data/antutu_samples.json`** · history **`docs/leaderboard/data/history/antutu_samples.jsonl`**

**Agent rule:** One full AnTuTu run per invocation (~15 min). Each run appends one sample; publish computes the **mean across all samples** for a model (`maintainer_usb`, `community_submit`, `legacy_seed`). Do **not** run in parallel with **`pns_photo_capture_verify`** / **`pns_chrome_ux_gate`** on the same serial (heat + camera mutex).

```powershell
.\scripts\pns_antutu_benchmark.ps1
python scripts\antutu_samples_validate.py
.\scripts\pns_leaderboard_site_publish.ps1 -SkipGsmarenaScrape
.\scripts\pns_leaderboard_host_smoke.ps1
```

Requires AnTuTu Benchmark installed on device (Play Store). Always **`force-stop`** AnTuTu + P&S after the session. Community optional **`antutuScore`** on `pns.leaderboard_submission.v1` (Engineering Hub → optional AnTuTu total field).

---

## CRITICAL — Agent regression memory (whack-a-mole prevention)

### Before editing (read order)

1. **[`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md)** — grep for canonical doc, code entry, and gate script for your area.
2. **[`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md)** — grep target files / subsystem; obey `Do not` rows.
3. **Relevant `.cursor/rules/*-lock.mdc`** — see [Template file map](#template-file-map-milestone-t) inventory.

**Before** capture / DNG / GLES preview / fleet / session changes: complete steps 1–3. **After** any USB-proven fix or reverted experiment: **append a `REG-*` row** in the same commit (`Do not`, `Proves OK`, `Also test`).

**Rule:** `.cursor/rules/agent-regression-memory.mdc` · Deep bisect tables: **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8 · DNG loadability: **`docs/DNG_OPENABILITY_REGRESSIONS.md`**

---

## CRITICAL — sequential RAW / `pns_preview_raw_count` and preview session wiring

**Never** set **`automationSuppressFacePipeline = true`** for **`adbSequentialRawStills > 0`** alone (sequential RAW-only / `pns_preview_raw_count`). That path must keep the **same H-dial YUV / face-pipeline behavior** as manual H capture; suppressing it forced **`wantYuv=false`** and broke RAW still session create on **legacy SKU-class** stacks (`CAMERA_DISCONNECTED`). **Only `adbBracketPattern != null`** should enable **`automationSuppressFacePipeline`**. See **`README.md`** STOP banner, **`BUILD_PLAN.md`** item **11** (hard rule), and **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** (top). After any capture-session change: **`scripts/pns_photo_capture_verify.ps1`** or **`scripts/pns_capture_pipeline_verify.ps1`** on USB; after bulk restore from the bisect doc: **`scripts/pns_capture_restore_verified.ps1`**.

**Incremental restore (May 2026, legacy SKU proof):** Do **not** re-apply every §1–§5 “shipping” hunk in one commit without **per-hunk** **`pns_photo_capture_verify`** (or pipeline verify). **§4a** (stream hints on) and **§2** (RAW10 before RAW_SENSOR on `Default`) each broke scripted capture on **`legacy serial`** while other rows stayed restored; the **max verified** combo for that device keeps **§4a off** and **§2 bisected**, and restores **§1** + **§5**. Table: **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8.

---

## CRITICAL — REGULAR session stream hints (§4a) and `Default` RAW tier (§2)

**Do not** flip these back to “Milestone shipping” on **`PreviewEngineScreen.kt`** / **`RawCaptureSupport.kt`** for the dodge / **legacy SKU-class** fleet **without** a fresh USB **`pns_photo_capture_verify.ps1`** (or **`pns_capture_pipeline_verify.ps1`**) pass — they are **known regressions** on **`legacy serial`** (May 2026):

- **§4a — `streamHints = SDK_INT >= TIRAMISU` on the REGULAR session:** causes scripted RAW still **timeouts** and **`ERROR_CAMERA_DEVICE` (`onError` 4)** after capture starts (HAL never completes the still in time). **Keep** bisect **`streamHints = false`** (+ comments) unless you have **device proof** and a **narrow** OEM-specific gate.
- **§2 — `RawStreamPreference.Default` with RAW10 before RAW_SENSOR:** picks **RAW10 (format 37)**; capture can succeed but **`DngCreator.writeImage`** fails with **`Unsupported image format 37`**. **Keep** bisect order **RAW12 → RAW_SENSOR → RAW10** for **`Default`** here until the DNG pipeline explicitly supports RAW10 for this path **and** USB proof exists.

Full avoidance table + artifact paths: **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8 and **§8 “What agents must avoid”**.

---

## DNG metadata pairing (`DngMetadataResolver`) and RAW still diagnostics

**legacy SKU topology (confirmed May 2026):** Cameras 2 (UW), 3 (wide), 4 (tele) are **independent logical camera IDs** — not children of a logical multi-camera. `logicalCharacteristics.physicalCameraIds` returns **empty** for all of them. `DngMetadataResolver` therefore always produces `picked=null, pairedPhysical=false, children=` — the physical-pairing path is never exercised on this device. The dark/green DNG cast on UW/tele is a **color calibration issue** in the `CameraCharacteristics` of those camera IDs, not a metadata-pairing bug.

**`allowPhysicalTotalResultPairing` — locked `false` (shipped):** All six `resolveForDngSave` / `ReferenceAppDngCreatorPair.forSave` call sites in `PreviewEngineScreen.kt` pass **`DngSavePairingPolicy.ALLOW_PHYSICAL_TOTAL_RESULT_PAIRING`** (`false`). The resolver only uses physical chars+result when **`allowPhysicalTotalResultPairing`** is **true** and `physicalCameraTotalResults` contains the picked id; otherwise logical+logical. Do **not** flip to **true** without maintainer USB proof and physically pinned RAW outputs (see `.cursor/rules/dng-logical-multicam-metadata-lock.mdc`).

**Active investigation:** `Dng12Saver` now logs `dng color diag` (tag `PNS.Dng`) with `cm1/cm2/fm1/fm2` matrices and black/white levels for each capture. Grep this to compare wide vs UW/tele color transforms and identify the miscalibration.

**Invariants that still hold:**

1. **Never hybrid:** Never pass physical `CameraCharacteristics` with a logical `TotalCaptureResult` (or vice-versa) into `DngCreator`.
2. **`resolveForDngSave` return type** stays **`DngMetadataResolution`** (do not regress to `Pair`-only).
3. **`PreviewEngineScreen.kt`** RAW still + bracket DNG paths: keep **`Log.i`** on **`PNS.CaptureStill`** with the **`dng save diag`** line (**`toDiagSummary()`** + ISO + RAW `Image` format/size).
4. **`usePhysicalChildRawStreamMapForLogicalSession = false`** stays at all `PreviewEngineScreen.kt` call sites — RAW ImageReader format/size must follow the logical `SCALER_STREAM_CONFIGURATION_MAP`.

**Docs / scripts:** **`docs/RAW_CAPTURE_DEVICE_MATRIX.md`**; **`scripts/pns_aux_dng_triage_focal_slots.ps1`**.

---

## CRITICAL — DNG save pipeline (do not break loadability)

**May 2026 (legacy SKU / USB-verified):** DNGs were **unopenable in Lightroom and ACR** while host tools (rawpy) could still decode. Cause was **not** `DngCreator` pairing alone — it was **post-save file corruption**.

### What broke

| Step | Problem |
|------|---------|
| **`StillCaptureMetadata.applyToDngUri`** | Called **`ExifInterface.saveAttributes()`** on the full ~25 MB row-strip DNG after in-place TIFF patches. That rewrites the file like JPEG EXIF and **destroys** legacy SKU **3072-row `StripOffsets`** layout. |
| **`LeafDngHalReconcile` (removed)** | **`TiffDngColorMatrixPatch.patchCalibrationTagsIfd0`** rewrote CM/FM in IFD0 — not needed for ReferenceApp parity; risks Adobe validation failures. |

**Symptom:** “Broken” DNGs, won’t load in viewers; **`dng_tiff_integrity_check.py`** may still PASS if strips were not truncated on the pulled copy.

### Shipped fix (do not revert without maintainer sign-off + USB proof)

1. **`applyToDngUri`:** **In-place** metadata only (`TiffIfd0Software305`, `TiffExifSubIfdCapturePatch`) → write bytes to MediaStore. **No `ExifInterface` on DNG.**
2. **`LeafDngHalReconcile`:** Patch **`AsShotNeutral` only** from Bayer means (estimate **before** `DngCreator.writeImage`). No IFD0 CM/FM overwrite.
3. **DNG test scripts:** `pns_preview_jpeg_companion=false` in `pns_aux_dng_capture_analyze.ps1` and related aux DNG scripts.
4. **Gate:** `scripts/dng_tiff_integrity_check.py` — run via capture-analyze; must print **`DNG INTEGRITY: PASS`** before treating capture as valid.

**Verified on device:** `hfr-runs/aux_dng_capture_analyze_20260519_014855` (`legacy serial`) — 3/3 captures, integrity PASS, log shows `apply DNG metadata ok` without ExifInterface rewrite.

**Rule:** `.cursor/rules/dng-save-pipeline-lock.mdc`

**Lock L9 (Milestone 13):** No post-save TIFF on leaf (`LeafDngHalReconcile`, `useWideLeafCalibrationForAuxDng`) until **13.3h** bisect with **ACR open 3/3**. Regression ledger: **`docs/DNG_OPENABILITY_REGRESSIONS.md`**. Gate: **`scripts/dng_desktop_open_gate.py`** (wired in `pns_aux_dng_capture_analyze.ps1`).

---

## CRITICAL — GLES preview aspect (do not reapply reverted fixes)

**May 2026 (legacy SKU / user-verified):** Multiple attempts to fix **gallery-return** or **resume** preview stretch **broke default preview** (distorted / stretched) and were **reverted**. **Do not reintroduce** these patterns without maintainer sign-off, a **new** design, and USB proof on a real device:

1. **`LaunchedEffect`** (or similar coroutine) calling **`LutCameraPreviewRenderer.setGeometry`**, especially keyed on **`previewPipelineGeneration`**, **`previewBufferSize`**, **`centerViewSize`**, or other high-churn Compose state — races **layout** and **`RENDERMODE_WHEN_DIRTY`**.
2. **`Handler.post`**-deferred **`kickPreviewPipelineRestart()`** on **`ON_RESUME`** — ordering vs **`GLSurfaceView.onResume()`** / new **`SurfaceTexture`** was reverted as risky.
3. **`PreviewController.setPreviewBufferGeometryListener`** + coalesced **`mainHandler`** notifications + **`GLSurfaceView.queueEvent { setGeometry }`** on **`previewBufferSize()`** changes — **reverted**; user reported preview broken again.
4. **`PreviewController.setPreviewDisplayLayoutSyncListener`** + **`previewLayoutSyncNonce`** + extra **`ON_RESUME`** buffer / layout nudges — **reverted May 2026**; caused **cold-start** distortion and related regressions on **`legacy serial`**-class devices.

**Shipped invariant:** **`setGeometry`** is driven only from **`PreviewMainViewport`** — **`AndroidView` `update`** and **`OnLayoutChangeListener`**. Any future gallery/resume fix must **not** duplicate that contract with a second writer unless explicitly redesigned.

**May 2026 follow-up — what actually failed (do not repeat blindly):**

| Approach | Result |
|----------|--------|
| **`previewGeometryApplyToken`** + delayed second bump + **`AndroidView` `update`** | User: **gallery** stretch **not** fixed; extra complexity. |
| **`setPreviewDisplayLayoutSyncListener`** + pushing **`previewBufferSize()`** into Compose from **`reconcile…`** (even gated) | **Cold-start** finder / preview distortion — view-sized ST hints are **not** buffer WxH. |
| **`previewLayoutSyncNonce`** + **`ON_RESUME`** re-read **`previewBufferSize`** | Same **cold-start** class of breakage when combined with forced **`AndroidView` `update`**. |
| **`GLSurfaceView.setPreserveEGLContextOnPause(true)`** | **`Surface was abandoned`** / **`createCaptureSession`** **`IllegalArgumentException`** on **`legacy serial`** cold **`pns_photo_capture_verify`**. |
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
| `.\gradlew.bat :app:testDebugUnitTest` | JVM unit tests (`app/src/test`). |
| `.\gradlew.bat :app:koverVerifyDebug` | **40% line floor** on scoped fleet/DNG/bracket helpers — see [`docs/adr/0007-code-style-gate.md`](docs/adr/0007-code-style-gate.md). |
| `.\gradlew.bat :app:assembleRelease` | Release APK with **R8** shrink + obfuscation; `app/proguard-rules.pro` must stay **UTF-8** (no BOM). |
| `.\gradlew.bat :app:generateBaselineProfile` | Macrobenchmark baseline + startup profiles (USB device); outputs under `app\src\release\generated\baselineProfiles\`. Prefer **`scripts\pns_baseline_profile_generate.ps1`**. |

Some Gradle tasks in the baseline-profile graph (e.g. **`mergeReleaseBaselineProfile`**) can still invoke **connected** instrumentation; flaky USB or **adb** client/server version mismatches may surface as **`Connection reset`** / **`Connection refused`** from ddmlib — retry with a stable cable or aligned **adb** builds.

`pns_verify_toolchain.ps1 -RunTests` runs **Detekt**, **lintDebug**, **unit tests**, and **Kover verify** after `assembleDebug`. **SBOM:** `.github/workflows/sbom-monthly.yml` runs `pns_sbom.ps1 -Verify` on a schedule; pushes still verify SBOM via the toolchain script.

**No checked-in `androidTest/`:** there is no instrumented UI test tree in-repo. Capture, DNG, preview chrome, and fleet matrix integration proof use **USB ADB scripts** (`pns_photo_capture_verify.ps1`, `pns_chrome_ux_gate.ps1`, `pns_fleet_matrix_scan.ps1`, …) — see [`CONTRIBUTING.md`](CONTRIBUTING.md) and the automation table below.

---

## ADB device configuration (gitignored local file)

| File | Purpose |
|------|---------|
| `scripts\pns_adb_device.env` | **Local only** (gitignored). Set `PNS_ADB_SERIAL` to the USB serial from `adb devices`. |
| `scripts\pns_adb_device.env.example` | Copy to `pns_adb_device.env` and edit. |

**Behavior (shared across many scripts):**

- If `-Serial` is omitted, scripts read `PNS_ADB_SERIAL` from `scripts\pns_adb_device.env`.
- If more than one device is online, set `PNS_ADB_SERIAL` or pass `-Serial` where the script supports it.
- **`scripts\pns_adb_serial.ps1`** — dot-source module with **`Read-PnsAdbSerialFromEnvFile`** and **`Resolve-PnsAdbSerial`**; high-traffic gates should use this instead of copy-paste env parsing.

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

**USB gate SKIP contract:** Capability or host-tool pre-checks that prove a gate does not apply on the connected stack write **`gate.json`** with **`skipped=true`** and exit **0** (orchestrators treat as pass-with-skip, not fail). Examples: **`pns_4k_regular_verify.ps1`** (matrix **`fourKRegular.sessionOk=false`**), **`pns_4k120_verify.ps1`** (capability class **S0**), **`pns_jpeg_icc_verify.ps1`** (host **exiftool** missing). Use **`-Skip*GateCheck`** switches to force the attempt anyway. Real capture/encode failures still exit **1**.

| Script | Use when |
|--------|----------|
| `pns_sideload_and_launch.ps1` | Build (optional), `adb install -r`, grant camera, launch app (`-LaunchScreen preview` default). Prepends SDK **platform-tools** to PATH when **`pns_resolve_adb.ps1`** is present. |
| `pns_resolve_adb.ps1` | Prefer one **adb**: **`-EmitPath`** prints SDK **platform-tools\\adb.exe**; **`-CheckOnly`** exits **2** if PATH adb differs; dot-source **`-PrependToPath`** (**`-Quiet`**) at startup in device-facing **`scripts\\`** automation (preview validate, probes, gates, DCIM pull, screencap, Perfetto, sideload, HFR/cold-start, probe watch/append, automation smoke, etc.). |
| `pns_adb_preview_validate.ps1` | Device preview validation; **`-Milestone6Pack`** for milestone pack. |
| `pns_capture_still_forensics.ps1` | Cold **preview** + **`pns_preview_dial=H`** + **`pns_preview_raw_count`**: install (optional), pull pid + ring logcat into **`hfr-runs/capture_still_forensics_*`** (use after DNG save failures; see **`PNS.CaptureStill`**). **`-Fast`** passes **`pns_preview_raw_still_fast`** for shorter in-app ADB settle and a shorter default wait. |
| `pns_photo_capture_verify.ps1` | Loop **assembleDebug** (optional) → install → cold preview + one scripted RAW still; retries until **`PNS.AdbValidation`** shows **`captureRawStill 1/1 ok=true saved=`** or **`-MaxAttempts`**. Optional **`-SweepCameraIds`** tries **`pns_preview_camera_id`** **`(default),0,1,2,3`** in one artifact folder. Uses timeout-wrapped **adb**; artifacts **`hfr-runs/photo_capture_verify_*`** (logcat + **`run-as`** `files/PNS_CAPTURE_PIPELINE_DIAGNOSTICS.txt` when present). Logcat filter includes **`PNS.Cam:I`** for **`PNS.PreviewSessionCtx`**. Prefer **`pns_capture_pipeline_verify.ps1`** for **`docs/CAPTURE_PIPELINE_VERIFY_*.json`** (BUILD_PLAN item **11**). |
| `pns_in_app_video_verify.ps1` | Cold **preview** with **`pns_preview_primary_photo=false`** + **`pns_preview_automation_in_app_video_sec`**: install (optional), **`assembleDebug`** (optional), assert **`PNS.AdbValidation`** **`inAppVideoSaved ok=true`** and **`bytes ≥ MinBytes`**; artifacts **`hfr-runs/in_app_video_verify_*`**. Uses **`adb exec-out logcat -s …`** for OEM-stable tag dumps. Gate after in-app **`MediaRecorder`** / **`PreviewEngineScreen`** session changes alongside **`pns_capture_pipeline_verify.ps1`** when RAW session wiring moves. |
| `pns_4k_regular_verify.ps1` | **4K @ 30 H.264** in-app record when matrix **`fourKRegular.sessionOk=true`**: cold video-primary preview, **`pns_preview_video_encode_w/h=3840/2160`**, 5 s clip; asserts **`inAppVideoSaved ok=true`**. **Exit 0 SKIP** when gate false (expected on EXODUS-class). Grants legacy storage on API ≤28. Artifacts **`hfr-runs/4k_regular_verify_*`**. |
| `pns_audio_quality_test.ps1` | Sprint **AS.1** — hi-fi audio extras + scripted in-app video; asserts **`videoAudioProfile`** **`hiFi=true`** and **`sampleRate=`** 48k/96k. Artifacts **`hfr-runs/audio_quality_test_*`**. |
| `pns_shutter_sound_test.ps1` | Sprint **AS.2** — **`pns_preview_composed_still`** + **`pns_preview_shutter_sound_pack`**; asserts **`shutterSound ok=true`**. Artifacts **`hfr-runs/shutter_sound_test_*`**. |
| `pns_audio_sprint_gate.ps1` | Sprint **AS** — unit tests + optional USB **`pns_audio_quality_test`** + **`pns_shutter_sound_test`** (**`-HostOnly`** for CI). |
| `pns_video_codec_color_compare.ps1` | **Milestone 14.6** — H.264 @ 60 (MediaRecorder) vs 8-bit HEVC @ 120 (MediaCodec): **`colorVui=bt709`** in **`PNS.MCVideoRec`** + ffprobe **`color_primaries`/`color_transfer`**; artifacts **`hfr-runs/video_codec_color_compare_*`**. Do not overlap with chrome/capture gates on one device. |
| `pns_video_hdr10_metadata_verify.ps1` | Sprint **13.4** DCG session + HDR10 encode: **`pns_preview_video_dcg`** @ 60 fps, 8 s in-app record; asserts **`dcgSessionTemplate=EnableHDRDCGMode`**, **`inAppVideoFormat=DCG`**, MediaCodec path, **ffprobe** bt2020 + smpte2084 + MaxCLL; artifacts **`hfr-runs/hdr10_meta_verify_*`**. Requires **ffprobe** on PATH. |
| `pns_capture_pipeline_verify.ps1` | Wraps **`pns_photo_capture_verify.ps1`** in a child process; writes **`hfr-runs/capture_pipeline_gate_*/gate.json`**, **`docs/CAPTURE_PIPELINE_VERIFY_LATEST.json`**, appends **`docs/CAPTURE_PIPELINE_VERIFY_HISTORY.jsonl`**. Optional **`-BisectStep`**, **`-Notes`**, **`-NoHistoryAppend`**. |
| `pns_capture_bisect_device.ps1` | **USB:** cumulative bisect steps **1..N** on **`PreviewEngineScreen.kt`** + **`RawCaptureSupport.kt`** (see **`docs/REVERTED_FEATURES_RESTORE_LIST.md`**), **`assembleDebug`**, **`pns_capture_pipeline_verify`** per step; **`hfr-runs/capture_bisect_device_*/report.md`**. **`-DryRun`**, **`-Fast`**, **`-FromStep`**, **`-NoRestore`**, **`-WriteDocHistory`**. |
| `pns_capture_restore_verified.ps1` | **`assembleDebug`** + USB **`pns_capture_pipeline_verify.ps1`** after capture restores — gate **`captureRawStill 1/1 ok=true saved=`** before merge. **Do not** treat as “ship full Milestone §1–§5”; **§4a** / **§2** are fleet-sensitive — see **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8. |
| `pns_raw_regression_bisect.ps1` | **USB automation:** snapshot `RawCaptureSupport.kt` + `PreviewEngineScreen.kt`, run **`pns_photo_capture_verify`** on baseline, then re-apply **one** suspect regression at a time (wrong default RAW tier order, `desiredFps` default 120, gated H-dial YUV), rebuild, re-verify; writes **`hfr-runs/raw_regression_bisect_*/results.json`** + **`report.md`**. Exit **1** if baseline fails (bisect inconclusive on that device). Dot-source **`pns_resolve_adb.ps1 -PrependToPath`** first on Windows if PATH adb differs from SDK. |
| `pns_raw_capture_matrix.ps1` | **20-cell** matrix (optional **`-Quick`** for 4 cells): **`pns_preview_imaging_profile`** × **`pns_preview_raw_stream`** (`default`, `raw_sensor_first`, `raw12_only`, `raw_sensor_only`, `raw10_only`) × **`pns_preview_jpeg_companion`**, plus optional **`-CameraId`**. Artifacts **`hfr-runs/raw_capture_matrix_*`** (`matrix.csv`, `matrix.md`, per-cell logcat). See **`docs/RAW_CAPTURE_DEVICE_MATRIX.md`**. |
| `pns_deep_caps_diff.ps1` | Host-side **Markdown** diff of two **`deep_caps_*.json`** pulls (**HFR max**, **HDR DR** summary, **`maxNumOutputRaw`**, **`rawCapabilityAdvertised`** per `cameraId`). See **`docs/FLEET_REFERENCE_M10_8.md`** (Milestone **10.8** fleet evidence). |
| `pns_fleet_matrix_scan.ps1` | Milestone **16.3** — cold **`pns_screen=probehub`** + optional **`-ScanTier full`**, pull **`files/fleet_device_matrix.json`** → **`hfr-runs/fleet_matrix_*`**, assert **`PNS.FleetMatrix scanTier=`** + **`schemaVersion`**; runs **`fleet_matrix_schema_validate.py`** when Python on PATH; **`-Redact`** writes **`hal_dumpsys_media_camera_redacted.txt`** when full-tier appendix includes HAL excerpt. |
| `pns_fleet_parity_sweep.ps1` | Milestone **18.6** — **`-Mode Full\|Delta` required**; matrix refresh + parity ADB extras; **`parity_report.json`** + **`docs/FLEET_PARITY_LATEST.json`**. |
| `pns_antutu_benchmark.ps1` | **USB** — full-auto single AnTuTu run; appends one row to **`docs/leaderboard/data/antutu_samples.json`**. Artifacts **`hfr-runs/antutu_benchmark_*`**. ~15 min; mutex vs capture/chrome gates. |
| `antutu_samples_validate.py` | Host schema gate for **`antutu_samples.json`** (wired in **`pns_leaderboard_host_smoke.ps1`**). |
| `antutu_samples_aggregate.py` | Host helper: mean/stddev by model for publish debugging. |
| `pns_leaderboard_site_publish.ps1` | Publishes **`docs/leaderboard/data/`**; **`Match-AntutuFromSamples`** mean across **`antutu_samples.json`**. **`-MergeSubmissions`** ingests community **`antutuScore`**. |
| `pns_leaderboard_host_smoke.ps1` | Host JSON/CSV/RSS smoke including **`antutu_samples.json`**. |
| `pns_fleet_parity_diff.ps1` | Host diff two **`parity_report.json`** files. |
| `pns_fleet_regression_pack.ps1` | Milestone **18.4** — tier 1 matrix + tier 2 parity Delta + catalog gate. |
| `pns_m18_gate.ps1` | Milestone **18** one-shot — catalog gate + `pns_verify_toolchain.ps1 -RunTests` + regression pack (`-Serial` for USB). |
| `pns_fleet_macro_export.ps1` | Milestone **18.4** — cross-device CSV from latest matrix + parity artifacts. |
| `pns_capability_catalog_gate.ps1` | Milestone **18.5** — host catalog version + row count + format descriptor gate. |
| `pns_changelog_gate.ps1` | Host **CHANGELOG coverage** — latest release header, required milestone mentions, `versionCode` sync vs `scripts/changelog_coverage.v1.json` (wired in `pns_verify_toolchain.ps1`). |
| `pns_fleet_matrix_diff.ps1` | Milestone **16.3** — Markdown diff of two **`fleet_device_matrix.json`** files (HFR, RAW, roles, **`featureGates`**, encoder stub). |
| `fleet_matrix_schema_validate.py` | Milestone **16.12** — structural validation of pulled matrix JSON (schema v1, sorted **`cameraId`** on full tier). |
| `pns_legacy_regression_pack.ps1` | Milestone **16.7** — canonical wrapper → **`pns_op13_regression_pack.ps1`** (optional legacy device lane: matrix + aux DNG + parity; not default on CPH2583). |
| `pns_gen_camera2_keys_reference.ps1` | Regenerate **`docs/CAMERA2_KEYS_AND_APIS_REFERENCE.md`** from **`local.properties` → sdk.dir** `platforms/android-<N>/android.jar`; **`<N>` = `compileSdk`** parsed from **`app/build.gradle.kts`** (override **`-ApiLevel`**). |
| `pns_ae_highlight_probe_adb.ps1` | Cold-start **`pns_screen=probehub`** + **`pns_auto_export_probe`**, pull **`PROBE_EXPORT_LATEST.md`**, write **`ae_highlight_probe_summary.txt`** + **`ae_highlight_probe.json`** (`summary` path); optional **`-AlsoRootCapabilityAdb`**. **Debuggable APK** required for `run-as`. |
| `pns_face_meter_probe.ps1` | Cold-start **`pns_screen=facemeter`** + **`pns_autofacemeter`**, wait for **`FACE_METER_PROBE_DONE`** in **`PNS.SWEEP_SIGNAL`**, pull **`face_meter_probe_*.{md,json}`** (face / eye / metering inventory). Artifacts under **`hfr-runs\face_meter_probe_*`**. |
| `pns_milestone6_gate.ps1` | One-shot: assembleDebug → validate pack → optional `PROBE_BUILD_PLAN.md` §5 append. Artifacts under `hfr-runs\`. |
| `pns_milestone3_gate.ps1` | **Milestone 3** mapping gate: JVM tests (`SensorCropGeometryTest`, `CropPlanTest`, `DngDefaultUserCropRatiosTest`, `BackCameraRoleResolverTest`) + optional **`-RunDeviceSmoke`** (sideload preview + `PNS.ChromeUx` **`seedOk slot=M23`** log grep). |
| `pns_automation_smoke.ps1` | Automation smoke; optional **`-RunAeHighlightProbe`** chains **`pns_ae_highlight_probe_adb.ps1`** (debug APK + `run-as` pull). |
| `pns_chrome_ux_gate.ps1` | Chrome UX gate; optional **`-FocalMmSlot`** (`14`…`150`, default **`85`**) appends **`pns_preview_focal_mm_slot`** for **`focalSlotTap=`** tele proof (**`teleFocalSlotOk`**). |
| `pns_aperture_readout_verify.ps1` | Variable-aperture readout: cold preview + **`pns_preview_aperture_cycles`** (default **2**) on **`pns_preview_camera_id`** (default **`2`**); asserts **`PNS.AdbValidation apertureCycle ok=true`**, **`apertureCycle`** f/2↔f/4 lines, **`apertureInit variable=true`**. Artifacts **`hfr-runs/aperture_readout_verify_*`**. |
| `pns_ux_sprint_adb_gate.ps1` | Sprint **UX** one-shot: theme Dark/Light, nav mode matrix (`settings secure navigation_mode` 0/2), gallery batch share, `KEYCODE_BACK`, workflow presets. Artifacts **`hfr-runs/ux_sprint_adb_gate_*`**. |
| `pns_ui_modernization_test.ps1` | UX.1 theme ADB (`pns_preview_theme_mode`). |
| `pns_navigation_compatibility_test.ps1` | UX.2 nav telemetry + gallery BACK smoke. |
| `pns_workflow_test.ps1` | UX.3 presets; **`-AllPresets`** for street/portrait/video_log. |
| `pns_ux_gallery_batch_test.ps1` | UX.3 gallery **`pns_preview_open_gallery`** + **`pns_preview_gallery_batch_share`**. |
| `pns_cloud_backup_test.ps1` | UX.3 cloud backup probe sync (`pns_preview_cloud_backup_probe`). |
| `pns_platform_integration_test.ps1` | **IP.1** — deep link, FileProvider, widget, share probe extras. |
| `pns_connectivity_test.ps1` | **IP.2** — LAN HTTP `/status`, WebDAV/social/collab/cloud probes. |
| `pns_video_status_bar_verify.ps1` | Sprint **14.2**: cold video-primary preview + **`pns_preview_automation_in_app_video_sec`**; asserts **`PNS.ChromeUx`** **`statusBar=visible`**, **`readoutMode=video`**, **`audioMeters=true`** while recording. Artifacts **`hfr-runs/video_status_bar_verify_*`**. |
| `pns_dnd_restore_verify.ps1` | Sprint **14.10**: preview DND **`dndPreview=applied`** → HOME **`restored`** → relaunch **`applied`**; **`dumpsys notification`** head; JVM **`InterruptionFilterHoldTest`**. Artifacts **`hfr-runs/dnd_restore_verify_*`**. |
| `pns_about_links_verify.ps1` | Sprint **14.11**: JVM **`PnsExternalUrlTest`** + HTTP HEAD/GET on locked Venmo donation URL. |
| `pns_eye_af_alignment_probe.ps1` | Sprint **14.5**: **`TexturePreviewFitTest`** + **`CaptureMediaFamilyTest`**; optional USB crosshair log (`PNS.FaceAlign`). Default **`-HostOnly`**. |
| `pns_dual_video_verify.ps1` | Sprint **14.12**: design doc + JVM scaffold tests; **`-HostOnly`** default; USB scaffold log when device online. |
| `pns_release_packaging.ps1` | Sprint **14.13**: **`assembleRelease`**, rename **`Point-and-Shoot-{versionName}.apk`**, **`zipalign -c -v 4`** (no `gh`). Naming: **`pns_release_naming.ps1`**. |
| `pns_github_release.ps1` | **GitHub release orchestrator** — `-PrepareOnly` (version + CHANGELOG + coverage + About tag constant), `-Publish` (package APK, `gh release` with changelog body + **`CHANGELOG.md`** asset). Skill: `.cursor/skills/github-release/SKILL.md`. |
| `pns_qr_scan_verify.ps1` | Sprint **14.4**: cold preview **`pns_preview_dial=QR`** + photo-primary; asserts **`qrScanMode=active`** and **`PNS.PreviewSessionCtx`** **`dial=Qr wantYuv=true`**. Artifacts **`hfr-runs/qr_scan_verify_*`**. |
| `pns_memory_profiler.ps1` | Sprint **PO.1**: one-session preview + **`pns_preview_raw_count=1`**; greps **`PNS.MemoryProfiler`**, RAW capture ok, **`dumpsys meminfo`**. Artifacts **`hfr-runs/memory_profiler_*`**. |
| `pns_battery_life_test.ps1` | Sprint **PO.2**: JVM **`PreviewAdaptiveFpsPolicyTest`** + USB adaptive FPS cap (`pns_preview_adaptive_battery_pct`) + lifecycle **`longRunningPaused`**. Artifacts **`hfr-runs/battery_life_test_*`**. |
| `pns_po_optimization_gate.ps1` | **PO optimization gate:** **`pns_memory_profiler.ps1`** + **`pns_battery_life_test.ps1`** → **`hfr-runs/po_optimization_gate_*`**. |
| `pns_video_format_test.ps1` | **VF.1** — **`PNS.VideoCapProbe`** `av1=` probe; optional **`-RunAv1Record`** (`pns_preview_video_av1`). Artifacts **`hfr-runs/video_format_test_*`**. |
| `pns_video_stabilization_test.ps1` | **VF.2** — video-primary preview + **`pns_preview_video_stabilization`**; greps **`PNS.VideoEffects videoStabilization`**. Artifacts **`hfr-runs/video_stabilization_test_*`**. |
| `pns_video_quality_gate.ps1` | **VF** gate: JVM + **`pns_mediacodec_hfr_verify.ps1 -GateProfile vf`** (H.264/H.265 @ 60, HEVC **120/240/480** @ 1080p, **ffprobe** audio+video on pulled MP4) + AV1 probe + stabilization. Requires **ffprobe** on PATH. Artifacts **`hfr-runs/video_quality_gate_*`**. |
| `pns_mediacodec_hfr_verify.ps1` | HFR/codec matrix; **`-GateProfile vf`** = VF subset; **`-RequireFfprobeAv`** = fail without audio+video streams in MP4; emits 4K120 truth classes (`true_4k120`, `hs120_sub4k`, `blocked_unstable`) in `summary.json` for `4K_120fps_MediaCodec`. |
| `pns_4k120_verify.ps1` | USB one-shot **4K @ 120** H.264 MediaCodec on Sony-class devices — wraps **`-OnlyTest 4K_120fps_MediaCodec`** + **ffprobe**. **Exit 0 SKIP** when capability class **S0** (no 4K120 encoder path). |
| `pns_4k120_endurance.ps1` | M24 endurance sweep for strict 4K120 (`bestPassSec`, terminal reason) via stepped `pns_mediacodec_hfr_verify` runs; artifacts `hfr-runs/4k120_endurance_*/endurance_report.{json,md}`. |
| `pns_m24_gate.ps1` | Milestone 24 orchestrator: capability probe → strict 4K120 → endurance → parity Full → toolchain verify (`-HostOnly` for CI host lane). |
| `pns_aux_dng_capture_analyze.ps1` | M14/M23/M73 scripted RAW stills, pull DNGs, **`dng_desktop_open_gate.py`** (13.3g **hard fail**), **`dng_tiff_integrity_check.py`**, **`dng_referenceapp_parity_gate.py`** (informational unless **`-RequireProshotParity`**), informational **`structural_verify.py`**. **`pns_preview_jpeg_companion=false`**. |
| `dng_desktop_open_gate.py` / `pns_dng_desktop_open_gate.ps1` | Host-only: integrity + ASN bounds + wide-cal CM2 leak check on pulled DNGs. |
| `pns_fixture_dng_gates.ps1` | Host-only CI: openability gate on `tests/fixtures/referenceapp_legacy_sku/` (toolchain-verify workflow). |
| `pns_m13_3g2_gate.ps1` | **13.3g-2:** open gate + logcat diag on `aux_dng_capture_analyze_*`; **`-RecordAcrPass`** for Milestone H ACR sign-off. |
| `pns_m13_3h_wide_cal_bisect.ps1` | **13.3h:** USB H1–H3 wide-cal bisect (patches `LegacyDeviceFleetPolicy.kt`, restores after); artifacts `hfr-runs/m13_3h_wide_cal_bisect_*`. |
| `pns_m13_3e_lock_bisect.ps1` | **13.3e:** USB E1–E6 lock ladder (L2,L3,L6,L4,L5,L7); patches policy + PreviewEngineScreen + RawCaptureSupport, restores after. |
| `pns_m13_3f_gate.ps1` | **13.3f:** daylight gates (pipeline verify, capture analyze, openability, ReferenceApp parity, optional session); `-RecordAcrPass` for human color sign-off. |
| `pns_m13_3g4_fixture_refresh.ps1` | **13.3g-4:** ReferenceApp live forensics (15/23/73 mm) + `pns_referenceapp_reference_sync.ps1 -FromForensicsDir` + parity gate. |
| `pns_dng_referenceapp_pns_session.ps1 -HostOnly` | Host-only: runs fixture gates without USB (full session needs device + ReferenceApp captures). |
| `pns_still_mode_benchmark.ps1` | **13.8d** — `-Mode standard\|zsl\|hdr\|all`; `results.json` + `report.md` (timing, openability, ZSL/HDR notes). |
| `pns_m13_8d_gate.ps1` | **13.8d** — pipeline verify (`stillMode=standard`) + benchmark all; optional `-PnsStillModes` session via `pns_dng_referenceapp_pns_session.ps1`; `-RecordHumanPass`. |
| `pns_raw_video_verify.ps1` | Sprint **13.6** RAW video: **`pns_preview_video_raw_sec`** @ 30 fps, wide **`camera_id=2`**; asserts **`rawVideoSaved ok=true`**, frame count, **`PNMRAWV1`** header (`xxd` on device, no full pull by default); artifacts **`hfr-runs/raw_video_verify_*`**. Optional **`-PullMcraw`** (multi-GB). |
| `pns_m13_lock_bisect_host.ps1` | Host template for **13.3e** lock bisect report. |
| `pns_referenceapp_parity_gate.ps1` | One-shot: capture + pull + ReferenceApp color/luminance parity (fails if DNGs not loadable or not close to reference). |
| `pns_referenceapp_live_forensics.ps1` | **Live** ReferenceApp session: stream logcat, detect `CameraService::connect … camera ID`, pull DCIM DNGs per lens; **`-TryUiAutomation`** or manual (see **`docs/REFERENCEAPP_LIVE_FORENSICS.md`**). |
| `pns_referenceapp_adb_forensics.ps1` | Post-hoc logcat + dumpsys after **manual** ReferenceApp captures (no per-lens automation). |
| `pns_referenceapp_reference_sync.ps1` | Refresh **`tests/fixtures/referenceapp_legacy_sku/`** from newest 3 non–P&S DCIM DNGs. |
| `dng_tiff_integrity_check.py` | Host: row-strip TIFF + rawpy load check. Exit **1** if DNG structure broken (e.g. post-save **`ExifInterface`** regression). |
| `pns_failure_matrix_smoke.ps1` | Failure-matrix smoke. |
| `pns_hfr_autorun.ps1` | HFR autorun (`-PerfReport`, **`-PerfReportApkVariant Release`**, etc.). |
| `pns_cold_start_capture.ps1` | **`pns_hfr_autorun.ps1 -PerfReport`** → **`perf-runs/perf_*.md`** (or **`-Release`** → **`perf_release_*.md`** + assemble/install Release); optional **`-Serial`**, **`-SkipGradleBuild`**. |
| `pns_perf_budget_host_gate.ps1` | Milestone **T.8** — `PERFORMANCE_BUDGETS.md` ↔ `PerfBudget.kt` / `PerfBudgetTest` drift (wired in **`pns_verify_toolchain.ps1`**). |
| `pns_fdroid_metadata_validate.ps1` | Milestone **T.10** — `metadata/metadata.yml` + en-US store assets vs `app/build.gradle.kts`; wired in **`pns_prerelease_gate.ps1`**. |
| `pns_repro_build_verify.ps1` | Milestone **T.11** — version sync, lockfile, PRIVACY/NOTICE README links, SBOM purl fingerprint; optional **`-ApkPath`** cert class. |
| `pns_local_dev_parallel.ps1` | Milestone **T.15** — Tier 0 host gates (no Gradle); **PS7+** runs jobs in parallel, **PS5.1** falls back to sequential — see **`docs/LOCAL_FIRST_DEV_LOOP.md`**. |
| `pns_agent_worktree_bootstrap.ps1` | Milestone **T.15** — `-Create` / `-Remove` / `-List` for `feature/agent-*` worktrees. |
| `pns_milestone_t_gate.ps1` | Milestone **T** closure — `pns_local_dev_parallel.ps1` + `pns_prerelease_gate.ps1 -SkipGradle`; writes `hfr-runs/milestone_t_gate_*/`. |
| `pns_prerelease_gate.ps1` | Milestone **T.12** — full pre-release orchestrator; **`-SkipGradle`** host subset; **`-IncludeUsb`** USB subset. |
| `pns_a11y_dump_gate.ps1` | USB preview a11y — `uiautomator dump`; focusable buttons must have `content-desc` (chrome locked; not CI). |
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

## Local-first dev loop (Tier 0–4)

**Do not wait on CI** for doc/fixture/metadata drift. This repo is **Kotlin/Gradle + PowerShell** — not a Python monorepo (`ruff`/`mypy`/`pyright` do not apply globally; Python runs only inside specific scripts).

| Tier | Script | When |
|------|--------|------|
| **0** | `pns_local_dev_parallel.ps1` | While editing — 7 host gates in parallel (~5–15s) |
| **1** | `pns_prerelease_gate.ps1 -SkipGradle` | Before commit — full prerelease host lane |
| **2** | `pns_verify_toolchain.ps1 -RunTests` | Before push — Gradle + Detekt + lint + unit tests + Kover |
| **3** | USB gates (sequential, one serial) | Capture **or** chrome — never parallel on same device |
| **4** | `pns_prerelease_gate.ps1` (+ `-IncludeUsb` when device online) | Pre-release / ship |

Full matrix: **`docs/LOCAL_FIRST_DEV_LOOP.md`**. VS Code tasks: **Run Task → P&S: …**

---

## Multi-agent parallel orchestration (up to 8 agents)

Before splitting a milestone across parallel Cursor agents or Cloud VMs:

1. **Worktree + branch** — `feature/agent-<slug>` via `pns_agent_worktree_bootstrap.ps1 -Create`
2. **Asymmetric scoping** — no overlapping file paths between concurrent agents
3. **Shared schema lock** — one sequential agent merges `libs.versions.toml`, fleet matrix/catalog schema, DNG/session locks, version/changelog ship files **before** parallel workers start

Full guardrails: **`docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md`** · rule **`.cursor/rules/multi-agent-parallel.mdc`** · prompts **`PROMPT_LIBRARY.md`** §12–§13

---

## Cursor workspace rules (do not ignore)

| Rule | Summary |
|------|---------|
| `.cursor/rules/adb-device-env.mdc` | ADB env file, `PNS_ADB_SERIAL` (USB), script entry points. |
| `.cursor/rules/dodge-tele-focal-routing.mdc` | **Locked** dodge tele **73/85/150 mm** routing + crop gates — no fleet policy; physical tele preferred when enumerated; see **`AGENTS.md`** CRITICAL section. |
| `.cursor/rules/dng-logical-multicam-metadata-lock.mdc` | **Locked** **`DngMetadataResolver`** (**`allowPhysicalTotalResultPairing=false`**), **`RawCaptureSupport.pickRawOutputForPreviewSession`** (**`usePhysicalChildRawStreamMap=false`**, logical-map **`RAW_SENSOR`** when **`shouldPreferRawSensorForAuxPhysicalPreviewPin`** — do not rely on **`Default`**/RAW12 alone on logical), **`PNS.CaptureStill`** **`dng save diag`**; see **`AGENTS.md`** DNG metadata pairing. |
| `.cursor/rules/dng-save-pipeline-lock.mdc` | **Locked** post-save DNG path — **no `ExifInterface.saveAttributes` on DNG**; leaf reconcile **AsShotNeutral only**; **`dng_tiff_integrity_check.py`** gate; see **`AGENTS.md`** CRITICAL — DNG save pipeline. |
| `.cursor/rules/preview-chrome-ui-lock.mdc` | **Frozen** preview chrome layout — behavioral fixes only unless the user explicitly changes UI. |
| `.cursor/rules/preview-readout-video-mode-lock.mdc` | **Locked** photo vs video readout chips + `PreviewTopStatusBar` wiring — see **`docs/M14_READOUT_STATUS_BAR.md`**. |
| `.cursor/rules/pns-technical-settings.mdc` | **`docs/PNS_TECHNICAL_SETTINGS.md`** must stay in sync with settings/constants/mode behavior changes. |
| `.cursor/rules/changelog-coverage.mdc` | **`CHANGELOG.md`** + **`scripts/changelog_coverage.v1.json`** must stay in sync on milestone ship / `versionCode` bump; gate: **`pns_changelog_gate.ps1`**. |
| `.cursor/rules/fleet-generic-policy.mdc` | **Fleet matrix SoT**; CPH2583 primary; legacy device optional regression; no new legacy device-only gates without plugin. |
| `.cursor/rules/agent-regression-memory.mdc` | Read/update **`docs/AGENT_REGRESSION_MEMORY.md`** before risky edits; append row after proven fixes. |
| `.cursor/rules/multi-agent-parallel.mdc` | Parallel agents: worktrees, asymmetric file scoping, shared schema lock — **`docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md`**. |
| `.cursor/rules/session-checkpoint.mdc` | Optional milestone handoff via `.cursor-session-state` + **`AGENT_MEMORY.md`**. |
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
