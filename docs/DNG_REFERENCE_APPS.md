# DNG reference apps (device truth)

## ReferenceCam (`com.riseupgames.proshot2`) — primary reference

On **legacy SKU / legacy device** (`legacy serial`), user-verified **May 2026**: ReferenceCam DNGs decode with **correct color on all lenses** (UW, wide, native tele).

**Investigation (no source at runtime):**

| Method | Script / artifact |
|--------|-------------------|
| Pull latest non–Point & Shoot DNGs + tag diff vs P&S | `scripts/pns_proshot_dng_reference_pull.ps1` → `hfr-runs/proshot_reference_*` |
| logcat + `dumpsys media.camera` during manual captures | `scripts/pns_proshot_adb_forensics.ps1` |
| APK string grep (`DngCreator`, `RAW_SENSOR`, `setPhysicalCameraId`, …) | Pulled into `proshot_reference_*/apk/` by reference pull script |

**Cannot:** `run-as`, attach debugger, or drive lens/RAW via documented ADB intents (package dump has no P&S-style automation extras).

---

## OpenCamera — deprioritized on this device

[OpenCamera](https://sourceforge.net/p/opencamera/code/) (GPLv3) is useful for **reading** a minimal Camera2 DNG path:

- One `cameraId` per session; `new DngCreator(characteristics, capture_result)` with no TIFF matrix rewriting.
- `ImageFormat.RAW_SENSOR`; `STATISTICS_LENS_SHADING_MAP_MODE_ON` on still requests when RAW enabled.

**On legacy SKU, OpenCamera produces the same broken aux DNG color as Point & Shoot** — not a product color reference for this HAL. Keep source links for API patterns only; do not treat OpenCamera parity as a color fix.

---

## Point & Shoot baseline

- **Openable DNGs:** tag `checkpoint/dng-decode-all-cameras-baseline` — `DngMetadataResolver`, preview-only physical pin, logical RAW_SENSOR routing (`docs/DNG_COLOR_REVERT_CHECKPOINT.md`).
- **FM/ASN TIFF post-process:** **reverted** — broke wide/tele when applied; do not reapply without USB proof.
- **Automation:** `scripts/pns_aux_dng_capture_analyze.ps1` (M14 / M23 / M73).

Follow-up design: `docs/DNG_PS_ALIGNMENT_SPIKE.md` (from ReferenceCam diff + APK strings).
