# :pns-capture

**Gradle:** `modules/pns-capture` · **Package:** `dev.pointandshoot` (capture/DNG)

## Role

RAW still pipeline: `RawCaptureSupport`, DNG save, TIFF patches, bracket scheduling, leaf DNG reconcile (`fleet/LeafDngHalReconcile`).

## Locks

- No `ExifInterface.saveAttributes()` on DNG ([`dng-save-pipeline-lock.mdc`](../../.cursor/rules/dng-save-pipeline-lock.mdc))
- `allowPhysicalTotalResultPairing=false` at app call sites

## Dependencies

- `:pns-core` only — fleet policy via `LeafDngFleetPolicies.active` (registered in `:app`)

## Gates

`pns_fixture_dng_gates.ps1` · USB `pns_capture_pipeline_verify.ps1`
