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

---

## Superseded / historical

*(Move rows here when no longer applicable; keep for archaeology.)*

---

## Changelog

| Date | Action |
|------|--------|
| 2026-05-28 | Scaffold + seed from REVERTED_FEATURES §8, DNG openability, GLES, fleet pivot |
