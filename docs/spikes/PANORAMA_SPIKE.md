# Panorama spike (Sprint 31.0)

**Status:** NO-GO for Milestone 28 · **Date:** 2026-06-21

## Goal

Photon / Open Camera–class multi-frame panorama still with preview guidance and merge export.

## Findings

- No in-repo stitch pipeline, gyro alignment, or preview sweep UX.
- Camera2 preview engine is locked to portrait chrome + GLES `setGeometry` contract; panorama needs a dedicated capture session with motion metadata and offline merge (high GLES/session risk).
- Primary fleet device (CPH2583) has no HAL panorama extension advertised.

## Decision

**Defer** consumer `still.panorama` to post-M28. Catalog row **`still.panorama`** → **NotApplicable** (M28 closure) until a dedicated sprint with USB proof on a proving SKU.

## References

- `docs/CAMERA_APP_PIPELINE_BENCHMARK.md` §Feature matrix
- `BUILD_PLAN_COMPLETED.md` Milestone 28
