### Semantics (public HAL contract)

| Topic | Notes |
|-------|--------|
| Face geometry | `CaptureResult.STATISTICS_FACES` → `android.hardware.camera2.params.Face` (bounds, id, score). Eye positions are **optional** (`getLeftEyePosition` / `getRightEyePosition`) when `STATISTICS_FACE_DETECT_MODE_FULL` is supported and the HAL fills them. |
| Face detect request | `CaptureRequest.STATISTICS_FACE_DETECT_MODE` — values from `CameraMetadata.STATISTICS_FACE_DETECT_MODE_*` (`OFF`, `SIMPLE`, `FULL`). |
| Capability | `CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES`, `STATISTICS_INFO_MAX_FACE_COUNT`. |
| Metering / AF | `CONTROL_AE_REGIONS`, `CONTROL_AF_REGIONS`, `CONTROL_AWB_REGIONS`; caps `CONTROL_MAX_REGIONS_AE` / `_AF` / `_AWB` on characteristics. Tap / face-priority paths also use `CONTROL_AF_TRIGGER`, `CONTROL_AE_PRECAPTURE_TRIGGER`, `CONTROL_AE_LOCK`, `CONTROL_AF_STATE`, `LENS_FOCUS_DISTANCE`, `LENS_STATE`, `LENS_FOCUS_RANGE` on results. |
| Auto-framing | `CONTROL_AUTOFRAMING` (request) / `CONTROL_AUTOFRAMING_STATE` (result) when the device advertises `CONTROL_AUTOFRAMING_AVAILABLE`. |
| Latency | `SYNC_MAX_LATENCY` on characteristics (face pipeline timing expectations). |

There is **no separate** public Camera2 “eye tracking” key: eyes are carried on `Face` when in FULL mode.

### Vendor metadata (OEM)

- **Discovery:** `CameraCapabilitiesProbe` / exported **PROBE_RESULTS** markdown — section **`### Named vendor keys — face / eye / tracking (by scope)`**, and **`face_meter_probe_*.json`** (`vendorNamedFaceEyeTracking_*`, `schemaVersion` ≥ 2).
- **Heuristic filter:** `VendorFaceEyeKeyNames.kt` — vendor-ish name (`com.` / `org.` / `vendor`) **and** substring list (`face`, `eye`, `iris`, `tracking`, `portrait`, …). **Not exhaustive** for every OEM spelling.
- **Production use:** `VendorKeyGuard.kt` — any vendor tag must be gated before set.

### ML Kit (YUV analysis fallback)

- `MlKitFaceTrackSupport` — `FaceDetectorOptions` **FAST** + **`LANDMARK_MODE_ALL`**; boxes + optional eye landmarks mapped to preview buffer space (`TexturePreviewFit`).
- Geometry: `MlFaceHudDetections`, `FaceTrackOverlay`, `EyeAfOverlay`, `FaceDetectAdapter`.

### Point & Shoot — where it lives

| Area | Primary files |
|------|----------------|
| Preview face + eyes + metering | `PreviewEngineScreen.kt` (`PreviewController.processFaceStatistics`, `dispatchFaceHudOverlay`, ML YUV lane) |
| Overlay | `FaceTrackOverlay.kt`, `EyeAfOverlay.kt` |
| Caps / gate | `HardwareCapsSnapshot.kt`, `CapabilityGate.kt` |
| Probes | `CameraCapabilitiesProbe.kt`, `FaceMeterProbeScreen.kt`, `scripts/pns_face_meter_probe.ps1` |
| Full toolkit narrative | `docs/face-eye-tracking-toolkit.md` |

### ADB / automation (debug)

| Extra | Purpose |
|-------|---------|
| `--es pns_screen facemeter` | Opens face / metering probe screen |
| `--ez pns_autofacemeter true` | Auto-write `face_meter_probe_*.{md,json}` and finish |
| `pns_auto_export_probe` + `pns_screen=probehub` | Full probe markdown to app files (`PROBE_EXPORT_LATEST.md`) — see `scripts/pns_ae_highlight_probe_adb.ps1` |

### Related Android types (not re-listed in `javap` blocks above)

- `android.hardware.camera2.CameraMetadata` — `STATISTICS_FACE_DETECT_MODE_*`, `CONTROL_AF_STATE`, `CONTROL_AE_STATE`, …
- `com.google.mlkit.vision.face.*` — detector, `Face`, `FaceLandmark` (ML path; not Camera2).
