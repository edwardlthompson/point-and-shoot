# Failure matrix

Source-of-truth for **graceful-degradation policy** when the camera, the storage stack, the user permissions, or the device thermal envelope misbehaves. Satisfies BUILD_PLAN §9 cross-cutting requirement: "Camera2 robustness & failure-matrix validation".

This document defines the **expected** behavior for each failure mode. The on-device validation gates (the `[ADB]` rows in BUILD_PLAN §9) verify each row.

## Conventions

| Severity | Meaning |
|---|---|
| **CRITICAL** | App must remain usable. Recovery must not require the user to reinstall, clear data, or reboot. |
| **HIGH** | Active capture or session is lost; must surface a clear UX message; next user action recovers. |
| **MEDIUM** | Single capture / preview frame lost; HUD warns; engine continues. |
| **LOW** | Internal hiccup; logged at WARN; no user-visible effect. |

## Permissions

| Failure | Expected behavior | Severity |
|---|---|---|
| `CAMERA` permission denied at install | Probe home shows "Camera permission required to probe" + "Request permission" button; no Camera2 calls attempted. | CRITICAL |
| `CAMERA` permission revoked while session is active | `CameraDevice.StateCallback.onError` fires; engine tears down session, returns to home with toast "Camera permission revoked - re-grant to continue". | CRITICAL |
| `VIBRATE` permission absent (manifest stripped) | `CaptureHaptics.scheduleStillTick()` returns false; capture continues silently. No user-visible warning. | LOW |
| Storage write blocked by scoped-storage rule | `CaptureStorage.openOutput` throws; engine surfaces "Cannot write capture - check storage" toast and aborts the in-flight capture. | HIGH |

## Camera device lifecycle

| Failure | Expected behavior | Severity |
|---|---|---|
| `CameraDevice.StateCallback.onError(ERROR_CAMERA_IN_USE)` | Wait 250 ms, retry once. If still in use, show "Camera busy - close other apps and retry" with a "Retry" button. | HIGH |
| `onError(ERROR_MAX_CAMERAS_IN_USE)` | Same retry policy as `ERROR_CAMERA_IN_USE`. | HIGH |
| `onError(ERROR_CAMERA_DEVICE)` | Tear down session immediately; reopen with exponential backoff (250 ms / 500 ms / 1 s); after 3 failures, surface "Camera hardware error - restart the app" and stop retrying. | CRITICAL |
| `onError(ERROR_CAMERA_SERVICE)` | Same as `ERROR_CAMERA_DEVICE`; logged separately so we can tell the difference in `DiagnosticsMode` dumps. | CRITICAL |
| `onDisconnected` (other app stole the camera) | Tear down session; do not retry; show "Camera disconnected" + "Reconnect" button. | HIGH |
| `CameraCaptureSession.StateCallback.onConfigureFailed` | Log the offending stream config to `PNS.SessionMatrix`; if a known-good fallback exists, switch to it; otherwise show "Capture session failed - check probe results". | HIGH |

## App lifecycle / configuration changes

| Failure | Expected behavior | Severity |
|---|---|---|
| Orientation change during preview | No crash. Compose state survives via `rememberSaveable`; session is rebuilt on the camera-control thread before the next frame. | MEDIUM |
| Background -> foreground (`onPause` / `onResume`) | Session torn down on `onPause` (1 s drain); reopened on `onResume`. State machine reports "ready" only after first preview frame post-resume. | MEDIUM |
| Process death + restart from recents | Cold-start path runs (PERFORMANCE_BUDGETS targets apply); persisted `HudSettings` reload from SharedPreferences. | MEDIUM |
| Configuration change with active recording | Recording is stopped cleanly (`MediaRecorder.stop()`); partial file is finalized via MediaStore `IS_PENDING = 0` before tear-down. | HIGH |

## Capture / encode failures

| Failure | Expected behavior | Severity |
|---|---|---|
| `DngCreator.writeImage` throws (sensor format mismatch) | `Dng12Saver.SaveStats` is not produced; `CaptureStorage.Handle.discard()` removes the partial entry; HUD flashes red border for 250 ms. | HIGH |
| AVIF / JXL encoder returns non-zero | Same as above; logged to `PNS.Reader`. | HIGH |
| `ImageReader` queue overflow | Oldest frame is dropped (`acquireLatestImage()`). HUD increments a "dropped frames this minute" counter. | MEDIUM |
| MediaStore insert returns null URI | `CaptureStorage.openOutput` throws `IllegalStateException`; engine retries once, then aborts with toast. | HIGH |
| Burst submit (`captureBurst(plan)`) rejected | The dial briefly flashes; "Engine busy - retry" toast; `BracketPlan` is not consumed. | MEDIUM |

## Vendor-tag misbehavior

| Failure | Expected behavior | Severity |
|---|---|---|
| Vendor key advertised but `CaptureRequest.set` throws | `VendorKeyGuard.useIfAvailable` log records `present` but the surrounding try/catch in the engine catches the throw and falls back to the standard pipeline. | MEDIUM |
| Vendor key silently ignored (no exception, no effect) | Behavior verified during PROBE_BUILD_PLAN §3 verification-before-tick gate; if the gate fails, the key is added to `AboutScreen.KNOWN_BAD_PATHS`. | MEDIUM |
| Vendor key disappears between `availableCaptureRequestKeys` query and use | Same as the throw case; engine falls back to standard pipeline. | LOW |

## Color & LUT pipeline (Phase 4)

| Failure | Expected behavior | Severity |
|---|---|---|
| Imported `.cube` file fails `LutPipeline.parseCube` (corrupt body, non-numeric token, truncated samples) | `LutPipeline.parseCube` throws `IllegalArgumentException` with the offending line; SAF importer catches the throw, removes the in-flight file, surfaces toast `"LUT file is corrupt - import skipped"`. | MEDIUM |
| Imported `.cube` size is unsupported (not 17 / 33 / 65) | Same path as above; toast text reads `"LUT size N not supported - use 17/33/65"`. | MEDIUM |
| Imported `.cube` `DOMAIN_MIN`/`DOMAIN_MAX` is not `[0, 1]` | Same path as above; toast `"LUT domain not [0,1]"`. The decision to support extended-range LUTs ships in a later phase. | LOW |
| Imported `.cube` SPDX (declared in companion text or filename) not in `LutCatalog.ALLOWED_SPDX` | The user is allowed to import (it's their device, their LUT) but the LUT does NOT appear in the `AboutScreen` "Color & LUT credits" sub-block (which is auto-derived from the catalog whitelist). `DiagnosticsMode` records the user-supplied LUT separately. | LOW |
| GLES `glTexImage3D` upload of a 33^3 LUT fails (driver out-of-memory, unsupported internal format) | Preview LUT is disabled with a transient toast `"LUT GPU upload failed - using identity"`; the still-encode CPU path is unaffected because it does not go through the GPU. The session continues. | MEDIUM |
| Preview LUT shader exceeds `PerfBudget.Defaults.LUT_SHADER_PER_FRAME_1080P_MS` for >= 60 consecutive frames | Preview is sacred (per `CAPTURE_ARCHITECTURE.md` rule 1) so the LUT is automatically disabled with a one-shot toast `"LUT preview disabled - frame budget regression"`; the failure is recorded in `DiagnosticsMode.dump`. The still LUT remains active so captures still get the grade. | MEDIUM |
| Calibration mode: tapped corner is far from the chart (per-channel patch variance > `CalibrationSampler.DEFAULT_MAX_VARIANCE`) | The offending patch's `PatchSample.rejected = true`; if any patch is rejected, the calibration UI displays a banner `"Chart not flat / refocus"` and disables the "Save profile" button until the user re-taps the corners. | MEDIUM |
| Calibration mode: `CalibrationMath.computeCcm` reports a singular system (chart degeneracy or all-neutral patches) | The solver throws `IllegalArgumentException`; the calibration UI surfaces `"Calibration failed - chart not visible / illuminant flat"` and resets the corner taps. | MEDIUM |
| Calibration mode: MTF50 measurement returns `null` (no edge in slanted-edge ROI) | The numeric MTF50 chip on the calibration screen renders as `"--"`; the calibration profile is still saved (sharpness is informational). | LOW |
| RAW capture path called with a non-identity LUT active | Engine ignores the LUT entirely (per `CAPTURE_ARCHITECTURE.md` "RAW path: skipped entirely"); the active LUT id + SHA256 are written to DNG metadata only. No user-visible warning - this is the intended design. | LOW |
| User-imported LUT removed from disk while the session has it active | On the next still capture, the encode lane re-loads the LUT and surfaces `"Active LUT missing - reverted to None"` if the file is gone. The session continues with the identity LUT. | MEDIUM |

## Thermal / long-session

| Failure | Expected behavior | Severity |
|---|---|---|
| `THERMAL_STATUS_SEVERE` reported by `PowerManager` | HUD shows a warning chip; encode executor pool drops to 1 thread; AVIF / JXL bitrate target is reduced 25%. | MEDIUM |
| `THERMAL_STATUS_CRITICAL` | Active recording stops cleanly; new captures rejected with "Thermal limit - cooling" toast until status drops to `LIGHT` or below. | HIGH |
| 15+ minute preview session without user interaction | Session continues; periodic `Log.i("PNS.Cam", "session alive elapsed=...")` heartbeat every 5 minutes. No leaked threads. | LOW |

## On-device validation gates

The BUILD_PLAN §9 rows below map onto this matrix - each gate validates the corresponding row(s):

* "Permission denied -> graceful UX + recovery" -> "Permissions" rows.
* "Camera in use by another app -> graceful error + retry path" -> `ERROR_CAMERA_IN_USE`.
* "Orientation change during preview/capture -> no crash, session recovers" -> "App lifecycle" rows.
* "Background/foreground transitions -> no dead session, state restored" -> "App lifecycle" rows.
* "Thermal throttling / long session (10+ min preview) -> no runaway errors" -> "Thermal / long-session" rows.

## Out of scope (intentionally)

* OTA firmware updates that change vendor key names: out of scope. The probe is the source of truth and PROBE_BUILD_PLAN §5 captures observed deltas.
* Network failures: not applicable - Point & Shoot is offline-first; no network calls in the production app.
* Play Services regressions: not applicable - we deliberately do not depend on Play Services (FOSS-only).
