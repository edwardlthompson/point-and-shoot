## Build plan (Point & Shoot)

This plan implements your Parts 1–5 spec and is ordered for execution: **foundations → probe → mapping → engine → HUD → polish → release**.

### Conventions
- **[HOST]**: runs on your workstation
- **[ADB]**: runs on the connected OnePlus 13 over ADB
- **Gate rule**: if a V&V gate fails, stop, capture repro + logs, fix, then re-run the gate.

## 0) Test harness & global quality gates (apply to every phase)
- [ ] [ADB] `adb devices` shows the OnePlus 13 as `device` (authorized).
- [ ] [ADB] Launch + PID works:
  - `adb shell am start -n dev.pointandshoot/.MainActivity`
  - `adb shell pidof dev.pointandshoot`
- [ ] [ADB] Live PID log monitoring available:
  - `adb logcat -v time --pid=<pid> *:V`
- [ ] [HOST] Build: `.\gradlew.bat :app:assembleDebug`
- [ ] [ADB] Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- [ ] [ADB] Smoke run: launch + basic interaction without crash
- [ ] [HOST] CLI-only workflow documented and kept current: `CLI_BUILD_AND_SIDELOAD.md`

## 1) Foundations (Part 1: repo + FOSS constraints)
### FOSS non-negotiables
- [x] Apache-2.0 license
- [x] Zero proprietary binaries committed
- [x] No Google Play Services dependencies
- [ ] Dependency audit gate: verify `app/build.gradle.kts` has **no** `com.google.android.gms` / Play Services / proprietary SDKs

### Repo & structure
- [x] Local git repository initialized
- [x] Public GitHub repository created: `point-and-shoot`
- [x] Repo structure present:
  - [x] `app/` (Compose/Kotlin)
  - [x] `native/` (NDK/JNI stubs for AVIF/JXL/peaking)
  - [x] `metadata/` (F-Droid compliance placeholders)

### Publishing state (source control)
- [x] Code is committed and pushed to `origin/main`
- [ ] GitLab integration (mirror + optional GitLab CI) is configured (see CI/CD section)

## 2) Phase 0 (Part 5): Capability probe (Immediate) — iterate until it answers the gates
### Deliverable
- [x] `CameraCapabilitiesProbe.kt` exports Markdown
- [x] `PROBE_RESULTS.md` populated from on-device export
- [ ] Add a durable, machine-readable probe artifact:
  - [x] Deep caps probe writes JSON to app external files (`deep_caps_*.json`)
  - [x] HFR/encoder probe writes JSON to app external files (`enc_probe_*.json`)
  - [x] Exhaustive matrix probe writes JSON (`exhaustive_probe_*.json`: every advertised HFR size×fps×codec + regular SurfaceTexture×AE-FPS matrix, vendor key hints, capability flags)
  - [x] [HOST] `scripts/pns_hfr_autorun.ps1` pulls `deep_caps_*.json`, `enc_probe_*.json`, `exhaustive_probe_*.json`, `legacy_camera1_*.json` into `hfr-runs/` (`-RunDeepCaps`, `-RunLegacyCamera1`, `-RunExhaustive`, `-ExhaustiveIncludeLogical`, **`-RunFullSuite`** = deep → legacy Camera1 → exhaustive → encoder; use `-MaxRuns 0` to skip encoder; `-NoRestartBetweenPhases` to skip `cameraserver` restarts between phases)

### Required probe expansions (unblocks engine decisions)
- [x] Vendor tags: identify the relevant tags for:
  - LBMF / MFHDR paths
  - DCG-HDR (HDR DCG) paths
  - Android 16 hybrid AE / 10-bit/HDR pipeline support
- [x] Typed values (not just key names):
  - `android.request.availableCapabilities`
  - `android.request.availableDynamicRangeProfiles` + `android.request.recommendedTenBitDynamicRangeProfile`
  - `android.scaler.streamConfigurationMap` (preview sizes + FPS ranges; identify 120fps candidates)
  - RAW outputs (sizes/formats; stall characteristics)
  - Face detect modes (confirm `STATISTICS_FACE_DETECT_MODE_FULL` availability)

### Phase 0 V&V gate (must pass before Phase 1)
- [x] [ADB] Vendor request/session keys present in `PROBE_RESULTS.md`
- [x] [ADB] Probe answers: which cameraId supports **120fps preview**, and at what size/format
- [x] [ADB] Probe answers: **RAW12 capture** feasibility per camera mode (wide/tele/ultra-wide/front)
- [ ] [ADB] Probe answers (validated, not just enumerated): which cameraId+size+fps+mimetype combinations are actually stable for HFR encode (AVC/HEVC), including exact failure modes (e.g., `-38 Function not implemented`)

## 3) Part 2: Hardware-to-software mapping ("dodge profile")
### Deliverable
- [ ] `DODGE_PROFILE.md` mapping each focal-equivalent mode to:
  - cameraId(s) + physicalCameraId(s) (if logical)
  - sensor role + constraints (RAW/HDR/OIS/macro)
  - crop metadata strategy for 35/50/85mm modes

### Target mapping table (from spec)
- [ ] 15mm: Samsung S5KJN5 — Ultra-wide / Macro (🌷 Super Macro)
- [ ] 23mm: Sony LYT-808 — Main wide (LBMF, DCG-HDR, RAW12)
- [ ] 35mm: Sony LYT-808 — Street crop (1.5x + DefaultUserCrop metadata)
- [ ] 50mm: Sony LYT-808 — Standard crop (2.2x + center-weighted metering)
- [ ] 73mm: Sony LYT-600 — Tele (3x periscope, OIS, LBMF)
- [ ] 85mm: Sony LYT-600 — Portrait crop (1.16x + Eye-AF priority)
- [ ] 21mm: Sony IMX615 — Front (32MP, 4K/60 RAW, zero beauty)

### Mapping V&V gates (before implementing mapping-dependent behavior)
- [x] [ADB] Confirm logical vs physical camera topology (from probe)
- [x] [ADB] Confirm focal length clusters match intended roles
- [ ] [ADB] Confirm macro capability + min focus distance / mode-switch behavior

## 4) Phase 1 (Part 5): Imaging engine + Part 3 pipeline requirements
### Part 3 requirements that Phase 1 must satisfy
#### Imaging profiles
- [ ] Standard Pro (default): lossless-compressed DNG + 10-bit AVIF (HDR) + Display P3
- [ ] Ultra-Max: uncompressed RAW12 DNG + 12-bit JPEG XL (`.jxl`) + Rec. 2020

#### Sensor stability protocol
- [ ] 30ms haptic delay: fire electronic shutter → await readout completion → fire tick haptic
- [ ] Video tally: solid red border; **disable all haptics** during video start/stop

#### Advanced metering & AF
- [ ] Highlight-weighted metering (protect 95th percentile luma; Ricoh GR style)
- [ ] Sony-style Eye-AF overlay (green micro-rectangles over pupils; uses `STATISTICS_FACE_DETECT_MODE_FULL` when available)
- [ ] Nikon-style 3D tracking persistence logic
- [ ] Exposure bracketing (BKT): 3/5/7 RAW12 sequences with GroupingID metadata

### Phase 1 deliverable
- [ ] Preview + capture engine with:
  - 120fps preview on supported path(s)
  - RAW12 DNG saving
  - NDK pipeline callable from Kotlin

### Phase 1 work items
- [ ] Implement `CameraDevice` + `CaptureSession` targeting 120fps preview where supported
- [ ] RAW12 DNG saver (lossless + uncompressed modes per imaging profile)
- [ ] NDK pipeline integration:
  - [ ] `libavif` path (10-bit AVIF HDR)
  - [ ] `libjxl` path (12-bit JXL)
- [ ] Implement the 30ms haptic delay logic (still capture only)

### Phase 1 V&V gate (must pass before Phase 2)
- [ ] [ADB] 10 consecutive captures without session death
- [ ] [HOST] Verify outputs by pulling files and opening in desktop tooling:
  - Standard Pro: DNG (lossless) + AVIF (10-bit HDR)
  - Ultra-Max: DNG (uncompressed RAW12) + JXL (12-bit)
- [ ] [ADB] Logcat shows no repeating Camera2 errors during preview/capture loop

## 5) Phase 2 (Part 5): Professional HUD & dial
### Deliverable
- [ ] Pro HUD + dial usable during live preview

### Work items
- [ ] Rotary command dial: M / H / S / BKT
- [ ] Video tally (solid red border) + Sony-style timecode `00:00:00:00`
- [ ] `Settings > HUD` granular element toggles

### Phase 2 V&V gate (must pass before Phase 3)
- [ ] [ADB] Mode transitions deterministic and logged (no hidden state)
- [ ] [ADB] No UI-induced capture regressions (preview remains stable)

## 6) Phase 3 (Part 5): Street polish + Part 4 UX/heritage requirements
### Part 4 requirements
- [ ] Typography: JetBrains Mono for all technical readouts
- [ ] Visual feedback colors:
  - Photo button `#FF5C00` (Hasselblad orange)
  - Video button `#E00000` (record red)
- [ ] About page tribute block (monospaced, exact content):

```
SONY: For the relentless pursuit of speed and the intelligence of the "sticky" Eye-AF.
RICOH: For the "Snap Focus" philosophy and the courage to protect the highlights.
OLYMPUS: For the pioneering "Super Macro" and the soul of the compact professional tool.
HASSELBLAD: For the legendary Natural Colour Solution and the iconic Orange shutter.
CANON & NIKON: For the gold standard of focus bracketing, 3D tracking, and the unwavering reliability of the professional instrument.
```

### Phase 3 deliverable
- [ ] Street interaction model + macro lock + heritage page

### Phase 3 work items
- [ ] Tap-to-shoot (lock AF/AE on DOWN, fire on UP)
- [ ] Finalize 🌷 Super Macro hardware lock behavior
- [ ] Implement Heritage About page per Part 4
- [ ] Add a developer-facing “What works on OnePlus 13 (dodge)” block to the About page:
  - [ ] Summarize the **successful, validated capture method(s)** discovered by probes (e.g., constrained high-speed vs regular session, encoder surface path, sizes/FPS, AVC vs HEVC)
  - [ ] Include the exact “known-good recipe” as copy/pastable bullets (cameraId, size, fpsRange, mime, key request settings)
  - [ ] Explicitly list “known-bad” paths and the canonical errors (e.g., `Function not implemented (-38)` for specific HEVC/HFR stream configs)
  - [ ] Keep this section updated from JSON probe artifacts to save other developers time

### Phase 3 V&V gate (release readiness)
- [ ] [ADB] 15-minute on-device session: preview, mode changes, capture, export; no crash
- [ ] [HOST] Dodge profile decisions trace to `PROBE_RESULTS.md` and/or `DODGE_PROFILE.md`

## 7) Documentation, releases, and CI/CD (continuous, but required before publishing)
### Branding (icon) (must be done before first public release)
- [ ] Generate an app icon set and wire it into the APK:
  - [ ] Create adaptive launcher icon resources:
    - [ ] `app/src/main/res/mipmap-*/ic_launcher.png`
    - [ ] `app/src/main/res/mipmap-*/ic_launcher_round.png`
    - [ ] `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
    - [ ] `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
    - [ ] `app/src/main/res/drawable/ic_launcher_foreground.xml` (vector)
    - [ ] `app/src/main/res/values/ic_launcher_background.xml`
  - [ ] Ensure `AndroidManifest.xml` references `@mipmap/ic_launcher` and (if desired) `@mipmap/ic_launcher_round`.
  - [ ] [ADB] Verify icon appears correctly on the launcher (light/dark mode, themed icons if enabled).
- [ ] Add a README-renderable icon asset:
  - [ ] Export a 512×512 PNG (e.g., `docs/icon.png`) for GitHub/README display.
  - [ ] Embed it at the top of `README.md`.

### README (pitch + developer entrypoint)
- [ ] Refresh `README.md` to be an enticing pitch and onboarding doc:
  - [ ] Product pitch + differentiators
  - [ ] Feature highlights (probe, RAW12, AVIF/JXL targets, pro HUD)
  - [ ] Device focus + constraints (OnePlus 13 / LineageOS; FOSS-only; no Play Services)
  - [ ] Screenshots section (placeholder until UI exists)
  - [ ] Quickstart linking to `CLI_BUILD_AND_SIDELOAD.md`
  - [ ] Roadmap linking to this `BUILD_PLAN.md`
  - [ ] License + contribution notes

### Changelog + release notes (updated every release)
- [ ] Keep `CHANGELOG.md` with an **Unreleased** section during development
- [ ] On release:
  - [ ] Move Unreleased items to a versioned section (e.g., `## 0.1.0 - YYYY-MM-DD`)
  - [ ] Tag the release in git (annotated tag)
  - [ ] Publish GitHub Release notes from prepared markdown (use `RELEASE_NOTES_TEMPLATE.md`)
- [ ] After release:
  - [ ] Start a fresh Unreleased section

### CI/CD integration (GitHub Actions + GitLab) + private signed builds
#### Goals
- Signed APKs from CI for private distribution/testing
- Signing material stays out of git (encrypted secrets only)
- Optional GitLab mirror and/or GitLab CI

#### GitHub Actions (private signed builds)
- [ ] Add workflow: `.github/workflows/build-signed.yml` (manual `workflow_dispatch`, optional tags)
- [ ] Add signing support in Gradle (no secrets committed):
  - [ ] Use env vars or `keystore.properties` (gitignored)
  - [ ] Configure `signingConfigs.release` + `buildTypes.release`
- [ ] Store secrets in GitHub Actions:
  - [ ] `ANDROID_KEYSTORE_BASE64`
  - [ ] `ANDROID_KEYSTORE_PASSWORD`
  - [ ] `ANDROID_KEY_ALIAS`
  - [ ] `ANDROID_KEY_PASSWORD`
- [ ] V&V:
  - [ ] [HOST] `./gradlew :app:assembleRelease`
  - [ ] [HOST] `apksigner verify --verbose app-release.apk`
  - [ ] [ADB] `adb install -r app-release.apk` + smoke run

#### GitLab integration (mirror + optional GitLab CI)
- [ ] Create GitLab project (can be private)
- [ ] Configure mirroring (GitLab mirror from GitHub OR add GitLab remote)
- [ ] Optional `.gitlab-ci.yml` to build debug/release
- [ ] If signing in GitLab CI: store masked/protected variables:
  - `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`
- [ ] V&V: mirror stays current; pipelines build from a clean runner

## 8) Cross-cutting optimizations (add as gates as features land)
### Performance budget + profiling gates
- [ ] Define budgets (per mode):
  - [ ] [ADB] Preview FPS target(s) (including any 120fps mode)
  - [ ] [ADB] Capture latency budget (tap-to-shutter, BKT burst window)
  - [ ] [ADB] Cold start time budget
- [ ] Add profiling gates:
  - [ ] [ADB] Collect a Perfetto trace for: launch, preview start, still capture, BKT
  - [ ] [ADB] Measure UI jank/frame pacing during preview (baseline + regressions)
  - [ ] [HOST] Store profiling summaries (what changed, what improved/regressed)

### Camera2 robustness & failure-matrix validation
- [ ] Add explicit “failure matrix” test runs after each major camera change:
  - [ ] [ADB] Permission denied → graceful UX + recovery
  - [ ] [ADB] Camera in use by another app → graceful error + retry path
  - [ ] [ADB] Orientation change during preview/capture → no crash, session recovers
  - [ ] [ADB] Background/foreground transitions → no dead session, state restored
  - [ ] [ADB] Thermal throttling / long session (10+ min preview) → no runaway errors
- [ ] Vendor tag safety gate:
  - [ ] [ADB] Every vendor tag use is feature-detected and guarded
  - [ ] [ADB] Fallback behavior verified when tag is unavailable/ignored

### Capture pipeline architecture (avoid preview stalls)
- [ ] Enforce separation:
  - [ ] Camera control thread (requests/session)
  - [ ] Image acquisition/processing thread(s)
  - [ ] IO/encode thread(s) (DNG/AVIF/JXL)
  - [ ] UI state thread (Compose)
- [ ] Backpressure rules:
  - [ ] [ADB] Burst/BKT does not OOM or block preview
  - [ ] [ADB] RAW + encode queues bounded; behavior defined when overloaded (drop/slow/fail)

### Storage + media indexing strategy
- [ ] Decide and document where files go:
  - [ ] MediaStore vs SAF vs app-private + Export (pick a default per profile)
- [ ] Validation:
  - [ ] [ADB] Outputs are written reliably with predictable naming
  - [ ] [ADB] (If MediaStore) files appear in gallery apps; (if app-private) export works
  - [ ] [HOST] Pull + verify files open in desktop tooling (DNG/AVIF/JXL)

### Testability hooks + diagnostics mode
- [ ] Add a “Diagnostics mode” toggle:
  - [ ] [ADB] Dumps active session configuration + key vendor settings into logs
  - [ ] [ADB] Exports a compact diagnostics report alongside `PROBE_RESULTS.md`
- [ ] Add correctness tests where feasible:
  - [ ] [HOST] Golden-file tests for metadata serialization (GroupingID, crop metadata)

### Release hardening gates
- [ ] Logging policy:
  - [ ] [HOST] Release builds do not emit verbose debug logs by default
  - [ ] [ADB] Release build smoke run with PID log monitoring
- [ ] Security/F-Droid hygiene:
  - [ ] [HOST] Dependency/license scan gate (FOSS-only)
  - [ ] [HOST] SBOM generation (optional, but recommended for transparency)

### UX safety nets (capability-driven UI)
- [ ] Feature gating UX:
  - [ ] [ADB] UI only enables features supported by probe results
  - [ ] [ADB] Unsupported options are disabled with a clear reason
  - [ ] [ADB] Safe defaults always produce a valid capture path

## 9) Scope control gates
- No engine/UI behavior that depends on unknown capabilities until Phase 0 typed values are captured
- Every vendor-tag use must be guarded (feature detection + safe fallback)









