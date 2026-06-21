# Camera app pipeline benchmark (Milestone 28.0)

Evidence base for **Milestone 28 — Feature richness + pipeline parity**. Compares Point & Shoot (P&S) against a fixed FOSS corpus: Google **`android/camera-samples`** (pattern reference), Mark Harman **Open Camera** (SourceForge), and five GitHub camera apps.

**Program:** [`BUILD_PLAN.md`](BUILD_PLAN.md) Milestone 28 · plan `.cursor/plans/camera_pipeline_benchmark_ba492901.plan.md`

**Latest scan:** `hfr-runs/camera_benchmark_20260620_120123/` (`benchmark_needles.json`, `benchmark_scan_summary.md`)

**Regenerate:**

```powershell
.\scripts\pns_camera_app_pipeline_scan.ps1
```

---

## Corpus tiers

| Tier | Repo | Role | Stack (needle profile) |
|------|------|------|------------------------|
| **0 — Pattern reference** | [android/camera-samples](https://github.com/android/camera-samples) | Google Camera2 + CameraX samples (not a product app) | Both stacks side-by-side; `DngCreator`, `SessionConfiguration`, `MediaRecorder` |
| **1 — F-Droid flagship** | [SourceForge Open Camera](https://git.code.sf.net/p/opencamera/code) (Mark Harman) | Long-running Camera2 pro/consumer hybrid | `CameraDevice`, `DngCreator`, `MediaRecorder`, `IS_PENDING`, no CameraX |
| **2a — CameraX consumer** | [GrapheneOS/Camera](https://github.com/GrapheneOS/Camera) | Privacy-first CameraX app | `ProcessCameraProvider`, `REMOVE_EXIF`, extensions |
| **2b — CameraX consumer** | [FossifyOrg/Camera](https://github.com/FossifyOrg/Camera) | Fossify commons CameraX | CameraX capture + `IS_PENDING` |
| **2c — OEM replacement** | [LineageOS Aperture](https://github.com/LineageOS/android_packages_apps_Aperture) | Lineage stock camera | CameraX + heavy `ExtensionMode` / `FACE_RETOUCH` strings (engineering inventory only for P&S) |
| **2d — Computational** | [eszdman/PhotonCamera](https://github.com/eszdman/PhotonCamera) | Camera2 + GLES/C++ compute | `ImageReader`, `DngCreator`, native merge stack |
| **2e — Legacy pro** | [almalence/OpenCamera](https://github.com/almalence/OpenCamera) | Almalence OpenCamera (not Mark Harman) | `CameraDevice`, `MediaRecorder`, multicam class |

**P&S position:** Camera2-primary preview engine (`PreviewEngineScreen.kt`), dedicated encode lane (`PNS.Reader`), fleet matrix + honesty gates — closer to **Tier 1 + Photon** than to pure CameraX consumer apps. CameraX is used for probes and optional extension handoff (Sprint **28.2** spike), not as the main preview session.

---

## Needle scan summary (2026-06-20)

Shallow clone + grep for pipeline API needles (see script for full list). Counts = files matching pattern (not occurrences).

| Needle | android/camera-samples | Open Camera (SF) | GrapheneOS | Fossify | Aperture | Photon | Almalence |
|--------|------------------------|------------------|------------|---------|----------|--------|-----------|
| `ProcessCameraProvider` | 5 | 0 | 1 | 3 | 1 | 0 | 0 |
| `ImageCapture` / `VideoCapture` | 6 / 3 | 3 / 11 | 7 / 6 | 9 / 5 | 8 / 1 | 0 / 0 | 0 / 6 |
| `CameraDevice` | 17 | 2 | 0 | 0 | 0 | 2 | 6 |
| `ImageReader` | 3 | 1 | 0 | 0 | 0 | 8 | 3 |
| `DngCreator` | 3 | 3 | 0 | 0 | 0 | 7 | 1 |
| `MediaRecorder` | 13 | 13 | 0 | 0 | 0 | 2 | 5 |
| `SessionConfiguration` | 3 | 1 | 0 | 0 | 0 | 1 | 0 |
| `ExtensionMode` | 7 | 0 | 1 | 0 | 6 | 0 | 0 |
| `IS_PENDING` | 0 | 4 | 3 | 1 | 0 | 0 | 0 |
| `REMOVE_EXIF` | 0 | 0 | 3 | 0 | 0 | 0 | 0 |
| `FACE_RETOUCH` / `BEAUTY` | 4 / 0 | 2 / 12 | 2 / 0 | 0 / 0 | 46 / 0 | 0 / 0 | 0 / 0 |
| `ExifInterface` | 16 | 6 | 8 | 4 | 5 | 4 | 4 |

### `android/camera-samples` subprojects

| Sample | Highlights |
|--------|------------|
| **Camera2Basic** | `CameraDevice`, `ImageReader`, `DngCreator` — RAW still reference |
| **Camera2Video** | `MediaRecorder` + `SessionConfiguration` — regular video session |
| **Camera2SlowMotion** | HFR `MediaRecorder` pattern |
| **Camera2Extensions** | Extension session + `ImageReader` |
| **CameraXBasic** | `ProcessCameraProvider`, `ImageCapture`, `MediaStore` |
| **CameraXVideo** | CameraX video + `MediaStore` |
| **CameraX-MLKit** | `ImageAnalysis` for ML Kit barcode/QR class |
| **CameraXExtensions** | `ExtensionMode`, `ImageCapture` (HDR/NIGHT/AUTO class) |

---

## Common pipeline patterns (G1–G8 checklist)

Audit targets for Sprint **28.1**. **USB gates PASS** on **b5214fc6** USB (2026-06-20).

| Id | Pattern | Peer norm | P&S mapping | Sprint 28.1 gate | 28.1 status |
|----|---------|-----------|-------------|------------------|
| **G1** | **Audio routing** | Explicit mic source (built-in / USB / BT) before record | `PnsAudioCaptureSupport`, video record path | `pns_audio_quality_test.ps1` | **PASS** `audio_quality_test_20260620_123511` |
| **G2** | **MediaStore pending writes** | `IS_PENDING=1` until bytes complete; then clear pending | `CaptureStorage` / composed still path uses pending contract per [`STORAGE_STRATEGY.md`](STORAGE_STRATEGY.md) | Host JVM + USB pull smoke | Code PASS · USB pending |
| **G3** | **JPEG focus-lock** | Precapture AF before JPEG-only / composed shutter when user expects sharp still | Still shutter builders; optional wait-for-focus (peer: Open Camera `WAIT_FOR_FOCUS_LOCK`) | `pns_photo_capture_verify.ps1` | **PASS** via `capture_pipeline_gate_20260620_123712` |
| **G4** | **Session configuration** | `SessionConfiguration` output list order stable; stream hints only when HAL-proven | `PreviewSessionRegularOutputsPolicy`; **§4a `streamHints=false`** locked on legacy-class fleet | `pns_capture_pipeline_verify.ps1` | **PASS** `capture_pipeline_gate_20260620_123712` |
| **G5** | **Backpressure / lanes** | Camera thread ≠ encode thread; bounded `ImageReader` queue | [`CAPTURE_ARCHITECTURE.md`](CAPTURE_ARCHITECTURE.md) — `PNS.Cam`, `PNS.Reader`, `PNS.Jpeg`, drop rules | `pns_analyze_reader_backpressure.ps1` on validate logs | Doc PASS · USB pending |
| **G6** | **Lifecycle / GLES preview** | Pause closes camera; preview aspect from single geometry writer | `onPause` camera close; **`setGeometry` only from `PreviewMainViewport`** (no second writer) | Chrome gate + gallery-return manual | Code PASS · USB pending |
| **G7** | **RAW session outputs** | RAW `ImageReader` WxH/format matches logical session map when unpinned | `RawCaptureSupport.pickRawOutputForPreviewSession`; `DngMetadataResolver` logical pairing | `pns_capture_pipeline_verify.ps1` | **PASS** `capture_pipeline_gate_20260620_123712` |
| **G8** | **DNG metadata + loadability** | `DngCreator(chars, result)` pairing consistent; no full-file EXIF rewrite on DNG | `Dng12Saver`, `StillCaptureMetadata.applyToDngUri` IFD-safe patches only | `pns_aux_dng_capture_analyze.ps1` | **PASS** `aux_dng_capture_analyze_20260620_123721` |

**Locks (do not flip without USB proof + `REG-*`):** §4a stream hints, §2 `Default` RAW tier order, DNG save pipeline, GLES aspect contract — see [`AGENTS.md`](AGENTS.md) CRITICAL sections.

---

## Feature matrix — peer vs P&S

Legend: **Shipped** · **Partial** (scaffold/code exists) · **ProbeOnly** · **Missing** (no consumer path) · **N/A** (product policy / intentional)

### P&S advantages (peers lack or are weaker on)

| Area | P&S | Typical peer |
|------|-----|--------------|
| Fleet RAW/DNG + matrix honesty | **Shipped** — `DngMetadataResolver`, openability gates | Open Camera DNG yes; CameraX apps usually no RAW |
| H-mode metering / readout chase | **Shipped** | Rare in consumer CameraX apps |
| HFR MediaCodec matrix (1080p–4K class) | **Shipped** / **Partial** surfacing | GOS/Fossify: basic 30 fps video |
| DCG HDR10 PQ + HLG 10-bit video | **Shipped** (`video.dcg_hdr`, `H265_10BIT`) | Not in corpus consumer apps |
| RAW video scaffold | **Partial** (`video.raw`) | Essentially none in tier-2 CameraX apps |
| Engineering hub + parity sweep | **Shipped** | Open Camera / Photon: settings depth; no fleet matrix |
| GLES LUT preview + calibration | **Shipped** | Photon: compute LUT; others: minimal |
| Dodge tele 73/85/150 + focal row | **Shipped** | Open Camera: focal zoom; not fleet-gated mm row |
| Bracket / ZSL / NightScape dial | **Shipped** | Open Camera: bracket; NightScape unique to P&S |
| Intervalometer / timelapse | **Shipped** | Open Camera yes; CameraX peers often no |

### Partial / scaffold closure (Wave B–C) — **closed M28 2026-06-21**

| Catalog id | Status at M28 close | Sprint |
|------------|---------------------|--------|
| `still.motion_photo` · `still.heic` · `still.jxl` · `still.tiff16` · `still.independent_tonal` · `still.monochrome_capture` | **Shipped** | 30.1 |
| `video.vp9` · `video.raw` · `video.raw_picker` · `video.4k_regular` · `video.uhd60` · `video.hfr` | **Shipped** | 30.2 |
| `video.av1` | **Partial** (probe; mux device-limited) | 30.2 |
| `video.dual` · `preview.pip` · `audio.spatial` | **Shipped** | 30.3 |
| `capture.wait_focus_lock` | **Shipped** | 31.3 |
| `video.multicam_melt` | **Partial** (arm scaffold) | 30.3 |
| `video.dual_iso` | **ProbeOnly** | 30.3 |
| `still.panorama` · `video.log_profile` · `still.ultrahdr` | **N/A** (spike / HAL) | 31.0–31.6 |
| `still.preview_shots` · `still.computational_hdr` · `preview.qr_mlkit` | **Planned** post-M28 | 31.2–32.2 |
| `still.depth` | **ProbeOnly** | 32.1 |

### Peer gaps → Milestone 28 closure (2026-06-21)

| Feature | M28 outcome | Catalog id |
|---------|-------------|------------|
| Panorama | Spike **NO-GO** — defer | `still.panorama` N/A |
| Preview Shots | **Planned** post-M28 | `still.preview_shots` |
| Log profile video | **N/A** — FlatCine substitute | `video.log_profile` |
| UltraHDR still | **N/A** on primary fleet | `still.ultrahdr` |
| Computational HDR | Spike **NO-GO** — defer | `still.computational_hdr` Planned |
| DEPTH still | **ProbeOnly** defer | `still.depth` |
| EXIF strip | **Shipped** Wave A | `privacy.exif_strip` |
| Settings export | **Shipped** Wave A | `product.settings_export` |
| Wait-for-focus-lock | **Shipped** | `capture.wait_focus_lock` |
| Secure camera launch | **Shipped** (handler) | `product.still_image_camera_secure_launch` |
| CameraX extensions | **ProbeOnly**; handoff spike N/A on CPH2583 | `camerax.hdr` / `camerax.auto` |
| ML Kit QR | **ZXing Shipped**; ML Kit deferred | `preview.qr` / `preview.qr_mlkit` Planned |

**Hard exclusion:** `FACE_RETOUCH` / `BEAUTY` / skin-smoothing — **never** consumer ship. `camerax.face_retouch` stays **ProbeOnly** engineering inventory.

### HDR / 10-bit video (not Dolby)

| Capability | P&S | Peers in corpus |
|------------|-----|-----------------|
| Dolby Vision / Dolby 10-bit | **N/A** — not in repo | None in FOSS corpus |
| DCG / HDR10 PQ (`video.dcg_hdr`) | **Shipped** | None consumer |
| HLG 10-bit (`VideoColorProfile.Hlg`) | **Shipped** | None consumer |
| Flat / cine profile | **Shipped** (`FlatCine`) | Open Camera log profile = **Missing** in P&S until 31.5 |

Human color sign-off: **CRI-033** (H.265 DCG @4K) — [`BUILD_PLAN.md`](BUILD_PLAN.md) Milestone H.

---

## Deep dive — Mark Harman Open Camera (Tier 1)

**Stack:** Camera2-first; `MediaRecorder` for video; `DngCreator` for DNG; heavy `MediaStore` + `IS_PENDING` usage.

**Pipeline traits P&S should mirror where audits fail (28.1):**

1. **Pending MediaStore rows** until file complete (G2).
2. **Focus discipline** — optional wait-for-AF before still (G3); preview shots + panorama are sequential still classes (31.x).
3. **Feature breadth** — panorama, preview shots, log video, geotag, intervalometer — P&S matches several; gaps listed in matrix above.
4. **No fleet matrix** — Open Camera optimizes for broad device support; P&S trades that for **honesty gates** and per-SKU matrix.

**Not adopting:** beauty filters (`BEAUTY` needles in SF tree) — product policy exclusion.

---

## Deep dive — `android/camera-samples` (Tier 0)

Use as **pattern catalog**, not UX reference.

| Pattern | Sample | P&S equivalent |
|---------|--------|----------------|
| Minimal Camera2 still + RAW | Camera2Basic | `PreviewEngineScreen` REGULAR session + `Dng12Saver` |
| Video on same session | Camera2Video | In-app `MediaRecorder` / `MediaCodecVideoRecorder` |
| Slow motion | Camera2SlowMotion | HFR MediaCodec path + FPS rail |
| OEM extensions | Camera2Extensions / CameraXExtensions | Engineering probe + Sprint **28.2** handoff spike |
| Analysis use-case | CameraX-MLKit | ZXing QR today; ML Kit optional **32.x** |
| Depth output | Camera2Basic DEPTH | `still.depth` Sprint **32.1** |

**CameraX vs Camera2:** P&S keeps Camera2 for the live preview engine. Extensions and ML Kit may use **isolated** CameraX routes after spike sign-off — not a primary migration.

---

## Milestone 28 catalog roll-up — **closed 2026-06-21**

Ship cuts: **beta.13** (Wave A) · **beta.14** (Wave B) · **beta.15** (Waves C+D). Catalog **v6**. Parity Delta PASS CPH2583 `parity_sweep_20260621_043558`. Full sprint archive: [`BUILD_PLAN_COMPLETED.md` — Milestone 28](BUILD_PLAN_COMPLETED.md#milestone-28--feature-richness-waves-a-d).

---

## Related docs

| Doc | Role |
|-----|------|
| [`CAPTURE_ARCHITECTURE.md`](CAPTURE_ARCHITECTURE.md) | P&S threading / backpressure target |
| [`STORAGE_STRATEGY.md`](STORAGE_STRATEGY.md) | MediaStore + pending contract |
| [`docs/CAMERA_CAPABILITY_TAXONOMY.md`](CAMERA_CAPABILITY_TAXONOMY.md) | Catalog categories + gap classes |
| [`docs/PNS_TECHNICAL_SETTINGS.md`](PNS_TECHNICAL_SETTINGS.md) | Runtime settings SoT |
| [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) | Index entry for M28 |
