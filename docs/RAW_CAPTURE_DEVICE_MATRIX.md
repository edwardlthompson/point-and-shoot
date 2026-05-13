# RAW capture methods and quality (device matrix)

This document describes **what combinations the app can exercise** for scripted preview RAW stills, the **intent / bit-depth story** per profile, and **observed results** from host automation.

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

## Re-running on another device

```powershell
.\scripts\pns_raw_capture_matrix.ps1
# or faster smoke:
.\scripts\pns_raw_capture_matrix.ps1 -Quick -WaitSec 55
```

Copy the newest **`hfr-runs/raw_capture_matrix_*/matrix.csv`** summary back into this doc when you have a device where **`captureRawStill 1/1 ok=true saved=`** appears in logcat.
