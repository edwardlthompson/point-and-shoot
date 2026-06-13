# ADR-0008 — Gallery-return cold restart; defer unified mock mode

- **Status:** Accepted
- **Date:** 2026-06-12

## Context

Returning to Point & Shoot from an **external media viewer** (e.g. Google Photos) opened via the preview tray thumbnail can leave **GLES external-OES preview** with a wrong aspect ratio or stretched finder. May 2026 bisect showed that in-process resume fixes (`LaunchedEffect` → `setGeometry`, buffer-geometry listeners, `setPreserveEGLContextOnPause`, and related patterns) either failed to fix gallery return or broke **cold-start** preview on fleet devices.

Milestone **T.14** planned a **unified mock/demo mode** for deterministic preview without a live camera. That work touches session create, automation extras, and resume policy — overlapping the gallery-return problem.

## Decision

1. **Gallery return (shipped):** When the tray thumb successfully starts **`openMediaWithSystemResolver`**, set a one-shot flag so **`ON_RESUME`** runs **`restartMainActivityCold`** in `PreviewEngineScreen.kt`:
   - Copy the current `Intent` (action, extras, data).
   - Relaunch the same activity class with **`FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`**.
   - Call **`finishAffinity()`** on the old task.
   - Log: `PNS.AdbValidation` / `preview external viewer return -> restartMainActivityCold`.

2. **All other resumes:** Keep **`kickPreviewPipelineRestart()`** plus optional **`GLSurfaceView.post { requestLayout(); invalidate() }`**. Do **not** add a second `setGeometry` writer — geometry stays driven only from **`PreviewMainViewport`** (`AndroidView` `update` + `OnLayoutChangeListener`).

3. **Unified mock/demo mode (T.14):** **Deferred** until this ADR and **H.CRI-5 slice 1** (session surface policy + automation extras registry) land. Mock mode must not fork resume policy; it should reuse the same cold-restart vs kick split documented here.

## Consequences

- **Positive:** Gallery return matches cold-start GLES state without reintroducing reverted geometry races.
- **Negative:** External viewer return pays a full activity restart (acceptable vs broken preview).
- **Revisit:** T.14 mock mode may inject synthetic buffers but must not replace `restartMainActivityCold` for real viewer return without USB proof on CPH2583 + legacy regression lane.

## References

- [`PreviewEngineScreen.kt`](../../app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt) — `restartMainActivityCold`, tray `openMediaWithSystemResolver` flag
- [`AGENTS.md`](../../AGENTS.md) — CRITICAL GLES preview aspect
- [`docs/PNS_TECHNICAL_SETTINGS.md`](../PNS_TECHNICAL_SETTINGS.md) — gallery return + tray surface restore
- [`BUILD_PLAN.md`](../../BUILD_PLAN.md) — H.CRI-5 / T.14 mock mode gate
