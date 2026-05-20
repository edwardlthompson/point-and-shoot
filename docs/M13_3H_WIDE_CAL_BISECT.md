# Milestone 13.3h — wide leaf calibration bisect (optional)

**Prerequisites:**

1. **13.3g** complete: pure `DngCreator`, `dng_desktop_open_gate.py` **PASS**, human ACR **3/3**.
2. **13.3f** shows color/luminance still unacceptable vs ProShot after fixture refresh.

**Do not** enable `useWideLeafCalibrationForAuxDng` by default until this bisect finishes and **13.7** signs off.

---

## Bisect steps (one variable per commit)

| Step | Flag / code | Openability | Color |
|------|-------------|-------------|-------|
| H1 | `useWideLeafCalibrationForAuxDng=true` (CM/FM from cam **2** on aux RAW only) | ACR **3/3** required | vs ProShot |
| H2 | H1 + `LeafDngHalReconcile` ASN on aux | ACR **3/3** | vs ProShot |
| H3 | H2 + `proShotLatchManualExposureOnStill` + `adjustProShotExposureLatch` | ACR **3/3** | vs ProShot |

After each step: `pns_aux_dng_capture_analyze.ps1 -PreviewDial A -NoFast` (open gate must pass). Logcat must not show unexpected `wide-cal reconcile` unless H1+ is intentional.

**Regression reference:** `docs/DNG_OPENABILITY_REGRESSIONS.md` **R2**, **R5**.

**Automation:** `scripts/pns_m13_3h_wide_cal_bisect.ps1` (patches policy per step, restores baseline after run).

### USB results (CPH2655 `8bf09993`, 2026-05-20)

| Step | Open gate | Notes |
|------|-----------|-------|
| H1 | **FAIL** | `dng_desktop_open_gate.py` — UW+tele CM2[0,0]=1.4337 matches wide |
| H2 | **FAIL** | Same as H1 |
| H3 | **FAIL** | Same as H1 |

Artifacts: `hfr-runs/m13_3h_wide_cal_bisect_20260520_003542/`. Evidence table: **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §9 (13.3h subsection). **Shipped default unchanged** (`useWideLeafCalibrationForAuxDng=false`).

---

*Host protocol — execution requires USB + ACR.*
