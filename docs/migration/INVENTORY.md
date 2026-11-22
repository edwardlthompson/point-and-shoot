# Phase 0 — Full inventory + trajectory analysis

**Product:** Point & Shoot (`dev.pointandshoot`) — Edward’s FOSS pro camera for Android (Kotlin / Camera2).  
**This document:** Phase 0 of a locked multiplatform migration process. **Documentation only** for the inventory itself. Edward’s answers (2026-11-16) are recorded under **Decisions** below. Later phases live beside this file: [`DESIGN_BRIEF.md`](DESIGN_BRIEF.md), [`TECH_DECISION.md`](TECH_DECISION.md), [`PARITY_CHECKLIST.md`](PARITY_CHECKLIST.md). No app code was changed in this PR.

**Tree inventoried:** `main` at `a3ab663` (2026-11-16). Shipped product version **0.14.2** (`versionCode` **22019**).  
**Build on this agent:** **not run.** OpenJDK 21 is present; Android SDK (`ANDROID_HOME` / `local.properties`) is not. Device USB verification was **not** run.

**Scope rule:** Every shipped surface is listed even when a later platform cannot honestly host it. Nothing is “simplified away.”

**Platform-behavior tags** (Edward’s standing rule — no fragile hacks):

| Tag | Meaning |
|-----|---------|
| **Durable** | Public, supported API on that platform can implement the *same user-visible contract*. |
| **Fallback** | Honest platform-appropriate alternative. The user is told it is not the Android behavior. |
| **Omit** | No honest equivalent without OEM/private APIs, root, or a hack that would break. Hide it (same policy as `FleetUiVisibilityGate`: unavailable → hidden, not greyed-out theater). |

Tags below are **iOS** (AVFoundation / PhotoKit / UIKit-or-equivalent) and **Desktop** (Windows / macOS / Linux as a class). Desktop is **not** assumed to be “the phone finder on a monitor.” See §1.18.

---

## Summary

Point & Shoot is a **Camera2-first Android camera** with a **locked portrait chrome** (3:4 finder, 7×3 grid, charcoal/orange), **DNG-first stills** that must open in Lightroom/ACR, in-app **H.264/HEVC/AV1/VP9/HDR10/RAW `.mcraw` video**, a **gallery library desk**, **Wear OS remote**, **USB webcam / HDMI / MJPEG / LAN tether**, and a large **engineering hub + fleet capability matrix**. Production preview is **not CameraX**; CameraX is used for OEM extensions (Night/Bokeh), QR analysis, and probes.

**Counts (this inventory):**

| Class | Count |
|-------|------:|
| Consumer-facing full screens / routes | **12** |
| In-preview modal sheets / pickers / dialogs | **29** |
| Finder overlays / HUD layers | **19** |
| Engineering / probe screens | **22** |
| Wear screens | **1** (`WearRemoteActivity`) |
| Extra Android entry surfaces (intents, widget, QS tiles, share) | **10** |
| Command-dial modes | **11** |
| Named capture / chrome / gallery / connectivity features | **140+** (catalog registry is ~200 rows including codec×tier expansion) |
| Persisted preference stores | **20+** SharedPreferences XML names |
| Open GitHub issues | **0** (tracker exists; backlog lives in `BUILD_PLAN.md`) |

**Top iOS constraints (do not hack around):**

1. **No Camera2.** Logical multi-camera, `physicalCameraTotalResults`, RAW12/RAW10 packing, Qualcomm session vendor keys, `SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION`, Dual Conversion Gain session params, and the locked DNG pairing rules have **no 1:1 AVFoundation map**.
2. **ProRAW ≠ this app’s DNG.** Apple ProRAW is a supported Apple RAW path. Edward (2026-11-16): **ProRAW is ACCEPTABLE** on iPhone — iOS RAW does **not** need Camera2 DNG strip layout. Android DNG loadability locks stay Android-only.
3. **Per-lens access is partial.** `AVCaptureDevice.DiscoverySession` + virtual devices can switch UW/wide/tele on many iPhones (**Durable** for native primes). Dodge-style **73 / 85 / 150 mm on one mid-tele with `SCALER_CROP_REGION`** is **Fallback** (digital crop in the capture pipeline) or **Omit** if the crop must match Android HAL crop tags in DNG.
4. **iPhone cannot be a USB UVC gadget**, cannot grant Magisk/KernelSU vendor keys, cannot host Lineage `svc usb setFunctions uvc`, and cannot be a default-camera `IMAGE_CAPTURE` provider for Android apps. Those are **Omit**.
5. **Background camera + Quick Settings tiles + lockscreen `STILL_IMAGE_CAMERA_SECURE`** are Android-shaped. iOS Control Center / Shortcuts / lock-screen Camera are **Fallback**, not the same contract.

**Top desktop constraints:**

1. **Laptop/desktop webcams are not a phone HAL.** There is no multi-lens Camera2 stack, no RAW12 ImageReader, no OEM Night/Bokeh extension, no 4K120 HS session. Live “pro camera” capture on desktop is **webcam / USB / tethered phone** — not a port of `PreviewEngineScreen` session create.
2. **Tether + gallery companion already exist in the Android app** (`TetheredCaptureServer` :28765, LAN roll :28766, library desk). Desktop value is likely **those**, not a 7×3 phone chrome on a mouse.
3. **UVC from phone → desktop** is already the webcam story (Lineage DeviceAsWebcam). A desktop *app* that *is* the camera is a different product.

**Desktop-value (decided 2026-11-16 — COMPANION):** tethered phone capture console (`:28765`), MJPEG live view (`:28770`), UVC **host** (phone-as-webcam and real USB cameras; output is **not** DNG), RAW review + library desk, LAN roll/proofing/offload, settings/preset sync. **Not** a phone-camera clone and **not** pixel-identical 7×3 finder chrome on a monitor. See §1.18 and Decisions.

---

## Open questions for Edward

Answered **2026-11-16**. The numbered list is kept as the historical prompt. Binding answers are in **Decisions** immediately below. Do not re-ask these in later phases unless Edward overrides.

1. **What is desktop *for*?** → **COMPANION** (a + d + gallery + LAN + presets). Not a phone-camera clone.
2. **Is iOS a second *camera*?** → **FULL** camera app, all Android functionality iOS can do without hacks.
3. **ProRAW vs Camera2 DNG?** → **ProRAW ACCEPTABLE.** iPhone RAW need not be Camera2 DNG.
4. **Locked chrome?** → **ADAPT** to iPhone (Dynamic Island, safe areas, platform conventions). Same **information architecture / flow**, not pixel-identical.
5. **Wear OS / watchOS?** → **defaulted:** watchOS later, after iOS capture parity; Wear stays as-is.
6. **ML Kit / Vision?** → **defaulted:** iOS uses on-device Vision; Android should prefer a FOSS/flavored detector because ML Kit blocks F-Droid.
7. **Root / Qualcomm / max-res?** → **defaulted: Omit** on iOS and desktop.
8. **USB webcam gadget?** → **defaulted:** Omit on iOS; desktop is a UVC **host**.
9. **Distribution?** → **defaulted:** TestFlight/App Store; GitHub Releases + Flathub/AppImage; MSIX/installer; macOS best-effort.
10. **Planned / ProbeOnly catalog?** → **defaulted:** Android-only future work, not migration parity.
11. **DCIM folder split?** → **BUG.** Single tree **`DCIM/PointAndShoot`** (no spaces). Separate Android fix PR from `main`.
12. **Settings export as preset format?** → **defaulted: yes** (`SettingsExportBundle`).
13. **GitHub Issues vs BUILD_PLAN?** → **defaulted:** keep `BUILD_PLAN.md`.
14. **News-inbox P3?** → **defaulted: out of scope.**
15. **Parity debt vs freeze?** → **defaulted:** freeze Android feature set at migration start; debt ledger stays Android work.
16. **Capture journal UI?** → **PARTIAL** on all platforms (with Q17). Do not implement in migration.
17. **Motion trip / Astro / Document?** → **PARTIAL** on all platforms, Android included. Do not implement in migration.
18. **Dual-video PiP overlay?** → **defaulted: out of migration parity.** Stacked dual-video v1 remains in inventory.

---

## Decisions (Edward, 2026-11-16)

Binding product decisions for Phases 1–3. Sources: Edward’s Phase 0 answers. Items marked **defaulted, Edward may override** were not independently re-litigated; they follow the defaults he supplied for remaining questions.

| ID | Decision | Status |
|----|----------|--------|
| Q1 | **Desktop = COMPANION**, not a phone-camera clone. In scope: **(a)** tethered phone capture console (existing HTTP `:28765`, MJPEG live view `:28770`); **(d)** USB webcam / connected-camera capture as a **UVC host** (phone-as-webcam **and** real USB/connected cameras; webcam output is **not** DNG); plus RAW review + library desk; LAN roll / proofing / offload; settings / preset sync. Out of scope as the desktop *primary* UI: portrait 7×3 finder chrome, volume shutter as a product, QS tiles, root vendor keys, NightScape HAL, USB-gadget webcam *from* the desktop machine. | **stated** |
| Q2 | **iOS = FULL camera app** with **all** the same functionality as Android, as far as iOS allows **without hacks**. Anything iOS truly cannot do is listed in a Fallback/Omit table — **no silent drops**. | **stated** |
| Q3 | **Apple ProRAW is ACCEPTABLE** on iPhone. iOS RAW does **not** need to be DNG in Android’s Camera2 strip layout. Android DNG loadability / pairing locks stay Android. | **stated** |
| Q4 | **Design: ADAPT** to iPhone (Dynamic Island, safe areas, platform conventions) while keeping the same **general design flow / information architecture** of the locked chrome (charcoal/orange, 3:4 finder, 7×3 grid *flow*). **Not** pixel-identical. | **stated** |
| Q16+Q17 | Unfinished systems — **motion trip**, **Astro** and **Document** recipes, **capture journal UI** — are **PARTIAL on every platform, Android included**. **Do not implement them as part of migration.** Concert / Museum / Airplane recipes that *are* wired stay in the inventory. | **stated** |
| Q11 | `DCIM/Point & Shoot` vs `DCIM/PointAndShoot` is a **BUG**. Standardize on **`DCIM/PointAndShoot`** (no spaces). A separate fix PR from `main` is being opened. **Migration docs assume that single tree.** PhotoKit / desktop folders use the same unspaced product name. | **stated** |
| Q5 | watchOS remote = **later sprint after iOS capture parity**. `:wear` stays as-is for this migration. | **defaulted, Edward may override** |
| Q6 | iOS face/eye uses **Apple Vision, on-device, no cloud**. ML Kit is also a **F-Droid blocker** on Android — prefer a FOSS/flavored detector there (does not unblock F-Droid in this PR). | **defaulted, Edward may override** |
| Q7 | Root / Qualcomm vendor keys / CPH2583 max-res unlock = **Omit** on iOS and desktop. | **defaulted, Edward may override** |
| Q8 | USB **gadget** webcam = **Omit** on iOS. Desktop is a UVC **HOST**. | **defaulted, Edward may override** |
| Q9 | Distribution: iOS **TestFlight / App Store**; Linux **GitHub Releases + Flathub / AppImage**; Windows **MSIX / installer**; **macOS best-effort**. Android GitHub Releases / Obtainium / F-Droid unchanged. | **defaulted, Edward may override** |
| Q10 | Catalog **Planned / ProbeOnly / NotApplicable** rows stay **Android-only future work**, not migration parity. | **defaulted, Edward may override** |
| Q12 | **`SettingsExportBundle` is the cross-device preset format.** | **defaulted, Edward may override** |
| Q13 | Keep **`BUILD_PLAN.md` as the backlog** (GitHub Issues unused). | **defaulted, Edward may override** |
| Q14 | News-inbox P3 ideas are **out of migration scope**. | **defaulted, Edward may override** |
| Q15 | **Freeze the Android feature set at migration start.** The fleet parity-debt ledger stays **Android work**, not a migration sprint. | **defaulted, Edward may override** |
| Q18 | Dual-video **PiP overlay** is **out of migration parity**. Shipped stacked dual-video v1 remains an inventory item for iOS if AVFoundation multi-cam can do it without hacks (see design brief). | **defaulted, Edward may override** |

**PARTIAL (all platforms, not a migration deliverable):** motion trip shutter hook; Astro recipe; Document recipe; capture journal consumer UI.

**Also not migration parity (defaulted):** watchOS remote; Planned/ProbeOnly catalog rows; news-inbox ideas; fleet parity-debt closure; dual-video PiP overlay.

---

# 1. Current State

## 1.1 Product snapshot

| Item | Value | Path |
|------|-------|------|
| Name | Point & Shoot | `README.md`, `app/src/main/AndroidManifest.xml` `android:label` |
| Package | `dev.pointandshoot` | `app/build.gradle.kts` |
| License | Apache-2.0 (not MIT) | `LICENSE`, ADR-0005 |
| Pitch | Predictable controls, honest hardware, files that open where you edit; no Play Services; no proprietary blobs | `README.md` |
| Privacy | No analytics; on-device face work; network opt-in | `PRIVACY.md` |
| Primary USB device | OnePlus 12 **CPH2583** | `AGENTS.md`, `BUILD_PLAN.md` |
| Legacy / dodge reference SKU | Optional regression lane; tele **73 / 85 / 150** on mid-tele | `DODGE_PROFILE.md`, ADR-0003 |
| minSdk / targetSdk / compileSdk | 28 / 36 / 37 | `app/build.gradle.kts` |
| NDK | 26.3.11579264 | `app/build.gradle.kts` |
| ABIs | `arm64-v8a`, `x86_64` | `app/build.gradle.kts` |
| versionName / versionCode | 0.14.2 / 22019 | `app/build.gradle.kts`, `CHANGELOG.md` |

**iOS:** Durable as “a camera app with a Photos library,” not as this HAL.  
**Desktop:** Durable as “a media/tether companion,” not as this HAL.

## 1.2 Stack, modules, build, CI, distribution

### Runtime stack

| Layer | What it is | Paths |
|-------|------------|-------|
| UI | Jetpack Compose + Material 3; **no app-wide ViewModel/Hilt** (ADR-0001) | `app/src/main/java/dev/pointandshoot/*Screen*.kt` |
| Preview | Camera2 `CameraDevice` / `CameraCaptureSession` + GLES `GLSurfaceView` (`LutCameraPreviewRenderer`) | `PreviewEngineScreen.kt`, `LutCameraPreviewRenderer.kt`, `app/src/main/assets/shaders/` |
| Stills | Camera2 RAW `ImageReader` → `DngCreator` → `Dng12Saver`; hardware JPEG; AVIF/JXL/HEIC/TIFF muxers | `modules/pns-capture/`, `Dng12Saver.kt`, `AvifStillMuxer.kt` |
| Video | `MediaRecorder` (H.264 ≤60) + `MediaCodec` (HEVC/HFR/8K/HDR10); RAW `.mcraw` | `MediaCodecVideoRecorder.kt`, `VideoRecordingController.kt` |
| Face / eye | Camera2 faces + **ML Kit face-detection** (ADR-0006 FOSS exception) | `MlKitFaceTrackSupport.kt` |
| QR | ZXing on YUV (CameraX ImageAnalysis on dedicated QR route) | `QrScanScreen.kt`, `QrCodeAnalyzer` |
| CameraX | **Extensions + probes only** (Night/Bokeh dial, extension smoke, QR) — not the live finder | `docs/M13V_18_CAMERAX_EXTENSIONS.md`, `libs.versions.toml` `cameraXExtensions` |
| Native | Optional `libpns_native.so` JNI stubs; encoders degrade to JPEG if missing | `NativeEncoders.kt`, `native/pns_native.cpp` |
| Wear | Separate `:wear` APK, BLE GATT + LAN HTTP, **no Play Services** | `wear/` |

**Libraries (version catalog):** Compose BOM `2026.09.00`, Kotlin `2.4.0`, AGP `9.1.1`, core-ktx `1.19.1`, CameraX `1.6.1`, ZXing `3.5.3`, ExifInterface `1.4.0`, ML Kit face-detection `16.1.7`, Paparazzi, Detekt, Kover. See `gradle/libs.versions.toml`.

**Gradle modules:** `:app` `:wear` `:baselineprofile` `:pns-core` `:pns-fleet` `:pns-capture` `:pns-preview` (`settings.gradle.kts`, ADR-0009). Capture session glue and hub UI stay in `:app`.

### Build / quality

| Gate | Path |
|------|------|
| Wrapper | `gradlew` / `scripts/pns_gradlew.ps1` |
| Detekt | `config/detekt/detekt.yml`, `:app:detekt` |
| Lint | `app/lint-baseline.xml` |
| Unit tests | `app/src/test`, module `src/test` — **no `androidTest/`** (ADR-0007) |
| Kover | 40% line floor on scoped helpers |
| USB truth | `scripts/pns_photo_capture_verify.ps1`, `pns_chrome_ux_gate.ps1`, many others in `AGENTS.md` |
| Changelog | `CHANGELOG.md` + `scripts/changelog_coverage.v1.json` |

### CI (`.github/workflows/`)

`toolchain-verify.yml`, `security-scan.yml`, `codeql.yml` / `codeql-analysis.yml`, `dependency-review.yml`, `scorecard.yml` (informational until HUMAN enables), `build-signed.yml`, `sbom-monthly.yml`, `leaderboard-pages.yml`, `leaderboard-submission.yml`, `plan-doc-verify.yml`, `conventional-pr-title.yml`, Argus stubs.

### Distribution

| Channel | Status | Path |
|---------|--------|------|
| GitHub Releases | Primary signed APK | `scripts/pns_github_release.ps1`, `.cursor/skills/github-release/` |
| Obtainium | Documented | `README.md`, `scripts/obtainium-sources.txt` |
| In-app updates | About → Check for updates (GitHub API, SHA-256 dialog, Wi-Fi-only option) | `PnsUpdatePrefs`, `AboutScreen.kt` |
| F-Droid | Metadata in-repo; listing “next” / planned | `metadata/metadata.yml`, `metadata/en-US/` |
| Leaderboard site | GitHub Pages | `docs/leaderboard/`, ingest `https://pns-leaderboard-ingest-live.onrender.com` |
| Wear APK | Built as `:wear` | `wear/build.gradle.kts` |

**iOS:** Durable distribution is TestFlight / App Store (separate review, entitlements). Obtainium/F-Droid **Omit**. GitHub IPA sideload **Fallback**.  
**Desktop:** Durable as GitHub Releases / package managers. F-Droid **Omit**.

---

## 1.3 Screens, routes, dialogs, sheets, overlays

Navigation is **not** Navigation-Compose. `MainActivity` hosts `CameraCapabilitiesProbe`, which switches on `pns_screen` extras and in-app callbacks (`LaunchScreenPolicy.kt`, `CameraCapabilitiesProbe.kt`). Home-icon launches strip sticky `pns_screen`; ADB `am start` keeps it.

### 1.3.1 Consumer-facing screens

| # | Screen | How reached | File | What it does |
|---|--------|-------------|------|----------------|
| C1 | **Welcome / permissions** | First launch until complete | `WelcomePermissionsScreen.kt`, `WelcomeFlowConfig.kt` | Runtime steps: CAMERA (required), RECORD_AUDIO, legacy storage, location (optional). **Also two informational cards** (not `uses-permission` dialogs): Vibration (manifest-only haptic) and optional Do Not Disturb policy. Replay via hub “Reset permission welcome” |
| C2 | **Live preview (engine)** | Default after welcome; `pns_screen=preview`; deep link `pointandshoot://preview\|camera\|video`; system camera intents | `PreviewEngineScreen.kt` (~23k lines) | Entire shooting chrome + Camera2 session |
| C3 | **In-app gallery** | Tray thumb; `pns_preview_open_gallery`; deep link `…://gallery` | `BespokeGalleryScreen.kt` | Grid + 3:4 pager (`ContentScale.Fit`), pinch/pan, **double-tap zoom**, EXIF/specs, video inline player, batch share, delete + 30 s undo (`GalleryTrash`), compare (`GalleryComparePane`), “What this file is”, system-gallery escape hatch |
| C4 | **Library desk** | Gallery overflow → `Dialog` | `PnsLibraryDeskScreen.kt` | **Days** contact sheets; **Cull** reject/keep/hero; **Library** rating 0–5, keywords, collections, DNG XMP (`DngXmpSidecar`); **Travel** GPS day buckets + Syncthing hint; **Finish** trim / frame extract / LUT bake; **Offload** SAF export, vault copy keepers (`GalleryVault`), Immich `POST /api/assets` or WebDAV (`GalleryPublish`, HTTPS-only, ≤12); **Privacy** redact, SHA-256, bug pack |
| C5 | **About & heritage** | Settings → About; `pns_screen=about`; `pns_preview_show_about` | `AboutScreen.kt` | Credits, Venmo, updates, NOTICE/LICENSE, JPEG artist/copyright |
| C6 | **Calibrate** | Settings / hub; `pns_screen=calibrate` | `CalibrateScreen.kt` | ColorChecker Classic 24 chart, auto-detect quad, apply WB/CCM |
| C7 | **Import LUT** | Hub / settings; `pns_screen=lutimport` | `LutImporterScreen.kt` | Import `.cube`; listed in LUT picker |
| C8 | **QR scan (dedicated)** | Hub; `pns_screen=qrscan`; also dial QR on preview | `QrScanScreen.kt` | CameraX YUV + ZXing |
| C9 | **HUD settings (standalone)** | Hub; `pns_screen=hudsettings` | `HudSettingsScreen.kt` | Same HUD flags as in-preview HUD sheet |
| C10 | **Share receive** | `ACTION_SEND` / `SEND_MULTIPLE` | `ShareReceiveActivity.kt` | Ingress of image/video shares |
| C11 | **Mock preview** | `pns_screen=mock` (aliases `prohud`, `glpreview`) | `UnifiedMockPreviewScreen.kt` | GLES test pattern + Pro HUD, **no Camera2** (ADR-0008) |
| C12 | **Wear remote** | Wear APK | `wear/.../WearRemoteActivity.kt` | Remote / Timer switch, countdown, BLE/LAN |

**iOS:** C1–C8 Durable as screens (permissions via Info.plist). C10 Fallback (share extension). C11 Durable (offline mock). C12 Fallback (watchOS) or Omit if no watch app.  
**Desktop:** C2 finder **Fallback** (windowed; see §1.18). C3–C7 Durable as companion UI. C1 permissions Fallback (OS camera/mic prompts). C12 Omit unless a desktop remote is in scope.

### 1.3.2 `pns_screen` route values

From `CameraCapabilitiesProbe.kt`:

| Extra value | Screen |
|-------------|--------|
| `preview` | Preview engine |
| `probehub` | Engineering hub (`DebugMenuScreen`) |
| `facemeter` | Face / eye / metering probe |
| `hardwarekeyprobe` | Hardware key probe |
| `cameraextsmoke` | CameraX extension smoke |
| `extensionhandoff` | HDR extension handoff spike (ADR-0010) |
| `mock` / `prohud` / `glpreview` | Unified mock |
| `qrscan` | QR |
| `enc` | HFR encoder probe |
| `deepcaps` | Deep capabilities |
| `sessionmatrix` | Session configuration matrix |
| `hdrdcg` | HDR/DCG runtime |
| `capturelatency` | Capture latency |
| `rawhdrexcl` | RAW vs HDR exclusivity |
| `burst` | Burst probe |
| `logicalphysical` | Logical vs physical |
| `exhaustive` | Exhaustive encoder/media matrix |
| `camera1` | Legacy Camera1 probe |
| `about` | About |
| `hudsettings` | HUD settings |
| `calibrate` | Calibrate |
| `lutimport` | LUT import |
| `native` | Native diagnostics |
| `rootsettings` | Root-only enhancements |
| `diagnostics` | **Not mapped** in `CameraCapabilitiesProbe.kt`. `pns_native_encoder_verify.ps1` passes this extra; it falls through to hub root. Do not treat as a real screen |
| (Dodge mapping) | Via hub “Dodge lens mapping” → `DodgeMappingScreen.kt` |
| (Fleet matrix) | `FleetMatrixHubScreen.kt` |

### 1.3.3 Engineering hub entries (`DebugMenuScreen.kt`)

Long-press **Settings** on the 7×3 grid, or Settings → Developer, or `pns_screen=probehub`.

**Live camera & preview:** Dodge lens mapping; Live preview (engine); Eye overlay calibration; QR / barcode scan; Legacy Camera1 probe.  
**Capability matrices:** Device capability matrix; Deep capabilities; Face / eye / metering probe; Session configuration matrix; HDR / dynamic range runtime; Logical vs physical; Exhaustive encoder / media matrix.  
**Performance:** HFR encoder probe; Capture latency; RAW vs HDR exclusivity; Burst probe.  
**Color:** Calibrate; Import LUT.  
**Interface previews:** Mock preview; HUD settings; About / heritage.  
**Platform & root:** Native diagnostics; Root-only enhancements; Diagnostics dump.

Plus hub search (`ProbeHubSearch.kt`), recents/favorites (`ProbeHubRecentsStore`).

**iOS / Desktop:** Engineering hub is **Durable** as a developer window if you want it; **Omit** from consumer iOS/desktop store builds unless Edward wants a hidden diagnostic.

### 1.3.4 In-preview sheets, dialogs, overlays (consumer)

All live on `PreviewEngineScreen.kt` unless noted. Style: `docs/preview-chrome-settings-style-guide.md`, `PreviewChromeMenuUi.kt`.

| # | Surface | Trigger |
|---|---------|---------|
| D1 | Settings home rail | Grid Settings (r2c6) |
| D2 | Quick settings (all toggles) | Settings → Quick settings |
| D3 | Capture & stills | Settings → Capture |
| D4 | Video & stabilization | Settings → Video |
| D5 | Target frame rate | Settings / FPS chip |
| D6 | HUD & readouts | Settings → HUD |
| D7 | Guides & framing | Overlays tile / Settings → Guides |
| D8 | Preview & behavior | Preview & keys tile / Settings → Preview |
| D9 | About & heritage (sheet) | Settings → About |
| D10 | Developer settings | Settings bottom pin |
| D11 | ISO picker (range + lock list) | Readout ISO chip |
| D12 | Shutter-speed picker | Readout SS chip |
| D13 | Aperture cycle (no sheet; tap) | Readout F chip |
| D14 | White-balance picker | Readout WB chip |
| D15 | Focus-mode picker | Readout AF chip — `PreviewFocusModePickerDialog.kt` |
| D16 | Still LUT picker | Readout Still LUT |
| D17 | Video LUT picker | Readout Video LUT |
| D18 | IMG / still format flow | **Primary:** tray still-format FAB. **Also:** long-press readout IMG chip (`PreviewReadoutStrip.onImgChipLongClick`). Sheet: `StillFormatPickerSheet.kt` (MAX → color space → sensor res → RAW → compressed) |
| D19 | Video format picker | Tray video-format FAB — `VideoFormatPickerSheet.kt` (incl. Audio step) |
| D20 | Command dial menu | Tray mode dial |
| D21 | Timer / Single / Burst | Grid Timer tile |
| D22 | Flash cycle | Grid Flash tile |
| D23 | False color / zebra menu | Overlays / QS (Off, Zebra, Exposure bands) |
| D24 | Snackbars / toasts | Capture errors, FPS cap honesty, root-only, disconnect |
| D25 | Front/rear spotlight (3 steps) | First-run `PnsUiHintsStore` |
| D26 | Self-timer countdown on finder | Timer > 0 then shutter |
| D27 | Burst status in top band | During burst |
| D28 | Settings search | Settings home search field — `ChromeSettingsSearch.kt` |
| D29 | Donate / GitHub-update launch dialogs | `PnsLaunchPrompts.kt` after a normal (non-ADB) launch; skipped when airplane-safe, recording, or other capture busy flags |

**iOS:** Durable as sheets/popovers.  
**Desktop:** Durable as windows/panels; 7×3 modal-from-grid is Fallback.

### 1.3.5 Finder overlays (HUD layers)

| Overlay | Default | Code |
|---------|---------|------|
| Luma histogram | off | `HudSettings.showHistogram` |
| RGB histogram | off | `showRgbHistogram` |
| Histogram during video record | off | `showHistogramDuringVideo` |
| Zebra / false color | off / IRE 95 | `falseColorMode`, `zebraIreThreshold` |
| Focus peaking (GL shader) | Off | `focusPeakingColor`, `lut_preview_external.frag.glsl` |
| Horizon level | **on** | accelerometer |
| Eye-AF overlay | **on** | ML Kit + Camera2 |
| Face alignment debug crosshair | off | HUD |
| Crop guide (1:1, 4:3, 3:2, 16:9, 2.39, 9:16) | off | `CompositionGuideSettings` |
| Grid (thirds, golden ratio, spiral, diagonals, 3×3) | off | same |
| Selfie ring (front) | when front | `PreviewSelfieRingIndicator` |
| Chart-corner overlay | off | calibration |
| Video pillar HUD (timecode + PPM) | **on** while recording if letterbox | M15.23 |
| Power/thermal chip | **on** | HFR/DCG |
| Storage remaining | **on** | video minutes |
| Video tally | **on** | |
| Preview status line | as needed | queue, burst fps, disconnect |
| Concurrent rear PiP inset | off / catalog `preview.pip` | `ConcurrentPipPreviewController` — **preview-only** concurrent rear inset; not the dual-video encode overlay |
| Scene vendor hint chip | HUD toggle default **off** | `HudSettings.showSceneVendorHints` + `SceneVendorHintProbe` (`PNS.SceneHint`). Probe always runs at app start. Toggle description says “logs keys”; `summaryForReadout()` is **not** wired to `PreviewReadoutStrip`. KDoc claims HUD readout feed — that surface is missing |

**iOS:** Histogram/peaking/zebras/grids **Durable** as GPU overlays (Metal). Horizon Durable (CoreMotion). Eye-AF Durable via Vision (Fallback vs ML Kit). Chart calibration Durable. Dual-cam PiP Durable on supported devices (multi-cam session limits).  
**Desktop:** Overlays Durable in a preview widget. Eye-AF Fallback (webcam face APIs) or Omit. Horizon Omit unless a phone is tethered.

---

## 1.4 Preview chrome and every control

**Locked.** Do not redesign. Canonical: `docs/preview-chrome-layout-style-guide.md`, `.cursor/rules/preview-chrome-ui-lock.mdc`.

### Portrait stack (top → bottom)

1. Top inset band = **one** status-bar+cutout (`rememberSystemInsetsDp`) + `PreviewTopStatusBar` + selfie ring.  
2. Section divider (`PreviewChromeSectionDivider`, white 22% alpha).  
3. Finder band weight **2.9f**, **3:4** tile, full width, `BottomCenter` slack toward status bar.  
4. Divider.  
5. Readout chips (`PreviewReadoutStrip`).  
6. Divider.  
7. 7×3 rail weight **1f** (`PreviewChromeGrid7x3` + focal row). Settings at **r2c6**.  
8. Bottom tray height **76.dp**: gallery thumb 48.dp, format FAB (mode-dependent), **center shutter**, Photo/Video/Webcam toggle **left of** mode dial.

Landscape (`PnsFormFactor.kt` / `PnsAdaptivePreviewChrome`): after the top inset band, a `Row` with finder `weight(1.65f)` and a right column `weight(1f)` holding readout + rail + tray. Same band *contents* as portrait; only flex geometry changes. Chrome glyphs stay upright via `DeviceUiRotation` / `chromeGlyphRotation` (tiles do not twist with the sensor). Foldable two-pane helper exists (`PnsFoldableTwoPane`: width≥600, w>h, smallest≥500) and is **not** the default preview route.

Manual focus on the finder is **horizontal drag** (`PreviewManualFocusDrag.kt`) so vertical swipes remain front/rear. Do not document it as vertical.

**iOS:** Durable if you keep the same stack in a portrait iPhone layout. Safe-area insets are Durable. Landscape split is Durable.  
**Desktop:** **Fallback** — window chrome, menu bar, and mouse density fight a 7×3 phone grid. See §1.18.

### 1.4.1 Focal row

Targets **14, 23, 35, 50, 73, 85, 150 mm** (`FocalLensStripSupport.FOCAL_CHIP_EQ_MM`). Native primes switch cameras; digital primes (35/50/85/150) use `SensorCropGeometry` / `SCALER_CROP_REGION` when **FPS < 120**. Dodge tele: **73 native, 85 Portrait85, 150 LongTele150** on **same mid-tele**; physical tele id preferred (`teleOpenablePair`). Hide slots when effective resolution &lt; 12 MP.

**iOS:** Native UW/wide/tele **Durable**. Digital 85/150 on one tele **Fallback** (zoom/crop). Exact DNG crop tags **Fallback/Omit**.  
**Desktop:** Webcam zoom **Fallback**. Multi-lens **Omit** unless a tethered phone supplies lenses.

### 1.4.2 7×3 shortcut slots (`previewChromeGridSlots`)

| Row, col | Slot | Action |
|----------|------|--------|
| r1c0 | Overlays | Sheet: histogram, peaking, false color, crop, grid |
| r1c1 | Preview & keys | Gestures, shutter, keys, brightness, DND |
| r1c2 | Capture & tools | Stills tools, front/rear |
| r1c3 | Timer | Single / Timer / Burst |
| r1c4 | Horizon | Toggle level |
| r1c5 | Eye AF | Toggle overlay (hidden if catalog says unavailable) |
| r2c0 | Video QS | Tally, OIS, EIS |
| r2c1 | DND | Toggle DND in preview |
| r2c2 | Flash | Cycle flash |
| r2c3 | *(empty / reorderable)* | Geometry stays; empty `Box` when hidden |
| r2c4 | Geotag | Toggle save location |
| r2c5 | Max brightness | Toggle |
| r2c6 | Settings | Settings rail; **long-press → engineering hub** |

Tap opens a menu (does not instant-toggle). **Long-press + drag** reorders 7×2 shortcut rows (`PreviewChromePreferences` `qs_grid_slot_order_v1`).

Unavailable features: **hidden** (empty cell), not greyed (`FleetUiVisibilityGate`). Root-only: visible in `PnsColors.RootAccentBlue`, tap → toast until SU.

**iOS:** Grid Durable; root-blue **Omit**. Reorder Durable.  
**Desktop:** Fallback (toolbar). Root **Omit**.

### 1.4.3 Readout chips

Photo: **ISO · SS · F (if HAL) · WB · AF · Still LUT · IMG**.  
Video: **ISO · SS (may show shutter angle) · F · WB · AF · Video LUT · STAB**; **no** Still LUT / IMG. Video format is the **tray FAB**, not the readout (`docs/M14_READOUT_STATUS_BAR.md`).

| Chip | Behavior | Default |
|------|----------|---------|
| ISO | Auto, single-stop lock, or range clamp `min…max`; long-press **AE lock** (amber padlock) | Auto, sensor range |
| SS | Lock shutter; video shutter-angle FREE/360/180/90/45 → `LOCKED_SS_AUTO_ISO` | Auto |
| F | Hidden if no `LENS_INFO_AVAILABLE_APERTURES`; one value read-only; 2+ tap-cycles; **per cameraId**; default **widest** | smallest f-number |
| WB | AWB / presets / manual | AWB |
| AF | Auto CAF, manual distance, HAL modes; long-press rack-focus waypoints | Auto |
| LUT | Per-mode memory, None default | None |
| IMG | Composed still matrix | Standard Pro |
| STAB | OIS / EIS / OIS+EIS / Off | OIS on, EIS off |

**Dial M is focus only.** Manual *exposure* = both ISO and SS chips locked (`ReadoutAeCoupling`).

YUV exposure chase (one axis locked): target median bin **34**, deadband 6, etc. (`ReadoutExposureChase.kt`, `docs/PNS_TECHNICAL_SETTINGS.md` §4).

**iOS:** ISO/SS/WB/AF/EV **Durable** on devices that advertise `AVCaptureDevice` manual controls (many recent iPhones). Variable aperture **Omit** (iPhone iris is not user-cyclic like Xperia PRO-I). LUT preview Durable (Metal 3D LUT). IMG format matrix Durable as export picker. AE lock Durable (`setExposureTargetBias` / locked exposure).  
**Desktop:** UVC cameras: a few expose exposure/WB (**Fallback**); most webcams are auto-only (**Omit** manual). LUT on review Durable.

### 1.4.4 Command dial (`CommandDialMode.kt`)

| Mode | Label | Behavior | Visibility |
|------|-------|----------|------------|
| Auto | A | HAL AE/AF | Always |
| M | M | Manual focus drag; ISO/SS still from chips | Always |
| H | H | Highlight protection; YUV EV darken-only; no positive EV | Catalog `hud.highlight_meter` |
| S | S | AF infinity; tap to refocus | Always |
| Monochrome | MONO | Dedicated mono sensor, not LUT | Only if mono camera detected |
| BKT | BKT | 3/5/7 AE bracket, RAW12 + GroupingID | `still.bracket` |
| Macro | MACRO | Close-focus camera auto-switch; video cap 60 + EIS+OIS | `af.macro` |
| Night | NIGHT | NightScape 4/6/8 JPEG stack → AVIF/JXL | Hidden if no extension |
| Bokeh | BOKEH | CameraX BOKEH extension | Hidden if no extension |
| Qr | QR | ZXing on YUV; **not on rotary UI** — separate entry | AlwaysShow catalog |
| Dual | DUAL | Rear+front stacked one MP4; **video only** | `video.dual` |

Fresh-install / tray default dial: **Auto** (`HudSettings` when no stored name; `PreviewTrayModeStore.commandDialMode = Auto`). ADB automation often seeds **M** (`pns_preview_dial`); do not confuse that with the user default. Per-tray dial: `PreviewTrayModeStore` `photo_dial` / `video_dial`.

**H mode:** software YUV path unless root vendor-extra opt-in (`VendorHighlightAePrefs`). Requires `desiredFps < 120` and YUV analysis. Darken-only. Face AF regions only (not AE) on H. See `docs/PNS_TECHNICAL_SETTINGS.md` §2.

**iOS:** A/M/S Durable. H **Fallback** (exposure target bias / custom metering; no Camera2 YUV histogram identical). MONO **Omit** unless a future iPhone has a mono camera. BKT Durable as bracketed `AVCapturePhoto` (not RAW12 GroupingID). Macro Durable (`AVCaptureDevice.virtualDeviceSwitchOverVideoZoomFactors` / ultra-wide). Night/Bokeh **Fallback** to AVFoundation photo quality / portrait (`builtInDualCamera` depth) — **not** CameraX extensions. QR Durable (Vision + ZXing). Dual **Fallback** (multi-cam session; thermal limits).  
**Desktop:** Dial **Fallback** as radio modes on a tethered phone. Local webcam: Auto only unless UVC CCTV-class camera.

### 1.4.5 Tray: Photo / Video / Webcam

`PreviewBottomCaptureTray`, `PreviewLastSurfacePrefs` restores last surface.

| Mode | Shutter | Notes |
|------|---------|-------|
| Photo | Composed still (RAW/DNG + tonal per IMG) | Long-press JPEG burst when burst enabled |
| Video | Start/stop record | Format FAB; chapter on volume-up |
| Webcam | Enters USB/HTTP webcam path | Lineage UVC gadget for Zoom/Teams; HTTP `/h264` `/mjpeg` is OBS-only |

**iOS:** Photo/Video Durable. Webcam gadget **Omit**. HTTP preview **Fallback** (local network, not Zoom).  
**Desktop:** Photo/Video as tether commands Durable. Desktop-as-webcam **Omit** (would be a virtual camera driver — different product). Desktop as UVC **host** Durable.

### 1.4.6 Manual / hardware controls (complete)

| Control | Where | Notes |
|---------|-------|-------|
| ISO | Readout | Auto / lock / range |
| Shutter | Readout | Auto / lock / angle in video |
| Exposure compensation | H mode + chase | Not a dedicated EV chip; H applies negative EV |
| Focus | Dial M, AF picker, finder **horizontal** drag | Vertical swipe is front/rear |
| WB | Readout | |
| Metering | H highlight; face AE on Auto; ProShot-style AE precapture on RAW | No classic matrix/spot UI beyond H and face |
| Zoom / focal | Focal row + digital crop | Not a pinch-to-arbitrary-zoom product primary (pinch may exist on gallery) |
| Lens / camera | Focal row, front/rear swipe, Capture & tools | |
| Flash | Grid + settings; strength 25–100% API 35+ | Default **Auto** |
| Timer | 0 / 3 / 5 / 10 s | Default 0 |
| Burst | Tap burst count; long-press JPEG train 17 ms fleet max | RAW burst serialized |
| FPS | Target FPS sheet; adaptive thermal/battery caps | Tray store defaults **90 photo / 60 video** (`PreviewTrayModeSettings`). Session/controller may still seed **120** before UI sync — two numbers; do not collapse them |
| OIS / EIS | Video settings + STAB chip | OIS default on; EIS default off; EIS skipped ≥120 |
| LUT | Readout + importer | Built-ins + `.cube` |
| Smile still | HUD | ML Kit smile ≥0.85, 4.5 s cooldown |
| Rack focus | Long-press AF; ▶ Rack on M | 500–3000 ms |
| Focus breathing | HUD, tele + M | Default off, k=0.005 |
| Auto-framing | HUD, API 35+ | Default off |
| HDR10 live preview | HUD | Default off |
| Post-RAW sensitivity boost | HUD | Default off |
| ZSL pre-capture ring | HUD | Default off |
| Intervalometer / time-lapse | HUD + recipes | Photo vs video `timeLapseMode`; ISO ramp `ramp_iso_s/e` (default 100→800) when `ramp_on` |
| Motion trip | `PnsProductPrefs.trip_armed` + `PnsMotionTrip.shouldFire` | **Arm toggle in product settings only.** No shutter hook in `PreviewEngineScreen.kt` — helper + unit test exist; live auto-shutter is **not wired**. Treat as incomplete, not a shipped capture path |
| Bulb / focus stack | `bulb_armed`, `focus_stack_n` default 5 | Prefs + UI labels; do not assume a full bulb/stack capture engine without a later code pass |
| Capture recipes | `PnsProductPrefs.CaptureRecipe` | **Concert / Museum:** on preview start, shutter volume 0 + flash Off. **Airplane:** `airplaneSafe()` suppresses donate/update dialogs (`PnsLaunchPrompts`). **Astro / Document:** enum + settings labels only — **no** capture-path wiring in `PreviewEngineScreen.kt` |
| Picture profiles | `ProPictureProfiles` | |
| Power profile | Performance / Balanced / Endurance | **Default Balanced** (cap 60). Performance unbounded; Endurance cap 30. Store `pns_power_profile` — **not** in Auto Backup allow-list |
| Workflow presets | street / portrait / video_log / macro_video | `pns_workflow_presets` |

**iOS:** Most listed controls Durable where AVFoundation exposes them. Smile Durable (Vision). Burst Durable. Intervalometer Durable. Vendor research keys / post-RAW boost / DCG **Omit**.  
**Desktop:** Tether can fire the phone’s controls (**Durable** as remote). Local webcam: subset Fallback.

---

## 1.5 Capture formats and processing pipeline

### 1.5.1 Still formats

Imaging profiles (`ImagingProfile.kt`):

| Profile | RAW | Tonal | Color |
|---------|-----|-------|-------|
| Standard Pro (default) | Lossless compressed DNG | 10-bit AVIF HDR | Display P3 |
| Ultra-Max | Uncompressed RAW12 DNG | 12-bit JXL | Rec. 2020 |
| JPEG only | None | 8-bit JPEG | Display P3 |

Picker also exposes HEIC, Motion Photo (`mp.jpg`), TIFF 16-bit, JPEG XL, AVIF (`TonalContainer`, `StillFormatPickerSheet`). MAX Photo: highest-CQI color space + highest bit-depth RAW/compressed pair; Rec.2020 MAX prefers TIFF 16-bit over JXL 12-bit; Max resolution may stitch tiles (`MaxPhotoTileStitch`).

RAW stream pick order **Default:** RAW12 → RAW_SENSOR → RAW10 (locked; RAW10-first breaks `DngCreator` on legacy SKU). Aux physical preview pin prefers RAW_SENSOR on logical map.

ZSL (`still.zsl`, `ZslStillFrameRing`), HDR still mode (`StillCaptureMode.HdrStill`), NightScape stack, BKT.

**DNG locks (must survive any port of the *Android* still path; iOS is not this path):**

- `DngMetadataResolver` logical+logical pairing; `allowPhysicalTotalResultPairing=false`.  
- **Never** `ExifInterface.saveAttributes()` on DNG.  
- Leaf reconcile AsShotNeutral only.  
- `PNS.CaptureStill` `dng save diag` Log.i.  
- Pure-HAL save default (`PureHalDngSavePolicy`).

**iOS:** JPEG/HEIF **Durable**. Live Photos ≈ Motion Photo **Fallback**. TIFF/AVIF/JXL **Fallback** (encode yourself). ProRAW **Fallback** (not this DNG). Night/Bokeh computational photos **Fallback** (Apple pipeline, not NightScape).  
**Desktop:** DNG **Durable as files** (review). Capture DNG from a laptop webcam **Omit**. Capture DNG from a tethered phone **Durable** (phone does the work).

### 1.5.2 Video formats

| Item | Detail |
|------|--------|
| Codecs | H.264, HEVC, AV1 (partial), VP9/WebM, RAW `.mcraw` (`PNMRAWV1`) |
| Color | SDR BT.709, HLG Main10, flat/cine, HDR10 on DCG stacks |
| Resolutions | 720p–8K where HAL+encoder prove it; 8K → MediaCodec |
| FPS | 24/30/60/120/240/480 with honesty labels (`VideoDeliveryHonesty`, `hs120_sub4k` vs `true_4k120`) |
| Dual video | **Shipped v1:** vertical stack front-top / rear-bottom, one MP4, 1080×1920 @ 30 (`DualVideoRecordingController`, `docs/M14_12_DUAL_VIDEO.md`). LG-style **draggable PiP overlay** is explicitly “may follow in a later sprint” — **stated, not shipped**. Concurrent rear PiP (`preview.pip`) is a **preview inset**, not dual-file encode |
| Multicam Melt | Partial; thermal-limited (`MulticamMeltRecordingController`) |
| Dual-ISO video | Experimental stub (`DualIsoVideoMerger`) |
| Time-lapse video | JPEG frames → H.264 30 fps |
| Anamorphic desqueeze | SAR metadata |
| Audio | Camcorder default; hi-fi 48/96 kHz AAC 256 kbps; wind NS+AEC; gain −12…+12 dB; external mic prefer; compression; ducking; spatial logged unsupported for multi-track |
| Stabilization | OIS + EIS; skipped HFR |
| Remaining time | Refuse start &lt;1 min; toast &lt;5; stop at 0 |

**iOS:** HEVC/H.264 Durable. HDR (Dolby Vision / HDR10) Durable on supported devices as **Apple’s** HDR, not DCG session keys (**Fallback**). AV1/VP9 **Fallback/Omit** per device. RAW video **Omit** (no `.mcraw` HAL). Dual Durable with multi-cam limits. Hi-fi audio Durable (48 kHz; 96 kHz device-dependent).  
**Desktop:** Encode Durable (FFmpeg/OS). Capture from webcam Durable at webcam limits. HFR 240/480 **Omit** on typical webcams. RAW video **Omit**.

### 1.5.3 Pipeline (Android)

`CAPTURE_ARCHITECTURE.md` (referenced by ADR-0001): dedicated executors — camera control, reader/encode, meter, JPEG companion.

Preview session: REGULAR `createCaptureSession`; **`streamHints = false`** (legacy SKU lock); physical pin **preview output 0 only**; RAW/JPEG unpinned.

Still: ProShot-style AE precapture while repeating; `stopRepeating`; `TEMPLATE_STILL_CAPTURE`; dual-target RAW+JPEG; face detect OFF on RAW still; `DngCreator.writeImage`; in-place TIFF patches; JPEG EXIF + optional ICC (`IccProfileBuilder`) + optional privacy strip.

Video: interleaved HS vs regular; MediaCodec before HS burst; mux PTS rules for HFR.

**iOS:** Durable as `AVCaptureSession` + photo/movie outputs — **a different pipeline**. Do not port Camera2 session code.  
**Desktop:** Durable as “send capture command to phone” or “open UVC device.” Do not port HAL session create.

---

## 1.6 Gallery / library / review

| Feature | Path |
|---------|------|
| Index | `PnsMediaStoreGallery.kt` — `DCIM/Point & Shoot`, max 500, lazy EXIF |
| Viewer | `BespokeGalleryScreen.kt` — 3:4 tile, Fit, pinch, specs card |
| Capture groups | `GalleryCaptureGroups` — DNG+JPEG, `bkt-` / `hdr-` / `nightscape` as one shot |
| Share | JPEG / both; batch `ACTION_SEND_MULTIPLE`; undo delete 30 s |
| Library desk | Days contact sheets, cull, keywords/collections, travel days |
| Finish | Trim, pull frame, bake LUT (`LogLutBakeExport`), vault copy |
| Offload | Immich / WebDAV (`NetworkStorageClient`), folder SAF export, LAN `/proofing` |
| Privacy | Redact-before-share (`GalleryRedact`), SHA-256 evidence (`GalleryEvidence`), bug pack (`PnsBugReportPack`) |
| Journal | `CaptureJournal` in-memory ring (max 50). Recorded from still save/fail in `PreviewEngineScreen.kt`. **Consumer UI is missing:** only `PnsBugReportPack` prints `latestLine()`. README / CHANGELOG claim “last saved and failed stills — no logcat required” (**stated** copy vs **inferred** gap) |
| Cloud backup | `CloudCaptureBackup` SAF folder, optional Wi-Fi-only |
| Bitmaps | `PnsBitmapGuard` recycle |

Some finish/redact/LUT paths already write `DCIM/PointAndShoot` (no spaces) (`LogLutBakeExport.kt`, `GalleryRedact.kt`, `GalleryVideoFinish.kt`). **Q11 decided:** that unspaced name is the **single** tree; spaced `Point & Shoot` is the bug to fix on Android (separate PR), not a second product folder.

**iOS:** Durable via PhotoKit (albums, live photo pairs). Keywords/collections Durable. Immich/WebDAV Durable. SHA-256 Durable. System share sheet Durable.  
**Desktop:** **High value Durable** — this is a natural desktop app (Lightroom-adjacent culling, DNG open, WebDAV). Android MediaStore queries become filesystem/PhotoKit/WinRT.

---

## 1.7 File naming and storage

`CaptureStorage.kt`:

- Folder: **`DCIM/PointAndShoot`** (stills and video). **Edward 2026-11-16:** the spaced `DCIM/Point & Shoot` tree is a **bug**; migration assumes the unspaced name. A separate Android fix PR from `main` is being opened. Historical code/docs may still say `Point & Shoot` until that PR lands.  
- Name: `pns_{yyyyMMdd'T'HHmmss'Z'}_{profileId}_{seq:04}[_{suffix}].{ext}`  
- Ext: `dng`, `jpg`, `heic`, `mp.jpg`, `tiff`, `avif`, `jxl`, `mp4`, `webm`, `mcraw`  
- `IS_PENDING` until close.  
- Diagnostics: app-private external files, not MediaStore.  
- Calibration JSON: `getExternalFilesDir()/calibration/`  
- LUT sidecars / DNG XMP: beside DNG when `writeXmpSidecar` (default true).  
- Updates cache: `cache/updates/*.apk`  
- Optional SAF record tree (`PnsProductPrefs.saf_record_tree`).  
- Syncthing layout toggle.

Still storage floor 48 MB/frame; intervalometer 2×; few-stills toast at 3×.

**iOS:** Durable naming; store in PhotoKit (and/or Files). `DCIM/` path **Fallback** (not user-visible the same way).  
**Desktop:** Durable paths under a chosen folder.

---

## 1.8 Metadata / EXIF / location

| Item | Policy | Path |
|------|--------|------|
| DNG | In-place IFD only (`TiffIfd0Software305`, `TiffExifSubIfdCapturePatch`); Software “Point & Shoot”; **no** ExifInterface rewrite | `StillCaptureMetadata.applyToDngUri` |
| DNG GPS | `DngCreator.setLocation` when geotag on | `Dng12Saver` |
| JPEG EXIF | Full apply; optional Artist/Copyright (`PnsJpegCreditPrefs`) | `applyToJpegUri` |
| JPEG ICC | APP2 after EXIF | `JpegIccEmbedder` |
| Privacy strip | Removes GPS, make/model, software, timestamps, user comment on JPEG; skips DNG apply | `JpegExifPrivacyStrip`, `privacy.exif_strip` |
| Geotag | Off / Coarse (~0.01° ≈ 1 km) / Precise | `PnsGeotagMode`, `CaptureLocationBridge` |
| Video | Geotag + DESCRIPTION with measured fps | `VideoCaptureMetadata` |
| LUT identity | DNG software tag / sidecar SHA-256 | `DngLutMetadata` |

Default geotag: **off** (`saveLocationWithMedia=false`). Share may strip GPS (`strip_gps_share` default **true**).

**iOS:** PhotoKit location + EXIF Durable. Coarse rounding Durable. DNG rewrite ban is Android-specific; iOS must not smash ProRAW either (**Durable policy**).  
**Desktop:** ExifTool-class tools Durable; do not rewrite DNG strips if the desktop app ever writes Android DNGs.

---

## 1.9 Settings and defaults

Defaults below are code defaults on a fresh install. Persistence: SharedPreferences (no DataStore — `docs/SETTINGS_PERSISTENCE_AUDIT.md`).

### 1.9.1 `PreviewChromePreferences` (`pns_preview_chrome`)

| Key | Default |
|-----|---------|
| maxBrightnessInPreview | true |
| dndWhileInPreview | true |
| dndWhileRecording | false |
| volumeKeysCapture | true |
| btRemoteShutter | false |
| hardwareCameraKeyCapture | true |
| saveLocationWithMedia | false |
| stripExifPrivacyTags | false |
| showOnScreenShutter | true |
| tapPreviewToCapture | true |
| liveChartCornerOverlay | false |
| staticPreviewRotationDeg | 90 (effective 0; see comments) |
| selfTimerDelaySec | 0 |
| stillCaptureJpegCompanion | true |
| previewFlashMode | Auto |
| inAppVideo encode WxH | 0×0 auto |
| codec/fps/color ordinals | unset |
| audioHiFiCapture | false |
| audioWindNoiseReduction | true |
| audioPreferExternalInput | true |
| shutterSoundPackKey | mechanical |
| shutterSoundVolume | 0.85 |
| shutterHapticSync | false |
| audioLightCompression | false |
| audioVoiceoverDucking | false |

### 1.9.2 `HudSettings` (`pns_hud_settings`)

See `HudSettings.kt` and `docs/PNS_TECHNICAL_SETTINGS.md` §9. Notable defaults: command dial shown; timecode/tally/FPS/ISO-SS **on**; histogram **off**; highlight meter **on**; eye-AF **on**; horizon **on**; peaking **off**; LUTs **None**; OIS **on**; EIS **off**; JPEG ISP bias **0**; companion JPEG quality **92**; still mode Standard; video lane Encoded; burst off count 5 interval 17 ms; NightScape 6 frames; flash strength 100%; video shutter FREE; color SDR; pillar HUD on; audio camcorder; wind filter off; gain 0; dual-ISO off; experimental flags off.

### 1.9.3 Other stores

| Store | Defaults / contents |
|-------|---------------------|
| `pns_composition_guides` | crop OFF, grid OFF |
| `pns_ux_settings` | theme System; photo chrome stays charcoal in all themes |
| `pns_tray_mode_settings` | per-tray FPS (**90 photo / 60 video**), OIS/EIS, angle, prime, readout locks, dial **Auto** |
| `pns_preview_last_surface` | last Photo/Video/Gallery |
| `pns_product_systems` | geotag mode; recipes (see below); ISO ramp 100–800; focus stack 5; HDMI on; MJPEG off; USB webcam off; XMP sidecar **true**; strip GPS on share **true**. **Not** in Auto Backup allow-list (`pns_backup_rules.xml`) |
| `pns_power_profile` | **Default Balanced.** Not in Auto Backup allow-list |
| `pns_jpeg_credits` | optional artist/copyright |
| `pns_updates` | **not** backed up |
| `pns_connectivity` | LAN/WebDAV/leaderboard toggles |
| `pns_cloud_backup` | SAF folder |
| `pns_workflow_presets` | street/portrait/video_log |
| `pns_welcome_flow` | completed steps |
| `pns_ui_hints` | front/rear spotlight seen |
| `pns_probehub_nav` | recents/favorites |
| `pns_root_capability` | Grant Su |
| `pns_vendor_highlight_ae` | hardware H-AE opt-in |
| `pns_diagnostics` | |
| `FaceOverlayCalibrationStore` | engineering |
| `ExperimentalSafeModeStore` | crash-loop recovery; does **not** steal launch to hub |
| `SettingsExportBundle` | JSON export/import via SAF |

Android Auto Backup allow-list: `app/src/main/res/xml/pns_backup_rules.xml` (prefs only; **not** DCIM, LUTs, update cache). **Also omitted from backup:** `pns_product_systems`, `pns_power_profile`, `pns_updates` (recipes, geotag mode, power profile, update cache would not restore after reinstall via Auto Backup).

Settings search index (`ChromeSettingsSearch.kt`) is the consumer setting inventory used by the in-preview search field — shutter mode, timer, burst, shutter sound, flash, geotag, EXIF strip, on-screen shutter, tap-to-capture, volume keys, BT remote, hardware camera key, max brightness, DND preview/record, peaking, focus mode, histogram, horizon, eye-AF, tally, OIS, EIS, shutter angle, ISO band, crop, grid, FPS, Wear, HDMI/MJPEG, About/updates/Venmo/Obtainium/NOTICE/LICENSE/JPEG credits.

**iOS:** Durable as UserDefaults / App Group for Watch. iCloud backup **Fallback** (must exclude captures like Android).  
**Desktop:** Durable as JSON (already have `SettingsExportBundle`).

---

## 1.10 Data models

Pure JVM helpers in `:pns-core` / `:pns-capture` / `:pns-fleet`: `CommandDialMode`, `FocalMmSlot`, `FocalMode`, `BracketPattern`, `ImagingProfile`, `CapabilityGate`, `FleetDeviceMatrix`, `CameraCapabilityCatalog.CatalogRow`, `DngMetadataResolution`, `CropPlan`, `MediaItem`, `GalleryCaptureGroups`, `ProPictureProfiles`, `VideoFormatPresets`, `FalseColorMode`, `PreviewFlashMode`, `PreviewFocusMode`, `VideoShutterAngle`, `VideoColorProfile`, `TimeLapseMode`, `PnsGeotagMode`, `PnsThemeMode`, `BurstPhotoQualityProfile`.

On-device JSON: `files/fleet_device_matrix.json`, `files/fleet_focal_map.json` (legacy), calibration profiles, probe exports (`PROBE_EXPORT_LATEST.md`), memory profiles CSV.

**iOS/Desktop:** Durable as shared Kotlin (KMP) or reimplemented models. Schema lock is the catalog JSON + settings export — **do not pick a toolkit here**.

---

## 1.11 Permissions

`app/src/main/AndroidManifest.xml` + `WelcomeFlowConfig.kt`:

| Permission | Why | Required to enter |
|------------|-----|-------------------|
| CAMERA | Preview/capture | Yes |
| RECORD_AUDIO | Video sound | No |
| WRITE_EXTERNAL_STORAGE maxSdk 28 | Legacy video insert | No |
| READ_EXTERNAL_STORAGE maxSdk 32 | Legacy gallery | (not in welcome list except write) |
| ACCESS_COARSE/FINE_LOCATION | Optional geotag + NSD tether | No (fine in welcome) |
| VIBRATE | 30 ms still haptic | Manifest only |
| ACCESS_NOTIFICATION_POLICY | Optional DND | System Settings |
| INTERNET / ACCESS_NETWORK_STATE / CHANGE_NETWORK_STATE | Opt-in net | |
| REQUEST_INSTALL_PACKAGES | In-app APK install | |
| NEARBY_WIFI_DEVICES | LAN tether discovery | |
| BLUETOOTH* | Wear BLE | |

`uses-feature` camera.any / camera **required=false** (can list as camera app without failing devices without camera — unusual for a camera; keep as-is).

**No `FOREGROUND_SERVICE` permission.** Camera work is activity-scoped; `ON_PAUSE` pauses YUV (`PreviewLongRunningPause`).

**iOS:** Camera/Mic/Location Info.plist Durable. Bluetooth Wear Durable. Local network Durable. Install-APK **Omit**. DND **Fallback** (Focus modes).  
**Desktop:** OS camera/mic/location prompts Durable. BLE Wear **Fallback**.

---

## 1.12 Hardware buttons and gestures

| Input | Behavior | Default |
|-------|----------|---------|
| Volume **up** | Still (or BKT); while recording → **chapter mark** | on |
| Volume **down** | Toggle recording | on |
| `KEYCODE_CAMERA` / `KEYCODE_FOCUS` | Half-press AF + chirp; full-press still | on if matrix has key |
| BT / headset media key | Shutter | off |
| Tap finder | AF/AE then still if `tapPreviewToCapture` | on |
| Horizontal drag | Manual focus (M / manual AF) | |
| Swipe **up** on finder | Front camera | first-run spotlight |
| Swipe **down** | Rear | |
| Long-press shutter | JPEG burst train | when burst |
| Long-press ISO/SS | AE lock | |
| Long-press AF | Rack waypoints | |
| Long-press Settings | Engineering hub | |
| Long-press+drag QS | Reorder | |
| System Back | Gallery: clear selection then exit; preview: close overlays | `NavigationUx` |
| Gesture nav | Bottom tray in `PnsGestureExclusionBottomBand` (~24% height, API 29+) | |
| Widget tap | `pointandshoot://preview?shoot=1` composed still | |
| QS tiles | Camera / Video / Selfie | |

Hardware-key probe writes `files/HARDWARE_KEY_PROBE_LATEST.json`.

**iOS:** Volume shutter Durable (common). Dedicated camera key **Omit** (no Xperia-style key). BT shutter Durable. Swipes Durable. Widget **Fallback** (WidgetKit cannot keep a live Camera2 session). Control Center **Fallback** vs QS tiles.  
**Desktop:** Keyboard shutter Durable. Volume keys **Fallback** (may be OS volume). Widget/QS **Omit**. Mouse-drag focus Durable.

---

## 1.13 Android platform specifics

### Intents / integration

`MainActivity` exported filters:

- `MAIN` / `LAUNCHER`  
- `STILL_IMAGE_CAMERA` + MAIN/DEFAULT (OEM default-camera lists)  
- `VIDEO_CAMERA`, `VIDEO_CAPTURE`  
- `IMAGE_CAPTURE`, `IMAGE_CAPTURE_SECURE`  
- `STILL_IMAGE_CAMERA_SECURE` (lockscreen)  
- Deep links `pointandshoot://preview|camera|video|gallery|share`  

`ShareReceiveActivity`: `SEND` / `SEND_MULTIPLE` image/* video/*.  
FileProvider `dev.pointandshoot.fileprovider`.  
Widget `PnsCameraWidgetProvider`.  
QS: `CameraTileService`, `VideoTileService`, `SelfieTileService`.

`ACTION_IMAGE_CAPTURE` return: needs a **tonal JPEG/JXL** (`deliverImageCaptureToCaller`); RAW-only cannot satisfy the contract.

### Camera2 / CameraX / vendor

- Production preview: **Camera2**.  
- CameraX: Night/Bokeh dial, QR route, extension smoke, `CameraXExtensionProbe` at `PnsApplication` start.  
- Vendor keys: Qualcomm session params (AF bracketing, AI HSR, VIULL, VSR, HDR DCG, QHDR) — **research, default off**. Catalog in `docs/QCAMERA3_VENDOR_KEY_CATALOG.md`.  
- Root: `RootSettingsScreen`, CPU governor pin, HFR unlock above matrix, CPH2583 max-res unlock.  
- Fleet matrix: `files/fleet_device_matrix.json` SoT (ADR-0004).  
- Logical multicam DNG pairing locked **off**.  
- `CONTROL_ZOOM_RATIO` gated API 30+.

### MediaStore / FGS / backup

MediaStore inserts with `IS_PENDING`. No foreground service. Auto Backup prefs-only. `profileable android:shell="true"` for baseline profiles.

### USB / HDMI / LAN / tether

| Port | Role |
|------|------|
| 28765 | Tether control `GET /status` `POST /capture` `POST /flash` (`docs/TETHER_API.md`) |
| 28766 | LAN media `/status` `/files` `/file` + Wear `/remote` |
| 28770 | MJPEG `/mjpeg` `/snapshot.jpg` |

HDMI: `Presentation` on `TYPE_EXTERNAL`, PixelCopy from SurfaceView/TextureView.  
USB webcam: Lineage UVC + DeviceAsWebcam; P&S must **force-stop** so the gadget owns the camera. HTTP webcam is OBS-only.

**iOS:** Intents/QS/default-camera/lockscreen camera **Omit** or Control Center **Fallback**. Photo capture extension **Fallback**. Tether HTTP Durable (Network.framework). UVC gadget **Omit**. HDMI **Fallback** (AirPlay / USB-C video). Vendor keys / root **Omit**. CameraX **Omit** (use AVFoundation).  
**Desktop:** Intents/QS/widget **Omit**. Tether HTTP **Durable (high value)**. UVC host **Durable**. UVC gadget **Omit**. HDMI preview **Fallback**.

---

## 1.14 Visual design

| Token | Value | Path |
|-------|-------|------|
| Photo orange | `#FF5C00` | `PnsTheme.kt` `PnsColors.PhotoOrange` |
| Record red | `#E00000` | |
| Charcoal | `#181A1B` | launcher + chrome |
| Warn amber | `#E6A23C` | |
| Ok green | `#4CAF50` | |
| Root blue | `#4DA3FF` | root-only only |
| Preview scrim | `#CC000000` | |
| Menu surface | `#1A1A1A`, 16.dp corners, 6.dp elevation | settings style guide |
| Type | JetBrains Mono (SIL OFL) all HUD | `res/font/jetbrainsmono_regular.ttf` |
| Touch | ≥48.dp | `PnsTheme` KDoc |
| Icons | Material Outlined / Filled | grid slots |
| Animation | QS hover/reorder; selfie ring rotate; 3× orange pulse on settings search | |
| Orientation | Activity `fullUser`; preview **static** rotation pref (not gravity); chrome gravity separate | `PreviewChromePreferences.staticPreviewRotationDeg` |
| Theme | System/Light/Dark for non-preview; **photo chrome stays dark charcoal** | `UxSettings` |
| Immersive | Status band is a layout slot, not doubled padding | style guide |
| A11y | `contentDescription` constants `AboutScreenA11y`, `ChromeSettingsA11y`; USB dump gate | |

Brand assets: `docs/branding/`, `docs/BRAND_ICON.md`.

**iOS:** Color/type Durable. Dynamic Type **Fallback** (mono HUD may stay fixed). SF Symbols vs Material icons **Fallback** if Edward allows; “no redesign” argues keep the same vector set.  
**Desktop:** Durable colors; window chrome Fallback.

---

## 1.15 Wear OS

`wear/src/main/java/dev/pointandshoot/wear/`:

- `WearRemoteActivity` — Remote vs Timer.  
- Protocol lock-step with phone `PnsRemoteProtocol`: BLE service `8f7a0001-…`, write `8f7a0002-…`, LAN port 28766.  
- Actions: shutter, video start/stop/toggle, chapter, timer 3/5/10, cancel (`0x11`).  
- No Play Services.

**iOS:** watchOS **Fallback** (Watch Connectivity ≠ BLE GATT unless you keep BLE).  
**Desktop:** Omit, or a tiny remote window (Fallback).

---

## 1.16 Connectivity (consumer + engineering)

LAN transfer, WebDAV, Immich, social webhook (`SocialStreamHooks`), collaborative capture, cloud SAF backup, leaderboard opt-in POST (capability summaries, not photos), Obtainium link, GitHub update check (skip metered / battery saver / Wi-Fi-only).

**iOS/Desktop:** Durable as HTTPS clients. Leaderboard opt-in Durable. Social webhook engineering-only.

---

## 1.17 Fleet / per-device quirks (do not drop)

The app’s honesty model **is** the fleet matrix. A port that greys out features or assumes OnePlus 12 behavior is a regression.

| Quirk / lock | Path |
|--------------|------|
| Hide unavailable; root-blue + toast | `FleetUiVisibilityGate.kt` |
| Catalog **CATALOG_VERSION = 6**; **84** `CatalogRow(` in the base registry + expansion loops (gate treats expansion as 115 → ≥170 total; ~200 with codec×tier) | `CameraCapabilityCatalog.kt`, `CameraCapabilityCatalogExpansion.kt`, `scripts/pns_capability_catalog_gate.ps1` |
| CPH2583 primary | `AGENTS.md` |
| Legacy SKU independent cameras 2/3/4, empty physical IDs | DNG pairing docs |
| Dodge 73/85/150 | ADR-0003 |
| §4a streamHints false; §2 RAW tier order | `docs/REVERTED_FEATURES_RESTORE_LIST.md` |
| Aux DNG dark/green if RAW12 on logical + unpinned | `.cursor/rules/dng-logical-multicam-metadata-lock.mdc` |
| GLES `setGeometry` only from `PreviewMainViewport` | `AGENTS.md` CRITICAL GLES |
| HFR honesty classes | `docs/VIDEO_MODE_MATRIX.md` |
| 4K regular vs UHD60 vs 4K120 S0/S1/S2 | technical settings §10 |
| Night/Bokeh hidden on Lineage (no extensions) | CameraX probe |
| Variable aperture (Xperia-class) | `lens.variable_aperture` |
| Hardware camera key (Sony-class) | `product.hardware_camera_key` |
| Max-res unlock CPH2583 root | `root.max_res_unlock_cph2583` |

**iOS:** Per-device honesty Durable **as an iOS capability matrix** (DiscoverySession, isProRAWSupported, virtual devices) — **not** the Android JSON as-is. Qualcomm/root rows **Omit**.  
**Desktop:** Per-webcam UVC descriptor matrix **Fallback**. Phone fleet matrix stays on the phone.

### Catalog consumer/planned rows (explicit so none are dropped)

**Shipped / partial (product):** `raw.dng`, `raw.ultra_max`, `still.bracket`, `still.zsl`, `still.avif`, `still.jxl`, `still.heic`, `still.motion_photo`, `still.tiff16`, `still.nightscape`, `still.intervalometer`, `still.monochrome_capture`, `dial.monochrome`, `video.h264`, `video.hevc`, `video.av1` (partial), `video.vp9`, `video.hfr`, `video.regular.1080p30`, `video.4k_regular`, `video.uhd60`, `video.dcg_hdr`, `video.raw`, `video.raw_picker`, `video.dual`, `video.timelapse`, `video.multicam_melt` (partial), codec×tier expansion `video.{h264,hevc,av1,vp9}.{720p,1080p,4k,8k}` and fps tags, `face.*`, `af.*`, `lens.*`, `hud.*`, `preview.qr`, `preview.pip`, `audio.hifi`, `audio.external_mic`, `audio.spatial`, `capture.wait_focus_lock`, `product.format_picker`, launch handlers, hardware keys, `tether.*`, `privacy.exif_strip`, `product.settings_export`, fleet matrix/parity.

**Probe-only / engineering:** `still.resolution_maximum_map`, `still.hidden_highres`, CameraX night/bokeh/hdr, OEM app smoke, root vendor/cpu/hfr/max-res, `fleet.deep_caps`, `video.dual_iso`, `video.prores_probe`, `still.depth`.

**Planned / N/A (must remain on the list):** `still.panorama` N/A (spike NO-GO), `still.preview_shots` Planned, `video.log_profile` N/A (flat cine substitute), `still.ultrahdr` N/A, `still.computational_hdr` Planned, `preview.qr_mlkit` Planned.

---

## 1.18 Desktop value assessment (**decided COMPANION**)

A pro **phone** camera’s worth on desktop is **not** “the same 7×3 finder with a mouse.”

| Desktop role | Honest fit | Tag |
|--------------|------------|-----|
| **Tethered capture console** (HTTP :28765 already: status, capture, flash; Wi‑Fi Direct + mDNS) | High. Matches how capture tethering works on real cameras. | **Durable** |
| **LAN roll / proofing / WebDAV / Immich offload** | High. Desktop is where those files go. | **Durable** |
| **RAW review + library desk** (cull, keywords, compare, evidence, LUT bake) | High. Desktop screens and calibrated displays. | **Durable** |
| **Settings / preset / LUT / calibration sync** (`SettingsExportBundle`) | High. | **Durable** |
| **UVC host** (talk to phone-as-webcam or USB cams) | Medium–high, already partly scripted (`pns_usb_webcam_windows.ps1`) | **Durable** |
| **Capture from laptop webcam as a Camera2-class RAW camera** | Low / false. Webcams are not DNGs. | **Omit** as parity; **Fallback** as a simple webcam recorder if Edward wants a bonus |
| **Pixel-identical portrait chrome as the main desktop UI** | Low. Fights windowing, input, and the locked mobile layout. | **Fallback** at most |
| **QS tiles, lockscreen camera, IMAGE_CAPTURE provider** | None. | **Omit** |
| **Root vendor keys, NightScape HAL, 4K120 HS** | None on a PC GPU/webcam. | **Omit** (remain phone-side) |
| **Wear remote from desktop** | Niche. | **Fallback** |

**Decided (Edward 2026-11-16):** desktop is that companion + tether + library + **UVC host** (webcam output is not DNG). Locked shooting chrome stays on **handheld** (Android + iOS). UVC capture on desktop is a connected-camera recorder, not Camera2 DNG parity.

---

## 1.19 Cross-cutting tag table (platform-specific behaviors)

| Android behavior | iOS | Desktop |
|------------------|-----|---------|
| Camera2 RAW12/DNG loadability pipeline | **Durable** as Apple ProRAW (Q3); **Omit** identical Camera2 DNG | **Omit** capture; **Durable** review |
| Logical multicam DNG pairing locks | **Omit** | **Omit** |
| Qualcomm session vendor keys | **Omit** | **Omit** |
| Root / Magisk / max-res unlock | **Omit** | **Omit** |
| CameraX Night/Bokeh | **Fallback** Portrait/Night photo | **Omit** |
| Lineage USB UVC gadget | **Omit** | Host **Durable**; gadget **Omit** |
| HDMI Presentation + PixelCopy | **Fallback** AirPlay | **Fallback** window |
| MediaStore `DCIM/PointAndShoot` (Q11 single tree) | **Fallback** PhotoKit album of the same name | **Durable** folder |
| `ACTION_IMAGE_CAPTURE` provider | **Omit** | **Omit** |
| QS tiles | **Fallback** Control Center | **Omit** |
| Home widget still | **Fallback** WidgetKit (no live HAL) | **Omit** |
| Lockscreen secure camera | **Fallback** | **Omit** |
| Volume shutter | **Durable** | **Fallback** |
| Dedicated camera key | **Omit** | **Omit** unless specialized hardware |
| Wear BLE GATT | **Fallback** watchOS | **Fallback** |
| DND while preview | **Fallback** Focus | **Omit** |
| Quick Settings DND policy permission | **Omit** | **Omit** |
| Foreground? (none today) | n/a | n/a |
| GLES `GLSurfaceView` LUT | **Durable** Metal | **Durable** GL/Vulkan/Metal |
| ML Kit faces | **Durable** Vision on-device (Q6) | **Fallback** / **Omit** |
| ZXing QR | **Durable** | **Durable** |
| Fleet hide-unavailable | **Durable** policy | **Durable** policy |
| 7×3 locked chrome | **Durable** as *flow / IA* on iPhone portrait (Q4 adapt, not pixels) | **Omit** as primary UI (companion); **Fallback** only if a tether live-view pane reuses chips |
| Tether HTTP | **Durable** | **Durable** (high value) |
| In-app GitHub APK install | **Omit** | **Fallback** installer |
| F-Droid / Obtainium | **Omit** | **Omit** / **Fallback** |

---

# 2. Trajectory & Future Goals

Each item is labeled **stated** (written in a repo artifact) or **inferred** (implied by code/catalog/news, not a committed product promise).

## 2.1 Stated product goals (`README.md`)

- Be **a camera**, not a clever phone app — **stated**.  
- Finder you can trust; readout in sunlight; Lightroom-openable files — **stated**.  
- Hide what hardware cannot do; no greyed-out theater — **stated**.  
- Drive aperture, shutter, ISO, LUT, focal length, Photo/Video/Webcam — **stated**.  
- DNG first; optional JPEG companion; never rewrite DNG with JPEG EXIF — **stated**.  
- Privacy default; no analytics; opt-in network — **stated**.  
- GitHub Releases + Obtainium now; F-Droid listing in progress — **stated**.  
- Not a finished commercial product promise; unbounded scope is rejected — **stated** (“What this is not”).

## 2.2 Active `BUILD_PLAN.md` (stated, open)

**AGENT:** BOOT.3 after HUMAN BOOT.1 (bootstrap 0.15 → 1.10); CRI-032 eye-AF pixel gate needs USB face scene.  
**BLOCKED:** colorchecker ΔE2000 gate on HUMAN ColorChecker capture.  
**AUTO:** Scorecard/template weekly; Dependabot 0 Critical/High; Security scan + CodeQL green; ADB env health; keystore pins; F-Droid metadata structure; PRIVACY/NOTICE links.  
**HUMAN:** CPH2583 serial in gitignored env; GitLab login; keystore custody; BOOT.1 upgrade scope; Scorecard required-check decision; Dependabot merge/defer; ColorChecker; H.SCENE sky torture; HUD/LUT aesthetics; H.8.1–8.5 overlays/dual-video/H.265 DCG/false color; CRI-035 ACR sign-off OP13 aux DNG; CRI-034 store copy; community announcements.  
**Deferred:** DNG-FLEET-EXPOSURE P3 OP13 residual shadow.

Milestone H gate: HUMAN list + `pns_prerelease_gate.ps1 -IncludeUsb` on CPH2583.

## 2.3 Recently shipped (stated, `CHANGELOG.md` / `BUILD_PLAN_COMPLETED.md`)

0.14.2 (2026-10-22): cold start, sticky tray dial, H metering re-arm, reconnect storm cooldown.  
0.14.1: gallery title layout.  
0.14.0: stable name/icon/signed ship.  
0.14.0-beta.22: Wear, HDMI/MJPEG, USB webcam, library desk, recipes, geotag modes, landscape finder.  
Milestones T (bootstrap), 24 (4K120 honesty), 28 (feature richness waves), H residuals archived.

## 2.4 Catalog Planned / N/A (stated)

From `CameraCapabilityCatalog.kt` + spikes:

| ID | Status | Source |
|----|--------|--------|
| `still.panorama` | NotApplicable | `docs/spikes/PANORAMA_SPIKE.md` NO-GO M28 |
| `still.computational_hdr` | Planned | `docs/spikes/COMP_HDR_SPIKE.md` NO-GO M28 |
| `still.preview_shots` | Planned | catalog `post_m28_defer` |
| `preview.qr_mlkit` | Planned | `docs/spikes/ML_KIT_FOSS_SPIKE.md` — stay ZXing on foss |
| `still.ultrahdr` | NotApplicable | HAL not on primary fleet |
| `video.log_profile` | NotApplicable | flat cine substitute |
| `still.depth` | ProbeOnly | post-M28 defer |
| `video.prores_probe` | ProbeOnly | |
| `video.dual_iso` | ProbeOnly | merger stub |
| CameraX HDR inline | Isolated handoff only | ADR-0010 |

## 2.5 Parity debt (stated)

`docs/FLEET_PARITY_DEBT_LEDGER.md`: 115 actionable rows (HEIC/JXL/TIFF/motion photo/spatial/AV1/color profiles/delivery honesty/perf).  
`docs/FLEET_PARITY_BUILD_PLAN_INTAKE.md`: open **PBI-video.hfr.120-DeliveryHonesty**.

These are **Android fleet proof gaps**, not new features — **stated**.

## 2.6 HUMAN_BACKLOG.md

Empty table — **stated** (no deferred `/build` rows).

## 2.7 News inbox (stated ideas, not committed)

`docs/NEWS_INBOX.md` 2026-11-16: evaluate RAW-referenced ETTR / zebras from sensor clip (**idea only**; RawLens is GPL-3.0 — copy no code). 2026-11-13: CameraX quirk study P3; Kotlin 2.5.0-RC smoke P3.

## 2.8 ADRs that constrain any port (stated)

0001 Compose monolith + USB tests; 0002 no DNG EXIF rewrite; 0003 dodge tele; 0004 fleet matrix SoT; 0005 Apache-2.0; 0006 ML Kit face exception; 0007 Detekt/Kover/no androidTest; 0008 mock/gallery restart; 0009 module boundaries; 0010 extension handoff isolation.

A multiplatform toolkit pick would need a **new ADR** — not this Phase 0 document.

## 2.9 Inferred from code (not promised)

- Settings export JSON is the natural cross-device preset format (`product.settings_export` shipped).  
- Tether API is already a desktop/companion protocol.  
- Concurrent PiP (preview inset) and Multicam Melt are shipped/partial but not README-headline. Dual-video **encode** is stacked; LG-style PiP overlay is **stated later**, not shipped (`docs/M14_12_DUAL_VIDEO.md`).  
- Capture journal is **stated** in README/CHANGELOG as a user-facing list; **inferred** gap: no Compose consumer besides `PnsBugReportPack.latestLine()`.  
- Motion trip / Astro / Document recipes are **stated** as product-system labels; **inferred** incomplete (arm/enum only).  
- Scene vendor hints: HUD toggle + always-on probe; readout chip **not** wired despite KDoc.  
- TalkBack / a11y dump gate: **H.6 agent** closed (`BUILD_PLAN_COMPLETED.md`); formal gallery TalkBack was deferred in the 2026-05-22 gallery sign-off note — treat H.6 USB dump as done, gallery TalkBack as possibly still HUMAN-feel.  
- `libpns_native.so` is a stub/facade — native encode is not the shipping still path.  
- GitHub Issues unused; process is BUILD_PLAN + HUMAN list.  
- F-Droid `IssueTracker` URL exists but has 0 open issues.  
- Dual folder names were a **bug** (Q11); migration assumes `DCIM/PointAndShoot`.  
- Desktop “true cross-platform with full feature and design parity” is resolved as **companion** (Q1), not a phone-chrome clone — Omit-tagged HAL features stay phone-side.

## 2.10 Commit-history themes (stated via git log)

Recent `main`: news-bot inbox, Argus/bootstrap parent sync, Dependabot Compose/core/mockito, security Netty/BouncyCastle, F-Droid SHA pins, **0.14.2 launch/H/reconnect**, Wear/webcam/gallery desk beta.22, ProShot AE precapture, H metering, QR restore, Milestone 28 richness, 4K120 honesty. Trajectory through 0.14.2 is **stabilize Android camera + FOSS distribution**. This inventory plus DESIGN_BRIEF / TECH_DECISION / PARITY_CHECKLIST (same PR, 2026-11-16) is the start of the documented iOS + desktop companion program.

## 2.11 TODOs in tree

A `TODO`/`FIXME` grep over `*.kt`/`*.md` did not surface a living in-code TODO backlog (CHANGELOG match only). Work tracking is **BUILD_PLAN emoji rows**, not scattered TODOs — **stated observation**.

---

## Document control

| Field | Value |
|-------|-------|
| Phase | 0 — inventory + trajectory; Decisions recorded 2026-11-16 |
| Authoring | Cursor cloud agent, 2026-11-16 |
| App code | Untouched |
| Technology pick | See [`TECH_DECISION.md`](TECH_DECISION.md) (Phase 2, same PR) |
| Design brief | See [`DESIGN_BRIEF.md`](DESIGN_BRIEF.md) (Phase 1, same PR) |
| Next | Phase 4 = BUILD_PLAN `MIG.*` — **not started** |

**Related Android SoT (do not duplicate into a second inventory):** `docs/PNS_TECHNICAL_SETTINGS.md`, `docs/preview-chrome-layout-style-guide.md`, `AGENTS.md` CRITICAL locks, `KNOWLEDGE_BASE.md`.
