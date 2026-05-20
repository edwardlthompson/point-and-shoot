# Point & Shoot

---

## STOP — capture / ADB automation (read before changing preview or RAW stills)

**Do not** tie **`automationSuppressFacePipeline`** to **`pns_preview_raw_count`** / sequential RAW-only automation. Suppressing the face/YUV analysis path forced **`wantYuv=false`** on the H-dial preview session and broke RAW still session create on real hardware (e.g. **CPH2655 / OnePlus 13 class stacks**): `SESSION_CREATE_THROW` / `CAMERA_DISCONNECTED`. **Rule:** `automationSuppressFacePipeline` is for **bracket** automation only; sequential RAW-only must keep the **same preview stream wiring as in-app H** so scripted still capture matches user capture. After any change under `PreviewEngineScreen.kt` / `RawCaptureSupport.kt` affecting stills or sessions, run **`scripts/pns_photo_capture_verify.ps1`** (or **`pns_capture_pipeline_verify.ps1`**) on USB. See **`AGENTS.md`** (capture automation warning), **`BUILD_PLAN.md`** item **11**, and **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** (top warning).

**Bulk “restore everything” from the bisect checklist without per-step USB proof is unsafe.** On **CPH2655** (`8bf09993`, May 2026), re-enabling **§4a** (API 33+ stream-use-case tags on the REGULAR session) alone caused **RAW still timeouts**; re-applying Milestone **10.1** **§2** RAW10-before-RAW_SENSOR order produced **RAW10** captures that **`DngCreator`** refused (**unsupported format 37**). **§1** (still **PreviewStabilization**) and **§5** (**PreviewPostRawSensitivity**) were restored **with** a green **`pns_photo_capture_verify`** only while **§4a** and the **§2** bisect tier order stayed reverted. See **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8 for the incremental proof table.

**Future agents — do not merge without USB proof if you change:**

| Area | Avoid |
|------|--------|
| `PreviewEngineScreen.kt` REGULAR `createSession` | Turning **`streamHints`** back to **`SDK_INT >= TIRAMISU`** (§4a “restore”) — **RAW still timeout** + **`onError` 4** on this fleet. |
| `RawCaptureSupport.kt` **`Default`** tier | **RAW12 → RAW10 → RAW_SENSOR** (Milestone 10.1 order) — **`DngCreator` format 37** failure on this fleet; keep **RAW_SENSOR before RAW10** unless DNG path + device gate prove otherwise. |
| `DngMetadataResolver.kt` / RAW still DNG save in `PreviewEngineScreen.kt` | **Hybrid** physical `CameraCharacteristics` + **logical** `TotalCaptureResult` when **`physicalCameraTotalResults`** omits the picked id; removing **`DngMetadataResolution`** / **`dng save diag`** without USB proof. See **`AGENTS.md`** CRITICAL “DNG metadata pairing” and **`.cursor/rules/dng-logical-multicam-metadata-lock.mdc`**. |
| `BackCameraRoleResolver.kt` / `SensorCropGeometry.kt` / tele **`resolveFocalMmSlot`** | **Fleet-style dual policy**, **`longTele`** routing for **150 mm**, or **logical-first** tele when **physical tele is in `cameraIdList`** — breaks **85/150** digital crops vs **73** on dodge; see **`AGENTS.md`** CRITICAL “Dodge tele focal slots”. Verify with **`pns_chrome_ux_gate.ps1 -FocalMmSlot 150`**. |
| Bisect doc §1–§5 | **All hunks at once** — use **per-hunk** **`pns_photo_capture_verify.ps1`**; see **`AGENTS.md`** (§4a / §2 CRITICAL) and **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8 “What agents must avoid”. |

---

[![Toolchain verify](https://github.com/edwardlthompson/point-and-shoot/actions/workflows/toolchain-verify.yml/badge.svg?branch=main)](https://github.com/edwardlthompson/point-and-shoot/actions/workflows/toolchain-verify.yml)
[![Plan doc verify](https://github.com/edwardlthompson/point-and-shoot/actions/workflows/plan-doc-verify.yml/badge.svg?branch=main)](https://github.com/edwardlthompson/point-and-shoot/actions/workflows/plan-doc-verify.yml)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A FOSS pro camera app for the **OnePlus 13 (`dodge`)** running **LineageOS 23 (Android 16 / API 36)**.

> Predictable controls. RAW-first workflows. A HUD designed for shooting. **No proprietary blobs. No Google Play Services.**

- **Stack**: Kotlin 2.1+, Jetpack Compose, Camera2, NDK (C++23), CMake
- **Output formats** (planned): DNG (RAW12), AVIF (10-bit HDR), JPEG XL (12-bit)
- **Min / target / compile SDK**: 28 / 36 / 36
- **License**: Apache-2.0
- **Constraint**: zero proprietary binaries, no Google Play Services dependencies

## Why this exists

- **Speed and determinism** — camera behavior you can learn and trust.
- **Modern formats** — RAW (DNG) plus AVIF/JXL targets for high-quality stills.
- **Device-specific excellence** — a "dodge" profile tuned to the OnePlus 13 camera stack instead of trying to be everything for everyone.

## Highlights

- **Comprehensive Camera2 capability probe**
  - Deep characteristics, session-configuration matrix, HDR/DCG runtime, capture latency, RAW + dynamic-range exclusivity, burst / AE bracket, logical-vs-physical, exhaustive HFR + encoder matrix, legacy Camera1 sanity.
  - JSON artifacts that survive on-device runs and a Markdown export for review.
  - **Milestone 10.1:** the Markdown export also lists **RAW12 / RAW10 / RAW_SENSOR** stream sizes (with min frame duration), **`rawPickEffective=`** (aligned with `RawCaptureSupport.pickRawOutput`), **HFR rollup** lines, and a versioned **shallow fleet JSON** block — treat those exports plus **`hfr-runs/`** pulls as canonical per-device numbers, not chat-only summaries.
- **Portrait preview engine (locked chrome)** — **7×3** quick grid, **3:4** finder, dodge tele **73 / 85 / 150 mm** on physical mid-tele when enumerated; GLES external-OES preview with per-mode LUTs, optional focus peaking (M dial + video), horizon line, histogram / zebra aids. Layout contract: [`docs/preview-chrome-layout-style-guide.md`](docs/preview-chrome-layout-style-guide.md).
- **Stills (M13)** — ProShot-style **DNG** via framework `DngCreator` on OP13 leaf cameras; optional **ZSL** / **HDR still** (3× DNG bracket); fleet **`FleetCameraProfile`** + openability gates; aux UW/tele color still under human ACR review (**[`BUILD_PLAN.md`](BUILD_PLAN.md)** **H.7**).
- **Encoded video (M12 + 13V)** — In-app **MP4** (`MediaRecorder` ≤60 fps, **MediaCodec** for HFR / 10-bit / DCG); unified **format picker**; DCG + HDR10 metadata path; power-button quick-launch; macro dial; timecode + audio meters; RGB histogram; focus peaking; GLES **video LUT** preview; battery/thermal + **storage-remaining** HUD on video modes.
- **RAW video lane (M13.6)** — MCRAW-class **`.mcraw`** (`PNMRAWV1`) on OP13 leaf cameras when HUD **RAW** lane is enabled — not a gallery-playable MP4.
- **Host orchestration** (`scripts/pns_hfr_autorun.ps1`)
  - Build → install → grant camera → run any subset of probes → pull JSON → write a suite-summary file → optional Phase 9 thermal snapshot.
- **Toolchain gate** (`scripts/pns_verify_toolchain.ps1`)
  - Gradle `assembleDebug`, UTF-8 enforcement on Kotlin and PowerShell, PowerShell parser sanity. Mirrored in CI on Ubuntu (`.github/workflows/toolchain-verify.yml`).
- **CLI-only workflow** — every step runs from PowerShell + ADB; Android Studio is optional.

## Imaging-engine targets (roadmap vs shipped)

| Area | Shipped (reference: **CPH2655** USB gates) | Still planned / partial |
|------|-------------------------------------------|-------------------------|
| **Stills** | DNG (RAW_SENSOR-first on OP13 leaf); hardware JPEG companion; ZSL / HDR still modes; bracket bursts | Full **AVIF** / **JPEG XL** still encode bodies (NDK path stubbed); Ultra-Max profile polish |
| **Video** | H.264/HEVC MP4; HFR via MediaCodec; 10-bit + DCG HDR10; RAW `.mcraw` lane; LUT on preview; smile still + bitrate scale (**13V.17**) | LUT baked into encoded MP4 |
| **HUD / metering** | Highlight-weighted meter, eye-AF overlay, face track boxes, readout strip, command dial **A/M/H/S/BKT/Macro** | Nikon-style 3D tracking persistence |
| **LUTs & color** | Built-in + imported `.cube`; GLES preview + still CPU path; calibration screen | Full DNG matrix injection from calibration export |
| **Haptics** | Still capture haptics; video tally without record haptics | — |

Probe outputs still gate **new** OEM keys and fleet expansion — see [`PROBE_BUILD_PLAN.md`](PROBE_BUILD_PLAN.md).

## Status

| Milestone | State | Notes |
|-----------|--------|--------|
| **Phase 0 / probes** | ✅ | JSON + Markdown export; `hfr-runs/` artifacts |
| **M10** (product expansion) | ✅ Gate passed | Shallow cache, focal strip, JPEG-only profile, Photo\|Video tray, 7×3 grid, QR scan, … → [`BUILD_PLAN_COMPLETED.md`](BUILD_PLAN_COMPLETED.md) |
| **M11** (capture UX) | ✅ Gate passed | WB menu order, dodge tele crops, in-app video + RES selector |
| **M12** (video completeness) | ✅ Gate passed | Audio policy, `VideoRecordingController`, MediaCodec HFR path |
| **M13** (fleet RAW) | 🚧 **13.7** (automated ✅) | USB gates **PASS** on `8bf09993`; human **ACR 3/3** + visual aux parity → **H.7** |
| **M13V** (video expansion) | ✅ USB-verified | **13V.1–13V.18** incl. 4K@120, AI features, CameraX probe — see table below |

**Latest pre-release:** [`v0.13.0-beta.1`](https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.13.0-beta.1) — APK `Point-and-Shoot_0.13.0-beta.1.apk` · notes [`RELEASE_NOTES_v0.13.0-beta.1.md`](RELEASE_NOTES_v0.13.0-beta.1.md)

**Active roadmap:** [`BUILD_PLAN.md`](BUILD_PLAN.md) · **Archive:** [`BUILD_PLAN_COMPLETED.md`](BUILD_PLAN_COMPLETED.md) · **Changelog:** [`CHANGELOG.md`](CHANGELOG.md)

### Shipped video & preview features (M13V.1–13V.18)

| Sprint | Feature | Verify script (USB) |
|--------|---------|---------------------|
| **13V.1** | Power button / QS tiles → preview | `pns_power_button_gate.ps1` |
| **13V.15** | HEVC MediaCodec capability matrix (`PNS.VideoCapProbe`) | `pns_video_capability_probe.ps1` |
| **13V.2–4** | MediaCodec 10-bit + HFR + format picker | `pns_mediacodec_hfr_verify.ps1` |
| **13V.5** | DCG session + HDR10 metadata | `pns_video_hdr10_metadata_verify.ps1` |
| **13V.6** | Macro command dial | `pns_macro_focus_verify.ps1` |
| **13V.8** | Timecode + audio meters | `pns_recording_overlays_verify.ps1` |
| **13V.9** | RGB histogram | `pns_rgb_histogram_verify.ps1` |
| **13V.10** | Focus peaking (M dial video) | `pns_focus_peaking_verify.ps1` |
| **13V.11** | Video LUT on GLES preview | `pns_video_lut_preview_verify.ps1` |
| **13V.12** | Battery + thermal HUD | `pns_power_thermal_verify.ps1` |
| **13V.13** | Storage minutes remaining | `pns_storage_remaining_verify.ps1` |
| **13V.16** | 4K@120 encode unlock (HFR MediaCodec) | `pns_mediacodec_hfr_verify.ps1` |
| **13V.17** | Smile still, scene probe, bitrate scale | `pns_ai_features_verify.ps1` |
| **13V.18** | CameraX OEM extension probe | `pns_camerax_extension_probe.ps1` |

Sprint docs: [`docs/M13V_10_FOCUS_PEAKING.md`](docs/M13V_10_FOCUS_PEAKING.md) … [`docs/M13V_18_CAMERAX_EXTENSIONS.md`](docs/M13V_18_CAMERAX_EXTENSIONS.md). Agent automation index: [`AGENTS.md`](AGENTS.md).

### Core capture gates (still regression)

```powershell
.\scripts\pns_capture_pipeline_verify.ps1    # RAW still 1/1 — after session/DNG changes
.\scripts\pns_photo_capture_verify.ps1 -Fast # lighter still smoke
.\scripts\pns_chrome_ux_gate.ps1 -FocalMmSlot 150   # dodge tele — do not run parallel with capture verify
```

See `PROBE_BUILD_PLAN.md` for detailed device limitation documentation.

## Screenshots

Raster screenshots are **not** stored in this repository. Capture locally with `scripts/pns_device_screencap.ps1` after installing on a device. Historical validation narrative remains in `PROBE_BUILD_PLAN.md` §5.

## Quickstart

> Prereqs: Android SDK + ADB on PATH, JDK 17 (Android Studio's `jbr` works), and a connected OnePlus 13 with camera permission grantable. **Android Studio is not required.**

End-to-end build + sideload (PowerShell):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n dev.pointandshoot/.MainActivity
```

Detailed CLI flow (no Android Studio): see [`CLI_BUILD_AND_SIDELOAD.md`](CLI_BUILD_AND_SIDELOAD.md).

Probe automation (Windows, ADB-attached device):

```powershell
.\scripts\pns_hfr_autorun.ps1 -OutDir .\hfr-runs -RunProbeSmoke -Sideload
.\scripts\pns_hfr_autorun.ps1 -OutDir .\hfr-runs -RunCoreProbePlan -ExhaustiveHfrOnly -MaxRuns 1
.\scripts\pns_hfr_autorun.ps1 -OutDir .\hfr-runs -RunFullSuite -ExhaustiveHfrOnly -MaxRuns 1
```

Toolchain gate (run after Kotlin / PowerShell changes):

```powershell
.\scripts\pns_verify_toolchain.ps1                # full
.\scripts\pns_verify_toolchain.ps1 -SkipGradle    # docs-only
.\scripts\pns_verify_toolchain.ps1 -RunTests      # full + JVM unit tests
```

## Documentation

### Product
- **Product roadmap & V&V gates** — [`BUILD_PLAN.md`](BUILD_PLAN.md) · completed milestones — [`BUILD_PLAN_COMPLETED.md`](BUILD_PLAN_COMPLETED.md)
- **Probe automation plan** — [`PROBE_BUILD_PLAN.md`](PROBE_BUILD_PLAN.md)
- **OnePlus 13 hardware-to-software mapping** — [`DODGE_PROFILE.md`](DODGE_PROFILE.md)
- **Latest probe export** — [`PROBE_RESULTS.md`](PROBE_RESULTS.md)
- **Fleet RAW / DNG policy** — [`docs/FLEET_ONEPLUS13_RAW_POLICY.md`](docs/FLEET_ONEPLUS13_RAW_POLICY.md) · [`docs/DNG_OPENABILITY_REGRESSIONS.md`](docs/DNG_OPENABILITY_REGRESSIONS.md)
- **Video (DCG / RAW lane)** — [`docs/M13_4_DCG_SESSION.md`](docs/M13_4_DCG_SESSION.md) · [`docs/M13_6_RAW_VIDEO.md`](docs/M13_6_RAW_VIDEO.md)
- **Preview chrome layout (locked)** — [`docs/preview-chrome-layout-style-guide.md`](docs/preview-chrome-layout-style-guide.md)

### Engineering
- **Capture engine architecture** (threading, backpressure, cancellation) — [`CAPTURE_ARCHITECTURE.md`](CAPTURE_ARCHITECTURE.md)
- **Performance budgets** (per mode FPS / latency / cold start) — [`PERFORMANCE_BUDGETS.md`](PERFORMANCE_BUDGETS.md)
- **Storage strategy** (MediaStore vs SAF vs app-private; per-profile destinations) — [`STORAGE_STRATEGY.md`](STORAGE_STRATEGY.md)
- **NDK pipeline plan** (libavif + libjxl + JNI surface + fallback) — [`NDK_PLAN.md`](NDK_PLAN.md)
- **Color pipeline & LUTs** (sensor → demosaic → WB → CCM → tone → LUT → encode; calibration mode flow; pinned numbers) — [`COLOR_PIPELINE.md`](COLOR_PIPELINE.md)
- **Failure matrix** (graceful-degradation policy, severity-tagged) — [`FAILURE_MATRIX.md`](FAILURE_MATRIX.md)
- **Third-party license inventory** (FOSS hygiene) — [`LICENSES.md`](LICENSES.md)
- **CLI build / sideload** — [`CLI_BUILD_AND_SIDELOAD.md`](CLI_BUILD_AND_SIDELOAD.md)

### Releases
- **Latest beta** — [`v0.13.0-beta.1`](https://github.com/edwardlthompson/point-and-shoot/releases/tag/v0.13.0-beta.1) · [`RELEASE_NOTES_v0.13.0-beta.1.md`](RELEASE_NOTES_v0.13.0-beta.1.md)
- **Changelog** — [`CHANGELOG.md`](CHANGELOG.md)
- **Release-notes template** — [`RELEASE_NOTES_TEMPLATE.md`](RELEASE_NOTES_TEMPLATE.md)
- **Local release-signing config** — [`keystore.properties.example`](keystore.properties.example)
- **CLI signed builds** — [`CLI_BUILD_AND_SIDELOAD.md`](CLI_BUILD_AND_SIDELOAD.md)

#### Updates without Google Play (users)

**Package ID:** `dev.pointandshoot`

| Channel | What to use |
|--------|----------------|
| **GitHub Releases** | [Releases](https://github.com/edwardlthompson/point-and-shoot/releases) — install the signed APK attached to each release (sideload / `adb install -r`). |
| **[Obtainium](https://github.com/ImranR98/Obtainium)** | Add the repository URL **`https://github.com/edwardlthompson/point-and-shoot`** so updates track **GitHub Releases** assets. If maintainers mark drops as **Pre-release** only, turn on **Include prereleases** for this entry in Obtainium. Quick add on device: `obtainium://add/github.com/edwardlthompson/point-and-shoot`. For bulk paste import (one URL per line), see [`scripts/obtainium-sources.txt`](scripts/obtainium-sources.txt). |
| **F-Droid** | Inclusion is **planned** (FOSS-only stack aligns with project rules). Metadata placeholders live under [`metadata/`](metadata/README.md); submission follows the [official inclusion process](https://f-droid.org/docs/Including_an_App/) (typically a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata)). Until it is listed, use GitHub Releases or Obtainium. |

#### Publishing checklist (maintainers — GitHub ↔ Obtainium ↔ F-Droid prep)

1. Bump **`versionCode`** / **`versionName`** in [`app/build.gradle.kts`](app/build.gradle.kts).
2. Summarize user-visible changes in [`CHANGELOG.md`](CHANGELOG.md) (and optional [`RELEASE_NOTES_TEMPLATE.md`](RELEASE_NOTES_TEMPLATE.md)).
3. **Signed APK:** push a Git tag matching **`v*`** to run [`.github/workflows/build-signed.yml`](.github/workflows/build-signed.yml) (requires repo **Actions secrets** for the release keystore — see workflow header). The workflow uploads a **`pns-release-apk-*`** artifact. Alternatively build locally with real signing via `keystore.properties` (see [`CLI_BUILD_AND_SIDELOAD.md`](CLI_BUILD_AND_SIDELOAD.md)).
4. **GitHub Release:** create a release for that tag and **attach the signed `app-release.apk`** (or a clearly named renamed APK) so Obtainium and sideload users download the same binary. Keep signing keys stable across releases so in-place upgrades work.
5. **F-Droid (when ready):** tagged source, reproducible/recipe-friendly builds, and metadata in [`metadata/`](metadata/README.md) — coordinate with F-Droid’s [build server docs](https://f-droid.org/docs/Build_Server_Setup/) and reviewer feedback.

## Repo layout

- `app/` — Android app (Compose + Camera2 probe + Pro HUD scaffolds + capture-engine helpers)
  - `app/src/main/java/dev/pointandshoot/` — production Kotlin (`PnsTheme`, `CommandDial`, `ProHudScreen`, `LutChipRow`, `LutImporterScreen`, `ImportedLutStore`, `CalibrateScreen`, `BitmapRgbPlane`, `GLPreviewScreen`, `LutPreviewRenderer`, `TestPattern`, `NativeEncoders`, `EncoderRoute`, `NativeDiagnosticsScreen`, `RootCapability`, `RootCapabilityProbe`, `RootSettingsScreen`, `HdrCurves`, `ColorSpaceMatrix`, `WorkingSpace`, `AvifColrPayload`, `HdrStaticMetadata`, `IsobmffSampleAspect`, `AvifAuxiliaryBoxes`, `IsobmffBox`, `ItemPropertyAssociation`, `IsobmffItemProperties`, `PrimaryItemBox`, `ItemInfoEntry`, `ItemInfoBox`, `HandlerReferenceBox`, `ItemLocationBox`, `MetaBox`, `FileTypeBox`, `MediaDataBox`, `ImageSpatialExtents`, `AvifStillMuxer`, `Av1CodecConfiguration`, `ItemReferenceBox`, `AvifAuxiliaryTypeProperty`, `AvifImageGrid`, `AvifImageOverlay`, `LensInfoSummary`, `LensInfoExtractor`, `PreviewLumaHistogram`, `DngLutMetadata`, `Dng12Saver`, `DngColorTags`, `CaptureStorage`, `CaptureHaptics`, `BracketPlan`, `BracketScheduler`, `HighlightMeter`, `EyeAfOverlay`, `FaceDetectAdapter`, `TrackerState`, `CropPlan`, `CapabilityGate`, `EncoderResultAggregator`, `EncoderAttemptJsonAdapter`, `EncoderRecipeBuilder`, `PerfBudget`, `PnsLog`, `VendorKeyGuard`, `DiagnosticsMode`, `AboutScreen`, `HudSettings`, `Lut3D`, `LutPipeline`, `LutShaderProgram`, `LutImportValidator`, `BuiltInLuts`, `LutCatalog`, `LutSidecar`, `LutSidecarWriter`, `CalibrationProfile`, `CalibrationProfileJsonAdapter`, `CalibrationProfileStorage`, `CalibrationMath`, `CalibrationToLut`, `ReferenceTarget`, `BundledReferenceTargets`, `CalibrationSampler`, `SlantedEdgeMtf`, `ColorMath`, `LutCreditsBuilder`, `LutDiagnosticsBuilder`, ...)
  - `app/src/main/assets/shaders/` — GLES 3.0 shader assets (`lut_apply.vert.glsl` + `lut_apply.frag.glsl` for the live-preview / video LUT apply path).
  - `app/src/main/assets/fonts/jetbrainsmono/` — vendored JetBrains Mono Regular `v2.304` (SIL OFL 1.1) license + provenance metadata (`LICENSE.txt`, `SOURCE.txt`, `SHA256.txt`); the matching `.ttf` lives at `app/src/main/res/font/jetbrainsmono_regular.ttf`.
  - `app/src/test/java/dev/pointandshoot/` — pure-JVM unit tests (`BracketPlanTest`, `BracketSchedulerTest`, `HighlightMeterTest`, `TimecodeFormatTest`, `CaptureStorageFilenameTest`, `CropPlanTest`, `FaceDetectAdapterTest`, `TrackerStateTest`, `CapabilityGateTest`, `EncoderResultAggregatorTest`, `EncoderAttemptJsonAdapterTest`, `EncoderRecipeBuilderTest`, `PerfBudgetTest`, `PnsLogTest`, `Lut3DTest`, `LutPipelineTest`, `LutShaderProgramSourceTest`, `LutPreviewRendererQuadTest`, `TestPatternTest`, `NativeEncodersFallbackTest`, `EncoderRouteTest`, `LensInfoSummaryTest`, `PreviewLumaHistogramTest`, `Dl3ParserTest`, `Spi3dParserTest`, `DngLutMetadataTest`, `RootCapabilityTest`, `RootCapabilityProbeTest`, `HdrCurvesTest`, `ColorSpaceMatrixTest`, `WorkingSpaceTest`, `AvifColrPayloadTest`, `HdrStaticMetadataTest`, `IsobmffSampleAspectTest`, `AvifAuxiliaryBoxesTest`, `IsobmffBoxTest`, `ItemPropertyAssociationTest`, `IsobmffItemPropertiesTest`, `PrimaryItemBoxTest`, `ItemInfoEntryTest`, `ItemInfoBoxTest`, `HandlerReferenceBoxTest`, `ItemLocationBoxTest`, `MetaBoxTest`, `FileTypeBoxTest`, `MediaDataBoxTest`, `ImageSpatialExtentsTest`, `AvifStillMuxerTest`, `Av1CodecConfigurationTest`, `ItemReferenceBoxTest`, `AvifAuxiliaryTypePropertyTest`, `AvifImageGridTest`, `AvifImageOverlayTest`, `LutImportValidatorTest`, `BuiltInLutsTest`, `LutCatalogTest`, `LutSidecarTest`, `LutSidecarWriterTest`, `HudSettingsLutResolutionTest`, `ImportedLutStoreTest`, `BitmapRgbPlaneTest`, `CalibrationMathTest`, `CalibrationToLutTest`, `CalibrationProfileJsonAdapterTest`, `CalibrationProfileStorageTest`, `CalibrationCcmAccuracyTest`, `DngColorTagsTest`, `ReferenceTargetTest`, `CalibrationSamplerTest`, `SlantedEdgeMtfTest`, `ColorMathTest`, `LutCreditsBuilderTest`, `LutDiagnosticsBuilderTest`, `MetadataSerializationGoldenTest`)
- `native/` — NDK / JNI stubs + CMake skeleton + license matrix for the planned libavif / libjxl pipeline (`pns_native.cpp` JNI stubs matching `NativeEncoders`; `CMakeLists.txt` with commented `FetchContent_Declare` blocks; `THIRD_PARTY.md` license matrix; `README.md` Phase-0 layout)
- `metadata/` — F-Droid compliance placeholders
- `scripts/` — PowerShell automation: **`pns_verify_toolchain.ps1`**, **`pns_hfr_autorun.ps1`**, capture gates (**`pns_capture_pipeline_verify.ps1`**, **`pns_photo_capture_verify.ps1`**, **`pns_aux_dng_capture_analyze.ps1`**), video gates (**`pns_mediacodec_hfr_verify.ps1`**, **`pns_in_app_video_verify.ps1`**, **`pns_video_hdr10_metadata_verify.ps1`**, **`pns_raw_video_verify.ps1`**, **`pns_focus_peaking_verify.ps1`**, **`pns_video_lut_preview_verify.ps1`**, **`pns_power_thermal_verify.ps1`**, **`pns_storage_remaining_verify.ps1`**, …), **`pns_sideload_and_launch.ps1`**, **`pns_chrome_ux_gate.ps1`**. Full index: [`AGENTS.md`](AGENTS.md).
- `hfr-runs/` — pulled probe artifacts (gitignored)
- `.github/workflows/` — CI: toolchain verify + unit tests + debug-APK artifact (Ubuntu), plan-doc verify, signed-build (manual / `v*` tag)

## Contributing

- File issues / PRs against `main`.
- Code style: Kotlin idiomatic, UTF-8 source files (Windows users: do not save as UTF-16 LE — the toolchain gate rejects it).
- Before pushing:
  - Code changes (Kotlin / PowerShell): `.\scripts\pns_verify_toolchain.ps1 -RunTests`
  - Docs-only changes: `.\scripts\pns_verify_toolchain.ps1 -SkipGradle`
- New scripts and Kotlin sources must pass the same gate. New pure-JVM logic should ship with a JUnit test under `app/src/test/java/dev/pointandshoot/`.

## License

Apache-2.0. See [`LICENSE`](LICENSE).
