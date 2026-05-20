# Milestone 13.3e — lock bisect runbook (L2–L7)

**When:** Only after **13.3g** openability is green (automated `dng_desktop_open_gate.py` + human ACR **3/3**) and **13.3f** shows aux color still wrong vs ProShot.

**Rule:** One lock per commit; USB proof each step. Do not enable **L9** (post-save TIFF / wide-cal) here — that is **13.3h**.

---

## Locks (BUILD_PLAN table)

| ID | Variable | Bisect via |
|----|----------|------------|
| **L2** | `allowPhysicalTotalResultPairing` | `DngMetadataResolver` + ADB extras |
| **L3** | `useOp13AsnReconcileOnly` | `OnePlus13FleetPolicy` / `LeafDngHalReconcile` |
| **L4** | `streamHints` (§4a) | `PreviewEngineScreen` — **high risk** |
| **L5** | Default RAW tier (§2) | `RawCaptureSupport` — RAW10 DNG risk |
| **L6** | `useHalColorCalibrationReconcile` | `LeafDngHalReconcile` |
| **L7** | Preview JPEG hints on still | `PreviewJpegProcessingHints` |

**Order:** L2 → L3 → L6 → L4 → L5 → L7 (see `BUILD_PLAN.md`).

---

## Scripts (device required)

```powershell
# Per focal slot (M14 / M23 / M73)
.\scripts\pns_aux_dng_triage_focal_slots.ps1 -Serial <id> -FocalMmSlot 14

# RAW stream map experiments
.\scripts\pns_aux_dng_exp2_raw_stream.ps1 -Serial <id>

# Full aux matrix (after each lock step)
.\scripts\pns_aux_dng_capture_analyze.ps1 -PreviewDial A -NoFast -Serial <id>
```

Artifacts: `hfr-runs/m13_3e_lock_bisect_<timestamp>/report.md`

**USB automation:** `scripts/pns_m13_3e_lock_bisect.ps1` (restores sources after run). Host template only: `scripts/pns_m13_lock_bisect_host.ps1`.

### USB results (CPH2655 `8bf09993`, 2026-05-20)

Consolidated table: `hfr-runs/m13_3e_lock_bisect_20260520_005414/report.md`. **No lock promoted** — color parity still **FAIL**; **L4** (`streamHints`) regressed capture.

---

## Pass criteria per step

1. `pns_capture_pipeline_verify.ps1` green.
2. `dng_desktop_open_gate.py` **PASS** on pulled DNGs.
3. Logcat: `dng openability diag reconcile=false wideCal=false` on leaf saves.
4. Append row to **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §9 when promoting a flag to shipped `OnePlus13FleetPolicy`.

---

*Host doc — execution needs USB.*
