# Reproducible builds

Point & Shoot targets **recipe-friendly** release builds for GitHub Releases, Obtainium, and a future F-Droid listing. Full byte-identical reproducibility across every host is **not** claimed today; this document lists what is pinned and known deltas.

## Pinned inputs

| Input | Location |
|-------|----------|
| Gradle wrapper | `gradle/wrapper/gradle-wrapper.properties` (8.10.2) |
| Version catalog | `gradle/libs.versions.toml` |
| Dependency lockfile | `app/gradle.lockfile` (regenerate after catalog bumps) |
| NDK | `app/build.gradle.kts` → `ndkVersion = "26.3.11579264"` |
| compileSdk / targetSdk | 36 |
| Bundled LUT URLs + SHA-256 | `app/build.gradle.kts` → `bundledLutSpecs` |
| JetBrains Mono | `LICENSES.md` font table + `assets/fonts/jetbrainsmono/SHA256.txt` |

Refresh dependency locks after changing `libs.versions.toml`:

```powershell
.\scripts\pns_gradlew.ps1 :app:dependencies --write-locks
```

Commit the updated `app/gradle.lockfile` in the same change.

## SOURCE_DATE_EPOCH

Release CI (`.github/workflows/build-signed.yml`) sets `SOURCE_DATE_EPOCH` from the checked-out Git commit timestamp before `:app:assembleRelease`. Local reproducible attempt:

```powershell
$env:SOURCE_DATE_EPOCH = git log -1 --format=%ct
.\scripts\pns_gradlew.ps1 :app:assembleRelease
```

AGP uses this for archive entry timestamps where supported.

## Build-time LUT fetch

`:app:downloadBundledLuts` runs on `preBuild` when `bundledLutSpecs` is non-empty. Each blob is verified against a pinned SHA-256 before copying into `app/src/main/assets/luts/`. Dry-run URLs:

```powershell
.\scripts\pns_gradlew.ps1 :app:downloadBundledLutsDryRun
```

F-Droid builders should run the same Gradle graph so assets materialize identically.

## Known non-determinism (expected deltas)

| Source | Effect |
|--------|--------|
| **Release signing key** | APK signature bytes differ per maintainer keystore. Compare unsigned or same-key rebuilds only. |
| **R8 / resource shrink** | Release minification can vary slightly with JDK/AGP patch levels. |
| **Native `.so`** | CMake/Ninja + NDK patch level affects `libpns_native.so` object code. |
| **SBOM metadata** | `pns_sbom.ps1` emits a fresh UUID/timestamp in CycloneDX metadata (components list is stable). |
| **Baseline profile** | Macrobenchmark outputs may differ per device when regenerated. |

## Host verification

```powershell
.\scripts\pns_repro_build_verify.ps1
```

Checks: Gradle ↔ F-Droid metadata version sync, `app/gradle.lockfile` present, `PRIVACY.md` + `NOTICE` linked from README, SBOM component fingerprint, optional APK cert class via `-ApkPath`.

Wired into `scripts/pns_prerelease_gate.ps1` (Milestone T.11+).

## F-Droid builder notes

See [`CLI_BUILD_AND_SIDELOAD.md`](../CLI_BUILD_AND_SIDELOAD.md) § F-Droid build recipe and [`metadata/metadata.yml`](../metadata/metadata.yml) `Builds:` block.

Offline CI without network: commit verified LUT blobs under `app/src/main/assets/luts/` so `downloadBundledLuts` hits cache-only paths.
