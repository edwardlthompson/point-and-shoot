# Performance budgets

Source-of-truth for **performance budgets per capture mode**. Satisfies BUILD_PLAN §9 cross-cutting requirement: "Performance budgets (per mode) + ADB validation".

These budgets are *targets* for the OnePlus 13 (`dodge`) on LineageOS 23 (Android 16 / API 36) with the imaging engine in production form. Probe / scaffold builds may exceed these temporarily; CI and `pns_hfr_autorun.ps1` will track regressions once the engine lands.

## Cold start

| Metric | Budget | Measured how |
|---|---|---|
| Cold start to first preview frame | <= 800 ms | `am start -W -n dev.pointandshoot/.MainActivity` -> "TotalTime" |
| Cold start to "ready to shoot" (preview + AE/AF converged) | <= 1200 ms | Trace marker `pns.firstFrameReady` (TBD) vs `Application.onCreate` |
| Process resident memory after cold start | <= 180 MB PSS | `dumpsys meminfo dev.pointandshoot` |

## Live preview (per mode)

| Mode | Resolution | Target FPS | Frame deadline | Allowed dropped frames per minute |
|---|---|---|---|---|
| Standard Pro / preview | 1920x1080 | 30 | 33.3 ms | <= 3 |
| Standard Pro / preview (HFR) | 1920x1080 | 60 | 16.7 ms | <= 6 |
| Standard Pro / preview (HFR ultra) | 1920x1080 | 120 | 8.3 ms | <= 12 |
| Ultra-Max / preview | 1920x1080 | 30 | 33.3 ms | <= 6 |
| HFR sweep (probe) | 1920x1080 | 480 | 2.1 ms | (informational) |

## Still capture (Standard Pro)

| Stage | Budget | Notes |
|---|---|---|
| Tap UP -> shutter open | <= 80 ms | Includes `TapToShootHandler.onFire` -> Camera2 `capture` round-trip |
| Shutter open -> readout complete | sensor-bound | Logged but not budgeted (varies with shutter speed) |
| Readout complete -> haptic tick | 30 ms +/- 5 ms | Hard requirement (see `CaptureHaptics.POST_READOUT_TICK_DELAY_MS`) |
| Readout complete -> DNG written to MediaStore | <= 250 ms | `Dng12Saver.SaveStats.elapsedMs` |
| Readout complete -> AVIF written | <= 600 ms | Once NDK pipeline lands |
| Tap UP -> next shot accepted | <= 350 ms | Engine state machine ready bit |

## Still capture (Ultra-Max)

| Stage | Budget | Notes |
|---|---|---|
| Tap UP -> shutter open | <= 100 ms | Larger sensor read; budget loosened |
| Readout complete -> RAW12 DNG written | <= 600 ms | Uncompressed RAW12 is significantly larger |
| Readout complete -> JXL written | <= 1200 ms | Once NDK pipeline lands; budget will tighten with `libjxl` tuning |
| Tap UP -> next shot accepted | <= 800 ms | Single-shot mode; bracket-mode handled separately |

## Bracket capture (BKT 3 / 5 / 7)

| Stage | Budget | Notes |
|---|---|---|
| Bracket submit | <= 50 ms | `captureBurst(plan)` enqueue time |
| Inter-frame interval | sensor + AE bound | Logged via `BracketPlan.indexInBurst` timing |
| Full 7-shot bracket complete | <= 4 s | End-to-end (UI tap to last DNG persisted) |
| In-flight queue overflow | 0 | `ImageReader` queue must never exceed `maxImages = 4` while burst runs |

## Video recording

| Mode | Resolution | FPS | Bitrate target | Notes |
|---|---|---|---|---|
| Standard | 1920x1080 | 30 | 20 Mbps | AVC; HEVC if available and proven on probe |
| Standard | 3840x2160 | 30 | 80 Mbps | AVC fallback if HEVC fails (see PROBE_BUILD_PLAN encoder matrix) |
| Slow-mo (capture) | 1920x1080 | 240 | 40 Mbps | HFR session; played back at 30 fps -> 8x slow |
| Slow-mo (capture) | 1920x1080 | 480 | 80 Mbps | HFR session; played back at 30 fps -> 16x slow |

## Color & LUT pipeline (Phase 4)

| Stage | Budget | Notes |
|---|---|---|
| Preview LUT shader (1920x1080, 33^3 LUT, GLES `sampler3D` trilinear) | <= 2 ms / frame | Pinned in `PerfBudget.Defaults.LUT_SHADER_PER_FRAME_1080P_MS`. Must consume <= 12 % of the 60 fps preview frame budget; Phase 4 V&V gate caps regression at 5 % FPS drop when toggling the LUT |
| Preview LUT shader (3840x2160, 33^3 LUT) | <= 5 ms / frame | Same shader, 4x pixel count; budget loosened proportionally for 4K preview |
| Still LUT CPU pass (12 MP / 4000x3000, 33^3 LUT, trilinear) | <= 80 ms | Pinned in `PerfBudget.Defaults.LUT_CPU_STILL_12MP_MS`. Per-pixel cost target ~ 6.7 ns; runs on the still encode lane between tone curve and AVIF/JXL encode |
| Still LUT CPU pass (Ultra-Max 50 MP) | <= 320 ms | 4x pixel count vs 12 MP; absorbed into the existing 600 ms RAW12 + 1200 ms JXL Ultra-Max budgets |
| Calibration solve (WB gains + 3x3 CCM + LUT bake) | <= 200 ms | One-shot per calibration session; `CalibrationMath.computeCcm` + `CalibrationToLut.toLut3D(profile, 33)` on a single thread |
| LUT cube parse / serialize (33^3 entry) | <= 50 ms | `LutPipeline.parseCube` / `serializeCube`; bounded by 35,937 string parses |
| RAW LUT bake | n/a | RAW is **never** baked through a LUT (per `COLOR_PIPELINE.md`); the LUT id + SHA256 ride along as DNG metadata only |

## Memory pressure

| Trigger | Action |
|---|---|
| `onTrimMemory(LEVEL_MODERATE)` | Drop encode executor pool to 1 thread; clear histogram cache |
| `onTrimMemory(LEVEL_LOW)` | Reject new captures until queue drains; show transient HUD warning |
| `onTrimMemory(LEVEL_CRITICAL)` | Tear down session; reopen on next `onResume` |

## Validation gates (BUILD_PLAN §9)

* [ ] [HOST] `pns_hfr_autorun.ps1` adds a `--PerfReport` switch that pulls `am start -W` + `dumpsys meminfo` + `logcat | rg "PNS.Reader drop"` and emits `perf-runs/<utc>.md` summarizing budget adherence.
* [ ] [ADB] Each capture mode is exercised once per release-prep run; failures are surfaced in the Verification block of `RELEASE_NOTES_TEMPLATE.md`.
* [ ] [HOST] CI publishes a perf trend chart (deferred until at least 3 release-prep runs exist).

## Open questions

* Cold-start budget assumes `Theme.DeviceDefault.NoActionBar` and no splash screen. If a splash screen is later added, the budgets above shift by `splashDuration`.
* The HFR ultra (120fps) preview budget assumes `createConstrainedHighSpeedCaptureSession`; switching to a regular session at 120 fps for compatibility may require a separate budget row.
* Eye-AF overlay rendering cost is currently estimated at <= 0.3 ms per frame; this becomes a measurable budget once `EyeAfOverlay` is wired into the live preview.
