## Build plan (Point & Shoot)

This plan **implements Parts 1–5 from the original spec** and adds industry-standard agent gates:
- Each phase has a **deliverable**.
- Each phase has **validation & verification (V&V)** checks.
- If a gate fails: **stop, capture repro + logs, fix, then re-verify** before advancing.

## Non-negotiables (Part 1: FOSS constraint)
- [x] Apache-2.0 license.
- [x] **Zero** proprietary binaries committed.
- [x] **No** Google Play Services dependencies.
- [ ] Dependency audit gate: verify `app/build.gradle.kts` has **no** `com.google.android.gms` / Play Services / proprietary SDKs.

## Repo setup (Part 1: repository + structure)
- [x] Local git repository initialized.
- [x] Public GitHub repository created: `point-and-shoot`.
- [x] Repo structure present:
  - [x] `app/` (Compose/Kotlin)
  - [x] `native/` (NDK/JNI stubs for AVIF/JXL/peaking)
  - [x] `metadata/` (F-Droid compliance placeholders)
- [ ] GitHub publishing (repository must contain code, not just an empty repo):
  - [ ] Create initial commit (no secrets; `local.properties` must stay untracked)
  - [ ] Push `main` to `origin`
  - [ ] Verify GitHub Actions (if added later) and release artifacts policy (FOSS-only)

## Global quality gates (apply to every phase)
- [ ] Build: `:app:assembleDebug` succeeds.
- [ ] Install: `adb install -r app-debug.apk` succeeds.
- [ ] Smoke run: launch app + basic interaction without crash.
- [ ] Live log monitoring during validation: `adb logcat --pid=<pid> *:V`.
- [ ] If anything fails: record **repro steps**, **log excerpt**, and a **fix** before continuing.
- [ ] CLI-only workflow documented and kept current: see `CLI_BUILD_AND_SIDELOAD.md`.

## Part 2: Hardware-to-Software Mapping ("dodge" profile)
### Deliverable
- [ ] `DODGE_PROFILE.md` mapping each focal-equivalent mode to:
  - cameraId(s) + physicalCameraId(s) (if logical)
  - sensor role + constraints (RAW/HDR/OIS/macro)
  - crop metadata strategy for 35/50/85mm modes

### Target mapping table (from spec)
- [ ] 15mm: Samsung S5KJN5 - Ultra-wide / Macro (🌷 Super Macro)
- [ ] 23mm: Sony LYT-808 - Main wide (LBMF, DCG-HDR, RAW12)
- [ ] 35mm: Sony LYT-808 - Street crop (1.5x + DefaultUserCrop metadata)
- [ ] 50mm: Sony LYT-808 - Standard crop (2.2x + center-weighted metering)
- [ ] 73mm: Sony LYT-600 - Tele (3x periscope, OIS, LBMF)
- [ ] 85mm: Sony LYT-600 - Portrait crop (1.16x + Eye-AF priority)
- [ ] 21mm: Sony IMX615 - Front (32MP, 4K/60 RAW, **zero beauty**)

### V&V gates (before implementing engine behavior that depends on this mapping)
- [ ] Confirm camera count/IDs + which are logical vs physical (from probe).
- [ ] Confirm focal length clusters match intended roles (ultra-wide vs wide vs tele vs front).
- [ ] Confirm macro capability + min focus distance / mode switch behavior.

## Part 5: Phase 0 — Setup & Capability Probe (Immediate) (DONE, iterate as needed)
### Deliverable
- [x] `CameraCapabilitiesProbe.kt` exists and exports Markdown.
- [x] `PROBE_RESULTS.md` populated from on-device export.

### Required probe output expansions (must complete to unblock Phase 1)
- [ ] Log vendor tags relevant to:
  - LBMF / MFHDR paths
  - DCG-HDR (HDR DCG) paths
  - Android 16 hybrid AE / 10-bit/HDR pipeline support
- [ ] Dump typed values (not just key names):
  - `android.request.availableCapabilities`
  - `android.request.availableDynamicRangeProfiles` + `android.request.recommendedTenBitDynamicRangeProfile`
  - `android.scaler.streamConfigurationMap` (preview sizes + FPS ranges; identify 120fps candidates)
  - RAW outputs (sizes/formats; stall characteristics)
  - face detect modes (confirm `STATISTICS_FACE_DETECT_MODE_FULL` availability)

### Phase 0 V&V gate (must pass before Phase 1)
- [x] Vendor request/session keys are present in `PROBE_RESULTS.md` (confirmed).
- [ ] Probe answers: **which cameraId supports 120fps preview** and at what size/format.
- [ ] Probe answers: **RAW12 capture** feasibility per camera mode (wide/tele/ultra-wide/front).

## Part 3: Engineering & Imaging Pipeline
### Imaging profiles (must be implemented + testable)
- [ ] **Standard Pro (default)**:
  - Lossless-compressed DNG
  - 10-bit AVIF (HDR)
  - Display P3 gamut
- [ ] **Ultra-Max**:
  - Uncompressed RAW12 DNG
  - 12-bit JPEG XL (`.jxl`)
  - Rec. 2020 gamut

### Sensor stability protocol (must be implemented + testable)
- [ ] 30ms haptic delay:
  - fire electronic shutter
  - await sensor readout completion
  - fire "tick" haptic
- [ ] Video tally:
  - solid red frame border around preview
  - **disable all haptics** during video start/stop

### Advanced metering & AF (must be implemented + testable)
- [ ] Highlight-weighted metering: protect 95th percentile luma (Ricoh GR style).
- [ ] Sony-style Eye-AF:
  - green micro-rectangles over pupils
  - requires `STATISTICS_FACE_DETECT_MODE_FULL` (and/or best-available fallback)
- [ ] Nikon-style 3D tracking: pattern-based persistence for occlusions.
- [ ] Exposure bracketing (BKT):
  - 3/5/7 frame RAW12 sequences
  - grouping metadata (GroupingID)

## Phase 1 — The Imaging Engine (Part 5)
### Deliverable
- [ ] Preview + capture engine with:
  - 120fps preview on supported path(s)
  - RAW12 DNG saving
  - NDK pipeline callable from Kotlin

### Work items
- [ ] Implement `CameraDevice` + `CaptureSession` targeting 120fps preview where supported.
- [ ] RAW12 DNG saver (lossless + uncompressed modes per imaging profile).
- [ ] NDK pipeline integration:
  - [ ] `libavif` path (10-bit AVIF HDR)
  - [ ] `libjxl` path (12-bit JXL)
- [ ] Implement the 30ms haptic delay logic.

### V&V gate (must pass before Phase 2)
- [ ] On device: 10 consecutive captures without session death.
- [ ] Verify:
  - Standard Pro outputs: DNG (lossless) + AVIF (10-bit HDR)
  - Ultra-Max outputs: DNG (uncompressed RAW12) + JXL (12-bit)
- [ ] Logcat: no repeating Camera2 errors during preview/capture loop.

## Phase 2 — Professional HUD & Dial (Part 5)
### Deliverable
- [ ] Pro HUD + dial usable during live preview.

### Work items
- [ ] Rotary command dial: **M / H / S / BKT**.
- [ ] Video tally (solid red border) + Sony-style timecode `00:00:00:00`.
- [ ] `Settings > HUD` granular element toggles.

### V&V gate (must pass before Phase 3)
- [ ] Mode transitions are deterministic and logged (no hidden state).
- [ ] No UI-induced capture regressions (preview remains stable).

## Part 4: UX & Heritage "About" page (must be satisfied by Phase 3)
- [ ] Typography:
  - JetBrains Mono for all technical readouts
- [ ] Visual feedback colors:
  - Hasselblad Orange photo button: `#FF5C00`
  - Record red video button: `#E00000`
- [ ] About page tribute block (monospaced text, **exact content**):
  - [ ] Text matches the spec verbatim:

```
SONY: For the relentless pursuit of speed and the intelligence of the "sticky" Eye-AF.
RICOH: For the "Snap Focus" philosophy and the courage to protect the highlights.
OLYMPUS: For the pioneering "Super Macro" and the soul of the compact professional tool.
HASSELBLAD: For the legendary Natural Colour Solution and the iconic Orange shutter.
CANON & NIKON: For the gold standard of focus bracketing, 3D tracking, and the unwavering reliability of the professional instrument.
```

### V&V gate (Part 4)
- [ ] All technical readouts use JetBrains Mono (verify visually + font resource present).
- [ ] Buttons render with exact hex colors (pixel check / screenshot inspection).
- [ ] Tribute block is monospaced and copy-paste matches exactly (including punctuation/quotes).

## Phase 3 — The Street Polish (Part 5)
### Deliverable
- [ ] Street interaction model + macro lock + heritage page.

### Work items
- [ ] Tap-to-shoot:
  - lock AF/AE on DOWN
  - fire on UP
- [ ] Finalize 🌷 Super Macro hardware lock behavior.
- [ ] Implement Heritage About page per Part 4.

### V&V gate (release readiness)
- [ ] 15-minute on-device session: preview, mode changes, capture, export; no crash.
- [ ] "Dodge profile" decisions trace back to `PROBE_RESULTS.md` and/or `DODGE_PROFILE.md`.

## Scope control gates
- No engine/UI behavior that depends on unknown capabilities until Phase 0 typed values are captured.
- Every vendor-tag use must be guarded (feature detection + safe fallback).




