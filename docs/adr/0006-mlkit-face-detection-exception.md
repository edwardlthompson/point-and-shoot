# ADR-0006 — ML Kit face detection FOSS exception

- **Status:** Accepted
- **Date:** 2026-06-12

## Context

Face/Eye HUD fallback uses **`com.google.mlkit:face-detection`** (`MlKitFaceTrackSupport.kt`). The project FOSS gate in `pns_verify_toolchain.ps1` rejects Play Services, Firebase, and most ML Kit artifacts. ML Kit pulls **`com.google.android.gms.tasks`** at runtime for async completion. F-Droid and strict FOSS reviewers may question the transitive Google artifact.

## Decision

1. **Allow** only `com.google.mlkit:face-detection` in the FOSS dep-audit (explicit exception in `pns_verify_toolchain.ps1`).
2. **On-device only** — no cloud face API; processing stays on-device for preview overlay / metering assist.
3. **Document** in [`PRIVACY.md`](../../PRIVACY.md) (Sprint T.11) and F-Droid store copy when published.
4. **Alternative considered:** Camera2 `CaptureResult` face rectangles only — insufficient for Eye-AF overlay alignment on some fleet SKUs; ML Kit retained until Camera2-only path is USB-proven per SKU.

## Consequences

- SBOM and license inventory must list ML Kit + transitive deps.
- Removing ML Kit requires fleet matrix + parity proof for face/Eye features on all onboarded SKUs.
- No other ML Kit or Play Services modules may be added without a new ADR + audit whitelist change.

## References

- `app/build.gradle.kts` — `libs.google.mlkit.face.detection`
- [`LICENSES.md`](../../LICENSES.md)
- [`docs/face-eye-tracking-toolkit.md`](../face-eye-tracking-toolkit.md)
- [`docs/CAPABILITY_NOVELTY_TRACKING.md`](../CAPABILITY_NOVELTY_TRACKING.md)
