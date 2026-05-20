# Point & Shoot — v0.13.0-beta.1

**Pre-release** for OnePlus 13 (`CPH2655` / `dodge`) on LineageOS 23 (Android 16). Not a Play Store build.

## Summary

- **Fleet RAW (M13):** ProShot-style DNG on OP13 leaf cameras, ZSL/HDR still modes, DCG HDR10 video, MCRAW-class RAW video lane, fleet profiles, and automated USB gates on reference hardware.
- **Video expansion (M13V):** HFR MediaCodec path, 4K@120 encode tier, format picker, macro dial, recording overlays, GLES video LUT, power/thermal + storage HUD, AI-adjacent toggles (smile still, scene probe, bitrate scale), CameraX extension probe.
- **Human sign-off still open:** ACR 3/3 and visual aux color vs ProShot remain under **Milestone H.7** — do not treat aux UW/tele color as final.

## Highlights

| Area | What shipped |
|------|----------------|
| **Stills** | Framework `DngCreator` DNG; Standard / ZSL / HDR modes; bracket bursts; openability gates |
| **Video** | H.264/HEVC MP4; HFR @ 120 fps (720p HS capture on OP13); 10-bit + DCG; manual bitrate scale 50–150% |
| **RAW video** | `.mcraw` (`PNMRAWV1`) preview-session lane on OP13 leaf cameras |
| **Preview** | Locked portrait chrome; dodge tele **73 / 85 / 150 mm**; focus peaking; RGB histogram |
| **Optional AI** | Smile-triggered still (HUD); vendor scene key probe; CameraX Night/Bokeh dial when extensions exist |

## Compatibility

- **Target device:** OnePlus 13 (`CPH2655` class)
- **OS:** LineageOS 23 / Android 16 (API 36) — primary validation device `8bf09993`
- **ABIs in APK:** `arm64-v8a`, `x86_64` (emulator)
- **FOSS-only:** no Google Play Services

## Known limitations (beta)

- Aux **ultra-wide / tele** DNG color may look wrong in ACR vs ProShot (HAL ColorMatrix2) — documented, not gate-blocking for this beta.
- **4K@120:** encoder advertises 4K@120; HAL high-speed capture may remain **1280×720@120** on OP13.
- **Smile still:** requires HUD enable; ML Kit path needs a visible face — use synthetic ADB hook only for automation.
- **LineageOS:** CameraX OEM extensions typically absent (`PROBE_OK_NO_EXTENSIONS`); Night/Bokeh dial hidden.

## Verification (reference USB `8bf09993`, May 2026)

- `pns_capture_pipeline_verify.ps1` — RAW still regression
- `pns_mediacodec_hfr_verify.ps1` — **7/7** HFR cases
- `pns_ai_features_verify.ps1` — **USB_PASS**
- `pns_camerax_extension_probe.ps1` — **PROBE_OK_NO_EXTENSIONS**
- `pns_raw_video_verify.ps1`, `pns_video_hdr10_metadata_verify.ps1` — encoded / RAW video lanes

## Install

```powershell
adb install -r Point-and-Shoot_0.13.0-beta.1.apk
adb shell pm grant dev.pointandshoot android.permission.CAMERA
adb shell am start -n dev.pointandshoot/.MainActivity --es pns_screen preview
```

Obtainium: add `https://github.com/edwardlthompson/point-and-shoot` and enable **Include prereleases**.

## Full changelog

See [`CHANGELOG.md`](CHANGELOG.md) section **[0.13.0-beta.1]**.
