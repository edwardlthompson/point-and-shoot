# Computational HDR spike (Sprint 31.0)

**Status:** NO-GO for Milestone 28 · **Date:** 2026-06-21

## Goal

Photon-class multi-frame computational HDR still merge (not beauty / face retouch).

## Findings

- `NightScapeCapture.kt` is night stacking, not bracket-merge computational HDR.
- No align-and-merge stack, ghost removal, or tone-map pipeline in `:pns-capture`.
- Comp HDR would touch capture session timing, buffer pools, and DNG/JPEG export paths — overlaps DNG loadability locks.

## Decision

**Defer** `still.computational_hdr` to post-M28. Catalog **`still.computational_hdr`** → **Planned** with `sweepSkipReason=post_m28_defer` until spike PASS on a bracket-capable SKU.

## References

- `docs/spikes/PANORAMA_SPIKE.md` (same defer lane)
- `docs/CAMERA_APP_PIPELINE_BENCHMARK.md`
