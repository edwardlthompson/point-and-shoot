# ADR-0001 — Compose monolith with pure JVM helpers

- **Status:** Accepted
- **Date:** 2026-06-12

## Context

Point & Shoot is a Camera2 pro camera with a large preview/capture surface (`PreviewEngineScreen.kt`), engineering probe hub, and many USB-gated behaviors. A full MVVM + DI stack would slow iteration on HAL-specific fixes. The project still needs fast unit tests for policy, scheduling, and format logic.

## Decision

1. **UI:** Jetpack Compose screens with in-composable state; no app-wide ViewModel/Hilt layer.
2. **Concurrency:** Dedicated executors per lane (camera control, reader/encode, meter, JPEG companion) as documented in [`CAPTURE_ARCHITECTURE.md`](../../CAPTURE_ARCHITECTURE.md).
3. **Testability:** Extract **pure JVM** helpers (fleet gates, bracket schedulers, DNG/TIFF patches, crop math) with `app/src/test` coverage.
4. **Golden Path reference:** Engineering **probe hub** + **`ProHudScreen`** / **`GLPreviewScreen`** mock routes for offline layout and GLES checks without a live sensor.

## Consequences

- Large composable files remain; extraction is incremental (e.g. `preview/session/*` orchestrators).
- Integration truth stays on **USB ADB gates**, not instrumented UI tests.
- New features should add pure helpers + JVM tests before wiring into `PreviewEngineScreen.kt`.

## References

- [`CAPTURE_ARCHITECTURE.md`](../../CAPTURE_ARCHITECTURE.md)
- [`PROBE_BUILD_PLAN.md`](../../PROBE_BUILD_PLAN.md)
- [`KNOWLEDGE_BASE.md`](../../KNOWLEDGE_BASE.md) §1
