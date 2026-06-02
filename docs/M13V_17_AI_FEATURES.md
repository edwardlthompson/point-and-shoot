# Sprint 13V.17 — AI features backlog (host-shipped)

Optional “AI-adjacent” features: smile-triggered still, OEM scene vendor key probe, manual encode bitrate scale. None replace the locked ReferenceCam DNG / capture pipeline.

## Smile-triggered still

- **Policy:** `SmileStillCapturePolicy` — threshold **0.85**, **4.5 s** cooldown.
- **Detection:** `MlKitFaceTrackSupport.maxSmilingProbability()` with lazy `CLASSIFICATION_MODE_ALL` detector on the preview YUV path when HUD **Smile-triggered still** is on and primary mode is photo (not recording / sweep).
- **Capture:** `PreviewController.runSmileStillIfNeeded()` → tray still capture ref (same path as shutter DNG).
- **HUD:** Settings → HUD → **Smile-triggered still** (default off).

**Device verify:** Enable toggle, smile at camera, grep `PNS.SmileStill` / `captureRawStill` once per cooldown.

## Scene classification (EVA hints)

- **Probe:** `SceneVendorHintProbe.probe()` at app start (`PnsApplication`); logs **`PNS.SceneHint`** with `sceneHintProbeComplete` and per-camera vendor key names matching `media_quality`, `scene`, `eva`, `ais`, etc.
- **HUD:** **Scene vendor hints (log)** — when on, fleet triage can grep logcat; readout chip not wired yet (keys often absent on LineageOS).
- **Cached matrix:** `SceneVendorHintProbe.cached` for future HUD readout.

## Perceptual bitrate (manual scale)

- **Setting:** `HudSettings.videoBitrateScalePercent` (**50–150%**, default **100**).
- **Apply:** `VideoRecordingController.bitrateForSize()` multiplies MediaCodec probe table bitrate.
- **HUD:** Settings → HUD → **Video bitrate scale** slider (above toggle list).

## Host gate

```powershell
.\scripts\pns_ai_features_verify.ps1 -HostOnly
```

Runs JVM tests (`SmileStillCapturePolicyTest`, `SceneVendorHintProbeTest`) + `assembleDebug`; writes `hfr-runs/ai_features_host_* /host_gate.json`.

## ADB automation (13V.17 gate)

```powershell
.\scripts\pns_ai_features_verify.ps1
```

| Extra | Purpose |
|-------|---------|
| `--ez pns_preview_smile_still true` | Enable smile-triggered still |
| `--ez pns_preview_smile_still_synthetic true` | Gate hook: one tray still after session settle (no face required) |
| `--ei pns_preview_video_bitrate_scale 125` | HUD bitrate scale **50–150** |
| `--ez pns_preview_scene_vendor_hints true` | Scene vendor hint logging toggle |

**USB pass** (`legacy serial` class): `sceneHintProbeComplete`, `videoBitrateScale=125%` **>** `100%` in `PNS.VideoController`, `smileSyntheticTrigger` + `captureRawStill ok=true`.

## Device spot-check (manual)

1. HUD → **Smile-triggered still** → smile at camera (one still per **4.5 s** cooldown).
2. `adb logcat -d -s PNS.SceneHint` after cold start.
3. HUD bitrate slider **125%** → record video → compare size vs **100%**.
