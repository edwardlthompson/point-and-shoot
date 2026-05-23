# Video mode matrix (device-verified)

**Device:** OnePlus 13 (CPH2655)  
**Gate:** `scripts/pns_mediacodec_hfr_verify.ps1 -GateProfile vf -RequireFfprobeAv`  
**UI catalog:** `InAppVideoFormatSelection.loadCatalog` — HFR rows require a matching **camera** `highSpeedVideoSizes` entry (encoder-only tiers such as **4K @ 120** are **not** shown).

## Legend

| Column | Meaning |
|--------|---------|
| **Path** | `MR` = MediaRecorder (≤60 fps), `MC` = MediaCodec + constrained HS burst |
| **Container fps** | `ffprobe` `avg_frame_rate` (gate: ≥ **75%** of target for HFR) |
| **Video packets** | `ffprobe` `nb_read_packets` — catches frozen single-frame clips |
| **Duration** | `ffprobe` `format.duration` must be **&lt; 25 s** for automation clips (guards bogus ~hour timelines from raw encoder PTS) |
| **A/V** | Both video and audio streams in the MP4 |

## Shown in video format picker (1080p-focused HFR on CPH2655)

| Mode | Codec | Target | Path | Container | Notes |
|------|-------|--------|------|-----------|-------|
| Standard | H.264 | 60 | MR | 1920×1080 | |
| Standard | H.265 | 60 | MR | 1920×1080 | |
| HFR | H.264 | 120 / 240 / 480 | MC | 1920×1080 | HS + encoder aligned; linear mux PTS |
| H.265 | 120 / 240 / 480 | MC | 1920×1080 | Same |

## Not offered in picker (this device)

| Mode | Reason |
|------|--------|
| **4K @ 120** HFR | No **3840×2160** entry in camera `highSpeedVideoSizes` at 120 fps (encoder PP alone is insufficient). |
| **4K @ 240 / 480** HFR | Same — HS table has no 4K tier at those fps. |

4K **30 / 60** (non-HFR) may still appear when the encoder probe lists them.

## Pipeline fixes (May 2026)

1. **Catalog filter** — `filterCatalogToCaptureCapabilities` uses `pickHighSpeedVideoTarget`, not MediaCodec PP only.
2. **Encoder WxH = HS capture size** — avoids frozen first frame when chrome pref ≠ HS tier.
3. **Linear mux PTS** — `MediaCodecVideoRecorder` rewrites `presentationTimeUs` to `frameIndex * (1e6/fps)` so players show motion and correct duration.
4. **HS session** — preview + encoder surfaces when both are in the session list (aligned sizes).
5. **Gate** — video packet count + duration ceiling + `mcVideoFramesWritten`.

## Code pointers

- `InAppVideoFormatSelection.loadCatalog` / `filterCatalogToCaptureCapabilities`
- `MediaCodecVideoRecorder` mux PTS rewrite
- `PreviewEngineScreen.hfrSessionOutputSurfaces`
- `scripts/pns_mediacodec_hfr_verify.ps1` (`-GateProfile vf`)
