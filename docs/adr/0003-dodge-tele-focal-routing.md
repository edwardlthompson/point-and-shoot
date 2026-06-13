# ADR-0003 — Dodge tele focal routing (physical tele, single sensor)

- **Status:** Accepted
- **Date:** 2026-06-12

## Context

Tele M-slots **73 / 85 / 150 mm** on the dodge reference stack share the mid-tele sensor (`cameraId=4`, LYT-600). May 2026 regressions from dual routing policies (`FocalRoutingPolicy`, logical-only tele, `longTele` for 150 mm) broke digital equivalence: 85/150 looked identical to 73 mm or routed to the wrong sensor.

## Decision

1. **Single policy:** `resolveFocalMmSlot` / `teleOpenablePair` use **`Roles.tele`** for all three tele M-slots — **73** native, **85** `Portrait85`, **150** `LongTele150`.
2. **Physical-first:** when physical tele `tid` is in `cameraIdList`, prefer **`tid to mode`** (open physical e.g. `4`) over forcing logical parent `0` first.
3. **`SensorCropGeometry.LongTele150`:** gates on **`teleId`** only — not **`longTeleId`**.
4. **No persisted fleet tele routing prefs** — one dodge-style path aligned with [`DODGE_PROFILE.md`](../../DODGE_PROFILE.md).
5. **FPS:** digital crops apply when **`desiredFps < 120`** in preview controller.

## Consequences

- Chrome UX gate must prove tele slots with `pns_chrome_ux_gate.ps1 -FocalMmSlot 150` (physical tele + crop mode in logs).
- New SKUs use fleet matrix for capability flags, not a second tele routing enum.

## References

- [`DODGE_PROFILE.md`](../../DODGE_PROFILE.md) — master focal table
- [`.cursor/rules/dodge-tele-focal-routing.mdc`](../../.cursor/rules/dodge-tele-focal-routing.mdc)
- `BackCameraRoleResolver.kt`, `SensorCropGeometry.kt`
