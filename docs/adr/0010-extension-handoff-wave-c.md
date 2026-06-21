# ADR-0010 — Isolated extension handoff for Wave C HDR/AUTO

- **Status:** Accepted
- **Date:** 2026-06-20

## Context

Milestone **28.0** benchmarked FOSS camera apps and catalog rows for OEM ISP extensions (HDR, night, auto). The shipped preview engine is a **Camera2** `PreviewController` + GLES external-OES path locked to layout-driven `setGeometry` ([ADR-0008](0008-mock-mode-cold-restart.md), `preview-chrome-ui-lock.mdc`). Inline merging `CameraExtensionSession` or CameraX extension capture into the live preview session risks:

- `ERROR_CAMERA_DEVICE` / session recreate races with RAW still automation
- GLES preview stretch on resume (reverted patterns in `AGENTS.md` CRITICAL — GLES preview aspect)
- DNG metadata pairing drift when extension and logical RAW share one session

Sprint **28.2** spike: prove HDR extension in an **isolated route** with **cold handoff** back to `pns_screen=preview`.

## Decision

**GO** for Wave C (**31.4**) consumer HDR and AUTO extension modes via **isolated handoff**, not inline session merge.

1. **Primary preview** stays Camera2 + existing `PreviewEngineScreen` session graph.
2. **Extension capture/preview** runs in a dedicated route (`pns_screen=extensionhandoff` / future consumer UI entry) using `CameraDevice.createExtensionSession`, preferring **HDR** when advertised.
3. **Return path** clears the task and relaunches `MainActivity` with `pns_screen=preview` + one-shot `pns_after_extension_handoff` extra; preview logs `previewReturnAfterExtensionHandoff ok=true` for USB gates.
4. **Hard exclusions** (product policy): no `FACE_RETOUCH` / beauty extensions; fleet visibility hides unavailable extension modes on consumer chrome.
5. **CameraX** remains probe-only (`CameraXExtensionProbe`) until a separate spike shows parity with Camera2 handoff on primary fleet SKUs; Wave C ships Camera2 extension path first.

## Consequences

- Wave C implementation adds consumer chrome entry → handoff activity/route → return; no `PreviewEngineScreen` session fork for extensions.
- Devices with **no** advertised extensions: gate `PROBE_OK_NO_EXTENSIONS`; consumer modes stay hidden per `FleetUiVisibilityGate`.
- USB proof: `scripts/pns_extension_handoff_spike.ps1` on primary fleet device (CPH2583).
- **CPH2583 (b5214fc6, 2026-06-20):** Camera2 + CameraX report no OEM extensions on this ROM; spike still proves cold preview return (`PROBE_OK_NO_EXTENSIONS`).
- Regression: after extension handoff changes, run spike gate **then** optional `pns_chrome_ux_gate.ps1` **sequentially** (never parallel with capture verify on one serial).

## References

- `BUILD_PLAN.md` Milestone **28.2**, Wave C **31.4**
- `docs/CAMERA_APP_PIPELINE_BENCHMARK.md`
- `ExtensionHandoffSpikeRunner.kt`, `scripts/pns_extension_handoff_spike.ps1`
- `docs/M13V_18_CAMERAX_EXTENSIONS.md` (probe lane)
- `.cursor/rules/preview-chrome-ui-lock.mdc`
