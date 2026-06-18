# ADR-0009 — Gradle module boundaries (Sprint TM)

- **Status:** Accepted
- **Date:** 2026-06-17
- **Supersedes:** [ADR-0001](0001-core-architecture.md) §Decision item 1 (monolith-only packaging) — **partially**; `:app` remains the composition root.

## Context

Milestone **T** delivered bootstrap template parity (docs, CI, agent gates). Sprint **TM** adds Gradle libraries under `modules/` per the bootstrap template (`MODULE.md`, Golden Path docs, `pns_validate_bootstrap.ps1`). A naïve “move entire packages” split hit **circular dependencies**:

- `:pns-fleet` catalog/parity types reference capture/DNG policy hooks.
- `:pns-capture` bracket/DNG helpers reference fleet matrix and policy surfaces.
- `:pns-preview` session orchestrators reference GLES preview, chrome, and `PreviewEngineScreen` session builders still in `:app`.

Full extraction of `PreviewEngineScreen.kt`, `RawCaptureSupport.kt`, and fleet hub probes in one sprint would violate DNG/chrome/fleet regression locks without per-hunk USB proof.

## Decision

1. **Physical layout:** Four Android libraries — `:pns-core`, `:pns-fleet`, `:pns-capture`, `:pns-preview` — with `projectDir = modules/pns-*` in `settings.gradle.kts`. Each ships a **`MODULE.md`** contract.
2. **Dependency DAG (acyclic):**
   - `:app` → all libraries (composition root: `MainActivity`, `PreviewEngineScreen`, manifest, NDK JNI).
   - `:pns-preview` → `:pns-capture`, `:pns-fleet`, `:pns-core`.
   - `:pns-capture` → `:pns-core` only (fleet DNG policy via `LeafDngFleetPolicies` in core).
   - `:pns-fleet` → `:pns-core`.
   - **No library may depend on `:app`.**
3. **What lives in each module (post–deferred extraction, 2026-06-17):**
   | Module | Shipped | Remains in `:app` |
   |--------|---------|-------------------|
   | **`:pns-core`** | `PnsLog`, `CapabilityGate`, `Feature`, `HardwareCaps`, `RootCapability*`, `BracketPattern`, `PnsSweepSignals`, **`LeafDngFleetPolicy` / `StillDngBackend`**, **`BackCameraRoleResolver`**, **`FocalMmSlot` / `FocalMode`**, **`CommandDialMode` / `ReadoutAeCoupling`**, **`PreviewVideoConstants`**, `SessionConfigurationCompat`, `VideoEncodeLane` | `HardwareCapsSnapshot`, `CapabilityGateBridge` (Camera2 boundary) |
   | **`:pns-fleet`** | Matrix schema/catalog/parity **pure** types, `SessionMatrixProbeCore`, `ProductHardwareLaunchScan`, `FleetCameraProfileStore`, leaderboard slug/readiness | Hub Compose (`*Screen.kt`, `CatalogAttach`), matrix **builder/store**, `FleetUiVisibilityGate`, probes needing live Camera2 / `MediaCodecCapabilityProbe` |
   | **`:pns-capture`** | **`RawCaptureSupport`**, **`Dng12Saver`**, **`DngMetadataResolver`**, **`StillCaptureMetadata`**, TIFF/DNG helpers, bracket schedulers, **`fleet/LeafDngHalReconcile`** (policy via `:pns-core` registry) | None for core RAW/DNG path — session pairing call sites stay in `PreviewEngineScreen` |
   | **`:pns-preview`** | **`preview/session/*`** orchestrators (regular + HFR create, surface policy, context diag), `Camera2SessionCompat`, `PreviewAutomationExtrasRegistry`, `ImageReaderAwait` | `PreviewSessionJpegCompanion`, vendor/macro parameters, HFR output wiring (`HudSettings`, `VideoEffectsProcessor`), GLES mock |
4. **Policy cycle break:** `:pns-capture` does **not** depend on `:pns-fleet`. `LeafDngFleetPolicies.active` in `:pns-core`; `:app` registers `LegacyFleetPolicy` in `PnsApplication.onCreate`.
5. **Hub Compose:** unchanged — `FleetMatrixHubScreen`, `FleetParityModeSheet`, `FleetDeviceMatrixCatalogAttach` in `:app`.
6. **Kover:** `:app` merges `kover(project(":pns-*"))` for integration floor; module verify rules optional.
7. **Golden Path:** unchanged — [`examples/golden-path/README.md`](../../examples/golden-path/README.md).

## Consequences

- Imports in `:app` use `implementation(project(":pns-*"))` for shared types; agents grep both `app/src` and `modules/` when locating fleet/DNG/preview code.
- Further splits (next sprint): move `FleetDeviceMatrixBuilder` / `Store` after `DeviceCameraCapabilityCache` extraction; move remaining `preview/session/*` after `HudSettings` / video-effects seams; GLES mock under `:pns-preview`.
- USB gates after capture/preview moves: `pns_capture_pipeline_verify.ps1` then `pns_chrome_ux_gate.ps1` (sequential, one serial).
- Bootstrap host gate: `pns_validate_bootstrap.ps1` (Tier 0 job 8); closure: `pns_milestone_tm_gate.ps1`.

## References

- [`docs/BOOTSTRAP_TEMPLATE_MAP.md`](../BOOTSTRAP_TEMPLATE_MAP.md)
- [`modules/pns-core/MODULE.md`](../../modules/pns-core/MODULE.md) … `pns-preview/MODULE.md`
- [ADR-0001](0001-core-architecture.md) (Compose + pure JVM helpers — still valid for test strategy)
- [`AGENTS.md`](../../AGENTS.md) — CRITICAL locks unchanged
