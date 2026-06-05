# Fleet device verification matrix

Per-onboarded-SKU checklist for Milestone **16.8**. Update a row after matrix rescan, capture/video gates, or chrome UX proof on that device.

**Primary development device:** OnePlus 12 **CPH2583** (`b5214fc6`). **legacy device regression** (legacy SKU `legacy serial`) is optional — see `docs/FLEET_ONEPLUS13_RAW_POLICY.md`.

| SKU | Serial | Matrix quick | Matrix full | Shallow hub | Capture verify | Video verify | Chrome UX | DNG aux (if RAW) | Last rescan (UTC) | Notes |
|-----|--------|--------------|-------------|-------------|----------------|--------------|-----------|------------------|-------------------|-------|
| **CPH2583** | `b5214fc6` | PASS | PASS | PASS | PASS | PASS | PASS | PASS | 2026-06-05 | **M25:** full matrix `fleet_matrix_20260605_021552/`; Full parity `parity_sweep_20260605_021644/` (`resolutionBetrayalIndex=0`). **Capture:** `photo_capture_verify_20260605_024927` (sequential USB). **Regression pack:** `fleet_regression_pack_20260605_023355/` tier1+2 PASS. DNG: `aux_dng_capture_analyze_20260529_015653`. |
| **CPH2655** (OP13-class) | `8bf09993` | PASS | PASS | — | — | — | — | — | 2026-06-05 | **M25 stock lane:** full matrix `hfr-runs/fleet_matrix_20260605_025643/` (`cameraKeyConfirmed=true`); Full parity `hfr-runs/parity_sweep_20260605_025715/` (**74.6%, pass=true, 0 ship blockers**, `resolutionBetrayalIndex=0`). AV1 tiers ENGINEERING_ONLY (no QTI HW encoder); hardware key auto-probe cleared `product.hardware_camera_key`. |
| **legacy SKU** (legacy device regression) | `legacy serial` | — | — | — | — | — | — | — | — | Archived primary; use `-LegacyOp13FleetPolicy` / plugin for DNG parity lane only. |

**M21.6 concurrency (parity matrix truth):** **CPH2583** advertises **`dualVideo`** only; **`pipPreview`** and **`multicamMelt`** are **not** advertised on primary fleet device. PiP + Multicam melt USB smoke runs via `pns_legacy_regression_pack.ps1` when connected device model is **legacy SKU / legacy device** (optional regression lane).

## Leaderboard priority devices (Milestone 19)

| Priority | Device | Sweeps needed |
|----------|--------|----------------|
| P0 | **OnePlus 12 (CPH2583)** | Stock Full sweep — baseline good Camera2 parity (published) |
| P0 | **OnePlus 13** | **Stock + Lineage** Full pair — ROM collapse vs OP12 |
| P1 | Sony XQ-BE62 | Stock (published) |
| P1 | Community | `leaderboard_device_request` issue template |

Product groups keep **Camera2 stock (tested)** and **GSMArena advertised (untested)** as separate line items. See `docs/CAMERA2_OEM_DISPARITY.md`.

## Column definitions

| Column | Script / signal |
|--------|-----------------|
| Matrix quick | `scripts/pns_fleet_matrix_scan.ps1 -ScanTier quick` → `fleet_matrix_scan.json` `pass=true` |
| Matrix full | `scripts/pns_fleet_matrix_scan.ps1 -ScanTier full` → `scanTierObserved=full` |
| Shallow hub | `scripts/pns_shallow_scan_hub_validate.ps1` |
| Capture verify | `scripts/pns_capture_pipeline_verify.ps1` or `pns_photo_capture_verify.ps1` |
| Video verify | `scripts/pns_in_app_video_verify.ps1` |
| Chrome UX | `scripts/pns_chrome_ux_gate.ps1` |
| DNG aux | `scripts/pns_aux_dng_capture_analyze.ps1` (legacy device / plugin lane) |
| Video matrix | `scripts/pns_video_matrix_verify.ps1` — picker tiers + ffprobe (see matrix `encoder.verifyScripts`) |
| Codec color | `scripts/pns_video_codec_color_compare.ps1` — H.264 vs HEVC VUI |

Matrix **`encoder`** slice (full/quick scan) lists **surfaceEncoding** and best rows from latest on-device **`enc_probe_*.json`** when present.

## Rescan

See **`docs/FLEET_DEVICE_CAPABILITY_MATRIX.md`** (playbook §16.3 / **16.9**).
