# Camera2 reference — QR / barcode appendix

**Status:** **Milestone 10 Sprint 10.9** — in-app **QR / barcode scan** uses **CameraX** (`Preview` + `ImageAnalysis`, YUV_420_888) and **ZXing** (`com.google.zxing:core`) for decode. Vendor-specific **Barcode / QR** Camera2 keys are still not part of the Android SDK static catalog; they appear only when OEM HALs advertise them in `CameraCharacteristics.getAvailableCaptureRequestKeys()` and related sets.

## Canonical static catalog

Use the repo-generated SDK field list (regenerate with `scripts/pns_gen_camera2_keys_reference.ps1`):

- [`CAMERA2_KEYS_AND_APIS_REFERENCE.md`](./CAMERA2_KEYS_AND_APIS_REFERENCE.md)

## Product implementation (this repo)

| Topic | Choice |
|--------|--------|
| **ADB entry** | `--es pns_screen qrscan` ([`PNS_SCREEN_QR_SCAN`](../app/src/main/java/dev/pointandshoot/CameraCapabilitiesProbe.kt)); engineering hub row **QR / barcode scan**. |
| **Camera API** | **CameraX** — `Preview` + `ImageAnalysis` on the default **back** camera ([`QrScanScreen`](../app/src/main/java/dev/pointandshoot/QrScanScreen.kt)). |
| **YUV format** | `ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888`. |
| **Stride** | Y plane copied into a **tight `width × height`** buffer when `rowStride != width` or the buffer is padded (`copyYPlaneTight`); **requires `pixelStride == 1`** for the Y plane (ZXing `PlanarYUVLuminanceSource` expects dense luminance rows). |
| **Throttle** | Minimum **~280 ms** between decode attempts on the analysis thread ([`QR_SCAN_DECODE_MIN_INTERVAL_MS`](../app/src/main/java/dev/pointandshoot/QrScanScreen.kt)); `STRATEGY_KEEP_ONLY_LATEST` on `ImageAnalysis`. |
| **Decode** | **ZXing** `MultiFormatReader` (QR, Aztec, Data Matrix, PDF417, common 1D). **ML Kit barcode** is intentionally **not** used (FOSS dep-audit allows only pinned `com.google.mlkit:face-detection`). |
| **Threading** | Decode runs on a **single-thread executor**; UI updates are posted to the **main** executor. |

## Fleet / vendor follow-up

On reference devices, export **`PROBE_RESULTS`** / deep-caps JSON and grep vendor namespaces for barcode / QR / ML pipeline hints when investigating OEM-specific behavior.
