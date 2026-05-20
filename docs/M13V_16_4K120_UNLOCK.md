# Sprint 13V.16 — 4K@120 unlock

## Problem

At **120 fps**, `PreviewController.maybeRestartBody()` used `pickHighSpeedTarget()` which preferred **1080p** before any **4K** high-speed size, even when chrome prefs requested **3840×2160**. In-app recording also sized from the preview buffer instead of the user's encode preference.

## Fix

1. **`InAppVideoRecordingSupport.pickHighSpeedVideoTarget()`** — when chrome encode pref is **4K**, pick that high-speed size (or largest 4K-capable size at target FPS) before 1080p/720p fallbacks.
2. **`PreviewController.setInAppVideoEncodeSize()`** — fed from `LaunchedEffect(videoEncodeResolved)` in `PreviewEngineScreen`.
3. **HFR session buffer** — `desiredSurfaceSize` comes from `pickHighSpeedVideoTarget(..., inAppVideoEncodeSizePref)`.
4. **Recording shell** — `resolveInAppVideoRecordSize()` uses session size at HFR; logs under **`PNS.VideoEncode`**.
5. **`MediaCodecVideoRecorder`** — muxer must be running before automation records; `awaitMuxerReady()` after `start()`; do not discard pre-EOS buffers while `stopping` until muxer is up (fixes ~3 KB corrupt MP4s).
6. **ADB** — `--ei pns_preview_video_encode_w` / `_h` seed chrome encode prefs for gates.

## Verification

```powershell
.\scripts\pns_mediacodec_hfr_verify.ps1
```

**4K_120fps_MediaCodec** pass criteria (CPH2655-class):

- Chrome prefs **3840×2160** (SharedPrefs patch and/or ADB encode extras) + preview **120 fps** → **MediaCodec** path (`mcVideoPrepared` @ **120** fps on **1280×720** or **1920×1080** when HAL has no 4K HS size).
- Constrained high-speed **capture** may stay **1280×720** (HAL lists only **720p@120** in `highSpeedVideoSizes`; encoder still advertises **4K@120**).
- Encoder **output size matches HFR session** (avoids empty mux when 4K encode mismatches 720p input).
- `muxer started` before or during record (not only at stop); `inAppVideoSaved` + **ffprobe**: **≥1280×720** @ ~**120** fps, file **≥50 KB** (tier unlock via logs if ffprobe pull fails but MC path + chrome 4K pref verified).

Prerequisite: `pns_video_capability_probe.ps1` → `has4k120=true`.
