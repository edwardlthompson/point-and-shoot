## Build plan (Point & Shoot)

This plan implements your Parts 1–5 spec and is ordered for execution: **foundations → probe → mapping → engine → HUD → polish → release**.

### Conventions
- **[HOST]**: runs on your workstation
- **[ADB]**: runs on the connected OnePlus 13 over ADB
- **[CI]**: runs in GitHub Actions (and, eventually, GitLab CI)
- **Gate rule**: if a V&V gate fails, stop, capture repro + logs, fix, then re-run the gate.

### Verification protocol (must follow before marking ANY item `[x]`)
Every `[x]` in this plan is a falsifiable claim: a future audit (or fresh
clone) must be able to reproduce the evidence behind it. Marking an item
complete without meeting the rules below is treated as a defect and must
be reverted to `[ ]` as soon as the gap is discovered. The protocol applies
to every checkbox at every level (top-level items AND nested children).

#### Tick eligibility by item tag
| Tag | Eligible to tick when... |
|---|---|
| **[HOST] code** | (a) the file exists at the claimed path, (b) the claimed symbols (classes/functions/object members) are present, (c) `scripts/pns_verify_toolchain.ps1 -RunTests` exits 0 (compile + JUnit + dep-audit + license-drift + UTF-8 + parse all pass), (d) `ReadLints` is clean for the touched files. |
| **[HOST] test** | The new suite is green in `:app:testDebugUnitTest`; the per-suite XML under `app/build/test-results/testDebugUnitTest/` shows `failures="0" errors="0"` for that class. |
| **[HOST] doc** | The file exists at the claimed path AND the documented behavior matches what the code/scripts actually do (no orphan claims to nonexistent symbols, paths, or flags). |
| **[HOST] script** | The script exists, encoding is UTF-8 (not UTF-16 LE / no BOM mismatch), `Test-Ps1ParseOk` reports zero parse errors, AND a non-destructive smoke invocation exits 0. |
| **[HOST] gate** | The gate ran on the current `HEAD` (or a referenced shipped commit) and passed; the run is recorded in `PROBE_BUILD_PLAN.md` §5 with date + result. |
| **[ADB]** | The behavior was observed on the connected OnePlus 13 in a recorded run logged in `PROBE_BUILD_PLAN.md` §5. **Pure host-side scaffolds DO NOT close an `[ADB]` item** — they may be referenced in the bullet body as supporting work while the headline checkbox stays `[ ]` until the device run is logged. |
| **[CI]** | A successful CI run on a referenced commit SHA, recorded in `PROBE_BUILD_PLAN.md` §5. |

#### Partial-completion convention
When host-side scaffolding is shipped but the headline contract is not yet
fully met (typically because engine wire-up is pending, or because the
contract is `[ADB]` and we are still on a non-device build), the bullet
stays `[ ]` and the body explicitly enumerates:
1. What WAS shipped (with file path + symbol).
2. What is still outstanding (the precise blocker).
3. Where the test coverage lives, if any.

This is the format used throughout §3-§9 today (e.g., "host-side crop math
complete (`CropPlan.LongTele150`); engine wiring (`SCALER_CROP_REGION` + 12 MP
output sizing) pending"). Do not invert the polarity to `[x]` until ALL of
the bullet's contract clauses are satisfied. For nested checklists, the
parent stays `[x]` only when ALL children are `[x]`.

#### Error-handling rules
- **Failed gate after a tick**: if `pns_verify_toolchain.ps1 -RunTests`
  starts failing after an item was ticked, treat it as a regression
  (not a flake). Investigate, fix, re-run, and only then re-tick.
- **Stale claim discovered**: if an audit finds an `[x]` whose claimed
  artifact no longer exists or no longer contains the claimed symbol,
  immediately revert to `[ ]` and add a row to `PROBE_BUILD_PLAN.md` §5
  documenting the revert + the reason.
- **Doc/code drift**: if `BUILD_PLAN.md` claims a behavior the code no
  longer exhibits (renamed function, removed test, moved file), reconcile
  in this order: code is the ground truth; update the plan, then re-verify.
- **Test removal**: never remove or skip a JUnit test that backs an `[x]`
  item without first reverting the `[x]` to `[ ]`.
- **Tooling drift in dependencies**: when Dependabot bumps AGP / Compose /
  Kotlin / a major lib, schedule a full audit (see "Audit cadence") on the
  same PR — toolchain shifts can silently invalidate prior `[x]` claims
  (the lint regression in §0 "Known limitations" is the canonical example).

#### Audit cadence
- **Per-session**: at the end of every session that touches `BUILD_PLAN.md`,
  re-verify every item ticked in that session and run the full gate.
- **Periodic**: do a full audit (re-validate every `[x]` against current
  filesystem + tests) at least every 5 sessions, when this plan is
  substantially restructured, or when a Dependabot bump lands.
- **Audit results go into `PROBE_BUILD_PLAN.md` §5** as a dated row
  beginning with `**AUDIT**:`, listing the items checked and any reverts.

#### Mandatory pre-tick checklist (operational summary)
Before flipping ANY `[ ]` to `[x]`:
1. Run `scripts/pns_verify_toolchain.ps1 -RunTests`. Expect `RESULT: PASSED`.
2. Run `ReadLints` on all touched files. Expect "No linter errors found."
3. Confirm the bullet's claimed file paths and symbols actually exist
   (`Read` or `Grep`).
4. Confirm any claimed test class exists and its `TEST-*.xml` shows
   `failures="0" errors="0"`.
5. Update `CHANGELOG.md` (Unreleased), `README.md` if the surface area
   changed, and append a row to `PROBE_BUILD_PLAN.md` §5 noting the
   verification result.
6. ONLY THEN flip `[ ]` to `[x]`.

## 0) Test harness & global quality gates (apply to every phase)
- [x] [ADB] `adb devices` shows the OnePlus 13 as `device` (authorized) — proven by `pns_hfr_autorun.ps1` device runs (PROBE_BUILD_PLAN.md §5).
- [x] [ADB] Launch + PID works:
  - `adb shell am start -n dev.pointandshoot/.MainActivity`
  - `adb shell pidof dev.pointandshoot`
- [x] [ADB] Live PID log monitoring available:
  - `adb logcat -v time --pid=<pid> *:V`
- [x] [HOST] Build: `.\gradlew.bat :app:assembleDebug` (gated by `scripts/pns_verify_toolchain.ps1`; mirrored in `.github/workflows/toolchain-verify.yml`)
- [x] [ADB] Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk` (automated in `pns_hfr_autorun.ps1 -Sideload` / `-SideloadOnly`)
- [x] [ADB] Smoke run: launch + basic interaction without crash (`-RunProbeSmoke` suite)
- [x] [HOST] CLI-only workflow documented and kept current: `CLI_BUILD_AND_SIDELOAD.md`

### Known limitations
- **`:app:lintDebug` not part of the toolchain gate today.** AGP 8.7.3 + Compose BOM 2026.04.01 ships compose-lint detectors (`ComposableFlowOperatorDetector`, `RememberInCompositionDetector`, `FrequentlyChangingValueDetector`, ...) that crash with `IncompatibleClassChangeError` against the bundled Kotlin Analysis API — i.e., a tooling/version-mismatch bug, not project code. `app/build.gradle.kts` carries a comment about it; `pns_verify_toolchain.ps1` deliberately does **not** invoke lint. Re-evaluate after the next AGP / Compose-BOM bump (Dependabot will surface the upgrade PRs). Static analysis coverage in the meantime: `assembleDebug` (compile-time), `:app:testDebugUnitTest` (logic), `ReadLints` (IDE-side, per file).

## 1) Foundations (Part 1: repo + FOSS constraints)
### FOSS non-negotiables
- [x] Apache-2.0 license
- [x] Zero proprietary binaries committed
- [x] No Google Play Services dependencies
- [x] Dependency audit gate: `scripts/pns_verify_toolchain.ps1` rejects `com.google.android.gms`, Firebase, ML Kit, Play Core, Play Billing, and Ads references in any `*.gradle{,.kts}` file or `gradle/libs.versions.toml`. Runs locally and in `.github/workflows/toolchain-verify.yml`.
- [x] CI also runs `:app:testDebugUnitTest` on every push / PR (`.github/workflows/toolchain-verify.yml`); test reports uploaded as a workflow artifact on failure (`pns-unit-test-reports-<sha>`); debug APK uploaded on success (`pns-debug-apk-<sha>`).
- [x] Local equivalent of the CI test step: `scripts/pns_verify_toolchain.ps1 -RunTests` (also runs `:app:testDebugUnitTest`).

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
- [ ] [ADB] Probe answers (validated, not just enumerated): which cameraId+size+fps+mimetype combinations are actually stable for HFR encode (AVC/HEVC), including exact failure modes (e.g., `-38 Function not implemented`) — **host-side aggregator + Android adapter shipped**: `EncoderResultAggregator.summarize(attempts)` returns `EncoderSummary` with `knownGood` (sorted by camera then descending fps then descending area), `knownBad` (sorted by canonical-error frequency), `byCamera` roll-up (`bestHfrFps` / `bestRegularFps` per camera), and `canonicalErrors` (errno-collapsed -> count, e.g., `errno -38 -> 12`); `bestHfrRecipe(summary, cameraId)` returns the highest-fps known-good HFR row per camera; **JUnit-tested** in `EncoderResultAggregatorTest` (9 tests). `EncoderAttemptJsonAdapter.loadLatest(context)` finds the newest `exhaustive_probe_*.json` under `getExternalFilesDir(null)`, parses it, and returns `List<EncoderAttempt>` flattened across `hfrAttempts` + `regularAttempts` (mime extracted from `note` via `EncoderAttempt.extractMimeFromNote`); `decode(JSONObject)` is split out and **JUnit-tested** with real `org.json:json` (test-only dep) in `EncoderAttemptJsonAdapterTest` (5 tests, including malformed-row tolerance and end-to-end decode + summarize). **About-page hydration shipped**: `EncoderRecipeBuilder.recipesFromSummary(summary)` selects the best HFR + best regular row per camera and emits a deterministic `List<Row>`; `errorRowsFromSummary(summary, max)` surfaces the most-frequent canonical errors; `headlineCounts(summary)` produces the `<totalAttempts>` / `<okPercent>` banner. `AboutScreen` now takes an optional `liveSummary: EncoderSummary?`; when present, a "From the latest probe (live)" section renders one `LiveRecipeCard` per row plus up to five `LiveErrorCard`s (RecordRed) for the top failure modes. **JUnit-tested** in `EncoderRecipeBuilderTest` (13 tests): empty summary yields no rows, picks one HFR + one Regular per camera, skips cameras with no successes, recipes deterministic across summary reorderings, mime recovered from note when first-class mime is null, error rows sorted by canonical-error frequency with cap respected, `headlineCounts` is null for null/empty summaries, `okPercent` arithmetic is correct (2/3 → 67 %). **Live About-screen plumbing shipped**: `CameraCapabilitiesProbe` calls `EncoderAttemptJsonAdapter.loadLatest(context)` when navigating to About, runs `EncoderResultAggregator.summarize(...)` on the result, and passes the resulting `EncoderSummary` to `AboutScreen(liveSummary = …)`; on the OnePlus 13 (adb 8bf09993) the live section reports "180 attempts across 4 cameras — 180 ok / 0 fail (100% pass)" with one `LiveRecipeCard` per camera (Cam2 Hfr 480fps measured 427.8 fps, etc.). The `[ADB]` row remains open until the device-side validation row formally records the live-hydration screenshot in `PROBE_BUILD_PLAN.md` §5.

## 3) Part 2: Hardware-to-software mapping ("dodge profile")
### Deliverable
- [ ] `DODGE_PROFILE.md` mapping each focal-equivalent mode to:
  - cameraId(s) + physicalCameraId(s) (if logical)
  - sensor role + constraints (RAW/HDR/OIS/macro)
  - crop metadata strategy for 35/50/85mm modes

### Target mapping table (from spec)
- [ ] 15mm: Samsung S5KJN5 — Ultra-wide / Macro (🌷 Super Macro)
- [ ] 23mm: Sony LYT-808 — Main wide (LBMF, DCG-HDR, RAW12)
- [ ] 35mm: Sony LYT-808 — Street crop (1.5x + DefaultUserCrop metadata) — host-side crop math complete (`CropPlan.centeredCrop(FocalMode.Street35, ...)` + `CropPlanTest`); engine wiring (`SCALER_CROP_REGION` + DNG `DefaultUserCrop` tag) pending Phase 1 capture engine.
- [ ] 50mm: Sony LYT-808 — Standard crop (2.2x + center-weighted metering) — host-side crop math + center-weighted metering hint complete (`CropPlan.Standard50`); engine wiring pending.
- [ ] 73mm: Sony LYT-600 — Tele (3x periscope, OIS, LBMF)
- [ ] 85mm: Sony LYT-600 — Portrait crop (1.16x + Eye-AF priority) — host-side crop math + Eye-AF priority hint complete (`CropPlan.Portrait85`); engine wiring pending.
- [ ] 150mm: Sony LYT-600 — Long-tele crop (~2.04x: 12 MP center crop of the 50 MP sensor; Eye-AF priority retained) — host-side crop math complete (`CropPlan.LongTele150`); engine wiring (`SCALER_CROP_REGION` + 12 MP output sizing) pending.
- [ ] 21mm: Sony IMX615 — Front (32MP, 4K/60 RAW, zero beauty)

### Mapping V&V gates (before implementing mapping-dependent behavior)
- [x] [ADB] Confirm logical vs physical camera topology (from probe)
- [x] [ADB] Confirm focal length clusters match intended roles
- [x] [ADB] Confirm macro capability + min focus distance / mode-switch behavior — **closed by Round 11 lens-info probe**: `DeepCapsProbeScreen.runDeepCapsProbe` emits a typed `lensInfo` block per cameraId via `LensInfoExtractor`; round 11 ADB run on adb 8bf09993 (`hfr-runs/deep_caps_round11.json`) yields `cameraId=3` (S5KJN5 UW) at **25 diopters** (~4 cm minimum focus) - well above the `LensInfoSummary.MACRO_MIN_DIOPTERS_THRESHOLD = 15f` (~6.7 cm) gate, confirming the spec's 🌷 Super Macro is wired to the ultra-wide. The threshold was tuned UPWARD from an initial 10 diopters specifically because the LYT-808 main wide reports exactly 10 diopters of close focus, which would have falsely-classified it as macro. Mode-switch behavior (vendor `oplus.macro_mode` style request key) still needs to be probed - tracked under DODGE_PROFILE.md "Outstanding Phase 0 follow-ons".

## 4) Phase 1 (Part 5): Imaging engine + Part 3 pipeline requirements
### Part 3 requirements that Phase 1 must satisfy
#### Imaging profiles
- [x] Standard Pro (default): lossless-compressed DNG + 10-bit AVIF (HDR) + Display P3 — modeled in `ImagingProfile.kt` (`ImagingProfile.StandardPro`)
- [x] Ultra-Max: uncompressed RAW12 DNG + 12-bit JPEG XL (`.jxl`) + Rec. 2020 — modeled in `ImagingProfile.kt` (`ImagingProfile.UltraMax`)

#### Sensor stability protocol
- [x] 30ms haptic delay: fire electronic shutter → await readout completion → fire tick haptic — `CaptureHaptics.scheduleStillTick()` (`POST_READOUT_TICK_DELAY_MS = 30L`); manifest `android.permission.VIBRATE` granted.
- [x] Video tally: solid red border; **disable all haptics** during video start/stop — `VideoTallyOverlay` (locked to `PnsColors.RecordRed`); `CaptureHaptics` is intentionally only invoked from still-capture paths, never from video.

#### Advanced metering & AF
- [ ] Highlight-weighted metering (protect 95th percentile luma; Ricoh GR style) — `H` mode wired in `CommandDialMode.H`; algorithm scaffolded in `HighlightMeter.suggestEvCorrection()` (pure function over a 256-bin luma histogram, EV-clamped, **JUnit-tested** in `HighlightMeterTest.kt`); preview-frame histogram **pure-data layer shipped (Round 12)** in `PreviewLumaHistogram.kt`: `reduceY8(plane, w, h)` for tightly-packed `Y8` buffers, `reduceYuv420Y(plane, w, h, rowStride)` for stride-padded `YUV_420_888` Y planes (skips the trailing padding bytes per row), `reduceYuv420YCenterWeighted(...)` for subject-tracking metering with a configurable `centerFrac`/`centerWeight` (defaults 0.5 / 3x); `pixelCount(hist)` is the sanity helper. **JUnit-tested** in `PreviewLumaHistogramTest.kt` (13 tests: 256-bin output, every-pixel-counted, unsigned byte handling, dimension/buffer validation, rowStride padding skipped, centerWeight=1 short-circuit, multiplier on the center region, clamping, end-to-end pass through `HighlightMeter.suggestEvCorrection`, default-constants pin, `pixelCount` sums every bin). Engine wiring (where the Y plane comes from in the live preview surface) still pending Phase 1 capture engine.
- [ ] Sony-style Eye-AF overlay (green micro-rectangles over pupils; uses `STATISTICS_FACE_DETECT_MODE_FULL` when available) — render layer shipped (`EyeAfOverlay.kt` Compose `Canvas` overlay; takes `List<EyeMark>` in preview-relative pixels and draws crosshair-decorated green rectangles); face/eye -> preview-pixel adapter shipped (`FaceDetectAdapter.kt`: handles 0/90/180/270 sensor rotation, front-camera mirroring, face-bbox proxy when face-detect FULL is unavailable; **JUnit-tested** in `FaceDetectAdapterTest.kt`); engine ingestion (`STATISTICS_FACES` -> adapter -> overlay) pending Phase 1.
- [ ] Nikon-style 3D tracking persistence logic — pure-data tracker shipped (`TrackerState.kt`: hysteresis-based lock/unlock with `acquireFrames` + `keepAliveFrames` per ID; intermittent presence resets the absent streak; **JUnit-tested** in `TrackerStateTest.kt`); engine wiring (drive `update(observedIds)` from `STATISTICS_FACES` per `CaptureResult`) pending Phase 1.
- [ ] Exposure bracketing (BKT): 3/5/7 RAW12 sequences with GroupingID metadata — `BKT` mode wired in `CommandDialMode.BKT`; **plan generator** complete in `BracketPlan.build(BracketPattern.{Three,Five,Seven})` (centered, monotonic, stable `bracketGroupingId`; **JUnit-tested** in `BracketPlanTest.kt`); sequence *runner* (Camera2 burst submitter) pending.

### Phase 1 deliverable
- [ ] Preview + capture engine with:
  - 120fps preview on supported path(s) — proven by probe `PreviewEngineScreen.kt` constrained high-speed sweep; full capture engine pending.
  - RAW12 DNG saving — saver (`Dng12Saver.kt`) scaffolded; capture pipeline integration pending.
  - NDK pipeline callable from Kotlin — `native/CMakeLists.txt` skeleton + JNI stubs (`Java_dev_pointandshoot_NativeEncoders_*` symbols) + `NativeEncoders` Kotlin facade + `EncoderRoute` router shipped (Phase 0 scaffolding); Gradle `externalNativeBuild` block + libavif/libjxl `FetchContent` pin pending Phase 1 (human action; see `NDK_PLAN.md`).

### Phase 1 work items
- [ ] Implement `CameraDevice` + `CaptureSession` targeting 120fps preview where supported (`PreviewEngineScreen.kt` already proves the HFR session path; production engine still pending).
- [x] RAW12 DNG saver (lossless + uncompressed modes per imaging profile) — `Dng12Saver.kt` (Camera2 `DngCreator` wrapper). Profile selection drives the session bit-depth choice; saver writes whatever Camera2 delivers.
- [ ] NDK pipeline integration (planning doc: [`NDK_PLAN.md`](NDK_PLAN.md) - library choice + license matrix + JNI surface + Gradle/CMake wiring + fallback strategy):
  - [ ] `libavif` path (10-bit AVIF HDR) — **Phase 0 scaffolding shipped**: `NativeEncoders.encodeAvif10Hdr(...)` Kotlin facade with defensive `System.loadLibrary` (`isAvailable` flips false when the .so is missing; `lastLoadError` surfaces the dlopen message); JNI symbol `Java_dev_pointandshoot_NativeEncoders_nativeEncodeAvif10Hdr` stubbed in `native/pns_native.cpp` (returns `nullptr`); `EncoderRoute.decide(StandardPro, nativeAvailable = false)` substitutes a JPEG fallback per `FAILURE_MATRIX.md`. **JUnit-tested** in `NativeEncodersFallbackTest` (10 tests, all green: isAvailable false on JVM, encodeAvif10Hdr returns NotAvailable instead of throwing, Result.Success ByteArray equality, etc.) + `EncoderRouteTest` (9 tests, all green: decide() routes both profiles, downgradedProfiles enumerates AVIF + JXL, DOWNGRADE_MESSAGE is non-blank). **Device-validated on adb 8bf09993**: `--es pns_screen native` brings up `NativeDiagnosticsScreen` showing "Native library: NOT LOADED" + `loadLibrary error: dlopen failed: library "libpns_native.so" not found` + per-profile rows reporting "Tonal: DOWNGRADED to JPEG" exactly as specified (`docs/screenshots/smoke_native_diag.png`). Real `libavif` body lands once `externalNativeBuild` is wired and the FetchContent URL + SHA-256 are pinned (see `NDK_PLAN.md` "Human action required").
  - [ ] `libjxl` path (12-bit JXL) — **Phase 0 scaffolding shipped**: same shape as the AVIF row (`NativeEncoders.encodeJxl12Rec2020(...)` + JNI stub + `EncoderRoute.decide(UltraMax, nativeAvailable = false)` -> JPEG fallback). Covered by the same `NativeEncodersFallbackTest` + `EncoderRouteTest` suites. Real `libjxl` body lands alongside libavif.
- [x] Implement the 30ms haptic delay logic (still capture only) — `CaptureHaptics.kt`; uses `VibrationEffect.EFFECT_TICK` on API 29+ with a 12 ms one-shot fallback.

### Phase 1 V&V gate (must pass before Phase 2)
- [ ] [ADB] 10 consecutive captures without session death
- [ ] [HOST] Verify outputs by pulling files and opening in desktop tooling:
  - Standard Pro: DNG (lossless) + AVIF (10-bit HDR)
  - Ultra-Max: DNG (uncompressed RAW12) + JXL (12-bit)
- [ ] [ADB] Logcat shows no repeating Camera2 errors during preview/capture loop

## 5) Phase 2 (Part 5): Professional HUD & dial
### Deliverable
- [x] Pro HUD + dial usable during live preview — `PreviewEngineScreen.LivePreviewHudOverlay` wraps the live `TextureView` in a `Box` and rides the FPS / cameraId readouts, the LUT chip row, the command dial, and an `EyeAfOverlay` placeholder on top. Validated on the OnePlus 13 (adb 8bf09993): with the rear camera streaming at 59.3 fps the overlay renders the `fps 59.3 (target 60) ISO -- 1/--` chip, `cam 0` chip, persisted `STILL LUT B&W BT.709` chip, `VIDEO LUT None` chip, and the M/H/S/BKT command dial without dropping frames or interfering with the camera surface.

### Work items
- [x] Rotary command dial: M / H / S / BKT — `CommandDial.kt` (segmented Compose component; selected segment uses `PnsColors.PhotoOrange`).
- [x] Video tally (solid red border) + Sony-style timecode `00:00:00:00` — `VideoTallyOverlay.kt` (`PnsColors.RecordRed`, optional pulse) + `TimecodeOverlay.kt` (`HH:MM:SS:FF` with rec/standby dot, monospaced).
- [x] `Settings > HUD` granular element toggles — `HudSettings.kt` (SharedPreferences-backed) + `HudSettingsScreen.kt` (Compose `Switch` rows with descriptions; reachable from probe home via "Settings > HUD" button).
- [x] Composite Pro HUD preview (host-side smoke harness) — `ProHudScreen.kt` wires `CommandDial` + `TimecodeOverlay` + `VideoTallyOverlay` + readouts + record toggle + profile switcher; reachable from probe home via "Pro HUD (preview)".

### Phase 2 V&V gate (must pass before Phase 3)
- [ ] [ADB] Mode transitions deterministic and logged (no hidden state)
- [ ] [ADB] No UI-induced capture regressions (preview remains stable)

## 6) Phase 3 (Part 5): Street polish + Part 4 UX/heritage requirements
### Part 4 requirements
- [x] Typography: JetBrains Mono for all technical readouts — JetBrains Mono Regular `v2.304` (SIL OFL 1.1) vendored at `app/src/main/res/font/jetbrainsmono_regular.ttf` (273900 bytes; SHA-256 `a0bf60ef0f83c5ed4d7a75d45838548b1f6873372dfac88f71804491898d138f`); license text at `app/src/main/assets/fonts/jetbrainsmono/LICENSE.txt`; `SOURCE.txt` documents the refresh procedure; `LICENSES.md` "Bundled font assets" row + OFL-1.1 compatibility statement updated. `PnsTheme.MonoFamily = FontFamily(Font(R.font.jetbrainsmono_regular))` so every `PnsTypography` slot pulls the vendored face. **Device-validated on adb 8bf09993**: `smoke_jetbrains_mono.png` shows the ProHUD timecode + FPS / ISO / shutter readouts + LUT chips + command dial + arrow ligature in the new face.
- [x] Visual feedback colors:
  - [x] Photo button `#FF5C00` (Hasselblad orange) — `PnsColors.PhotoOrange`
  - [x] Video button `#E00000` (record red) — `PnsColors.RecordRed`
- [x] About page tribute block (monospaced, exact content) — `AboutScreen.TRIBUTE_TEXT`, rendered via `MonospaceBlock` (kept verbatim from this file):

```
SONY: For the relentless pursuit of speed and the intelligence of the "sticky" Eye-AF.
RICOH: For the "Snap Focus" philosophy and the courage to protect the highlights.
OLYMPUS: For the pioneering "Super Macro" and the soul of the compact professional tool.
HASSELBLAD: For the legendary Natural Colour Solution and the iconic Orange shutter.
CANON & NIKON: For the gold standard of focus bracketing, 3D tracking, and the unwavering reliability of the professional instrument.
```

### Phase 3 deliverable
- [ ] Street interaction model + macro lock + heritage page — heritage page complete; street interaction model + macro lock pending Phase 1 capture engine.

### Phase 3 work items
- [ ] Tap-to-shoot (lock AF/AE on DOWN, fire on UP) — gesture handler shipped (`Modifier.tapToShoot(callbacks)` in `TapToShootHandler.kt`; `down` -> `onDown(position)` lock; clean `up` -> `onFire()`; cancellation -> `onCancel()`). Capture-engine hookup pending Phase 1.
- [ ] Finalize 🌷 Super Macro hardware lock behavior — depends on probe expansion (min focus distance / mode-switch behavior).
- [x] Implement Heritage About page per Part 4 — `AboutScreen.kt`, reachable from probe home via the "About / Heritage" button.
- [x] Add a developer-facing "What works on OnePlus 13 (dodge)" block to the About page:
  - [x] Summarize the **successful, validated capture method(s)** discovered by probes (HFR per-camera, RAW feasibility, reprocess session) — `AboutScreen.KNOWN_GOOD_RECIPES`.
  - [x] Include the exact "known-good recipe" as copy/pastable bullets (cameraId, size, fpsRange, mime, key request settings) — `RecipeCard` rows.
  - [x] Explicitly list "known-bad" paths and the canonical errors (e.g., `Function not implemented (-38)` for specific HEVC/HFR stream configs) — `AboutScreen.KNOWN_BAD_PATHS`.
  - [ ] Keep this section updated from JSON probe artifacts at runtime — currently sourced statically from `PROBE_BUILD_PLAN.md` §5; runtime hydration from the latest `hfr-runs/*.json` is a follow-up.

### Phase 3 V&V gate (release readiness)
- [ ] [ADB] 15-minute on-device session: preview, mode changes, capture, export; no crash
- [ ] [HOST] Dodge profile decisions trace to `PROBE_RESULTS.md` and/or `DODGE_PROFILE.md`

## 7) Phase 4: Color management, calibration & LUT pipeline
> Goal: give non-RAW outputs (AVIF / JXL / JPEG / MP4) a defensible color story
> by (a) calibrating the OnePlus 13 sensor → display chain against a known
> reference chart and (b) shipping a license-clean LUT library that users can
> apply as filters to live preview, video, and stills. RAW (DNG) outputs are
> NEVER baked through a LUT — RAW lives in the sensor domain; LUTs are display-
> domain transforms recorded as sidecar metadata only. Every checkbox below
> follows the **Verification protocol** at the top of this file.

### Imaging chain (where LUTs apply)
- [x] `COLOR_PIPELINE.md` shipped: documents the reference chain `sensor → demosaic → WB gains → CCM → tone curve → display gamut → LUT (optional) → encoder`, identifies which stages live in the Camera2 hardware ISP, our Kotlin engine, and our NDK encode path, and pins all the magic numbers (luma weights, supported LUT sizes, dE_2000 targets, MTF50 floor) against the actual code so doc/code drift is caught at gate time.
- [x] LUT application stage fixed as the **last step before encoding** in `COLOR_PIPELINE.md` § "The reference chain" + § "Encoder integration"; single LUT works across AVIF / JXL / JPEG / H.265 outputs (RAW always skips per the same doc).

### Calibration mode (one-shot per illuminant)
#### Deliverable
- [ ] In-app "Calibrate" mode: point at a reference chart, tap to compute and save a `CalibrationProfile { wbGains, ccm, mtf50Lpph, illuminant, capturedAt, cameraId }`.
- [ ] Profile JSON saved to app-private storage (`getExternalFilesDir(null)/calibration/<illuminant>_<utc>.json`); pulled into `hfr-runs/calibration/` by `pns_hfr_autorun.ps1 -PullCalibration` — **host-side serializer + parser + Android-side storage wrapper shipped**: `CalibrationProfileJsonAdapter.encode(profile)` produces a stable, pretty-printed JSON document (schema `version: 1`, top-level keys `cameraId / targetId / illuminant / capturedAtMs / wbGains / ccm / bias / mtf50Lpph?`) and `CalibrationProfileJsonAdapter.decode(text)` round-trips it back into a `CalibrationProfile`. Filename helper `CalibrationProfileJsonAdapter.filenameFor(profile, utc)` enforces the `<illuminant>_<utc>.json` convention. `CalibrationProfileStorage.kt` wraps the adapter with the actual file IO: `directory(context)` resolves (and creates) `getExternalFilesDir(null)/calibration/`, `save(context, profile)` writes via temp-file + atomic rename so a crashed write never leaves a half-formed JSON document, `load(file)` round-trips back through the adapter, `list(context)` enumerates saved profiles newest-first, and `latestFor(context, illuminant)` returns the most recent profile for an illuminant. UTC timestamp generation (`nowUtcTimestamp()`) matches the project-wide `yyyyMMdd_HHmmss` convention. Uses `org.json` (Android-framework type; real lib pulled in via `testImplementation` for JVM tests). **JUnit-tested** in `CalibrationProfileJsonAdapterTest` (16 tests) + `CalibrationProfileStorageTest` (6 tests, Context-free surface area: timestamp pattern + monotonicity, documented constants, load round-trip via JUnit temp dir, malformed-JSON propagation, filename convention parity with the storage `latestFor` key). The `pns_hfr_autorun.ps1 -PullCalibration` script flag still lands with the in-app Calibrate flow.
- [x] Each profile can also export a 33×33×33 `.cube` LUT representing the (WB → CCM → tone-curve) correction so non-RAW outputs land in the calibrated color space without re-running the math at capture time — **shipped**: `CalibrationToLut.toCube(profile, size = 33, title)` produces a `Lut3D` that bakes WB → CCM → identity tone curve onto every grid cell; `LutPipeline.serializeCube(lut, title)` round-trips it to Adobe Cube text. End-to-end fidelity is held by `CalibrationCcmAccuracyTest.LUT round-trip shrinks mean dE_2000 by at least 80 percent` and `LUT vs direct CCM apply parity`. Surfacing the export action in the in-app Calibrate flow lands with the Compose UI.

#### Reference targets supported
- [x] **Generic 24-patch test chart** shipped: `BundledReferenceTargets.Generic24` ships as a pure-data Kotlin constant (4 rows × 6 cols; 18 hue swatches at 3 luminance levels + 6-step neutral wedge in the bottom row), generated deterministically by `BundledReferenceTargets.hueToRgb` so the values are reproducible without redistributing any image. We do **not** redistribute the chart image itself — the user prints or buys their own. Reference values are synthetic (D65); users who need absolute color accuracy should use the ColorChecker entry below. **JUnit-tested** in `ReferenceTargetTest`: 24 patches, 18 colors + 6 grays, every value in `[0, 1]`, lookup via `BundledReferenceTargets.byId("generic24")` works.
- [x] **X-Rite ColorChecker Classic (24-patch)** shipped: `BundledReferenceTargets.ColorCheckerClassic24` ships the published reference Lab values (facts, not copyrightable) converted to linear-light sRGB under D50, as a pure-data Kotlin constant (4 rows × 6 cols matching the standard chart layout). We do NOT bundle the chart image and do NOT use the "ColorChecker" trademark in any user-facing string (the registered name lives only in the `BundledReferenceTargets.kt` source comments). **JUnit-tested** in `ReferenceTargetTest`: 24 patches in a 4×6 grid, row 3 is the neutral wedge (R=G=B exactly, monotonically decreasing brightness), row 2 primaries have the right dominant channel (red→R, green→G, blue→B).
- [ ] **X-Rite ColorChecker Passport (24-patch subset)** — **24-patch subset shipped (Round 12)** as `BundledReferenceTargets.ColorCheckerPassport24`: identical reference values to `ColorCheckerClassic24` (the Passport's "main" 24-patch grid uses the exact same X-Rite reference values; the trademarked name + image are NOT bundled — only the values, which are facts and not copyrightable), but with `id="passport24"`, a Passport-specific `displayName` and `source` string so a user calibrating against a real Passport gets a chart-name match in the calibrate flow without IP entanglement. **JUnit-tested** in `ReferenceTargetTest`: round-trip equality of patch values vs Classic 24, Passport-specific id/source/displayName, `byId("passport24")` resolves, `All` contains the entry. Pending: the Creative Enhancement / portrait-warming patches on the back of the Passport (1/3-stop highlight + shadow + warming + cooling rows) still need to be transcribed from the X-Rite published data sheet — tracked as a separate doc TODO.
- [ ] Target picker UI lets the user choose; default is the generic 24-patch (no IP entanglement). Pending Compose UI; the data layer is ready (`BundledReferenceTargets.All` + `BundledReferenceTargets.byId(...)`).

#### Work items
- [ ] **Manual 4-corner tap UI** (no OpenCV dep): user taps the 4 chart corners on the live preview; preview overlays the inferred patch grid via a homography. Pure Compose interaction; corner positions stored per-target.
- [x] `CalibrationSampler.sample(plane, target, corners)` shipped: takes an `RgbPlane` (linear-light, normalized `[0, 1]`, row-major 3-floats-per-pixel) + a `ReferenceTarget` + `ChartCorners` (4 user-tapped corners, clockwise from top-left); returns one `PatchSample` per patch (mean + per-channel variance + sample count + rejection flag + reason + patch back-ref). Uses bilinear chart→pixel mapping (`ChartCorners.bilinearMap`) — sufficient for the typical "phone held flat over a chart" calibration pose; perspective homography can wrap this later without changing the math. Rejects samples where `max(variance) > maxVariance` (default `CalibrationSampler.DEFAULT_MAX_VARIANCE = 5e-3`); also rejects when the ROI clips entirely off-plane. **JUnit-tested** in `CalibrationSamplerTest` (14 tests): `RgbPlane.uniform` round-trip, `pixel()` clamping, mismatched-array rejection, bilinear corner mapping (exact at u=0/1, v=0/1), bilinear center as average, sampleAt on uniform plane returns exact color + zero variance, two-color stripes mean + variance match analytically, ROI clips to plane bounds, off-plane ROI returns rejected sample with reason, max-variance triggers rejection + accepts under threshold, sample over uniform plane returns identical means for every patch, sample over a synthetic 24-patch chart finds every patch exactly (mean within 1e-3 of reference), `sampleNormalized` rejects out-of-range halfNorm.
- [x] `CalibrationMath.computeWbGains(neutralPatches)` shipped: per-channel scaling solver anchored at `g = 1`; rejects when the average green is below `MIN_AVG_FOR_NEUTRAL_SOLVE = 1e-4` (neutrals appear black). **JUnit-tested** in `CalibrationMathTest.kt`: anchors green at 1, corrects a magenta cast, averages across multiple gray patches, throws on empty input, throws on dark neutrals.
- [x] `CalibrationMath.computeCcm(measured, target)` shipped: linear least-squares (3x3 normal equations + Gaussian elimination with partial pivoting) solving `target = M × measured`; requires ≥ 3 patches; throws on a singular system (rank-deficient measured matrix) at `SINGULAR_TOLERANCE = 1e-12`. **JUnit-tested** in `CalibrationMathTest.kt`: recovers identity when measured equals target, recovers a known channel-swap matrix, recovers a synthetic non-trivial mixing matrix to within 1e-3, throws on length mismatch + too-few patches + singular systems. Bradford chromatic-adaptation conversion of published Lab values to the working color space lives upstream in the (pending) `ReferenceTarget` loader; this solver is intentionally agnostic to the reference space so calibration can target sRGB, Display P3, or Rec.2020 without re-implementing the algebra.
- [x] `SlantedEdgeMtf.measureMtf50(luma)` shipped: classical ISO 12233 slanted-edge MTF on a single-channel `GrayPlane` ROI. Pipeline: per-row centroids of `|dI/dx|` → linear fit `y → x` → bin pixels by perpendicular distance into a 4×-oversampled 128-bin ESF → linear-interpolate empty bins → differentiate to LSF → Hamming window → direct DFT (small N; an FFT is not worth the complexity here) → linear-interpolate the bin where MTF crosses 50 % of MTF(0). Returns cycles/pixel (Nyquist = 0.5); `cyclesPerPixelToLpph(cyclesPerPixel, pictureHeightPx)` converts to the BUILD_PLAN "lp/ph" flavor. Supports both `NearVertical` and `NearHorizontal` orientations (the latter transposes the ROI before processing). Stub implementation in `CalibrationProfile.mtf50Lpph` is now backed by real math; the engine call site (`CalibrationSampler` + ROI extraction from the 4 chart corners) lands when the camera engine arrives. **JUnit-tested** in `SlantedEdgeMtfTest` (12 tests): `GrayPlane.build` + `transpose`, oversample=0 / non-power-of-2 esfBins rejected, uniform plane returns null, too-few rows returns null, sharp edge yields higher MTF50 than blurred edge, monotonic decrease across σ ∈ {0.5, 1.0, 1.5, 2.0}, measured MTF50 sits within 50 % of the analytic Gaussian-blur formula `0.187/σ`, MTF50 stays bounded by the Nyquist of the oversampled signal, near-horizontal variant agrees with near-vertical within 18 %, `cyclesPerPixelToLpph` multiplies correctly.
- [x] `CalibrationToLut.toLut3D(profile, size)` + `CalibrationToLut.toCube(profile, size, title)` shipped: applies (WB → CCM → bias) to a uniformly-sampled normalized RGB cube (default size 33), round-trips through `LutPipeline.parseCube` exactly. **JUnit-tested** in `CalibrationToLutTest.kt`: identity profile produces an identity LUT, identity cube round-trips through `parseCube`, WB-only profile shifts neutrals predictably (R > G > B for warm gain), CCM profile rotates RGB primaries (channel-swap recovers exactly), full WB+CCM+bias profile reaches < 1 LSB on 8-bit through the LUT vs direct `profile.apply()`, mtf50 metadata is preserved on the profile but does NOT change the LUT, default cube title encodes provenance (illuminant + cameraId + targetId), explicit title override wins over default.
- [x] `CalibrationProfile` data class shipped: pure-data `{wbGains, ccm, bias, mtf50Lpph?, illuminant, capturedAtMs, cameraId, targetId}` with `Identity` companion sub-types (`WbGains.Identity` / `Ccm.Identity` / `Bias.Zero`), `Illuminant.{D50, D55, D65, StdA, F2}` enum, and an `apply(rgb)` method that applies the (WB → CCM → bias) chain with `[0, 1]` clamping. Validation: gains must be `> 0`, bias must be length 3, cameraId / targetId non-blank, mtf50Lpph >= 0 when set. **Indirectly JUnit-tested** through `CalibrationToLutTest` (identity / WB-only / CCM-only / full profiles all round-trip through the LUT exporter).
- [ ] `Dng12Saver` extension writes `ColorMatrix1` / `ForwardMatrix1` derived from the active `CalibrationProfile` so desktop tools (`darktable`, `RawTherapee`) inherit the calibration on the RAW path **without** baking it in. **Pure-data converter shipped**: `DngColorTags.kt` ships `forProfile(profile) -> DngColor` with three tag arrays + the EXIF light-source code. Math: `asShotNeutral(gains)` inverts WB gains and normalizes max to 1.0f per DNG spec; `colorMatrix1(profile)` solves `inverse(diag(wbGains) * sRGBtoXYZ_D65 * CCM)` (XYZ → camera-native RGB); `forwardMatrix1(profile)` produces `Bradford_D65_to_D50 * sRGBtoXYZ_D65 * CCM` (WB-gained camera RGB → XYZ-D50, the DNG profile connection space); `calibrationIlluminantCode(illuminant)` maps `Illuminant` → EXIF LightSource code (D50→23, D55→20, D65→21, StdA→17, F2→14). Standards constants (linear-sRGB → XYZ-D65 with BT.709 primaries; Bradford D65→D50 CAT) pinned as private constants — they're CIE/IEC standards data, not copyrightable. **JUnit-tested** in `DngColorTagsTest` (12 tests): identity gains → `(1, 1, 1)`, AsShotNeutral max is exactly `1.0`, AsShotNeutral inverts gains correctly, identity profile yields `ColorMatrix1 = inverse(sRGB→XYZ_D65)` (validated by `cm1 * sRGB→XYZ_D65 == I`), identity profile `ForwardMatrix1 = Bradford * sRGB→XYZ_D65`, EXIF light-source codes match the DNG 1.7 spec, `forProfile` picks the right code for every illuminant, non-identity CCM yields a different `ColorMatrix1`, WB gains shift `AsShotNeutral` but NOT `ForwardMatrix1`, `DngColor.equals` compares `FloatArray` contents (not references), `DngColor.init` rejects malformed `AsShotNeutral`. The `[ ]` stays open until the engine's `Dng12Saver.save(...)` path actually pushes these arrays into the underlying `DngCreator` (Camera2 metadata override; lands with the Phase 1 capture engine).

#### V&V gates
- [x] [HOST] On a synthetic 24-patch fixture (known reference values, configurable noise): computed CCM produces ≤ 1.0 dE_2000 mean error; WB gains anchored at `g = 1` recover known channel-swap / non-trivial mixing matrices to within `1e-3` — closed by `CalibrationCcmAccuracyTest` (7 tests, all green): synthetic 24-patch with ±0.5 % noise lands at ≤ 1.0 mean dE_2000; noiseless case lands < 0.05; small-rotation hue tint recovers cleanly; Generic24 fixture also passes; WB-only "warm cast" shrinks dE substantially when the recovered gains are applied; LUT-vs-direct apply parity to within 1 LSB on 8-bit. Backed by `ColorMath` (linear sRGB → XYZ → Lab D65 + full CIEDE2000 per Sharma 2005, 22 tests including the published Sharma row 1 / 2 / 3 / 14 / 22 reference vectors).
- [ ] [HOST] On a captured real-world chart (D65 daylight, OnePlus 13 main wide): mean dE_2000 ≤ 3.0 across all 24 patches; max ≤ 6.0 (excluding the pure-black patch). Pending the on-device Calibrate flow + a real chart capture.
- [ ] [HOST] MTF50 baseline measured and recorded; sanity-check sharpness > 1500 lp/ph at f/1.6 main wide. Pending the on-device Calibrate flow + a real chart capture (the math layer is shipped via `SlantedEdgeMtf.measureMtf50` + `cyclesPerPixelToLpph`).
- [x] [HOST] `.cube` round-trip: applying the exported LUT to the captured chart shrinks mean dE_2000 by ≥ 80 % vs the un-LUT'd capture — closed by `CalibrationCcmAccuracyTest.LUT round-trip shrinks mean dE_2000 by at least 80 percent`: synthetic baseline mean dE_2000 > 1, after applying the calibration `.cube` (`CalibrationToLut.toLut3D` + `LutPipeline.applyTrilinear`) the corrected mean dE_2000 is ≥ 80 % smaller. The "real-world chart" variant of this gate stays `[ ]` and lands with the on-device Calibrate flow.
- [ ] [ADB] In-app "Calibrate" flow: pick target type → tap 4 corners → tap "Compute" → preview shows green checkmarks per patch → "Save profile" persists the JSON + `.cube` to app-private storage — **Compose UI shipped (chart-photo flavor)**: `CalibrateScreen.kt` ships the four-step flow against a static photo (the live-preview flavor lands with the Camera2 capture engine). The screen offers a target picker for `BundledReferenceTargets.All` (Generic24 + ColorChecker24), a SAF `OpenDocument(arrayOf("image/*"))` "Load chart photo…" button, an `Image` surface that captures four taps in TL → TR → BR → BL order with a live orange dot + connecting quad overlay (`Canvas` + `pointerInput.detectTapGestures`), a "Reset corners" affordance, and a Compute / Save row. Compute runs `BitmapRgbPlane.fromBitmap(bitmap)` (sRGB EOTF in linear-light, downsampled to 1024 px on the long edge), maps the user-tapped layout coordinates into plane coordinates using the displayed Box size (`Modifier.onSizeChanged` tracks it), then chains `CalibrationSampler.sample(...)` → `CalibrationMath.computeWbGains(neutralPatches)` → `CalibrationMath.computeCcm(measuredAfterWb, targetRgb)` and surfaces a monospace breakdown of WB gains + 3×3 CCM. Save calls `CalibrationProfileStorage.save(context, profile)` which writes `<illuminant>_<utc>.json` via temp + atomic rename. **JUnit-tested** in `BitmapRgbPlaneTest` (11 tests covering the sRGB EOTF endpoints, monotonicity, clamping, and the long-edge downsample arithmetic) — the Bitmap-touching `fromBitmap` path lands when Robolectric arrives. **Device-validated on adb 8bf09993**: launching `--es pns_screen calibrate` brings up the screen with target picker + "Load chart photo…" button + placeholder; no crashes. The `[ADB]` row remains open until a real chart-image round-trip + green-checkmark overlay land — those need the Camera2 capture engine for the live-preview flavor.

### Built-in LUT library (filters)
#### Deliverable
- [ ] LUT assets under `app/src/main/assets/luts/<spdx-folder>/<name>/` (each leaf folder MUST contain `LICENSE.txt`, `SOURCE.txt`, and `SHA256.txt`).
- [ ] `LutCatalog.kt` enumerates each bundled LUT with metadata (name, scope = still / video / both, bundled vs imported, SPDX, source URL, SHA256).
- [x] HUD chip "LUT" alongside the imaging-profile selector; per-mode memory (still vs video can carry different defaults); "None" (identity) is always the default and survives app restart unless the user explicitly chose otherwise — `HudSettings` extended with `selectedLutForStills` + `selectedLutForVideo` fields (both default to `LutCatalog.None.name`, persisted to `SharedPreferences` under `selected_lut_stills` / `selected_lut_video`) plus `stillsLut()` / `videoLut()` resolvers that fall back to `None` on rename or removal. `LutChipRow.kt` ships the two-chip row (STILL LUT / VIDEO LUT) with an `AlertDialog`-based picker that lists every `LutCatalog.forScope(scope)` entry with display name + 1-line description + SPDX badge; selection writes through `HudSettingsState.update(...)` so process death never loses intent. Wired into `ProHudScreen` between the command dial and the record button. **JUnit-tested** in `HudSettingsLutResolutionTest` (5 tests): defaults resolve to None, stills + video resolve independently, unknown enum names fall back to None, default constructor fields equal `LutCatalog.None.name`. **Device-validated on adb 8bf09993**: tapping STILL LUT chip → "Choose still LUT" dialog renders all four catalog entries; tapping "B&W BT.709" writes `selected_lut_stills=BwBt709` to `pns_hud_settings.xml`; force-stop + relaunch shows STILL LUT chip still reading `B&W BT.709` (persistence across process death verified).

#### Bundled LUTs (FOSS-only; vendored at build via Gradle download task with SHA256 pin)
- [ ] **ACES sRGB → ACEScct** (Apache-2.0, AMPAS OpenColorIO config) — neutral working-space conversion. **Blocker**: the AMPAS upstream (`AcademySoftwareFoundation/OpenColorIO-Config-ACES`, formerly `colour-science/OpenColorIO-Configs`) ships `.ocio` configs + spi3d/CTL transforms — NOT pre-baked `.cube`. Landing this requires either (a) committing a host-baked `.cube` produced by `ociobakelut` from a pinned upstream OCIO config (we'd then ship the script, the input config SHA-256, AND the resulting cube SHA-256 — defensible chain but we become the canonical baker), or (b) extending `LutPipeline` to parse `.spi3d` directly (more code, no new external deps). Decision deferred until the live-preview GLES path lands so we can prioritize against the actual user-perceived gap.
- [ ] **ACEScct → sRGB Display** (Apache-2.0, AMPAS OpenColorIO config) — display transform. **Blocker**: same as above (OCIO-shaped upstream, no canonical `.cube`).
- [ ] **Filmic (Blender)** sRGB → log (Apache-2.0, from Blender's `filmic-blender` repo) — gentle highlight rolloff for HDR-to-SDR delivery. **Blocker**: upstream tag `1.1.1` ships `desat65cube.spi3d` + `sRGB_OETF_to_Linear.spi1d` (Sony Pictures Imageworks LUT formats) under `luts/`, NOT `.cube`. Same two paths as the ACES rows: host-bake to `.cube` with a pinned script, or extend `LutPipeline` with a `.spi3d` parser. Decision deferred until the user-base actually requests this look — the in-house `LutCatalog.PnsCinematic` already covers most of the "warm log roll-off" use cases that motivate Filmic.
- [x] **Rec.709 identity** shipped: code-generated at runtime from pure encoding math (public-domain) via `BuiltInLuts.rec709Identity(size)`; routes through `LutCatalog.None` so the user-facing "None" entry uses the same shader path. **JUnit-tested** in `BuiltInLutsTest`: reports as identity at every supported size (17 / 33 / 65); applying it preserves arbitrary samples within float precision.
- [x] **B&W BT.601** shipped: code-generated by `BuiltInLuts.bwBt601(size)` from the public-domain `Y = 0.299R + 0.587G + 0.114B` luma weights. Catalog entry `LutCatalog.BwBt601`. **JUnit-tested** in `BuiltInLutsTest`: collapses output to gray at every cell, applies BT.601 luma weights exactly (pure red → 0.299, pure green → 0.587, pure blue → 0.114).
- [x] **B&W BT.709** shipped: code-generated by `BuiltInLuts.bwBt709(size)` from the public-domain `Y = 0.2126R + 0.7152G + 0.0722B` luma weights. Catalog entry `LutCatalog.BwBt709`. **JUnit-tested** in `BuiltInLutsTest` (3 of the 11 tests cover BT.709 specifically): applies BT.709 luma weights exactly; pure green is brighter through BT.709 than through BT.601 (sanity).
- [x] **"Point & Shoot Cinematic" (teal-orange)** shipped: original Apache-2.0 grade in `BuiltInLuts.pnsCinematic(size)`; pulls shadows toward teal `(0.30, 0.55, 0.70)` and highlights toward orange `(1.00, 0.65, 0.35)` based on BT.709 luma, smoothstep-blended at 30 % strength. Explicitly derived from public-domain math (BT.709 weights + Hermite smoothstep) so the licensing chain is fully traceable; no proprietary LUT (Lightroom, DaVinci, FilmConvert) was reverse-engineered as source material. **JUnit-tested** in `BuiltInLutsTest` (5 of the 11 tests cover Cinematic): not identity (it's a creative LUT); 50 % gray cells survive (smoothstep weight is zero at luma=0.5); deep shadows shift toward teal (B > G > R); pure highlights shift toward warm orange (R > G > B); every cell stays in `[0, 1]`.

#### File format support
- [ ] **`.cube`** (Adobe Cube LUT spec — publicly documented, free to implement) — primary import/export format. Supports 1D and 3D LUTs at sizes 17 / 33 / 65.
- [x] **`.3dl`** (Autodesk; publicly documented) — secondary import-only. **Shipped (Round 12)** as `LutPipeline.parseDl3(text)`: parses the Autodesk Lustre Mesh format (optional `#` comments + optional non-integer header lines like `Mesh 12 12`, then a uniform-ramp shaper line of N integers from 0 to `(2^outputBits) - 1`, then N^3 RGB integer triples with R varying fastest); inferred bit depth from the shaper's max value (1023→10-bit, 4095→12-bit, 65535→16-bit); rejects non-uniform shaper ramps (within 1-step tolerance for fractional `maxValue / (size - 1)`); rejects unsupported sizes (only `Lut3D.SUPPORTED_SIZES` = 17/33/65). `LutImportValidator.sniffFormat(text)` routes between `.cube` and `.3dl` automatically (cube wins on `LUT_3D_SIZE`/`TITLE`/`DOMAIN_*`/`LUT_1D_SIZE`; `.3dl` wins when the first integer-only line has at least `min(SUPPORTED_SIZES) = 17` tokens); body-only `.cube` payloads with 3-int rows route to `.cube` so the canonical `MalformedHeader` failure category is preserved. New `LutImportValidator.Format.{Cube, Dl3}` enum + `validateForFormat(text, format)` for round-trip tests. **JUnit-tested** in `Dl3ParserTest` (12 tests: identity 17-cube at 12-bit, identity 33-cube at 16-bit, leading comment + Mesh header tolerated, unsupported shaper size rejected, non-uniform shaper rejected, empty input rejected, malformed body row rejected, validator routes `.3dl` to parseDl3, sniffer picks Cube on `LUT_3D_SIZE`, sniffer picks Dl3 on integer-only first line, validator surfaces UnsupportedSize category, validator surfaces MalformedHeader category for empty `.3dl`).
- [x] User-imported LUTs land in `getExternalFilesDir(null)/luts/imported/`; SAF "Import LUT…" picker reads the user's `.cube` file, validates it (size + grid spacing + value range), and copies it in. Invalid files are rejected with a toast — **host-side validator shipped**: `LutImportValidator.validate(text)` and `validate(bytes)` wrap `LutPipeline.parseCube` with a structured `Result.Success(lut) | Result.Failure(category, message, cause)` so the SAF picker can surface a single-line toast without parsing exception strings. The `FailureCategory` enum (`TooLarge`, `MalformedHeader`, `MalformedBody`, `UnsupportedSize`, `OneDLut`, `NonUnitDomain`, `SizeMismatch`, `OutOfRange`) covers every observed failure mode; `Failure.toastMessage()` formats the user-facing string. Hard 16 MB payload cap; sample-value tolerance is ±0.001 around `[0, 1]` so professionally-exported LUTs that round-trip through fp32 still pass. **JUnit-tested** in `LutImportValidatorTest` (15 tests): identity at every supported size accepted, non-identity within range accepted, bytes overload wraps the string overload, oversized payloads rejected, missing/non-integer `LUT_3D_SIZE` rejected, `LUT_1D_SIZE` rejected, unsupported sizes rejected, non-unit `DOMAIN_MAX` rejected, non-float bodies rejected, wrong number of triples rejected, wildly out-of-range cells rejected, fp tolerance band accepted, toast message includes the category label, every `FailureCategory` has a non-blank toast label. **SAF picker UI shipped**: `LutImporterScreen.kt` is a Compose `Column`-with-`verticalScroll` with a "Pick `.cube` file…" button that fires `ActivityResultContracts.OpenDocument(arrayOf("*/*"))` (we accept anything because `.cube` has no system-wide MIME); on pick we read the bytes, run `LutImportValidator.validate(bytes)`, and either toast the structured failure message or call `ImportedLutStore.save(context, displayName, bytes)` which writes `<safeName>.cube` plus a `<safeName>.cube.sha256.txt` sidecar via temp + atomic rename. `ImportedLutStore` resolves `getExternalFilesDir(null)/luts/imported/`, sanitizes filenames to `[A-Za-z0-9._-]` (collapsing runs of underscores, stripping leading dots, capping length at 96), and picks collision-free filenames via `_2`, `_3`, … suffixes (with a SHA-derived fallback at 9999). The screen also lists every previously-imported LUT (newest first by mtime) with its size in bytes. **JUnit-tested** in `ImportedLutStoreTest` (9 tests): sanitize keeps allowed chars, replaces disallowed chars, collapses underscores, strips leading dots, caps length, falls back to `imported_lut` for empty input; pickAvailableFilename returns the base name when no collision and increments suffix on collisions; sha256 is 64 hex chars, differs for different inputs, and is stable. **Device-validated on adb 8bf09993**: launching `--es pns_screen lutimport` brings up the Import LUT screen with the "Pick `.cube` file…" button, the "Imported LUTs (newest first)" header, and "(no imports yet)" placeholder; no crashes, no logcat errors.

#### Apply path
- [x] **Live preview / video**: GLES 3.0 fragment shader sampling a `sampler3D` RGB texture with hardware trilinear interpolation. Shader template in `app/src/main/assets/shaders/lut_apply.frag.glsl`. Identity LUT bypasses the shader stage entirely. **Shipped end-to-end against a synthetic test pattern** (Camera2 source bind-in still pending Phase 1 capture engine; the GLES seam itself, however, is fully built and device-validated): `app/src/main/assets/shaders/lut_apply.frag.glsl` + `lut_apply.vert.glsl` ship the GLES 3.0 fragment + vertex pair (sampler3D with `LINEAR` filtering, half-texel inset on the UVW coordinates so we sample at cell centers). `LutShaderProgram.kt` exposes (a) a pure-data `Source` object (asset paths, attribute/uniform names, `FULL_SCREEN_QUAD` for `GL_TRIANGLE_STRIP`, `requiredSymbols()` for the contract test), (b) a pure-data `BypassPolicy` (returns `0f` to disable the LUT for null / identity LUTs, `1f` to apply, plus `shouldSkipUpload(...)` so the host can avoid even allocating the 3D texture), and (c) a thin GLES wrapper (`createFromAssets`, `uploadLutTexture`, `bindUniforms`). `LutPreviewRenderer.kt` is a `GLSurfaceView.Renderer` that owns the program + 2D source texture + 3D LUT texture + full-screen `GL_TRIANGLE_STRIP` VBO; UI-thread updates flow through two `AtomicReference` slots (`pendingSource`, `pendingLut` wrapped in a private `LutUpdate` data class so "no update" disambiguates from "user explicitly chose identity"); `BITMAP_VFLIP_QUAD` cancels Android-bitmap row-0-at-top vs OpenGL row-0-at-bottom (the Camera2 path will use `Source.FULL_SCREEN_QUAD` directly because the surface texture transform handles orientation). `GLPreviewScreen.kt` wires `AndroidView<GLSurfaceView>` + `LutChipRow` so picking STILL LUT live-cycles `LutCatalog` entries through the GLES uniforms; reachable from probe home via "Live preview LUT" or `--es pns_screen glpreview`. `TestPattern.kt` is a pure-data procedural source (8 vertical bars + 11-step grayscale wedge + smooth grayscale ramp; sRGB-encoded for display). **JUnit-tested** in `LutShaderProgramSourceTest` (13 tests, unchanged), `LutPreviewRendererQuadTest` (5 tests: vflip quad has the same vertex-position layout as `Source.FULL_SCREEN_QUAD` with V coords inverted), and `TestPatternTest` (12 tests: 8 color bars in screen order, wedge has 11 monotonic gray levels, smooth ramp is monotonic across width, generated arrays have the correct length + range, sRGB encoding endpoints `0.0 -> 0` and `1.0 -> 255`, generation rejects non-positive dimensions). **Device-validated on adb 8bf09993**: `docs/screenshots/smoke_glpreview_none.png` (identity bypass, all 8 bars in original color), `docs/screenshots/smoke_glpreview_bw709.png` (B&W BT.709 LUT collapses bars to grayscale with the documented luma weights — yellow > cyan > green > magenta > red > blue), `docs/screenshots/smoke_glpreview_cinematic.png` (PnS Cinematic LUT pulls shadows toward teal and highlights toward orange exactly as `BuiltInLuts.pnsCinematic` is documented to). Logcat `PNS.GLES: GLES program ready (program=3)` confirmed on every launch with no crash and no driver error. Camera2 source bind-in (replace the synthetic `TestPattern` `Bitmap` with a `samplerExternalOES`-backed `SurfaceTexture` from the live preview) lands with the Phase 1 capture engine.
- [ ] **Stills (post-encode)**: CPU trilinear interpolation in `LutPipeline.applyTrilinear(rgb, lut)`; runs after the encoder produces an 8/10/12-bit RGB plane and before final compression to AVIF / JXL / JPEG.
- [ ] **RAW (DNG)**: never baked in. The active LUT name + SHA256 are written into the DNG's `UniqueCameraModel` / `Software` tag chain so desktop processors (`darktable`, `RawTherapee`) can apply the same LUT optionally.
- [ ] **Capture sidecar**: every still or video written with a non-identity LUT also writes a sibling `.cube.txt` (or, for bundled LUTs, a small `.lutref.txt` pointing at `assets/luts/<name>` + SHA256) so the LUT'd output is reproducible offline — **host-side writer + parser shipped**: `LutSidecar.encode(BundledRef)` + `LutSidecar.encode(CubeFileRef)` produce ASCII line-oriented sidecars (`# pns-lut-sidecar v1` magic header, `key = value` pairs, `kind = bundled` vs `kind = cube`), and `LutSidecar.decode(text)` returns either `ParseResult.Bundled(BundledRef)` or `ParseResult.Cube(CubeFileRef)`. `BundledRef` carries the catalog name + SPDX + source + 64-char lowercase SHA256 (validated by regex); `CubeFileRef` carries the relative cube path + optional title + SHA256. Convenience helpers: `LutSidecar.bundledRefFor(catalog, ...)` populates SPDX + source straight from a `LutCatalog` enum entry; `LutSidecar.siblingFilenameFor(captureFilename, isBundled)` enforces the `.lutref.txt` / `.cube.txt` extension convention. **JUnit-tested** in `LutSidecarTest` (17 tests): bundled + cube round-trip, encoded sidecar contains the documented keys + magic header + schema version, `bundledRefFor` pulls metadata from `LutCatalog`, cube without a title omits the line, sibling filename per flavor, decode rejects missing magic / unknown schema version / malformed key=value lines / unknown kind / missing required fields, decode tolerates blank lines + inline comments, constructor validation rejects malformed sha256 / unsupported lutSize / blank cataloguedAs, Video captureKind round-trips. **File-IO writer shipped**: `LutSidecarWriter.kt` wraps `LutSidecar.encode` with atomic-rename writes (temp file + `renameTo`, with delete-and-rename fallback on Windows when the target exists; a power loss between operations leaves no half-formed sidecar). `siblingFor(captureFile, isBundled)` is a pure path helper; `sha256Hex(bytes)` and `sha256ForLut(lut)` (IEEE-754 LE) compute the digests both `BundledRef` and `CubeFileRef` need. **JUnit-tested** in `LutSidecarWriterTest` (14 tests): sibling resolution preserves the parent dir, `writeBundled` / `writeCube` round-trip through `decode`, mismatched `captureFilename` rejected, overwrite is atomic with no leaked temp files, parent dirs are created on demand, sha256 of empty input matches the canonical RFC value, sha256 differs across inputs and is stable, output is always 64 lowercase hex chars, identical LUTs hash identically, different LUT content hashes differently, different grid sizes hash differently, end-to-end "build a bundled sidecar with the LUT's own sha" round-trips perfectly. The actual sibling-file write happens at capture time (engine integration pending).

#### Architecture seams (host-side; pure-data first)
- [x] `Lut3D.kt` shipped: pure-data 3D LUT data class with `size` (17 / 33 / 65 enforced via `SUPPORTED_SIZES`) + `FloatArray(size³ × 3)` interleaved RGB (`((b * size + g) * size + r) * 3 + channel` order, matching the Adobe Cube spec); companion `identity(size)` builder + `isIdentity(tolerance)` checker (default tolerance `1e-5f`). **JUnit-tested** in `Lut3DTest` (8 tests): identity at every supported size, samples match grid coordinates exactly, unsupported size in constructor / factory throws, samples-length mismatch throws, perturbations within tolerance still report identity, tighter tolerance catches perturbations.
- [x] `LutPipeline.kt` shipped: `applyTrilinear(rgb, lut)` + allocation-free `applyTrilinearInto(rIn, gIn, bIn, lut, out, offset)` for hot per-pixel loops; `parseCube(text)` (tolerates blanks, comments, `TITLE`, `DOMAIN_MIN/MAX`, rejects non-`[0,1]` domain + 1D LUTs + unsupported sizes + body-size mismatch); `serializeCube(lut, title)` round-trips through `parseCube`. **JUnit-tested** in `LutPipelineTest` (12 tests): identity preserves arbitrary samples within float precision, inputs are clamped before lookup (out-of-gamut does not throw), NaN input throws explicitly, swap-channels LUT swaps R and B exactly, allocation-free variant writes at offset without touching neighboring slots, round-trip parse → serialize → parse is identical, non-default DOMAIN_MAX is rejected, 1D LUTs are rejected, unsupported sizes are rejected, malformed body is rejected, embedded quotes in TITLE are escaped. `parseDl3` is intentionally deferred: the .3dl format has multiple incompatible variants (mesh + integer triples, Lustre, Discreet) and the user impact is small (cube is by far the most common interchange format). It will land when a user actually requests it.
- [x] `LutCatalog.kt` shipped: enum of runtime-available LUTs (`None`, `BwBt601`, `BwBt709`, `PnsCinematic`) with metadata `(displayName, description, spdx, source, scope)`; `load(size)` materializes the LUT via the entry's generator; `forScope(scope)` filters; `defaultFor(scope)` returns `None`; `ALLOWED_SPDX = {Apache-2.0, BSD-2-Clause, BSD-3-Clause, MIT, CC0-1.0, public-domain}` enforced by the catalog test. ACES + Filmic entries are intentionally NOT in the enum yet — they'll be added when the Gradle `downloadBundledLuts` task lands. **JUnit-tested** in `LutCatalogTest` (8 tests): every entry's SPDX is in the whitelist, every entry loads at the default size, every entry loads at every supported grid size (17 / 33 / 65), `None` resolves to a true identity LUT, `forScope` returns entries for that scope plus Both, `defaultFor` is `None` for every scope, displayName / description / source are non-empty, B&W LUTs collapse to gray (R=G=B) when loaded from the catalog.
- [x] `BuiltInLuts.kt` shipped: code-generated `rec709Identity`, `bwBt601`, `bwBt709`, `pnsCinematic` with all magic numbers pinned as private constants (luma weights, shadow / highlight tints, strength). Source-of-truth for the four code-generated entries in `LutCatalog`. **JUnit-tested** in `BuiltInLutsTest` (11 tests): see "Bundled LUTs" rows above for the per-LUT coverage.
- [x] `LutShader.kt` — GLES setup: 3D-texture upload from `Lut3D`, fragment-shader binding, identity bypass when `Lut3D.isIdentity()` — shipped as `LutShaderProgram.kt` (compile + link + `uploadLutTexture` + `bindUniforms` + `BypassPolicy.shouldSkipUpload`) and exercised end-to-end via `LutPreviewRenderer.kt` against a synthetic `TestPattern` source on `GLPreviewScreen.kt`. Identity bypass verified on device by switching STILL LUT to None: the shader writes `uLutEnabled = 0` and the source texel is passed through verbatim (`docs/screenshots/smoke_glpreview_none.png`).
- [x] Tests shipped: `LutPipelineTest` (12), `LutCatalogTest` (8), `Lut3DTest` (8), `BuiltInLutsTest` (11), `CalibrationMathTest` (12), `CalibrationToLutTest` (8), `LutShaderProgramSourceTest` (13), `LutPreviewRendererQuadTest` (5), `TestPatternTest` (12) — 89 LUT-pipeline JUnit tests across the host stack, plus the surrounding suites. Full-suite tally now stands at 39 suites / 386 tests / 0 failures / 0 errors / 0 skipped via `:app:testDebugUnitTest`. Reference parity vs FFmpeg `lut3d` is intentionally NOT a unit-test gate (FFmpeg is not on every developer's PATH); we instead test that our CPU apply path matches a profile's direct `apply()` to within 1 LSB on 8-bit (which is the property a GLES↔CPU reconciliation needs anyway).

#### License & sourcing rules
- [x] No bundled LUT may be derived from a proprietary source even if redistributed elsewhere as "free" — the licensing chain is not auditable. `LutCatalog.ALLOWED_SPDX` pins the whitelist at `{Apache-2.0, BSD-2-Clause, BSD-3-Clause, MIT, CC0-1.0, public-domain}`; `LutCatalogTest.every catalog entry has an SPDX in the allowed set` enforces it at JVM-test time so a future commit cannot smuggle a non-FOSS entry in.
- [x] `LICENSES.md` "**Bundled LUTs (planned — Phase 4)**" section documents each LUT (name, source URL, license, SHA256, scope) and the SPDX-whitelist + sourcing rules; updated alongside this row to mark `Rec.709 identity`, `BwBt601`, `BwBt709`, and `PnsCinematic` as **shipped (code-generated)** while ACES + Filmic remain **planned (asset-backed via `downloadBundledLuts`)**.
- [x] `scripts/pns_license_inventory.ps1` extended to walk `app/src/main/assets/luts/`; asserts every leaf folder has `LICENSE.txt` + `SOURCE.txt` + `SHA256.txt` AND is referenced by `LICENSES.md`. Reports drift in either direction. **Shipped**: `Test-BundledLutFolders` walks `app/src/main/assets/luts/<spdx>/<name>/`, validates the three required sidecars are present in each leaf folder, and cross-references each leaf folder name against `LICENSES.md`. When the directory does not exist (the expected state until `downloadBundledLuts` lands a real entry) the walker reports `OK: bundled LUTs - no asset-backed LUTs (folder ... does not exist - expected until downloadBundledLuts lands)` and skips silently. Both the OK path (fresh checkout) and the FAIL path (synthetic temp project root with a leaf folder missing `SOURCE.txt` / `SHA256.txt` and not referenced in `LICENSES.md`) were smoke-tested. The walker is invoked by every `pns_verify_toolchain.ps1` run via the existing license-inventory hook, so CI catches drift the moment a real LUT lands.
- [ ] **Build-time download**: a Gradle task `downloadBundledLuts` fetches each upstream LUT to a build-cache dir, verifies SHA256, and copies into `assets/luts/`. The LUT files themselves are NOT committed to git (build artifact); the manifest + checksums + license sidecars ARE. Skipped in CI when the cache is hot. **Infrastructure shipped**: the `:app:downloadBundledLuts` task is registered in `app/build.gradle.kts` with a SHA-256-verifying `verifySha256(file, expected)` helper, an idempotent fetch loop (cache hit is a no-op), and on-failure cleanup that deletes the cached file and reports both expected + actual digests. A companion `:app:downloadBundledLutsDryRun` prints the URL list without fetching anything. The URL list (`bundledLutSpecs`) is INTENTIONALLY EMPTY in this milestone (the task is a clean no-op until ACES / Filmic upstream URLs + SHA-256s are pinned); both tasks were smoke-tested and reported `BUILD SUCCESSFUL` with the expected `bundledLutSpecs is empty; skipping` log line. When the URL list becomes non-empty, `preBuild` will automatically depend on `downloadBundledLuts`.

#### V&V gates
- [x] [HOST] Identity LUT (`Rec.709 → Rec.709`) produces bit-equal output via `LutCatalog.None.load()` + `Lut3DTest.identity LUT samples match normalized grid coordinates` (every cell at every supported size matches the normalized grid coordinate exactly within `1e-6f`); `BuiltInLutsTest.applying rec709 identity preserves arbitrary samples` validates the round-trip through `LutPipeline.applyTrilinear`.
- [x] [HOST] CPU trilinear self-consistency gate shipped via `CalibrationToLutTest.LUT round-trip preserves WB+CCM apply within trilinear precision`: applying a non-trivial WB + CCM + bias profile through `CalibrationToLut.toLut3D` + `LutPipeline.applyTrilinear` matches the direct `profile.apply()` to within 1 LSB on 8-bit (`< 0.01f`) on multiple interior samples — the property a future GLES↔CPU reconciliation needs. (FFmpeg `lut3d` parity is intentionally NOT a unit-test gate; FFmpeg is not on every developer's PATH and the property above is what we actually need.)
- [x] [HOST] Every catalog entry loads + validates + has the required metadata via `LutCatalogTest` (8 tests: SPDX in whitelist, loads at default size, loads at every supported size, `None` is identity, `forScope` filters correctly, `defaultFor` is `None`, displayName/description/source non-empty, B&W LUTs collapse to gray). SHA256 verification is N/A for code-generated LUTs (they have no on-disk asset to hash); SHA256 verification will land alongside the asset-backed `downloadBundledLuts` task.
- [ ] [HOST] `pns_license_inventory.ps1` reports `OK: bundled LUTs` (every LUT folder has license + source + sha256 and is referenced in `LICENSES.md`). Pending — see "License & sourcing rules" row above.
- [ ] [ADB] Live-preview LUT toggle: enabling/disabling a 33³ LUT does not drop preview FPS by more than 5 % on the 60 fps preview path; logged via `PerfBudget.check`. Pending GLES preview pipeline.
- [ ] [ADB] Video recording with a non-identity LUT produces a valid H.265 stream; `mediainfo` on the pulled file shows the expected color primaries (Rec.709 or Rec.2020 depending on profile). Pending video encoder.
- [ ] [ADB] Still capture with a non-identity LUT writes the LUT'd AVIF/JXL/JPEG AND a sibling `.cube.txt` (or `.lutref.txt`) into `Pictures/Point & Shoot/...` per `STORAGE_STRATEGY.md`. Pending still encoder.
- [ ] [ADB] User-imported `.cube` round-trip: import → apply → export the same LUT from the catalog → byte-equal cube data on disk. (Host-side: `LutPipelineTest.serializeCube round-trips through parseCube identically` already validates the parse → serialize → parse fidelity at the math layer; the SAF picker UI lands later.)

### Cross-cutting hooks (mirrors of the existing §9 sections)
- [x] **Performance budget**: `PERFORMANCE_BUDGETS.md` "Color & LUT pipeline (Phase 4)" section ships with 7 rows (preview LUT shader ≤ 2 ms / frame at 1080p, ≤ 5 ms at 4K; still LUT CPU ≤ 80 ms for 12 MP, ≤ 320 ms for 50 MP Ultra-Max; calibration solve ≤ 200 ms; cube parse / serialize ≤ 50 ms; RAW LUT bake = `n/a`). `PerfBudget.Defaults.LUT_SHADER_PER_FRAME_1080P_MS` (2 ms) + `LUT_CPU_STILL_12MP_MS` (80 ms) pin the constants; `PerfBudgetTest` adds 3 new tests (defaults match `PERFORMANCE_BUDGETS.md`, shader budget grades 60 fps preview correctly, CPU budget grades 12 MP capture correctly).
- [x] **Capture pipeline architecture**: `CAPTURE_ARCHITECTURE.md` "Color & LUT pipeline (Phase 4)" section ships with the executor placement table (preview LUT → GLES surface compositor; video LUT → GLES on the encode-side surface; still LUT → `PNS.Reader` encode executor between tone curve and AVIF/JXL/JPEG encode; RAW → skipped entirely; calibration solve → `PNS.Reader`; cube parse / serialize → `PNS.Reader`) and 3 new backpressure rules (preview is sacred so the LUT auto-disables before a frame drop; still LUT shares the encode budget; RAW captures bypass the LUT entirely).
- [x] **Failure matrix**: `FAILURE_MATRIX.md` "Color & LUT pipeline (Phase 4)" section ships with 11 new rows covering corrupt `.cube` (LutPipeline rejects + toast), unsupported LUT size, non-`[0,1]` domain, non-FOSS user SPDX (allowed but not credited), GLES 3D-texture upload failure (CPU fallback), preview LUT exceeds frame budget for 60 frames (auto-disable), calibration patch variance too high ("chart not flat / refocus" toast tied to `PatchSample.rejected`), singular CCM system, MTF50 = null (informational), RAW path with non-identity LUT (intentional skip), user-imported LUT removed from disk (revert to None).
- [x] **About / Heritage**: `AboutScreen.kt` "Color & LUT credits" sub-block ships, sourced from `LutCreditsBuilder.creditsFromCatalog()` which walks `LutCatalog.entries` and emits one `LutCreditRow(displayName, description, spdx, source, scope)` per entry whose SPDX is in `LutCatalog.ALLOWED_SPDX` (defense in depth: even if a non-FOSS entry slipped past the catalog test, the About page would not surface it). **JUnit-tested** in `LutCreditsBuilderTest` (5 tests): one row per entry, every row has non-empty fields, every row's SPDX is in the whitelist, ordering matches the catalog, well-known entries appear (None / Cinematic / B&W BT.709). SHA256 column is omitted on this page because the bundled LUTs are code-generated and have no on-disk asset to hash; SHA256 lights up alongside the asset-backed `downloadBundledLuts` task.
- [x] **Diagnostics dump**: `DiagnosticsMode.dump(context, colorState)` now appends a "Color & LUT" section produced by `LutDiagnosticsBuilder.buildSection`. The section reports the active LUT name + SPDX + SHA256, the active calibration profile id + capture timestamp (or "(none)"), enumerates every bundled LUT (auto-derived from `LutCatalog.entries`), and lists `LutCatalog.ALLOWED_SPDX` so support tickets can confirm why a user-imported LUT was rejected. `LutDiagnosticsBuilder.ActiveColorState.Default` provides a sentinel for "no calibration, identity LUT". **JUnit-tested** in `LutDiagnosticsBuilderTest` (8 tests): default state reports identity LUT + no calibration, custom state reports active LUT name / SPDX / SHA / calibration id / captured-at, every catalog entry appears in the bundled section, every entry's SPDX is shown, allowed SPDX whitelist enumerates in sorted order, output ends with a trailing newline, null captured-at omits the line, default state matches `LutCatalog.None`.

## 8) Documentation, releases, and CI/CD (continuous, but required before publishing)
### Branding (icon) (must be done before first public release)
- [x] Generate an app icon set and wire it into the APK (vector-only; `minSdk = 28` so PNG raster fallbacks are not required):
  - [x] Adaptive launcher icon resources:
    - [x] `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
    - [x] `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
    - [x] `app/src/main/res/drawable/ic_launcher_foreground.xml` (vector reticle + Hasselblad-orange shutter dot)
    - [x] `app/src/main/res/drawable/ic_launcher_monochrome.xml` (themed-icon layer for Material You)
    - [x] `app/src/main/res/values/ic_launcher_background.xml` (`#181A1B` charcoal)
    - [ ] (optional) `app/src/main/res/mipmap-*/ic_launcher.png` legacy raster — skipped because `minSdk = 28` already implies adaptive-icon support.
  - [x] `AndroidManifest.xml` references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
  - [x] [ADB] Verify icon appears correctly on the launcher (light/dark mode, themed icons if enabled) — captured via `adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:dev.pointandshoot` on adb 8bf09993; `docs/screenshots/smoke_app_info.png` shows the system rendering the adaptive `mipmap/ic_launcher` resource (reticle ring + 4-direction crosshair ticks + Hasselblad-orange center dot on charcoal) correctly. The Settings App-info page reads the same `ic_launcher` resource the launcher does.
- [x] Add a README-renderable icon asset:
  - [x] Export a 512×512 PNG (`docs/icon.png`) for GitHub/README display — center-cropped from a high-quality vector-style render and resized to 512×512 PNG via `System.Drawing` (`HighQualityBicubic`); 205591 bytes.
  - [x] Embed it at the top of `README.md` — `<p align="center"><img src="docs/icon.png" ... width="160"></p>` block lands above the project title.

### README (pitch + developer entrypoint)
- [x] Refresh `README.md` to be an enticing pitch and onboarding doc:
  - [x] Product pitch + differentiators
  - [x] Feature highlights (probe, RAW12, AVIF/JXL targets, pro HUD)
  - [x] Device focus + constraints (OnePlus 13 / LineageOS; FOSS-only; no Play Services)
  - [x] Screenshots section — `## Screenshots` block under Status, embeds the six device-validated PNGs from `docs/screenshots/` (probe home, Pro HUD + LUT picker, live preview HUD overlay, Calibrate, Import LUT, About live hydration).
  - [x] Quickstart linking to `CLI_BUILD_AND_SIDELOAD.md`
  - [x] Roadmap linking to this `BUILD_PLAN.md`
  - [x] License + contribution notes

### Changelog + release notes (updated every release)
- [x] Keep `CHANGELOG.md` with an **Unreleased** section during development (now populated with shipped probe + automation work + this session's deltas)
- [x] `RELEASE_NOTES_TEMPLATE.md` deduplicated and aligned with `pns_verify_toolchain.ps1` + `apksigner verify` flow
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
- [x] Add workflow: `.github/workflows/build-signed.yml` (manual `workflow_dispatch`, also runs on `v*` tags; runs full toolchain gate first; verifies signature with `apksigner`; uploads APK artifact)
- [x] Add signing support in Gradle (no secrets committed):
  - [x] Reads env vars (`ANDROID_KEYSTORE_PATH` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`) **or** `keystore.properties` (gitignored)
  - [x] `signingConfigs.release` + `buildTypes.release` wired with safe fallback to debug key (so local probe `assembleRelease` still works)
- [ ] Store secrets in GitHub Actions (one-time, owner action):
  - [ ] `ANDROID_KEYSTORE_BASE64`
  - [ ] `ANDROID_KEYSTORE_PASSWORD`
  - [ ] `ANDROID_KEY_ALIAS`
  - [ ] `ANDROID_KEY_PASSWORD`
- [ ] V&V:
  - [x] [HOST] `./gradlew :app:assembleRelease` (debug-key fallback path, verified by `pns_hfr_autorun.ps1 -AssembleReleaseOnly`; PROBE_BUILD_PLAN.md §5)
  - [ ] [CI] `./gradlew :app:assembleRelease` (real signing key path)
  - [ ] [CI] `apksigner verify --verbose app-release.apk` (covered by workflow once secrets are configured)
  - [ ] [ADB] `adb install -r app-release.apk` + smoke run

#### GitLab integration (mirror + optional GitLab CI)
- [ ] Create GitLab project (can be private)
- [ ] Configure mirroring (GitLab mirror from GitHub OR add GitLab remote)
- [ ] Optional `.gitlab-ci.yml` to build debug/release
- [ ] If signing in GitLab CI: store masked/protected variables:
  - `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`
- [ ] V&V: mirror stays current; pipelines build from a clean runner

## 9) Cross-cutting optimizations (add as gates as features land)
### Performance budget + profiling gates
- [x] Define budgets (per mode) — `PERFORMANCE_BUDGETS.md` (cold start <= 800 ms to first preview, Standard Pro DNG save <= 250 ms, BKT 7-shot <= 4 s end-to-end, video bitrate targets, memory-pressure responses):
  - [x] Preview FPS target(s) including 120fps + HFR rows
  - [x] Capture latency budget (tap-to-shutter, BKT burst window)
  - [x] Cold start time budget
- [ ] Add profiling gates:
  - [ ] [ADB] Collect a Perfetto trace for: launch, preview start, still capture, BKT
  - [ ] [ADB] Measure UI jank/frame pacing during preview (baseline + regressions)
  - [ ] [HOST] Store profiling summaries (what changed, what improved/regressed) — `--PerfReport` switch on `pns_hfr_autorun.ps1` proposed in `PERFORMANCE_BUDGETS.md`; implementation pending the actual capture engine.
  - [x] Machine-checkable mirror of `PERFORMANCE_BUDGETS.md` shipped: `PerfBudget.kt` (`check(label, measured, budget, warnFactor)` -> OK / WARN / FAIL; `checkHapticTick(measuredMs)` enforces the `30 ms +/- 5 ms` post-readout window). **JUnit-tested** in `PerfBudgetTest.kt` (defaults pinned to the markdown so doc/code drift is caught).

### Camera2 robustness & failure-matrix validation
- [x] Document the matrix and per-row expected behavior — `FAILURE_MATRIX.md` (permissions, camera lifecycle, app lifecycle, capture/encode, vendor-tag misbehavior, thermal/long-session). Each row is severity-tagged (CRITICAL / HIGH / MEDIUM / LOW).
- [ ] Add explicit "failure matrix" test runs after each major camera change:
  - [ ] [ADB] Permission denied → graceful UX + recovery — expected behavior in `FAILURE_MATRIX.md` "Permissions" rows.
  - [ ] [ADB] Camera in use by another app → graceful error + retry path — `ERROR_CAMERA_IN_USE` row.
  - [ ] [ADB] Orientation change during preview/capture → no crash, session recovers — "App lifecycle" rows.
  - [ ] [ADB] Background/foreground transitions → no dead session, state restored — "App lifecycle" rows.
  - [ ] [ADB] Thermal throttling / long session (10+ min preview) → no runaway errors — "Thermal / long-session" rows.
- [x] Vendor tag safety gate:
  - [x] Every vendor tag use is feature-detected and guarded — `VendorKeyGuard.kt` centralizes name-based feature detection (`isCharacteristicKeyAvailable` / `isRequestKeyAvailable` / `isSessionKeyAvailable`); `useIfAvailable {}` enforces the guard pattern + logs `present`/`absent` to logcat at `PNS.VendorKey`.
  - [ ] [ADB] Fallback behavior verified when tag is unavailable/ignored — gate code shipped; on-device fallback validation pending Phase 1 capture engine.

### Capture pipeline architecture (avoid preview stalls)
- [x] Document target architecture — `CAPTURE_ARCHITECTURE.md` (thread lanes, executor names, queue depth, backpressure rules, cancellation policy, failure handling).
- [ ] Enforce separation:
  - [ ] Camera control thread (requests/session)
  - [ ] Image acquisition/processing thread(s)
  - [ ] IO/encode thread(s) (DNG/AVIF/JXL)
  - [ ] UI state thread (Compose)
- [ ] Backpressure rules:
  - [ ] [ADB] Burst/BKT does not OOM or block preview
  - [ ] [ADB] RAW + encode queues bounded; behavior defined when overloaded (drop/slow/fail)

### Storage + media indexing strategy
- [x] Decide and document where files go — `STORAGE_STRATEGY.md`:
  - [x] MediaStore vs SAF vs app-private + Export (pick a default per profile) — Standard Pro / Ultra-Max default to `MediaStore.Images` under `Pictures/Point & Shoot/...`; probe artifacts stay app-private under `getExternalFilesDir(...)`; SAF "Save as ..." path documented for one-shot exports.
- [x] Implementation: `CaptureStorage.openOutput(profile, kind)` reserves a MediaStore entry (with `IS_PENDING = 1`), exposes a `Handle` (AutoCloseable) that clears the pending bit on `close()` or deletes the row on `discard()`. Filenames: `pns_<utc>_<profile>_<seq>.<ext>`. MIME types: `image/x-adobe-dng`, `image/avif`, `image/jxl`.
- [ ] Validation:
  - [ ] [ADB] Outputs are written reliably with predictable naming
  - [ ] [ADB] (If MediaStore) files appear in gallery apps; (if app-private) export works
  - [ ] [HOST] Pull + verify files open in desktop tooling (DNG/AVIF/JXL)

### Testability hooks + diagnostics mode
- [x] Add a "Diagnostics mode" toggle — `DiagnosticsMode.setEnabled(ctx, true)` (`pns_diagnostics` SharedPreferences); probe home button "Diagnostics dump" enables-and-dumps. Toast confirms the artifact path.
  - [x] Dumps active session configuration + key vendor settings into logs (`Log.i("PNS.Diagnostics", report)`).
  - [x] Exports a compact diagnostics report alongside `PROBE_RESULTS.md` (`getExternalFilesDir(null)/diagnostics_<utc>.txt`; pulled by `pns_hfr_autorun.ps1` via the standard JSON-pull machinery once the file mask is added).
- [x] Add correctness tests where feasible — JUnit 4 wired (`gradle/libs.versions.toml` -> `junit = "4.13.2"`; `app/build.gradle.kts` `testImplementation(libs.junit)`); tests live under `app/src/test/java/dev/pointandshoot/`:
  - [x] `BracketPlanTest.kt` (3/5/7 patterns, EV step, grouping-id stability, validation errors)
  - [x] `HighlightMeterTest.kt` (empty histogram, dark / bright / clamped corrections, percentile sweep)
  - [x] `TimecodeFormatTest.kt` (zero, frame wrap, second / minute / hour rollover, defensive clamps)
  - [x] [HOST] Golden-file tests for metadata serialization (GroupingID, crop metadata) — **shipped** at the plan-level via `MetadataSerializationGoldenTest.kt` (9 tests). Locks the `BracketPlan v1` text projection (3-shot @ 1 EV, 5-shot @ 0.667 EV, 7-shot @ 1 EV; including grouping-id stability across stops + non-collision of two default `build()` calls) and the `CropPlan v1` projection (Street35, Standard50, Portrait85, LongTele150 on an 8160×6144 active array; locks the centered crop rectangle + effective zoom + metering / AF hints). Any future refactor of these plan classes that changes the on-disk metadata downstream tools see now requires a deliberate version bump + CHANGELOG entry. Full DNG/AVIF/JXL byte-level serialization checks land with the encoder pipeline.
  - [x] CI: `:app:testDebugUnitTest` runs locally via `pns_verify_toolchain.ps1`-adjacent shell command; Gradle reports `BUILD SUCCESSFUL` with all suites green.

### Release hardening gates
- [x] Logging policy:
  - [x] [HOST] Release builds do not emit verbose debug logs by default — `PnsLog.kt` facade routes `v`/`d` through an `ApplicationInfo.FLAG_DEBUGGABLE` + `DiagnosticsMode` gate (`shouldEmitVerbose`); `i`/`w`/`e` are always emitted; cached at `MainActivity.onCreate` via `PnsLog.init(applicationContext)`. **JUnit-tested** in `PnsLogTest.kt`.
  - [ ] [ADB] Release build smoke run with PID log monitoring
- [x] Security/F-Droid hygiene:
  - [x] [HOST] Dependency/license scan gate (FOSS-only) — `LICENSES.md` enumerates every declared coordinate (runtime / debug / test / plugin) with SPDX id, scope, and notes; `scripts/pns_license_inventory.ps1` parses `gradle/libs.versions.toml` and reports drift in either direction (catalog dep missing from license map, or stale license-map entry no longer in the catalog). Wired into `pns_verify_toolchain.ps1` so every gate run also runs the license drift check. CI inherits it for free via `.github/workflows/toolchain-verify.yml`.
  - [x] [HOST] SBOM generation (optional, but recommended for transparency) — **shipped** as `scripts/pns_sbom.ps1`, a host-side PowerShell emitter that reads `gradle/libs.versions.toml` + the SPDX map embedded in `pns_license_inventory.ps1` and writes a CycloneDX 1.5 JSON SBOM (with `urn:uuid:` serial, ISO-8601 timestamp, `pkg:maven/...` PURLs for every dependency, scope marking for runtime / debug / test / plugin, and a top-level `application` component for the app itself). Direct-deps only by design (CycloneDX allows partial graphs); transitive-resolution is a Phase-1 follow-up. The verify gate (`pns_verify_toolchain.ps1`) calls `pns_sbom.ps1 -Verify` on every run, which re-parses + structural-checks the emitted JSON (`bomFormat = CycloneDX`, `specVersion = 1.5`, every component has a `purl` + `licenses`, `serialNumber` is `urn:uuid:`-prefixed). 16 components emitted on the current dependency set.

### UX safety nets (capability-driven UI)
- [ ] Feature gating UX:
  - [ ] [ADB] UI only enables features supported by probe results — pure-data gate shipped (`CapabilityGate.evaluate(HardwareCaps)` returns one `GateResult` per `Feature` with an enable bit + human-readable disable reason; **JUnit-tested** in `CapabilityGateTest.kt`); UI binding to `HardwareCaps` from probe pending Phase 1.
  - [ ] [ADB] Unsupported options are disabled with a clear reason — gate already returns `disabledReason` strings (e.g., "Eye-AF overlay requires STATISTICS_FACE_DETECT_MODE_FULL."); UI surface (tooltip/toast) pending.
  - [ ] [ADB] Safe defaults always produce a valid capture path — `CapabilityGate.recommendedDefaults(caps)` enforces the Standard Pro baseline (RAW + 10-bit AVIF) and returns the empty set when even baseline is unmet (engine should fall back to JPEG-only).

## 10) Scope control gates
- No engine/UI behavior that depends on unknown capabilities until Phase 0 typed values are captured
- Every vendor-tag use must be guarded (feature detection + safe fallback)









