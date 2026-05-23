# Video format recommendations (Sprint VF)

Point & Shoot surfaces in-app video formats from **hardware MediaCodec performance-points** (`MediaCodecCapabilityProbe`) plus user chrome prefs (`PreviewChromePreferences`).

## Quick picks

| Use case | Codec | Resolution / FPS | Notes |
|----------|-------|------------------|-------|
| **Sharing / compatibility** | H.264 | 1080p @ 30–60 | MediaRecorder path; widest player support. |
| **Default quality (rear)** | H.265 8-bit | 4K @ 30 or 1080p @ 60 | Good size/quality on OP13-class devices. |
| **HFR / slow-mo** | H.264 or H.265 8-bit | 1080p @ 120/240/480 or 4K @ 120 | **MediaCodec** + **constrained high-speed** burst; see **`docs/VIDEO_MODE_MATRIX.md`**. |
| **Maximum SDR dynamic range** | DCG (HEVC HDR10) | 4K @ 30–60 | Research HUD + HAL DCG; verify with `pns_video_hdr10_metadata_verify.ps1`. |
| **10-bit SDR / grading** | H.265 10-bit | 1080p–4K @ ≤60 | Main10 + BT.2020 HLG tags; MediaCodec. |
| **Smallest files (when available)** | **AV1** | 1080p @ 60 (tier-dependent) | Requires HW `video/av01` encoder; **MediaCodec** only. |

## AV1 (VF.1)

- Shown in the format picker only when `PNS.VideoCapProbe` reports `av1=true` (e.g. `c2.qti.av1.encoder` on CPH2655).
- Recording uses `MediaCodecVideoRecorder` with `MIMETYPE_VIDEO_AV1` — not `MediaRecorder` (which has no AV1 surface on this stack).
- ADB smoke: `--ez pns_preview_video_av1 true --ei pns_preview_video_encode_w 1920 --ei pns_preview_video_encode_h 1080 --ei pns_preview_video_fps 60`

## Stabilization (VF.2)

- **OIS:** `LENS_OPTICAL_STABILIZATION_MODE` when HUD “lens OIS” is on and the lens advertises OIS.
- **EIS:** `CONTROL_VIDEO_STABILIZATION_MODE` when HUD “video stabilization (preview)” is on, not in manual sensor mode, and preview AE upper &lt; 120 fps.
- Diagnostics: `PNS.VideoEffects videoStabilization oisOn=… eisOn=…`
- ADB gate: `--ez pns_preview_video_stabilization true` with `pns_video_stabilization_test.ps1`

## Bitrate

- Base tables: `VideoFormatPresets.calculateBitrate`
- User scale: HUD **video bitrate scale** (Sprint 13V.17) applied in `VideoRecordingController`

## Verification scripts

| Script | Purpose |
|--------|---------|
| `pns_in_app_video_verify.ps1` | H.264/H.265 MediaRecorder clip |
| `pns_mediacodec_hfr_verify.ps1` | H.264/H.265 @ 60 + HFR matrix (`-GateProfile vf`) |
| `docs/VIDEO_MODE_MATRIX.md` | USB-verified mode / resolution / fps table |
| `pns_video_format_test.ps1` | Cap probe + optional AV1 clip |
| `pns_video_stabilization_test.ps1` | OIS/EIS log needles |
| `pns_video_quality_gate.ps1` | Host orchestrator (VF gate) |

## Players

- **VLC / MX Player:** H.264, H.265, AV1 (device-dependent), HDR10 DCG.
- **YouTube upload:** Prefer H.264 or H.265 8-bit SDR unless you intend HDR workflow.
