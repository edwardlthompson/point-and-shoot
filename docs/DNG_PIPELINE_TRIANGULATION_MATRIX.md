# DNG pipeline triangulation — ProShot vs MotionCam vs Point & Shoot

**Purpose:** Narrow aux-lens **dark / green** color on **OnePlus 13 (CPH2655)** by marking what is **the same** across working Play Store apps vs what **only P&S** does. Forensics showed ProShot and P&S can share **`FM1[0,0]=0.4375`** on all leaf ids while ProShot still looks correct — so the bug is unlikely to be “missing TIFF FM patch” and more likely **shared HAL metadata + something P&S does to the still path or file**.

**Reference runs:** `hfr-runs/proshot_reference_*`, `hfr-runs/aux_dng_capture_analyze_*`, `docs/DNG_PS_ALIGNMENT_SPIKE.md`, `docs/MOTIONCAM_APK_FLEET_ANALYSIS.md`.

---

## Pipeline stages (matrix)

Legend: **S** = same across all three (or same on OP13 leaf ids) · **D** = differs · **P** = P&S-only · **?** = unverified on device for MotionCam DNG

| Stage | ProShot (good color) | MotionCam Pro (good UX) | P&S ProShot attempt | P&S MotionCam-inspired (current OP13) | Triage |
|-------|----------------------|-------------------------|---------------------|----------------------------------------|--------|
| **Rear leaf `cameraId`** | **3** UW, **2** wide, **4** tele | Same ids via native catalog | **S** | **S** | Ruled out — dumpsys + forensics |
| **API stack** | Java Camera2 | NDK `ACamera*` + native encode | Java Camera2 | Java Camera2 | MotionCam **D**; P&S matches ProShot |
| **DNG writer** | `DngCreator(chars, result)` | Native `RawEncoder` + Adobe DNG SDK | **S** (framework) | **S** (framework) | MotionCam **D**; both refs ≠ native |
| **Metadata pairing (leaf)** | Opened id chars + still result | Native (not `DngCreator`) | `pairForDngCreator` direct | **S** — `picked=null`, `pairedPhysical=false` | **S** on leaf — not logical hybrid |
| **RAW format (leaf)** | Try **32,37,38,36** max area on opened map | Native **RAW10/12/16** at chosen WxH | Order 32→37→38→36 | **RAW_SENSOR** @ active array **4096×3072** (`rawFmt=32`) | **D** vs ProShot order — bisect |
| **Preview session camera** | Leaf when shooting | `StartCapture(cameraId,…)` | Often **0** then switch to leaf for slot | **S** pattern (logs show `0` then **3/2/4**) | Preview id **S**; still id leaf |
| **RAW stream pin** | Preview may pin physical | Native output list | Unpinned RAW on logical; leaf unpinned | **S** — `usePhysicalChildRawStreamMap=false` | Locked — not aux root alone |
| **Still: lens shading map** | `STATISTICS_LENS_SHADING_MAP_MODE_ON` | Profile `disableShadingMap` | ON (fleet profile) | ON when `lensShadingMapOnStill` | **S** intent |
| **Still: `SHADING_MODE`** | HQ/FAST when available | Pref / native | ON (incl. UW) | **D** — tele **map only** (no `SHADING_MODE`) | Tele **D** — try UW-only bisect |
| **Still: aberration / distortion** | When advertised | Native post-process | ON on leaf | **OFF** (MotionCam backend) | **D** — optional bisect ON |
| **Still: `COLOR_CORRECTION`** | On (ProShot still) | Native pipeline | ON leaf (`useNeutral…=false`) | **S** — CC on leaf still | **S** |
| **Still: neutral / HQ CC skip** | No | — | Only logical+aux pin | **S** off on leaf | **S** |
| **Preview JPEG hints on still** | Unknown | — | `PreviewJpegProcessingHints` | **P** — same | Bisect off for RAW still |
| **`SCALER_CROP_REGION` still** | Yes (digital tele) | Yes | Yes (dodge tele) | **S** | Unlikely color root |
| **Post-`writeImage` ASN patch** | None | N/A | **OFF** when `useProShotPureDngSave()` (13.3g) | **OFF** (MotionCam path) | **S** with ProShot — shipped May 2026 |
| **Post-`writeImage` CM/FM TIFF** | None | N/A | **OFF** (L9); wide-cal bisect **13.3h** only | **S** | Ruled out; see **`docs/DNG_OPENABILITY_REGRESSIONS.md`** |
| **`ExifInterface` on DNG** | No | N/A | **Skipped** on leaf (`skipStillMetadataApplyOnLeafDng`) | **S** | Ruled out for openability |
| **`StillCaptureMetadata` IFD0** | Minimal / OEM | N/A | **Skipped** on leaf (13.3g) | **P** on non-leaf only | Bisect **L7** if color still fails |
| **UniqueCameraModel 50708** | Unknown | N/A | **Skipped** on OP13 leaf (`skipUniqueCameraModelOnLeafDng`) | **P** | Openability gate + ACR |
| **`DngLutMetadata` / software tag** | Unknown | N/A | `setDescription` + sidecar JSON | **P** | Low risk |
| **Imaging profile (Std / Ultra)** | Product modes | Product modes | `ImagingProfile` → DNG mode | **P** | Same across slots in matrix runs |
| **In-file `FM1[0,0]` (CPH2655)** | **0.4375** all lenses | ? | **0.4375** all | **0.4375** all | **S** — wrong FM in file not the story |
| **In-file ASN WB** | Per-lens, matches scene | ? | Patched / HAL | HAL / no reconcile | **D** — ProShot ASN matches scene better |
| **HAL `ColorMatrix2` / calibration** | Embedded via `DngCreator` | Own SDK | From `CameraCharacteristics` | **S** source | **S** suspect — HAL wrong for 3/4 |
| **§4a session stream hints** | Unknown | — | **false** (bisect lock) | **S** | Locked |
| **§2 Default RAW tier (logical)** | — | — | RAW12→RAW_SENSOR→RAW10 | N/A on leaf pick | Locked on logical only |

---

## What is the same (focus here)

These hold for **ProShot**, **MotionCam**, and **P&S** on CPH2655 leaf stills:

1. **Physical sensors and `cameraId` 3 / 2 / 4** — not a wrong-id routing bug.
2. **HAL-supplied color calibration in characteristics** — `DngCreator` (and Play Store apps) read the same OEM matrices; shared **wide FM tag** in file does not explain ProShot-only good color.
3. **Leaf `TotalCaptureResult` + same-id `CameraCharacteristics`** for `DngCreator` on P&S (no logical hybrid on leaf).
4. **RAW_SENSOR delivery** on current P&S MotionCam path (`rawFmt=32`) — aligns with MotionCam’s “full sensor” RAW, not ProShot’s “first matching format in 32,37,38,36”.
5. **ISP still requests include color pipeline** (CC on) for P&S leaf — not the “neutral preview CC” path.

**Working hypothesis:** Decoder color is dominated by **(A)** HAL **ColorMatrix2 / calibration** for UW & tele in characteristics, plus **(B)** **`AsShotNeutral` / WB tags** that ProShot’s still pipeline gets right from the capture result but P&S files do not — without requiring different FM TIFF tags. **(C)** P&S-only **post-creator TIFF edits** (`StillCaptureMetadata`) may still disturb IFD layout or subIFD values Adobe uses even when rawpy opens the file.

MotionCam **does not** help if the issue is (A): native encoder still needs correct matrices from the same HAL unless it applies its own per-device profile JSON.

---

## What differs (eliminate in order)

| Priority | Difference | Action |
|----------|------------|--------|
| **1** | **`StillCaptureMetadata.applyToDngUri`** after save | A/B: save DNG with **metadata apply disabled** on OP13 leaf; open in ACR/Lightroom |
| **2** | **RAW format pick** ProShot order vs `RAW_SENSOR@activeArray` | A/B: `stillDngBackend=FRAMEWORK_PROSHOT` **only** raw pick, keep reconcile **off** |
| **3** | **Still `SHADING_MODE`** (tele map-only) | A/B: ProShot full shading on tele still (watch capture reason=0) |
| **4** | **ProShot optical correction** off on MotionCam path | A/B: enable `applyProShotOpticalCorrection` on leaf |
| **5** | **`LeafDngHalReconcile`** (ProShot path only) | Already off on MotionCam path; if (2) retried, keep off |
| **6** | **50708 / LUT description** | Strip optional tags; one capture compare |
| **7** | **`PreviewJpegProcessingHints` on RAW still** | Remove for `captureRawStill` only |

Automation: `pns_aux_dng_capture_analyze.ps1` + `structural_verify.py` (WB/FM) + **visual** ACR on `M14_uw.dng` / `M73_tele.dng`. Grep `PNS.Dng` **`dng color diag`** for `cm1/cm2` wide vs UW vs tele.

---

## USB bisect results (`hfr-runs/dng_matrix_bisect_20260519_030756`)

Objective metric: `scripts/dng_color_metric.py` — `render_green_delta_vs_wide` (lower is better; gate ≤0.12).

| Step | Change | uw_delta | tele_delta | tele_ok |
|------|--------|----------|------------|---------|
| baseline | MotionCam-inspired (shipped) | 0.42 | 0.42 | no |
| E1 | skip `StillCaptureMetadata` | 0.40 | 0.40 | no |
| E1_50708 | E1 + skip 50708 | 0.40 | 0.43 | no |
| **E2** | **`FRAMEWORK_PROSHOT` still IQ** | **0.33** | **0.03** | **yes** |
| E2_reconcile | E2 + force ASN reconcile | 0.31 | 0.04 | yes |
| E2_skipmeta | E2 + skip metadata | 0.38 | 0.04 | yes |
| E7_reconcile_mc | MotionCam + ASN reconcile | 0.37 | 0.05 | no |

**Verified conclusions (do not guess):**

1. **`StillCaptureMetadata.applyToDngUri` is not the green/black cause** — E1 ≈ baseline.
2. **MotionCam-inspired still IQ was the tele regression** — tele RGB mean **B≈9.9** (baseline) vs **B≈16.4** (E2); map-only tele shading + no ProShot optical correction.
3. **Ship `FRAMEWORK_PROSHOT` on OP13** — ProShot leaf RAW order + full still shading + aberration/distortion when advertised.
4. **UW still fails metric gate** — needs follow-up (E2_reconcile best uw_delta 0.31); not blocked on tele fix.

Automation: `.\scripts\pns_dng_matrix_bisect.ps1 -Serial <device>`

---

## P&S paths tried (history)

| Attempt | Still backend | Leaf RAW | Post-DNG | USB loadability | Aux color |
|---------|---------------|----------|----------|-----------------|-----------|
| Logical RAW12 + hybrid meta | Default | RAW12 logical aux | — | — | Dark/green |
| Resolver logical+logical lock | Default | RAW_SENSOR aux | — | — | Still bad |
| ProShot order + shading + ASN reconcile | `FRAMEWORK_PROSHOT` | 32→37→38→36 | ASN TIFF | Broken until Exif fix | Still bad |
| Loadability fix | ProShot | same | No Exif; ASN only | **PASS** | Still bad |
| **MotionCam-inspired (current)** | `MOTIONCAM_INSPIRED` | RAW_SENSOR @ active | None | **PASS** | **Still bad** (user May 2026) |

---

## Next bisect (recommended)

1. **`pns_aux_dng_capture_analyze`** with a build flag or ADB extra `pns_preview_dng_metadata_apply=false` (if added) — tests row **1**.
2. Pull **ProShot** + **P&S** DNGs same scene; `scripts/structural_verify.py` + exiftool diff on **AsShotNeutral**, **ColorMatrix2**, **CameraCalibration**.
3. Log **`dng color diag`** side-by-side wide vs UW for same lighting.

Do **not** re-enable IFD0 CM/FM overwrite or `ExifInterface.saveAttributes` on DNG (loadability lock).

---

## Related docs

- `docs/DNG_PS_ALIGNMENT_SPIKE.md`
- `docs/RAW_REFERENCE_APP_MATRIX.md`
- `docs/FLEET_ONEPLUS13_RAW_POLICY.md`
- `.cursor/rules/dng-save-pipeline-lock.mdc`

*May 2026 — triangulation for aux DNG color.*
