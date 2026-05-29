# Milestone 10 — Sprint 10.8 reference fleet (host evidence)

**Goal (BUILD_PLAN Sprint 10.8):** reconcile **static** camera caps, **product** dodge mapping, and **scripted** preview RAW behavior across **more than one** fleet narrative — without treating a single USB serial as the only source of truth.

This file **does not replace** fresh exports on new hardware. When you attach additional SKUs, re-run **§ Export / diff procedure** below and extend the comparison table (keep **USB serials out of committed prose** per sprint check).

## Evidence streams already in-repo

| Stream | Artifact | What it captures |
|--------|----------|------------------|
| **A — Shallow probe export** | [`PROBE_RESULTS.md`](../PROBE_RESULTS.md) | Full **CameraCharacteristics** style dump for **OnePlus CPH2655**, **Android 16 (API 36)** (`generated` header in file). Use for **RAW / DR key advertisement** and **high-speed size × FPS** tables per `cameraId`. |
| **B — Dodge topology** | [`DODGE_PROFILE.md`](../DODGE_PROFILE.md) | **OnePlus 13-class** triple rear stack: logical **`0`** → physical **`[2,3,4]`**, per-lens **HFR ceiling** (480 / 240 / 120), **RAW** sizes, **HDR / DCG** probe notes. |
| **C — Scripted RAW matrix** | [`RAW_CAPTURE_DEVICE_MATRIX.md`](RAW_CAPTURE_DEVICE_MATRIX.md) | Same **CPH2655-class** unit under **cold ADB preview** + `pns_raw_capture_matrix.ps1`: whether **`captureRawStill`** + **`DngCreator`** path completes vs **session / HAL** failures (orthogonal to “RAW advertised” in stream A). |
| **D — M13 fleet profiles** | Probe export **Fleet profiles** + **Fleet camera catalog** sections; `files/fleet_camera_profiles_<model>.json`; [`FLEET_ONEPLUS13_RAW_POLICY.md`](FLEET_ONEPLUS13_RAW_POLICY.md) | Per-`cameraId` **roles** (UW/wide/tele), **RAW format order**, **ProShot DNG** flags, **catalog** (public ids + physical children). Refreshed on **Camera capabilities probe → Export** without USB serials in committed markdown. |

Together, **A + B** satisfy “**≥2 extra device classes**” in the **documentation** sense: **shallow-export OnePlus 16 / CPH2655** vs **dodge-profile OnePlus 13 triple-stack** (distinct bill-of-materials and topology even when USB fleet overlaps). **C** adds a **runtime** dimension so RAW12 vs RAW10 ordering and session policy are not inferred from characteristics alone.

## Diff highlights (RAW12 / HFR max / DR) — static vs product vs scripted

### Dynamic range (10-bit session profiles)

- **Stream A (`PROBE_RESULTS.md`):** `android.request.availableDynamicRangeProfiles` example value **`[64, 1, 2, 4, 8]`** and **`recommendedTenBitDynamicRangeProfile: 2`** (see camera **0** block in that export). Use as the **HAL advertisement** baseline for **`PreviewHdrSessionSupport`** gating.
- **Stream B (`DODGE_PROFILE.md`):** maps those capabilities to **product focal slots** and notes **HDR / DCG** vendor paths per lens row.

### HFR (constrained high-speed) ceilings

- **Stream A:** e.g. logical **`cameraId=0`** shows **1920×1080** with **`[480,480]`** among `fpsRanges` (see **high-speed** section in `PROBE_RESULTS.md`).
- **Stream B:** restates per physical id (**480** on wide / logical, **240** on UW/tele, **120** front).

### RAW12 / RAW tier vs save path

- **Stream A:** confirms **RAW** outputs and **`maxNumOutputRaw`** style fields per camera in the markdown export.
- **Stream C:** documents that **no matrix cell** achieved **`captureRawStill 1/1 ok=true saved=`** on the recorded **2026-05-12** runs — i.e. **tier pick (`RawStreamPreference`)** is necessary but **not sufficient**; fleet gates must keep using **`pns_photo_capture_verify.ps1`** / **`pns_capture_pipeline_verify.ps1`** (see also [`REVERTED_FEATURES_RESTORE_LIST.md`](REVERTED_FEATURES_RESTORE_LIST.md) §8).

## Export / diff procedure (for the next USB devices)

1. **Shallow markdown:** Debug hub → **Export** `PROBE_RESULTS_*.md` (or rely on committed canonical refresh when policy changes).
2. **Deep JSON:** in-app **Deep caps** (`pns_autodeepcaps`) or `scripts/pns_hfr_autorun.ps1 -RunProbeSmoke …`, then pull `deep_caps_*.json` from `Android/data/.../files/` (see script comments in repo automation).
3. **Host diff:**  
   `.\scripts\pns_deep_caps_diff.ps1 -PathA <first.json> -PathB <second.json> -OutMarkdown fleet_diff.md`  
   Commit only **redacted** `fleet_diff.md` if it contains no serials / paths you consider sensitive.

### Stream D — fleet profile + catalog (Milestone 13)

- **Probe export:** `CameraCapabilitiesProbe` appends **`FleetCameraProfileStore.appendProbeMarkdown`** (roles, RAW tier, ProShot flags) and **`appendCatalogMarkdown`** (id roster, physical children, OP13 aliases).
- **On-disk JSON:** `Android/data/.../files/fleet_camera_profiles_<MODEL>.json` from first probe or preview session build.
- **Policy doc:** [`FLEET_ONEPLUS13_RAW_POLICY.md`](FLEET_ONEPLUS13_RAW_POLICY.md) — **Standard** still = ProShot `DngCreator` path; wide-cal / reconcile **off** until USB bisect **13.3h** / **13.3e**.
- **Host CI:** `scripts/pns_fixture_dng_gates.ps1` (openability on `tests/fixtures/proshot_cph2655/`) in **toolchain-verify** workflow.

## Related automation

- **`files/fleet_device_matrix.json`** (Milestone **16**) — fleet **source of truth** after hub shallow/full scan. Host: **`scripts/pns_fleet_matrix_scan.ps1`**, **`scripts/pns_fleet_matrix_diff.ps1`**, **`scripts/fleet_matrix_schema_validate.py`**.
- **`scripts/pns_deep_caps_diff.ps1`** — legacy side-by-side for standalone **`deep_caps_*.json`** pulls; for fleet review prefer **`pns_fleet_matrix_diff.ps1`** on matrix JSON (full tier embeds deep caps under **`appendix.deepCaps`**).
- **`scripts/pns_fixture_dng_gates.ps1`** — host DNG openability on committed ProShot reference fixtures (no device).
- **`scripts/pns_op13_regression_pack.ps1`** — optional OP13 lane (matrix + aux DNG + parity); not a default gate on **CPH2583**.
