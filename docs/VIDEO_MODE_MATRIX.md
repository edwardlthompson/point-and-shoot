# Video mode matrix (device-verified)

**Device:** OnePlus 13 (CPH2655)  
**Gate:** `scripts/pns_mediacodec_hfr_verify.ps1 -GateProfile vf -RequireFfprobeAv`  
**UI catalog:** `InAppVideoFormatSelection.loadCatalog` — each row requires **exact** labeled resolution+fps on the camera HS table (HFR) or encoder+MR (≤60). Encoder-only tiers such as **4K @ 120** are **not** shown when HAL has no **3840×2160** HS @ that fps.

## Legend

| Column | Meaning |
|--------|---------|
| **Path** | `MR` = MediaRecorder (≤60 fps), `MC` = MediaCodec + constrained HS burst |
| **Container fps** | `ffprobe` `avg_frame_rate` (gate: ≥ **75%** of target for HFR) |
| **Video packets** | `ffprobe` `nb_read_packets` — catches frozen single-frame clips |
| **Unique motion** | Subjective / `mcVideoFramesWritten` + capture path — **HEVC HFR is not honest on this fleet** (see below) |
| **A/V** | Both video and audio streams in the MP4 |

## Shown in video format picker (1080p HFR on CPH2655)

| Mode | Codec | Target | Path | Container | Notes |
|------|-------|--------|------|-----------|-------|
| Standard | H.264 | 60 | MR | 1920×1080 | |
| Standard | H.265 | 60 | MR | 1920×1080 | |
| **HFR** | **H.264** | **120 / 240 / 480** | MC | 1920×1080 | True HS + QTI AVC encoder; encoder PTS mux |
| H.265 / 10-bit / DCG / **AV1** | ≥120 | — | — | — | **Hidden** — `lacksTrueHfrUniqueFrames` |

## Not offered in picker (this device)

| Mode | Reason |
|------|--------|
| **HEVC @ ≥120 fps** (8-bit, 10-bit) | Constrained HS + `c2.qti.hevc.encoder` (Main10 surface for 8-bit HFR) delivers ~**half** the target **unique** frame rate; container fps can still look correct. |
| **4K @ 120+ HFR** | No **3840×2160** entry in camera `highSpeedVideoSizes` at those fps (encoder PP alone is insufficient). |
| **DCG @ >60** | HDR DCG session incompatible with HFR (capped at 60 in catalog). |
| **AV1 @ ≥120** | CPH2655 May 2026: probe lists only `c2.android.av1.encoder`; record at 120 targets missing `c2.qti.av1.encoder` (NAME_NOT_FOUND). AV1 **≤60** remains when advertised. |

4K **30 / 60** (non-HFR) may still appear when the encoder probe lists them.

## HFR honesty policy (May 2026)

1. **No frame duplication in mux** — `MediaCodecVideoRecorder` muxes encoder surface PTS (no synthetic `frameIndex × (1/fps)` duplication).
2. **Catalog filter** — `VideoRecordingController.lacksTrueHfrUniqueFrames` drops **all HEVC-family** rows at **≥120 fps**; **H.264** remains when HS table + `pickHighSpeedVideoTarget` match.
3. **Encoder WxH = HS capture size** — avoids frozen first frame when chrome pref ≠ HS tier.
4. **Gate** — `pns_mediacodec_hfr_verify.ps1 -GateProfile vf` still exercises HEVC HFR for regression; **user-facing picker** only lists honest H.264 HFR on this fleet.

## Code pointers

- `VideoRecordingController.lacksTrueHfrUniqueFrames`
- `InAppVideoFormatSelection.filterCatalogToCaptureCapabilities`
- `VideoFormatPresets.getAvailableFormats`
- `MediaCodecVideoRecorder` mux PTS (`muxVideoPresentationUs`)
- `scripts/pns_mediacodec_hfr_verify.ps1` (`-GateProfile vf`)
