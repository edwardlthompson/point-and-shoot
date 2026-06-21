# Privacy policy — Point & Shoot

**Application ID:** `dev.pointandshoot`  
**Last updated:** 2026-06-12  
**Contact:** open a GitHub issue at [edwardlthompson/point-and-shoot](https://github.com/edwardlthompson/point-and-shoot/issues) (see [`SECURITY.md`](SECURITY.md) for vulnerability reporting).

Point & Shoot is a **FOSS pro camera** app. We do **not** sell personal data. This document describes what the app can access, what leaves your device, and what stays local.

## Summary

| Topic | Behavior |
|-------|----------|
| Analytics / ads | **None.** No Firebase, no Play Services analytics, no ad SDKs. |
| Crash reporting | **None** built into the app. OS-level crash dialogs are controlled by your device vendor. |
| Account sign-in | **None** required to use the camera. |
| Cloud photo backup | **Opt-in only** (Settings → cloud backup folder via Storage Access Framework). |
| EXIF privacy strip | **Opt-in** (Settings → strip identifying EXIF from new still JPEG exports). |
| Public leaderboard | **Opt-in only** (Settings → connectivity). Submits capability summaries you explicitly approve. |
| LAN / WebDAV | **Opt-in only** when you configure endpoints in Settings. |
| Face / eye HUD | **On-device** ML Kit processing; frames are not uploaded for face detection. |

## Data processed on your device

### Camera, microphone, and storage

The app captures photos and videos you trigger. Still RAW (DNG), JPEG, and in-app video files are written to **local storage** (typically `DCIM/Point & Shoot/` or paths you choose). Audio is recorded only when you start video recording with audio enabled.

Optional **Strip EXIF privacy tags** (Settings) removes GPS, device make/model, software, and capture timestamps from **JPEG** still exports on the encode lane (`PNS.Reader`). DNG post-save metadata enrichment is skipped when strip is enabled; DNG files are never rewritten with `ExifInterface.saveAttributes()` (loadability lock).

### On-device machine learning (ML Kit)

When face / eye alignment HUD features are enabled and supported on your device, the app uses **`com.google.mlkit:face-detection`** **on-device**. Detection runs locally on preview/analysis frames; we do not operate a backend that receives those frames for ML Kit.

### Diagnostics and engineering probes

Engineering screens (probe hub, latency probes, fleet matrix export) can write **local files** under app-private storage or paths you pull via `adb` during development. These are not transmitted automatically.

### Android Auto Backup

When **Auto Backup** is enabled on your device, Android may back up **allow-listed SharedPreferences** only (HUD layout, welcome flow state, connectivity toggles, etc.). See [`app/src/main/res/xml/pns_backup_rules.xml`](app/src/main/res/xml/pns_backup_rules.xml). **DCIM captures, DNG files, and imported LUTs are not included** in that allow-list.

## Data that may leave your device (your choice)

### Public parity leaderboard (opt-in)

If you enable **Contribute to public leaderboard** in Settings, the app may POST a **device capability summary** (model, Camera2 feature flags, parity sweep results — not your photos) to the ingest URL configured by the project (see [`docs/leaderboard/README.md`](docs/leaderboard/README.md)). You can leave this disabled.

### LAN HTTP transfer, WebDAV, and connectivity probes

Optional features (Sprint IP.2) send data **only to endpoints you configure** (LAN tether, WebDAV upload, webhook URLs used in engineering probes). The app does not embed hard-coded third-party upload targets for your media.

### External links

About / Settings may open **GitHub**, **Venmo** (optional donation), or release notes in your **system browser**. Those sites have their own policies.

## Permissions

| Permission | Why |
|------------|-----|
| `CAMERA` | Preview and capture. |
| `RECORD_AUDIO` | Video recording with sound. |
| `READ/WRITE_EXTERNAL_STORAGE` (API ≤32 / ≤28) | Legacy MediaStore paths on older Android versions. |
| `ACCESS_FINE/COARSE_LOCATION` | Optional EXIF location tagging when you enable it in Settings. |
| `VIBRATE` | Short haptic tick after still capture (not on video start/stop). |
| `ACCESS_NOTIFICATION_POLICY` | Optional Do Not Disturb while recording when you grant policy access. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Opt-in leaderboard, LAN transfer, WebDAV, and probe HTTP clients. |
| `NEARBY_WIFI_DEVICES` | LAN tether / companion discovery when you use those features. |

Runtime permission prompts follow the welcome flow ([`WelcomeFlowConfig`](app/src/main/java/dev/pointandshoot/WelcomeFlowConfig.kt)).

## Children's privacy

The app is a general-purpose camera tool not directed at children under 13. We do not knowingly collect personal information from children.

## Changes

Material changes to this policy will be noted in [`CHANGELOG.md`](CHANGELOG.md) and reflected in the GitHub-hosted copy of this file.

## F-Droid / offline reviewers

You can exercise core capture flows **without network permission grants** by declining optional connectivity features and not enabling leaderboard contribution. Engineering probe routes are not required for normal camera use.

See also [`NOTICE`](NOTICE) (third-party licenses) and [`LICENSE`](LICENSE) (Apache-2.0).
