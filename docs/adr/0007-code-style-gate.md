# ADR-0007 — Detekt-only code style (no Spotless / ktfmt)

- **Status:** Accepted
- **Date:** 2026-06-12

## Context

Milestone T Sprint T.7 evaluated adding **Spotless** or **ktfmt** for automatic Kotlin formatting alongside the existing **Detekt** static analysis (`config/detekt/detekt.yml` + `app/lint-baseline.xml` baseline).

The codebase is a large Compose monolith with:

- An established **Detekt baseline** (`config/detekt/baseline.xml`) suppressing historical findings without rewriting history.
- **Android Lint** baselines for UI/platform rules.
- Mixed formatting from years of incremental agent/human edits — a wholesale format pass would touch hundreds of files unrelated to functional work.

Spotless/ktfmt adoption would either:

1. Require a **mass reformat commit** (high review noise, git blame damage, merge conflict risk), or
2. Leave a permanent **format-vs-lint drift** if only new files are formatted.

## Decision

**Do not adopt Spotless or ktfmt.** Ship **Detekt + Android Lint** as the code-style gate:

| Layer | Tool | Gate |
|-------|------|------|
| Kotlin static analysis | Detekt | `:app:detekt` in `pns_verify_toolchain.ps1 -RunTests` |
| Android / Compose | Lint | `:app:lintDebug` in `-RunTests` |
| New file size (soft) | CONTRIBUTING policy | ≤250 lines Compose / ≤150 logic for **new** files |

Manual formatting follows Android Studio defaults; agents match surrounding file style per [`AGENTS.md`](../../AGENTS.md) conventions.

## Consequences

- **Positive:** Zero baseline churn; CI stays fast; Detekt rules remain the single Kotlin quality contract.
- **Negative:** No automated format-on-save enforcement in CI — reviewers rely on Detekt + human review for style drift.
- **Revisit:** Re-evaluate Spotless only if a maintainer schedules a dedicated “format baseline” sprint with no concurrent feature work.

## Kover coverage scope (Sprint T.7)

**Plugin:** `org.jetbrains.kotlinx.kover` in `app/build.gradle.kts`.

**Floor:** 40% **line** coverage on JVM-testable helpers:

- `dev.pointandshoot.fleet` (policy, gates, parity evaluators)
- `dev.pointandshoot.Dng*` / `Bracket*` / selected `Tiff*` metadata helpers

**Excluded from floor** (USB or Compose integration tier):

- Compose `*Screen*` / `*ScreenKt*` in fleet
- USB matrix builders (`FleetDeviceMatrixBuilder`, `DeepCapsProbeCore`, …)
- Post-save DNG byte pipelines (`Dng12Saver`, `TiffExifSubIfdCapturePatch`, …) — gated by `dng_tiff_integrity_check.py` / `pns_aux_dng_capture_analyze.ps1`

**Gate:** `:app:koverVerifyDebug` wired in `pns_verify_toolchain.ps1 -RunTests`.

**No `androidTest/`:** Instrumented UI tests are intentionally absent; ADB scripts in [`AGENTS.md`](../../AGENTS.md) are the integration substitute.

## References

- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) — file-size policy, `-RunTests` matrix
- [`config/detekt/detekt.yml`](../../config/detekt/detekt.yml)
- [`scripts/pns_verify_toolchain.ps1`](../../scripts/pns_verify_toolchain.ps1)
- Milestone T Sprint T.7 in [`BUILD_PLAN.md`](../../BUILD_PLAN.md)
