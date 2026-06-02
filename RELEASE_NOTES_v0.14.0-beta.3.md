# Point & Shoot — v0.14.0-beta.3

**Pre-release** for legacy device (`legacy SKU` / `dodge`) on LineageOS 23 (Android 16). Not a Play Store build.

## Summary

- **120 fps in-app video:** MediaCodec HFR record with a **live auxiliary-camera finder** (smooth ~30 fps preview while the record camera runs encoder-only high-speed).
- **Focal-matched finder crop:** Monitor stream is center-cropped to approximate the active focal slot (wide / tele / UW) using role-equivalent focal lengths.
- **Carry-over:** Bespoke gallery polish, tray surface restore, PO memory/thermal work, video format matrix docs — see **CHANGELOG.md**.

## Highlights

| Area | What shipped |
|------|----------------|
| **HFR video** | 1080p120 MediaCodec path; wide monitor for tele/UW record; UV crop + GLES YUV shader |
| **Finder UX** | Correct orientation/mirror; no strobing; stable second record |
| **Gallery** | BG.3 metadata/share/delete; selfie DNG/JPEG orientation fix |
| **Release** | `Point-and-Shoot_0.14.0-beta.3.apk` |

## Compatibility

- **Target device:** legacy device (`legacy SKU` class)
- **OS:** LineageOS 23 / Android 16 (API 36) — primary validation **`legacy serial`**
- **FOSS-only:** no Google Play Services

## Known limitations (beta)

- HFR finder is **not WYSIWYG** — auxiliary wide (~30 fps), not the tele/UW record sensor.
- **UW record** cannot show wider than the wide monitor (full wide frame only).
- Aux **UW / tele** DNG color in ACR may still diverge from ProShot (**H.7**).
- **Dual video** capped at **1080p30** — see **v0.14.0-beta.2** notes.

## Verification (reference USB `legacy serial`, May 2026)

- Manual: **120 fps** record ×2 (wide / tele / UW slots) — smooth finder + plausible framing
- `pns_in_app_video_verify.ps1` — 60 fps rear smoke
- `pns_mediacodec_hfr_verify.ps1` — HFR encode gate (best-effort)
- `pns_chrome_ux_gate.ps1` — chrome + tele slot

## Install

```powershell
adb install -r Point-and-Shoot_0.14.0-beta.3.apk
adb shell pm grant dev.pointandshoot android.permission.CAMERA
adb shell pm grant dev.pointandshoot android.permission.RECORD_AUDIO
adb shell am start -n dev.pointandshoot/.MainActivity --es pns_screen preview
```

Full changelog: [`CHANGELOG.md`](CHANGELOG.md).
