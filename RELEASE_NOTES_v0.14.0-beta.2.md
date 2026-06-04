# Point & Shoot — v0.14.0-beta.2

**Pre-release** for legacy device (`legacy SKU` / `dodge`) on LineageOS 23 (Android 16). Not a Play Store build.

## Summary

- **Milestone 14:** Preview readout + video format chip, top status-bar HUD (timecode/audio), orange mode-dial sections, in-preview **QR scan**, ISO/SS readout coupling, focus mode picker, selfie ring + smile-under-Eye-AF, DND restore, heritage/donation About sheet, **stacked dual video** (one MP4), release APK packaging scripts.
- **Carry-over from M13 / M13V:** Fleet RAW DNG, HFR MediaCodec, DCG/HDR10, RAW `.mcraw` lane — see **v0.13.0-beta.1** notes.
- **Human sign-off still open:** ACR 3/3 and aux color (**H.7**); glass overlay alignment and subjective dual-video / HEVC color (**H.8**).

## Highlights

| Area | What shipped |
|------|----------------|
| **Preview UX** | Video readout + format picker; status-bar timer/meters; Photo/Video program sections in mode menu |
| **Photo** | QR mode (confirm-then-open); readout ISO bands + AE chase parity for JPEG/DNG |
| **Video** | 8-bit HEVC BT.709 VUI @ HFR; tray video-format FAB; **Dual** program — stacked rear+front → one **1080p30** MP4 |
| **Settings** | Heritage credits + LG nod + Venmo support link; About opens in-preview (scroll fix) |
| **Release** | `pns_release_packaging.ps1` → `Point-and-Shoot_0.14.0-beta.2.apk` |

## Compatibility

- **Target device:** legacy device (`legacy SKU` class)
- **OS:** LineageOS 23 / Android 16 (API 36) — primary validation **`legacy serial`**
- **FOSS-only:** no Google Play Services

## Known limitations (beta)

- Aux **UW / tele** DNG color in ACR may still diverge from ReferenceApp (**H.7**).
- **Dual video:** v1 capped at **1080p30**; front pass has no LUT/peaking — validate framing under **H.8.2**.
- **DND restore** automation needs notification policy access on some devices.
- **AI bitrate scale** gate may fail 125% needle while smile/scene pass.

## Verification (reference USB `legacy serial`, May 2026)

- `pns_capture_pipeline_verify.ps1` — RAW still
- `pns_chrome_ux_gate.ps1` — chrome + tele slot
- `pns_in_app_video_verify.ps1` — rear video smoke
- `pns_dual_video_verify.ps1 -RecordSec 5` — stacked dual + saved MP4
- `pns_about_links_verify.ps1` — About overlay
- `pns_qr_scan_verify.ps1` — QR photo mode

## Install

```powershell
adb install -r Point-and-Shoot_0.14.0-beta.2.apk
adb shell pm grant dev.pointandshoot android.permission.CAMERA
adb shell am start -n dev.pointandshoot/.MainActivity --es pns_screen preview
```

Obtainium: add `https://github.com/edwardlthompson/point-and-shoot` and enable **Include prereleases**.

## Full changelog

See [`CHANGELOG.md`](CHANGELOG.md) section **[0.14.0-beta.2]**.
