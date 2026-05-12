# Face / eye tracking, exposure, and focus — toolkit inventory

This document lists **standard Camera2**, **vendor**, **root**, and **in-app** tools relevant to face detection, eye geometry, metering (AE), and focus (AF). Use it for planning; regenerate device-specific vendor detail via **Debug → Export PROBE_RESULTS** (see `CameraCapabilitiesProbe.buildProbeReport`).

---

## 1. Standard Camera2 (portable)

### Face statistics (HAL)

| Capability / control | Role |
|---------------------|------|
| `CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES` | OFF / SIMPLE / FULL — **FULL** is required for per-face landmarks (eyes) in results. |
| `CaptureRequest.STATISTICS_FACE_DETECT_MODE` | Enables face pipeline for that session/stream. |
| `CaptureResult.STATISTICS_FACES` | Array of `android.hardware.camera2.params.Face` — bounds, scores, **left/right eye positions** when mode is FULL and OEM populates them. |
| `CaptureResult.STATISTICS_FACE_DETECT_MODE` | Actual mode applied (may differ from request on some devices). |

There is **no separate Camera2 “eye tracking” API** in the public HAL contract: eye positions are optional fields on `Face` when face detect is FULL and the HAL fills them.

### Regions (tap-AF, face-weighted AE/AF/AWB)

| Item | Role |
|------|------|
| `CaptureRequest.CONTROL_AF_REGIONS` | Metering rectangles for autofocus (normalized or active-array coords per API level). |
| `CaptureRequest.CONTROL_AE_REGIONS` | AE metering regions. |
| `CaptureRequest.CONTROL_AWB_REGIONS` | AWB regions when using region-based white balance. |
| `CameraCharacteristics.CONTROL_MAX_REGIONS_AE` / `_AF` / `_AWB` | Upper bounds for region counts (typed probe section in exported PROBE_RESULTS). |
| `CaptureRequest.CONTROL_AF_TRIGGER` / `CaptureResult.CONTROL_AF_STATE` | Start/cancel AF; observe focus convergence. |
| `CaptureRequest.CONTROL_AE_LOCK`, `CONTROL_AE_PRECAPTURE_TRIGGER`, `CONTROL_AE_MODE`, etc. | Exposure behavior; **not** face-specific but pair naturally with AE regions. |
| `CaptureResult.LENS_FOCUS_DISTANCE`, `LENS_STATE` | Lens focus distance / state where supported. |

### Probe output (this repo)

Exported markdown includes:

- **Vendor-key highlights** — vendor-looking names for LBMF/HDR/AE/bracketing; **vendor-named face / eye / tracking** summary line (`VendorFaceEyeKeyNames`).
- **`### Named vendor keys — face / eye / tracking`** — per-scope bullet list (`Characteristics`, `CaptureRequest`, `CaptureResult`, `SessionConfiguration`).
- **Face / eye / metering (Camera2 typed)** — `CONTROL_MAX_REGIONS_*`, `SYNC_MAX_LATENCY`.
- **Face / AE-AF related keys** — request/result key names matching face / statistics / region / focus-distance substrings.

---

## 2. What Point & Shoot already uses

| Area | Implementation notes |
|------|----------------------|
| Face mode | `STATISTICS_FACE_DETECT_MODE` chosen from available modes (prefer FULL). See `PreviewEngineScreen` helpers around face-detect setup. |
| Face + eye geometry | `CaptureResult.STATISTICS_FACES` read each frame; **Eye-AF overlay** documents reliance on FULL mode (`EyeAfOverlay.kt`, `CapabilityGate` gates overlay on FULL). |
| AF / AE regions | `CONTROL_AF_REGIONS` for tap and face-priority metering (`PreviewEngineScreen`). |
| Capability snapshot | `HardwareCapsSnapshot` sets `hasFaceDetectFull` from `STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES`. |
| ML fallback | When `STATISTICS_FACES` is empty or unreliable, ML Kit path (`MlKitFaceTrackSupport.kt`, `FaceDetectAdapter.kt`) feeds overlays. |
| Vendor macro (not face, but AF-relevant context) | `com.oplus.macro.closeup.enable` via `VendorKeyGuard` / `HardwareCapsSnapshot.VENDOR_MACRO_CLOSEUP_REQUEST` for ultra-wide close-up. |

---

## 3. Vendor tags (`VendorKeyGuard` + probe)

- **Discovery**: full key lists appear in **PROBE_RESULTS** export; **vendor highlights** line lists vendor-looking names that match AE/HDR/etc.; **`### Named vendor keys — face / eye / tracking`** lists only keys whose names include `com.` / `org.` / `vendor` **and** face/eye/tracking heuristics (`VendorFaceEyeKeyNames`).
- **Safety**: production use of any vendor key goes through `VendorKeyGuard` (`VendorKeyGuard.kt`) so missing tags fail gracefully.
- **Eye / face-specific vendor keys**: **not fixed in code** — they vary by OEM. Use **`### Named vendor keys — face / eye / tracking`** in the export (and **`vendorNamedFaceEyeTracking_*`** in `face_meter_probe_*.json`) for the strict vendor-named list; widen with manual search on full vendor key sections if needed.

---

## 4. Root-only tools (`RootCapability`)

Root features are **orthogonal** to face APIs: none are named “face tracking,” but they affect camera stack observability and vendor unlock paths:

| Feature | Relevance to AF / AE / face |
|---------|-----------------------------|
| **VendorSetProp** | `persist.vendor.camera.*` — could expose OEM toggles that indirectly change portrait / scene modes (device-specific; not guaranteed). |
| **VendorKeyProbe** | Read vendor capture keys that need privileged access — widens what appears probeable vs stock app sandbox. |
| **VendorHighlightAe** | Highlight-weighted AE mode integers when stubs omit `CONTROL_AE_MODE_ON_HIGHLIGHT_WEIGHTED` — exposure behavior, not face-specific. |
| **CameraServerRestart** | Recover HAL — useful when experimenting with tags/props. |
| **ResolutionOverride** | Force preview size via vendor persist — can interact with preview-based ML geometry. |
| **CpuGovernorPin**, **ThermalTripRead**, **LogcatSystemWide**, **BacklightRead** | Performance/diagnostics and HUD exposure helpers — indirect. |

Every root feature has a **non-root fallback** documented in `RootCapability.FEATURE_DESCRIPTORS`.

---

## 5. Gaps and OEM variance

- **`STATISTICS_FACES` empty or eye fields zero** while mode is FULL → rely on **ML Kit** path or accept degraded Eye-AF overlay.
- **Vendor keys** differ per device — treat PROBE_RESULTS as source of truth per fleet device.
- **No public HAL guarantee** for continuous “eye tracking” separate from face rectangles; Eye-AF UX is **best-effort** from statistics + ML.

---

## 6. Regenerating the inventory on a device

### Automated (ADB)

From the repo root (USB or Wi‑Fi adb; optional `scripts/pns_adb_device.env` with `PNS_ADB_SERIAL`):

```text
.\scripts\pns_face_meter_probe.ps1
```

This installs `app-debug.apk` when needed, cold-starts `MainActivity` with `--es pns_screen facemeter --ez pns_autofacemeter true`, waits for logcat tag **`PNS.SWEEP_SIGNAL`** line **`FACE_METER_PROBE_DONE`**, and pulls **`face_meter_probe_*.{md,json}`** from `Android/data/dev.pointandshoot/files/` into `hfr-runs\face_meter_probe_<utc>\`.

Equivalent manual `am start`:

```text
adb shell am force-stop dev.pointandshoot
adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen facemeter --ez pns_autofacemeter true
```

- **Markdown** (`face_meter_probe_*.md`): same content as a full **PROBE_RESULTS** export (`buildProbeReportMarkdown` / `buildProbeReport`).
- **JSON** (`face_meter_probe_*.json`): per-camera summary from `buildFaceMeterProbeSummaryJson` (**schemaVersion 2**) — face modes, `hasFaceDetectFull`, max AE/AF/AWB regions, name-filtered request/result keys, **`vendorNamedFaceEyeTracking_*`** (characteristics / request / result / session / deduped `all`), OPLUS macro close-up key advertised.

In-app: **Developer menu → Capability matrices → Face / eye / metering probe** (no runtime camera open; optional **Run** for manual refresh).

### Manual export

1. Open the app **Developer / probe** flow and **Export PROBE_RESULTS_*.md**.
2. Search the file for: `Named vendor keys — face / eye / tracking`, `Face / eye / metering`, `Face / AE-AF related keys`, `STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES`.
3. Cross-check **Root Only** drawer (if rooted) for enabled **VendorKeyProbe** / **VendorSetProp** when testing vendor-specific behavior.
