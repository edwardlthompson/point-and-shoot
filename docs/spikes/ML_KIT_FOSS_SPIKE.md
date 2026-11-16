# ML Kit vs FOSS spike (Sprint 32.0)

**Status:** Decision recorded · **Date:** 2026-06-21

## Question

Should `foss` flavor ship ML Kit barcode/QR or stay ZXing-only?

## Decision

| Capability | `foss` default | `gplay` optional |
|------------|----------------|----------------|
| QR scan (`preview.qr`) | **ZXing** (`QrCodeAnalyzer`) — **Shipped** | Same; ML Kit QR **not** enabled in M28 |
| Face track (HUD fallback) | Camera2 `STATISTICS_FACES` + `android.media.FaceDetector` (ADR-0011) | ML Kit YUV fallback (ADR-0006, play-only) |
| ML Kit QR backend (`preview.qr_mlkit`) | **Deferred** post-M28 | Optional future flavor gate |

**Rationale:** ZXing is Apache-friendly, already gated by `pns_qr_scan_verify.ps1`, and avoids Play Services coupling on FOSS builds. ML Kit barcode adds dependency audit surface without user-visible gap on primary fleet workflows.

## References

- `pns_qr_scan_verify.ps1` · [`docs/adr/0011-foss-play-distribution-flavors.md`](../adr/0011-foss-play-distribution-flavors.md)
- Catalog `preview.qr` Shipped · `preview.qr_mlkit` Planned
