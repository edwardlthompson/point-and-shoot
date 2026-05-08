# Release notes template

Copy this file to `RELEASE_NOTES.md` when preparing a release, then fill in the sections below. After publishing, also move the matching `Unreleased` block in `CHANGELOG.md` to a new versioned section.

## Summary

- (1–3 bullets: what changed and why it matters)

## Highlights

- (Notable features / fixes / performance changes)

## Compatibility

- **Target device**: OnePlus 13 (`dodge`)
- **OS**: LineageOS 23 (Android 16 / API 36)
- **Constraint**: FOSS-only; no Google Play Services
- **Notes**: (any known limitations)

## Changes

### Added

- (bullets)

### Changed

- (bullets)

### Fixed

- (bullets)

### Removed / deprecated

- (bullets)

## Verification

- [ ] [HOST] `.\scripts\pns_verify_toolchain.ps1` (or `-SkipGradle` for docs-only)
- [ ] [HOST] `.\gradlew.bat :app:assembleRelease` (or `-AssembleReleaseOnly` via `pns_hfr_autorun.ps1`)
- [ ] [HOST] `apksigner verify --verbose <app-release.apk>` (signed CI builds only)
- [ ] [ADB] `adb install -r <apk>` + smoke run (no crash)
- [ ] [ADB] Logcat monitored with `--pid=$(adb shell pidof dev.pointandshoot)` filter

## Upgrade notes

- (breaking changes, migration steps, behavior the user should re-verify)
