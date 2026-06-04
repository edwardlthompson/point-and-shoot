# legacy device fleet RAW policy (legacy SKU-class) — **legacy plugin**

> **Milestone 16.4:** This policy is **not** the default fleet path. It applies only when
> **Legacy legacy device fleet policy** is enabled in developer prefs (`FleetPolicyPreferences`) **and**
> `Build.MODEL` matches legacy SKU/CPH2653. New SKUs use **`GenericFleetPolicy`** — see
> `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md`.

**Policy id:** `oneplus_13_legacy_sku`  
**Code:** [`LegacyDeviceFleetPolicy.kt`](../app/src/main/java/dev/pointandshoot/fleet/LegacyDeviceFleetPolicy.kt)  
**Profiles cache:** `files/fleet_camera_profiles_<model>.json` (pull via `run-as` on debug builds)  
**Probe export:** Diagnostics hub → **Fleet profiles (Milestone 13.2)** section in `PROBE_EXPORT_LATEST.md`

This page is the auditor-facing summary for Milestone **13.2**. Detailed topology remains in [`DODGE_PROFILE.md`](../DODGE_PROFILE.md).

---

## Device gate

| Field | Value |
|-------|--------|
| `Build.MODEL` | Contains **`legacy SKU`** or **`CPH2653`** |
| Reference USB | `legacy serial` (LineageOS 23 class) |

When the gate matches, focal routing uses the **canonical dodge table** instead of focal-length clustering alone.

---

## Canonical camera ids

| Role | `cameraId` | Sensor (bill of materials) | Focal slots |
|------|------------|------------------------------|-------------|
| Logical multi-cam | **0** | Wrapper over 2+3+4 | Optional fused zoom (not default P&S path) |
| Ultra-wide | **3** | Samsung S5KJN5 | **M14** |
| Wide | **2** | Sony LYT-808 | **M23**, **M35**, **M50** (digital crop on 2) |
| Tele | **4** | Sony LYT-600 | **M73**, **M85**, **M150** (digital crop on 4) |
| Front | **1** | Sony IMX615 | Selfie |

**Invariant (M11.2 / dodge tele):** All three tele M-slots (**73 / 85 / 150** mm) use **`Roles.tele` → id 4** with digital crop modes; do not route 150 mm to a separate long-tele id on this stack.

---

## RAW still defaults (Sprint 13.3 — USB-verified, May 2026)

| Setting | legacy device policy |
|---------|-------------------|
| **Still backend** | **`FRAMEWORK_REFERENCEAPP`** on legacy SKU/2653 (matrix bisect `dng_matrix_bisect_20260519_030756`) |
| **Leaf open** | Prefer physical **3 / 2 / 4** when listed in `cameraIdList` |
| **Leaf RAW pick** | **`LEAF_RAW_FORMAT_ORDER`** — **32 → 37 → 38 → 36** on opened map (ReferenceCam order) |
| **Logical session RAW** | Unpinned RAW on parent **0**; preview-only physical pin; resolver falls back logical+logical when no per-physical totals |
| **Still lens shading** | Map + **`SHADING_MODE`** HQ/FAST when advertised (not tele map-only) |
| **Still optical** | Aberration + distortion when advertised (`StillCaptureIqPolicy.applyReferenceAppOpticalCorrection`) |
| **DNG save (Standard)** | **`ReferenceAppDngCreatorPair`** → **`DngCreator(openedLeaf, stillResult)`** only — `reconcile=false` in logcat |
| **Still capture (leaf)** | **`ReferenceAppLeafStillCaptureRequest`** — crop + ReferenceCam still IQ (lens shading map, edge/NR/tonemap/aberration/distortion); **HAL AE**; **no** readout manual ISO latch, **no** post-save TIFF reconcile |
| **Post-save metadata** | **`skipStillMetadataApplyOnLeafDng`** on **2/3/4**; **no** P&S software auxiliary string on leaf (`skipDngSoftwareDescriptionOnLeaf`) |
| **Reference-cal / aux reconcile** | **`useReferenceAppReferenceCalibration()` = false**, **`useLegacyLeafAuxColorReconcile()` = false** (bisect only) |
| **Wide-cal aux** | **`useWideLeafCalibrationForAuxDng()` = false** until **13.3h** bisect + ACR **3/3** — see **`docs/DNG_OPENABILITY_REGRESSIONS.md`** |
| **Still precapture / skip stopRepeating** | **`useReferenceAppStillPrecapture()` / `proShotLeafStillSkipsStopRepeating()` = false** (bisect flags only) |
| **AltReferenceApp-inspired** | Available via ADB `pns_preview_still_dng_backend=altreferenceapp_inspired` for bisect only — **not** shipped on legacy device |

---

## Still capture modes (Sprint 13.8 — planned)

| Mode | Default | Notes |
|------|---------|-------|
| **Standard** | **Yes** | ReferenceCam-class single still (this document) |
| **ZSL still** | Optional | AltReferenceApp-inspired ring buffer; same DNG writer |
| **HDR still** | Optional | **3-shot** EV bracket (**±1 EV**, reference = middle); **burst of 3 DNGs** per shutter (MVP — no in-app merge; post in ACR/Lightroom). Same `DngCreator` writer as Standard. |

---

## Video (separate from still policy)

| Lane | Sprint | Notes |
|------|--------|-------|
| Encoded H.264/HEVC / DCG | M12 + **13.4** | `EnableHDRDCGMode` on session template + DCG `VideoFormat` encode path (`docs/M13_4_DCG_SESSION.md`) |
| RAW video `.mcraw` | **13.6** | **`RawVideoRecordingController`** + `PNMRAWV1` writer; legacy device leaf only — **`docs/M13_6_RAW_VIDEO.md`** |

---

## Verification

| Check | Command / artifact |
|-------|-------------------|
| Roles | JVM: `LegacyDeviceFleetPolicyTest`, `BackCameraRoleResolverTest` |
| Profiles JSON | Probe hub export or `adb exec-out run-as dev.pointandshoot cat files/fleet_camera_profiles_legacy_sku.json` |
| Aux DNG daylight | `pns_aux_dng_capture_analyze.ps1 -PreviewDial A -NoFast` — **3/3** + **`dng_desktop_open_gate.py`** |
| DNG openability | Human ACR **3/3** (M14/M23/M73); logcat `dng openability diag reconcile=false wideCal=false` |
| vs ReferenceCam | `pns_dng_referenceapp_pns_session.ps1` (after **13.3g** gate green) |
| Aesthetic (same scene) | `scripts/pns_dng_aesthetic_gate.py` — ±20% luma/R/G/B vs `tests/fixtures/referenceapp_legacy_sku/` (wired in `pns_aux_dng_capture_analyze.ps1`) |

---

## Non–OnePlus-13 fleet (generic profile)

Devices that do **not** match **`legacy SKU` / `CPH2653`** (e.g. **OnePlus 12 `CPH2583`**) use **`FleetCameraProfileBuilder`** + **`FleetCameraStartupScan`** without **`LegacyDeviceFleetPolicy`**. USB smoke: `pns_shallow_scan_hub_validate.ps1`, `pns_adb_preview_validate.ps1` (Milestone 6 + full pack). Example: **`hfr-runs/adb_preview_validate_20260528_061254`** on **`b5214fc6`** — ProbeHub **6 cameras**, Milestone 6 **PASS**, full validate **PASS** (jpeg_only uses independent-tonal fallback when hardware JPEG surface absent).

---

*Milestone 13.2 — May 2026. ReferenceCam-exact leaf path — May 2026.*
