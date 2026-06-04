# Point and Shoot v0.14.0-beta.7

Pre-release focused on 4K120 reliability hardening, fleet truth reporting, and broad ReferenceApp naming migration cleanup.

## Highlights

- 4K120 strict-start hardening in the preview pipeline:
  - safer constrained high-speed startup ordering,
  - guarded burst failure recovery and deferred reopen handling,
  - stricter fault retry behavior for unstable start windows.
- M24 script lane upgrades:
  - telemetry-aware 4K120 retries with per-attempt summaries,
  - capability-class gating for strict/endurance paths,
  - parity truth ingestion now binds to explicit strict-run artifacts and serial.
- Fleet parity reporting enhancements:
  - 4K120 truth source/serial fields in parity reports,
  - refreshed leaderboard/scoring outputs.
- Naming and fixture migration cleanup:
  - ProShot/OpenCamera/MotionCam references moved to ReferenceApp/AltReferenceApp/ExternalCameraApp across app code, scripts, docs, and fixtures.

## Validation Notes

- USB validation lane included strict 4K120 verify, endurance, and parity full sweep.
- Truth output remains fail-closed: only `true_4k120` is treated as strict pass.

## Artifacts

- APK: `Point-and-Shoot_0.14.0-beta.7.apk`
- Changelog: `CHANGELOG.md`
