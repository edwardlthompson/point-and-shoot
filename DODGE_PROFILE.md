# Dodge profile (OnePlus 13 / LineageOS 23 / Camera2)

This document maps the **Point & Shoot** shooting modes (15/23/35/50/73/85/150/21mm eq.) to **Camera2 cameraId(s)** on the OnePlus 13 (`dodge`). It satisfies **BUILD_PLAN.md §3** (“hardware-to-software mapping”): each focal-equivalent mode lists **cameraId(s)**, **physicalCameraId(s)** where logical, **sensor role + constraints** (RAW / HDR·DCG / OIS / macro), and **digital-crop metadata strategy** for 35 / 50 / 85 / 150mm.

Primary probe artifacts: exported **`PROBE_EXPORT_LATEST.md`** (Markdown dump), **`hfr-runs/deep_caps_round11.json`** (lensInfo + keys; adb **8bf09993**), plus newer **`deep_caps_*.json`** / **`logical_physical_*.json`** runs under `hfr-runs/` when present.

**Focal slot availability (Milestone 10.2, pure policy):** digital equivalents **35 / 50 / 85 / 150** mm are only meaningful when the active rear **active-array** budget is **≥ 12 effective MP** (`width × height / 1e6`); see **`FocalSlotAvailability`** in code (UI graying / lens strip remains **[MIXED]**).

## Spec ↔ Camera2 mapping (master table)

Auditors should reconcile this table with **§ Mode mapping** and **§ Lens + sensor info** below. Rows marked **digital crop** do not change `cameraId`; they rely on `CropPlan` + (Phase 1) `SCALER_CROP_REGION` + DNG tags.

| Spec focal (eq.) | Vendor sensor (bill-of-materials) | Primary `cameraId` | Logical / physical | `physicalCameraIds` (if logical) | RAW12 / SENSOR | HDR / DCG (probe) | OIS (Camera2 API) | Macro / focus | Crop / metadata strategy |
|---|---|---:|---|---|---|---|---|---|---|
| **15mm** UW / 🌷 Super Macro | Samsung S5KJN5 | **3** | physical | — | yes (`4096×3072`) | session profiles in HDR probe | API reports OFF only | **~25 diopters** (~4 cm) UW — see § Lens + sensor | Lens switch only |
| **23mm** main wide | Sony LYT-808 | **2** (alt. logical **0**) | physical (see logical row) | logical **0** → `[2, 3, 4]` | yes (`4096×3072`) | LBMF / DCG paths per vendor tags | API reports OFF only | 10 diopters (~10 cm) — not classified macro | Lens switch; optional seamless zoom via logical **0** |
| **35mm** street | Sony LYT-808 | **2** | physical | — | same as 23mm | same | same | same | **Digital:** `CropPlan` **Street35** (1.5× center); **`PreviewEngineScreen`** **`SCALER_CROP_REGION`** (+ `DngDefaultUserCropRatios` for metadata parity); still-capture path Phase 1 |
| **50mm** standard | Sony LYT-808 | **2** | physical | — | same | same | same | same | **Digital:** **Standard50** (2.2×) + **`CONTROL_AE_REGIONS`** center patch in preview; still capture Phase 1 |
| **73mm** tele | Sony LYT-600 | **4** | physical | — | yes (`4096×3072`) | same | same | ~58 cm min focus | Lens switch only |
| **85mm** portrait | Sony LYT-600 | **4** | physical | — | same | same | same | same | **Digital:** **Portrait85** (1.16×) + Eye-AF priority — preview **`SCALER_CROP_REGION`** wired |
| **150mm** long tele | Sony LYT-600 | **4** | physical | — | same | same | same | same | **Digital:** **LongTele150** (~2.04×, 12 MP from 50 MP) — preview **`SCALER_CROP_REGION`** wired; still encode Phase 1 |
| **21mm** selfie | Sony IMX615 | **1** | physical (FRONT) | — | yes (`3280×2464`) | front pipeline | OFF | fixed focus | Lens switch; “zero beauty” = policy later |

**Logical `cameraId=0`:** Multi-camera wrapper exposing **`physicalCameraIds: [2, 3, 4]`** (wide / UW / tele). Use for OEM-style fused zoom; discrete Point & Shoot modes select **2 / 3 / 4** directly unless we intentionally fuse.

## Current Camera2 topology (from probe)

- **Cameras enumerated**: 5 (`cameraId` 0–4)
- **Front**: `cameraId=1` (facing=FRONT, focalLengths=[3.23], RAW_SENSOR=3280x2464)
- **Back logical multi-camera**: `cameraId=0` reports `physicalCameraIds: [2, 3, 4]`
- **Back physical**:
  - `cameraId=2` (facing=BACK, focalLengths=[6.06], RAW_SENSOR=4096x3072)
  - `cameraId=3` (facing=BACK, focalLengths=[2.3], RAW_SENSOR=4096x3072)
  - `cameraId=4` (facing=BACK, focalLengths=[13.85], RAW_SENSOR=4096x3072)

## High-speed preview capability (HFR) (from probe)

This is **Camera2 constrained high-speed** (`StreamConfigurationMap.highSpeedVideoSizes` + `getHighSpeedVideoFpsRangesFor(size)`).

- **480 fps available**:
  - `cameraId=0`: 1280x720 and 1920x1080 include `[480, 480]`
  - `cameraId=2`: 1280x720 and 1920x1080 include `[480, 480]`
- **240 fps available (no 480 listed)**:
  - `cameraId=3`: up to `[240, 240]`
  - `cameraId=4`: up to `[240, 240]`
- **120 fps max (front)**:
  - `cameraId=1`: up to `[120, 120]`

## RAW feasibility (from probe)

All 5 cameras report:

- `android.request.maxNumOutputRaw: 1`
- RAW_SENSOR outputs are present (sizes above)

This satisfies the Phase 0 “RAW feasible per cameraId” gate; next step is validating **real capture stability** in Phase 1.

## Mode mapping (working hypothesis)

These are the most reasonable assignments based on focal length clustering and topology. We will refine once we add/consume: apertures, OIS modes, min focus distance, physical sensor size, and any vendor lens role tags.

| Spec mode | Intended sensor | Camera2 target(s) | Notes |
|---|---|---|---|
| 23mm Main wide | Sony LYT-808 | `cameraId=2` (and/or logical `cameraId=0`) | `cameraId=2` matches the 6.06mm focal cluster and has 480fps HFR; `cameraId=0` is a logical wrapper over [2,3,4]. |
| 35mm Street crop | Sony LYT-808 crop | `cameraId=2` | Digital crop + metadata only (no lens switch). Preview: **`SCALER_CROP_REGION`**. |
| 50mm Standard crop | Sony LYT-808 crop | `cameraId=2` | Digital crop + center-weighted metering (`CONTROL_AE_REGIONS`) in preview. |
| 15mm Ultra-wide / Macro | Samsung S5KJN5 | `cameraId=3` | 2.3mm focal cluster strongly suggests UW. Macro depends on min focus distance / mode-switch behavior (to confirm). |
| 73mm Tele | Sony LYT-600 | `cameraId=4` | 13.85mm focal cluster strongly suggests tele. |
| 85mm Portrait crop | Sony LYT-600 crop | `cameraId=4` | Digital crop + Eye-AF prioritization; preview **`SCALER_CROP_REGION`** wired. |
| 150mm Long-tele crop | Sony LYT-600 crop | `cameraId=4` | 12 MP center crop of the 50 MP sensor (~2.04x); preview **`SCALER_CROP_REGION`** wired; still encode output sizing Phase 1. |
| 21mm Front | Sony IMX615 | `cameraId=1` | Front camera. “Zero beauty” is a capture-request/vendortag policy to enforce later. |

## Host-side mapping (FocalMode -> CropPlan -> CapabilityGate)

The repo ships a pure-data mapping that lets unit tests cover the digital-
crop side of this table without a device. The relevant Kotlin sources are
listed here so an audit can grep its way from "spec mode" -> "code" without
leaving the file. Lens-switch modes (15 / 23 / 73 / 21 mm) live in the
"Mode mapping" table above and route directly to the `cameraId`; only the
crop-derived modes have `FocalMode` enum entries.

| FocalMode | `CropPlan` constant | `CapabilityGate.Feature` hints | Notes |
|---|---|---|---|
| `FocalMode.Street35` | `CropPlan.centeredCrop(FocalMode.Street35, ...)` | (Standard Pro baseline: `RawDng` + `TenBitHdrAvif`) | 1.5x center crop on `cameraId=2`; DNG `DefaultUserCrop` carries the metadata. |
| `FocalMode.Standard50` | `CropPlan.Standard50` | `HighlightWeightedMetering` (when AE histogram is available) | 2.2x center crop on `cameraId=2`; center-weighted metering hint. |
| `FocalMode.Portrait85` | `CropPlan.Portrait85` | `EyeAfOverlay` (face-detect FULL when available) | 1.16x center crop on `cameraId=4`; Eye-AF priority. |
| `FocalMode.LongTele150` | `CropPlan.LongTele150` | `EyeAfOverlay` | ~2.04x center crop on `cameraId=4` (12 MP from 50 MP sensor). |

Lens-switch modes (no enum entry, direct `cameraId` selection):

| Spec mode | Active `cameraId` | `CapabilityGate.Feature` hints |
|---|---|---|
| 15 mm Ultra-wide / Macro | `cameraId=3` | `SuperMacroLock` (when min-focus-distance is short enough) |
| 23 mm Main wide | `cameraId=2` (or logical `cameraId=0`) | `RawDng`, `TenBitHdrAvif`, optional `HfrPreview120` |
| 73 mm Tele | `cameraId=4` | `EyeAfOverlay` (face-detect FULL when available) |
| 21 mm Front | `cameraId=1` | (no extra; "zero beauty" enforced via capture-request policy) |

Source:
`app/src/main/java/dev/pointandshoot/CropPlan.kt` (the `FocalMode` enum
lives in this file alongside `CropPlan`),
`app/src/main/java/dev/pointandshoot/CapabilityGate.kt`.

## HFR recipes (live, from the latest probe artifact)

The AboutScreen renders these dynamically from `EncoderSummary` (built on
top of `EncoderResultAggregator` + `EncoderRecipeBuilder`) so the doc and
the in-app surface cannot drift. The static table below is the audit-time
snapshot for offline reference; the in-app surface is the source of truth
for any specific build.

| cameraId | Best HFR (preview) | Best HFR (encoded video) | Notes |
|---|---|---|---|
| `0` (logical) | `1920x1080 @ [480, 480]` | from latest `exhaustive_probe_*.json` / About live | Wraps physicals `[2, 3, 4]`. |
| `1` (front) | `1280x720 @ [120, 120]` | same | IMX615. |
| `2` (LYT-808) | `1920x1080 @ [480, 480]` | same | 23mm main wide. |
| `3` (S5KJN5) | `1280x720 @ [240, 240]` | same | 15mm ultra-wide / macro. |
| `4` (LYT-600) | `1280x720 @ [240, 240]` | same | 73mm tele. |

Run `scripts/pns_hfr_autorun.ps1 -RunExhaustive` (optionally `-ExhaustiveHfrOnly`) or `-RunFullSuite` to refresh on-device `exhaustive_probe_*.json`; **About → From the latest probe (live)** reads the newest file under `getExternalFilesDir(null)`.

## Imaging profile defaults per FocalMode

Each `FocalMode` defaults to the Standard Pro imaging profile (lossless DNG
+ 10-bit AVIF + Display P3) unless explicitly overridden. Switching to
Ultra-Max bumps the imaging pipeline to uncompressed RAW12 + 12-bit JXL +
Rec. 2020 across every focal mode (see `ImagingProfile.kt`).

## Color & LUT pipeline applicability

| FocalMode | LUT applies to preview | LUT applies to video | LUT applies to stills | RAW LUT'd? |
|---|---|---|---|---|
| All focal modes | yes (GLES shader) | yes (GLES surface) | yes (CPU pass on encode thread) | **never** (RAW lives in sensor domain) |

See `CAPTURE_ARCHITECTURE.md` "Color & LUT pipeline (Phase 4)" for the
full executor + backpressure rules.

## Storage filename + GroupingID convention

Captures land in `MediaStore.Images` under `Pictures/Point & Shoot/...`
with the filename pattern `pns_<utc>_<profile>_<seq>.<ext>`
(see `CaptureStorage.openOutput`). Bracket / focus-stack sequences share a
stable `BracketPlan.bracketGroupingId` so desktop tooling can re-group them
post-capture; the GroupingID is mirrored into the DNG metadata (Phase 1).

## Lens + sensor info (from probe)

The deep-caps probe (`DeepCapsProbeScreen.runDeepCapsProbe`) emits a
typed `lensInfo` block per cameraId, surfaced in
`hfr-runs/deep_caps_round11.json` and parsed back through
`LensInfoSummaryJson.decode`. Values are framework-reported.

| cameraId | facing | apertures | OIS modes | min focus | hyperfocal | focal length | sensor size (mm) | active array (px) | orientation |
|---|---|---|---|---|---|---|---|---|---|
| `0` (logical) | BACK | f/1.60 | OFF only | 10 diopters (10 cm) | 0.10 diopters (10.2 m) | 6.06 mm | 9.18 x 6.88 | 4096 x 3072 | 90 deg |
| `1` (front IMX615) | FRONT | f/2.40 | OFF only | 0 (fixed-focus) | 0.37 diopters (2.7 m) | 3.23 mm | 5.25 x 3.94 | 3280 x 2464 | 270 deg |
| `2` (LYT-808 main wide) | BACK | f/1.60 | OFF only | 10 diopters (10 cm) | 0.10 diopters (10.2 m) | 6.06 mm | 9.18 x 6.88 | 4096 x 3072 | 90 deg |
| `3` (S5KJN5 ultra-wide) | BACK | f/2.05 | OFF only | **25 diopters (4 cm)** | 0.50 diopters (2.0 m) | 2.30 mm | 5.24 x 3.93 | 4096 x 3072 | 90 deg |
| `4` (LYT-600 tele) | BACK | f/2.60 | OFF only | 1.72 diopters (58 cm) | 0.022 diopters (46 m) | 13.85 mm | 6.55 x 4.92 | 4096 x 3072 | 90 deg |

### Findings

- **Macro confirmation**: `cameraId=3` reports a 25-diopter minimum focus
  distance (~ 4 cm). This is well above the
  `LensInfoSummary.MACRO_MIN_DIOPTERS_THRESHOLD = 15f` threshold (6.7 cm)
  and confirms super-macro is wired to the ultra-wide. The threshold
  was tuned UPWARD from an initial 10 diopters because the LYT-808 main
  wide reports exactly 10 diopters of close-focus capability, which would
  have falsely triggered the macro mode.
- **OIS gap**: NO camera advertises a non-`OFF` mode in
  `LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION`, even though the OnePlus
  13 ships hardware OIS on the LYT-808 (main wide, `cameraId=2`) and
  the LYT-600 (periscope tele, `cameraId=4`). This means OIS is NOT
  exposed via the standard Camera2 API on this device; we will need to
  surface it later via vendor capture-request keys or accept that OIS
  stays hardware-default. `CapabilityGate.Feature.OpticalStabilization`
  is currently False across the board until that vendor key is
  identified.
- **Aperture pinning**: every back camera reports a single fixed aperture
  (no variable-aperture support), as expected for a phone optical stack.
- **Sensor size sanity**: the LYT-808 (1/1.4 inch effective area,
  9.18 x 6.88 mm) and LYT-600 (1/2.7 inch, 6.55 x 4.92 mm) match
  published OnePlus 13 specifications; the S5KJN5 footprint matches a
  1/3.4 inch UW-class sensor.

### Source artifact

`hfr-runs/deep_caps_round11.json` (pulled 2026-05-08 from adb 8bf09993).
Re-run via `adb shell am start -W -n dev.pointandshoot/.MainActivity
--es pns_screen deepcaps --ez pns_autodeepcaps true`; the JSON lands in
`getExternalFilesDir(null)/deep_caps_<utc>.json` and can be pulled
with `adb pull`.

## Outstanding Phase 0 follow-ons

- Vendor key search for OIS exposure on `cameraId=2` (LYT-808 main) and
  `cameraId=4` (LYT-600 tele): both have hardware OIS but the standard
  Camera2 API surfaces only `OFF`. Probe the vendor request-key
  namespace (`com.oplus.*`, `com.oneplus.*`) for an `oisMode`
  analogue.
- Vendor tags that explicitly label main / uw / tele roles - the
  deep-caps probe enumerates every characteristic / request / result key
  by name; an audit pass on `deep_caps_round11.json` against the
  `cameraId=2/3/4` key lists is the next step.

