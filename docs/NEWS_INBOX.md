# News Inbox

Auto-collected inbox of news, upstream updates, security fixes, and ideas relevant to this repo, appended daily by News Bot.
A Cursor planning agent should review these items and fold the useful ones into BUILD_PLAN; entries are append-only, newest at the bottom.


## 2026-11-13

**News and updates**

- Pillow heap overflow when saving TIFF via libtiff (CVE-2026-128067, high); affects <= 12.3.0, fixed in 13.0.0. [source](https://github.com/advisories/GHSA-xcvg-w56q-r89m)
- Android Developers case study: SmileID used CameraX to handle OEM orientation quirks, aspect stretching and OOM across 18,000+ devices. [source](https://developer.android.com/blog/posts/smileid-camerax-android-identity-verification)
- Kotlin 2.5.0-RC released (fixes a reified Enum<T> inference regression); Gradle 9.9.1 / 8.14.7 patch releases. [source](https://github.com/JetBrains/kotlin/releases/tag/v2.5.0-RC)

**Tasks for the planning agent**

- [x] Pin pillow>=13.0.0 for JPEG/DNG compare scripts — The scripts import Pillow and the TIFF-save overflow affects all versions through 12.3.0. Label: `[AGENT]` · Priority: P1 · https://github.com/advisories/GHSA-xcvg-w56q-r89m (synced to BUILD_PLAN)
- [ ] Review CameraX device-quirk and OOM patterns from SmileID — Proven fixes for OEM orientation, stretching and low-RAM crashes apply to a pro camera pipeline. Label: `[AGENT]` · Priority: P3 · https://developer.android.com/blog/posts/smileid-camerax-android-identity-verification
- [ ] Smoke-test Kotlin 2.5.0-RC and bump Gradle wrapper — Catches compiler regressions before 2.5.0 stable. Label: `[AGENT]` · Priority: P3 · https://github.com/JetBrains/kotlin/releases/tag/v2.5.0-RC

## 2026-11-16

**News and updates**

- RawLens 2.2.85/2.2.86: GPL-3.0 RAW camera for Android that meters off the RAW data (ETTR auto-exposure, RAW clipping zebras, false colour and spot meter relative to sensor clip); the new releases cut merge time and memory. [source](https://github.com/matthew777777/RawLens/releases/tag/2.2.86) · [thread](https://discuss.pixls.us/t/rawlens-bringing-desktop-raw-processing-to-an-open-source-android-camera/62220)

**Tasks for the planning agent**

- [ ] Evaluate RAW-referenced ETTR and drive zebras/false colour from RAW sensor-clip levels — The app already has false colour, zebras, Ultra HDR and JPEG XL, but they read the processed preview; idea source only, since RawLens is GPL-3.0 and this repo is Apache-2.0 (copy no code). Label: `[AGENT]` · Priority: P3 · https://github.com/matthew777777/RawLens/releases/tag/2.2.86

## 2026-11-18

Catch-up for Nov 17–18 (the daily brief did not run those mornings).

**News and updates**

- RawLens 2.3.94 makes Sigmoid the default tone mapper, adds a DNG develop queue (Sigmoid/Basic/AgX), and speeds Lucas–Kanade Reloaded merges about 3× on measured mobile runs. Idea source only (GPL-3.0). [source](https://github.com/matthew777777/RawLens/releases/tag/2.3.94)

**Tasks for the planning agent**

- [ ] Evaluate Sigmoid tone mapping and a DNG develop queue as idea sources — The earlier ETTR/zebra task still stands; 2.3.94's default Sigmoid curve and batch DNG develop are separate borrowing ideas. Copy no GPL-3.0 code. Label: `[AGENT]` · Priority: P3 · https://github.com/matthew777777/RawLens/releases/tag/2.3.94

## 2026-11-19

**News and updates**

- Jetpack Compose November '26 release is stable: Compose BOM 2026.11.01 (Compose 1.13) with Material3 1.5.0. The BOM needs Kotlin 2.2+ and compileSdk 37.1. Material3 1.5.0 breaks Slider/RangeSlider source (stateless overloads deprecated, `onValueChange` now required, RangeSliderState activeRangeStart/End renamed to startValue/endValue); SecureTextField now defaults to system obfuscation, and clickables play platform click sounds by default. [source](https://developer.android.com/blog/posts/jetpack-compose-november-2026-release) · [Material3 notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- CameraX 1.7.0 is stable: Camera2Interop V2 (no opt-in) exposes OutputConfiguration/SessionConfiguration, use cases can bind to physical cameras, FocusMeteringAction gains AE/AWB lock while staying in continuous AF, plus Quality.QHD and codec/bitrate control. Camera2Interop V1 and `getExtensionEnabledCameraSelector()` are deprecated. [source](https://developer.android.com/jetpack/androidx/releases/camera)

**Tasks for the planning agent**

- [ ] Bump Compose BOM 2026.09.00 to 2026.11.01 (Compose 1.13 / Material3 1.5.0) and fix Slider/RangeSlider call sites — Material3 1.5.0 makes `onValueChange` required on Slider/RangeSlider (3 files here call Slider/RangeSlider) and the BOM requires Kotlin 2.2+ and compileSdk 37.1, so a Dependabot/Argus BOM bump will fail unless AGP accepts 37.1 and the call sites are fixed first. Label: `[AGENT]` · Priority: P2 · https://developer.android.com/blog/posts/jetpack-compose-november-2026-release (not synced: no /build queue)
- [ ] Upgrade CameraX 1.6.1 to 1.7.0 and migrate Camera2Interop V1 / getExtensionEnabledCameraSelector to V2 / ExtensionSessionConfig, then evaluate physical-lens binding and AE/AWB lock with continuous AF for the pro modes — the app mixes Camera2 and CameraX, so V1 deprecations will warn, and the new 3A and physical-camera APIs map onto its A/M/H/S dial and lens switching. Label: `[AGENT]` · Priority: P2 · https://developer.android.com/jetpack/androidx/releases/camera (not synced: no /build queue)
