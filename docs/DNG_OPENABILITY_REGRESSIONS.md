# DNG openability regressions (Sprint 13.3g)

Living ledger of changes that made **CPH2655** UW/tele DNGs **structurally valid** (`dng_tiff_integrity_check.py` PASS) but **rejected by Adobe Camera Raw / Lightroom**, or that broke parity with ProShot’s **DngCreator-only** path.

**Gate:** `scripts/dng_desktop_open_gate.py` or `scripts/pns_dng_desktop_open_gate.ps1 -Dir <artifact>` (wired in `pns_aux_dng_capture_analyze.ps1`; writes **`openability_gate.json`**). Consolidated: **`scripts/pns_m13_3g2_gate.ps1`**; ACR sign-off **`-RecordAcrPass`**. **Human:** ACR open **3/3** on M14 / M23 / M73 after sideload.

**Lock bisect (color still wrong after openability):** `docs/M13_3E_LOCK_BISECT_RUNBOOK.md` (L2–L7). **Wide-cal OEM path:** Sprint **13.3h** only.

---

## Regression signatures

| ID | Change | Symptom | Detection |
|----|--------|---------|-----------|
| **R1** | Post-save `StillCaptureMetadata` / `ExifInterface.saveAttributes()` on full DNG | ACR “unsupported” / corrupt; green cast | No `StillCaptureMetadata` on leaf when `skipStillMetadataApplyOnLeafDng` |
| **R2** | `useWideLeafCalibrationForAuxDng` — wide CM/FM on UW/tele RAW | ACR reject; aux CM2[0,0] == wide | `dng_desktop_open_gate.py` wide-cal leak; logcat no `wide-cal reconcile` |
| **R3** | `LeafDngHalReconcile` ASN / inverted-gain TIFF surgery | Extreme ASN; decoder reject | ASN multipliers outside ~0.45–2.8; `useProShotPureDngSave()` → reconcile **off** |
| **R4** | `Op13LeafStillColorCorrection` capture-time gains with pure save | Color drift; occasional open fail | Disabled when `useProShotPureDngSave()` |
| **R5** | `proShotLatchManualExposureOnStill` + exposure scale (wide-cal mode) | Tele under/over vs Bayer; metadata mismatch | Only when `useWideLeafCalibrationForAuxDng()` |
| **R6** | `proShotLeafStillSkipsStopRepeating` / `ProShotStillPrecapture` | Session timing; flaky still / bad metadata | Shipped **false**; normal `stopRepeating` + `fireStillCapture` |
| **R7** | `TiffUniqueCameraModel50708` buffer rewrite on large RAW | Rare heap / strip edge cases | `skipUniqueCameraModelOnLeafDng` on OP13 leaf |

---

## Shipped Standard path (13.3g)

- Leaf **`DngCreator(openedCharacteristics, stillResult)`** only — `LeafDngHalReconcile` **off** when `useProShotPureDngSave()`.
- **`useWideLeafCalibrationForAuxDng()` = false** until Sprint **13.3h** bisect with per-step ACR proof.
- **`useProShotStillPrecapture()` = false**; **`proShotLeafStillSkipsStopRepeating()` = false**.
- **`skipStillMetadataApplyOnLeafDng`** for cam **3 / 2 / 4**.

Contract object: `ProShotPipelineContract.kt`. Policy: `OnePlus13FleetPolicy.kt`, `docs/FLEET_ONEPLUS13_RAW_POLICY.md`.

---

## Bisect protocol (13.3h)

1. One variable per commit.
2. `pns_aux_dng_capture_analyze.ps1 -PreviewDial A -NoFast` — desktop open gate **PASS**.
3. Human ACR open **3/3** before color scoring.
4. Log row in `docs/REVERTED_FEATURES_RESTORE_LIST.md` §9 if promoting a lock.

---

*May 2026 — Milestone 13.*
