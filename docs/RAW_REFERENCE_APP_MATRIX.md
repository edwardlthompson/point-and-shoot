# RAW reference app matrix (P&S vs ProShot vs MotionCam Pro)

Triangulation for **Milestone 13** fleet RAW work on **OnePlus 13** (`CPH2655`). Refresh after APK updates via `pns_proshot_apk_decompile.ps1` and `pns_motioncam_apk_decompile.ps1`.

| Capability | Point & Shoot (today) | ProShot (`com.riseupgames.proshot2`) | MotionCam Pro (`com.motioncam.pro`) |
|------------|----------------------|----------------------------------------|-------------------------------------|
| **Still DNG** | `DngCreator` + `DngMetadataResolver` (logical pairing locked); leaf focal open M14/M23/M73 | `DngCreator(openedCharacteristics, stillResult)`; lens shading on still | Native DNG SDK + `RawEncoder::encode_DNG10/12`; no Java `DngCreator` |
| **Still RAW formats** | `RawCaptureSupport` — logical aux prefers `RAW_SENSOR`; fleet §2 order RAW12→RAW_SENSOR→RAW10 | RAW **32, 37, 38, 36** on **opened** id map | Native **RAW10/12/16** encode; `NativeCameraRawOutput.bits=16` → `RAW_SENSOR` label |
| **Aux DNG color (CPH2655)** | **MotionCam-inspired** still path (May 2026): `RAW_SENSOR@activeArray`, no leaf ASN reconcile | ProShot path **retired** on OP13 after color failure | Native DNG SDK; triangulate behavior not copy JNI |
| **Camera enumeration** | `BackCameraRoleResolver` + `DODGE_PROFILE` | Public + physical expand + hidden id probe + OEM blocklist | `NativeCameraManager.GetSupportedCameras()` + per-id RAW/preview configs |
| **Leaf vs logical open** | Leaf for focal slots (dodge tele) | `openCamera(leafId)` | `StartCapture(cameraId, …)` with `NativeCameraInfo.physicalCameraId` |
| **Lens shading (still)** | Not on still capture request (May 2026) | `STATISTICS_LENS_SHADING_MAP_MODE_ON` + `SHADING_MODE` | Viewfinder vignette + `disableShadingMap` in device profile |
| **Encoded HDR video** | `MediaCodec` / `MediaRecorder`; DCG format + HDR10 SEI (M12); **DCG session key not wired** | Standard encoders / profiles (not M13 focus) | `RecordToScreen` + HEVC export paths (Java); separate from RAW lane |
| **DCG / vendor HDR session** | `DcgModeSupport` probes DynamicRangeProfiles; **`EnableHDRDCGMode` attach pending** (M13.4) | Not analyzed for DCG | **No `EnableHDR` / `codeaurora` strings** in current APK — do not block P&S DCG wire on MotionCam |
| **RAW video** | **None** (M13.6 planned) | Not primary product mode | **`RecordToVideo`** → `RawEncoder` → **`.mcraw`** |
| **Video session topology** | Preview + optional `MediaRecorder` surface on REGULAR session | Preview + JPEG + RAW still readers | Native REGULAR session via NDK; RAW video **not** `MediaRecorder` |
| **DNG post-save TIFF surgery** | Reverted (FM/ASN broke wide/tele) | None in save path | N/A (native DNG SDK) |
| **Automation** | `pns_aux_dng_capture_analyze.ps1`, `pns_capture_pipeline_verify.ps1`, `pns_dng_proshot_pns_session.ps1` | `pns_proshot_apk_decompile.ps1`, reference pull scripts | `pns_motioncam_apk_decompile.ps1`; `pns_raw_video_verify.ps1` (planned) |

## Which reference for which M13 sprint

| Sprint | Primary reference | Secondary |
|--------|-----------------|-----------|
| **13.2** Fleet profile | ProShot `C0527f` + MotionCam `NativeCameraRawOutput` / `NativeCameraInfo` | `DODGE_PROFILE.md`, probe hub |
| **13.3** Still DNG — **Standard** default (OP13) | **MotionCam-inspired** (`RAW_SENSOR@activeArray`, map-first shading, no leaf ASN reconcile) | ProShot for other devices / bisect |
| **13.8** ZSL + HDR still modes | MotionCam `CaptureZslImage` / `CaptureHdrImage` **behavior** | Same ProShot DNG save as 13.3 |
| **13.4** DCG wire | **P&S probe** / Qualcomm session keys | MotionCam — no string evidence in APK |
| **13.6** RAW video | **MotionCam** `.mcraw` + `RawEncoder` | P&S `VideoRecordingController` patterns (lifecycle only) |

**P&S still modes (shipped plan):** `Standard` (default, ProShot) | `ZslStill` | `HdrStill` — one framework DNG encoder, no dual native backend.

## Evidence paths

| App | APK / decompile | Analysis doc |
|-----|-----------------|--------------|
| P&S | `app/build/outputs/apk/debug/app-debug.apk` | `docs/RAW_CAPTURE_DEVICE_MATRIX.md`, `docs/DNG_PS_ALIGNMENT_SPIKE.md` |
| ProShot | `hfr-runs/proshot_apk_decompile/` | `docs/PROSHOT_APK_FLEET_ANALYSIS.md` |
| MotionCam | `hfr-runs/motioncam_apk_decompile/` | `docs/MOTIONCAM_APK_FLEET_ANALYSIS.md` |

---

*Milestone 13 Sprint 13.1 — May 2026.*
