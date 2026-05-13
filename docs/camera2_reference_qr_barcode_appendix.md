# Camera2 reference — QR / barcode appendix (stub)

**Status:** Scaffold for **Milestone 10 Sprint 10.9** (QR scan mode). Vendor-specific **Barcode / QR** Camera2 keys are not part of the Android SDK static catalog; they appear only when OEM HALs advertise them in `CameraCharacteristics.getAvailableCaptureRequestKeys()` and related sets.

## Canonical static catalog

Use the repo-generated SDK field list (regenerate with `scripts/pns_gen_camera2_keys_reference.ps1`):

- [`CAMERA2_KEYS_AND_APIS_REFERENCE.md`](./CAMERA2_KEYS_AND_APIS_REFERENCE.md)

## Next steps (product)

1. On reference devices, export **`PROBE_RESULTS`** / deep-caps JSON and grep vendor namespaces for barcode / QR / **Google** ML pipeline hints.
2. When ML Kit or `ImageAnalysis` ships, record the chosen **YUV format**, stride handling, and throttle policy here.
