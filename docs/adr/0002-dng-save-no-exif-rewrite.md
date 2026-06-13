# ADR-0002 — No full-file EXIF rewrite on DNG after save

- **Status:** Accepted
- **Date:** 2026-06-12

## Context

May 2026 regression: calling `ExifInterface.saveAttributes()` on row-strip DNGs (~25 MB) after `DngCreator.writeImage` rewrote TIFF like JPEG-style EXIF and **destroyed per-row `StripOffsets`** on the legacy SKU HAL. Lightroom/ACR refused files; `dng_tiff_integrity_check.py` could still pass on pulled copies.

## Decision

1. **`StillCaptureMetadata.applyToDngUri`:** in-place IFD patches only (`TiffIfd0Software305`, `TiffExifSubIfdCapturePatch`); write bytes back via `contentResolver.openOutputStream(uri, "wt")`.
2. **Never** call `ExifInterface.saveAttributes()` (or any full-file EXIF rewrite) on DNG.
3. **`LeafDngHalReconcile`:** `AsShotNeutral` only from Bayer means — no IFD0 ColorMatrix/ForwardMatrix overwrite in the save path without maintainer USB proof.

## Consequences

- Post-save metadata edits are limited to safe in-place TIFF patches.
- Desktop openability gate (`dng_desktop_open_gate.py`) and aux DNG analyze scripts remain mandatory USB proof for DNG path changes.
- Regression signature **R1** in [`docs/DNG_OPENABILITY_REGRESSIONS.md`](../DNG_OPENABILITY_REGRESSIONS.md).

## References

- [`.cursor/rules/dng-save-pipeline-lock.mdc`](../../.cursor/rules/dng-save-pipeline-lock.mdc)
- [`docs/AGENT_REGRESSION_MEMORY.md`](../AGENT_REGRESSION_MEMORY.md) — grep `ExifInterface`
- Gate: `scripts/pns_aux_dng_capture_analyze.ps1`, `scripts/dng_tiff_integrity_check.py`
