# ProShot APK analysis — DNG / still pipeline vs Point & Shoot

**Package:** `com.riseupgames.proshot2` **8.34** (`versionCode` 546)  
**APK:** `hfr-runs/proshot_apk_decompile/proshot2_base.apk` (pulled from CPH2655 `8bf09993`)  
**Decompile:** `hfr-runs/proshot_apk_decompile/jadx_sources/` (jadx 1.5.1)  
**Refresh:** `.\scripts\pns_proshot_apk_decompile.ps1 -Serial <serial>`  
**Needle scan:** `hfr-runs/proshot_apk_decompile/scan.json`

ProShot shares the **same obfuscated RiseUp camera stack** as ReferenceCam (`l0.C0353b0`, `m0.RunnableC0539s`, `m0.C0527f`, `m0.T`). See also `docs/REFERENCEAPP_APK_FLEET_ANALYSIS.md`.

---

## Executive summary (tele 13.8 mm underexposure)

| Area | ProShot | P&S (before this doc) | Fleet takeaway |
|------|---------|------------------------|----------------|
| **DNG encode** | `DngCreator(opened chars, still CaptureResult).writeImage` — **no** ASN/CM TIFF surgery | Pure-HAL `DngCreator` + in-place Make/Model only | Same class of save path; do **not** reintroduce color surgery |
| **Still surfaces** | **JPEG and/or RAW ImageReader only** — **not** preview | Historically preview + RAW | **Match ProShot:** still request without preview target (AE bias risk) |
| **Still IQ defaults** | `VIGNETTE_CORRECTION=true`, `LENS_SHADING_MAP=true` → `SHADING_MODE` HQ + map ON when caps allow | Gated / profile-dependent; PureHal had skipped IQ | Capability-gated still IQ on all SKUs |
| **AE on Auto still** | `CONTROL_AE_MODE` ON (flash off); optional precapture for flash | AE ON; face AE regions possible | Skip face AE on RAW still (already); HAL default meter |
| **Default AE rect** | Full array **weight 0** (`B4()`) = ignore custom regions | Face / tap regions | Prefer empty/default metering on still |
| **OIS** | Rear OIS **ON** by default on still (`u6`) | Optional HUD “OIS off for still” | Keep default ON unless user opts out |
| **AWB / CC (`w6`)** | Auto: `CONTROL_AWB_MODE=AUTO`, `AWB_LOCK=false`, `COLOR_CORRECTION_MODE=HQ` on LIMITED/FULL/LEVEL_3 | JPEG ISP bias could override CC | Fleet: `RawStillProcessingHints.applyProShotStyleAwbAndColorCorrection` on RAW stills |
| **Zoom on still** | `x6(0)` → `CONTROL_ZOOM_RATIO` base / full FOV | Prime-eq remap crops 85/150 on tele via `SCALER_CROP_REGION` | Keep prime / FocalMode crops; `CONTROL_ZOOM_RATIO=1` only resets pinch-zoom, does **not** clear 85/150 digital crop |
| **Vendor tags** | `org.codeaurora.qcamera3.sharpness.strength` etc. (`m0.Y`) | Not mirrored | Optional later; not required for DNG loadability |

Valid bisect pair remains **tele FocalLength 13.8 mm only** — do not compare mismatched UW FLs.

### Residual (USB 2026-07-12, OP13 tele)

**ProShot tele DNGs (FL 13.85):** ASN ≈ 0.56, **no OpcodeList2**. Bayer **R/G is scene-dependent** — morning/fixture samples range **~0.52–0.57** (e.g. `20260712_081040.dng` full R/G≈0.52; “good” refs ≈0.57). Always **HEIC+DNG** dual-target.

**P&S after ProShot-aligned still work + YUV-free session:**
| Lever | Result |
|-------|--------|
| Native 73 crop, face OFF on still, dual JPEG+RAW, map OFF | Baseline footprint OK |
| **YUV-free** REGULAR (`OMIT_YUV_ANALYSIS_FOR_PURE_HAL_RAW_SESSION`) | `wantYuv=false`; R/G **0.40→0.43** (kept — ProShot stream set) |
| Map ON | OpcodeList2 only; **Bayer unchanged** → keep map **OFF** |
| `SHADING_MODE` OFF when `lensShadingApplied=true` | Correct Camera2; **Bayer unchanged** vs HQ |

**Channel means (yuv-free):** P&S **R≈77 ≈ ProShot R**, but **G elevated** (full ~180 vs ProShot ~138) — green excess at edges (center R/G≈0.53–0.54 ≈ ProShot “lo” samples). Not fixed by map/YUV/shading toggles.

**Next (superseded 2026-07-13):** ASN Bayer sync does **not** fix UW RAW black-crush. Active sprint: **DNG-FLEET-EXPOSURE-2026-07** — exposure-first matrix [`docs/DNG_FLEET_EXPOSURE_BISECT_MATRIX.md`](DNG_FLEET_EXPOSURE_BISECT_MATRIX.md). Re-run tele when 73 recovers. Do **not** force full-array crop.

---

## ProShot same-scene dead ends (2026-07-12…13, OP13)

Do **not** re-bisect these without a new hypothesis. Ledger: **REG-20260713-001…003**, **REG-20260712-001…007**.

| Lever / mistake | Result | Artifact / REG |
|-----------------|--------|----------------|
| ASN sync as UW darkness fix | ASN already ≈ ProShot; mosaic crushed (p50 8 vs 68); TIFF OK | `same_scene_14_61_20260713_0106` · REG-20260713-001 |
| Multi-CFA scoring for ASN | Wrong phase → ASN R≈0.95 / sync no-op | REG-20260713-002 / REG-007 |
| Full Bayer ASN (R+B) | Blue crush after CM | REG-007 |
| Force ASN when Δ≈0 | No-op; image still dark | `same_scene_14mm_20260713_0100` |
| Map ON / shading HQ↔OFF / YUV-free / CC HQ off | Tele Bayer color unchanged | `dng_*_tele73_20260712` · REG-20260713-003 |
| Mismatched UW FL pairs | Invalid truth | REG-20260712-001 |
| `forceFullActiveArrayCrop` | Breaks 85 prime crop | REG-20260712-003 |
| `ExifInterface` / CM/FM on DNG | Unopenable / wide-cal leak | dng-save lock |

**Valid pairs:** same FocalLength (±0.1 mm), same scene, ProShot + P&S. Host metric: `scripts/dng_same_scene_exposure_metric.py`.

**Wide control:** FL 6.1 mm in `same_scene_14_61_20260713_0106` — P&S exposure/color acceptable; any UW “fix” must not regress wide.

---

## DNG save (`m0.RunnableC0539s.h`)

```java
DngCreator dngCreator = new DngCreator(this.f6722l, this.f6723m.f6519a);
dngCreator.writeImage(byteArrayOutputStream, this.f6711a); // thumbnail probe
dngCreator.setThumbnail(...);
dngCreator.setLocation(...); // optional
dngCreator.setOrientation(...);
dngCreator.writeImage(this.f6724n, this.f6711a); // file
```

- `f6722l` = characteristics for **opened** camera (`m0.T.f6459a.f6612b`)
- `f6723m.f6519a` = still `CaptureResult`
- **No** post-save ColorMatrix / AsShotNeutral rewrite

---

## Still fire (`l0.C0353b0` ~3418–3613)

1. `createCaptureRequest(2)` = **`TEMPLATE_STILL_CAPTURE`**; `CONTROL_CAPTURE_INTENT=2`
2. Optional `createCaptureRequest(6)` = **`TEMPLATE_MANUAL`** + ZSL when manual ISO/exp / bracket and not extension
3. `A5(builder, 0.0f, true)` — still IQ + AE/AWB/OIS/NR
4. `addTarget` JPEG (`f5905D0`) and/or RAW (`f5906E0`) only
5. Usually `stopRepeating()` then `captureBurst`
6. AE path helpers: `L6()` / `f6()` set `CONTROL_AE_PRECAPTURE_TRIGGER` START/CANCEL; default meter `B4()` = full array **weight 0**

### P&S alignment (2026-07-12)

| ProShot | P&S fleet path |
|---------|----------------|
| No preview on still | RAW/JPEG ImageReaders only |
| `LENS_SHADING_MAP` + vignette defaults | Capability-gated `StillCaptureIqPolicy` under pure-HAL |
| Precapture trigger | `ReferenceAppStillPrecapture.shouldRun` = key advertised + rear camera; **rebuild still with converged `TotalCaptureResult`** (do not discard) |
| `B4()` weight 0 | `applyProShotStyleDefaultAeRegions` on RAW still |
| `w6` AWB AUTO + CC HQ | `applyProShotStyleAwbAndColorCorrection`; pure-HAL **AE_LOCK** after precapture (**not** AWB_LOCK; **not** SENSOR_* latch) |
| `x6(0)` zoom reset | `CONTROL_ZOOM_RATIO=1` on RAW still builder; **keep** prime-eq / `FocalMode` `SCALER_CROP_REGION` (85/150) — do not force full-array |
| Still IQ without JPEG-bias overrides | Skip `PreviewJpegProcessingHints` on RAW still (`StillCaptureIqPolicy` owns EDGE/NR/TONEMAP) |

### Still IQ (`A5`, `z2 == true`) — defaults from `j0.C0250o` V56 init

| Pref / cap | Request |
|------------|---------|
| `VIGNETTE_CORRECTION` (default **true**) | `SHADING_MODE` 2 (HQ) or 1 (FAST) |
| `LENS_SHADING_MAP` (default **true**) + profile `f6567F` | `STATISTICS_LENS_SHADING_MAP_MODE` ON |
| `IMAGE_PLUS == 1` | `TONEMAP_MODE` HQ, aberration HQ |
| profile `f6575J` | `HOT_PIXEL_MODE` HQ |
| distortion modes | `DISTORTION_CORRECTION_MODE` 1/2 |
| sharpness | `EDGE_MODE` + vendor `qcamera3.sharpness.strength` |

---

## What does *not* explain tele green alone

- Different ColorMatrix2 in file (tele pair: **identical**)
- ProShot DNG “Software” OEM fingerprint vs “Point & Shoot” (cosmetic)
- Post-save ASN surgery (ProShot does none)

Tele gap remains **lower still ISO at same shutter** (AE / still request stream set) on the **stop-first** P&S path. **2026-07-13 PS01:** rebuilding ProShot `L6`/`i4`/`j4` (PRECAPTURE on repeating + capture, then stop + STILL) lifted mosaic ~1.5× vs E10 on OP13 UW/tele without EV tweaks — still short of ProShot same-scene; keep ADB-only (`pns_preview_dng_proshot_pipeline`).

---

## Key source anchors

| Topic | File |
|-------|------|
| DNG save | `m0/RunnableC0539s.java` |
| Still fire + IQ | `l0/C0353b0.java` (`A5`, still ~3418+) |
| Defaults | `j0/C0250o.java` (`LENS_SHADING_MAP`, `VIGNETTE_CORRECTION`) |
| Vendor sharpness | `m0/Y.java` |
| Device profile | `m0/C0527f.java` |

*Generated 2026-07-12 from ProShot 8.34 on CPH2655.*
