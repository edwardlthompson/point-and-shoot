# Agent regression memory

**Purpose:** Append-only causal ledger so agents do not re-break fixed subsystems (“whack-a-mole”). Read **before** risky edits; append a row **in the same commit** as a USB-proven fix or a reverted attempt.

**Related (deeper detail):** [`REVERTED_FEATURES_RESTORE_LIST.md`](REVERTED_FEATURES_RESTORE_LIST.md) · [`DNG_OPENABILITY_REGRESSIONS.md`](DNG_OPENABILITY_REGRESSIONS.md) · [`AGENTS.md`](../AGENTS.md) · `.cursor/rules/*.mdc`

**Rule:** `.cursor/rules/agent-regression-memory.mdc`

---

## How to use

1. **Before** changing capture session, DNG save, GLES preview, fleet policy, or chrome layout: `rg` this file + the file you will edit.
2. **After** a fix: add a row with `Proves OK` (script or log needle) and `Also test` (cross-subsystem gates).
3. **Supersede** old rows (do not delete): set `Status: superseded by REG-…`.

### Row template

```markdown
### REG-YYYYMMDD-NNN — Short title
- **Status:** active | superseded
- **Area:** capture | dng | preview | fleet | video | chrome
- **Symptom:** what the user or gate saw
- **Cause:** root cause (one sentence)
- **Fix shipped:** what we did
- **Do not:** what must not come back without USB proof
- **Proves OK:** `scripts/….ps1` and/or log tag
- **Also test:** other gates when touching nearby code
- **Touches:** `File.kt`, …
- **Conflicts with:** other REG ids or milestones
```

---

## Active entries

### REG-20260603-001 — Variable aperture readout (per-cameraId map)

- **Status:** active
- **Area:** chrome / capture
- **Symptom:** Focal-row lens switch on variable-aperture devices (Sony Xperia PRO-I) reset main-lens f/4.0 when visiting UW/tele
- **Cause:** Global aperture override cleared on every `setDesired` camera change
- **Fix shipped:** `PreviewApertureSupport` + readout **F** chip; `apertureByCameraId` map; `LENS_APERTURE` via `applyReadoutAperture` in `applyReadoutManualExposureAndWb`
- **Do not:** Clear `apertureByCameraId` on focal-row camera switches; do not hardcode Xperia SKUs — use HAL `LENS_INFO_AVAILABLE_APERTURES` only
- **Proves OK:** USB XQ-BE62 `PNS.ChromeUx apertureInit cameraId=2 options=f/2.0, f/4.0 pick=f/2.0 variable=true control=true`
- **Also test:** `pns_video_status_bar_verify.ps1`; `pns_photo_capture_verify.ps1` on fleet primary (fixed f-stop chip, non-interactive)
- **Touches:** `PreviewApertureSupport.kt`, `PreviewReadoutStrip.kt`, `PreviewEngineScreen.kt`, `FleetUiVisibilityGate.kt`, `CameraCapabilityCatalog.kt`

### REG-20260513-001 — §4a streamHints on REGULAR session

- **Status:** active
- **Area:** capture
- **Symptom:** Scripted RAW still timeout; `onError` 4 (`ERROR_CAMERA_DEVICE`)
- **Cause:** `streamHints = SDK_INT >= TIRAMISU` on REGULAR preview session (HAL never completes still in time)
- **Fix shipped:** `streamHints = false` + bisect comments in `PreviewEngineScreen.kt`
- **Do not:** Re-enable §4a without per-hunk `pns_capture_pipeline_verify.ps1` on primary fleet device
- **Proves OK:** `scripts/pns_capture_pipeline_verify.ps1` — fail artifact `photo_capture_verify_20260513_040047` when enabled
- **Also test:** `pns_photo_capture_verify.ps1`; matrix `sessionOk` for RAW after **16.1**
- **Touches:** `PreviewEngineScreen.kt` (`createRegularCaptureSessionWithRetries`)
- **Conflicts with:** Milestone 13 lock **L4** “turn streamHints on” without fresh USB proof

### REG-20260513-002 — Default RAW tier RAW10 before RAW_SENSOR

- **Status:** active
- **Area:** capture / dng
- **Symptom:** Capture completes; `DngCreator.writeImage` → `Unsupported image format 37`
- **Cause:** `RawStreamPreference.Default` order **RAW12 → RAW10 → RAW_SENSOR** (Milestone 10.1) on legacy SKU-class
- **Fix shipped:** Bisect order **RAW12 → RAW_SENSOR → RAW10** in `RawCaptureSupport.kt`
- **Do not:** Make RAW10 first in `Default` without DNG path support + USB proof
- **Proves OK:** `photo_capture_verify_20260513_040401` (fail when wrong order)
- **Also test:** `pns_aux_dng_capture_analyze.ps1` when changing RAW pick
- **Touches:** `RawCaptureSupport.kt`, `PreviewEngineScreen.kt` stream setup
- **Conflicts with:** REG-20260513-001 (fix capture session before RAW tier experiments)

### REG-20260519-001 — ExifInterface.saveAttributes on full DNG

- **Status:** active
- **Area:** dng
- **Symptom:** ~25 MB DNG; Lightroom/ACR refuse; rawpy may still decode
- **Cause:** `StillCaptureMetadata.applyToDngUri` rewrote row-strip TIFF like JPEG EXIF
- **Fix shipped:** In-place IFD patches only; write bytes back; **no** `ExifInterface` on DNG
- **Do not:** Any full-file EXIF rewrite on DNG; `LeafDngHalReconcile` IFD0 CM/FM overwrite
- **Proves OK:** `scripts/dng_tiff_integrity_check.py` + `dng_desktop_open_gate.py` via `pns_aux_dng_capture_analyze.ps1`
- **Also test:** Desktop open on pulled DNG after metadata changes
- **Touches:** `StillCaptureMetadata.kt`, `Dng12Saver.kt`, `LeafDngHalReconcile.kt`
- **Conflicts with:** REG-20260520-001 (wide-cal CM leak)

### REG-20260520-001 — Wide-cal CM/FM on aux leaf DNG (13.3h)

- **Status:** active
- **Area:** dng / fleet
- **Symptom:** Desktop open gate FAIL; CM2 matches wide on UW/tele
- **Cause:** `useWideLeafCalibrationForAuxDng=true` copies wide calibration to aux cameras
- **Fix shipped:** **L9:** `useWideLeafCalibrationForAuxDng=false`; pure `DngCreator` + ASN-only reconcile when enabled
- **Do not:** Re-enable wide-cal on aux without open gate 3/3 + ACR
- **Proves OK:** `hfr-runs/m13_3h_wide_cal_bisect_*` H1–H3 FAIL; shipped L9 PASS `aux_dng_capture_analyze_20260519_155213`
- **Also test:** `dng_proshot_parity_gate.py` only on legacy device regression lane
- **Touches:** `LegacyDeviceFleetPolicy.kt`, `Dng12Saver.kt`, `LeafDngHalReconcile.kt`
- **Conflicts with:** Milestone 16 generic fleet policy (**16.4**)

### REG-20260512-001 — automationSuppressFacePipeline for sequential RAW only

- **Status:** active
- **Area:** capture
- **Symptom:** `CAMERA_DISCONNECTED`; `wantYuv=false` on H-dial path; RAW session fails
- **Cause:** Suppressing face pipeline for `adbSequentialRawStills` alone forced `wantYuv=false`
- **Fix shipped:** Only `adbBracketPattern != null` sets `automationSuppressFacePipeline`
- **Do not:** Tie suppress flag to `pns_preview_raw_count` / sequential RAW alone
- **Proves OK:** `pns_photo_capture_verify.ps1` with sequential RAW extras
- **Also test:** `pns_capture_pipeline_verify.ps1`
- **Touches:** `PreviewEngineScreen.kt`
- **Conflicts with:** REG-20260513-001

### REG-20260526-001 — GLES setGeometry second writer / resume experiments

- **Status:** active
- **Area:** preview
- **Symptom:** Cold-start or gallery-return preview stretched / distorted
- **Cause:** Duplicate `setGeometry` from Compose coroutines, buffer-size listeners, or `preserveEGLContextOnPause`
- **Fix shipped:** `setGeometry` only from `PreviewMainViewport` `AndroidView` update + layout listener; tray viewer → cold task restart when viewer opened
- **Do not:** `LaunchedEffect` on `previewPipelineGeneration`; `setPreviewDisplayLayoutSyncListener`; `setPreserveEGLContextOnPause(true)` without new design + USB proof
- **Proves OK:** User visual on device; avoid `pns_photo_capture_verify` cold-start `IllegalArgumentException` after preserve-EGL experiments
- **Also test:** `pns_chrome_ux_gate.ps1` after chrome-adjacent preview changes
- **Touches:** `PreviewMainViewport`, `LutCameraPreviewRenderer.kt`, `PreviewController.kt`
- **Conflicts with:** REG-20260513-001 (session recreate ordering)

### REG-20260528-001 — legacy device as default fleet proof device

- **Status:** active
- **Area:** fleet / process
- **Symptom:** Agents optimize for legacy SKU; fixes do not generalize; whack-a-mole on other SKUs
- **Cause:** BUILD_PLAN and gates referenced `legacy serial` / legacy SKU as sole truth
- **Fix shipped:** Primary device **CPH2583**; legacy device archived; `fleet_device_matrix.json` SoT (**16.0+**); verify matrix per SKU
- **Do not:** New Priority-1 work requiring legacy SKU only; legacy device color policy on unknown SKUs without plugin
- **Proves OK:** `docs/FLEET_DEVICE_VERIFY_MATRIX.md` row + `pns_fleet_matrix_scan.ps1` (when **16.3** lands)
- **Also test:** Gates on primary device for capture/video/chrome changes
- **Touches:** `BUILD_PLAN.md`, `AGENTS.md`, `FleetCameraProfileBuilder.kt` (**16.4**)
- **Conflicts with:** REG-20260520-001 (legacy device regression lane only)

### REG-20260528-002 — Parallel chrome + capture gates on one device

- **Status:** active
- **Area:** process
- **Symptom:** `ERROR_CAMERA_DEVICE` false failures in capture scripts
- **Cause:** `pns_chrome_ux_gate` and `pns_photo_capture_verify` overlapping cold starts
- **Fix shipped:** Documented in `AGENTS.md` — run gates **sequentially** on one USB device
- **Do not:** Parallel automation on same serial
- **Proves OK:** Clean single-script runs
- **Also test:** N/A
- **Touches:** `scripts/pns_*.ps1` operator discipline
- **Conflicts with:** none

### REG-20260528-003 — Dodge tele via logical 0 only (fleet routing)

- **Status:** active
- **Area:** capture / chrome
- **Symptom:** 85/150 mm look identical to 73 mm; wrong crop basis
- **Cause:** `FocalRoutingPolicy` / logical parent when physical tele id listed
- **Fix shipped:** Physical tele preferred; `LongTele150` on mid-tele sensor only — see `DODGE_PROFILE.md`
- **Do not:** Second fleet tele policy enum; `longTeleId` for 150 mm M-slot
- **Proves OK:** `pns_chrome_ux_gate.ps1 -FocalMmSlot 150`
- **Also test:** `pns_photo_capture_verify.ps1` alone (not parallel with chrome gate)
- **Touches:** `BackCameraRoleResolver.kt`, `SensorCropGeometry.kt`, `PreviewEngineScreen.kt`
- **Conflicts with:** Milestone 16 generic `product` slots (**16.4**)

### REG-20260529-001 — Consumer chrome ghost content-desc for hidden features

- **Status:** active
- **Area:** chrome | fleet
- **Symptom:** Accessibility / UX gate sees toggles or focal chips labeled “unavailable” for features matrix marks unsupported
- **Cause:** Chrome showed disabled controls instead of **hiding** per M17 policy; focal row kept chips with `", unavailable"` in contentDescription
- **Fix shipped:** M17 **`FleetUiVisibilityGate`** + **`FleetChromeVisibility`** — empty QS/focal cells, filtered mode dial / format picker / settings rails; readout STAB/IMG gated
- **Do not:** Reintroduce disabled-but-visible catalog features on consumer chrome; do not remove 7×3 slot definitions when hiding
- **Proves OK:** `scripts/pns_chrome_ux_gate.ps1`; log `PNS.FleetVisibility hidden feature=`
- **Also test:** `pns_fleet_matrix_scan.ps1` after matrix-affecting changes; hub `ProbeHubSearch` pick → `PNS.ProbeHub settingsSearchPick`
- **Touches:** `PreviewEngineScreen.kt`, `FleetChromeVisibility.kt`, `PreviewReadoutStrip.kt`, `PreviewCommandDialDropdownMenu.kt`
- **Conflicts with:** `preview-chrome-ui-lock.mdc` (geometry locked; behavioral hide only)

### REG-20260529-002 — 1080p@30 missing from video format picker after rescan

- **Status:** active
- **Area:** video | fleet
- **Symptom:** H.264 1080p@30 absent until cold restart; probe cache stale after hub matrix rescan
- **Cause:** `MediaCodecCapabilityProbe` omitted 1080p@30 tier; H.264 ≤60 required exact encoder perf point; probe cache not invalidated on matrix save
- **Fix shipped:** Probe tier + MR baseline fps union; `invalidateAndReprobe()` in `FleetDeviceMatrixBuilder`; H.264 ≤60 via `supportsMediaRecorderOutputSize`; preview watches matrix epoch on ON_RESUME
- **Do not:** Require exact H.264 perf point for ≤60 fps when HAL MR lists the size; do not skip probe invalidation on matrix rescan
- **Proves OK:** `PNS.VideoCapProbe capProbeInvalidate`; video format catalog refresh after hub rescan on CPH2583
- **Also test:** `pns_video_capability_probe.ps1`; `pns_chrome_ux_gate.ps1` in video mode
- **Touches:** `MediaCodecCapabilityProbe.kt`, `InAppVideoFormatSelection.kt`, `FleetDeviceMatrixBuilder.kt`, `PreviewEngineScreen.kt`
- **Conflicts with:** none

### REG-20260530-001 — Parity sweep false pass (beta.5)
- **Status:** active
- **Area:** fleet
- **Symptom:** Full sweep reported `pass=true`, `gapAdvertisedNotProven=0` while logcat showed many `provenOk=false` cells; broken `parityCell=([^\s]+)` parser; stale APK with `-SkipInstall`; Partial auto-pass in Full
- **Cause:** Host script ignored in-app JSON; logcat regex captured only first token after `=`; quick matrix lacked per-camera `featureGates`; Partial rows auto-passed in Full mode
- **Fix shipped:** M21 — `pns.fleet_parity_sweep.v2` JSON in `files/`; `FleetParityLogcatParser`; honest `gapBreakdown` + `shipBlockerGapCount`; `pns_m21_gate.ps1`; quick-tier `featureGatesShallow()` in matrix builder
- **Do not:** Revert to v1 host-only logcat heuristic as pass criteria; do not auto-pass Partial in Full without `parityProofScript` or `sessionOk`; do not skip APK install on Full without version preflight
- **Proves OK:** `scripts/pns_m21_gate.ps1`; `PNS.FleetParity sweepComplete`; `run-as cat files/parity_report_quick.json`
- **Also test:** `pns_fleet_regression_pack.ps1 -Tier 2`; catalog gate on `fleet/*` PRs
- **Touches:** `FleetParitySweepRunner.kt`, `pns_fleet_parity_sweep.ps1`, `FleetDeviceMatrixBuilder.kt`
- **Conflicts with:** none

### REG-20260531-001 — Experimental max-res lane must fail closed
- **Status:** active
- **Area:** capture | fleet
- **Symptom:** Experimental unlock automation can report success without proving stream-size delta, and forced safe mode can silently drift if new seeds bypass flag reset.
- **Cause:** Proof automation only compared stock binned/max mode and did not verify experimental-vs-stock delta + safe-mode fail-closed behavior in one run.
- **Fix shipped:** Added ADB seeds for experimental toggles/safe mode in preview startup (`pns_preview_experimental_*`, `pns_preview_force_safe_mode`) and upgraded `pns_still_resolution_mode_verify.ps1` to a 3-scenario matrix (`baseline`, `experimental`, `safe_mode_forced`) with rollback signals.
- **Do not:** Claim max-res unlock progress without `unlockProducedDelta=true` and `safeModeForced.failClosed=true` in `still_resolution_mode_verify_summary.json`.
- **Proves OK:** `scripts/pns_still_resolution_mode_verify.ps1`; log needles `preview seeded experimental ...`, `preview seeded experimental safeMode=true (forced)`, `maxResUnlock active=... applied=...`.
- **Also test:** `scripts/pns_fleet_parity_sweep.ps1 -Mode Quick` (ensure `experimentalUnlockState` emitted) and `scripts/pns_root_privileged_smoke.ps1` (root wiring) after unlock lane edits.
- **Touches:** `CameraCapabilitiesProbe.kt`, `PreviewEngineScreen.kt`, `scripts/pns_still_resolution_mode_verify.ps1`, `scripts/pns_fleet_parity_sweep.ps1`, `scripts/pns_root_privileged_smoke.ps1`
- **Conflicts with:** REG-20260512-001, REG-20260530-001

### REG-20260602-001 — M22 parity closure requires gap-zero gate semantics
- **Status:** active
- **Area:** fleet
- **Symptom:** `pns_fleet_parity_sweep.ps1` remained non-zero for non-M22 ship-blocker classes even when proof-pack merge produced `unautomated=0`, `not_proven=0`, `planned=0`; this blocked M22 closure automation.
- **Cause:** M22 gate consumed parity sweep exit code directly, but Milestone 22 success criteria are explicitly the three gap counters after proof merge.
- **Fix shipped:** Added ownership map + host ownership coverage gate (`docs/M22_PROVIDER_OWNERSHIP.json`, `pns_capability_catalog_gate.ps1`), hardened proof scripts/manifest matrix gates, and updated `pns_m22_gate.ps1` to treat non-zero parity sweep exit as non-blocking when gap-zero criteria are met.
- **Do not:** Regress M22 closure to raw parity sweep exit-only semantics without checking merged gap counters.
- **Proves OK:** `scripts/pns_m22_gate.ps1 -AssembleDebug -SkipInstall -SkipMatrixRefresh` -> `hfr-runs/m22_gate_20260602_011300/m22_gate.json` (`pass=true`, `unautomated=0 not_proven=0 planned=0`).
- **Also test:** `scripts/pns_parity_proof_pack.ps1 -HostOnly`; `scripts/pns_fleet_parity_sweep.ps1 -HostProofPackMergeFixture`; `scripts/pns_capability_catalog_gate.ps1 -HostOnly`.
- **Touches:** `scripts/pns_m22_gate.ps1`, `scripts/pns_capability_catalog_gate.ps1`, `scripts/pns_fleet_parity_sweep.ps1`, `scripts/parity_proof_manifest.json`, `docs/M22_PROVIDER_OWNERSHIP.json`, `BUILD_PLAN.md`
- **Conflicts with:** REG-20260530-001

### REG-20260602-002 — Photo shutter long-press burst lost tray press/release wiring
- **Status:** active
- **Area:** capture | chrome
- **Symptom:** Long-press burst automation hook existed, but tray shutter only handled tap; no continuous burst while holding shutter in photo mode.
- **Cause:** `PreviewBottomCaptureTray` did not emit long-press start/stop callbacks to the existing `startLongPressBurstCapture`/`stopLongPressBurstCapture` pipeline.
- **Fix shipped:** Added tray shutter press-hold gesture wiring (`detectTapGestures`) to call long-press burst start on hold and stop on release; added Timer QS burst cadence presets (`Slow/Medium/Fast`) with interval-backed FPS labels; long-press loop now keeps cadence target by subtracting capture elapsed time from interval.
- **Do not:** Revert tray shutter to tap-only behavior or add fixed post-shot delay that ignores capture elapsed time (this halves effective cadence and desyncs audible shutter pacing).
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1` (`pass=True`, `fastRawOff=True`, `slowRawOn=True`, `savedAny=8`) + `scripts/pns_chrome_ux_gate.ps1 -SkipGradle -SkipHost` (`selfTimerOk=True`, `pass=True`).
- **Also test:** `scripts/pns_capture_modes_test.ps1` when changing burst interval/count internals; `scripts/pns_photo_capture_verify.ps1` if still session wiring changes near shutter dispatch.
- **Touches:** `PreviewEngineScreen.kt`, `ShutterCaptureMode.kt`, `PreviewChromeQuickSettings.kt`, `AdvancedCaptureSettings.kt`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** none

### REG-20260602-003 — Burst menu allowed mixed RAW+JPEG and wrong cadence tier targets
- **Status:** active
- **Area:** capture | chrome
- **Symptom:** Burst could run mixed RAW+JPEG profiles and cadence presets did not match requested high/medium/slow targets; timer popup sections were blended, making mode/file/speed picks error-prone.
- **Cause:** Legacy burst profile enum (`Auto`, `Raw+Processed`) remained selectable in burst flows; cadence options were 150/350/800 ms and burst engine clamped minimum delay to 50 ms.
- **Fix shipped:** Timer QS split into distinct **Single**, **Timer**, and **Burst** sections; burst file type picker constrained to **RAW only** or **JPEG only**; burst cadence presets changed to 33/67/125 ms (target 30/15/8 fps); burst delay clamps lowered to 30 ms in long-press and tap burst engines.
- **Do not:** Reintroduce `Auto`/`Raw+Processed` mixed burst capture in shutter burst menu without explicit product request and new USB proof; do not raise burst delay floor above 33 ms if 30 fps tier remains product requirement.
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1` (`pass=True`, `speedFast=True`, `speedMedium=True`, `speedSlow=True`, `singleFormatOnly=True`); `scripts/pns_photo_capture_verify.ps1 -Fast -SkipAssemble`; `scripts/pns_chrome_ux_gate.ps1 -SkipGradle -SkipHost`.
- **Also test:** Manual UI persistence check after app relaunch (burst file type + cadence retained) whenever changing shutter menu wiring; capture pipeline verify if burst path touches session/capture plumbing.
- **Touches:** `PreviewEngineScreen.kt`, `PreviewChromeQuickSettings.kt`, `ShutterCaptureMode.kt`, `AdvancedCaptureSettings.kt`, `scripts/pns_longpress_burst_verify.ps1`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** none

### REG-20260602-004 — 30 fps burst intent needs bounded queue buffering
- **Status:** active
- **Area:** capture | performance
- **Symptom:** Direct synchronous long-press burst shot loop could not maintain high-rate shutter cadence; each shot waited on full save path, collapsing practical rate under fast settings.
- **Cause:** Capture path serialized on save completion instead of decoupling shutter intent pacing from downstream processing.
- **Fix shipped:** Added bounded pending-shot queue for long-press burst so fast cadence can enqueue capture intent while still pipeline drains in-flight work; stop action flushes pending queue to avoid long tail lockup after release.
- **Do not:** Revert to strict "wait for save then next shot" loop on long-press fast tier without equivalent buffering and USB proof.
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1` (`speedFast=True`, `speedMedium=True`, `speedSlow=True`, `singleFormatOnly=True`) plus `scripts/pns_photo_capture_verify.ps1 -Fast -SkipAssemble`.
- **Also test:** `scripts/pns_chrome_ux_gate.ps1 -SkipGradle -SkipHost -FocalMmSlot ""` after shutter/menu edits to keep timer/burst QS wiring stable.
- **Touches:** `PreviewEngineScreen.kt`, `scripts/pns_longpress_burst_verify.ps1`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** none

### REG-20260602-005 — Max burst tier + status-bar effective FPS telemetry
- **Status:** active
- **Area:** capture | chrome | telemetry
- **Symptom:** Burst UI had no explicit max-speed tier and no live feedback for achieved burst cadence, making "60 fps if possible" verification opaque.
- **Cause:** Cadence presets stopped at 33 ms (~30 fps) and status bar line did not include burst runtime telemetry.
- **Fix shipped:** Added burst cadence preset `Max` at 17 ms target (~60 fps), surfaced live top-band telemetry (`Burst <effective> fps (target <fps>) q=<pending>`), and expanded burst verification to assert max/fast/medium/slow intervals in one run.
- **Do not:** Remove burst telemetry or max-tier preset without replacing with another user-visible achieved-rate indicator and updated USB proof script.
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1` (`pass=True`, `speedMax=True`, `speedFast=True`, `speedMedium=True`, `speedSlow=True`) and `scripts/pns_photo_capture_verify.ps1 -Fast`.
- **Also test:** `scripts/pns_chrome_ux_gate.ps1 -SkipGradle -SkipHost` after status bar/timer menu changes; keep app force-stopped after ADB runs.
- **Touches:** `AdvancedCaptureSettings.kt`, `PreviewEngineScreen.kt`, `PreviewTopStatusBar.kt`, `PreviewChromeQuickSettings.kt`, `HudSettingsScreen.kt`, `scripts/pns_longpress_burst_verify.ps1`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** none

### REG-20260602-006 — Long-press burst dispatch must not block on save callback
- **Status:** active
- **Area:** capture | performance
- **Symptom:** Burst cadence collapsed toward "capture + save + next capture" because long-press dispatch treated `onResult` completion as the release signal for the next shot.
- **Cause:** `captureComposedStill` returns `onResult` after downstream save/encode; burst loop waited on that callback and interpreted transient `capture_busy` as a hard failure.
- **Fix shipped:** Long-press dispatcher now treats `capture_busy` as retry/backpressure, keeps queueing cadence intents while capture path is busy, enables a low-latency JPEG burst request variant (`burstLowLatency=true`), and forces burst intent to `photoResolutionMode=Binned`.
- **Do not:** Revert long-press burst to strict one-shot-in-flight + stop-on-`capture_busy` behavior unless replacing with a true multi-request burst pipeline and USB proof.
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1` (`pass=True`, `speedMax=True`, `speedFast=True`, `speedMedium=True`, `speedSlow=True`); `scripts/pns_photo_capture_verify.ps1 -Fast -SkipAssemble`.
- **Also test:** Manual hold burst UX in photo mode (watch status bar burst fps + shutter cadence), plus `scripts/pns_chrome_ux_gate.ps1 -SkipGradle -SkipHost` after further burst/menu/status changes.
- **Touches:** `PreviewEngineScreen.kt`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** none

### REG-20260602-007 — Fleet-max burst mode + JPEG/RAW strategy benchmarking seeds
- **Status:** active
- **Area:** capture | automation
- **Symptom:** Multiple user-selectable burst pace tiers fragmented tuning and made fleet benchmarking noisy; RAW/JPEG strategy comparisons were not scriptable in one gate.
- **Cause:** Burst cadence UI exposed slow/medium/fast/max presets and long-press gate only validated cadence intervals, not per-format pipeline strategy throughput.
- **Fix shipped:** Collapsed burst pace to a single `Fleet Max` preset (`17 ms` target), kept RAW/JPEG file-type selection, added ADB seeds `pns_preview_burst_file` + `pns_preview_burst_strategy`, and updated `pns_longpress_burst_verify.ps1` to sweep `jpeg/raw × aggressive/paced` in one run.
- **Do not:** Reintroduce multiple user pace tiers without fresh fleet benchmarking evidence; keep burst benchmark seeds wired for repeatable JPEG vs RAW strategy runs.
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1` (`pass=True`, `jpegSeen=True`, `rawSeen=True`, `aggressiveSeen=True`, `pacedSeen=True`) + `scripts/pns_photo_capture_verify.ps1 -Fast -SkipAssemble`.
- **Also test:** Manual long-press burst UX after timer/QS menu edits (confirm Fleet Max label + RAW/JPEG file selector) and capture pipeline verify on still-session changes.
- **Touches:** `AdvancedCaptureSettings.kt`, `ShutterCaptureMode.kt`, `PreviewChromeQuickSettings.kt`, `PreviewEngineScreen.kt`, `CameraCapabilitiesProbe.kt`, `scripts/pns_longpress_burst_verify.ps1`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** none

### REG-20260602-008 — JPEG burst must decouple capture cadence from save latency
- **Status:** active
- **Area:** capture | performance | automation
- **Symptom:** Fleet-max long-press burst still behaved like "capture one frame, wait for save, then request next frame" when JPEG path was routed through composed still callback, capping achieved cadence and masking capture-vs-save bottlenecks.
- **Cause:** Long-press dispatch used `captureComposedStill` completion (`onResult`) as the pacing unlock for both RAW and JPEG; that callback lands after downstream save work.
- **Fix shipped:** Added split long-press engines in `PreviewEngineScreen.kt`: JPEG burst now calls `captureIndependentTonalStill(... onHardwareJpegFrame=...)` and offloads saves to async worker coroutines; RAW burst stays composed/serialized to avoid `No RAW buffer` stalls. Finish telemetry now emits `captured=<n> saved=<n> savePending=<n>`, and `pns_longpress_burst_verify.ps1` parses those metrics.
- **Do not:** Revert JPEG long-press to save-blocked pacing or remove finish telemetry fields without replacing benchmark coverage for capture/save separation.
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1 -SkipAssemble` (`pass=True`, `capturedAny=7`, `savedAny=5`, `savePendingAny=2`) with artifact `hfr-runs/longpress_burst_verify_20260602_105833/`.
- **Also test:** `scripts/pns_photo_capture_verify.ps1 -Fast` after still-session/capture busy changes; manual long-press cadence + status bar burst telemetry check on USB device.
- **Touches:** `PreviewEngineScreen.kt`, `scripts/pns_longpress_burst_verify.ps1`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** REG-20260602-006, REG-20260602-007

### REG-20260602-009 — Fleet-max burst benchmarks require drop + latency histograms
- **Status:** active
- **Area:** capture | performance | automation
- **Symptom:** Burst sweeps could report start/shot/saved totals, but lacked normalized drop-rate and latency histograms per scenario, making aggressive-vs-paced tuning hard to compare and easy to misread.
- **Cause:** `pns_longpress_burst_verify.ps1` only parsed coarse `finished saved=` style counters and did not consume per-strategy telemetry fields.
- **Fix shipped:** Long-press finish logs now include `profile/strategy/captured/saved/savePending/drops/captureLatBuckets`; burst shot logs include strategy; verifier now computes per-scenario shot count, drop %, captured/save fps, and latency buckets, plus JPEG/RAW winner labels by saved fps.
- **Do not:** Remove strategy-tagged burst shot/finish logs or telemetry bucket fields without updating `pns_longpress_burst_verify.ps1` parser and proving equivalent scenario metrics output.
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1` -> `hfr-runs/longpress_burst_verify_20260602_112218/longpress_burst_verify_summary.md` (`pass=True`, scenario metric table with drop + latency buckets).
- **Also test:** `scripts/pns_photo_capture_verify.ps1 -Fast` after burst dispatcher edits to ensure RAW still capture path remains healthy.
- **Touches:** `PreviewEngineScreen.kt`, `scripts/pns_longpress_burst_verify.ps1`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** REG-20260602-008

### REG-20260602-010 — JPEG aggressive burst needs dynamic train depth + bounded save workers
- **Status:** active
- **Area:** capture | performance | automation
- **Symptom:** Fixed-size JPEG burst train and unconstrained async saves left high queue-full drop counts and long save tails under fleet-max aggressive mode.
- **Cause:** Burst dispatch always used a small fixed hardware request train and save tasks competed unbounded on `Dispatchers.Default`, creating contention without reducing backlog efficiently.
- **Fix shipped:** JPEG aggressive long-press now scales hardware train depth by backlog (`2/4/6/8` requests) and routes save work through a bounded limiter (`Semaphore(3)`), while preserving strategy/profile telemetry for drop-rate and latency tracking.
- **Do not:** Revert to fixed single train depth in aggressive JPEG burst or remove bounded save concurrency without rerunning USB sweeps and publishing updated drop/latency metrics.
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1` -> `hfr-runs/longpress_burst_verify_20260602_113450/` (`pass=True`, scenario drop/latency table present); `scripts/pns_photo_capture_verify.ps1 -Fast` -> `hfr-runs/photo_capture_verify_20260602_113659/VERIFY_OK.txt`.
- **Also test:** Manual long-press burst cadence + status bar telemetry after aggressive-depth tuning, and RAW still gate after any burst dispatcher edits.
- **Touches:** `PreviewEngineScreen.kt`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** REG-20260602-008, REG-20260602-009

### REG-20260602-011 — JPEG burst backpressure must be adaptive (not fixed-rate flood)
- **Status:** active
- **Area:** capture | performance | automation
- **Symptom:** Fixed aggressive enqueue pacing could flood queue capacity (very high drop totals) without improving saved fps, especially when save latency spikes.
- **Cause:** Burst enqueue loop used mostly fixed pace/cap behavior, so it kept feeding the queue even when drop ratio stayed high.
- **Fix shipped:** Added adaptive backpressure tuning for aggressive JPEG burst (windowed drop-ratio feedback adjusts `paceFloorMs` and `queueCap`), and burst-save `lightweightMetadata` mode to reduce per-frame save overhead.
- **Do not:** Revert to fixed aggressive flood enqueue for JPEG burst without reintroducing an equivalent adaptive drop-aware controller and proving lower drop totals in USB sweep artifacts.
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1` -> `hfr-runs/longpress_burst_verify_20260602_123632/` (`pass=True`, `dropsAny=344`, lower than prior `423`, metrics table present); `scripts/pns_photo_capture_verify.ps1 -Fast` -> `hfr-runs/photo_capture_verify_20260602_123838/VERIFY_OK.txt`.
- **Also test:** Manual long-press burst cadence in JPEG mode (watch status bar + shutter cadence) and RAW still gate after any burst save-path edits.
- **Touches:** `PreviewEngineScreen.kt`, `IndependentTonalStillSaver.kt`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** REG-20260602-010

### REG-20260602-012 — JPEG burst strategy should switch live when drop pressure spikes
- **Status:** active
- **Area:** capture | performance | automation
- **Symptom:** Fixed strategy per run (seeded aggressive or paced) left throughput on the table; aggressive could keep high drop pressure while paced sometimes recovered saved fps better.
- **Cause:** Burst strategy choice was static for the whole hold session, even as live drop ratio / saved progress changed.
- **Fix shipped:** Added live strategy controller for seeded-aggressive JPEG runs that can switch `aggressive -> paced` under high drop/low-save windows and switch back when pressure drops or saves recover; strategy ID now reflects effective mode in shot/finish telemetry.
- **Do not:** Revert to fixed strategy during long-press JPEG burst without an equivalent live strategy controller and USB sweep evidence.
- **Proves OK:** `scripts/pns_longpress_burst_verify.ps1` -> `hfr-runs/longpress_burst_verify_20260602_233149/` (`pass=True`, metrics table + winner labels), and RAW sanity gate `scripts/pns_photo_capture_verify.ps1 -Fast` -> `hfr-runs/photo_capture_verify_20260602_233353/VERIFY_OK.txt`.
- **Also test:** Manual JPEG long-press with log grep for `longPressBurst strategySwitch` and status bar cadence checks after any strategy/backpressure edits.
- **Touches:** `PreviewEngineScreen.kt`, `docs/PNS_TECHNICAL_SETTINGS.md`
- **Conflicts with:** REG-20260602-011

### REG-20260603-013 — Focal chip mapping must prefer matrix `product.focalRow`
- **Status:** active
- **Area:** fleet | chrome
- **Symptom:** On some devices (e.g., OP13 class), runtime `resolveFocalMmSlot` heuristics can mis-map focal chips even when matrix onboarding has correct camera ids and static-slot availability.
- **Cause:** `FocalLensStripSupport` interaction/native-hint paths depended on runtime resolver first and only used matrix data for partial labeling.
- **Fix shipped:** `FocalLensStripSupport` now prefers matrix `product.focalRow` camera ids and static slot availability in focal chip interaction and native focal hint selection; runtime resolver remains fallback when matrix row is missing.
- **Do not:** Revert focal chip interaction/native hint paths to resolver-first behavior without matrix-first fallback and fleet USB gate proof.
- **Proves OK:** `scripts/pns_gradlew.ps1 :app:testDebugUnitTest --tests dev.pointandshoot.BackCameraRoleResolverTest --tests dev.pointandshoot.SensorCropGeometryTest`; `scripts/pns_chrome_ux_gate.ps1 -SkipGradle -SkipHost -FocalMmSlot 150` -> `hfr-runs/chrome_ux_gate_20260603_023928/chrome_ux_gate.json` (`teleFocalSlotOk=true`, `pass=True`).
- **Also test:** `scripts/pns_fleet_matrix_scan.ps1` and parity quick sweep after focal-row builder/policy edits.
- **Touches:** `FocalLensStripSupport.kt`
- **Conflicts with:** REG-20260528-003

### REG-20260603-014 — Fleet focal row assignment must be mathematical, not role-assumed
- **Status:** active
- **Area:** fleet | chrome
- **Symptom:** Device-specific role assumptions can mis-assign focal chips when camera topology differs (1/3/N cameras), especially with overlapping crop reach between UW/Wide/Tele paths.
- **Cause:** Matrix product focal-row builder relied on role-derived labels/ids and wide-only static crop estimates instead of a target-by-target least-crop assignment across all available cameras.
- **Fix shipped:** `FleetFocalRowProductBuilder` now builds `product.focalRow` from mathematical prime assignments for chip targets (`14/23/35/50/73/85/150`) using `FocalLensStripSupport.resolvePrimeLensAssignments(..., targets)`; overlap resolution prefers least crop first, then higher effective MP, and writes per-slot assignment metadata (`slotAssignments`) consumed by matrix-first focal chip mapping.
- **Do not:** Reintroduce role-first/static-estimate focal row generation without target-level assignment math and fleet scan verification.
- **Proves OK:** `scripts/pns_gradlew.ps1 :app:testDebugUnitTest --tests dev.pointandshoot.FocalLensStripSupportTest --tests dev.pointandshoot.fleet.FleetFocalRowPolicyTest`; `scripts/pns_chrome_ux_gate.ps1 -SkipGradle -SkipHost -FocalMmSlot 150` -> `hfr-runs/chrome_ux_gate_20260603_025656/chrome_ux_gate.json` (`teleFocalSlotOk=true`, `pass=True`).
- **Also test:** `scripts/pns_fleet_matrix_scan.ps1` + parity quick sweep after focal-row builder / startup scan math changes.
- **Touches:** `FocalLensStripSupport.kt`, `fleet/FleetFocalRowProductBuilder.kt`
- **Conflicts with:** REG-20260603-013

### REG-20260603-015 — Prime focal assignment must always emit a best-fit camera mapping
- **Status:** active
- **Area:** fleet | chrome
- **Symptom:** Generic devices can end up with partial focal-row assignment payloads (`slotAssignments` missing for M14/static targets), which leaves chips under-specified and can hide usable cameras/focal coverage in matrix-first routing.
- **Cause:** Prime assignment filtered out targets without a crop-compatible candidate (`target < native`) and dropped low-effective-MP crop targets entirely, so matrix output omitted best-fit mappings even when the device could still route those focal intents.
- **Fix shipped:** `resolvePrimeLensAssignmentsFromCandidates` now always emits a best-fit assignment per target: first least-crop among crop-compatible candidates, else nearest native fallback for wider-than-native targets. MP gating stays in `staticSlots` availability (not in assignment generation), so matrix keeps full routing metadata while still disabling low-MP static slots.
- **Do not:** Reintroduce assignment-time MP filtering or strict `target >= native` drop behavior for all targets; keep assignment generation separate from UI enable gates.
- **Proves OK:** `scripts/pns_gradlew.ps1 :app:testDebugUnitTest --tests dev.pointandshoot.FocalLensStripSupportTest --tests dev.pointandshoot.fleet.FleetFocalRowPolicyTest`; `scripts/pns_fleet_matrix_scan.ps1 -Serial 8bf09993 -ScanTier quick -SkipInstall` -> `hfr-runs/fleet_matrix_20260603_035231/fleet_matrix_scan.json` (`pass=true`) with `product.focalRow.slotAssignments` populated for all seven slots.
- **Also test:** `scripts/pns_chrome_ux_gate.ps1 -SkipGradle -SkipHost -FocalMmSlot 150` and parity quick sweep after any focal assignment comparator or slot-gating edits.
- **Touches:** `FocalLensStripSupport.kt`, `FocalLensStripSupportTest.kt`
- **Conflicts with:** REG-20260603-014

### REG-20260603-016 — Chrome UX gate must poll PNS logs under high logcat churn
- **Status:** active
- **Area:** automation | fleet | adb
- **Symptom:** `pns_chrome_ux_gate.ps1` can false-fail on noisy devices (OP13-class) with all `PNS.ChromeUx` needles missing even when the app emits them and focal tap succeeds.
- **Cause:** Gate waited a long focal window and read tail snapshots after heavy camera/kernel spam, so early `PNS.ChromeUx` lines were evicted from ring buffers; readout-capture matcher also lagged current values (`JXL`).
- **Fix shipped:** Gate now polls filtered `PNS.ChromeUx`/`PNS.AdbValidation` logs during the wait window and merges collected logs before regex checks; readout capture matcher accepts current/future format tokens (`readoutCapture=<token>`).
- **Do not:** Revert to tail-only post-wait parsing for focal-slot gates on noisy devices or hard-code legacy-only `readoutCapture` formats.
- **Proves OK:** `scripts/pns_chrome_ux_gate.ps1 -Serial 8bf09993 -FocalMmSlot 150 -SkipHost -SkipGradle -SkipInstall` -> `hfr-runs/chrome_ux_gate_20260603_040043/chrome_ux_gate.json` (`pass=true`, all checks true including `teleFocalSlotOk=true`).
- **Also test:** `scripts/pns_fleet_matrix_scan.ps1 -Serial 8bf09993 -ScanTier quick -SkipInstall` after focal/matrix changes to confirm product row + gate parity.
- **Touches:** `scripts/pns_chrome_ux_gate.ps1`
- **Conflicts with:** REG-20260603-015

### REG-20260603-017 — Focal routing must ignore tiny/non-backward-compatible auxiliary sensors
- **Status:** active
- **Area:** fleet | focal | capture
- **Symptom:** On some fleet devices, 35 mm slot could route to a tiny auxiliary sensor (`~0.04 MP`) that cannot form a valid preview/capture session, leading to focal switch failures (`Session configure failed`, no RAW reader) and capture verify false negatives.
- **Cause:** Prime lens candidate collection accepted any back camera with focal metadata, including non-backward-compatible or ultra-low-MP auxiliary/depth sensors.
- **Fix shipped:** `FocalLensStripSupport.collectPrimeLensCandidates` now filters candidates to `BACKWARD_COMPATIBLE` back cameras with `sensorMp >= 2.0`, preventing auxiliary/non-imaging sensors from entering focal assignment math.
- **Do not:** Reintroduce all-back-camera focal candidate selection without capability + MP viability gates.
- **Proves OK:** Sony `DA7803TC1R` focal sweep (`hfr-runs/chrome_ux_gate_20260603_050802`..`051010`) no longer routes 35 mm to camera `5`; 35 mm no longer emits a camera switch to that auxiliary lens, while 23/50/73/85/150 continue to route and recover preview.
- **Also test:** per-slot RAW capture sweep + chrome gate (`-FocalMmSlot 14/23/35/50/73/85/150`) on at least one non-OnePlus fleet device after focal assignment edits.
- **Touches:** `FocalLensStripSupport.kt`
- **Conflicts with:** REG-20260603-015

### REG-20260603-018 — RAW automation must recover from dead focal camera session
- **Status:** active
- **Area:** capture | focal | automation
- **Symptom:** Slot-driven RAW automation can hit `captureRawStill ... err=camera_or_raw_not_ready reason=no CameraDevice` after focal switch (Sony 14 mm path on camera `4`) and fail the run even though another RAW-capable rear camera is available.
- **Cause:** Focal slot mapping selected a camera that repeatedly disconnected (`onError ... error=4`); sequential RAW path aborted after first failure without retrying on a viable RAW camera.
- **Fix shipped:** Added RAW fallback/retry plumbing in `PreviewEngineScreen.kt` for focal + sequential RAW automation: on RAW failure, remap to nearest RAW-capable prime assignment (excluding failing camera), settle, retry the same shot once, and emit explicit fallback telemetry.
- **Do not:** Treat first `captureRawStill` failure as terminal in focal automation when a RAW-capable fallback camera exists.
- **Proves OK:** Sony `DA7803TC1R` run `hfr-runs/slot14_clear_task_verify_20260603_014350/pid.logcat.txt` shows `focalSlotTap mm=14 ... cameraIdAfter=4`, initial `captureRawStill ... err=camera_or_raw_not_ready`, then `raw fallback retry cameraId=4 -> 2 ...`, and retry `ok=true saved=...dng`.
- **Also test:** `scripts/pns_photo_capture_verify.ps1` and `scripts/pns_chrome_ux_gate.ps1 -FocalMmSlot 14` (run sequentially, not parallel) after focal/session wiring edits.
- **Touches:** `PreviewEngineScreen.kt`
- **Conflicts with:** REG-20260528-002, REG-20260603-017

### REG-20260603-019 — RAW automation must not let tray snapshot reapply 120 fps
- **Status:** active
- **Area:** capture | automation
- **Symptom:** `pns_photo_capture_verify.ps1 -Fast` could repeatedly fail with `createCaptureSession threw IllegalArgumentException: Surface was abandoned` and `HFR path active (desiredFps=120)` even though RAW automation seeded photo capture mode.
- **Cause:** In `PreviewEngineScreen`, automation started with `selectedFps=60` for RAW/bracket runs, but initial tray snapshot apply could overwrite it to a persisted 120 fps target before first RAW session create.
- **Fix shipped:** Keep automation FPS seed when `rawOrBracketAutomation` is active (`applyPreviewTrayModeSnapshot` no longer overrides seeded <=60 in that path); hardened `pns_photo_capture_verify.ps1` to use fleet-generic default seed camera (no hardcoded `cameraId=3`) while preserving `pns_preview_video_fps=60` seed for scripted runs.
- **Do not:** Allow tray snapshot restore to override seeded RAW/bracket automation FPS targets; do not hardcode legacy-only seed camera ids in fleet capture gate scripts.
- **Proves OK:** `scripts/pns_photo_capture_verify.ps1 -Fast -Serial DA7803TC1R -MaxAttempts 3` -> `hfr-runs/photo_capture_verify_20260603_113749/VERIFY_OK.txt` (`captureRawStill 1/1 ok=true saved=` on attempt 1).
- **Also test:** `scripts/pns_chrome_ux_gate.ps1 -SkipHost -SkipGradle -FocalMmSlot 150` and `scripts/pns_capture_pipeline_verify.ps1` (sequentially, same device).
- **Touches:** `PreviewEngineScreen.kt`, `scripts/pns_photo_capture_verify.ps1`
- **Conflicts with:** REG-20260512-001, REG-20260603-018

### REG-20260603-020 — M23 gate scripts must survive logcat churn without false pass/fail
- **Status:** active
- **Area:** automation | fleet | chrome
- **Symptom:** Sequential M23 closeout runs intermittently failed even with healthy behavior when key log lines were evicted (`PNS.FleetParity sweepComplete`, `PNS.ChromeUx readout=live|fallback`) under noisy OEM buffers.
- **Cause:** Script gates depended on single log needles and treated missing needles as hard-fail, even when equivalent in-app evidence (`parity_report_quick.json` schema/cell counts, `readoutCapture` + `statusBar`) proved the run.
- **Fix shipped:** `pns_fleet_parity_sweep.ps1` now falls back to in-app parity evidence when sweep-complete log is missing; `pns_chrome_ux_gate.ps1` now accepts `readoutCapture + statusBar` fallback when `readout=live|fallback` is evicted.
- **Do not:** Revert to single-needle-only pass/fail logic for parity/chrome gates on high-churn log buffers.
- **Proves OK:** `hfr-runs/m23_closeout_chain_20260603_0842/` sequential chain PASS (`matrix_quick`, `capture_pipeline`, `chrome_150`, `parity_quick`) + `scripts/pns_verify_toolchain.ps1 -RunTests` PASS.
- **Also test:** `scripts/pns_fleet_parity_sweep.ps1 -Mode Delta`; `scripts/pns_chrome_ux_gate.ps1 -FocalMmSlot 150`; `scripts/pns_capture_pipeline_verify.ps1` (sequentially on same serial).
- **Touches:** `scripts/pns_fleet_parity_sweep.ps1`, `scripts/pns_chrome_ux_gate.ps1`
- **Conflicts with:** REG-20260528-002, REG-20260530-001

### REG-20260603-021 — Preview session seam extraction must preserve capture teardown/order
- **Status:** active
- **Area:** capture | preview | fleet
- **Symptom:** Session refactors can reintroduce half-torn camera state (`onError`/`onDisconnected` leaves stale session/readers) or bracket `No RAW buffer` failures when callback order flips under HAL load.
- **Cause:** Monolithic `PreviewEngineScreen` orchestration coupled camera open/session create with inline callback assumptions and partial teardown logic.
- **Fix shipped:** Added narrow session/capture seams (`preview/session/PreviewSessionOrchestrators.kt`, `preview/capture/ImageReaderAwait.kt`), made camera disconnect/error call full `closeCamera()`, added bracket generation-token stale guard + bounded reader waits, and shut down all controller executors in `stop()`.
- **Do not:** Reintroduce partial teardown (`camera.close` only) or immediate image-acquire assumptions in bracket burst callbacks without bounded wait/token checks.
- **Proves OK:** `scripts/pns_capture_pipeline_verify.ps1 -Serial DA7803TC1R` PASS; `scripts/pns_chrome_ux_gate.ps1 -SkipHost -SkipGradle -FocalMmSlot 73|85|150` PASS; `scripts/pns_verify_toolchain.ps1 -RunTests` PASS.
- **Also test:** `scripts/pns_in_app_video_verify.ps1`; `scripts/pns_memory_profiler.ps1`; `scripts/pns_po_optimization_gate.ps1` (sequentially on one device).
- **Touches:** `PreviewEngineScreen.kt`, `preview/session/PreviewSessionOrchestrators.kt`, `preview/capture/ImageReaderAwait.kt`, `BackCameraRoleResolver.kt`, `ZslStillFrameRing.kt`
- **Conflicts with:** REG-20260512-001, REG-20260603-019

---

## Superseded / historical

*(Move rows here when no longer applicable; keep for archaeology.)*

---

## Changelog

| Date | Action |
|------|--------|
| 2026-05-28 | Scaffold + seed from REVERTED_FEATURES §8, DNG openability, GLES, fleet pivot |
