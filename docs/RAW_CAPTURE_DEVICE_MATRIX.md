# RAW capture methods and quality (device matrix)

This document describes **what combinations the app can exercise** for scripted preview RAW stills, the **intent / bit-depth story** per profile, and **observed results** from host automation.

**Fleet context (Milestone 10 Sprint 10.8):** see **`FLEET_REFERENCE_M10_8.md`** for how this matrix relates to committed **`PROBE_RESULTS.md`** and **`DODGE_PROFILE.md`** caps.

Implementation reference: `RawCaptureSupport.kt` (`RawStreamPreference`, `pickRawOutput`), `ImagingProfile.kt`, `PreviewEngineScreen.kt` (`PreviewController` session RAW attach).

## Automation

- **Script:** `scripts/pns_raw_capture_matrix.ps1`
  - Full matrix: **2** [`ImagingProfile`](app/src/main/java/dev/pointandshoot/ImagingProfile.kt) ids × **5** [`RawStreamPreference`](app/src/main/java/dev/pointandshoot/RawCaptureSupport.kt) ADB strings × **2** JPEG companion seeds = **20** cold-start cells.
  - **`-Quick`:** `standard_pro` only, `default` + `raw_sensor_first`, JPEG on/off (**4** cells).
  - **`-CameraId`:** passes `pns_preview_camera_id` (e.g. physical / alternate logical id).
- **Related:** `scripts/pns_photo_capture_verify.ps1` (retry-until-success for one configuration).

### ADB extras (preview)

| Extra | Role |
|--------|------|
| `pns_screen=preview` | Preview engine route |
| `pns_preview_dial=H` | H dial (highlight metering + YUV path when preview target fps is below 120) |
| `pns_preview_raw_count=1` | One scripted `captureRawStill` after settle |
| `pns_preview_imaging_profile` | `standard_pro` or `ultra_max` (DNG / sidecar intent) |
| `pns_preview_raw_stream` | `default`, `raw_sensor_first`, `raw12_only`, `raw_sensor_only`, `raw10_only` |
| `pns_preview_jpeg_companion` | `true` / `false` (session-only seed; does not persist to disk) |
| `pns_preview_camera_id` | Optional camera id string |

## Quality and bit depth (app intent)

The camera HAL still delivers **one** RAW pixel format per session (`RAW12`, `RAW_SENSOR`, or `RAW10` depending on advertisement and `pns_preview_raw_stream`). On top of that:

| Profile | RAW / DNG intent | Tonal companion (bit depth) |
|---------|------------------|-----------------------------|
| **standard_pro** | Lossless compressed DNG (`RawMode.LosslessCompressedDng`) | AVIF HDR **10-bit** + Display P3 |
| **ultra_max** | Uncompressed **RAW12** DNG (`RawMode.UncompressedRaw12Dng`) | JPEG XL **12-bit** + Rec.2020 |

**`pns_preview_raw_stream`** only changes **which advertised RAW format** is attached to the preview session (see ordering in [`RawCaptureSupport.pickRawOutputFromMaps`](app/src/main/java/dev/pointandshoot/RawCaptureSupport.kt)). It does not add a new HAL mode beyond what the device lists in `StreamConfigurationMap`.

## Sticky `Intent` regression (fixed 2026-05-12)

`CameraCapabilitiesProbe` re-reads `activity.intent` every composition for the live preview route. ADB / matrix runs leave **`pns_preview_raw_count`**, **`pns_preview_raw_stream`**, **`pns_preview_jpeg_companion`**, dial, imaging profile, and camera id on the **same** `MainActivity` intent. Opening preview from the **engineering hub** (debug menu, Back from hub, etc.) did **not** clear those flags, so the app replayed automation and wrong RAW stream picks against a normal TextureView session → **`Surface was abandoned`**, **`No RAW buffer`**, or **`ERROR_CAMERA_DEVICE`**.

**Fix:** when returning to live preview from the engineering hub, set `previewLaunchedFromDebug` on those navigation paths. While the hub flag is set **and** `launchScreen != PNS_SCREEN_PREVIEW`, pipeline seeds from the sticky intent are ignored (`trustIntentForPreviewPipeline`). If the user is still on the cold ADB `pns_screen=preview` route (`launchScreen == PNS_SCREEN_PREVIEW`), intent extras stay honored even after opening the dev menu.

## Host run: OnePlus CPH2655 (USB serial `8bf09993`, 2026-05-12)

Artifacts: **`hfr-runs/raw_capture_matrix_20260512_205335/`** (default M23 seed → logical **`cameraId=2`** in this build).

| profile | raw_stream | jpeg | Result |
|---------|------------|------|--------|
| standard_pro | default | true | **Fail** — session/preview surface race (`Surface was abandoned`) then **`Session create aborted: IllegalArgumentException`** during automation wait; no `captureRawStill 1/1 ok=true saved=` |
| standard_pro | default | false | **Fail** (same class of automation log) |
| standard_pro | raw_sensor_first | true / false | **Fail** (same) |
| standard_pro | raw12_only | true / false | **Fail** (same) |
| standard_pro | raw_sensor_only | true / false | **Fail** (same) |
| standard_pro | raw10_only | true / false | **Fail** (same; RAW10 is also the least portable path for `DngCreator` on many devices) |
| ultra_max | all combinations above | | **Fail** (same; profile only changes DNG/JXL intent, not HAL delivery on this run) |

**Additional spot check:** **`hfr-runs/raw_capture_matrix_20260512_204601/`** (quick matrix, default camera **2**) showed a **different** dominant failure: preview session came up (`PNS.PreviewSessionCtx` with `wantYuv=true`), **`captureRawStill`** ran, then **`No RAW buffer`** and **`PNS.Cam onError cameraId=2 error=4`** (`ERROR_CAMERA_DEVICE`). **`hfr-runs/raw_capture_matrix_20260512_204947/`** (`-CameraId 0`, quick matrix) matched the **No RAW buffer** + **`onError`** pattern.

**Conclusion for this fleet unit:** No matrix cell produced a verified saved RAW/DNG on the scripted path in these runs. The “greatest quality / bit depth” **configuration cannot be honored end-to-end** until the HAL delivers a RAW `ImageReader` frame reliably (and `raw10_only` remains a poor choice for DNG portability even when the session configures).

## Aux DNG color cast triage (logical vs leaf, CPH2655-class)

**Agent lock:** **`.cursor/rules/dng-logical-multicam-metadata-lock.mdc`** and **`AGENTS.md`** section **CRITICAL — DNG metadata pairing (`DngMetadataResolver`) and RAW still diagnostics** — do not relax **`DngMetadataResolver`** hybrid avoidance, **`DngMetadataResolution`**, or remove **`PNS.CaptureStill`** **`dng save diag`** without maintainer sign-off + USB capture verify.

**Context:** Ultrawide / tele DNGs can decode dark with a strong green cast while hardware JPEG from the same capture looks correct — often **RAW buffer vs DNG metadata** pairing (`DngCreator(characteristics, totalCaptureResult)`). See [`DngMetadataResolver`](app/src/main/java/dev/pointandshoot/DngMetadataResolver.kt) (never use physical `CameraCharacteristics` with a logical `TotalCaptureResult` when the HAL omits that physical id from `physicalCameraTotalResults`).

**Second root cause (May 2026, user-verified on `8bf09993`):** With **preview-only** physical pin, RAW outputs are **logical** again, but if [`pickRawOutputForPreviewSession`](app/src/main/java/dev/pointandshoot/RawCaptureSupport.kt) still sizes the RAW **`ImageReader`** from the **preview physical child’s** `SCALER_STREAM_CONFIGURATION_MAP` (`pickRawForLogicalMulticamPinnedAux`), buffer **geometry/packing** can disagree with the **logical** RAW stream → **same dark/green decode** even when **`dng save diag`** shows **logical + logical**. **Fix:** **`usePhysicalChildRawStreamMapForLogicalSession = false`** at **`PreviewEngineScreen.kt`** call sites (and do not re-enable without physically pinning RAW to that child + USB aux DNG proof).

**Fourth root cause (May 2026, user-verified):** On **logical** `cameraId` with a **non-wide** preview physical pin, **`pickRawOutputForPreviewSession`** must not end at **`pickRawOutput(chars, Default)`** alone. Fleet **§2** **`Default`** is still **RAW12 → RAW_SENSOR → RAW10** on the **logical** map; **leaf** UW/tele skips RAW12 via **`shouldUseLeafNonWideBackRawSensorPolicy`**, but logical sessions have **non-empty** `physicalCameraIds` so that branch does not apply. Disabling **`pickRawForLogicalMulticamPinnedAux`** (correct for stream-map alignment) accidentally dropped the **RAW_SENSOR-first** behavior that the old path enforced from the child map — **RAW12** stayed first on logical → dark/green aux DNG. **Fix:** when **`shouldPreferRawSensorForAuxPhysicalPreviewPin`**, call **`pickRawOutput(chars, RawSensorOnly)`** then **`RawSensorFirst`** before **`Default`** (all on **logical** `chars`).

**Host checks (when `exiftool` is available):** compare wide vs aux DNGs for `AsShotNeutral`, `ColorMatrix1`/`ColorMatrix2`, `CFAPattern2`, `BlackLevel`, `WhiteLevel`, `ActiveArea` / crop tags.

**Log tags:** `PNS.DngMeta`, `PNS.CaptureStill` (includes one-line **`dng save diag`** after resolution: `session`, `picked`, `pairedPhysical`, `mapKeys`, `active`, `children`, `iso`, `rawFmt`, `rawWxH`).

### May 2026 USB evidence (serial `8bf09993`)

| Artifact | What it shows |
|----------|----------------|
| **`hfr-runs/aux_dng_triage_20260515_221222/`** | **Experiment 1** — three cold starts: `pns_preview_focal_mm_slot` **14** / **23** / **150** with `pns_preview_camera_id=0` + scripted RAW. **UW (M14)** and **tele (M150)** routed to **leaf** cameras (`session=3` / `4`); **`dng save diag`** reports `picked=null`, empty `children` (no logical multi-camera on that `CameraDevice`). **Wide (M23)** `session=2`, same. All saves **`rawFmt=32`** (`RAW_SENSOR`), **`captureRawStill 1/1 ok=true`**. |
| **`hfr-runs/photo_capture_verify_20260515_221650/`** | **Logical parent `cameraId=0`** scripted still: **`PNS.DngMeta`** `physical id=2` but **`physicalCameraTotalResults missing (mapKeys=[])`** → **fallback logical + logical**; **`dng save diag`** `session=0 picked=2 pairedPhysical=false children=2,3,4 active=2`. Same **`rawFmt=32`**. |
| **`hfr-runs/aux_dng_exp2_rawstream_20260515_221826/`** | **Experiment 2** — `pns_preview_raw_stream=raw_sensor_only` on logical **0**: same **`DngMeta` fallback + `dng save diag`** as above. **`raw12_only`**: preview never attached a RAW reader (`PNS.AdbValidation` **`no RAW ImageReader`** for full wait) — not a useful aux cast regression cell on this unit. |
| **Session pin (May 2026 follow-up)** | Pinning **RAW+JPEG** to the preview physical id while **`physicalCameraTotalResults`** stays **empty** routes **aux sensor pixels** into **`DngCreator`** with **logical** tags → cast. **Fix:** **`physicalPinnedSurfaceIndices = null`** (preview-only pin via [Camera2SessionCompat](app/src/main/java/dev/pointandshoot/Camera2SessionCompat.kt)); re-verify aux DNG on USB. |
| **RAW reader vs logical stream (May 2026 follow-up)** | Preview-only pin fixed surface routing, but **`pickRawForLogicalMulticamPinnedAux`** still picked RAW WxH/format from the **physical** map while the session delivered **logical** RAW → cast persisted. **Fix:** **`usePhysicalChildRawStreamMapForLogicalSession = false`** in **`pickRawOutputForPreviewSession`** / **`PreviewEngineScreen`**. |
| **Per-physical `TotalCaptureResult` vs unpinned RAW** | HAL may populate **`physicalCameraTotalResults[picked]`** while RAW stays logical-unpinned; **`resolveForDngSave`** previously used **physical** chars + that total → cast. **Fix:** **`allowPhysicalTotalResultPairing = false`** (default); **`PreviewEngineScreen`** explicit **`false`**. **`PNS.DngMeta`** log: *map has entry but allowPhysicalTotalResultPairing=false*. |
| **RAW12 `Default` on logical + aux pin (May 2026)** | **`usePhysicalChildRawStreamMap=false`** fixed physical vs logical **sizes**, but **`pickRawOutput(logical, Default)`** still tried **RAW12** first while HAL aux route needs **`RAW_SENSOR`** (leaf had this; logical did not). **Fix:** **`shouldPreferRawSensorForAuxPhysicalPreviewPin`** → **`RawSensorOnly` / `RawSensorFirst`** on **logical** `chars` in **`pickRawOutputForPreviewSession`**. |

**Automation helpers (repo `scripts/`):**

- **`pns_aux_dng_triage_focal_slots.ps1`** — install + three focal-slot cold captures + `pns_pull_dcim_captures.ps1` (optional `-Serial`).
- **`pns_aux_dng_exp2_raw_stream.ps1`** — two cold captures: `raw_sensor_only` vs `raw12_only` on logical **0** + logcat.

**Product / engineering stop rule:** If repeated USB evidence shows **empty `physicalCameraTotalResults`** for the picked physical id while RAW remains pinned to that sensor, prefer documenting the **OEM HAL gap** over further ordering tweaks. **Ship fallback:** treat aux-path DNG as **best-effort**, keep **hardware JPEG (or JXL)** as the reliable reference, optional user-facing note. **Next architecture spike (not implemented here):** trial **`CaptureRequest.Builder.setPhysicalCameraKey`** on still requests for logical multi-camera (API **28+**), with `pns_photo_capture_verify.ps1` / `pns_capture_pipeline_verify.ps1` proof and without re-enabling fleet-regression **§4a** stream hints or **§2** default RAW tier order without a fresh USB gate.

## Re-running on another device

```powershell
.\scripts\pns_raw_capture_matrix.ps1
# or faster smoke:
.\scripts\pns_raw_capture_matrix.ps1 -Quick -WaitSec 55
```

Copy the newest **`hfr-runs/raw_capture_matrix_*/matrix.csv`** summary back into this doc when you have a device where **`captureRawStill 1/1 ok=true saved=`** appears in logcat.
