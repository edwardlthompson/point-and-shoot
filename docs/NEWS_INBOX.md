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

- [x] Bump Compose BOM 2026.09.00 to 2026.11.01 (Compose 1.13 / Material3 1.5.0) and fix Slider/RangeSlider call sites — Material3 1.5.0 makes `onValueChange` required on Slider/RangeSlider (3 files here call Slider/RangeSlider) and the BOM requires Kotlin 2.2+ and compileSdk 37.1, so a Dependabot/Argus BOM bump will fail unless AGP accepts 37.1 and the call sites are fixed first. Label: `[AGENT]` · Priority: P2 · https://developer.android.com/blog/posts/jetpack-compose-november-2026-release (synced to BUILD_PLAN)
- [x] Upgrade CameraX 1.6.1 to 1.7.0 and migrate Camera2Interop V1 / getExtensionEnabledCameraSelector to V2 / ExtensionSessionConfig, then evaluate physical-lens binding and AE/AWB lock with continuous AF for the pro modes — the app mixes Camera2 and CameraX, so V1 deprecations will warn, and the new 3A and physical-camera APIs map onto its A/M/H/S dial and lens switching. Label: `[AGENT]` · Priority: P2 · https://developer.android.com/jetpack/androidx/releases/camera (synced to BUILD_PLAN)

## 2026-11-20

**News and updates**

- Gradle 9.9.2 and 8.14.8 (Nov 19) fix four High-severity build-tool bugs: SFTP repositories didn't verify host keys, transitive snapshot dependencies skipped dependency verification, TestKit used a predictable temp directory, and the daemon registry was briefly readable by other local users. 9.9.2 also fixes a 9.9.0 plugin-resolution regression with registries that answer 401. Wrappers older than 9.9.1/8.14.7 also miss the earlier Critical fix (version-catalog values run as code). [9.9.2 notes](https://docs.gradle.org/9.9.2/release-notes.html) · [8.14.8 notes](https://docs.gradle.org/8.14.8/release-notes.html)
- Android developer verification goes global from January 2027 and will cover all install sources on certified devices: it starts with Play and six other distributors, then expands country by country over several months. Developers who ship only outside Play are told to create an Android Developer Console account and register package names plus signing-key SHA-256 fingerprints (a new open-source guide covers re-signing repos such as F-Droid). Unregistered apps stay installable only through a one-time "advanced flow" on each device; hobbyist accounts now allow 50 devices. F-Droid publicly opposes the scheme. [Android blog](https://developer.android.com/blog/posts/android-developer-verification-2027) · [open-source registration guide](https://developer.android.com/developer-verification/guides/open-source-app-registration) · [F-Droid open letter](https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html)

**Tasks for the planning agent**

- [x] Bump the Gradle wrapper 9.5.1 to 9.9.2 and commit the regenerated wrapper jar and distributionSha256Sum — the patch fixes four High build-tool vulnerabilities (SFTP host keys, snapshot dependency verification, TestKit temp dir, daemon registry). It also picks up the 9.3 Critical version-catalog code-execution fix this wrapper lacks. Run ./gradlew wrapper --gradle-version=<ver> in the wrapper (gradle/wrapper/). Label: `[AGENT]` · Priority: P2 · https://docs.gradle.org/9.9.2/release-notes.html (synced to BUILD_PLAN)
- [x] Decide whether to verify your identity and register this app with Google before January 2027 — if yes, create an Android Developer Console account and register the package name(s) with the SHA-256 of the GitHub Releases signing key (plus F-Droid's key if F-Droid re-signs it); if no, add an "advanced flow" install note to the README. The app is sideloaded (GitHub Releases/Obtainium/F-Droid), exactly the installs the rollout gates, and this is a personal identity/policy call. Label: `[HUMAN]` · Priority: P2 · https://developer.android.com/blog/posts/android-developer-verification-2027 (synced to BUILD_PLAN)

## 2026-11-25

**News and updates**

- RawLens 2.4.20 (Nov 21) adds LK-R HDR highlight reconstruction (rebuild a clipped channel from unclipped neighbors so bright clouds keep shape), and a lighter viewfinder. Useful reference for point-and-shoot's pro exposure tools; this repo still has no /build queue. [2.4.20](https://github.com/matthew777777/RawLens/releases/tag/2.4.20)

**Tasks for the planning agent**

- - [ ] Read RawLens 2.4.20's LK-R highlight-reconstruction approach (rebuild a single clipped channel from unclipped neighbors) and decide whether a similar local preview belongs in the pro exposure tools — inspiration, not a port of their code. Label: `[AGENT]` · Priority: P3 · https://github.com/matthew777777/RawLens/releases/tag/2.4.20 (not synced: no /build queue)

## 2026-11-27

**News and updates**

- RawLens [2.4.58](https://github.com/matthew777777/RawLens/releases/tag/2.4.58) (Nov 27, GPL-3.0) adds a Vulkan DCT denoiser that runs on Bayer data before demosaicing, a ~4× faster MFSR night-burst merge (8 × 12.5 MP in ~4 s on a Mali-G615, peak memory ~1.7 → ~1.3 GB), its own TinyDNG v3 writer (DngCreator kept as fallback in Automatic mode, black/white levels repaired before save), and AAC audio in video. For point-and-shoot the overlap is DNG writing: `TiffDngColorMatrixPatch.kt` post-patches DngCreator output because DngCreator can't set some aux-camera tags, and a writer-first design would remove that. Idea source only; copy no code.

**Tasks for the planning agent**

- [ ] Evaluate a native DNG writer path (DngCreator as fallback) instead of post-patching DngCreator output in TiffDngColorMatrixPatch, and look at a CFA-domain denoise for bursts — Owning the writer removes the aux-camera tag patching, and RawLens's numbers show a pre-demosaic GPU denoise is practical on mid-range phones. Label: `[AGENT]` · Priority: P3 · https://github.com/matthew777777/RawLens/releases/tag/2.4.58
