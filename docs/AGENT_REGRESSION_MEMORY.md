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
- **Cause:** `RawStreamPreference.Default` order **RAW12 → RAW10 → RAW_SENSOR** (Milestone 10.1) on CPH2655-class
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
- **Also test:** `dng_proshot_parity_gate.py` only on OP13 regression lane
- **Touches:** `OnePlus13FleetPolicy.kt`, `Dng12Saver.kt`, `LeafDngHalReconcile.kt`
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

### REG-20260528-001 — OP13 as default fleet proof device

- **Status:** active
- **Area:** fleet / process
- **Symptom:** Agents optimize for CPH2655; fixes do not generalize; whack-a-mole on other SKUs
- **Cause:** BUILD_PLAN and gates referenced `8bf09993` / CPH2655 as sole truth
- **Fix shipped:** Primary device **CPH2583**; OP13 archived; `fleet_device_matrix.json` SoT (**16.0+**); verify matrix per SKU
- **Do not:** New Priority-1 work requiring CPH2655 only; OP13 color policy on unknown SKUs without plugin
- **Proves OK:** `docs/FLEET_DEVICE_VERIFY_MATRIX.md` row + `pns_fleet_matrix_scan.ps1` (when **16.3** lands)
- **Also test:** Gates on primary device for capture/video/chrome changes
- **Touches:** `BUILD_PLAN.md`, `AGENTS.md`, `FleetCameraProfileBuilder.kt` (**16.4**)
- **Conflicts with:** REG-20260520-001 (OP13 regression lane only)

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

---

## Superseded / historical

*(Move rows here when no longer applicable; keep for archaeology.)*

---

## Changelog

| Date | Action |
|------|--------|
| 2026-05-28 | Scaffold + seed from REVERTED_FEATURES §8, DNG openability, GLES, fleet pivot |
