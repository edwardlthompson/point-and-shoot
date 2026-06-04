# P&S DNG alignment spike (from ReferenceCam forensics)

**Status:** Forensics + **loadability fix** shipped May 2026 on `legacy serial`. Color alignment vs ReferenceCam remains open. Triage matrix: **`docs/DNG_PIPELINE_TRIANGULATION_MATRIX.md`** (common vs different across ReferenceCam / AltReferenceApp / P&S).

**Do not:** Re-apply `DngForwardMatrixFix` / full `TiffDngColorMatrixPatch` CM·FM overwrite in the save path (reverted May 2026; broke wide/tele and risks Adobe load failures).

---

## Shipped — DNG loadability fix (May 2026)

**Regression:** DNGs from leaf captures would not open in **Lightroom / ACR** (`aux_dng_capture_analyze_20260518_143923` era). Files were ~25 MB; rawpy could still read some copies.

| Cause | Fix |
|-------|-----|
| `StillCaptureMetadata.applyToDngUri` ran **`ExifInterface.saveAttributes()`** on row-strip DNGs | Removed; write **in-place** TIFF patches only (`TiffIfd0Software305`, `TiffExifSubIfdCapturePatch`) |
| `LeafDngHalReconcile` called **`patchCalibrationTagsIfd0`** | Removed; **AsShotNeutral-only** via `DngBayerAsShotNeutral` (before `writeImage`) |
| DNG gates used **`pns_preview_jpeg_companion=true`** | Set **`false`** in aux DNG scripts |

**Verification:** `scripts/pns_aux_dng_capture_analyze.ps1` + `scripts/dng_tiff_integrity_check.py` — reference run `hfr-runs/aux_dng_capture_analyze_20260519_014855`.

**Agent lock:** `.cursor/rules/dng-save-pipeline-lock.mdc` · **`AGENTS.md`** CRITICAL — DNG save pipeline.

---

## Forensics results (`referenceapp_reference_20260518_025412`)

| Observation | ReferenceCam (3 DNGs) | P&S (`aux_dng_capture_analyze_20260518_025101`) |
|---------------|-------------------|--------------------------------------------------|
| `FM1[0,0]` | **0.4375 on all three** | **0.4375 on all three** (M14/M23/M73) |
| ASN WB (R/B) | Differs per file (e.g. R=1.499–1.741) | Differs per slot |
| FM patch hypothesis | **Ruled out** — ReferenceCam looks correct with the same wide FM in file tags | |

**Conclusion:** Good ReferenceCam color is **not** explained by distinct ForwardMatrix tags in the DNG file. Next work is **session / pixel / ColorMatrix2 / lens-shading / pairing**, not TIFF FM surgery.

**APK (`base.apk`):** `DngCreator`, `setPhysicalCameraId`, `LOGICAL_MULTI_CAMERA`, `LENS_SHADING` present in dex (`apk_strings_grep.txt`).

**User ReferenceCam captures + dumpsys (`referenceapp_adb_forensics_20260518_025806`):** ReferenceCam **CONNECT** sequence **device 3 → 2 → 4** — **same leaf ids** as P&S M14/M23/M73. Camera id choice is **ruled out** as the sole difference vs ReferenceCam color.

---

## Hypothesis buckets (validate against `hfr-runs/referenceapp_reference_*/diff_report.md`)

### 1. Camera id / session topology — same ids; pairing / requests differ

**Confirmed:** ReferenceCam and P&S both use leaf **3 / 2 / 4** for UW / wide / tele on this session.

**Still open:**

- ReferenceCam may use leaf `DngCreator(characteristics, result)` without hybrid logical pairing; P&S leaf saves show `picked=null` in `dng save diag` — verify `DngCreator` inputs match opened `camId`.
- Tele HAL: `logicalCameraId: 3, cameraId: 4` on close — check whether P&S tele session should use logical **3** for metadata when device is **4**.

**P&S direction:** Leaf RAW still path: `DngCreator(characteristics(camId), totalResult)` **without** `DngMetadataResolver` hybrid when `sessionCameraId` is already a leaf id (`picked=null`, empty `physicalCameraTotalResults` in `dng save diag`).

### 2. RAW stream map

**P&S:** `pickRawOutputForPreviewSession` with `usePhysicalChildRawStreamMapForLogicalSession=false` and `shouldPreferRawSensorForAuxPhysicalPreviewPin` on logical parent.

**Check ReferenceCam:** `RAW_SENSOR` vs `RAW12` in APK strings / logcat; WxH in tag diff vs P&S `rawFmt=32` / `4096x3072`.

### 3. Capture request keys

**ExternalCameraApp (API pattern only):** `STATISTICS_LENS_SHADING_MAP_MODE_ON` for RAW stills.

**P&S today:** No `LENS_SHADING` keys in codebase (grep).

**Spike:** Gate on characteristics; set on `TEMPLATE_STILL_CAPTURE` when RAW attached — USB compare to ReferenceCam logcat if visible.

### 4. Post-DngCreator processing

**P&S (May 2026):** `DngCreator` → optional `UniqueCameraModel` (50708) → `LeafDngHalReconcile` (**AsShotNeutral only**) → `StillCaptureMetadata.applyToDngUri` (**in-place TIFF only, no ExifInterface**).

**Do not reintroduce:** `ExifInterface` on DNG; IFD0 CM/FM TIFF surgery in `LeafDngHalReconcile`.

**Check ReferenceCam:** If tags match P&S but color differs → unlikely; if tags differ per lens (distinct FM per cam) → HAL + correct pairing, not TIFF rewrite.

### 5. Role / id table mismatch

`DngForwardMatrixFix` assumed cam **2=UW, 3=wide, 4=tele**; P&S routing uses **3** for M14 UW slot. Any future calibration must use **runtime `cameraIdAfter`**, not a fixed table.

---

## Implementation order (after forensics)

1. Confirm ReferenceCam tag diff shows per-lens distinct ForwardMatrix (or correct CM2) without post-patch.
2. Narrow leaf vs logical path in `PreviewEngineScreen` still save (resolver only when `physicalChildren` non-empty on session id).
3. Optional: lens shading on still RAW request.
4. USB: `pns_aux_dng_capture_analyze.ps1` + visual check in darktable/ACR; compare to ReferenceCam files in same folder.

**Locks unchanged:** `allowPhysicalTotalResultPairing=false`, preview-only physical pin, §4a stream hints off, §2 Default RAW tier per `AGENTS.md`.

---

## Artifacts

| Run | Path |
|-----|------|
| ReferenceCam pull + diff | `hfr-runs/referenceapp_reference_*` |
| ReferenceCam logcat/dumpsys | `hfr-runs/referenceapp_adb_forensics_*` |
| P&S baseline capture | `hfr-runs/aux_dng_capture_analyze_*` |
