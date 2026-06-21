# Capture engine architecture

Source-of-truth for **threading, queueing, and lifecycle** of the Phase 1+ capture engine. Satisfies the BUILD_PLAN §9 cross-cutting requirement: "Camera control thread + image-reader thread separation; backpressure rules; cancellation policy".

This document describes the **target** architecture. It will land incrementally as Phase 1 progresses; the existing `PreviewEngineScreen.kt` (probe) and the new helpers (`Dng12Saver`, `CaptureStorage`, `BracketPlan`, `HighlightMeter`, `CaptureHaptics`) already follow these rules.

## Threads & executors

| Lane | Backed by | Used for | Key constraint |
|---|---|---|---|
| **UI / Compose** | Android main looper | Compose state, gesture (`Modifier.tapToShoot`), navigation, Toast | Never blocks; never opens Camera2 device |
| **Camera control** | `HandlerThread("PNS.Cam")` | `CameraDevice` open/close, `CameraCaptureSession` create/destroy, `setRepeatingRequest`, `capture`, `captureBurst` | Single-threaded. All Camera2 callbacks dispatch here. |
| **Image reader / encode** | `Executors.newSingleThreadExecutor("PNS.Reader")` | `ImageReader.OnImageAvailableListener`, `DngCreator.writeImage`, AVIF / JXL encode (NDK) | Single-threaded so we never overlap two saves. Writes flow into `CaptureStorage` which uses `MediaStore`. |
| **JPEG companion (post-RAW)** | `Executors.newFixedThreadPool(2)` (threads named **`PNS.Jpeg`**) | Hardware JPEG decode / rotate / LUT / recompress for RAW+JPEG companions | Runs off main; activity **`ON_PAUSE`** best-effort drains the pool (**2 s**); metadata + `MediaStore` inserts stay on **`PNS.Reader`**. |
| **Histogram / metering** | `Executors.newSingleThreadExecutor("PNS.Meter")` | Downsample preview YUV -> 256-bin luma histogram, call `HighlightMeter.suggestEvCorrection` | Drops frames freely (see backpressure). |
| **Haptics** | Main thread + `Handler.postDelayed` | `CaptureHaptics.scheduleStillTick()` (30 ms post-readout) | Light; safe on main. |
| **Diagnostics dump** | Caller thread (button press) | `DiagnosticsMode.dump` | One-shot. Acceptable to do from main because it's user-initiated and fast. |

### Lifecycle ownership

* The `CameraDevice` is opened on the camera-control thread and **lives for the entire active screen** (probe / Pro HUD). Reopens happen only on configuration change or error.
* Each `CameraCaptureSession` is owned by the screen that created it. Mode switches (e.g., switching profile from Standard Pro to Ultra-Max with a different RAW format) tear down the session and rebuild it on the camera-control thread.
* The image-reader / encode executor is created with the screen and shut down in `onDispose` / `onStop`. In-flight image saves get a 5-second drain window before forced shutdown.

## Backpressure rules

These rules exist because **the sensor never stops** - even when we cannot keep up with saves, the preview must remain smooth.

1. **Preview is sacred.** The preview SurfaceTexture target is always attached to the repeating request. If something has to be dropped, drop *anything but* the preview frame.
2. **Histogram metering drops freely.** When the meter executor is busy, the next preview frame intended for histogram analysis is silently dropped (no queue). The next frame replaces it. The meter therefore runs at "as-fast-as-it-can-finish" rather than "every frame".
3. **Image-reader queue depth is bounded.** `ImageReader` is constructed with **`maxImages = PerfBudget.Defaults.STILL_IMAGE_READER_MAX_IMAGES`** (4). If the queue fills, the *oldest* frame is dropped (`acquireLatestImage()` semantics). Diagnostic counters log every drop with `Log.w("PNS.Reader", "drop oldest queue=N")`.
4. **Burst captures (BKT 3/5/7) reserve full queue capacity.** Before starting a sequential RAW bracket, the engine waits up to **`PerfBudget.Defaults.ENCODE_LANE_DRAIN_WAIT_MS` (200 ms)** for the encode executor (`PNS.Reader`) to drain (noop `Future.get`), then best-effort discards any orphaned **`ImageReader`** frames. If the drain times out, the bracket is rejected with a Toast ("Engine busy - retry") and **`PNS.AdbValidation`** logs **`captureBracketBurst … err=encode_lane_busy`**. Camera2 **`captureBurst(List)`** with AE/AWB lock is a future optimization when HAL correlation with dual `ImageReader` outputs is proven on the dodge fleet; the shipped engine remains **sequential `capture()`** per stop.
5. **Video and stills don't overlap.** While `isRecording == true`, still-capture taps are ignored. The HUD's `RecordButton` and the still-capture path share a single `AtomicReference<EngineState>`; the gesture handler reads this before forwarding to `TapToShootHandler`.

### Host evidence rollup (Sprint 7.3)

After **`pns_adb_preview_validate.ps1`** (or any run that saves **`logcat_*.txt`** under **`hfr-runs/`**), classify **`PNS.Reader`** **`drop oldest`** lines with **`scripts/pns_analyze_reader_backpressure.ps1`** (**`-LogDir`** or **`-LogPath`**). Prefer **`-OutFile`** under **`perf-runs/`** next to **`perf_*.md`**. Use the **acceptance gates** below when closing **`BUILD_PLAN.md`** Sprint **7.3** backpressure evidence.

### Sprint 7.3 acceptance gates (`pns_adb_preview_validate`)

These gates pair **`scripts/pns_analyze_reader_backpressure.ps1`** output with the bracket / encode-lane rows in **`PERFORMANCE_BUDGETS.md`** (especially **In-flight queue overflow = 0** and **`ENCODE_LANE_DRAIN_WAIT_MS`** / **`encode_lane_busy`** semantics).

**Baseline log bundle (same `-OutDir`):** combine only

- **`logcat_raw_still_x10.txt`** — ten sequential RAW stills (`Run-Scenario raw_still_x10` in **`pns_adb_preview_validate.ps1`**; 180 s wall budget for capture + IO), and  
- **`logcat_bracket_bkt3.txt`** — sequential RAW BKT×3 (`Run-Scenario bracket_bkt3`).

Run, for example (from repo root; use **`-Command`** so **`@(...)`** binds cleanly to **`[string[]]$LogPath`**):

`powershell -NoProfile -Command "& .\scripts\pns_analyze_reader_backpressure.ps1 -LogPath @('<OutDir>\logcat_raw_still_x10.txt','<OutDir>\logcat_bracket_bkt3.txt') -OutFile .\perf-runs\reader_backpressure_validate_raw_and_bkt3.md"`

**Pass criteria for fleet evidence (reference hardware, e.g. OnePlus legacy SKU):**

| Check | Target |
|-------|--------|
| **`encode_lane_busy`** (`PNS.AdbValidation`) | **0** hits in the two logs combined |
| **`PNS.Reader` encode lane drain timed out** | **0** |
| **`drop oldest` with `queue=superseded`** | **0** total (still + bracket channels) — supersede implies the encode lane took a newer buffer before finishing the prior still; should not occur during healthy sequential RAW / BKT3 on the baseline scenarios |

**Informational (not automatic failures):** **`queue=pre-bracket-drain`** (intentional **`ImageReader`** discard before BKT) and **`queue=post-process`** (listener saw **`processed`** / abandoned path) may appear depending on timing; call them out in **§5** if non-zero.

**Out of scope for this baseline:** arbitrary-length bursts, manual “machine-gun” taps, or pathological storage — capture separate logs, extend targets here, or document a **§5** waiver.

## Cancellation policy

* **Mode switches cancel in-flight non-still work.** Switching dial mode (M / H / S / BKT) calls `setRepeatingRequest` with the new request and **does not** wait for any pending still callback to fire. The still callback either lands shortly after the switch (its `CaptureResult` is honored if it arrives within 250 ms) or is treated as orphaned and discarded.
* **Bracket cancellation is all-or-nothing.** If the user lifts off in the middle of a bracket, the engine waits for the in-flight sequential still sequence to complete naturally (Camera2 does not offer fine-grained cancel per slot on all OEMs) but discards any results that arrive after the cancel timestamp.
* **`onPause` shuts the camera.** The camera-control thread calls `cameraDevice.close()` from `onPause`; the encode executor is given 1 s to drain before forced shutdown.

## Color & LUT pipeline (Phase 4)

The color pipeline lives **between** the existing capture stages and the final encode/send-to-display step. See `COLOR_PIPELINE.md` for the end-to-end stage ordering and pinned constants; this section describes only the threading + executor placement so the rules above stay self-contained.

| Stage | Lane | Notes |
|---|---|---|
| Preview LUT (live HUD overlay) | **GLES surface compositor** thread (the same thread that owns the preview `SurfaceTexture`) | A single `sampler3D` upload + trilinear lookup in the existing fragment shader; no extra Java thread. The repeating preview request remains attached to a `SurfaceTexture` target; the LUT step happens during compositing into the on-screen `Surface` |
| Video LUT (recording lane) | **GLES** (encode-side surface) | The MediaCodec input `Surface` is wrapped by the same shader as preview; this guarantees what the user sees through the viewfinder matches what is encoded |
| Still LUT (CPU pass) | **Image reader / encode executor** (`PNS.Reader`) | Runs after tone curve, before AVIF/JXL/JPEG encode. Uses `LutPipeline.applyTrilinearInto` (allocation-free) so the encode lane stays GC-quiet. Budgeted at <= 80 ms for 12 MP (`PerfBudget.Defaults.LUT_CPU_STILL_12MP_MS`) |
| RAW path | **Skipped entirely** | RAW (DNG12) is *never* baked through a LUT (per `COLOR_PIPELINE.md`'s "RAW is sacred" rule). The active LUT id + SHA256 ride along as DNG metadata so the desktop converter can re-apply later |
| Calibration solve | **Image reader / encode executor** (`PNS.Reader`) | One-shot per calibration session: `CalibrationSampler.sample` -> `CalibrationMath.computeWbGains` / `computeCcm` -> `CalibrationToLut.toLut3D`. Budgeted at <= 200 ms total |
| LUT cube parse / serialize | **Image reader / encode executor** (`PNS.Reader`) when triggered by import; **camera-control thread** is never touched | `LutPipeline.parseCube` is pure-CPU; SAF picker callbacks dispatch to the encode lane to keep main responsive |

### LUT-pipeline backpressure rules (additions)

1. **GLES LUT does not change preview backpressure** - the shader runs in the existing compositor pass; if it ever pushes a frame past its 16.7 ms budget at 60 fps, the preview is sacred (rule 1) so the LUT is *disabled* with a transient toast and `DiagnosticsMode.dump` records the regression rather than dropping the preview frame.
2. **Still LUT runs strictly serially** with DNG / AVIF / JXL save - the encode lane is single-threaded and the LUT step shares its budget with the encoder. If the LUT pass exceeds `LUT_CPU_STILL_12MP_MS` it logs a `PerfBudget.check(... severity=WARN)` event but does not interrupt the encode.
3. **RAW captures bypass the LUT entirely** - the saver checks `frame.outputKind == RAW` and skips the LUT pass with a single-pixel-cost no-op.



* `CameraDevice.StateCallback.onError` triggers an exponential-backoff reopen (250 ms, 500 ms, 1 s) up to 3 attempts. After the last failure, the screen surfaces an error message and offers a "Retry" button.
* `CameraCaptureSession.StateCallback.onConfigureFailed` logs the offending stream configuration to `PNS.SessionMatrix` (the same tag the probe uses) and falls back to the previous known-good session, if any.
* MediaStore insert failures (no external storage, IO error) cause the saver to call `CaptureStorage.Handle.discard()` - the partial row is removed.

## Tying it back to the BUILD_PLAN

* §0 / §8: this doc + the executor names above satisfy the "Camera control thread + image-reader thread separation" requirement.
* §4 (Phase 1) sensor-stability protocol: `CaptureHaptics.scheduleStillTick()` is invoked from the camera-control thread's `onCaptureCompleted`, posting back to the main looper for the actual vibrator call.
* §8 vendor-tag safety: every Camera2 vendor tag must go through `VendorKeyGuard.useIfAvailable` from the camera-control thread.
* §8 storage strategy: `CaptureStorage.openOutput` must be called from the encode executor (it uses `ContentResolver.openOutputStream`, which is fine off-main but slow enough that we never want it on the camera-control thread).

## Outstanding decisions (revisit during Phase 1)

* AVIF vs JXL encoding inside the encode executor vs a separate `Executors.newFixedThreadPool(2, "PNS.Encode")`. Decision deferred until we benchmark `libavif` and `libjxl` on the legacy device.
* Whether the histogram path becomes `RenderScript`-free (RenderScript is deprecated; we will likely use a tiny C++ kernel via JNI tied into the same NDK pipeline as AVIF / JXL).

## Sprint 28.1 pipeline audit (Milestone 28)

Evidence doc: [`docs/CAMERA_APP_PIPELINE_BENCHMARK.md`](docs/CAMERA_APP_PIPELINE_BENCHMARK.md) G1–G8 table. **USB gates PASS** on **b5214fc6** (2026-06-20).

| Id | Code audit (host) | USB gate | Notes |
|----|-------------------|----------|-------|
| **G1** Audio routing | **PASS** | `pns_audio_quality_test.ps1` **PASS** | `audio_quality_test_20260620_123511` |
| **G2** MediaStore pending | **PASS** | JVM smoke pending | `CaptureStorage` IS_PENDING contract |
| **G3** JPEG focus-lock | **PASS** | `pns_capture_pipeline_verify.ps1` **PASS** | `capture_pipeline_gate_20260620_123712` |
| **G4** Session configuration | **PASS** | capture pipeline **PASS** | §4a `streamHints=false` locked |
| **G5** Backpressure / lanes | **PASS** (doc match) | backpressure script pending | See § Sprint 7.3 |
| **G6** Lifecycle / GLES | **PASS** | chrome gate pending | `queueSetGeometry` only from `PreviewMainViewport` |
| **G7** RAW session outputs | **PASS** | capture pipeline **PASS** | logical RAW/DNG pairing |
| **G8** DNG loadability | **PASS** | `pns_aux_dng_capture_analyze.ps1` **PASS** | `aux_dng_capture_analyze_20260620_123721` |

**Regression locks verified (no flip):** §4a `streamHints=false` · §2 RAW tier bisect unchanged · DNG IFD-only patches · GLES single geometry writer.

**Last green USB (CPH2583, 2026-06-20):** Sprint **28.1** — `audio_quality_test_20260620_123511` · `capture_pipeline_gate_20260620_123712` · `aux_dng_capture_analyze_20260620_123721` (integrity + desktop open PASS; USB **b5214fc6**).
