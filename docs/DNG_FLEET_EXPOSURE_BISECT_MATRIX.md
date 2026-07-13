# Fleet DNG exposure bisect matrix (2026-07)

**Sprint:** DNG-FLEET-EXPOSURE-2026-07 · **Plan:** `.cursor/plans/fleet_dng_bisect_matrix_30e23d6a.plan.md`  
**Dead ends:** [`AGENT_REGRESSION_MEMORY.md`](AGENT_REGRESSION_MEMORY.md) REG-20260713-001…003 · [`PROSHOT_APK_FLEET_ANALYSIS.md`](PROSHOT_APK_FLEET_ANALYSIS.md)  
**Host metric:** `python scripts/dng_same_scene_exposure_metric.py <pns.dng> <proshot.dng>`  
**USB helper:** `scripts/pns_dng_fleet_exposure_bisect.ps1`

## Isolation (other devices stay untouched)

Maintainer rule (2026-07-13): **CPH2583 and other SKUs already produce good DNGs.** OP13 (CPH2655) crush must **not** be “fixed” by changing generic still/DNG defaults.

| Layer | Rule |
|-------|------|
| **E03–E11 / E04 / E05 / E14 bisect** | `DngSaveBisectState` defaults are **all off** (`false` / `0`). Active **only** when cold-start ADB extras are present. |
| **PS01 process (L6/i4/j4)** | **Shipped as fleet default** for RAW Auto (2026-07-13) — process only. Full PS01 extras (skip AE_LOCK / skip ASN / map ON) stay ADB-only. |
| **Never promote E\* EV tweaks to GenericFleet** | Do **not** turn failing OP13 EV cells into fleet-wide defaults. |
| **Already-global pure-HAL / ASN tag sync** | Remains on by default under pure-HAL; PS01 ADB flag may disable ASN for experiments. |

## Pass bar (UW same-scene)

- Center mosaic median within **~1.5×** of ProShot (not 8 vs 68).
- `frac_below_black` not ≫ ProShot (+0.25 absolute tolerance).
- FL match (|Δ| &lt; 0.15 mm); integrity / openability PASS on ship.
- Color `cam_gi` secondary until exposure PASS.
- Wide 6.1 mm control must not regress after any UW-moving cell.

## Status legend

`pending` · `PASS` · `FAIL-dead` · `skip-proven-wrong` · `baseline` · `blocked-hw`

---

## Block A — Baseline (no code)

| Cell | Lever | Hypothesis | Status | Metric / artifact | Fleet rule |
|------|--------|------------|--------|-------------------|------------|
| **A0** | Same-scene UW 14 + wide 6.1 | Establish crush signature | **baseline** | `hfr-runs/same_scene_14_61_20260713_0106` — UW FAIL exposure; wide OK | Valid FL-matched pairs only |

---

## Block B — Still AE integration

ADB extras under `pns_preview_dng_*` / existing seeds (see `DngSaveBisectState`).

| Cell | Lever | Hypothesis | Status | Metric / artifact | Fleet rule |
|------|--------|------------|--------|-------------------|------------|
| **E01** | Still targets RAW+JPEG, **no preview** | Regress-check dual target | **pending** | Log `rawStillDualTarget jpeg=` | Capability-gated; all SKUs |
| **E02** | Precapture + rebuild from converged result | Confirm UW fires `aePrecapture converged` | **pending** | `PNS.ReferenceAppStill` | Key-advertised only |
| **E03** | Skip pure-HAL `AE_LOCK` after precapture | Lock may underexpose UW | **FAIL-dead** | USB pass `0938`: +~10% vs P&S base; still ≪ ProShot | Bisect only; tele needed lock historically |
| **E04** | After-stop debounce **420 ms** (manual path) | Stale AE after stopRepeating | **pending** | `--ei pns_preview_dng_after_stop_debounce_ms 420` | Fleet timing |
| **E05** | Skip ProShot weight-0 AE regions | Regions bias UW meter | **pending** | `--ez pns_preview_dng_skip_ae_regions true` | When `MAX_REGIONS_AE` &gt; 0 |
| **E06** | Face AE / face-detect OFF on RAW still | Must stay off | **skip-proven-wrong** | REG-20260712-001/005 | Do not re-enable |
| **E07** | Auto dial (no H EV / chase) | H dial EV crushes RAW | **pending** | Manual / ADB dial Auto | Isolate Auto |
| **E08** | AE exposure compensation **+1 EV** (when advertised) | Fleet EV nudge lifts mosaic | **FAIL-dead** | USB `0938`: best modest lift (~1.14× 73 / ~1.30× 14 vs base); still FAIL vs PS | Capability-gated steps |
| **E09** | Precapture uses **STILL** template | Preview-template meter wrong for UW | **FAIL-dead** | USB `0938`: **hurts 73** (0.54× base); UW ~same as E08 | Do not ship as default |
| **E10** | JPEG companion **off** (RAW-only still) | Dual-target AE bias | **FAIL-dead** | USB `0938`: flat vs base on 73; tiny UW lift; not the crush fix | Existing seed (keep for DNG-only gates) |

---

## Block C — Still IQ / session (only if Block B fails)

| Cell | Lever | Hypothesis | Status | Metric / artifact | Fleet rule |
|------|--------|------------|--------|-------------------|------------|
| **E11** | Skip still IQ pipeline | EDGE/NR/TONEMAP darkens RAW | **FAIL-dead** | USB `0938`: best UW lift (~1.39× base) but still FAIL; slight 73 drop | Bisect |
| **E12** | Map / shading toggles | Color/exposure via shading | **skip-proven-wrong** | REG-20260713-003 | Do not re-run for Bayer |
| **E13** | YUV-free session | Session graph AE | **skip-proven-wrong** | REG-20260713-003 | Exposure-only re-open needs new hypo |
| **E14** | Post-raw sensitivity boost | Digital gain after RAW | **pending** | HUD / existing boost path | If advertised |

---

## Block PS — ProShot process rebuild (not EV tweaks)

| Cell | Lever | Hypothesis | Status | Metric / artifact | Fleet rule |
|------|--------|------------|--------|-------------------|------------|
| **PS01** | ProShot `L6`/`f6`/`i4`/`j4` process | P&S **stopRepeating → one-shot precapture** diverges from ProShot **setRepeating+capture with PRECAPTURE**, then stop + STILL | **partial → strong** | Same-scene USB `101133` (UI-tapped ProShot): **73 centerMed 0.86×** · **14 0.92×** ProShot (was ~0.65 / ~0.27). `PASS_EXPOSURE=False` only on **frac&lt;bl** (0.37/0.53 vs ProShot ~0.01/0.04) — midtone OK, pedestal crush remains. ADB + `pns_proshot_pns_same_scene_ps01.ps1` | Promote only after CPH2583 no-regress + residual shadow work |

**PS01 process (from ProShot 8.34 decompile):** repeating stays up → `session.capture(previewReq + PRECAPTURE START)` → converge → CANCEL → `stopRepeating` → `TEMPLATE_STILL` RAW±JPEG (no AE_LOCK, no ASN patch; lens-shading map ON like ProShot A5 default).

---

## Phase 3 — Color (only after mosaic PASS)

| Cell | Lever | Status | Notes |
|------|--------|--------|-------|
| **C01** | `DngBayerAsnSyncPolicy` keep/disable by Δ(ASN,Bayer) | **pending** | No full Bayer B; hinted CFA only |
| **C02** | ProShot AWB/CC still path | **pending** | Only if ASN still desynced |

---

## Out of matrix until exposure PASS

ASN force / CM/FM / ExifInterface / wide-cal / physical pairing true / §4a `streamHints` / RAW10-first Default / `forceFullActiveArrayCrop` / multi-CFA ASN.

---

## Phase 5 — Tele reopen checklist

When tele **73** responds:

1. Same-scene ProShot + P&S at native 73 (crop full active array).
2. Confirm 85 crop intact (`289,217-…` class on OP13).
3. Re-run only exposure cells that **changed** since last tele PASS — skip E06/E12/E13 dead ends.
4. Artifact: `hfr-runs/dng_fleet_exposure_tele_*`.

**Status:** **reopened** — tele 73 responsive (2026-07-13 morning). Re-score only cells that change defaults after a PASS.

---

## Run log

| When | Cell | Device | Result | Artifact |
|------|------|--------|--------|----------|
| 2026-07-13 | A0 | OP13 `8bf09993` (host pair) | UW **FAIL** exposure (centerMed 0.27×); wide **PASS** | `same_scene_14_61_20260713_0106` + `dng_same_scene_exposure_metric.py` |
| 2026-07-13 | E01–E11 USB | OP13 | **blocked** (earlier) — PM Activity missing / Surface abandoned | `hfr-runs/dng_fleet_exposure_baseline_*` |
| — | E06 / E12 / E13 | — | **skip-proven-wrong** | REG-20260713-003 |
| 2026-07-13 | A0 tele 73 | OP13 `8bf09993` | FL match 13.85; ISO ~2276 vs 2244; centerMed **0.65×**; **spatial right-side crush** | `same_scene_73mm_20260713_0929` |
| 2026-07-13 | **P2 ship** ProShot process default | CPH2583 `b5214fc6` | Baseline + PS01 + post-promote `captureRawStill ok`; log `process=proshot_process_default`; `dng save path=pure_hal_bayer_asn` (ASN kept). Mosaic `frac&lt;bl≈0`. | `photo_capture_verify_20260713_143757` · live confirm `pns_20260713T143916Z` · REG-20260713-004 |
