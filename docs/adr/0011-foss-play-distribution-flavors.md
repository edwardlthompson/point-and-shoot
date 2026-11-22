# ADR-0011 — foss / play distribution flavors

- **Status:** Accepted
- **Date:** 2026-11-16
- **Supersedes:** [ADR-0006](0006-mlkit-face-detection-exception.md)

## Context

ADR-0006 allowed **`com.google.mlkit:face-detection`** in the single APK so Eye-AF HUD still worked when Camera2 `STATISTICS_FACES` was empty. That coordinate pulls Play Services / Firebase encoder transitives, so F-Droid marked the app **Not eligible** (`NonFreeDep`).

Edward's product rule is not to drop face / eye detection. Camera2 `STATISTICS_FACE_DETECT_MODE` is already the primary HUD path. AOSP `android.media.FaceDetector` is a practical on-device fallback with no GMS.

## Decision

1. **Product flavors** on `:app`, dimension `distribution`:
   - **`play`** (default) — GitHub Releases / Obtainium. Same ML Kit YUV fallback as ADR-0006. APK copied to legacy `app/build/outputs/apk/{debug,release}/app-*.apk` so USB scripts stay unchanged.
   - **`foss`** — F-Droid. No ML Kit / Play Services / Firebase. Camera2 `STATISTICS_FACES` primary; `android.media.FaceDetector` on the YUV analysis stream when the HAL reports no faces. Smile-triggered still uses [`FossSmileEstimator`](../../app/src/main/java/dev/pointandshoot/FossSmileEstimator.kt) (mouth-band luma), not ML Kit classification.
2. ML Kit stays **play-only**: `app/src/play/`, `app/play.gradle`, `app/proguard-play-rules.pro`, playRelease baseline profiles. F-Droid recipes `rm` those paths before scan. The foss dep-audit forbids `com.google.mlkit` outside `app/play.gradle`.
3. **CameraX** (`androidx.camera:*`, including extensions) stays on both flavors — no GMS / ML Kit Vision artifact. QR remains **ZXing** (no `preview.qr_mlkit`).
4. **On-device only** — no cloud face API on either flavor.
5. Host gate: `scripts/pns_fdroid_foss_scan.ps1` (source + packaged classes when a foss APK exists).

## Consequences

- F-Droid builds `:app:assembleFossRelease`. GitHub signed workflow still produces the play APK via `:app:assembleRelease` (aggregator) + legacy copy of playRelease.
- Smile-triggered still in foss is a weaker heuristic; it stays **off** by default. Face / eye overlay remains enabled.
- Device verification of the foss FaceDetector fallback was **not** run in the flavor-split change (no USB). Camera2 primary path is unchanged.

## References

- `app/src/foss/java/dev/pointandshoot/MlKitFaceTrackSupport.kt`
- `app/src/play/java/dev/pointandshoot/MlKitFaceTrackSupport.kt`
- [`PRIVACY.md`](../../PRIVACY.md)
- [`docs/face-eye-tracking-toolkit.md`](../face-eye-tracking-toolkit.md)
- `scripts/pns_fdroid_foss_scan.ps1`
